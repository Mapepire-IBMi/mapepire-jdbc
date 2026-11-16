package io.github.mapepire_ibmi;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLType;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.mapepire_ibmi.types.ColumnMetadata;
import io.github.mapepire_ibmi.types.ParameterResult;
import io.github.mapepire_ibmi.types.QueryMetadata;
import io.github.mapepire_ibmi.types.QueryResult;

/**
 * A statement that calls a stored procedure.
 * <p>
 * Register each OUT and INOUT parameter with
 * {@link #registerOutParameter(int, int)} before executing, and set INOUT
 * parameters like IN parameters. Always set an INOUT parameter: one that is
 * registered but not set is sent an empty string as its input, since the
 * driver cannot tell it apart from an OUT parameter. After execution, the
 * {@code getXxx} methods
 * read the values the procedure returned. They convert values the same way as
 * {@link MapepireResultSet}, from the parameter type the server reports, so the
 * type passed to {@code registerOutParameter} is not used.
 * <p>
 * Parameters are set and registered by index. OUT values can also be read by
 * parameter name, since the server sends the names with the values.
 * <p>
 * Limitations of the Mapepire server:
 * <ul>
 * <li>Only the first result set of a procedure is returned.</li>
 * <li>Return values ({@code {? = call ...}}) are not supported.</li>
 * <li>Binary OUT values are not supported.</li>
 * <li>An INOUT parameter cannot be given a NULL input value.</li>
 * </ul>
 */
