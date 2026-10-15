package io.github.mapepire_ibmi;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Types;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.github.mapepire_ibmi.types.ColumnMetadata;
import io.github.mapepire_ibmi.types.QueryResult;

/**
 * A result set over the rows of a query.
 * <p>
 * A {@link ResultSet#TYPE_FORWARD_ONLY} result set holds the block of rows its
 * statement fetched. A {@link ResultSet#TYPE_SCROLL_INSENSITIVE} result set
 * holds every row of the query, fetched when it is created, so the cursor can
 * move to any row; it does not see later changes made by others.
 * <p>
 * A {@link ResultSet#CONCUR_UPDATABLE} result set writes its changes with
 * separate SQL statements (see {@link ResultSetUpdater}), which requires a query
 * over a single table that selects every primary key column. Its own updates
 * and deletes are visible in the result set; inserted rows are not added.
 */
public class MapepireResultSet implements ResultSet {
    private final MapepireStatement statement;
    private final String sql;
    private final int type;
    private final int concurrency;
    private final List<String> columnLabels;
    // Values set by the updater methods on the current row or the insert row, by column index
    private final Map<Integer, Object> rowChanges = new TreeMap<>();
    private QueryResult<Object> result;
    private List<Object> rows;
    // 0 is before the first row and rows.size() + 1 is after the last row
    private int position;
    private Map<String, Object> currentRow;
    private boolean closed;
    private boolean lastWasNull;
    private Object[] currentRowValues;
    private ResultSetMetaData metaData;
    private boolean onInsertRow;
    private ResultSetUpdater updater;
    private int fetchDirection = FETCH_FORWARD;
    private int fetchSize;

    public MapepireResultSet(QueryResult<Object> result) {
        this(null, null, result, TYPE_FORWARD_ONLY, CONCUR_READ_ONLY);
    }

    /**
     * Create a result set over the rows of a statement's execution.
     *
     * @param statement   The statement that produced the result set.
     * @param sql         The query, which tells an updatable result set which
     *                    table to change.
     * @param result      The query result holding the rows.
     * @param type        The result set type.
     * @param concurrency The result set concurrency.
     */
    MapepireResultSet(MapepireStatement statement, String sql, QueryResult<Object> result, int type,
            int concurrency) {
        this.statement = statement;
        this.sql = sql;
        this.result = result;
        this.type = type;
        this.concurrency = concurrency;
        // A copy, since deleting through an updatable result set removes rows
        this.rows = result.getData() == null ? new ArrayList<Object>() : new ArrayList<Object>(result.getData());
        this.columnLabels = columnLabels(result, this.rows);
    }

    @SuppressWarnings("unchecked")
    private static List<String> columnLabels(QueryResult<Object> result, List<Object> rows) {
        if (!rows.isEmpty()) {
            return new ArrayList<>(((Map<String, Object>) rows.get(0)).keySet());
        }
        // Without rows, the column metadata still names the columns (e.g. for the insert row)
        List<String> labels = new ArrayList<>();
        if (result.getMetadata() != null && result.getMetadata().getColumns() != null) {
            for (ColumnMetadata column : result.getMetadata().getColumns()) {
                labels.add(column.getLabel() != null ? column.getLabel() : column.getName());
            }
        }
        return labels;
    }

    private void checkClosed() throws SQLException {
        if (this.closed) {
            throw new SQLException("ResultSet is closed");
        }
    }

    private void checkScrollable() throws SQLException {
        checkClosed();
        if (this.type == TYPE_FORWARD_ONLY) {
            throw new SQLException("Operation not allowed on a TYPE_FORWARD_ONLY ResultSet");
        }
    }

    private void checkColumnIndex(int columnIndex) throws SQLException {
        if (columnIndex < 1 || columnIndex > this.columnLabels.size()) {
            throw new SQLException("Invalid column index");
        }
    }

    private Object getValue(int columnIndex) throws SQLException {
        checkClosed();
        if (!this.onInsertRow && this.currentRow == null) {
            throw new SQLException("No current row for ResultSet");
        }
        checkColumnIndex(columnIndex);

        // Columns not yet set on the insert row read as null
        Object value = this.onInsertRow ? this.rowChanges.get(columnIndex) : this.currentRowValues[columnIndex - 1];
        this.lastWasNull = value == null;
        return value;
    }

