package io.github.mapepire_ibmi;

import java.sql.Types;
import java.util.Locale;

/**
 * Maps the Db2 for i type names reported by the Mapepire server (as produced by
 * JTOpen's {@code ResultSetMetaData.getColumnTypeName}) to {@link java.sql.Types}
 * codes and the standard JDBC Java class for each type.
 */
final class JdbcTypes {
    private static final String FOR_BIT_DATA = "FOR BIT DATA";

    private JdbcTypes() {
    }

    /**
     * Returns the {@link java.sql.Types} code for a Db2 for i type name, or
     * {@link Types#OTHER} if the name is not recognised.
     */
    static int toSqlType(String typeName) {
        if (typeName == null) {
            return Types.OTHER;
        }

        String name = typeName.trim().toUpperCase(Locale.ROOT);
        if (name.endsWith(FOR_BIT_DATA)) {
            if (name.startsWith("LONG")) {
                return Types.LONGVARBINARY;
            }
            return name.startsWith("VARCHAR") ? Types.VARBINARY : Types.BINARY;
        }

        switch (name) {
            case "SMALLINT":
                return Types.SMALLINT;
            case "INTEGER":
            case "INT":
                return Types.INTEGER;
            case "BIGINT":
                return Types.BIGINT;
            case "DECIMAL":
            case "DECFLOAT":
                return Types.DECIMAL;
            case "NUMERIC":
                return Types.NUMERIC;
            case "REAL":
                return Types.REAL;
            case "FLOAT":
                return Types.FLOAT;
            case "DOUBLE":
                return Types.DOUBLE;
            case "CHAR":
            case "GRAPHIC":
                return Types.CHAR;
            case "VARCHAR":
            case "VARGRAPHIC":
                return Types.VARCHAR;
            case "LONG VARCHAR":
            case "LONG VARGRAPHIC":
                return Types.LONGVARCHAR;
            case "CLOB":
            case "DBCLOB":
                return Types.CLOB;
            case "NCHAR":
                return Types.NCHAR;
            case "NVARCHAR":
                return Types.NVARCHAR;
            case "NCLOB":
                return Types.NCLOB;
            case "BINARY":
                return Types.BINARY;
            case "VARBINARY":
                return Types.VARBINARY;
            case "BLOB":
                return Types.BLOB;
            case "DATE":
                return Types.DATE;
            case "TIME":
                return Types.TIME;
            case "TIMESTAMP":
                return Types.TIMESTAMP;
            case "XML":
                return Types.SQLXML;
            case "BOOLEAN":
                return Types.BOOLEAN;
            case "ROWID":
                return Types.ROWID;
            case "DATALINK":
                return Types.DATALINK;
            case "DISTINCT":
                return Types.DISTINCT;
            case "ARRAY":
                return Types.ARRAY;
            default:
                return Types.OTHER;
        }
    }

    /**
     * Returns the fully-qualified Java class name that the JDBC specification
     * associates with the given {@link java.sql.Types} code.
     */
    static String toClassName(int sqlType) {
        switch (sqlType) {
            case Types.SMALLINT:
            case Types.INTEGER:
                return Integer.class.getName();
            case Types.BIGINT:
                return Long.class.getName();
            case Types.DECIMAL:
            case Types.NUMERIC:
                return java.math.BigDecimal.class.getName();
            case Types.REAL:
                return Float.class.getName();
            case Types.FLOAT:
            case Types.DOUBLE:
                return Double.class.getName();
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
                return String.class.getName();
            case Types.CLOB:
                return java.sql.Clob.class.getName();
            case Types.NCLOB:
                return java.sql.NClob.class.getName();
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
                return byte[].class.getName();
            case Types.BLOB:
                return java.sql.Blob.class.getName();
            case Types.DATE:
                return java.sql.Date.class.getName();
            case Types.TIME:
                return java.sql.Time.class.getName();
            case Types.TIMESTAMP:
                return java.sql.Timestamp.class.getName();
            case Types.SQLXML:
                return java.sql.SQLXML.class.getName();
            case Types.BOOLEAN:
                return Boolean.class.getName();
            case Types.ROWID:
                return java.sql.RowId.class.getName();
            case Types.DATALINK:
                return java.net.URL.class.getName();
            case Types.ARRAY:
                return java.sql.Array.class.getName();
            default:
                return Object.class.getName();
        }
    }

    static boolean isSigned(int sqlType) {
        switch (sqlType) {
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.REAL:
            case Types.FLOAT:
            case Types.DOUBLE:
                return true;
            default:
                return false;
        }
    }

    static boolean isCharacter(int sqlType) {
        switch (sqlType) {
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.CLOB:
            case Types.NCLOB:
            case Types.SQLXML:
                return true;
            default:
                return false;
        }
    }

    /**
     * Whether a column of this type can appear in a WHERE clause comparison.
     * Db2 for i does not allow LOB, XML, or DATALINK values in basic predicates.
     */
    static boolean isSearchable(int sqlType) {
        switch (sqlType) {
            case Types.BLOB:
            case Types.CLOB:
            case Types.NCLOB:
            case Types.SQLXML:
            case Types.DATALINK:
                return false;
            default:
                return true;
        }
    }
}