public class MapepireCallableStatement extends MapepirePreparedStatement implements CallableStatement {
    // {call proc(?)}: the JDBC escape for a procedure call, with an optional trailing ;
    private static final Pattern CALL_ESCAPE = Pattern.compile("^\\s*\\{\\s*(call\\b.*?)\\s*\\}\\s*;?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    // {? = call func(?)}: the JDBC escape for a call with a return value
    private static final Pattern RETURN_ESCAPE = Pattern.compile("^\\s*\\{\\s*\\?\\s*=\\s*call\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Sent as the value of a registered parameter that was not set. The server
     * only registers an OUT or INOUT parameter whose value is not null. It
     * ignores the value of an OUT parameter, but binds it as the input of an
     * INOUT parameter, so INOUT parameters must be set.
     */
    private static final String OUT_PLACEHOLDER = "";

    private final SortedSet<Integer> outParameters = new TreeSet<>();
    // What the server returned for each parameter in the last execution, by index - 1
    private List<ParameterResult> parameterResults = Collections.emptyList();
    // The same values as a one-row result set, with a column for each parameter
    private MapepireResultSet outValues;

    public MapepireCallableStatement(MapepireConnection connection, String sql) throws SQLException {
        super(connection, translateCallEscape(sql));
    }

    /**
     * Create a callable statement whose result sets have the given type and
     * concurrency.
     *
     * @see MapepireStatement#MapepireStatement(MapepireConnection, int, int)
     */
    public MapepireCallableStatement(MapepireConnection connection, String sql, int resultSetType,
            int resultSetConcurrency) throws SQLException {
        super(connection, translateCallEscape(sql), resultSetType, resultSetConcurrency);
    }

    /**
     * Turn the JDBC escape {@code {call proc(?)}} into {@code call proc(?)}. The
     * server treats a statement as a procedure call only when it starts with
     * {@code CALL}. Other SQL is returned unchanged.
     *
     * @throws SQLFeatureNotSupportedException If the SQL is a call with a return
     *                                         value, {@code {? = call ...}}.
     */
    static String translateCallEscape(String sql) throws SQLException {
        if (sql == null) {
            throw new SQLException("SQL must not be null");
        }
        if (RETURN_ESCAPE.matcher(sql).find()) {
            throw new SQLFeatureNotSupportedException("Calls with a return value ({? = call ...}) are not supported");
        }
        Matcher call = CALL_ESCAPE.matcher(sql);
        return call.matches() ? call.group(1) : sql;
    }

    private static SQLFeatureNotSupportedException namedParameterError() {
        return new SQLFeatureNotSupportedException(
                "Setting or registering parameters by name is not supported; use the parameter index");
    }

    @Override
    int lastParameterIndex() {
        int last = super.lastParameterIndex();
        return this.outParameters.isEmpty() ? last : Math.max(last, this.outParameters.last());
    }

    @Override
    Object parameterValue(int parameterIndex) throws SQLException {
        if (!this.outParameters.contains(parameterIndex)) {
            return super.parameterValue(parameterIndex);
        }
        if (!isParameterSet(parameterIndex)) {
            return OUT_PLACEHOLDER;
        }
        Object value = super.parameterValue(parameterIndex);
        if (value == null) {
            // The server only registers an OUT or INOUT parameter whose value is not null
            throw new SQLFeatureNotSupportedException(
                    "A NULL value for OUT or INOUT parameter " + parameterIndex + " is not supported");
        }
        return value;
    }

    private void clearOutValues() {
        this.parameterResults = Collections.emptyList();
        this.outValues = null;
    }

    private void readOutValues(QueryResult<Object> result) throws SQLException {
        List<ParameterResult> returned = result.getOutputParms();
        List<ParameterResult> byIndex = new ArrayList<>();
        if (returned != null) {
            for (ParameterResult parameter : returned) {
                int index = parameter.getindex();
                if (index < 1) {
                    continue;
                }
                while (byIndex.size() < index) {
                    byIndex.add(null);
                }
                byIndex.set(index - 1, parameter);
            }
        }

        Map<String, Object> row = new LinkedHashMap<>();
        List<ColumnMetadata> columns = new ArrayList<>(byIndex.size());
        for (int i = 0; i < byIndex.size(); i++) {
            ParameterResult parameter = byIndex.get(i);
            ColumnMetadata column = new ColumnMetadata();
            if (parameter != null) {
                column.setName(parameter.getName());
                column.setType(parameter.getType());
            }
            columns.add(column);
            row.put(String.valueOf(i + 1), parameter == null ? null : parameter.getValue());
        }

        QueryResult<Object> values = new QueryResult<>();
        values.setData(Collections.<Object>singletonList(row));
        values.setMetadata(new QueryMetadata(columns.size(), columns, null, null));
        values.setIsDone(true);
        MapepireResultSet valueRow = new MapepireResultSet(values);
        valueRow.next();

        this.parameterResults = byIndex;
        this.outValues = valueRow;
    }

    /**
     * The OUT values of the last execution, after checking that the given
     * parameter's value can be read.
     */
    private MapepireResultSet outValues(int parameterIndex) throws SQLException {
        checkClosed();
        if (!this.outParameters.contains(parameterIndex)) {
            throw new SQLException("Parameter " + parameterIndex + " is not registered as an OUT parameter");
        }
        if (this.outValues == null) {
            throw new SQLException("No OUT parameter values: the statement has not been executed");
        }
        ParameterResult parameter = parameterIndex <= this.parameterResults.size()
                ? this.parameterResults.get(parameterIndex - 1)
                : null;
        if (parameter == null) {
            throw new SQLException("The server returned no value for parameter " + parameterIndex);
        }
        if (JdbcTypes.isBinary(JdbcTypes.toSqlType(parameter.getType()))) {
            throw new SQLFeatureNotSupportedException("Binary OUT parameters are not supported");
        }
        return this.outValues;
    }

    private int findParameter(String parameterName) throws SQLException {
        checkClosed();
        if (this.outValues == null) {
            throw new SQLException("Parameter names are only known once the statement has been executed");
        }
        for (int i = 0; i < this.parameterResults.size(); i++) {
            ParameterResult parameter = this.parameterResults.get(i);
            if (parameter != null && parameterName != null && parameterName.equalsIgnoreCase(parameter.getName())) {
                return i + 1;
            }
        }
        throw new SQLException("Parameter not found: " + parameterName);
    }

    // -------------------------------------------------------------------------
    // execution
    // -------------------------------------------------------------------------

    /**
     * Every execution, batches included, ends here, so the OUT values are always
     * those of the last execution.
     */
    @Override
    protected void setExecutionState(Query query, QueryResult<Object> result, String sql) throws SQLException {
        clearOutValues();
        super.setExecutionState(query, result, sql);
        readOutValues(result);
    }

    /**
     * Add the bound parameters to the batch. A call with OUT parameters cannot
     * be batched, since a batch returns no OUT values.
     */
    @Override
    public void addBatch() throws SQLException {
        checkClosed();
        if (!this.outParameters.isEmpty()) {
            throw new SQLException("A call with OUT parameters cannot be added to a batch");
        }
        super.addBatch();
    }

    // -------------------------------------------------------------------------
    // OUT parameter registration
    // -------------------------------------------------------------------------

    // The registered type is not used: values are converted from the type the server reports
    private void registerOut(int parameterIndex) throws SQLException {
        checkClosed();
        if (parameterIndex < 1) {
            throw new SQLException("Invalid parameter index: " + parameterIndex);
        }
        // The server only returns OUT values for SQL that starts with CALL
        if (!getSql().trim().regionMatches(true, 0, "call", 0, 4)) {
            throw new SQLException(
                    "OUT parameters need SQL that starts with CALL, or a {call ...} escape: " + getSql());
        }
        this.outParameters.add(parameterIndex);
    }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
        registerOut(parameterIndex);
    }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {
        registerOut(parameterIndex);
    }

    @Override
    public void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException {
        registerOut(parameterIndex);
    }

    @Override
    public void registerOutParameter(int parameterIndex, SQLType sqlType) throws SQLException {
        registerOut(parameterIndex);
    }

    @Override
    public void registerOutParameter(int parameterIndex, SQLType sqlType, int scale) throws SQLException {
        registerOut(parameterIndex);
    }

    @Override
    public void registerOutParameter(int parameterIndex, SQLType sqlType, String typeName) throws SQLException {
        registerOut(parameterIndex);
    }

    @Override
    public void registerOutParameter(String parameterName, int sqlType) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException {
        throw namedParameterError();
    }

    // -------------------------------------------------------------------------
    // OUT parameter values by index
    // -------------------------------------------------------------------------

    @Override
    public boolean wasNull() throws SQLException {
        checkClosed();
        return this.outValues != null && this.outValues.wasNull();
    }

    @Override
    public String getString(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getString(parameterIndex);
    }

    @Override
    public boolean getBoolean(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getBoolean(parameterIndex);
    }

    @Override
    public byte getByte(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getByte(parameterIndex);
    }

    @Override
    public short getShort(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getShort(parameterIndex);
    }

    @Override
    public int getInt(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getInt(parameterIndex);
    }

    @Override
    public long getLong(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getLong(parameterIndex);
    }

    @Override
    public float getFloat(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getFloat(parameterIndex);
    }

    @Override
    public double getDouble(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getDouble(parameterIndex);
    }

    @Override
    @Deprecated
    public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
        return outValues(parameterIndex).getBigDecimal(parameterIndex, scale);
    }

