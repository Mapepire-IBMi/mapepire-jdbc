package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.mapepire_ibmi.types.ColumnMetadata;
import io.github.mapepire_ibmi.types.JobStatus;
import io.github.mapepire_ibmi.types.QueryMetadata;
import io.github.mapepire_ibmi.types.QueryOptions;
import io.github.mapepire_ibmi.types.QueryResult;

@ExtendWith(MockitoExtension.class)
class MapepireDatabaseMetaDataTest {

    @Mock
    private SqlJob mockJob;

    @Mock
    private Query mockQuery;

    private DatabaseMetaData md;

    @BeforeEach
    void setUp() {
        md = new MapepireDatabaseMetaData(new MapepireConnection(mockJob, "jdbc:mapepire://myhost:8076"));
    }

    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            row.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return row;
    }

    private static QueryMetadata metadata(String... columnNames) {
        ColumnMetadata[] columns = new ColumnMetadata[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            columns[i] = new ColumnMetadata(128, columnNames[i], columnNames[i], "VARCHAR", 128, 0, false,
                    ResultSetMetaData.columnNullable, false, false, null);
        }
        return new QueryMetadata(columns.length, Arrays.asList(columns), null, null);
    }

    private static QueryResult<Object> block(boolean done, QueryMetadata metadata, Object... rows) {
        QueryResult<Object> result = new QueryResult<>();
        result.setSuccess(true);
        result.setHasResults(true);
        result.setIsDone(done);
        result.setMetadata(metadata);
        result.setData(Arrays.asList(rows));
        return result;
    }

    /** Stub a single-block catalog query that returns the given rows. */
    private void stubQuery(Object... rows) throws Exception {
        when(mockJob.query(anyString(), any(QueryOptions.class))).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt()))
                .thenReturn(CompletableFuture.completedFuture(block(true, metadata("C1"), rows)));
    }

    private String capturedSql() {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(mockJob).query(sql.capture(), any(QueryOptions.class));
        return sql.getValue();
    }

    private QueryOptions capturedOptions() {
        ArgumentCaptor<QueryOptions> options = ArgumentCaptor.forClass(QueryOptions.class);
        verify(mockJob).query(anyString(), options.capture());
        return options.getValue();
    }

    // -------------------------------------------------------------------------
    // Query construction and parameter binding
    // -------------------------------------------------------------------------

    @Test
    void getTablesBindsPatternsAndTypesAsParameters() throws Exception {
        stubQuery();
        md.getTables("IHOST", "QSYS2", "SYS%", new String[] {"TABLE", "VIEW"}).close();

        String sql = capturedSql();
        assertTrue(sql.contains("FROM SYSIBM.SQLTABLES WHERE TABLE_CAT = ? AND TABLE_SCHEM LIKE ? ESCAPE '\\'"
                + " AND TABLE_NAME LIKE ? ESCAPE '\\' AND TABLE_TYPE IN (?, ?)"), sql);
        assertTrue(sql.endsWith(" ORDER BY TABLE_TYPE, TABLE_CAT, TABLE_SCHEM, TABLE_NAME"), sql);
        assertEquals(Arrays.asList("IHOST", "QSYS2", "SYS%", "TABLE", "VIEW"), capturedOptions().getParameters());
    }

    @Test
    void nullAndMatchAllFiltersAreOmitted() throws Exception {
        stubQuery();
        md.getTables(null, null, "%", null).close();

        assertFalse(capturedSql().contains(" WHERE "));
        // No parameters: the query runs unprepared
        assertNull(capturedOptions().getParameters());
    }

    @Test
    void namesAreNeverConcatenatedIntoSql() throws Exception {
        stubQuery();
        String hostile = "X' OR '1'='1";
        md.getColumns(null, hostile, null, null).close();

        assertFalse(capturedSql().contains(hostile));
        assertEquals(Collections.singletonList(hostile), capturedOptions().getParameters());
    }

    @Test
    void getColumnsSelectsJdbcDataTypeAndOrdersByPosition() throws Exception {
        stubQuery();
        md.getColumns(null, "QSYS2", "SQL_SIZING", null).close();

        String sql = capturedSql();
        // SQLCOLUMNS.DATA_TYPE holds ODBC codes; the JDBC code is in JDBC_DATA_TYPE
        assertTrue(sql.contains("JDBC_DATA_TYPE AS DATA_TYPE"), sql);
        assertTrue(sql.contains("AS IS_AUTOINCREMENT"), sql);
        assertTrue(sql.contains("AS IS_GENERATEDCOLUMN"), sql);
        assertTrue(sql.endsWith(" ORDER BY TABLE_CAT, TABLE_SCHEM, TABLE_NAME, ORDINAL_POSITION"), sql);
    }

    @Test
    void getPrimaryKeysUsesExactNameMatch() throws Exception {
        stubQuery();
        md.getPrimaryKeys(null, "QSYS2", "SQL_SIZING").close();

        String sql = capturedSql();
        assertTrue(sql.contains("FROM SYSIBM.SQLPRIMARYKEYS WHERE TABLE_SCHEM = ? AND TABLE_NAME = ?"), sql);
        assertTrue(sql.endsWith(" ORDER BY COLUMN_NAME"), sql);
    }

    @Test
    void getCrossReferenceFiltersBothSides() throws Exception {
        stubQuery();
        md.getCrossReference(null, "S", "PARENT", null, "S", "CHILD").close();

        String sql = capturedSql();
        assertTrue(sql.contains("WHERE PKTABLE_SCHEM = ? AND PKTABLE_NAME = ? AND FKTABLE_SCHEM = ?"
                + " AND FKTABLE_NAME = ?"), sql);
        assertEquals(Arrays.asList("S", "PARENT", "S", "CHILD"), capturedOptions().getParameters());
    }

    @Test
    void getIndexInfoUniqueKeepsStatisticsRow() throws Exception {
        stubQuery();
        md.getIndexInfo(null, "S", "T", true, false).close();

        assertTrue(capturedSql().contains("AND (NON_UNIQUE = 0 OR TYPE = 0)"));
    }

    @Test
    void getBestRowIdentifierExcludesNullableColumnsWhenAsked() throws Exception {
        stubQuery();
        md.getBestRowIdentifier(null, "S", "T", DatabaseMetaData.bestRowSession, false).close();

        assertTrue(capturedSql().contains("AND NULLABLE <> 1"));
    }

    @Test
    void getUdtsBindsTypeCodes() throws Exception {
        stubQuery();
        md.getUDTs(null, null, null, new int[] {Types.DISTINCT}).close();

        assertTrue(capturedSql().contains("WHERE DATA_TYPE IN (?)"));
        assertEquals(Collections.singletonList(Types.DISTINCT), capturedOptions().getParameters());
    }

    @Test
    void getSchemasOrdersByCatalogThenSchema() throws Exception {
        stubQuery();
        md.getSchemas(null, "Q%").close();

        String sql = capturedSql();
        assertTrue(sql.startsWith("SELECT TABLE_SCHEM, TABLE_CAT AS TABLE_CATALOG FROM SYSIBM.SQLSCHEMAS"), sql);
        assertTrue(sql.endsWith(" WHERE TABLE_SCHEM LIKE ? ESCAPE '\\' ORDER BY 2, 1"), sql);
    }

    // -------------------------------------------------------------------------
    // Result handling
    // -------------------------------------------------------------------------

    @Test
    void fetchesEveryBlockAndKeepsFirstBlockMetadata() throws Exception {
        when(mockJob.query(anyString(), any(QueryOptions.class))).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(CompletableFuture.completedFuture(
                block(false, metadata("TABLE_NAME"), row("TABLE_NAME", "A"), row("TABLE_NAME", "B"))));
        // Continuation blocks carry no metadata
        when(mockQuery.<Object>fetchMore(anyInt())).thenReturn(CompletableFuture.completedFuture(
                block(true, null, row("TABLE_NAME", "C"))));
        when(mockQuery.close()).thenReturn(CompletableFuture.completedFuture(null));

        try (ResultSet rs = md.getTables(null, "S", null, null)) {
            assertEquals("TABLE_NAME", rs.getMetaData().getColumnLabel(1));
            StringBuilder names = new StringBuilder();
            while (rs.next()) {
                names.append(rs.getString("TABLE_NAME"));
            }
            assertEquals("ABC", names.toString());
        }
        verify(mockQuery, times(1)).fetchMore(anyInt());
        verify(mockQuery).close();
    }

    @Test
    void queryIsClosedAndSqlStateKeptWhenFetchFails() throws Exception {
        CompletableFuture<QueryResult<Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new SQLException("Not authorized", "42501"));

        when(mockJob.query(anyString(), any(QueryOptions.class))).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt()))
                .thenReturn(CompletableFuture.completedFuture(block(false, metadata("C1"))));
        when(mockQuery.<Object>fetchMore(anyInt())).thenReturn(failed);
        when(mockQuery.close()).thenReturn(CompletableFuture.completedFuture(null));

        SQLException e = assertThrows(SQLException.class, () -> md.getTables(null, "S", null, null));
        assertEquals("42501", e.getSQLState());
        verify(mockQuery).close();
    }

    @Test
    void tableTypesAreAnsweredLocally() throws SQLException {
        try (ResultSet rs = md.getTableTypes()) {
            assertEquals("TABLE_TYPE", rs.getMetaData().getColumnName(1));
            StringBuilder types = new StringBuilder();
            while (rs.next()) {
                types.append(rs.getString(1)).append(';');
            }
            assertEquals("ALIAS;MATERIALIZED QUERY TABLE;SYSTEM TABLE;TABLE;VIEW;", types.toString());
        }
    }

    @Test
    void unsupportedCatalogFeaturesReturnEmptyResultsWithJdbcColumns() throws SQLException {
        try (ResultSet rs = md.getSuperTables(null, null, null)) {
            ResultSetMetaData rsmd = rs.getMetaData();
            assertEquals(4, rsmd.getColumnCount());
            assertEquals("SUPERTABLE_NAME", rsmd.getColumnName(4));
            assertFalse(rs.next());
        }
        try (ResultSet rs = md.getVersionColumns(null, "S", "T")) {
            assertEquals(Types.SMALLINT, rs.getMetaData().getColumnType(1));
            assertFalse(rs.next());
        }
    }

    // -------------------------------------------------------------------------
    // Product, driver, and version information
    // -------------------------------------------------------------------------

    @Test
    void productAndDriverInformation() throws SQLException {
        assertEquals("DB2 UDB for AS/400", md.getDatabaseProductName());
        assertEquals("Mapepire JDBC", md.getDriverName());
        assertEquals(MapepireDriver.MAJOR_VERSION + "." + MapepireDriver.MINOR_VERSION, md.getDriverVersion());
        assertEquals(new MapepireDriver().getMajorVersion(), md.getDriverMajorVersion());
        assertEquals(4, md.getJDBCMajorVersion());
    }

    @Test
    void urlIsTheSanitizedConnectionUrl() throws SQLException {
        assertEquals("jdbc:mapepire://myhost:8076", md.getURL());
        assertNull(new MapepireDatabaseMetaData(new MapepireConnection(mockJob)).getURL());
    }

    @Test
    void databaseVersionIsQueriedOnceAndFormatted() throws Exception {
        stubQuery(row("OS_VERSION", "7", "OS_RELEASE", "5"));

        assertEquals(7, md.getDatabaseMajorVersion());
        assertEquals(5, md.getDatabaseMinorVersion());
        assertEquals("07.05.0000 V7R5m0", md.getDatabaseProductVersion());
        verify(mockJob, times(1)).query(anyString(), any(QueryOptions.class));
    }

    // -------------------------------------------------------------------------
    // Capabilities that reflect what this driver implements
    // -------------------------------------------------------------------------

    @Test
    void onlyForwardOnlyReadOnlyResultSetsAreSupported() throws SQLException {
        assertTrue(md.supportsResultSetType(ResultSet.TYPE_FORWARD_ONLY));
        assertFalse(md.supportsResultSetType(ResultSet.TYPE_SCROLL_INSENSITIVE));
        assertTrue(md.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY));
        assertFalse(md.supportsResultSetConcurrency(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE));
    }

    @Test
    void identifierAndPatternConventions() throws SQLException {
        assertTrue(md.storesUpperCaseIdentifiers());
        assertEquals("\"", md.getIdentifierQuoteString());
        assertEquals("\\", md.getSearchStringEscape());
    }

    @Test
    void transactionIsolationLevelsMatchConnectionSupport() throws SQLException {
        assertTrue(md.supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE));
        assertTrue(md.supportsTransactionIsolationLevel(Connection.TRANSACTION_NONE));
        assertFalse(md.supportsTransactionIsolationLevel(99));
        assertFalse(md.supportsBatchUpdates());
        assertFalse(md.supportsSavepoints());
    }

    // -------------------------------------------------------------------------
    // Connection.getMetaData
    // -------------------------------------------------------------------------

    @Test
    void connectionReturnsSameMetaDataInstance() throws SQLException {
        when(mockJob.getStatus()).thenReturn(JobStatus.Ready);
        MapepireConnection connection = new MapepireConnection(mockJob);
        assertSame(connection.getMetaData(), connection.getMetaData());
        assertSame(connection, connection.getMetaData().getConnection());
    }

    @Test
    void connectionGetMetaDataThrowsWhenClosed() {
        when(mockJob.getStatus()).thenReturn(JobStatus.Ended);
        MapepireConnection connection = new MapepireConnection(mockJob);
        assertThrows(SQLException.class, connection::getMetaData);
    }
}
