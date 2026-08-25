package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

class AppIT extends MapepireTest {

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

            assertTrue(rs.next(), "Expected at least one row in SAMPLE.DEPARTMENT");

            String deptno = rs.getString(1);
            assertNotNull(deptno, "DEPTNO should not be null");
            System.out.println("First department number: " + deptno);
        }
    }
}