    /**
     * Move the cursor to the given row number, or to before the first or after
     * the last row if the number is out of range. Moving the cursor leaves the
     * insert row and discards changes not written by {@link #updateRow()}.
     *
     * @return Whether the cursor is on a row.
     */
    @SuppressWarnings("unchecked")
    private boolean moveTo(long row) {
        this.onInsertRow = false;
        this.rowChanges.clear();
        this.position = (int) Math.max(0, Math.min(row, this.rows.size() + 1L));
        if (this.position >= 1 && this.position <= this.rows.size()) {
            this.currentRow = (Map<String, Object>) this.rows.get(this.position - 1);
            this.currentRowValues = this.currentRow.values().toArray();
            return true;
        }

        this.currentRow = null;
        this.currentRowValues = null;
        return false;
    }

    private Number getNumber(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return (Number) value;
        } else if (value instanceof String) {
            try {
                return new BigDecimal(((String) value).trim());
            } catch (NumberFormatException e) {
                throw new SQLException("Cannot convert value of column " + columnIndex + " to a number: " + value, e);
            }
        }

        throw new SQLException(
                "Cannot convert value of type " + value.getClass().getName() + " in column " + columnIndex
                        + " to a number");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'unwrap'");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'isWrapperFor'");
    }

    @Override
    public boolean next() throws SQLException {
        checkClosed();
        return moveTo((long) this.position + 1);
    }

    @Override
    public void close() throws SQLException {
        closed = true;
        result = null;
        rows = null;
        currentRow = null;
        currentRowValues = null;
        rowChanges.clear();
        updater = null;
    }

    @Override
    public boolean wasNull() throws SQLException {
        if (this.closed) {
            throw new SQLException("ResultSet is closed");
        }

        return this.lastWasNull;
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        return value == null ? null : value.toString();
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) {
            return false;
        } else if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        } else if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty() || text.equalsIgnoreCase("false") || text.equals("0") || text.equalsIgnoreCase("N")) {
                return false;
            } else if (text.equalsIgnoreCase("true") || text.equals("1") || text.equalsIgnoreCase("Y")) {
                return true;
            }
            throw new SQLException("Cannot convert value of column " + columnIndex + " to boolean: " + value);
        }

        throw new SQLException(
                "Cannot convert value of type " + value.getClass().getName() + " in column " + columnIndex
                        + " to boolean");
    }

    @Override
    public byte getByte(int columnIndex) throws SQLException {
        Number value = getNumber(columnIndex);
        return value == null ? 0 : value.byteValue();
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        Number value = getNumber(columnIndex);
        return value == null ? 0 : value.shortValue();
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        Number value = getNumber(columnIndex);
        return value == null ? 0 : value.intValue();
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        Number value = getNumber(columnIndex);
        return value == null ? 0L : value.longValue();
    }

    @Override
    public float getFloat(int columnIndex) throws SQLException {
        Number value = getNumber(columnIndex);
        return value == null ? 0.0f : value.floatValue();
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        Number value = getNumber(columnIndex);
        return value == null ? 0.0d : value.doubleValue();
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException {
        Number value = getNumber(columnIndex);
        if (value == null) {
            return null;
        }

        BigDecimal decimal = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
        return decimal.setScale(scale, RoundingMode.HALF_UP);
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getBytes'");
    }

    @Override
    public Date getDate(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        if (value == null) {
            return null;
        }
        try {
            return Date.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid date value for column " + columnIndex + ": " + value, e);
        }
    }

    @Override
    public Time getTime(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        if (value == null) {
            return null;
        }
        try {
            return Time.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid time value for column " + columnIndex + ": " + value, e);
        }
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        String value = getString(columnIndex);
        if (value == null) {
            return null;
        }
        try {
            return Timestamp.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid timestamp value for column " + columnIndex + ": " + value, e);
        }
    }

    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getAsciiStream'");
    }

    @Override
    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getUnicodeStream'");
    }

    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getBinaryStream'");
    }

    @Override
    public String getString(String columnLabel) throws SQLException {
        return getString(findColumn(columnLabel));
    }

    @Override
    public boolean getBoolean(String columnLabel) throws SQLException {
        return getBoolean(findColumn(columnLabel));
    }

    @Override
    public byte getByte(String columnLabel) throws SQLException {
        return getByte(findColumn(columnLabel));
    }

    @Override
    public short getShort(String columnLabel) throws SQLException {
        return getShort(findColumn(columnLabel));
    }

    @Override
    public int getInt(String columnLabel) throws SQLException {
        return getInt(findColumn(columnLabel));
    }

    @Override
    public long getLong(String columnLabel) throws SQLException {
        return getLong(findColumn(columnLabel));
    }

    @Override
    public float getFloat(String columnLabel) throws SQLException {
        return getFloat(findColumn(columnLabel));
    }

    @Override
    public double getDouble(String columnLabel) throws SQLException {
        return getDouble(findColumn(columnLabel));
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException {
        return getBigDecimal(findColumn(columnLabel), scale);
    }

    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getBytes'");
    }

    @Override
    public Date getDate(String columnLabel) throws SQLException {
        return getDate(findColumn(columnLabel));
    }

    @Override
    public Time getTime(String columnLabel) throws SQLException {
        return getTime(findColumn(columnLabel));
    }

    @Override
    public Timestamp getTimestamp(String columnLabel) throws SQLException {
        return getTimestamp(findColumn(columnLabel));
    }

    @Override
    public InputStream getAsciiStream(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getAsciiStream'");
    }

    @Override
    public InputStream getUnicodeStream(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getUnicodeStream'");
    }

    @Override
    public InputStream getBinaryStream(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getBinaryStream'");
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        if (this.closed) {
            throw new SQLException("ResultSet is closed");
        }
        return null;
    }

    @Override
    public void clearWarnings() throws SQLException {
        if (this.closed) {
            throw new SQLException("ResultSet is closed");
        }
    }

    @Override
    public String getCursorName() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getCursorName'");
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        if (this.closed) {
            throw new SQLException("ResultSet is closed");
        }
        if (this.metaData == null) {
            if (this.result.getMetadata() == null) {
                throw new SQLException("Column metadata is not available for this result");
            }
            this.metaData = new MapepireResultSetMetaData(this.result.getMetadata());
        }
        return this.metaData;
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) {
            return null;
        }
        // If no column metadata is available, return the raw JSON value as-is.
        int sqlType;
        try {
            sqlType = getMetaData().getColumnType(columnIndex);
        } catch (SQLException e) {
            return value;
        }
        switch (sqlType) {
            case Types.SMALLINT:
            case Types.INTEGER:
                return getNumber(columnIndex).intValue();
            case Types.BIGINT:
                return getNumber(columnIndex).longValue();
            case Types.DECIMAL:
            case Types.NUMERIC:
                return getBigDecimal(columnIndex);
            case Types.REAL:
                return getNumber(columnIndex).floatValue();
            case Types.FLOAT:
            case Types.DOUBLE:
                return getNumber(columnIndex).doubleValue();
            case Types.DATE:
                return getDate(columnIndex);
            case Types.TIME:
                return getTime(columnIndex);
            case Types.TIMESTAMP:
                return getTimestamp(columnIndex);
            case Types.BOOLEAN:
                return getBoolean(columnIndex);
            default:
                return value;
        }
    }

    @Override
    public Object getObject(String columnLabel) throws SQLException {
        return getObject(findColumn(columnLabel));
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        checkClosed();
        for (int i = 0; i < this.columnLabels.size(); i++) {
            if (this.columnLabels.get(i).equalsIgnoreCase(columnLabel)) {
                return i + 1;
            }
        }
        throw new SQLException("Column not found: " + columnLabel);
    }

    @Override
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getCharacterStream'");
    }

    @Override
    public Reader getCharacterStream(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getCharacterStream'");
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        Object value = getValue(columnIndex);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert value of column " + columnIndex + " to BigDecimal: " + value, e);
        }
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        return getBigDecimal(findColumn(columnLabel));
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        checkClosed();
        return !this.rows.isEmpty() && this.position == 0;
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        checkClosed();
        return !this.rows.isEmpty() && this.position > this.rows.size();
    }

    @Override
    public boolean isFirst() throws SQLException {
        checkClosed();
        return this.currentRow != null && this.position == 1;
    }

    @Override
    public boolean isLast() throws SQLException {
        checkClosed();
        return this.currentRow != null && this.position == this.rows.size();
    }

    @Override
    public void beforeFirst() throws SQLException {
        checkScrollable();
        moveTo(0);
    }

    @Override
    public void afterLast() throws SQLException {
        checkScrollable();
        moveTo(this.rows.size() + 1L);
    }

    @Override
    public boolean first() throws SQLException {
        checkScrollable();
        return moveTo(1);
    }

    @Override
    public boolean last() throws SQLException {
        checkScrollable();
        return moveTo(this.rows.size());
    }

    @Override
    public int getRow() throws SQLException {
        checkClosed();
        return this.onInsertRow || this.currentRow == null ? 0 : this.position;
    }

    @Override
    public boolean absolute(int row) throws SQLException {
        checkScrollable();
        // A negative row number counts back from the end: -1 is the last row
        return moveTo(row >= 0 ? row : this.rows.size() + 1L + row);
    }

    @Override
    public boolean relative(int rows) throws SQLException {
        checkScrollable();
        return moveTo((long) this.position + rows);
    }

    @Override
    public boolean previous() throws SQLException {
        checkScrollable();
        return moveTo(this.position - 1L);
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        checkClosed();
        if (direction != FETCH_FORWARD && direction != FETCH_REVERSE && direction != FETCH_UNKNOWN) {
            throw new SQLException("Invalid fetch direction: " + direction);
        }
        this.fetchDirection = direction;
    }

    @Override
    public int getFetchDirection() throws SQLException {
        checkClosed();
        return this.fetchDirection;
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        checkClosed();
        if (rows < 0) {
            throw new SQLException("Fetch size must not be negative");
        }
        this.fetchSize = rows;
    }

    @Override
    public int getFetchSize() throws SQLException {
        checkClosed();
        return this.fetchSize;
    }

    @Override
    public int getType() throws SQLException {
        checkClosed();
        return this.type;
    }

    @Override
    public int getConcurrency() throws SQLException {
        checkClosed();
        return this.concurrency;
    }

    // Changes are not detected (see DatabaseMetaData.updatesAreDetected): updates are
    // applied to the row in place, deleted rows are removed, and inserted rows are not
    // added.

    @Override
    public boolean rowUpdated() throws SQLException {
        checkClosed();
        return false;
    }

    @Override
    public boolean rowInserted() throws SQLException {
        checkClosed();
        return false;
    }

    @Override
    public boolean rowDeleted() throws SQLException {
        checkClosed();
        return false;
    }

    private void checkUpdatable() throws SQLException {
        checkClosed();
        if (this.concurrency != CONCUR_UPDATABLE) {
            throw new SQLException("ResultSet is not updatable: its concurrency is CONCUR_READ_ONLY");
        }
    }

    private void checkOnCurrentRow(String method) throws SQLException {
        checkUpdatable();
        if (this.onInsertRow) {
            throw new SQLException("Cannot call " + method + "() when the cursor is on the insert row");
        } else if (this.currentRow == null) {
            throw new SQLException("No current row for ResultSet");
        }
    }

    /**
     * The updater for this result set, created on first use. Creating it checks
     * that the query's rows can be updated.
     */
    private ResultSetUpdater getUpdater() throws SQLException {
        if (this.updater == null) {
            this.updater = ResultSetUpdater.create(this.statement.getMapepireConnection(), this.sql, columnNames());
        }
        return this.updater;
    }

    /**
     * The name of each column in its table. Changes are written by name, and a
     * column label may be an alias.
     */
    private List<String> columnNames() {
        List<ColumnMetadata> columns = this.result.getMetadata() == null ? null
                : this.result.getMetadata().getColumns();
        if (columns == null || columns.size() != this.columnLabels.size()) {
            return this.columnLabels;
        }

        List<String> names = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            String name = columns.get(i).getName();
            names.add(name != null ? name : this.columnLabels.get(i));
        }
        return names;
    }

    /**
     * Record a new value for a column of the current row or the insert row. The
     * value is written to the database by {@link #updateRow()} or
     * {@link #insertRow()}.
     */
    private void setColumnValue(int columnIndex, Object value) throws SQLException {
        checkUpdatable();
        if (!this.onInsertRow && this.currentRow == null) {
            throw new SQLException("No current row for ResultSet");
        }
        checkColumnIndex(columnIndex);
        getUpdater();
        this.rowChanges.put(columnIndex, value);
    }

    @Override
    public void updateNull(int columnIndex) throws SQLException {
        setColumnValue(columnIndex, null);
    }

    @Override
    public void updateBoolean(int columnIndex, boolean x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateByte(int columnIndex, byte x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateShort(int columnIndex, short x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateInt(int columnIndex, int x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateLong(int columnIndex, long x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateFloat(int columnIndex, float x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateDouble(int columnIndex, double x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateString(int columnIndex, String x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateBytes(int columnIndex, byte[] x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBytes'");
    }

    @Override
    public void updateDate(int columnIndex, Date x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateTime(int columnIndex, Time x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateAsciiStream'");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBinaryStream'");
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateCharacterStream'");
    }

    @Override
    public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
        // The scale applies to BigDecimal values; the length of streams does not apply
        setColumnValue(columnIndex, x instanceof BigDecimal
                ? ((BigDecimal) x).setScale(scaleOrLength, RoundingMode.HALF_UP) : x);
    }

    @Override
    public void updateObject(int columnIndex, Object x) throws SQLException {
        setColumnValue(columnIndex, x);
    }

    @Override
    public void updateNull(String columnLabel) throws SQLException {
        updateNull(findColumn(columnLabel));
    }

    @Override
    public void updateBoolean(String columnLabel, boolean x) throws SQLException {
        updateBoolean(findColumn(columnLabel), x);
    }

    @Override
    public void updateByte(String columnLabel, byte x) throws SQLException {
        updateByte(findColumn(columnLabel), x);
    }

    @Override
    public void updateShort(String columnLabel, short x) throws SQLException {
        updateShort(findColumn(columnLabel), x);
    }

    @Override
    public void updateInt(String columnLabel, int x) throws SQLException {
        updateInt(findColumn(columnLabel), x);
    }

    @Override
    public void updateLong(String columnLabel, long x) throws SQLException {
        updateLong(findColumn(columnLabel), x);
    }

    @Override
    public void updateFloat(String columnLabel, float x) throws SQLException {
        updateFloat(findColumn(columnLabel), x);
    }

    @Override
    public void updateDouble(String columnLabel, double x) throws SQLException {
        updateDouble(findColumn(columnLabel), x);
    }

    @Override
    public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
        updateBigDecimal(findColumn(columnLabel), x);
    }

    @Override
    public void updateString(String columnLabel, String x) throws SQLException {
        updateString(findColumn(columnLabel), x);
    }

    @Override
    public void updateBytes(String columnLabel, byte[] x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBytes'");
    }

    @Override
    public void updateDate(String columnLabel, Date x) throws SQLException {
        updateDate(findColumn(columnLabel), x);
    }

    @Override
    public void updateTime(String columnLabel, Time x) throws SQLException {
        updateTime(findColumn(columnLabel), x);
    }

    @Override
    public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
        updateTimestamp(findColumn(columnLabel), x);
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateAsciiStream'");
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBinaryStream'");
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateCharacterStream'");
    }

    @Override
    public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
        updateObject(findColumn(columnLabel), x, scaleOrLength);
    }

    @Override
    public void updateObject(String columnLabel, Object x) throws SQLException {
        updateObject(findColumn(columnLabel), x);
    }

    @Override
    public void insertRow() throws SQLException {
        checkUpdatable();
        if (!this.onInsertRow) {
            throw new SQLException("Cannot call insertRow() when the cursor is not on the insert row");
        } else if (this.rowChanges.isEmpty()) {
            throw new SQLException("No column values have been set on the insert row");
        }
        getUpdater().insert(this.rowChanges);
        this.rowChanges.clear();
    }

    @Override
    public void updateRow() throws SQLException {
        checkOnCurrentRow("updateRow");
        if (this.rowChanges.isEmpty()) {
            return;
        }
        getUpdater().update(this.currentRowValues, this.rowChanges);

        Map<String, Object> updatedRow = new LinkedHashMap<>(this.currentRow);
        for (Map.Entry<Integer, Object> change : this.rowChanges.entrySet()) {
            updatedRow.put(this.columnLabels.get(change.getKey() - 1), change.getValue());
        }
        this.rows.set(this.position - 1, updatedRow);
        moveTo(this.position);
    }

    @Override
    public void deleteRow() throws SQLException {
        checkOnCurrentRow("deleteRow");
        getUpdater().delete(this.currentRowValues);

        // The cursor moves to the row before, so next() moves to the row that followed
        this.rows.remove(this.position - 1);
        moveTo(this.position - 1L);
    }

    @Override
    public void refreshRow() throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'refreshRow'");
    }

    @Override
    public void cancelRowUpdates() throws SQLException {
        checkUpdatable();
        if (this.onInsertRow) {
            throw new SQLException("Cannot call cancelRowUpdates() when the cursor is on the insert row");
        }
        this.rowChanges.clear();
    }

    @Override
    public void moveToInsertRow() throws SQLException {
        checkUpdatable();
        getUpdater();
        this.rowChanges.clear();
        this.onInsertRow = true;
    }

    @Override
    public void moveToCurrentRow() throws SQLException {
        checkUpdatable();
        if (this.onInsertRow) {
            this.onInsertRow = false;
            this.rowChanges.clear();
        }
    }

    @Override
    public Statement getStatement() throws SQLException {
        checkClosed();
        // Null for result sets the driver builds itself, such as DatabaseMetaData results
        return this.statement;
    }

    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getObject'");
    }

    @Override
    public Ref getRef(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getRef'");
    }

    @Override
    public Blob getBlob(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getBlob'");
    }

    @Override
    public Clob getClob(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getClob'");
    }

    @Override
    public Array getArray(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getArray'");
    }

    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getObject'");
    }

    @Override
    public Ref getRef(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getRef'");
    }

    @Override
    public Blob getBlob(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getBlob'");
    }

    @Override
    public Clob getClob(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getClob'");
    }

    @Override
    public Array getArray(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getArray'");
    }

    @Override
    public Date getDate(int columnIndex, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getDate'");
    }

    @Override
    public Date getDate(String columnLabel, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getDate'");
    }

    @Override
    public Time getTime(int columnIndex, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getTime'");
    }

    @Override
    public Time getTime(String columnLabel, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getTime'");
    }

    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getTimestamp'");
    }

    @Override
    public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getTimestamp'");
    }

    @Override
    public URL getURL(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getURL'");
    }

    @Override
    public URL getURL(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getURL'");
    }

    @Override
    public void updateRef(int columnIndex, Ref x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateRef'");
    }

    @Override
    public void updateRef(String columnLabel, Ref x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateRef'");
    }

    @Override
    public void updateBlob(int columnIndex, Blob x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBlob'");
    }

    @Override
    public void updateBlob(String columnLabel, Blob x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBlob'");
    }

    @Override
    public void updateClob(int columnIndex, Clob x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateClob'");
    }

    @Override
    public void updateClob(String columnLabel, Clob x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateClob'");
    }

    @Override
    public void updateArray(int columnIndex, Array x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateArray'");
    }

    @Override
    public void updateArray(String columnLabel, Array x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateArray'");
    }

    @Override
    public RowId getRowId(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getRowId'");
    }

    @Override
    public RowId getRowId(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getRowId'");
    }

    @Override
    public void updateRowId(int columnIndex, RowId x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateRowId'");
    }

    @Override
    public void updateRowId(String columnLabel, RowId x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateRowId'");
    }

    @Override
    public int getHoldability() throws SQLException {
        checkClosed();
        return HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return this.closed;
    }

    @Override
    public void updateNString(int columnIndex, String nString) throws SQLException {
        setColumnValue(columnIndex, nString);
    }

    @Override
    public void updateNString(String columnLabel, String nString) throws SQLException {
        updateNString(findColumn(columnLabel), nString);
    }

    @Override
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateNClob'");
    }

    @Override
    public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateNClob'");
    }

    @Override
    public NClob getNClob(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getNClob'");
    }

    @Override
    public NClob getNClob(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getNClob'");
    }

    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getSQLXML'");
    }

    @Override
    public SQLXML getSQLXML(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getSQLXML'");
    }

    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateSQLXML'");
    }

    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateSQLXML'");
    }

    @Override
    public String getNString(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getNString'");
    }

    @Override
    public String getNString(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getNString'");
    }

    @Override
    public Reader getNCharacterStream(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getNCharacterStream'");
    }

    @Override
    public Reader getNCharacterStream(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getNCharacterStream'");
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateNCharacterStream'");
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateNCharacterStream'");
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateAsciiStream'");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBinaryStream'");
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateCharacterStream'");
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateAsciiStream'");
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBinaryStream'");
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateCharacterStream'");
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBlob'");
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBlob'");
    }

    @Override
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateClob'");
    }

    @Override
    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateClob'");
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateNClob'");
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateNClob'");
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateNCharacterStream'");
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateNCharacterStream'");
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateAsciiStream'");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBinaryStream'");
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateCharacterStream'");
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateAsciiStream'");
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBinaryStream'");
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateCharacterStream'");
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBlob'");
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateBlob'");
    }

    @Override
    public void updateClob(int columnIndex, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateClob'");
    }

    @Override
    public void updateClob(String columnLabel, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateClob'");
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateNClob'");
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'updateNClob'");
    }

    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getObject'");
    }

    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        // TODO Auto-generated method stub
        throw new SQLFeatureNotSupportedException("Unimplemented method 'getObject'");
    }

}
