package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import io.github.mapepire_ibmi.types.QueryResult;

class MapepireStatementQueryTimeoutTest {

    // A future that never completes stands in for a request whose response the
    // server never sends, e.g. because the server has hung.
    private static MapepireStatement statementBackedByHungServer() throws Exception {
        SqlJob job = mock(SqlJob.class);
        Query query = mock(Query.class);
        when(job.query(anyString())).thenReturn(query);

        CompletableFuture<QueryResult<Object>> neverCompletes = new CompletableFuture<>();
        when(query.<Object>execute(anyInt())).thenReturn(neverCompletes);

        return new MapepireStatement(new MapepireConnection(job));
    }

    @Test
    void queryTimeoutDefaultsToZeroMeaningNoTimeout() throws SQLException {
        MapepireStatement stmt = new MapepireStatement(new MapepireConnection(new SqlJob()));
        assertEquals(0, stmt.getQueryTimeout());
    }

    @Test
    void setQueryTimeoutStoresValue() throws SQLException {
        MapepireStatement stmt = new MapepireStatement(new MapepireConnection(new SqlJob()));
        stmt.setQueryTimeout(5);
        assertEquals(5, stmt.getQueryTimeout());
    }

    @Test
    void setQueryTimeoutThrowsForNegativeValue() throws SQLException {
        MapepireStatement stmt = new MapepireStatement(new MapepireConnection(new SqlJob()));
        assertThrows(SQLException.class, () -> stmt.setQueryTimeout(-1));
    }

    @Test
    void executeQueryThrowsRatherThanHangingWhenServerNeverResponds() throws Exception {
        MapepireStatement stmt = statementBackedByHungServer();
        stmt.setQueryTimeout(1);

        SQLException e = assertThrows(SQLException.class,
                () -> stmt.executeQuery("SELECT 1 FROM SYSIBM.SYSDUMMY1"));
        assertInstanceOf(TimeoutException.class, e.getCause());
    }

    @Test
    void executeUpdateThrowsRatherThanHangingWhenServerNeverResponds() throws Exception {
        MapepireStatement stmt = statementBackedByHungServer();
        stmt.setQueryTimeout(1);

        SQLException e = assertThrows(SQLException.class,
                () -> stmt.executeUpdate("DELETE FROM T"));
        assertInstanceOf(TimeoutException.class, e.getCause());
    }
}
