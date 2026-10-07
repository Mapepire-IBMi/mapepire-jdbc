package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.mapepire_ibmi.types.ColumnMetadata;
import io.github.mapepire_ibmi.types.QueryMetadata;
import io.github.mapepire_ibmi.types.QueryResult;

class MapepireResultSetMetaDataTest {

    private ResultSetMetaData md;

    /**
     * Column descriptions shaped like the ones the Mapepire server sends, using
     * the type names JTOpen reports for each Db2 for i type.
     */
    private static QueryMetadata sampleMetadata() {
        return new QueryMetadata(5, Arrays.asList(
                new ColumnMetadata(11, "ID", "ID", "INTEGER", 10, 0, true,
                        ResultSetMetaData.columnNoNulls, false, true, "EMPLOYEE"),
                new ColumnMetadata(12, "Salary", "SALARY", "DECIMAL", 10, 2, false,
                        ResultSetMetaData.columnNullable, false, true, "EMPLOYEE"),
                new ColumnMetadata(20, "NAME", "NAME", "VARCHAR", 20, 0, false,
                        ResultSetMetaData.columnNullable, false, true, null),
                new ColumnMetadata(2, "FLAGS", "FLAGS", "CHAR () FOR BIT DATA", 2, 0, false,
                        ResultSetMetaData.columnNullable, true, false, null),
                new ColumnMetadata(1024, null, "DOC", "CLOB", 1024, 0, false,
                        ResultSetMetaData.columnNullableUnknown, false, false, null)),
                "123456/QUSER/QZDASOINIT", null);
    }

    @BeforeEach
    void setUp() {
        md = new MapepireResultSetMetaData(sampleMetadata());
    }

    // -------------------------------------------------------------------------
    // column count, names, labels
    // -------------------------------------------------------------------------

    @Test
    void columnCountMatchesServerMetadata() throws SQLException {
        assertEquals(5, md.getColumnCount());
    }

    @Test
    void columnNameAndLabelComeFromServer() throws SQLException {
        assertEquals("SALARY", md.getColumnName(2));
        assertEquals("Salary", md.getColumnLabel(2));
    }

    @Test
    void columnLabelFallsBackToNameWhenMissing() throws SQLException {
        assertEquals("DOC", md.getColumnLabel(5));
    }

    @Test
    void tableNameIsEmptyStringWhenServerDoesNotReportIt() throws SQLException {
        assertEquals("EMPLOYEE", md.getTableName(1));
        assertEquals("", md.getTableName(3));
    }

    @Test
    void schemaAndCatalogAreEmptyStrings() throws SQLException {
        // The server does not send schema/catalog; JDBC requires "" when unknown
        assertEquals("", md.getSchemaName(1));
        assertEquals("", md.getCatalogName(1));
    }

    // -------------------------------------------------------------------------
    // types
    // -------------------------------------------------------------------------

    @Test
    void columnTypeIsMappedFromDb2TypeName() throws SQLException {
        assertEquals(Types.INTEGER, md.getColumnType(1));
        assertEquals(Types.DECIMAL, md.getColumnType(2));
        assertEquals(Types.VARCHAR, md.getColumnType(3));
        assertEquals(Types.BINARY, md.getColumnType(4));
        assertEquals(Types.CLOB, md.getColumnType(5));
    }

    @Test
    void columnTypeNameIsReportedVerbatim() throws SQLException {
        assertEquals("CHAR () FOR BIT DATA", md.getColumnTypeName(4));
    }

    @Test
    void columnClassNameFollowsJdbcTypeMapping() throws SQLException {
        assertEquals("java.lang.Integer", md.getColumnClassName(1));
        assertEquals("java.math.BigDecimal", md.getColumnClassName(2));
        assertEquals("java.lang.String", md.getColumnClassName(3));
        assertEquals("[B", md.getColumnClassName(4));
        assertEquals("java.sql.Clob", md.getColumnClassName(5));
    }

    @Test
    void precisionScaleAndDisplaySizeComeFromServer() throws SQLException {
        assertEquals(10, md.getPrecision(2));
        assertEquals(2, md.getScale(2));
        assertEquals(12, md.getColumnDisplaySize(2));
    }

    // -------------------------------------------------------------------------
    // column attributes
    // -------------------------------------------------------------------------

    @Test
    void nullabilityIsPassedThrough() throws SQLException {
        assertEquals(ResultSetMetaData.columnNoNulls, md.isNullable(1));
        assertEquals(ResultSetMetaData.columnNullable, md.isNullable(2));
        assertEquals(ResultSetMetaData.columnNullableUnknown, md.isNullable(5));
    }

