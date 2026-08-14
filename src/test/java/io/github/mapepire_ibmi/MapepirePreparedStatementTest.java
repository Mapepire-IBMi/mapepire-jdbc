package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MapepirePreparedStatementTest {

    private MapepireConnection connection;
    private MapepirePreparedStatement ps;

    @BeforeEach
    void setUp() {
        connection = new MapepireConnection(new SqlJob());
        ps = new MapepirePreparedStatement(connection, "SELECT * FROM T WHERE A = ? AND B = ?");
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
}
