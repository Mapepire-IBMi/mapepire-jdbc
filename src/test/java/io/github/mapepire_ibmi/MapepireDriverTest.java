package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MapepireDriverTest {

    private MapepireDriver driver;

    @BeforeEach
    void setUp() {
        driver = new MapepireDriver();
    }

    // -------------------------------------------------------------------------
    // acceptsURL — valid URLs
    // -------------------------------------------------------------------------

    @Test
    void acceptsUrlWithHostAndPort() throws SQLException {
        assertTrue(driver.acceptsURL("jdbc:mapepire://myhost.example.com:8076"));
    }

    @Test
    void acceptsUrlWithHostPortAndProperties() throws SQLException {
        assertTrue(driver.acceptsURL("jdbc:mapepire://myhost.example.com:8076;USER=bob;PASSWORD=secret"));
    }

    @Test
    void acceptsUrlIsCaseInsensitive() throws SQLException {
        // The (?i) flag in the regex makes jdbc:MAPEPIRE:// valid too
        assertTrue(driver.acceptsURL("JDBC:MAPEPIRE://myhost.example.com:8076"));
    }

    @Test
    void acceptsUrlWithIpAddress() throws SQLException {
        assertTrue(driver.acceptsURL("jdbc:mapepire://192.168.1.100:8076"));
    }

    @Test
    void acceptsUrlWithoutPort() throws SQLException {
        // The port is optional — it defaults to 8076
        assertTrue(driver.acceptsURL("jdbc:mapepire://myhost.example.com"));
    }

    @Test
    void acceptsUrlWithoutPortButWithProperties() throws SQLException {
        assertTrue(driver.acceptsURL("jdbc:mapepire://myhost.example.com;USER=bob;PASSWORD=secret"));
    }

    @Test
    void acceptsUrlWithAdditionalJdbcProperties() throws SQLException {
        assertTrue(driver.acceptsURL("jdbc:mapepire://myhost.example.com:8076;naming=system;errors=full"));
    }

    // -------------------------------------------------------------------------
    // acceptsURL — invalid URLs
    // -------------------------------------------------------------------------

    @Test
    void rejectsNullUrl() throws SQLException {
        assertFalse(driver.acceptsURL(null));
    }

    @Test
    void rejectsEmptyUrl() throws SQLException {
        assertFalse(driver.acceptsURL(""));
    }

    @Test
    void rejectsWrongScheme() throws SQLException {
        // Should not accept a standard JDBC URL for another database
        assertFalse(driver.acceptsURL("jdbc:postgresql://localhost:5432/mydb"));
    }

    @Test
    void rejectsUrlWithoutHost() throws SQLException {
        assertFalse(driver.acceptsURL("jdbc:mapepire://"));
    }

    @Test
    void rejectsPlainString() throws SQLException {
        assertFalse(driver.acceptsURL("not-a-url-at-all"));
    }

    // -------------------------------------------------------------------------
    // version
    // -------------------------------------------------------------------------

    @Test
    void majorVersionIsOne() {
        assertEquals(1, driver.getMajorVersion());
    }

    @Test
    void minorVersionIsZero() {
        assertEquals(0, driver.getMinorVersion());
    }

    @Test
    void driverJdbcCompliantReturnsFalse() {
        // Not fully compliant yet — must stay false until all JDBC API is implemented
        assertFalse(driver.jdbcCompliant());
    }

}