    @Test
    void autoIncrementReadOnlyAndWritableArePassedThrough() throws SQLException {
        assertTrue(md.isAutoIncrement(1));
        assertFalse(md.isAutoIncrement(2));
        assertTrue(md.isReadOnly(4));
        assertFalse(md.isWritable(4));
        assertTrue(md.isWritable(1));
        assertFalse(md.isDefinitelyWritable(1));
    }

    @Test
    void signedOnlyForNumericColumns() throws SQLException {
        assertTrue(md.isSigned(1));
        assertTrue(md.isSigned(2));
        assertFalse(md.isSigned(3));
    }

    @Test
    void caseSensitiveOnlyForCharacterColumns() throws SQLException {
        assertFalse(md.isCaseSensitive(1));
        assertTrue(md.isCaseSensitive(3));
        assertFalse(md.isCaseSensitive(4));
    }

    @Test
    void lobColumnsAreNotSearchable() throws SQLException {
        assertTrue(md.isSearchable(3));
        assertFalse(md.isSearchable(5));
    }

    @Test
    void currencyIsAlwaysFalse() throws SQLException {
        assertFalse(md.isCurrency(2));
    }

    @Test
    void invalidColumnIndexThrows() {
        assertThrows(SQLException.class, () -> md.getColumnName(0));
        assertThrows(SQLException.class, () -> md.getColumnType(6));
    }

    @Test
    void missingColumnListMeansNoColumns() throws SQLException {
        ResultSetMetaData empty = new MapepireResultSetMetaData(new QueryMetadata());
        assertEquals(0, empty.getColumnCount());
    }

    @Test
    void unwrapsToItself() throws SQLException {
        assertTrue(md.isWrapperFor(MapepireResultSetMetaData.class));
        assertSame(md, md.unwrap(MapepireResultSetMetaData.class));
        assertThrows(SQLException.class, () -> md.unwrap(String.class));
    }

    // -------------------------------------------------------------------------
    // JdbcTypes mapping for names not covered above
    // -------------------------------------------------------------------------

    @Test
    void typeMappingCoversDb2ForIVariants() {
        assertEquals(Types.VARBINARY, JdbcTypes.toSqlType("VARCHAR () FOR BIT DATA"));
        assertEquals(Types.LONGVARBINARY, JdbcTypes.toSqlType("LONG VARCHAR FOR BIT DATA"));
        assertEquals(Types.DECIMAL, JdbcTypes.toSqlType("DECFLOAT"));
        assertEquals(Types.NCHAR, JdbcTypes.toSqlType("nchar"));
        assertEquals(Types.CHAR, JdbcTypes.toSqlType("GRAPHIC"));
        assertEquals(Types.CLOB, JdbcTypes.toSqlType("DBCLOB"));
        assertEquals(Types.SQLXML, JdbcTypes.toSqlType("XML"));
        assertEquals(Types.TIMESTAMP, JdbcTypes.toSqlType("TIMESTAMP"));
        assertEquals(Types.OTHER, JdbcTypes.toSqlType("SOMETHING_NEW"));
        assertEquals(Types.OTHER, JdbcTypes.toSqlType(null));
    }

    // -------------------------------------------------------------------------
    // ResultSet.getMetaData integration
    // -------------------------------------------------------------------------

    private static MapepireResultSet resultSet(QueryMetadata metadata, Object... rows) {
        QueryResult<Object> result = new QueryResult<>();
        result.setMetadata(metadata);
        result.setData(Arrays.asList(rows));
        result.setIsDone(true);
        return new MapepireResultSet(result);
    }

    @Test
    void resultSetMetaDataAvailableBeforeNextAndOnEmptyResult() throws SQLException {
        try (MapepireResultSet rs = resultSet(sampleMetadata())) {
            assertEquals(5, rs.getMetaData().getColumnCount());
            assertFalse(rs.next());
        }
    }

    @Test
    void resultSetMetaDataIsCached() throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ID", 1);
        try (MapepireResultSet rs = resultSet(sampleMetadata(), row)) {
            assertSame(rs.getMetaData(), rs.getMetaData());
        }
    }

    @Test
    void resultSetMetaDataThrowsWhenClosed() throws SQLException {
        MapepireResultSet rs = resultSet(sampleMetadata());
        rs.close();
        assertThrows(SQLException.class, rs::getMetaData);
    }

    @Test
    void resultSetMetaDataThrowsWhenServerSentNone() {
        MapepireResultSet rs = resultSet(null, Collections.emptyMap());
        SQLException e = assertThrows(SQLException.class, rs::getMetaData);
        assertTrue(e.getMessage().contains("not available"));
    }
}
