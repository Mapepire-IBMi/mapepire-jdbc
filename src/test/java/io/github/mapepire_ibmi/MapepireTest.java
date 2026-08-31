package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;

@Timeout(25)
class MapepireTest {
    private static String host;
    private static String user;
    private static String password;
    private static int port;
    private static boolean rejectUnauthorized;
    private static final String CONFIG_FILE = "config.properties";

    @BeforeAll
    static void setupCreds() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = MapepireTest.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            assumeTrue(input != null, CONFIG_FILE + " not found on classpath — skipping integration tests");
            properties.load(input);
        }

        host = properties.getProperty("IBMI_HOST", "").trim();
        user = properties.getProperty("IBMI_USER", "").trim();
        password = properties.getProperty("IBMI_PASSWORD", "").trim();
        String portStr = properties.getProperty("IBMI_PORT", "").trim();
        if (portStr.isEmpty()) {
            portStr = "8076";
        }
        String rejectUnauthorizedStr = properties.getProperty("REJECTUNAUTHORIZED", "true").trim();

        assumeTrue(!host.isEmpty(), "IBMI_HOST not set in " + CONFIG_FILE + " — skipping integration tests");
        assumeTrue(!user.isEmpty(), "IBMI_USER not set in " + CONFIG_FILE + " — skipping integration tests");
        assumeTrue(!password.isEmpty(), "IBMI_PASSWORD not set in " + CONFIG_FILE + " — skipping integration tests");

        port = Integer.parseInt(portStr);
        rejectUnauthorized = Boolean.parseBoolean(rejectUnauthorizedStr);
    }

    static String getJdbcUrl() {
        return "jdbc:mapepire://" + host + ":" + port + ";USER=" + user + ";PASSWORD=" + password
                + ";REJECTUNAUTHORIZED=" + rejectUnauthorized;
    }

    static Connection openConnection() throws Exception {
        DriverManager.registerDriver(new MapepireDriver());
        Properties p = new Properties();
        p.put("USER", user);
        p.put("PASSWORD", password);
        p.put("REJECTUNAUTHORIZED", String.valueOf(rejectUnauthorized));
        return DriverManager.getConnection("jdbc:mapepire://" + host + ":" + port, p);
    }

    static String getHost() {
        return host;
    }

    static String getUser() {
        return user;
    }

    static String getPassword() {
        return password;
    }

    static int getPort() {
        return port;
    }
}
