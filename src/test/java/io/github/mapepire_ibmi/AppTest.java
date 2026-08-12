package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

class AppTest {

    @Test
    void driverVersionIsCorrect() {
        MapepireDriver driver = new MapepireDriver();
        assertEquals(1, driver.getMajorVersion());
        assertEquals(0, driver.getMinorVersion());
    }

    @Test
    void driverAcceptsValidMapepireUrl() throws SQLException {
        MapepireDriver driver = new MapepireDriver();
        assertTrue(driver.acceptsURL("jdbc:mapepire://myhost.example.com:8076"));
    }

    @Test
    void driverRejectsNonMapepireUrl() throws SQLException {
        MapepireDriver driver = new MapepireDriver();
        assertFalse(driver.acceptsURL("jdbc:postgresql://localhost:5432/db"));
    }

    @Test
    void driverJdbcCompliantReturnsFalse() {
        // Not fully compliant yet — must stay false until all JDBC API is implemented
        MapepireDriver driver = new MapepireDriver();
        assertFalse(driver.jdbcCompliant());
    }
}
