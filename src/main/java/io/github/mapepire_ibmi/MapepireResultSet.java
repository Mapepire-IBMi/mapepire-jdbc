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
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Iterator;
import java.util.Map;

import io.github.mapepire_ibmi.types.QueryResult;

public class MapepireResultSet implements ResultSet {
    private QueryResult<Object> result;
    private Iterator<Object> rowIterator;
    private Map<String, Object> currentRow;
    private Object[] currentRowValues;

    public MapepireResultSet(QueryResult<Object> result) {
        this.result = result;
        this.rowIterator = this.result.getData().iterator();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'unwrap'");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isWrapperFor'");
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean next() throws SQLException {
        if (rowIterator.hasNext()) {
            this.currentRow = (Map<String, Object>) this.rowIterator.next();
            this.currentRowValues = this.currentRow.values().toArray();
            return true;
        }

        return false;
    }

    @Override
    public void close() throws SQLException {
        result = null;
        rowIterator = null;
        currentRow = null;
        currentRowValues = null;
    }

    private Object getValue(int columnIndex) throws SQLException {
        if (this.currentRow == null) {
            throw new SQLException("No current row for ResultSet");
        } else if (columnIndex < 1 || columnIndex > this.currentRowValues.length) {
            throw new SQLException("Invalid column index");
        }

        return this.currentRowValues[columnIndex - 1];
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
    public boolean wasNull() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'wasNull'");
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
        throw new UnsupportedOperationException("Unimplemented method 'getBytes'");
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
        throw new UnsupportedOperationException("Unimplemented method 'getAsciiStream'");
    }

