package io.github.mapepire_ibmi;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.mapepire_ibmi.types.QueryResult;
import io.github.mapepire_ibmi.types.QueryState;

public class MapepireStatement implements Statement {
    protected static final int DEFAULT_FETCH_SIZE = 100;

    private final MapepireConnection connection;
    private final int resultSetType;
    private final int resultSetConcurrency;
    private Query query;
    private QueryResult<Object> result;
    private String executedSql;
    private int fetchSize = DEFAULT_FETCH_SIZE;
    private int queryTimeoutSeconds;
    private boolean closed;

    public MapepireStatement(MapepireConnection connection) {
        this.connection = connection;
        this.resultSetType = ResultSet.TYPE_FORWARD_ONLY;
        this.resultSetConcurrency = ResultSet.CONCUR_READ_ONLY;
    }

    /**
     * Create a statement whose result sets have the given type and concurrency.
     *
     * @throws SQLFeatureNotSupportedException If the type is
     *                                         {@link ResultSet#TYPE_SCROLL_SENSITIVE}.
     * @throws SQLException                    If the type or concurrency is not a
     *                                         valid constant.
     */
    public MapepireStatement(MapepireConnection connection, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        checkResultSetOptions(resultSetType, resultSetConcurrency);
        this.connection = connection;
        this.resultSetType = resultSetType;
        this.resultSetConcurrency = resultSetConcurrency;
    }

    static void checkResultSetOptions(int resultSetType, int resultSetConcurrency) throws SQLException {
        switch (resultSetType) {
            case ResultSet.TYPE_FORWARD_ONLY:
            case ResultSet.TYPE_SCROLL_INSENSITIVE:
                break;
            case ResultSet.TYPE_SCROLL_SENSITIVE:
                // The server has no scrollable cursors, so rows are cached on the
                // client and cannot reflect later changes made by others.
                throw new SQLFeatureNotSupportedException("TYPE_SCROLL_SENSITIVE result sets are not supported");
            default:
                throw new SQLException("Invalid result set type: " + resultSetType);
        }
        if (resultSetConcurrency != ResultSet.CONCUR_READ_ONLY
                && resultSetConcurrency != ResultSet.CONCUR_UPDATABLE) {
            throw new SQLException("Invalid result set concurrency: " + resultSetConcurrency);
        }
    }

    static void checkResultSetHoldability(int resultSetHoldability) throws SQLException {
        if (resultSetHoldability != ResultSet.HOLD_CURSORS_OVER_COMMIT) {
            throw new SQLFeatureNotSupportedException("Only HOLD_CURSORS_OVER_COMMIT holdability is supported");
        }
    }

    protected void checkClosed() throws SQLException {
        if (this.closed) {
            throw new SQLException("Statement is closed");
        }
    }

    protected MapepireConnection getMapepireConnection() {
        return this.connection;
    }

    protected QueryResult<Object> getResult() {
        return this.result;
    }

    protected void setExecutionState(Query query, QueryResult<Object> result, String sql) throws SQLException {
        if (this.query != null && this.query.getState() != QueryState.RUN_DONE) {
            try {
                this.connection.resolve(this.query.close());
            } catch (Exception e) {
                throw SqlExceptions.toSqlException(e);
            }
        }
        this.query = query;
        this.result = result;
        this.executedSql = sql;
    }

    /**
     * Create a result set over the rows of the current execution. A scrollable
     * result set needs every row up front, so the remaining blocks are fetched
     * first.
     */
    protected ResultSet createResultSet() throws SQLException {
        if (this.resultSetType != ResultSet.TYPE_FORWARD_ONLY) {
            fetchAllRows();
        }
        return new MapepireResultSet(this, this.executedSql, this.result, this.resultSetType,
                this.resultSetConcurrency);
    }

    private void fetchAllRows() throws SQLException {
        if (this.result.getIsDone()) {
            return;
        }

        List<Object> rows = new ArrayList<>();
        if (this.result.getData() != null) {
            rows.addAll(this.result.getData());
        }
        try {
            QueryResult<Object> block = this.result;
            while (!block.getIsDone()) {
                block = resolve(this.query.<Object>fetchMore(this.fetchSize));
                if (block.getData() == null || block.getData().isEmpty()) {
                    break;
                }
                rows.addAll(block.getData());
            }
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        }
        this.result.setData(rows);
        this.result.setIsDone(true);
    }

