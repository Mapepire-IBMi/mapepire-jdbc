package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
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

import io.github.mapepire_ibmi.types.CLCommandResult;
import io.github.mapepire_ibmi.types.CLMessage;
import io.github.mapepire_ibmi.types.JobStatus;
import io.github.mapepire_ibmi.types.QueryResult;
import io.github.mapepire_ibmi.types.SqlCLCommandResult;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class MapepireConnectionTest {

    private MapepireConnection connection;

    @Mock
    private SqlJob mockJob;

    @BeforeEach
    void setUp() {
        // A connection over a job that was never started: enough to test all
        // client-side state handling without a real IBM i server.
        connection = new MapepireConnection(new SqlJob());
    }

    // -------------------------------------------------------------------------
    // auto-commit
    // -------------------------------------------------------------------------

    @Test
    void autoCommitDefaultsToTrue() throws SQLException {
        assertTrue(connection.getAutoCommit());
    }

    @Test
    void setAutoCommitFalseIsTracked() throws SQLException {
        connection.setAutoCommit(false);
        assertFalse(connection.getAutoCommit());
    }

    @Test
    void setAutoCommitWithSameValueIsANoOp() throws SQLException {
        // Setting true while already true must not trigger a COMMIT round trip
        // (which would fail here since the job is not connected)
        connection.setAutoCommit(true);
        assertTrue(connection.getAutoCommit());

        connection.setAutoCommit(false);
        connection.setAutoCommit(false);
        assertFalse(connection.getAutoCommit());
    }

    // -------------------------------------------------------------------------
    // isValid
    // -------------------------------------------------------------------------

    @Test
    void isValidReturnsTrueWhileJobIsNotEnded() throws SQLException {
        assertTrue(connection.isValid(0));
    }

    @Test
    void isValidThrowsForNegativeTimeout() {
        assertThrows(SQLException.class, () -> connection.isValid(-1));
    }

    // -------------------------------------------------------------------------
    // isClosed
    // -------------------------------------------------------------------------

    @Test
    void isClosedReturnsFalseWhileJobIsNotEnded() throws SQLException {
        assertFalse(connection.isClosed());
    }

    // -------------------------------------------------------------------------
    // unimplemented methods throw SQLFeatureNotSupportedException (SQLException)
    // -------------------------------------------------------------------------

    @Test
    void prepareCallThrowsSQLFeatureNotSupportedException() {
        assertThrows(SQLFeatureNotSupportedException.class, () -> connection.prepareCall("CALL FOO()"));
    }

    // -------------------------------------------------------------------------
    // commit / rollback / setSchema / setTransactionIsolation / getSchema
    // against a mocked SqlJob
    // -------------------------------------------------------------------------

    @Test
    void commitIssuesCommitSql() throws Exception {
        when(mockJob.<Object>execute("COMMIT")).thenReturn(CompletableFuture.completedFuture(null));

        try (MapepireConnection c = new MapepireConnection(mockJob)) {
            c.commit();
        }

        verify(mockJob).execute("COMMIT");
    }

    @Test
    void rollbackIssuesRollbackSql() throws Exception {
        when(mockJob.<Object>execute("ROLLBACK")).thenReturn(CompletableFuture.completedFuture(null));

        try (MapepireConnection c = new MapepireConnection(mockJob)) {
            c.rollback();
        }

        verify(mockJob).execute("ROLLBACK");
    }

    @Test
    void setSchemaIssuesSetSchemaSql() throws Exception {
        when(mockJob.<Object>execute("SET SCHEMA MYLIB")).thenReturn(CompletableFuture.completedFuture(null));

        try (MapepireConnection c = new MapepireConnection(mockJob)) {
            c.setSchema("MYLIB");
        }

        verify(mockJob).execute("SET SCHEMA MYLIB");
    }

    @Test
    void setTransactionIsolationIssuesExpectedSql() throws Exception {
        when(mockJob.<Object>execute("SET TRANSACTION ISOLATION LEVEL READ COMMITTED"))
                .thenReturn(CompletableFuture.completedFuture(null));

        try (MapepireConnection c = new MapepireConnection(mockJob)) {
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        }

        verify(mockJob).execute("SET TRANSACTION ISOLATION LEVEL READ COMMITTED");
    }

    @Test
    void getSchemaReturnsCurrentSchemaValue() throws Exception {
        QueryResult<Map<String, String>> result = mock(QueryResult.class);
        Map<String, String> row = new LinkedHashMap<>();
        row.put("CURRENT_SCHEMA", "MYLIB");
        when(result.getSuccess()).thenReturn(true);
        when(result.getData()).thenReturn(Collections.singletonList(row));

        when(mockJob.<Map<String, String>>execute("SELECT CURRENT SCHEMA FROM SYSIBM.SYSDUMMY1"))
                .thenReturn(CompletableFuture.completedFuture(result));

        try (MapepireConnection c = new MapepireConnection(mockJob)) {
            assertEquals("MYLIB", c.getSchema());
        }
    }

    @Test
    void commitPropagatesSqlStateFromFailedExecute() throws Exception {
        SQLException serverError = new SQLException("Commit failed", "40001");
        CompletableFuture<QueryResult<Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(serverError);
        when(mockJob.<Object>execute("COMMIT")).thenReturn(failed);

        try (MapepireConnection c = new MapepireConnection(mockJob)) {
            SQLException thrown = assertThrows(SQLException.class, c::commit);
            assertEquals("40001", thrown.getSQLState());
        }
    }

    // -------------------------------------------------------------------------
    // clCommand / rcclCommand against a mocked SqlJob
    // -------------------------------------------------------------------------

    @Test
    void clCommandReturnsServerResult() throws Exception {
        SqlCLCommandResult result = new SqlCLCommandResult("cl1", true, null, null, null, 5,
                Collections.singletonList(clMessage("CPC2102", "Library MYLIB created.")));
        when(mockJob.clCommand("CRTLIB MYLIB")).thenReturn(CompletableFuture.completedFuture(result));

        try (MapepireConnection c = new MapepireConnection(mockJob)) {
            assertSame(result, c.clCommand("CRTLIB MYLIB"));
        }
    }

    @Test
    void rcclCommandFailureWithoutMessagesReturnsEmptyData() throws Exception {
        CLCommandResult result = new CLCommandResult("rccl1", false, "Server error", 5, null);
        when(mockJob.rcclCommand("CRTLIB MYLIB")).thenReturn(CompletableFuture.completedFuture(result));

        try (MapepireConnection c = new MapepireConnection(mockJob)) {
            CLCommandResult returned = c.rcclCommand("CRTLIB MYLIB");
            assertFalse(returned.getSuccess());
            assertTrue(returned.getData().isEmpty());
        }
    }

    @Test
    void clCommandOnClosedConnectionThrows() throws Exception {
        when(mockJob.getStatus()).thenReturn(JobStatus.Ended);

        try (MapepireConnection c = new MapepireConnection(mockJob)) {
            SQLException thrown = assertThrows(SQLException.class, () -> c.clCommand("CRTLIB MYLIB"));
            assertEquals("Connection is closed", thrown.getMessage());
        }
    }

    @Test
    void unwrapReturnsTheConnectionItself() throws SQLException {
        assertTrue(connection.isWrapperFor(MapepireConnection.class));
        assertSame(connection, connection.unwrap(MapepireConnection.class));
    }

    private static CLMessage clMessage(String id, String text) {
        CLMessage message = new CLMessage();
        message.setId(id);
        message.setText(text);
        return message;
    }
}