    @Override
    public InputStream getUnicodeStream(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getUnicodeStream'");
    }

    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBinaryStream'");
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
        throw new UnsupportedOperationException("Unimplemented method 'getBytes'");
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
        throw new UnsupportedOperationException("Unimplemented method 'getAsciiStream'");
    }

    @Override
    public InputStream getUnicodeStream(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getUnicodeStream'");
    }

    @Override
    public InputStream getBinaryStream(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBinaryStream'");
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
    public String getCursorName() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getCursorName'");
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMetaData'");
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getObject'");
    }

    @Override
    public Object getObject(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getObject'");
    }

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        if (this.currentRow == null) {
            throw new SQLException("No current row for ResultSet");
        }
        int index = 1;
        for (String key : this.currentRow.keySet()) {
            if (key.equalsIgnoreCase(columnLabel)) {
                return index;
            }
            index++;
        }
        throw new SQLException("Column not found: " + columnLabel);
    }

    @Override
    public Reader getCharacterStream(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getCharacterStream'");
    }

    @Override
    public Reader getCharacterStream(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getCharacterStream'");
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBigDecimal'");
    }

    @Override
    public BigDecimal getBigDecimal(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBigDecimal'");
    }

    @Override
    public boolean isBeforeFirst() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isBeforeFirst'");
    }

    @Override
    public boolean isAfterLast() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isAfterLast'");
    }

    @Override
    public boolean isFirst() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isFirst'");
    }

    @Override
    public boolean isLast() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isLast'");
    }

    @Override
    public void beforeFirst() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'beforeFirst'");
    }

    @Override
    public void afterLast() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'afterLast'");
    }

    @Override
    public boolean first() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'first'");
    }

    @Override
    public boolean last() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'last'");
    }

    @Override
    public int getRow() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRow'");
    }

    @Override
    public boolean absolute(int row) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'absolute'");
    }

    @Override
    public boolean relative(int rows) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'relative'");
    }

    @Override
    public boolean previous() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'previous'");
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setFetchDirection'");
    }

    @Override
    public int getFetchDirection() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getFetchDirection'");
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setFetchSize'");
    }

    @Override
    public int getFetchSize() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getFetchSize'");
    }

    @Override
    public int getType() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getType'");
    }

    @Override
    public int getConcurrency() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getConcurrency'");
    }

    @Override
    public boolean rowUpdated() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'rowUpdated'");
    }

    @Override
    public boolean rowInserted() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'rowInserted'");
    }

    @Override
    public boolean rowDeleted() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'rowDeleted'");
    }

    @Override
    public void updateNull(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNull'");
    }

    @Override
    public void updateBoolean(int columnIndex, boolean x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBoolean'");
    }

    @Override
    public void updateByte(int columnIndex, byte x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateByte'");
    }

    @Override
    public void updateShort(int columnIndex, short x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateShort'");
    }

    @Override
    public void updateInt(int columnIndex, int x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateInt'");
    }

    @Override
    public void updateLong(int columnIndex, long x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateLong'");
    }

    @Override
    public void updateFloat(int columnIndex, float x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateFloat'");
    }

    @Override
    public void updateDouble(int columnIndex, double x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateDouble'");
    }

    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBigDecimal'");
    }

    @Override
    public void updateString(int columnIndex, String x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateString'");
    }

    @Override
    public void updateBytes(int columnIndex, byte[] x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBytes'");
    }

    @Override
    public void updateDate(int columnIndex, Date x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateDate'");
    }

    @Override
    public void updateTime(int columnIndex, Time x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateTime'");
    }

    @Override
    public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateTimestamp'");
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateAsciiStream'");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBinaryStream'");
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateCharacterStream'");
    }

    @Override
    public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateObject'");
    }

    @Override
    public void updateObject(int columnIndex, Object x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateObject'");
    }

    @Override
    public void updateNull(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNull'");
    }

    @Override
    public void updateBoolean(String columnLabel, boolean x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBoolean'");
    }

    @Override
    public void updateByte(String columnLabel, byte x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateByte'");
    }

    @Override
    public void updateShort(String columnLabel, short x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateShort'");
    }

    @Override
    public void updateInt(String columnLabel, int x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateInt'");
    }

    @Override
    public void updateLong(String columnLabel, long x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateLong'");
    }

    @Override
    public void updateFloat(String columnLabel, float x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateFloat'");
    }

    @Override
    public void updateDouble(String columnLabel, double x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateDouble'");
    }

    @Override
    public void updateBigDecimal(String columnLabel, BigDecimal x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBigDecimal'");
    }

    @Override
    public void updateString(String columnLabel, String x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateString'");
    }

    @Override
    public void updateBytes(String columnLabel, byte[] x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBytes'");
    }

    @Override
    public void updateDate(String columnLabel, Date x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateDate'");
    }

    @Override
    public void updateTime(String columnLabel, Time x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateTime'");
    }

    @Override
    public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateTimestamp'");
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateAsciiStream'");
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBinaryStream'");
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, int length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateCharacterStream'");
    }

    @Override
    public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateObject'");
    }

    @Override
    public void updateObject(String columnLabel, Object x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateObject'");
    }

    @Override
    public void insertRow() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'insertRow'");
    }

    @Override
    public void updateRow() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateRow'");
    }

    @Override
    public void deleteRow() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'deleteRow'");
    }

    @Override
    public void refreshRow() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'refreshRow'");
    }

    @Override
    public void cancelRowUpdates() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'cancelRowUpdates'");
    }

    @Override
    public void moveToInsertRow() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'moveToInsertRow'");
    }

    @Override
    public void moveToCurrentRow() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'moveToCurrentRow'");
    }

    @Override
    public Statement getStatement() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getStatement'");
    }

    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getObject'");
    }

    @Override
    public Ref getRef(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRef'");
    }

    @Override
    public Blob getBlob(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBlob'");
    }

    @Override
    public Clob getClob(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getClob'");
    }

    @Override
    public Array getArray(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getArray'");
    }

    @Override
    public Object getObject(String columnLabel, Map<String, Class<?>> map) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getObject'");
    }

    @Override
    public Ref getRef(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRef'");
    }

    @Override
    public Blob getBlob(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBlob'");
    }

    @Override
    public Clob getClob(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getClob'");
    }

    @Override
    public Array getArray(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getArray'");
    }

    @Override
    public Date getDate(int columnIndex, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDate'");
    }

    @Override
    public Date getDate(String columnLabel, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDate'");
    }

    @Override
    public Time getTime(int columnIndex, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTime'");
    }

    @Override
    public Time getTime(String columnLabel, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTime'");
    }

    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTimestamp'");
    }

    @Override
    public Timestamp getTimestamp(String columnLabel, Calendar cal) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTimestamp'");
    }

    @Override
    public URL getURL(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getURL'");
    }

    @Override
    public URL getURL(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getURL'");
    }

    @Override
    public void updateRef(int columnIndex, Ref x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateRef'");
    }

    @Override
    public void updateRef(String columnLabel, Ref x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateRef'");
    }

    @Override
    public void updateBlob(int columnIndex, Blob x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBlob'");
    }

    @Override
    public void updateBlob(String columnLabel, Blob x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBlob'");
    }

    @Override
    public void updateClob(int columnIndex, Clob x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateClob'");
    }

    @Override
    public void updateClob(String columnLabel, Clob x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateClob'");
    }

    @Override
    public void updateArray(int columnIndex, Array x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateArray'");
    }

    @Override
    public void updateArray(String columnLabel, Array x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateArray'");
    }

    @Override
    public RowId getRowId(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRowId'");
    }

    @Override
    public RowId getRowId(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRowId'");
    }

    @Override
    public void updateRowId(int columnIndex, RowId x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateRowId'");
    }

    @Override
    public void updateRowId(String columnLabel, RowId x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateRowId'");
    }

    @Override
    public int getHoldability() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getHoldability'");
    }

    @Override
    public boolean isClosed() throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isClosed'");
    }

    @Override
    public void updateNString(int columnIndex, String nString) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNString'");
    }

    @Override
    public void updateNString(String columnLabel, String nString) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNString'");
    }

    @Override
    public void updateNClob(int columnIndex, NClob nClob) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNClob'");
    }

    @Override
    public void updateNClob(String columnLabel, NClob nClob) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNClob'");
    }

    @Override
    public NClob getNClob(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNClob'");
    }

    @Override
    public NClob getNClob(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNClob'");
    }

    @Override
    public SQLXML getSQLXML(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSQLXML'");
    }

    @Override
    public SQLXML getSQLXML(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSQLXML'");
    }

    @Override
    public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateSQLXML'");
    }

    @Override
    public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateSQLXML'");
    }

    @Override
    public String getNString(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNString'");
    }

    @Override
    public String getNString(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNString'");
    }

    @Override
    public Reader getNCharacterStream(int columnIndex) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNCharacterStream'");
    }

    @Override
    public Reader getNCharacterStream(String columnLabel) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNCharacterStream'");
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNCharacterStream'");
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNCharacterStream'");
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateAsciiStream'");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBinaryStream'");
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateCharacterStream'");
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateAsciiStream'");
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBinaryStream'");
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateCharacterStream'");
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBlob'");
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBlob'");
    }

    @Override
    public void updateClob(int columnIndex, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateClob'");
    }

    @Override
    public void updateClob(String columnLabel, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateClob'");
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNClob'");
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader, long length) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNClob'");
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNCharacterStream'");
    }

    @Override
    public void updateNCharacterStream(String columnLabel, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNCharacterStream'");
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateAsciiStream'");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBinaryStream'");
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateCharacterStream'");
    }

    @Override
    public void updateAsciiStream(String columnLabel, InputStream x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateAsciiStream'");
    }

    @Override
    public void updateBinaryStream(String columnLabel, InputStream x) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBinaryStream'");
    }

    @Override
    public void updateCharacterStream(String columnLabel, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateCharacterStream'");
    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBlob'");
    }

    @Override
    public void updateBlob(String columnLabel, InputStream inputStream) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateBlob'");
    }

    @Override
    public void updateClob(int columnIndex, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateClob'");
    }

    @Override
    public void updateClob(String columnLabel, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateClob'");
    }

    @Override
    public void updateNClob(int columnIndex, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNClob'");
    }

    @Override
    public void updateNClob(String columnLabel, Reader reader) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateNClob'");
    }

    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getObject'");
    }

    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getObject'");
    }

}
