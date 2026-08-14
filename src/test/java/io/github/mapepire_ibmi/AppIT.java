package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AppIT {

    private static Map<String, String> env;

    // -------------------------------------------------------------------------
    // Setup — load .env once before all tests in this class
    // -------------------------------------------------------------------------

    @BeforeAll
    static void loadEnv() throws IOException {
        File envFile = new File(".env");

        // If .env doesn't exist, skip every test in this class gracefully.
        // This means the normal unit test suite never breaks on machines that
        // don't have IBM i credentials.
        assumeTrue(envFile.exists(), ".env file not found — skipping integration tests");

        env = new HashMap<>();
        for (String line : Files.readAllLines(envFile.toPath())) {
            // skip blank lines and comments
            if (line.trim().isEmpty() || line.startsWith("#")) {
                continue;
            }
            // split only on the first "=" so passwords with "=" in them still work
            int idx = line.indexOf('=');
            if (idx > 0) {
                env.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
            }
        }

        // Also skip if the required keys are missing from .env
        assumeTrue(env.containsKey("MAPEPIRE_HOST"), "MAPEPIRE_HOST not set in .env");
        assumeTrue(env.containsKey("MAPEPIRE_USER"), "MAPEPIRE_USER not set in .env");
        assumeTrue(env.containsKey("MAPEPIRE_PASSWORD"), "MAPEPIRE_PASSWORD not set in .env");
    }

    // -------------------------------------------------------------------------
    // Helper — build a connection URL from the loaded .env values
    // -------------------------------------------------------------------------

    private Connection openConnection() throws Exception {
        DriverManager.registerDriver(new MapepireDriver());

        String host = env.get("MAPEPIRE_HOST");

        // Append REJECTUNAUTHORIZED=false if requested in .env
        if ("false".equalsIgnoreCase(env.get("MAPEPIRE_REJECTUNAUTHORIZED"))) {
            host = host + ";REJECTUNAUTHORIZED=false";
        }

        Properties p = new Properties();
        p.put("USER", env.get("MAPEPIRE_USER"));
        p.put("PASSWORD", env.get("MAPEPIRE_PASSWORD"));

        return DriverManager.getConnection("jdbc:mapepire://" + host, p);
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void connectsToServerSuccessfully() throws Exception {
        try (Connection connection = openConnection()) {
            assertNotNull(connection);
            assertTrue(connection.isValid(5));
        }
    }

    @Test
    void queryDepartmentTableReturnsData() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT * FROM SAMPLE.DEPARTMENT")) {

            // There must be at least one row
            assertTrue(rs.next(), "Expected at least one row in SAMPLE.DEPARTMENT");

            // The first column (DEPTNO) must not be null
            String deptno = rs.getString(1);
            assertNotNull(deptno, "DEPTNO should not be null");
            System.out.println("First department number from real server: " + deptno);
        }
    }
}
