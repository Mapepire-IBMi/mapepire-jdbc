package io.github.mapepire_ibmi;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

public final class App {
    public static void main(String[] args) {
        try {
            MapepireDriver mapepireDriver = new MapepireDriver();
            DriverManager.registerDriver(mapepireDriver);

            final Properties p = new Properties();
            p.put("USER", "USER");
            p.put("PASSWORD", "PASSWORD");
            p.put("naming", "system");
            p.put("errors", "full");

            Connection connection = DriverManager.getConnection("jdbc:mapepire://HOST:PORT", p);

            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("SELECT * FROM SAMPLE.DEPARTMENT");
            rs.next();
            String deptno = rs.getString(1);

            connection.close();
            System.out.println("Done");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
