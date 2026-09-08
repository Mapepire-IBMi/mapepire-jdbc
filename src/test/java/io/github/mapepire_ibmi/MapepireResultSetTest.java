package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.mapepire_ibmi.types.QueryResult;

class MapepireResultSetTest {

    private MapepireResultSet rs;

    /**
     * Builds a fake QueryResult from a single row with the given column data.
     * This simulates what the mapepire-sdk returns after executing a query,
     * without needing a real IBM i connection.
     */
    private MapepireResultSet buildResultSet(Map<String, Object> row) {
        QueryResult<Object> result = new QueryResult<>();
        result.setData(Arrays.asList((Object) row));
        result.setHasResults(true);
        result.setIsDone(true);
        return new MapepireResultSet(result);
    }

    @BeforeEach
    void setUp() throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("NAME", "Alice");
        row.put("AGE", 30);
        row.put("SALARY", 99.50d);
        row.put("ACTIVE", true);
        row.put("SCORE", null);

        rs = buildResultSet(row);
    }

    // -------------------------------------------------------------------------
    // next() — row navigation
    // -------------------------------------------------------------------------

    @Test
    void nextReturnsTrueWhenRowsExist() throws SQLException {
        assertTrue(rs.next());
    }

    @Test
    void nextReturnsFalseWhenNoMoreRows() throws SQLException {
        rs.next(); // consume the only row
        assertFalse(rs.next());
    }

    @Test
    void nextReturnsFalseOnEmptyResultSet() throws SQLException {
        QueryResult<Object> empty = new QueryResult<>();
        empty.setData(Collections.emptyList());
        try (MapepireResultSet emptyRs = new MapepireResultSet(empty)) {
            assertFalse(emptyRs.next());
        }
    }

    // -------------------------------------------------------------------------
    // getString — by index and by name
    // -------------------------------------------------------------------------

    @Test
    void getStringByIndexReturnsCorrectValue() throws SQLException {
        rs.next();
        assertEquals("Alice", rs.getString(1));
    }

    @Test
    void getStringByIndexConvertsIntegerToString() throws SQLException {
        rs.next();
        // AGE column is an Integer — must not throw ClassCastException
        assertEquals("30", rs.getString(2));
    }

    @Test
    void getStringByIndexConvertsDoubleToString() throws SQLException {
        rs.next();
        assertEquals("99.5", rs.getString(3));
    }

    @Test
    void getStringByIndexReturnsNullForNullColumn() throws SQLException {
        rs.next();
        assertNull(rs.getString(5));
    }

    @Test
    void getStringByNameReturnsCorrectValue() throws SQLException {
        rs.next();
        assertEquals("Alice", rs.getString("NAME"));
    }

    @Test
    void getStringByNameIsCaseInsensitive() throws SQLException {
        rs.next();
        // IBM i column names are uppercase — callers may use lowercase
        assertEquals("Alice", rs.getString("name"));
    }

    @Test
    void getStringByNameThrowsForUnknownColumn() throws SQLException {
        rs.next();
        assertThrows(SQLException.class, () -> rs.getString("UNKNOWN"));
    }

    @Test
    void getStringThrowsWhenNextNotCalled() {
        // currentRow is null before next() is ever called
        assertThrows(SQLException.class, () -> rs.getString(1));
    }

    @Test
    void getStringThrowsForInvalidIndex() throws SQLException {
        rs.next();
        assertThrows(SQLException.class, () -> rs.getString(0));   // below range
        assertThrows(SQLException.class, () -> rs.getString(99));  // above range
    }

    // -------------------------------------------------------------------------
    // getInt / getLong / getDouble / getFloat / getBoolean — typed getters
    // -------------------------------------------------------------------------

    @Test
    void getIntByIndexReturnsCorrectValue() throws SQLException {
        rs.next();
        assertEquals(30, rs.getInt(2));
    }

    @Test
    void getIntByNameReturnsCorrectValue() throws SQLException {
        rs.next();
        assertEquals(30, rs.getInt("AGE"));
    }

    @Test
    void getIntReturnsZeroForNullColumn() throws SQLException {
        rs.next();
        assertEquals(0, rs.getInt(5)); // SCORE is null
    }

    @Test
    void getDoubleByIndexReturnsCorrectValue() throws SQLException {
        rs.next();
        assertEquals(99.50d, rs.getDouble(3), 0.001);
    }

    @Test
    void getDoubleByNameReturnsCorrectValue() throws SQLException {
        rs.next();
        assertEquals(99.50d, rs.getDouble("SALARY"), 0.001);
    }

    @Test
    void getDoubleReturnsZeroForNullColumn() throws SQLException {
        rs.next();
        assertEquals(0.0d, rs.getDouble(5), 0.0);
    }

    @Test
    void getBooleanByIndexReturnsCorrectValue() throws SQLException {
        rs.next();
        assertTrue(rs.getBoolean(4));
    }

    @Test
    void getBooleanByNameReturnsCorrectValue() throws SQLException {
        rs.next();
        assertTrue(rs.getBoolean("ACTIVE"));
    }

    @Test
    void getBooleanReturnsFalseForNullColumn() throws SQLException {
        rs.next();
        assertFalse(rs.getBoolean(5)); // SCORE is null
    }

    @Test
    void getLongByIndexReturnsCorrectValue() throws SQLException {
        rs.next();
        assertEquals(30L, rs.getLong(2));
    }

    @Test
    void getFloatByIndexReturnsCorrectValue() throws SQLException {
        rs.next();
        assertEquals(99.50f, rs.getFloat(3), 0.01f);
    }

    // -------------------------------------------------------------------------
    // getDate / getTime / getTimestamp
    // -------------------------------------------------------------------------

    @Test
    void getDateByIndexReturnsCorrectValue() throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("HIREDATE", "2024-03-15");
        MapepireResultSet dateRs = buildResultSet(row);
        dateRs.next();
        assertEquals(Date.valueOf("2024-03-15"), dateRs.getDate(1));
    }

    @Test
    void getDateByNameReturnsCorrectValue() throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("HIREDATE", "2024-03-15");
        MapepireResultSet dateRs = buildResultSet(row);
        dateRs.next();
        assertEquals(Date.valueOf("2024-03-15"), dateRs.getDate("HIREDATE"));
    }

    @Test
    void getTimeByIndexReturnsCorrectValue() throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("STARTTIME", "14:30:00");
        MapepireResultSet timeRs = buildResultSet(row);
        timeRs.next();
        assertEquals(Time.valueOf("14:30:00"), timeRs.getTime(1));
    }

    @Test
    void getTimestampByIndexReturnsCorrectValue() throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("CREATED", "2024-03-15 14:30:00");
        MapepireResultSet tsRs = buildResultSet(row);
        tsRs.next();
        assertEquals(Timestamp.valueOf("2024-03-15 14:30:00"), tsRs.getTimestamp(1));
    }

    // -------------------------------------------------------------------------
    // findColumn
    // -------------------------------------------------------------------------

    @Test
    void findColumnReturnsCorrectOneBasedIndex() throws SQLException {
        rs.next();
        assertEquals(1, rs.findColumn("NAME"));
        assertEquals(2, rs.findColumn("AGE"));
        assertEquals(3, rs.findColumn("SALARY"));
    }

    @Test
    void findColumnIsCaseInsensitive() throws SQLException {
        rs.next();
        assertEquals(1, rs.findColumn("name"));
        assertEquals(1, rs.findColumn("Name"));
        assertEquals(1, rs.findColumn("NAME"));
    }

    @Test
    void findColumnThrowsForUnknownColumn() throws SQLException {
        rs.next();
        assertThrows(SQLException.class, () -> rs.findColumn("NONEXISTENT"));
    }

    // -------------------------------------------------------------------------
    // close
    // -------------------------------------------------------------------------

    @Test
    void closeNullifiesInternalState() throws SQLException {
        rs.next();
        rs.close();
        // After close, getString should throw because currentRow is null
        assertThrows(SQLException.class, () -> rs.getString(1));
    }

    @Test
    void isClosedReturnsFalseBeforeClose() throws SQLException {
        assertFalse(rs.isClosed());
    }

    @Test
    void isClosedReturnsTrueAfterClose() throws SQLException {
        rs.close();
        assertTrue(rs.isClosed());
    }

    @Test
    void nextThrowsAfterClose() throws SQLException {
        rs.close();
        assertThrows(SQLException.class, () -> rs.next());
    }

    @Test
    void wasNullThrowsAfterClose() throws SQLException {
        rs.close();
        assertThrows(SQLException.class, () -> rs.wasNull());
    }

    // -------------------------------------------------------------------------
    // wasNull
    // -------------------------------------------------------------------------

    @Test
    void wasNullIsFalseBeforeAnyGetter() throws SQLException {
        rs.next();
        assertFalse(rs.wasNull());
    }

    @Test
    void wasNullIsTrueAfterReadingNullColumn() throws SQLException {
        rs.next();
        assertEquals(0, rs.getInt(5)); // SCORE is null
        assertTrue(rs.wasNull());
    }

    @Test
    void wasNullIsFalseAfterReadingNonNullColumn() throws SQLException {
        rs.next();
        assertEquals(30, rs.getInt(2));
        assertFalse(rs.wasNull());
    }

    @Test
    void wasNullTracksMostRecentGetter() throws SQLException {
        rs.next();
        rs.getInt(5); // null column
        assertTrue(rs.wasNull());
        rs.getString(1); // non-null column
        assertFalse(rs.wasNull());
    }

    @Test
    void wasNullDistinguishesZeroFromNull() throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("REALZERO", 0);
        row.put("NULLCOL", null);
        MapepireResultSet zeroRs = buildResultSet(row);
        zeroRs.next();

        assertEquals(0, zeroRs.getInt(1));
        assertFalse(zeroRs.wasNull()); // a real 0
        assertEquals(0, zeroRs.getInt(2));
        assertTrue(zeroRs.wasNull()); // a NULL surfaced as 0
    }

    // -------------------------------------------------------------------------
    // getObject
    // -------------------------------------------------------------------------

    @Test
    void getObjectByIndexReturnsRawValue() throws SQLException {
        rs.next();
        assertEquals("Alice", rs.getObject(1));
        assertEquals(30, rs.getObject(2));
        assertEquals(99.50d, rs.getObject(3));
        assertEquals(true, rs.getObject(4));
    }

    @Test
    void getObjectByNameReturnsRawValue() throws SQLException {
        rs.next();
        assertEquals("Alice", rs.getObject("NAME"));
        assertEquals(30, rs.getObject("age"));
    }

    @Test
    void getObjectReturnsNullForNullColumn() throws SQLException {
        rs.next();
        assertNull(rs.getObject(5));
        assertTrue(rs.wasNull());
    }

    @Test
    void getObjectThrowsForInvalidIndex() throws SQLException {
        rs.next();
        assertThrows(SQLException.class, () -> rs.getObject(0));
        assertThrows(SQLException.class, () -> rs.getObject(99));
    }

    // -------------------------------------------------------------------------
    // getBigDecimal (no-scale variant)
    // -------------------------------------------------------------------------

    @Test
    void getBigDecimalByIndexReturnsCorrectValue() throws SQLException {
        rs.next();
        assertEquals(0, new java.math.BigDecimal("99.5").compareTo(rs.getBigDecimal(3)));
    }

    @Test
    void getBigDecimalByNameReturnsNullForNullColumn() throws SQLException {
        rs.next();
        assertNull(rs.getBigDecimal("SCORE"));
        assertTrue(rs.wasNull());
    }

    @Test
    void multipleRowsNavigatedCorrectly() throws SQLException {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("ID", 1);
        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("ID", 2);

        QueryResult<Object> result = new QueryResult<>();
        result.setData(Arrays.asList((Object) row1, (Object) row2));
        result.setHasResults(true);
        result.setIsDone(true);
        try (MapepireResultSet multiRs = new MapepireResultSet(result)) {
            assertTrue(multiRs.next());
            assertEquals(1, multiRs.getInt(1));
            assertTrue(multiRs.next());
            assertEquals(2, multiRs.getInt(1));
            assertFalse(multiRs.next());
        }
    }

    // -------------------------------------------------------------------------
    // unimplemented methods throw SQLFeatureNotSupportedException (SQLException)
    // -------------------------------------------------------------------------

    @Test
    void unimplementedMethodsThrowSQLFeatureNotSupportedException() {
        assertThrows(SQLFeatureNotSupportedException.class, () -> rs.getMetaData());
    }
}
