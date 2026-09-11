package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.mapepire_ibmi.types.QueryResult;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class MapepireStatementTest {

    private MapepireStatement stmt;

    @Mock
    private SqlJob mockJob;

    @Mock
    private Query mockQuery;

    @BeforeEach
    void setUp() {
        // A statement over a connection whose job was never started. This is
        // enough to exercise all the state handling that happens before any
        // SQL is sent to a server.
        stmt = new MapepireStatement(new MapepireConnection(new SqlJob()));
    }

    // -------------------------------------------------------------------------
    // isClosed / close
    // -------------------------------------------------------------------------

    @Test
    void isClosedReturnsFalseBeforeClose() throws SQLException {
        assertFalse(stmt.isClosed());
    }

    @Test
    void isClosedReturnsTrueAfterClose() throws SQLException {
        stmt.close();
        assertTrue(stmt.isClosed());
    }

    @Test
    void closeBeforeAnyExecutionDoesNotThrow() throws SQLException {
        // query is still null here — close() must not NPE
        stmt.close();
    }

    @Test
    void closeIsIdempotent() throws SQLException {
        stmt.close();
        stmt.close();
        assertTrue(stmt.isClosed());
    }

    @Test
    void executeQueryThrowsOnClosedStatement() throws SQLException {
        stmt.close();
        assertThrows(SQLException.class, () -> stmt.executeQuery("SELECT 1 FROM SYSIBM.SYSDUMMY1"));
    }

    @Test
    void executeUpdateThrowsOnClosedStatement() throws SQLException {
        stmt.close();
        assertThrows(SQLException.class, () -> stmt.executeUpdate("DELETE FROM T"));
    }

    // -------------------------------------------------------------------------
    // getResultSet / getUpdateCount / getMoreResults before execution
    // -------------------------------------------------------------------------

    @Test
    void getResultSetThrowsClearErrorBeforeExecution() {
        SQLException e = assertThrows(SQLException.class, () -> stmt.getResultSet());
        assertTrue(e.getMessage().contains("has not been executed"));
    }

    @Test
    void getUpdateCountThrowsClearErrorBeforeExecution() {
        SQLException e = assertThrows(SQLException.class, () -> stmt.getUpdateCount());
        assertTrue(e.getMessage().contains("has not been executed"));
    }

    @Test
    void getMoreResultsThrowsClearErrorBeforeExecution() {
        SQLException e = assertThrows(SQLException.class, () -> stmt.getMoreResults());
        assertTrue(e.getMessage().contains("has not been executed"));
    }

    // -------------------------------------------------------------------------
    // fetch size
    // -------------------------------------------------------------------------

    @Test
    void fetchSizeDefaultsToNonZero() throws SQLException {
        // A default of 0 silently breaks pagination (fetchMore(0) returns nothing)
        assertEquals(100, stmt.getFetchSize());
    }

    @Test
    void setFetchSizeStoresValue() throws SQLException {
        stmt.setFetchSize(250);
        assertEquals(250, stmt.getFetchSize());
    }

    @Test
    void setFetchSizeZeroResetsToDefault() throws SQLException {
        stmt.setFetchSize(250);
        stmt.setFetchSize(0);
        assertEquals(100, stmt.getFetchSize());
    }

    @Test
    void setFetchSizeThrowsForNegativeValue() {
        assertThrows(SQLException.class, () -> stmt.setFetchSize(-1));
    }

    // -------------------------------------------------------------------------
    // getConnection
    // -------------------------------------------------------------------------

    @Test
    void getConnectionReturnsOwningConnection() throws SQLException {
        MapepireConnection conn = new MapepireConnection(new SqlJob());
        try (MapepireStatement s = new MapepireStatement(conn)) {
            assertEquals(conn, s.getConnection());
        }
    }

    // -------------------------------------------------------------------------
    // unimplemented methods throw SQLFeatureNotSupportedException (SQLException)
    // -------------------------------------------------------------------------

    @Test
    void unimplementedMethodsThrowSQLFeatureNotSupportedException() {
        assertThrows(SQLFeatureNotSupportedException.class, () -> stmt.cancel());
    }

    // -------------------------------------------------------------------------
    // executeQuery / executeUpdate / execute against a mocked SqlJob and Query
    // -------------------------------------------------------------------------

    @Test
    void executeQuerySuccessReturnsResultSetBackedByQueryData() throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("COL1", "value");

        QueryResult<Object> result = mock(QueryResult.class);
        when(result.getData()).thenReturn(Collections.singletonList(row));

        when(mockJob.query("SELECT 1")).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(CompletableFuture.completedFuture(result));

        MapepireStatement s = new MapepireStatement(new MapepireConnection(mockJob));
        try (ResultSet rs = s.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
            assertEquals("value", rs.getString(1));
        }
    }

    @Test
    void executeUpdateSuccessReturnsUpdateCount() throws Exception {
        QueryResult<Object> result = mock(QueryResult.class);
        when(result.getUpdateCount()).thenReturn(5);

        when(mockJob.query("DELETE FROM T")).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(CompletableFuture.completedFuture(result));

        MapepireStatement s = new MapepireStatement(new MapepireConnection(mockJob));
        assertEquals(5, s.executeUpdate("DELETE FROM T"));
    }

    @Test
    void executeSuccessReturnsHasResultsFlag() throws Exception {
        QueryResult<Object> result = mock(QueryResult.class);
        when(result.getHasResults()).thenReturn(true);

        when(mockJob.query("SELECT 1")).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(CompletableFuture.completedFuture(result));

        MapepireStatement s = new MapepireStatement(new MapepireConnection(mockJob));
        assertTrue(s.execute("SELECT 1"));
    }

    @Test
    void executeQueryPropagatesSqlStateFromFailedQuery() throws Exception {
        SQLException serverError = new SQLException("Syntax error", "42601");
        CompletableFuture<QueryResult<Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(serverError);

        when(mockJob.query("SELECT BOGUS")).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(failed);

        MapepireStatement s = new MapepireStatement(new MapepireConnection(mockJob));
        SQLException thrown = assertThrows(SQLException.class, () -> s.executeQuery("SELECT BOGUS"));
        assertEquals("42601", thrown.getSQLState());
    }

    // -------------------------------------------------------------------------
    // getMoreResults pagination against a mocked Query
    // -------------------------------------------------------------------------

    @Test
    void getMoreResultsDrivesFetchMoreUntilDone() throws Exception {
        QueryResult<Object> firstPage = mock(QueryResult.class);
        when(firstPage.getData()).thenReturn(Collections.emptyList());
        when(firstPage.getIsDone()).thenReturn(false);

        QueryResult<Object> secondPage = mock(QueryResult.class);
        when(secondPage.getIsDone()).thenReturn(true);
        when(secondPage.getHasResults()).thenReturn(true);

        when(mockJob.query("SELECT * FROM BIG_TABLE")).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(CompletableFuture.completedFuture(firstPage));
        when(mockQuery.<Object>fetchMore(anyInt())).thenReturn(CompletableFuture.completedFuture(secondPage));

        MapepireStatement s = new MapepireStatement(new MapepireConnection(mockJob));
        s.executeQuery("SELECT * FROM BIG_TABLE");

        // First page is not done: getMoreResults must fetch another page
        assertTrue(s.getMoreResults());
        verify(mockQuery, times(1)).fetchMore(100);

        // Second page is done: getMoreResults must not fetch again
        assertFalse(s.getMoreResults());
        verify(mockQuery, times(1)).fetchMore(anyInt());
    }
}
