package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.BatchUpdateException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.mapepire_ibmi.types.ColumnMetadata;
import io.github.mapepire_ibmi.types.QueryMetadata;
import io.github.mapepire_ibmi.types.QueryOptions;
import io.github.mapepire_ibmi.types.QueryResult;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class MapepirePreparedStatementTest {

    private MapepireConnection connection;
    private MapepirePreparedStatement ps;

    @Mock
    private SqlJob mockJob;

    @Mock
    private Query mockQuery;

    @BeforeEach
    void setUp() {
        connection = new MapepireConnection(new SqlJob());
        ps = new MapepirePreparedStatement(connection, "SELECT * FROM T WHERE A = ? AND B = ?");
    }

    private MapepirePreparedStatement preparedStatementWithMockJob(String sql) {
        return new MapepirePreparedStatement(new MapepireConnection(mockJob), sql);
    }

    // -------------------------------------------------------------------------
    // construction via Connection.prepareStatement
    // -------------------------------------------------------------------------

    @Test
    void prepareStatementReturnsPreparedStatement() throws SQLException {
        PreparedStatement prepared = connection.prepareStatement("SELECT 1 FROM SYSIBM.SYSDUMMY1");
        assertTrue(prepared instanceof MapepirePreparedStatement);
    }

    // -------------------------------------------------------------------------
    // parameter setting
    // -------------------------------------------------------------------------

    @Test
    void settingParametersDoesNotThrow() throws SQLException {
        ps.setString(1, "value");
        ps.setInt(2, 42);
        ps.setLong(1, 42L);
        ps.setBoolean(2, true);
        ps.setDouble(1, 1.5d);
        ps.setNull(2, Types.INTEGER);
    }

    @Test
    void setParameterThrowsForIndexBelowOne() {
        assertThrows(SQLException.class, () -> ps.setString(0, "value"));
        assertThrows(SQLException.class, () -> ps.setInt(-1, 42));
    }

    @Test
    void setParameterThrowsOnClosedStatement() throws SQLException {
        ps.close();
        assertThrows(SQLException.class, () -> ps.setString(1, "value"));
    }

    @Test
    void executeThrowsWhenParameterGapExists() throws SQLException {
        // Only parameter 2 is set — parameter 1 is missing
        ps.setInt(2, 42);
        SQLException e = assertThrows(SQLException.class, () -> ps.executeQuery());
        assertTrue(e.getMessage().contains("Parameter 1 is not set"));
    }

    @Test
    void clearParametersRemovesPreviouslySetValues() throws SQLException {
        ps.setString(1, "value");
        ps.setInt(2, 42);
        ps.clearParameters();
        ps.setInt(2, 42);

        // After clearing, parameter 1 is missing again
        SQLException e = assertThrows(SQLException.class, () -> ps.executeQuery());
        assertTrue(e.getMessage().contains("Parameter 1 is not set"));
    }

    // -------------------------------------------------------------------------
    // Statement-style execute methods are forbidden on a PreparedStatement
    // -------------------------------------------------------------------------

    @Test
    void executeQueryWithSqlStringThrows() {
        assertThrows(SQLException.class, () -> ps.executeQuery("SELECT 1 FROM SYSIBM.SYSDUMMY1"));
    }

    @Test
    void executeUpdateWithSqlStringThrows() {
        assertThrows(SQLException.class, () -> ps.executeUpdate("DELETE FROM T"));
    }

    @Test
    void executeWithSqlStringThrows() {
        assertThrows(SQLException.class, () -> ps.execute("SELECT 1 FROM SYSIBM.SYSDUMMY1"));
    }

    // -------------------------------------------------------------------------
    // inherited Statement behavior
    // -------------------------------------------------------------------------

    @Test
    void isClosedWorksOnPreparedStatement() throws SQLException {
        assertFalse(ps.isClosed());
        ps.close();
        assertTrue(ps.isClosed());
    }

    // -------------------------------------------------------------------------
    // unimplemented methods throw SQLFeatureNotSupportedException (SQLException)
    // -------------------------------------------------------------------------

    @Test
    void unimplementedMethodsThrowSQLFeatureNotSupportedException() {
        assertThrows(SQLFeatureNotSupportedException.class, () -> ps.setBytes(1, new byte[0]));
    }

    // -------------------------------------------------------------------------
    // addBatch / clearBatch / executeBatch
    // -------------------------------------------------------------------------

    @Test
    void executeBatchSendsAllRowsInOneRequestKeepingBoundParametersAndEmptiesBatch() throws Exception {
        MapepirePreparedStatement mockedPs = preparedStatementWithMockJob("INSERT INTO T VALUES (?, ?)");
        QueryResult<Object> two = new QueryResult<>();
        two.setUpdateCount(2);
        when(mockJob.query(anyString(), any(QueryOptions.class))).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(CompletableFuture.completedFuture(two));

        mockedPs.setString(1, "X");
        mockedPs.setInt(2, 0);
        mockedPs.addBatch();
        mockedPs.clearBatch();
        // Parameter 1 stays bound for every row; parameter 2 is a buffer reused for each row
        mockedPs.setString(1, "fixed");
        byte[] buffer = new byte[1];
        for (byte i = 1; i <= 2; i++) {
            buffer[0] = i;
            mockedPs.setObject(2, buffer);
            mockedPs.addBatch();
        }

        // The server only reports the total for the batch
        assertArrayEquals(new int[] { PreparedStatement.SUCCESS_NO_INFO, PreparedStatement.SUCCESS_NO_INFO },
                mockedPs.executeBatch());
        assertEquals(0, mockedPs.executeBatch().length);

        ArgumentCaptor<QueryOptions> captor = ArgumentCaptor.forClass(QueryOptions.class);
        verify(mockJob).query(anyString(), captor.capture());
        List<Object> rows = captor.getValue().getParameters();
        assertEquals(2, rows.size());
        for (int i = 0; i < 2; i++) {
            List<?> row = (List<?>) rows.get(i);
            assertEquals("fixed", row.get(0));
            assertArrayEquals(new byte[] { (byte) (i + 1) }, (byte[]) row.get(1));
        }
    }

    @Test
    void executeBatchSendsRowsInChunksAndStopsAtFailedChunk() throws Exception {
        MapepirePreparedStatement mockedPs = preparedStatementWithMockJob("INSERT INTO T VALUES (?)");
        CompletableFuture<QueryResult<Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new SQLException("Duplicate key", "23505"));
        when(mockJob.query(anyString(), any(QueryOptions.class))).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt()))
                .thenReturn(CompletableFuture.completedFuture(new QueryResult<>()), failed);

        int size = MapepirePreparedStatement.BATCH_CHUNK_SIZE;
        for (int i = 0; i < size + 2; i++) {
            mockedPs.setInt(1, i);
            mockedPs.addBatch();
        }

        BatchUpdateException e = assertThrows(BatchUpdateException.class, mockedPs::executeBatch);
        assertEquals("23505", e.getSQLState());
        int[] expected = new int[size];
        Arrays.fill(expected, PreparedStatement.SUCCESS_NO_INFO);
        assertArrayEquals(expected, e.getUpdateCounts());

        ArgumentCaptor<QueryOptions> captor = ArgumentCaptor.forClass(QueryOptions.class);
        verify(mockJob, times(2)).query(anyString(), captor.capture());
        assertEquals(size, captor.getAllValues().get(0).getParameters().size());
        assertEquals(Arrays.asList(Arrays.asList(size), Arrays.asList(size + 1)),
                captor.getAllValues().get(1).getParameters());
    }

    @Test
    void addBatchValidatesParametersAndStatementState() throws SQLException {
        // Parameter 1 is missing
        ps.setInt(2, 42);
        assertTrue(assertThrows(SQLException.class, () -> ps.addBatch()).getMessage()
                .contains("Parameter 1 is not set"));
        assertThrows(SQLException.class, () -> ps.addBatch("DELETE FROM T"));

        ps.close();
        assertThrows(SQLException.class, () -> ps.addBatch());
        assertThrows(SQLException.class, () -> ps.clearBatch());
        assertThrows(SQLException.class, () -> ps.executeBatch());
    }

    // -------------------------------------------------------------------------
    // executeQuery / executeUpdate / execute against a mocked SqlJob and Query
    // -------------------------------------------------------------------------

    @Test
    void executeQueryPassesBoundParametersInOrder() throws Exception {
        MapepirePreparedStatement mockedPs = preparedStatementWithMockJob("SELECT * FROM T WHERE A = ? AND B = ?");
        mockedPs.setString(1, "A");
        mockedPs.setInt(2, 42);

        QueryResult<Object> result = mock(QueryResult.class);
        when(result.getData()).thenReturn(Collections.emptyList());
        when(mockJob.query(eq("SELECT * FROM T WHERE A = ? AND B = ?"), any(QueryOptions.class)))
                .thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(CompletableFuture.completedFuture(result));

        try (ResultSet rs = mockedPs.executeQuery()) {
            assertFalse(rs.next());
        }

        ArgumentCaptor<QueryOptions> optionsCaptor = ArgumentCaptor.forClass(QueryOptions.class);
        verify(mockJob).query(eq("SELECT * FROM T WHERE A = ? AND B = ?"), optionsCaptor.capture());
        assertEquals(Arrays.asList("A", 42), optionsCaptor.getValue().getParameters());
    }

    @Test
    void executeUpdateReturnsUpdateCountFromMockedQuery() throws Exception {
        MapepirePreparedStatement mockedPs = preparedStatementWithMockJob("DELETE FROM T WHERE A = ?");
        mockedPs.setInt(1, 7);

        QueryResult<Object> result = mock(QueryResult.class);
        when(result.getUpdateCount()).thenReturn(3);
        when(mockJob.query(eq("DELETE FROM T WHERE A = ?"), any(QueryOptions.class))).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(CompletableFuture.completedFuture(result));

        assertEquals(3, mockedPs.executeUpdate());
    }

    @Test
    void executePropagatesSqlStateFromFailedQuery() throws Exception {
        MapepirePreparedStatement mockedPs = preparedStatementWithMockJob("SELECT * FROM BOGUS WHERE A = ?");
        mockedPs.setInt(1, 1);

        SQLException serverError = new SQLException("Table not found", "42S02");
        CompletableFuture<QueryResult<Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(serverError);

        when(mockJob.query(eq("SELECT * FROM BOGUS WHERE A = ?"), any(QueryOptions.class))).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(failed);

        SQLException thrown = assertThrows(SQLException.class, mockedPs::execute);
        assertEquals("42S02", thrown.getSQLState());
    }

    // -------------------------------------------------------------------------
    // getMetaData
    // -------------------------------------------------------------------------

    @Test
    void getMetaDataIsNullBeforeExecution() throws SQLException {
        // Column metadata only arrives with the execution result
        assertNull(ps.getMetaData());
    }

    @Test
    void getMetaDataDescribesResultAfterExecution() throws Exception {
        MapepirePreparedStatement mockedPs = preparedStatementWithMockJob("SELECT ID FROM T WHERE A = ?");
        mockedPs.setInt(1, 1);

        QueryResult<Object> result = new QueryResult<>();
        result.setData(Collections.emptyList());
        result.setMetadata(new QueryMetadata(1, Collections.singletonList(new ColumnMetadata(11, "ID", "ID",
                "INTEGER", 10, 0, false, ResultSetMetaData.columnNoNulls, false, true, "T")), null, null));
        when(mockJob.query(eq("SELECT ID FROM T WHERE A = ?"), any(QueryOptions.class))).thenReturn(mockQuery);
        when(mockQuery.<Object>execute(anyInt())).thenReturn(CompletableFuture.completedFuture(result));

        mockedPs.executeQuery().close();
        ResultSetMetaData md = mockedPs.getMetaData();
        assertEquals(1, md.getColumnCount());
        assertEquals(Types.INTEGER, md.getColumnType(1));
    }

    @Test
    void getMetaDataThrowsWhenClosed() throws SQLException {
        ps.close();
        assertThrows(SQLException.class, ps::getMetaData);
    }
}
