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
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

import io.github.mapepire_ibmi.types.JobStatus;
import io.github.mapepire_ibmi.types.QueryResult;

public class MapepireConnection implements Connection {
    private final SqlJob job;
    private boolean autoCommit = true;

    public MapepireConnection(SqlJob job) {
        this.job = job;
    }

    public SqlJob getJob() {
        return this.job;
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
        throw new UnsupportedOperationException("Unimplemented method 'prepareCall'");
    }

    @Override
    public String nativeSQL(String sql) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'nativeSQL'");
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
            this.job.execute("COMMIT").get();
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void rollback() throws SQLException {
        try {
            this.job.execute("ROLLBACK").get();
        } catch (Exception e) {
            throw new SQLException(e);
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
        throw new UnsupportedOperationException("Unimplemented method 'getMetaData'");
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        try {
            String type = readOnly ? "READ ONLY" : "READ WRITE";
            this.job.execute("SET TRANSACTION " + type).get();
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isReadOnly'");
    }

    @Override
    public void setCatalog(String catalog) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setCatalog'");
    }

    @Override
    public String getCatalog() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getCatalog'");
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

            this.job.execute("SET TRANSACTION ISOLATION LEVEL " + isolationLevel).get();
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override
    public int getTransactionIsolation() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTransactionIsolation'");
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getWarnings'");
    }

    @Override
    public void clearWarnings() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'clearWarnings'");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createStatement'");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'prepareStatement'");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'prepareCall'");
    }

    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTypeMap'");
    }

    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setTypeMap'");
    }

    @Override
    public void setHoldability(int holdability) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setHoldability'");
    }

    @Override
    public int getHoldability() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getHoldability'");
    }

    @Override
    public Savepoint setSavepoint() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setSavepoint'");
    }

    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setSavepoint'");
    }

    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'rollback'");
    }

    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'releaseSavepoint'");
    }

    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
            throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createStatement'");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency,
            int resultSetHoldability) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'prepareStatement'");
    }

    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
            int resultSetHoldability) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'prepareCall'");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'prepareStatement'");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'prepareStatement'");
    }

    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'prepareStatement'");
    }

    @Override
    public Clob createClob() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createClob'");
    }

    @Override
    public Blob createBlob() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createBlob'");
    }

    @Override
    public NClob createNClob() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createNClob'");
    }

    @Override
    public SQLXML createSQLXML() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createSQLXML'");
    }

    @Override
    public boolean isValid(int timeout) throws SQLException {
        if (timeout < 0) {
            throw new SQLException("Timeout must not be negative");
        }

        return this.job.getStatus() != JobStatus.Ended;
    }

    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setClientInfo'");
    }

    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setClientInfo'");
    }

    @Override
    public String getClientInfo(String name) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getClientInfo'");
    }

    @Override
    public Properties getClientInfo() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getClientInfo'");
    }

    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createArrayOf'");
    }

    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createStruct'");
    }

    @Override
    public void setSchema(String schema) throws SQLException {
        try {
            this.job.execute("SET SCHEMA " + schema).get();
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override
    public String getSchema() throws SQLException {
        try {
            QueryResult<Map<String, String>> result = this.job
                    .<Map<String, String>>execute("SELECT CURRENT SCHEMA FROM SYSIBM.SYSDUMMY1").get();
            if (result.getSuccess()) {
                return result.getData().get(0).entrySet().iterator().next().getValue();
            } else {
                throw new SQLException(result.getError(), result.getSqlState());
            }
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void abort(Executor executor) throws SQLException {
        this.job.close();
    }

    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setNetworkTimeout'");
    }

    @Override
    public int getNetworkTimeout() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNetworkTimeout'");
    }
}
