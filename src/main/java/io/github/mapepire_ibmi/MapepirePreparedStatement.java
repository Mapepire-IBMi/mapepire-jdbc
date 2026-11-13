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
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import io.github.mapepire_ibmi.types.QueryOptions;
import io.github.mapepire_ibmi.types.QueryResult;

public class MapepirePreparedStatement extends MapepireStatement implements PreparedStatement {
    /**
     * The most parameter rows sent to the server in one batch request, which
     * keeps the size of a single request bounded.
     */
    static final int BATCH_CHUNK_SIZE = 1000;

    private final String sql;
    private final SortedMap<Integer, Object> parameters = new TreeMap<>();
    private final List<List<Object>> batchRows = new ArrayList<>();

    public MapepirePreparedStatement(MapepireConnection connection, String sql) {
        super(connection);
        this.sql = sql;
    }

    /**
     * Create a prepared statement whose result sets have the given type and
     * concurrency.
     *
     * @see MapepireStatement#MapepireStatement(MapepireConnection, int, int)
     */
    public MapepirePreparedStatement(MapepireConnection connection, String sql, int resultSetType,
            int resultSetConcurrency) throws SQLException {
        super(connection, resultSetType, resultSetConcurrency);
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
        executeInternal(getParameterList());
    }

    private void executeInternal(List<Object> parameterList) throws SQLException {
        checkClosed();
        QueryOptions options = new QueryOptions();
        // The SDK sends byte arrays as Base64, which the server decodes for binary
        // parameters; servers before 2.4.0 expect hex instead
        options.setParameters(containsBinary(parameterList) && getMapepireConnection().sendsBinaryAsHex()
                ? binaryToHex(parameterList)
                : parameterList);

        Query query = null;
        try {
            query = getMapepireConnection().getJob().query(this.sql, options);
            setExecutionState(query, resolve(query.execute(getFetchSize()), query), this.sql);
            query = null;
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        } finally {
            if (query != null) {
                try {
                    getMapepireConnection().resolve(query.close());
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Whether a parameter list, or any row of a batch's list of rows, has a
     * binary value.
     */
    private static boolean containsBinary(List<?> parameters) {
        for (Object parameter : parameters) {
            if (parameter instanceof byte[] || parameter instanceof List && containsBinary((List<?>) parameter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A copy of a parameter list, or a batch's list of rows, with binary values
     * as hex, for servers before 2.4.0.
     */
    private static List<Object> binaryToHex(List<?> parameters) {
        List<Object> converted = new ArrayList<>(parameters.size());
        for (Object parameter : parameters) {
            if (parameter instanceof byte[]) {
                converted.add(Lobs.toHex((byte[]) parameter));
            } else if (parameter instanceof List) {
                converted.add(binaryToHex((List<?>) parameter));
            } else {
                converted.add(parameter);
            }
        }
        return converted;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        executeInternal();
        return createResultSet();
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
        } else if (x instanceof java.util.Date) {
            // Copy mutable values so a value the caller reuses after binding, for
            // example between addBatch calls, does not change rows already added
            setParameter(parameterIndex, ((java.util.Date) x).clone());
        } else {
            // Byte arrays are copied too; a Blob or Clob is sent as its value
            setParameter(parameterIndex, Lobs.materialize(x));
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

    /**
     * Add the currently bound parameters to the batch as one row. The parameters
     * stay bound, so only the values that change need to be set for the next row.
     */
    @Override
    public void addBatch() throws SQLException {
        checkClosed();
        this.batchRows.add(getParameterList());
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        throw new SQLException("Cannot call addBatch(String) on a PreparedStatement");
    }

    @Override
    public void clearBatch() throws SQLException {
        checkClosed();
        this.batchRows.clear();
    }

    /**
     * Send the batched parameter rows to the server, up to
     * {@value #BATCH_CHUNK_SIZE} rows per request. The server runs each request as
     * one JDBC batch and reports only its total update count, so every row's count
     * is {@link #SUCCESS_NO_INFO}. The batch is emptied afterwards, whether or not
     * it succeeded.
     *
     * @throws java.sql.BatchUpdateException If a request fails. Its update counts
     *                                        cover the rows of the requests that
     *                                        succeeded before it; rows of the
     *                                        failed request may or may not have
     *                                        been applied.
     */
    @Override
    public int[] executeBatch() throws SQLException {
        checkClosed();
        List<List<Object>> rows = new ArrayList<>(this.batchRows);
        this.batchRows.clear();

        int[] counts = new int[rows.size()];
        for (int start = 0; start < counts.length; start += BATCH_CHUNK_SIZE) {
            int end = Math.min(start + BATCH_CHUNK_SIZE, counts.length);
            try {
                // A list of parameter lists makes the server run them as a batch
                executeInternal(new ArrayList<Object>(rows.subList(start, end)));
                checkBatchResult();
            } catch (SQLException e) {
                throw toBatchUpdateException(e, counts, start);
            }
            Arrays.fill(counts, start, end, SUCCESS_NO_INFO);
        }
        return counts;
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        setParameter(parameterIndex, x == null ? null : x.clone());
    }

    // Stream and LOB parameters are read whole when they are set, since the
    // server receives every parameter value inline in the execute request.

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        setParameter(parameterIndex, Lobs.readAscii(x, length));
    }

    @Override
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setUnicodeStream'");
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        setParameter(parameterIndex, Lobs.readBytes(x, length));
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        setParameter(parameterIndex, Lobs.readString(reader, length));
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setRef'");
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        setParameter(parameterIndex, Lobs.toBytes(x));
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        setParameter(parameterIndex, Lobs.toString(x));
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setArray'");
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        checkClosed();
        QueryResult<Object> result = getResult();
        if (result == null || result.getMetadata() == null) {
            // Column metadata only arrives with the execution result. JDBC allows
            // null when the driver cannot describe the result up front.
            return null;
        }
        return new MapepireResultSetMetaData(result.getMetadata());
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
        setParameter(parameterIndex, Lobs.readString(value, length));
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        setParameter(parameterIndex, Lobs.toString(value));
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        setParameter(parameterIndex, Lobs.readString(reader, length));
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        setParameter(parameterIndex, Lobs.readBytes(inputStream, length));
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        setParameter(parameterIndex, Lobs.readString(reader, length));
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'setSQLXML'");
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        setParameter(parameterIndex, Lobs.readAscii(x, length));
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        setParameter(parameterIndex, Lobs.readBytes(x, length));
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        setParameter(parameterIndex, Lobs.readString(reader, length));
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        setParameter(parameterIndex, Lobs.readAscii(x));
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        setParameter(parameterIndex, Lobs.readBytes(x));
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        setParameter(parameterIndex, Lobs.readString(reader));
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        setParameter(parameterIndex, Lobs.readString(value));
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        setParameter(parameterIndex, Lobs.readString(reader));
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        setParameter(parameterIndex, Lobs.readBytes(inputStream));
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        setParameter(parameterIndex, Lobs.readString(reader));
    }
}
