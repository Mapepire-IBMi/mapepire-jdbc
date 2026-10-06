package io.github.mapepire_ibmi;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import io.github.mapepire_ibmi.types.ColumnMetadata;
import io.github.mapepire_ibmi.types.QueryMetadata;

/**
 * Column metadata for a {@link MapepireResultSet}, built from the column
 * descriptions the Mapepire server returns with the first block of a query.
 * <p>
 * The server does not report schema or catalog names for result columns, so
 * {@link #getSchemaName(int)} and {@link #getCatalogName(int)} return an empty
 * string as the JDBC specification requires for unknown values.
 */
public class MapepireResultSetMetaData implements ResultSetMetaData {
    private final List<ColumnMetadata> columns;

    public MapepireResultSetMetaData(QueryMetadata metadata) {
        List<ColumnMetadata> cols = metadata == null ? null : metadata.getColumns();
        this.columns = cols == null ? Collections.<ColumnMetadata>emptyList() : cols;
    }

    private ColumnMetadata getColumn(int column) throws SQLException {
        if (column < 1 || column > this.columns.size()) {
            throw new SQLException("Invalid column index: " + column);
        }
        return this.columns.get(column - 1);
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this);
    }

    @Override
    public int getColumnCount() throws SQLException {
        return this.columns.size();
    }

    @Override
    public boolean isAutoIncrement(int column) throws SQLException {
        return getColumn(column).getAutoIncrement();
    }

    @Override
    public boolean isCaseSensitive(int column) throws SQLException {
        return JdbcTypes.isCharacter(getColumnType(column));
    }

    @Override
    public boolean isSearchable(int column) throws SQLException {
        return JdbcTypes.isSearchable(getColumnType(column));
    }

    @Override
    public boolean isCurrency(int column) throws SQLException {
        getColumn(column);
        return false;
    }

    @Override
    public int isNullable(int column) throws SQLException {
        return getColumn(column).getNullable();
    }

    @Override
    public boolean isSigned(int column) throws SQLException {
        return JdbcTypes.isSigned(getColumnType(column));
    }

    @Override
    public int getColumnDisplaySize(int column) throws SQLException {
        return getColumn(column).getDisplaySize();
    }

    @Override
    public String getColumnLabel(int column) throws SQLException {
        ColumnMetadata col = getColumn(column);
        return col.getLabel() != null ? col.getLabel() : col.getName();
    }

    @Override
    public String getColumnName(int column) throws SQLException {
        return getColumn(column).getName();
    }

    @Override
    public String getSchemaName(int column) throws SQLException {
        getColumn(column);
        return "";
    }

    @Override
    public int getPrecision(int column) throws SQLException {
        return getColumn(column).getPrecision();
    }

    @Override
    public int getScale(int column) throws SQLException {
        return getColumn(column).getScale();
    }

    @Override
    public String getTableName(int column) throws SQLException {
        String table = getColumn(column).getTable();
        return table == null ? "" : table;
    }

    @Override
    public String getCatalogName(int column) throws SQLException {
        getColumn(column);
        return "";
    }

    @Override
    public int getColumnType(int column) throws SQLException {
        return JdbcTypes.toSqlType(getColumn(column).getType());
    }

    @Override
    public String getColumnTypeName(int column) throws SQLException {
        return getColumn(column).getType();
    }

    @Override
    public boolean isReadOnly(int column) throws SQLException {
        return getColumn(column).getReadOnly();
    }

    @Override
    public boolean isWritable(int column) throws SQLException {
        return getColumn(column).getWriteable();
    }

    @Override
    public boolean isDefinitelyWritable(int column) throws SQLException {
        getColumn(column);
        return false;
    }

    @Override
    public String getColumnClassName(int column) throws SQLException {
        return JdbcTypes.toClassName(getColumnType(column));
    }
}
