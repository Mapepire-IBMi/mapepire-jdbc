package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConnectionTest extends MapepireTest {

    @BeforeAll
    static void registerDriver() throws SQLException {
        DriverManager.registerDriver(new MapepireDriver());
    }

    @Test
    void connectsToIbmiAndRunsSimpleQuery() throws SQLException {
        try (Connection connection = DriverManager.getConnection(getJdbcUrl());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT * FROM SYSIBM.SYSDUMMY1")) {
            assertTrue(rs.next());
            assertNotNull(rs.getString(1));
        }
    }

    @Test
    void connectsToServerSuccessfully() throws Exception {
        try (Connection connection = openConnection()) {
            assertNotNull(connection);
            assertTrue(connection.isValid(5));
        }
    }

    @Test
    void closedConnectionReportsIsClosed() throws Exception {
        Connection connection = openConnection();
        assertFalse(connection.isClosed());
        connection.close();
        assertTrue(connection.isClosed());
    }

    @Test
    void preparedStatementParameterBindingRoundTrip() throws Exception {
        try (Connection connection = openConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT ? FROM SYSIBM.SYSDUMMY1")) {
            ps.setString(1, "hello");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue("hello".equalsIgnoreCase(rs.getString(1)));
            }
        }
    }

    @Test
    void invalidSqlProducesSqlExceptionWithSqlState() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement()) {
            try {
                statement.executeQuery("SELECT * FROM QSYS2.TABLE_THAT_DOES_NOT_EXIST_XYZ");
                throw new AssertionError("Expected SQLException was not thrown");
            } catch (SQLException e) {
                assertNotNull(e.getSQLState(), "sqlState must not be null");
                assertFalse(e.getSQLState().isEmpty(), "sqlState must not be empty");
            }
        }
    }

    @Test
    void setSchemaRoundTrip() throws Exception {
        try (Connection connection = openConnection()) {
            connection.setSchema("QSYS2");
            String schema = connection.getSchema();
            assertNotNull(schema);
            assertTrue("QSYS2".equalsIgnoreCase(schema.trim()),
                    "Expected current schema to be QSYS2 but was: " + schema);
        }
    }

    @Test
    void setTransactionIsolationDoesNotThrow() throws Exception {
        try (Connection connection = openConnection()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            try (Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT * FROM SYSIBM.SYSDUMMY1")) {
                assertTrue(rs.next());
            }
        }
    }

    @Test
    void concurrentStatementsOnOneConnection() throws Exception {
        try (Connection connection = openConnection();
                Statement s1 = connection.createStatement();
                Statement s2 = connection.createStatement()) {
            try (ResultSet rs1 = s1.executeQuery("SELECT * FROM SYSIBM.SYSDUMMY1");
                    ResultSet rs2 = s2.executeQuery("SELECT * FROM SYSIBM.SYSDUMMY1")) {
                assertTrue(rs1.next());
                assertTrue(rs2.next());
            }
        }
    }

    @Test
    void resultSetColumnAccessByNameAndIndex() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT * FROM SYSIBM.SYSDUMMY1")) {
            assertTrue(rs.next());
            String byIndex = rs.getString(1);
            String byName = rs.getString("IBMREQD");
            assertNotNull(byIndex);
            assertNotNull(byName);
            assertTrue(byIndex.equalsIgnoreCase(byName),
                    "Column value by index and by name must match");
        }
    }
}
