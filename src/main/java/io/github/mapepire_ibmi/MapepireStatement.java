package io.github.mapepire_ibmi;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.github.mapepire_ibmi.types.QueryResult;
import io.github.mapepire_ibmi.types.QueryState;

public class MapepireStatement implements Statement {
    protected static final int DEFAULT_FETCH_SIZE = 100;

    private final MapepireConnection connection;
    private Query query;
    private QueryResult<Object> result;
    private int fetchSize = DEFAULT_FETCH_SIZE;
    private int queryTimeoutSeconds;
    private boolean closed;

    public MapepireStatement(MapepireConnection connection) {
        this.connection = connection;
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

    protected void setExecutionState(Query query, QueryResult<Object> result) throws SQLException {
        if (this.query != null && this.query.getState() != QueryState.RUN_DONE) {
            try {
                resolve(this.query.close());
            } catch (Exception e) {
                throw SqlExceptions.toSqlException(e);
            }
        }
        this.query = query;
        this.result = result;
    }

    /**
     * Wait for the given future to complete, bounding the wait by the configured
     * query timeout. A timeout of 0 (the default) means wait indefinitely,
     * consistent with the JDBC "0 means no timeout" convention.
     */
    protected <T> T resolve(CompletableFuture<T> future) throws Exception {
        if (this.queryTimeoutSeconds <= 0) {
            return future.get();
        }
        return future.get(this.queryTimeoutSeconds, TimeUnit.SECONDS);
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
        try {
            Query newQuery = this.connection.getJob().query(sql);
            setExecutionState(newQuery, resolve(newQuery.execute(this.fetchSize)));
            return new MapepireResultSet(this.result);
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        }
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        checkClosed();
        try {
            Query newQuery = this.connection.getJob().query(sql);
            setExecutionState(newQuery, resolve(newQuery.execute(this.fetchSize)));
            return this.result.getUpdateCount();
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        }
    }

    @Override
    public void close() throws SQLException {
        if (this.closed) {
            return;
        }
        this.closed = true;

        if (this.query != null) {
            try {
                resolve(this.query.close());
            } catch (Exception e) {
                throw SqlExceptions.toSqlException(e);
            }
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
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getWarnings'");
    }

    @Override
    public void clearWarnings() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'clearWarnings'");
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setCursorName'");
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        checkClosed();
        try {
            Query newQuery = this.connection.getJob().query(sql);
            setExecutionState(newQuery, resolve(newQuery.execute(this.fetchSize)));
            return result.getHasResults();
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        }
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        checkClosed();
        if (this.result == null) {
            throw new SQLException("No results available: the statement has not been executed");
        }
        return new MapepireResultSet(result);
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
                this.result = resolve(this.query.fetchMore(this.fetchSize));
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
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getResultSetConcurrency'");
    }

    @Override
    public int getResultSetType() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getResultSetType'");
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