    /**
     * Wait for the given future to complete, bounding the wait by the configured
     * query timeout and the connection network timeout. A timeout of 0 (the
     * default) means wait indefinitely, consistent with the JDBC "0 means no
     * timeout" convention.
     * <p>
     * Query timeout cancels only the active query. Network timeout aborts the
     * connection via {@link MapepireConnection#abortAfterNetworkTimeout()}.
     */
    protected <T> T resolve(CompletableFuture<T> future) throws Exception {
        long queryMillis = this.queryTimeoutSeconds > 0 ? TimeUnit.SECONDS.toMillis(this.queryTimeoutSeconds) : 0;
        long networkMillis = this.connection.getNetworkTimeout();

        // Pick the shorter non-zero limit; 0 means no limit.
        long effectiveMillis;
        if (queryMillis > 0 && networkMillis > 0) {
            effectiveMillis = Math.min(queryMillis, networkMillis);
        } else {
            effectiveMillis = Math.max(queryMillis, networkMillis);
        }

        if (effectiveMillis <= 0) {
            return future.get();
        }
        try {
            return future.get(effectiveMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            // When both limits are set, treat the shorter (or equal) network limit as
            // a connection-level abort; otherwise cancel only the active query.
            boolean networkTimedOut = networkMillis > 0
                    && (queryMillis <= 0 || networkMillis <= queryMillis);
            if (networkTimedOut) {
                this.connection.abortAfterNetworkTimeout();
            } else if (this.query != null) {
                try {
                    this.query.close();
                } catch (Exception ignored) {
                }
            }
            throw e;
        }
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(query)) {
            return iface.cast(query);
        }

        throw new SQLException("Cannot unwrap to " + iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(query);
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        checkClosed();
        Query newQuery = null;
        try {
            newQuery = this.connection.getJob().query(sql);
            setExecutionState(newQuery, resolve(newQuery.execute(this.fetchSize)), sql);
            newQuery = null;
            return createResultSet();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        } finally {
            if (newQuery != null) {
                try {
                    this.connection.resolve(newQuery.close());
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        checkClosed();
        Query newQuery = null;
        try {
            newQuery = this.connection.getJob().query(sql);
            setExecutionState(newQuery, resolve(newQuery.execute(this.fetchSize)), sql);
            newQuery = null;
            return this.result.getUpdateCount();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        } finally {
            if (newQuery != null) {
                try {
                    this.connection.resolve(newQuery.close());
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void close() throws SQLException {
        if (this.closed) {
            return;
        }

        try {
            if (this.query != null) {
                try {
                    this.connection.resolve(this.query.close());
                } catch (Exception e) {
                    throw SqlExceptions.toSqlException(e);
                }
            }
        } finally {
            this.closed = true;
        }
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getMaxFieldSize'");
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setMaxFieldSize'");
    }

    @Override
    public int getMaxRows() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getMaxRows'");
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setMaxRows'");
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setEscapeProcessing'");
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        checkClosed();
        return this.queryTimeoutSeconds;
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        checkClosed();
        if (seconds < 0) {
            throw new SQLException("Query timeout must not be negative");
        }
        this.queryTimeoutSeconds = seconds;
    }

    @Override
    public void cancel() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'cancel'");
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkClosed();
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        checkClosed();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setCursorName'");
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        checkClosed();
        Query newQuery = null;
        try {
            newQuery = this.connection.getJob().query(sql);
            setExecutionState(newQuery, resolve(newQuery.execute(this.fetchSize)), sql);
            newQuery = null;
            return result.getHasResults();
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        } finally {
            if (newQuery != null) {
                try {
                    this.connection.resolve(newQuery.close());
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        checkClosed();
        if (this.result == null) {
            throw new SQLException("No results available: the statement has not been executed");
        }
        return createResultSet();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        checkClosed();
        if (this.result == null) {
            throw new SQLException("No update count available: the statement has not been executed");
        }
        return result.getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        checkClosed();
        if (this.result == null) {
            throw new SQLException("No results available: the statement has not been executed");
        }
        if (this.result.getIsDone()) {
            return false;
        } else {
            try {
                QueryResult<Object> next = resolve(this.query.fetchMore(this.fetchSize));
                // Only the first block of a query carries column metadata.
                if (next.getMetadata() == null) {
                    next.setMetadata(this.result.getMetadata());
                }
                this.result = next;
                return this.result.getHasResults();
            } catch (Exception e) {
                throw SqlExceptions.toSqlException(e);
            }
        }
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setFetchDirection'");
    }

    @Override
    public int getFetchDirection() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getFetchDirection'");
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        if (rows < 0) {
            throw new SQLException("Fetch size must not be negative");
        }
        this.fetchSize = rows == 0 ? DEFAULT_FETCH_SIZE : rows;
    }

    @Override
    public int getFetchSize() throws SQLException {
        return this.fetchSize;
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        checkClosed();
        return this.resultSetConcurrency;
    }

    @Override
    public int getResultSetType() throws SQLException {
        checkClosed();
        return this.resultSetType;
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'addBatch'");
    }

    @Override
    public void clearBatch() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'clearBatch'");
    }

    @Override
    public int[] executeBatch() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'executeBatch'");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return this.connection;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getMoreResults'");
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getGeneratedKeys'");
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'executeUpdate'");
    }

    @Override
    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'executeUpdate'");
    }

    @Override
    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'executeUpdate'");
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'execute'");
    }

    @Override
    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'execute'");
    }

    @Override
    public boolean execute(String sql, String[] columnNames) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'execute'");
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getResultSetHoldability'");
    }

    @Override
    public boolean isClosed() throws SQLException {
        return this.closed;
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setPoolable'");
    }

    @Override
    public boolean isPoolable() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'isPoolable'");
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'closeOnCompletion'");
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'isCloseOnCompletion'");
    }
}
