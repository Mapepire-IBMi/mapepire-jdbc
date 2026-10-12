package io.github.mapepire_ibmi;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes the changes made through an updatable {@link MapepireResultSet} to the
 * database.
 * <p>
 * The Mapepire server has no positioned updates ({@code WHERE CURRENT OF}), so
 * each change runs as its own {@code UPDATE}, {@code INSERT}, or
 * {@code DELETE} statement, and the row to change is identified by its
 * primary key. That requires a query that selects from a single table (see
 * {@link TableReference}) and includes every primary key column of that table.
 */
final class ResultSetUpdater {
    private final MapepireConnection connection;
    private final TableReference table;
    private final List<String> columnNames;
    private final int[] keyColumns;

    private ResultSetUpdater(MapepireConnection connection, TableReference table, List<String> columnNames,
            int[] keyColumns) {
        this.connection = connection;
        this.table = table;
        this.columnNames = columnNames;
        this.keyColumns = keyColumns;
    }

    /**
     * Creates an updater for the rows of the given query.
     *
     * @param connection  The connection to run changes on.
     * @param sql         The query that produced the result set.
     * @param columnNames The name of each result set column in the underlying
     *                    table, in column order.
     * @throws SQLException If the query's rows cannot be updated.
     */
    static ResultSetUpdater create(MapepireConnection connection, String sql, List<String> columnNames)
            throws SQLException {
        TableReference table = TableReference.fromQuery(sql);
        if (table == null) {
            throw notUpdatable("the query must select from a single table");
        }

        // An unqualified table resolves against the current schema.
        String schema = table.getSchema() != null ? table.getSchema() : connection.getSchema();
        List<String> keys = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getPrimaryKeys(null, schema, table.getTable())) {
            while (rs.next()) {
                keys.add(rs.getString("COLUMN_NAME").trim());
            }
        }
        if (keys.isEmpty()) {
            throw notUpdatable("no primary key was found for table " + schema + "." + table.getTable());
        }

        int[] keyColumns = new int[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            int index = columnNames.indexOf(keys.get(i));
            if (index < 0) {
                throw notUpdatable("the query must select every primary key column of " + table
                        + ", but it does not select " + keys.get(i));
            }
            keyColumns[i] = index + 1;
        }
        return new ResultSetUpdater(connection, table, columnNames, keyColumns);
    }

    private static SQLException notUpdatable(String reason) {
        return new SQLException("ResultSet is not updatable: " + reason);
    }

    /**
     * Updates the row whose current values are {@code row} with the given changes.
     *
     * @param row     The row's current values, in column order.
     * @param changes The new values, by 1-based column index.
     */
    void update(Object[] row, Map<Integer, Object> changes) throws SQLException {
        StringBuilder sql = new StringBuilder("UPDATE ").append(this.table.toSql()).append(" SET ");
        List<Object> parameters = new ArrayList<>();
        String separator = "";
        for (Map.Entry<Integer, Object> change : changes.entrySet()) {
            sql.append(separator).append(column(change.getKey())).append(" = ?");
            parameters.add(change.getValue());
            separator = ", ";
        }
        appendKeyCondition(sql, parameters, row);
        checkRowFound(execute(sql.toString(), parameters));
    }

    /**
     * Inserts a row with the given values. Columns without a value get their
     * default.
     *
     * @param values The column values, by 1-based column index.
     */
    void insert(Map<Integer, Object> values) throws SQLException {
        StringBuilder columns = new StringBuilder();
        StringBuilder markers = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        for (Map.Entry<Integer, Object> value : values.entrySet()) {
            if (!parameters.isEmpty()) {
                columns.append(", ");
                markers.append(", ");
            }
            columns.append(column(value.getKey()));
            markers.append('?');
            parameters.add(value.getValue());
        }
        execute("INSERT INTO " + this.table.toSql() + " (" + columns + ") VALUES (" + markers + ")", parameters);
    }

    /**
     * Deletes the row whose current values are {@code row}.
     *
     * @param row The row's current values, in column order.
     */
    void delete(Object[] row) throws SQLException {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(this.table.toSql());
        List<Object> parameters = new ArrayList<>();
        appendKeyCondition(sql, parameters, row);
        checkRowFound(execute(sql.toString(), parameters));
    }

    private String column(int columnIndex) {
        return TableReference.quoteIdentifier(this.columnNames.get(columnIndex - 1));
    }

    private void appendKeyCondition(StringBuilder sql, List<Object> parameters, Object[] row) {
        String separator = " WHERE ";
        for (int keyColumn : this.keyColumns) {
            sql.append(separator).append(column(keyColumn)).append(" = ?");
            parameters.add(row[keyColumn - 1]);
            separator = " AND ";
        }
    }

    private void checkRowFound(int updateCount) throws SQLException {
        if (updateCount == 0) {
            throw new SQLException("The row was not found in " + this.table
                    + "; it may have been changed or deleted since it was read");
        }
    }

    private int execute(String sql, List<Object> parameters) throws SQLException {
        try (PreparedStatement statement = this.connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.size(); i++) {
                statement.setObject(i + 1, parameters.get(i));
            }
            return statement.executeUpdate();
        }
    }
}
