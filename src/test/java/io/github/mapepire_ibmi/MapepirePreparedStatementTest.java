package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.mapepire_ibmi.types.QueryOptions;
import io.github.mapepire_ibmi.types.QueryResult;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class MapepirePreparedStatementTest {

    private MapepireConnection connection;
    private MapepirePreparedStatement ps;

    @Mock
    private SqlJob mockJob;

    @Mock
    private Query mockQuery;

    @BeforeEach
    void setUp() {
        connection = new MapepireConnection(new SqlJob());
        ps = new MapepirePreparedStatement(connection, "SELECT * FROM T WHERE A = ? AND B = ?");
    }

    private MapepirePreparedStatement preparedStatementWithMockJob(String sql) {
        return new MapepirePreparedStatement(new MapepireConnection(mockJob), sql);
    }

    // -------------------------------------------------------------------------
    // construction via Connection.prepareStatement
    // -------------------------------------------------------------------------

    @Test
    void prepareStatementReturnsPreparedStatement() throws SQLException {
        PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM SYSIBM.SYSDUMMY1");
        assertTrue(prepared instanceof MapepirePreparedStatement);
    }

    // -------------------------------------------------------------------------
    // parameter setting
    // -------------------------------------------------------------------------

    @Test
    void settingParametersDoesNotThrow() throws SQLException {
        ps.setString(1, "value");
        ps.setInt(2, 42);
        ps.setLong(1, 42L);
        ps.setBoolean(2, true);
        ps.setDouble(1, 1.5d);
        ps.setNull(2, Types.INTEGER);
    }

    @Test
    void setParameterThrowsForIndexBelowOne() {
        assertThrows(SQLException.class, () -> ps.setString(0, "value"));
        assertThrows(SQLException.class, () -> ps.setInt(-1, 42));
    }

    @Test
    void setParameterThrowsOnClosedStatement() throws SQLException {
        ps.close();
        assertThrows(SQLException.class, () -> ps.setString(1, "value"));
    }

    @Test
    void executeThrowsWhenParameterGapExists() throws SQLException {
        // Only parameter 2 is set — parameter 1 is missing
        ps.setInt(2, 42);
        SQLException e = assertThrows(SQLException.class, () -> ps.executeQuery());
        assertTrue(e.getMessage().contains("Parameter 1 is not set"));
    }

    @Test
    void clearParametersRemovesPreviouslySetValues() throws SQLException {
        ps.setString(1, "value");
        ps.setInt(2, 42);
        ps.clearParameters();
        ps.setInt(2, 42);

        // After clearing, parameter 1 is missing again
        SQLException e = assertThrows(SQLException.class, () -> ps.executeQuery());
        assertTrue(e.getMessage().contains("Parameter 1 is not set"));
    }

    // -------------------------------------------------------------------------
    // Statement-style execute methods are forbidden on a PreparedStatement
    // -------------------------------------------------------------------------

    @Test
    void executeQueryWithSqlStringThrows() {
        assertThrows(SQLException.class, () -> ps.executeQuery("SELECT 1 FROM SYSIBM.SYSDUMMY1"));
    }

    @Test
    void executeUpdateWithSqlStringThrows() {
        assertThrows(SQLException.class, () -> ps.executeUpdate("DELETE FROM T"));
    }

    @Test
    void executeWithSqlStringThrows() {
        assertThrows(SQLException.class, () -> ps.execute("SELECT 1 FROM SYSIBM.SYSDUMMY1"));
    }

    // -------------------------------------------------------------------------
    // inherited Statement behavior
    // -------------------------------------------------------------------------

    @Test
    void isClosedWorksOnPreparedStatement() throws SQLException {
        assertFalse(ps.isClosed());
        ps.close();
        assertTrue(ps.isClosed());
    }

    // -------------------------------------------------------------------------
    // unimplemented methods throw SQLFeatureNotSupportedException (SQLException)
    // -------------------------------------------------------------------------

    @Test
    void unimplementedMethodsThrowSQLFeatureNotSupportedException() {
        assertThrows(SQLFeatureNotSupportedException.class, () -> ps.addBatch());
    }

    // -------------------------------------------------------------------------
    // executeQuery / executeUpdate / execute against a mocked SqlJob and Query
    // -------------------------------------------------------------------------

    @Test
    void executeQueryPassesBoundParametersInOrder() throws Exception {
        MapepirePreparedStatement mockedPs = preparedStatementWithMockJob("SELECT * FROM T WHERE A = ? AND B = ?");
        mockedPs.setString(1, "A");
        mockedPs.setInt(2, 42);

        QueryResult<Object> result = mock(QueryResult.class);
        when(result.getData()).thenReturn(Collections.emptyList());
        when(mockJob.query(eq("SELECT * FROM T WHERE A = ? AND B = ?"), any(QueryOptions.class)))
                .thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(CompletableFuture.completedFuture(result));

        try (ResultSet rs = mockedPs.executeQuery()) {
            assertFalse(rs.next());
        }

        ArgumentCaptor<QueryOptions> optionsCaptor = ArgumentCaptor.forClass(QueryOptions.class);
        verify(mockJob).query(eq("SELECT * FROM T WHERE A = ? AND B = ?"), optionsCaptor.capture());
        assertEquals(Arrays.asList("A", 42), optionsCaptor.getValue().getParameters());
    }

    @Test
    void executeUpdateReturnsUpdateCountFromMockedQuery() throws Exception {
        MapepirePreparedStatement mockedPs = preparedStatementWithMockJob("DELETE FROM T WHERE A = ?");
        mockedPs.setInt(1, 7);

        QueryResult<Object> result = mock(QueryResult.class);
        when(result.getUpdateCount()).thenReturn(3);
        when(mockJob.query(eq("DELETE FROM T WHERE A = ?"), any(QueryOptions.class))).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(CompletableFuture.completedFuture(result));

        assertEquals(3, mockedPs.executeUpdate());
    }

    @Test
    void executePropagatesSqlStateFromFailedQuery() throws Exception {
        MapepirePreparedStatement mockedPs = preparedStatementWithMockJob("SELECT * FROM BOGUS WHERE A = ?");
        mockedPs.setInt(1, 1);

        SQLException serverError = new SQLException("Table not found", "42S02");
        CompletableFuture<QueryResult<Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(serverError);

        when(mockJob.query(eq("SELECT * FROM BOGUS WHERE A = ?"), any(QueryOptions.class))).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(failed);

        SQLException thrown = assertThrows(SQLException.class, mockedPs::execute);
        assertEquals("42S02", thrown.getSQLState());
    }
}
