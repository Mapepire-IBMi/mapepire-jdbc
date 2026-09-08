package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MapepireStatementTest {

    private MapepireStatement stmt;

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
}
