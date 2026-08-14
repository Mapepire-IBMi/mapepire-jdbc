package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MapepireConnectionTest {

    private MapepireConnection connection;

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
}
