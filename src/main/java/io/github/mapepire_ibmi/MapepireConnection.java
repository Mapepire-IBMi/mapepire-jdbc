package io.github.mapepire_ibmi;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.mapepire_ibmi.types.JobStatus;
import io.github.mapepire_ibmi.types.QueryResult;

public class MapepireConnection implements Connection {
    private final SqlJob job;
    private boolean autoCommit = true;
    private int networkTimeoutMillis;

    public MapepireConnection(SqlJob job) {
        this.job = job;
    }

    public SqlJob getJob() {
        return this.job;
    }

    /**
     * Wait for the given future to complete, bounding the wait by the configured
     * network timeout. A timeout of 0 (the default) means wait indefinitely,
     * consistent with the JDBC "0 means no timeout" convention.
     */
    private <T> T resolve(CompletableFuture<T> future) throws Exception {
        if (this.networkTimeoutMillis <= 0) {
            return future.get();
        }
        return future.get(this.networkTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(job)) {
            return iface.cast(job);
        }

        throw new SQLException("Cannot unwrap to " + iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(job);
    }

    @Override
    public Statement createStatement() throws SQLException {
        return new MapepireStatement(this);
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return new MapepirePreparedStatement(this, sql);
    }

    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'prepareCall'");
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'nativeSQL'");
    }

    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        if (isClosed()) {
            throw new SQLException("Connection is closed");
        }

        if (this.autoCommit == autoCommit) {
            return;
        }

        // Per the JDBC spec, enabling auto-commit mid-transaction commits the
        // pending transaction.
        if (autoCommit) {
            commit();
        }
        this.autoCommit = autoCommit;
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        return this.autoCommit;
    }

    @Override
    public void commit() throws SQLException {
        try {
            resolve(this.job.execute("COMMIT"));
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        }
    }

    @Override
    public void rollback() throws SQLException {
        try {
            resolve(this.job.execute("ROLLBACK"));
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        }
    }

    @Override
    public void close() throws SQLException {
        this.job.close();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return this.job.getStatus() == JobStatus.Ended;
    }

    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getMetaData'");
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        try {
            String type = readOnly ? "READ ONLY" : "READ WRITE";
            resolve(this.job.execute("SET TRANSACTION " + type));
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        }
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'isReadOnly'");
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setCatalog'");
    }

    @Override
    public String getCatalog() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getCatalog'");
    }

    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        try {
            String isolationLevel = "";
            switch (level) {
                case Connection.TRANSACTION_NONE:
                    isolationLevel = "NO COMMIT";
                    break;
                case Connection.TRANSACTION_READ_COMMITTED:
                    isolationLevel = "READ COMMITTED";
                    break;
                case Connection.TRANSACTION_READ_UNCOMMITTED:
                    isolationLevel = "READ UNCOMMITTED";
                    break;
                case Connection.TRANSACTION_REPEATABLE_READ:
                    isolationLevel = "REPEATABLE READ";
                    break;
                case Connection.TRANSACTION_SERIALIZABLE:
                    isolationLevel = "SERIALIZABLE";
                    break;
                default:
                    return;
            }

            resolve(this.job.execute("SET TRANSACTION ISOLATION LEVEL " + isolationLevel));
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        }
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getTransactionIsolation'");
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
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'createStatement'");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'prepareStatement'");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'prepareCall'");
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getTypeMap'");
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setTypeMap'");
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setHoldability'");
    }

    @Override
    public int getHoldability() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getHoldability'");
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setSavepoint'");
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setSavepoint'");
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'rollback'");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'releaseSavepoint'");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'createStatement'");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
            int resultSetHoldability) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'prepareStatement'");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
            int resultSetHoldability) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'prepareCall'");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'prepareStatement'");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'prepareStatement'");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'prepareStatement'");
    }

    @Override
    public Clob createClob() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'createClob'");
    }

    @Override
    public Blob createBlob() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'createBlob'");
    }

    @Override
    public NClob createNClob() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'createNClob'");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'createSQLXML'");
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        if (timeout < 0) {
            throw new SQLException("Timeout must not be negative");
        }

        JobStatus status = this.job.getStatus();
        if (status == JobStatus.Ended) {
            return false;
        }

        // Only ping the server when there is an active connection to ping.
        // NotStarted/Connecting have no socket yet but the job has not ended.
        if (status != JobStatus.Ready && status != JobStatus.Busy) {
            return true;
        }

        try {
            if (timeout == 0) {
                this.job.execute("VALUES 1").get();
            } else {
                this.job.execute("VALUES 1").get(timeout, TimeUnit.SECONDS);
            }
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        // TODO Auto-generated method stub
        throw new SQLClientInfoException();
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        // TODO Auto-generated method stub
        throw new SQLClientInfoException();
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getClientInfo'");
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getClientInfo'");
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'createArrayOf'");
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'createStruct'");
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        try {
            resolve(this.job.execute("SET SCHEMA " + schema));
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        }
    }

    @Override
    public String getSchema() throws SQLException {
        try {
            QueryResult<Map<String, String>> result = resolve(this.job
                    .<Map<String, String>>execute("SELECT CURRENT SCHEMA FROM SYSIBM.SYSDUMMY1"));
            if (result.getSuccess()) {
                return result.getData().get(0).entrySet().iterator().next().getValue();
            } else {
                throw new SQLException(result.getError(), result.getSqlState());
            }
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        }
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        this.job.close();
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        if (milliseconds < 0) {
            throw new SQLException("Network timeout must not be negative");
        }
        this.networkTimeoutMillis = milliseconds;
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        return this.networkTimeoutMillis;
    }
}
