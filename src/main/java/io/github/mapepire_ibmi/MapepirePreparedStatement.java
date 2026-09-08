package io.github.mapepire_ibmi;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import io.github.mapepire_ibmi.types.QueryOptions;

public class MapepirePreparedStatement extends MapepireStatement implements PreparedStatement {
    private final String sql;
    private final SortedMap<Integer, Object> parameters = new TreeMap<>();

    public MapepirePreparedStatement(MapepireConnection connection, String sql) {
        super(connection);
        this.sql = sql;
    }

    private void setParameter(int parameterIndex, Object value) throws SQLException {
        checkClosed();
        if (parameterIndex < 1) {
            throw new SQLException("Invalid parameter index: " + parameterIndex);
        }
        this.parameters.put(parameterIndex, value);
    }

    private List<Object> getParameterList() throws SQLException {
        int max = this.parameters.isEmpty() ? 0 : this.parameters.lastKey();
        List<Object> list = new ArrayList<>(max);
        for (int i = 1; i <= max; i++) {
            if (!this.parameters.containsKey(i)) {
                throw new SQLException("Parameter " + i + " is not set");
            }
            list.add(this.parameters.get(i));
        }
        return list;
    }

    private void executeInternal() throws SQLException {
        checkClosed();
        QueryOptions options = new QueryOptions();
        options.setParameters(getParameterList());

        try {
            Query query = getMapepireConnection().getJob().query(this.sql, options);
            setExecutionState(query, query.execute(getFetchSize()).get());
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        executeInternal();
        return new MapepireResultSet(getResult());
    }

    @Override
    public int executeUpdate() throws SQLException {
        executeInternal();
        return getResult().getUpdateCount();
    }

    @Override
    public boolean execute() throws SQLException {
        executeInternal();
        return getResult().getHasResults();
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        throw new SQLException("Cannot call executeQuery(String) on a PreparedStatement");
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        throw new SQLException("Cannot call executeUpdate(String) on a PreparedStatement");
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        throw new SQLException("Cannot call execute(String) on a PreparedStatement");
    }

    @Override
    public void clearParameters() throws SQLException {
        checkClosed();
        this.parameters.clear();
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        setParameter(parameterIndex, null);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        setParameter(parameterIndex, null);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        setParameter(parameterIndex, x);
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        setParameter(parameterIndex, x);
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        setParameter(parameterIndex, x);
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        setParameter(parameterIndex, x);
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        setParameter(parameterIndex, x);
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        setParameter(parameterIndex, x);
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        setParameter(parameterIndex, x);
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        setParameter(parameterIndex, x);
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        setParameter(parameterIndex, x);
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        // Sent as an ISO string so it serializes cleanly over the wire
        setParameter(parameterIndex, x == null ? null : x.toString());
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        setParameter(parameterIndex, x == null ? null : x.toString());
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        setParameter(parameterIndex, x == null ? null : x.toString());
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        if (x instanceof Date || x instanceof Time || x instanceof Timestamp) {
            setParameter(parameterIndex, x.toString());
        } else {
            setParameter(parameterIndex, x);
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        setObject(parameterIndex, x);
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        setObject(parameterIndex, x);
    }

    @Override
    public void addBatch() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'addBatch'");
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setBytes'");
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setAsciiStream'");
    }

    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setUnicodeStream'");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setBinaryStream'");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setCharacterStream'");
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setRef'");
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setBlob'");
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setClob'");
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setArray'");
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getMetaData'");
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setDate'");
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setTime'");
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setTimestamp'");
    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setURL'");
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getParameterMetaData'");
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setRowId'");
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        setParameter(parameterIndex, value);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setNCharacterStream'");
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setNClob'");
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setClob'");
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setBlob'");
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setNClob'");
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setSQLXML'");
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setAsciiStream'");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setBinaryStream'");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setCharacterStream'");
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setAsciiStream'");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setBinaryStream'");
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setCharacterStream'");
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setNCharacterStream'");
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setClob'");
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setBlob'");
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setNClob'");
    }
}