    @Override
    public BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getBigDecimal(parameterIndex);
    }

    @Override
    public byte[] getBytes(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getBytes(parameterIndex);
    }

    @Override
    public Date getDate(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getDate(parameterIndex);
    }

    @Override
    public Date getDate(int parameterIndex, Calendar cal) throws SQLException {
        return outValues(parameterIndex).getDate(parameterIndex, cal);
    }

    @Override
    public Time getTime(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getTime(parameterIndex);
    }

    @Override
    public Time getTime(int parameterIndex, Calendar cal) throws SQLException {
        return outValues(parameterIndex).getTime(parameterIndex, cal);
    }

    @Override
    public Timestamp getTimestamp(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getTimestamp(parameterIndex);
    }

    @Override
    public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
        return outValues(parameterIndex).getTimestamp(parameterIndex, cal);
    }

    @Override
    public Object getObject(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getObject(parameterIndex);
    }

    @Override
    public Object getObject(int parameterIndex, Map<String, Class<?>> map) throws SQLException {
        return outValues(parameterIndex).getObject(parameterIndex, map);
    }

    @Override
    public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
        return outValues(parameterIndex).getObject(parameterIndex, type);
    }

    @Override
    public Ref getRef(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getRef(parameterIndex);
    }

    @Override
    public Blob getBlob(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getBlob(parameterIndex);
    }

    @Override
    public Clob getClob(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getClob(parameterIndex);
    }

    @Override
    public NClob getNClob(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getNClob(parameterIndex);
    }

    @Override
    public Array getArray(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getArray(parameterIndex);
    }

    @Override
    public URL getURL(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getURL(parameterIndex);
    }

    @Override
    public RowId getRowId(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getRowId(parameterIndex);
    }

    @Override
    public SQLXML getSQLXML(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getSQLXML(parameterIndex);
    }

    @Override
    public String getNString(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getNString(parameterIndex);
    }

    @Override
    public Reader getNCharacterStream(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getNCharacterStream(parameterIndex);
    }

    @Override
    public Reader getCharacterStream(int parameterIndex) throws SQLException {
        return outValues(parameterIndex).getCharacterStream(parameterIndex);
    }

    // -------------------------------------------------------------------------
    // OUT parameter values by name
    // -------------------------------------------------------------------------

    @Override
    public String getString(String parameterName) throws SQLException {
        return getString(findParameter(parameterName));
    }

    @Override
    public boolean getBoolean(String parameterName) throws SQLException {
        return getBoolean(findParameter(parameterName));
    }

    @Override
    public byte getByte(String parameterName) throws SQLException {
        return getByte(findParameter(parameterName));
    }

    @Override
    public short getShort(String parameterName) throws SQLException {
        return getShort(findParameter(parameterName));
    }

    @Override
    public int getInt(String parameterName) throws SQLException {
        return getInt(findParameter(parameterName));
    }

    @Override
    public long getLong(String parameterName) throws SQLException {
        return getLong(findParameter(parameterName));
    }

    @Override
    public float getFloat(String parameterName) throws SQLException {
        return getFloat(findParameter(parameterName));
    }

    @Override
    public double getDouble(String parameterName) throws SQLException {
        return getDouble(findParameter(parameterName));
    }

    @Override
    public BigDecimal getBigDecimal(String parameterName) throws SQLException {
        return getBigDecimal(findParameter(parameterName));
    }

    @Override
    public byte[] getBytes(String parameterName) throws SQLException {
        return getBytes(findParameter(parameterName));
    }

    @Override
    public Date getDate(String parameterName) throws SQLException {
        return getDate(findParameter(parameterName));
    }

    @Override
    public Date getDate(String parameterName, Calendar cal) throws SQLException {
        return getDate(findParameter(parameterName), cal);
    }

    @Override
    public Time getTime(String parameterName) throws SQLException {
        return getTime(findParameter(parameterName));
    }

    @Override
    public Time getTime(String parameterName, Calendar cal) throws SQLException {
        return getTime(findParameter(parameterName), cal);
    }

    @Override
    public Timestamp getTimestamp(String parameterName) throws SQLException {
        return getTimestamp(findParameter(parameterName));
    }

    @Override
    public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException {
        return getTimestamp(findParameter(parameterName), cal);
    }

    @Override
    public Object getObject(String parameterName) throws SQLException {
        return getObject(findParameter(parameterName));
    }

    @Override
    public Object getObject(String parameterName, Map<String, Class<?>> map) throws SQLException {
        return getObject(findParameter(parameterName), map);
    }

    @Override
    public <T> T getObject(String parameterName, Class<T> type) throws SQLException {
        return getObject(findParameter(parameterName), type);
    }

    @Override
    public Ref getRef(String parameterName) throws SQLException {
        return getRef(findParameter(parameterName));
    }

    @Override
    public Blob getBlob(String parameterName) throws SQLException {
        return getBlob(findParameter(parameterName));
    }

    @Override
    public Clob getClob(String parameterName) throws SQLException {
        return getClob(findParameter(parameterName));
    }

    @Override
    public NClob getNClob(String parameterName) throws SQLException {
        return getNClob(findParameter(parameterName));
    }

    @Override
    public Array getArray(String parameterName) throws SQLException {
        return getArray(findParameter(parameterName));
    }

    @Override
    public URL getURL(String parameterName) throws SQLException {
        return getURL(findParameter(parameterName));
    }

    @Override
    public RowId getRowId(String parameterName) throws SQLException {
        return getRowId(findParameter(parameterName));
    }

    @Override
    public SQLXML getSQLXML(String parameterName) throws SQLException {
        return getSQLXML(findParameter(parameterName));
    }

    @Override
    public String getNString(String parameterName) throws SQLException {
        return getNString(findParameter(parameterName));
    }

    @Override
    public Reader getNCharacterStream(String parameterName) throws SQLException {
        return getNCharacterStream(findParameter(parameterName));
    }

    @Override
    public Reader getCharacterStream(String parameterName) throws SQLException {
        return getCharacterStream(findParameter(parameterName));
    }

    // -------------------------------------------------------------------------
    // setting parameters by name is not supported
    // -------------------------------------------------------------------------

    @Override
    public void setURL(String parameterName, URL val) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setNull(String parameterName, int sqlType) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setNull(String parameterName, int sqlType, String typeName) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setBoolean(String parameterName, boolean x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setByte(String parameterName, byte x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setShort(String parameterName, short x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setInt(String parameterName, int x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setLong(String parameterName, long x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setFloat(String parameterName, float x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setDouble(String parameterName, double x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setString(String parameterName, String x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setNString(String parameterName, String value) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setBytes(String parameterName, byte[] x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setDate(String parameterName, Date x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setDate(String parameterName, Date x, Calendar cal) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setTime(String parameterName, Time x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setTime(String parameterName, Time x, Calendar cal) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setTimestamp(String parameterName, Timestamp x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setObject(String parameterName, Object x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setRowId(String parameterName, RowId x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setBlob(String parameterName, Blob x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setBlob(String parameterName, InputStream inputStream) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setClob(String parameterName, Clob x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setClob(String parameterName, Reader reader) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setClob(String parameterName, Reader reader, long length) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setNClob(String parameterName, NClob value) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setNClob(String parameterName, Reader reader) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setNClob(String parameterName, Reader reader, long length) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setAsciiStream(String parameterName, InputStream x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setAsciiStream(String parameterName, InputStream x, long length) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setBinaryStream(String parameterName, InputStream x) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setBinaryStream(String parameterName, InputStream x, long length) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader, int length) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setCharacterStream(String parameterName, Reader reader, long length) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setNCharacterStream(String parameterName, Reader value) throws SQLException {
        throw namedParameterError();
    }

    @Override
    public void setNCharacterStream(String parameterName, Reader value, long length) throws SQLException {
        throw namedParameterError();
    }
}
