package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Integration tests for DatabaseMetaData and ResultSetMetaData against a live
 * IBM i. They only read system catalog objects that exist on every IBM i:
 * QSYS2.SQL_SIZING (a system table with a primary key) and the QSYS2 text
 * search catalog tables (foreign keys and an identity column).
 */
class DatabaseMetaDataTest extends MapepireTest {

    private static List<String> columnLabels(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        List<String> labels = new ArrayList<>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            labels.add(md.getColumnLabel(i));
        }
        return labels;
    }

    // -------------------------------------------------------------------------
    // Product, version, user
    // -------------------------------------------------------------------------

    @Test
    void reportsProductVersionAndUser() throws Exception {
        try (Connection connection = openConnection()) {
            DatabaseMetaData md = connection.getMetaData();
            assertEquals("DB2 UDB for AS/400", md.getDatabaseProductName());
            assertTrue(md.getDatabaseMajorVersion() >= 7);
            String expectedVersion = String.format("V%dR%dm0", md.getDatabaseMajorVersion(),
                    md.getDatabaseMinorVersion());
            assertTrue(md.getDatabaseProductVersion().endsWith(expectedVersion), md.getDatabaseProductVersion());
            assertTrue(getUser().equalsIgnoreCase(md.getUserName()));
            assertEquals("jdbc:mapepire://" + getHost() + ":" + getPort(), md.getURL());
        }
    }

    // -------------------------------------------------------------------------
    // Catalogs and tables
    // -------------------------------------------------------------------------

    @Test
    void getTablesReturnsJdbcColumnsAndHonoursFilters() throws Exception {
        try (Connection connection = openConnection()) {
            DatabaseMetaData md = connection.getMetaData();

            String catalog;
            try (ResultSet rs = md.getCatalogs()) {
                assertTrue(rs.next());
                catalog = rs.getString("TABLE_CAT");
                assertFalse(rs.next());
            }

            // "\_" escapes the LIKE wildcard so only SQL_SIZING matches
            try (ResultSet rs = md.getTables(null, "QSYS2", "SQL\\_SIZING", null)) {
                assertEquals(Arrays.asList("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "TABLE_TYPE", "REMARKS",
                        "TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "SELF_REFERENCING_COL_NAME", "REF_GENERATION"),
                        columnLabels(rs));
                assertTrue(rs.next());
                assertEquals(catalog, rs.getString("TABLE_CAT"));
                assertEquals("QSYS2", rs.getString("TABLE_SCHEM"));
                assertEquals("SQL_SIZING", rs.getString("TABLE_NAME"));
                assertEquals("SYSTEM TABLE", rs.getString("TABLE_TYPE"));
                assertFalse(rs.next());
            }

            try (ResultSet rs = md.getTables(null, "QSYS2", "SQL\\_SIZING", new String[] {"VIEW"})) {
                assertFalse(rs.next());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Columns
    // -------------------------------------------------------------------------

    @Test
    void getColumnsReturnsColumnsInOrdinalOrder() throws Exception {
        try (Connection connection = openConnection();
                ResultSet rs = connection.getMetaData().getColumns(null, "QSYS2", "SQL_SIZING", null)) {
            assertEquals(24, rs.getMetaData().getColumnCount());
            assertEquals("IS_GENERATEDCOLUMN", rs.getMetaData().getColumnLabel(24));

            assertTrue(rs.next());
            assertEquals("SIZING_ID", rs.getString("COLUMN_NAME"));
            assertEquals(Types.INTEGER, rs.getInt("DATA_TYPE"));
            assertEquals("INTEGER", rs.getString("TYPE_NAME"));
            assertEquals(1, rs.getInt("ORDINAL_POSITION"));
            assertEquals("NO", rs.getString("IS_NULLABLE"));
            assertEquals("NO", rs.getString("IS_AUTOINCREMENT"));

            int previous = 1;
            while (rs.next()) {
                int position = rs.getInt("ORDINAL_POSITION");
                assertTrue(position > previous);
                previous = position;
            }
        }
    }

    @Test
    void getColumnsFlagsIdentityColumnAsAutoIncrement() throws Exception {
        try (Connection connection = openConnection();
                ResultSet rs = connection.getMetaData().getColumns(null, "QSYS2", "SYSTEXTINDEXES", "INDEXID")) {
            assertTrue(rs.next());
            assertEquals("YES", rs.getString("IS_AUTOINCREMENT"));
            assertEquals("NO", rs.getString("IS_GENERATEDCOLUMN"));
        }
    }

    @Test
    void getColumnsReturnsEveryRowNotJustTheFirstBlock() throws Exception {
        try (Connection connection = openConnection()) {
            int expected;
            try (Statement statement = connection.createStatement();
                    ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM SYSIBM.SQLCOLUMNS"
                            + " WHERE TABLE_SCHEM = 'QSYS2' AND TABLE_NAME LIKE 'SYS%'")) {
                assertTrue(rs.next());
                expected = rs.getInt(1);
            }
            // Large enough to need several server round trips
            assertTrue(expected > 1000, "expected more than one fetch block, got " + expected);

            int actual = 0;
            try (ResultSet rs = connection.getMetaData().getColumns(null, "QSYS2", "SYS%", null)) {
                while (rs.next()) {
                    actual++;
                }
            }
            assertEquals(expected, actual);
        }
    }

    // -------------------------------------------------------------------------
    // Keys and indexes
    // -------------------------------------------------------------------------

    @Test
    void getPrimaryKeysReturnsKeyColumns() throws Exception {
        try (Connection connection = openConnection();
                ResultSet rs = connection.getMetaData().getPrimaryKeys(null, "QSYS2", "SQL_SIZING")) {
            assertEquals(Arrays.asList("TABLE_CAT", "TABLE_SCHEM", "TABLE_NAME", "COLUMN_NAME", "KEY_SEQ", "PK_NAME"),
                    columnLabels(rs));
            assertTrue(rs.next());
            assertEquals("SIZING_ID", rs.getString("COLUMN_NAME"));
            assertEquals(1, rs.getInt("KEY_SEQ"));
            assertNotNull(rs.getString("PK_NAME"));
            assertFalse(rs.next());
        }
    }

    @Test
    void foreignKeysAreReportedFromBothSides() throws Exception {
        try (Connection connection = openConnection()) {
            DatabaseMetaData md = connection.getMetaData();

            List<String> parents = new ArrayList<>();
            try (ResultSet rs = md.getImportedKeys(null, "QSYS2", "SYSTEXTINDEXES")) {
                assertEquals(14, rs.getMetaData().getColumnCount());
                while (rs.next()) {
                    assertEquals("SYSTEXTINDEXES", rs.getString("FKTABLE_NAME"));
                    parents.add(rs.getString("PKTABLE_NAME"));
                }
            }
            assertTrue(parents.contains("SYSTEXTSERVERS"), parents.toString());

            List<String> children = new ArrayList<>();
            try (ResultSet rs = md.getExportedKeys(null, "QSYS2", "SYSTEXTINDEXES")) {
                while (rs.next()) {
                    assertEquals("SYSTEXTINDEXES", rs.getString("PKTABLE_NAME"));
                    children.add(rs.getString("FKTABLE_NAME"));
                }
            }
            assertTrue(children.contains("SYSTEXTCOLUMNS"), children.toString());

            try (ResultSet rs = md.getCrossReference(null, "QSYS2", "SYSTEXTINDEXES", null, "QSYS2",
                    "SYSTEXTCOLUMNS")) {
                assertTrue(rs.next());
                assertEquals("INDEXID", rs.getString("PKCOLUMN_NAME"));
                assertEquals("INDEXID", rs.getString("FKCOLUMN_NAME"));
                assertEquals(DatabaseMetaData.importedKeyNotDeferrable, rs.getInt("DEFERRABILITY"));
            }
        }
    }

    @Test
    void getIndexInfoReturnsUniqueKeyIndex() throws Exception {
        try (Connection connection = openConnection();
                ResultSet rs = connection.getMetaData().getIndexInfo(null, "QSYS2", "SQL_SIZING", true, true)) {
            assertEquals(13, rs.getMetaData().getColumnCount());
            boolean found = false;
            while (rs.next()) {
                if ("SIZING_ID".equals(rs.getString("COLUMN_NAME"))) {
                    assertFalse(rs.getBoolean("NON_UNIQUE"));
                    found = true;
                }
            }
            assertTrue(found);
        }
    }

    // -------------------------------------------------------------------------
    // Routines and types
    // -------------------------------------------------------------------------

    @Test
    void getProceduresAndProcedureColumns() throws Exception {
        try (Connection connection = openConnection()) {
            DatabaseMetaData md = connection.getMetaData();
            try (ResultSet rs = md.getProcedures(null, "QSYS2", "QCMDEXC")) {
                assertEquals(9, rs.getMetaData().getColumnCount());
                assertTrue(rs.next());
                assertEquals("QCMDEXC", rs.getString("PROCEDURE_NAME"));
            }
            try (ResultSet rs = md.getProcedureColumns(null, "QSYS2", "QCMDEXC", null)) {
                assertEquals("PRECISION", rs.getMetaData().getColumnLabel(8));
                assertTrue(rs.next());
                assertEquals(DatabaseMetaData.procedureColumnIn, rs.getInt("COLUMN_TYPE"));
                assertEquals(Types.VARCHAR, rs.getInt("DATA_TYPE"));
            }
        }
    }

    @Test
    void getFunctionsAndFunctionColumns() throws Exception {
        try (Connection connection = openConnection()) {
            DatabaseMetaData md = connection.getMetaData();
            try (ResultSet rs = md.getFunctions(null, "QSYS2", "DELIMIT_NAME")) {
                assertTrue(rs.next());
                assertEquals("DELIMIT_NAME", rs.getString("FUNCTION_NAME"));
            }
            try (ResultSet rs = md.getFunctionColumns(null, "QSYS2", "DELIMIT_NAME", null)) {
                // The return value comes first (ordinal 0), then the parameters
                assertTrue(rs.next());
                assertEquals(DatabaseMetaData.functionReturn, rs.getInt("COLUMN_TYPE"));
                assertEquals(0, rs.getInt("ORDINAL_POSITION"));
                assertTrue(rs.next());
                assertEquals(DatabaseMetaData.functionColumnIn, rs.getInt("COLUMN_TYPE"));
            }
        }
    }

    @Test
    void getTypeInfoIsOrderedByDataType() throws Exception {
        try (Connection connection = openConnection();
                ResultSet rs = connection.getMetaData().getTypeInfo()) {
            assertEquals(18, rs.getMetaData().getColumnCount());
            boolean sawInteger = false;
            int previous = Integer.MIN_VALUE;
            while (rs.next()) {
                int dataType = rs.getInt("DATA_TYPE");
                assertTrue(dataType >= previous);
                previous = dataType;
                if ("INTEGER".equals(rs.getString("TYPE_NAME"))) {
                    assertEquals(Types.INTEGER, dataType);
                    sawInteger = true;
                }
            }
            assertTrue(sawInteger);
        }
    }

    // -------------------------------------------------------------------------
    // ResultSetMetaData for ordinary queries
    // -------------------------------------------------------------------------

    @Test
    void resultSetMetaDataDescribesQueryColumns() throws Exception {
        try (Connection connection = openConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT INTEGER(1) AS ID, DECIMAL(1.5, 9, 2) AS AMOUNT,"
                        + " CAST('x' AS VARCHAR(10)) AS NAME, CAST('ab' AS CHAR(2) FOR BIT DATA) AS FLAGS"
                        + " FROM SYSIBM.SYSDUMMY1 WHERE 1 = 0")) {
            // Available even though the result has no rows
            ResultSetMetaData md = rs.getMetaData();
            assertEquals(4, md.getColumnCount());

            assertEquals("ID", md.getColumnLabel(1));
            assertEquals(Types.INTEGER, md.getColumnType(1));
            assertTrue(md.isSigned(1));

            assertEquals(Types.DECIMAL, md.getColumnType(2));
            assertEquals(9, md.getPrecision(2));
            assertEquals(2, md.getScale(2));
            assertEquals("java.math.BigDecimal", md.getColumnClassName(2));

            assertEquals(Types.VARCHAR, md.getColumnType(3));
            assertEquals(10, md.getPrecision(3));
            assertTrue(md.isCaseSensitive(3));

            assertEquals(Types.BINARY, md.getColumnType(4));
            assertFalse(rs.next());
        }
    }

    @Test
    void preparedStatementMetaDataAvailableAfterExecution() throws Exception {
        try (Connection connection = openConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "SELECT TABLE_NAME FROM QSYS2.SYSTABLES WHERE TABLE_SCHEMA = ? FETCH FIRST 1 ROW ONLY")) {
            assertNull(ps.getMetaData());
            ps.setString(1, "QSYS2");
            ps.executeQuery().close();
            assertEquals("TABLE_NAME", ps.getMetaData().getColumnName(1));
            assertEquals(Types.VARCHAR, ps.getMetaData().getColumnType(1));
        }
    }
}
