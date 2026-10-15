package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.mapepire_ibmi.types.QueryResult;

/**
 * Unit tests for CONCUR_UPDATABLE result sets, checking the SQL generated for
 * each change against a mocked connection.
 */
@ExtendWith(MockitoExtension.class)
class MapepireResultSetUpdatableTest {
    private static final String QUERY = "SELECT ID, NAME FROM MYLIB.EMP";

    @Mock
    private MapepireConnection connection;

    @Mock
    private DatabaseMetaData databaseMetaData;

    @Mock
    private PreparedStatement preparedStatement;

    private static QueryResult<Object> rows(String column, Object... values) {
        List<Object> rows = new ArrayList<>();
        for (Object value : values) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(column, value);
            if (column.equals("ID")) {
                row.put("NAME", "E" + value);
            }
            rows.add(row);
        }
        QueryResult<Object> result = new QueryResult<>();
        result.setData(rows);
        result.setIsDone(true);
        return result;
    }

    private MapepireResultSet updatable(String sql, int rowCount) throws SQLException {
        Object[] ids = new Object[rowCount];
        for (int i = 0; i < rowCount; i++) {
            ids[i] = i + 1;
        }
        MapepireStatement statement = new MapepireStatement(this.connection, ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_UPDATABLE);
        return new MapepireResultSet(statement, sql, rows("ID", ids), ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_UPDATABLE);
    }

    private void stubPrimaryKey(String schema, Object... columns) throws SQLException {
        when(this.connection.getMetaData()).thenReturn(this.databaseMetaData);
        when(this.databaseMetaData.getPrimaryKeys(null, schema, "EMP"))
                .thenReturn(new MapepireResultSet(rows("COLUMN_NAME", columns)));
    }

    private void stubUpdateCount(int updateCount) throws SQLException {
        when(this.connection.prepareStatement(anyString())).thenReturn(this.preparedStatement);
        when(this.preparedStatement.executeUpdate()).thenReturn(updateCount);
    }

    @Test
    void updateRowUpdatesByPrimaryKeyAndShowsNewValue() throws SQLException {
        stubPrimaryKey("MYLIB", "ID");
        stubUpdateCount(1);
        MapepireResultSet rs = updatable(QUERY, 2);

        rs.absolute(2);
        rs.updateString("NAME", "Bob");
        rs.updateRow();

        verify(this.connection).prepareStatement("UPDATE \"MYLIB\".\"EMP\" SET \"NAME\" = ? WHERE \"ID\" = ?");
        verify(this.preparedStatement).setObject(1, "Bob");
        verify(this.preparedStatement).setObject(2, 2);
        assertEquals("Bob", rs.getString(2));
    }

    @Test
    void updateRowThrowsWhenRowIsGone() throws SQLException {
        stubPrimaryKey("MYLIB", "ID");
        stubUpdateCount(0);
        MapepireResultSet rs = updatable(QUERY, 1);

        rs.next();
        rs.updateString(2, "Bob");
        assertThrows(SQLException.class, rs::updateRow);
        assertEquals("E1", rs.getString(2));
    }

    @Test
    void deleteRowRemovesRowAndMovesToPreviousRow() throws SQLException {
        stubPrimaryKey("MYLIB", "ID");
        stubUpdateCount(1);
        MapepireResultSet rs = updatable(QUERY, 3);

        rs.absolute(2);
        rs.deleteRow();

        verify(this.connection).prepareStatement("DELETE FROM \"MYLIB\".\"EMP\" WHERE \"ID\" = ?");
        assertEquals(1, rs.getInt(1));
        assertTrue(rs.next());
        assertEquals(3, rs.getInt(1));
        assertTrue(rs.isLast());
    }

    @Test
    void insertRowInsertsOnlyColumnsThatWereSet() throws SQLException {
        stubPrimaryKey("MYLIB", "ID");
        stubUpdateCount(1);
        MapepireResultSet rs = updatable(QUERY, 1);

        rs.next();
        rs.moveToInsertRow();
        rs.updateInt("ID", 9);
        rs.insertRow();
        rs.moveToCurrentRow();

        verify(this.connection).prepareStatement("INSERT INTO \"MYLIB\".\"EMP\" (\"ID\") VALUES (?)");
        assertEquals(1, rs.getInt(1));
    }

    @Test
    void unqualifiedTableUsesCurrentSchemaForKeyLookup() throws SQLException {
        when(this.connection.getSchema()).thenReturn("NAJEB");
        stubPrimaryKey("NAJEB", "ID");
        stubUpdateCount(1);
        MapepireResultSet rs = updatable("SELECT * FROM emp", 1);

        rs.next();
        rs.deleteRow();

        verify(this.connection).prepareStatement("DELETE FROM \"EMP\" WHERE \"ID\" = ?");
    }

    @Test
    void joinIsNotUpdatable() throws SQLException {
        MapepireResultSet rs = updatable("SELECT E.ID, D.NAME FROM EMP E JOIN DEPT D ON E.DEPT = D.ID", 1);

        rs.next();
        SQLException e = assertThrows(SQLException.class, () -> rs.updateString(2, "Bob"));
        assertTrue(e.getMessage().contains("single table"), e.getMessage());
    }

    @Test
    void queryMustSelectEveryPrimaryKeyColumn() throws SQLException {
        stubPrimaryKey("MYLIB", "ID", "DEPT");
        MapepireResultSet rs = updatable(QUERY, 1);

        SQLException e = assertThrows(SQLException.class, rs::moveToInsertRow);
        assertTrue(e.getMessage().contains("DEPT"), e.getMessage());
    }
}
