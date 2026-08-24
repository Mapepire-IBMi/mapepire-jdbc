package io.github.mapepire_ibmi;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;

@Timeout(25)
class MapepireTest {
    private static String host;
    private static String user;
    private static String password;
    private static int port;
    private static String configFile = "config.properties";

    @BeforeAll
    public static void beforeAll() throws Exception {
        setupCreds();
    }

    public static void setupCreds() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = MapepireTest.class.getClassLoader().getResourceAsStream(configFile)) {
            if (input == null) {
                throw new FileNotFoundException("Unable to find " + configFile);
            }
            properties.load(input);
        }

        List<String> keys = Arrays.asList("IBMI_HOST", "IBMI_USER", "IBMI_PASSWORD", "IBMI_PORT");
        Map<String, String> secrets = new HashMap<>();
        for (String key : keys) {
            String value = properties.getProperty(key);
            if (value == null || value.equals("")) {
                throw new ParseException(key + " not set in config.properties", 0);
            }
            secrets.put(key, value);
        }

        host = secrets.get("IBMI_HOST");
        user = secrets.get("IBMI_USER");
        password = secrets.get("IBMI_PASSWORD");
        port = Integer.parseInt(secrets.get("IBMI_PORT"));
    }

    public static String getJdbcUrl() {
        return "jdbc:mapepire://" + host + ":" + port + ";USER=" + user + ";PASSWORD=" + password;
    }

    public static String getHost() {
        return host;
    }

    public static String getUser() {
        return user;
    }

    public static String getPassword() {
        return password;
    }

    public static int getPort() {
        return port;
    }
}
