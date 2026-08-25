package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
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
}
