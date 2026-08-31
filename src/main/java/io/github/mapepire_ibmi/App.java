package io.github.mapepire_ibmi;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class App {
    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    public static void main(String[] args) {
        String host = System.getenv("MAPEPIRE_HOST");
        String user = System.getenv("MAPEPIRE_USER");
        String password = System.getenv("MAPEPIRE_PASSWORD");

        if (host == null || user == null || password == null) {
            System.out.println("Usage: set MAPEPIRE_HOST, MAPEPIRE_USER, and MAPEPIRE_PASSWORD "
                    + "environment variables before running this sample.");
            System.out.println("Example: MAPEPIRE_HOST=myhost.example.com:8076 MAPEPIRE_USER=myuser "
                    + "MAPEPIRE_PASSWORD=mypassword mvn exec:java -Dexec.mainClass=io.github.mapepire_ibmi.App");
            return;
        }

        try {
            MapepireDriver mapepireDriver = new MapepireDriver();
            DriverManager.registerDriver(mapepireDriver);

            final Properties p = new Properties();
            p.put("USER", user);
            p.put("PASSWORD", password);
            p.put("naming", "system");
            p.put("errors", "full");

            try (Connection connection = DriverManager.getConnection("jdbc:mapepire://" + host, p);
                    Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT * FROM SAMPLE.DEPARTMENT")) {
                rs.next();
                String deptno = rs.getString(1);
                System.out.println("First department number: " + deptno);
            }

            System.out.println("Done");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to run sample query", e);
        }
    }
}
