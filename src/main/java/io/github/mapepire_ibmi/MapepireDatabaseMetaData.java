package io.github.mapepire_ibmi;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowIdLifetime;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.mapepire_ibmi.types.ColumnMetadata;
import io.github.mapepire_ibmi.types.QueryMetadata;
import io.github.mapepire_ibmi.types.QueryOptions;
import io.github.mapepire_ibmi.types.QueryResult;

/**
 * Database metadata for a Db2 for i server reached through Mapepire.
 * <p>
 * Catalog methods query the ODBC/JDBC catalog views that Db2 for i provides in
 * the {@code SYSIBM} schema ({@code SQLTABLES}, {@code SQLCOLUMNS}, ...). Each
 * query selects the columns the JDBC specification defines, in the specified
 * order, and binds every caller-supplied name or pattern as a parameter. Pattern
 * arguments use {@code LIKE} semantics with {@code \} as the escape character
 * (see {@link #getSearchStringEscape()}).
 * <p>
 * Unlike ordinary query results, metadata result sets contain every matching
 * row: the driver keeps fetching until the server reports the query is done.
 */
public class MapepireDatabaseMetaData implements DatabaseMetaData {
    private static final int METADATA_FETCH_SIZE = 1000;
    private static final String PRODUCT_NAME = "DB2 UDB for AS/400";
    private static final String DRIVER_NAME = "Mapepire JDBC";
    private static final String VARCHAR = "VARCHAR";
    private static final String SMALLINT = "SMALLINT";
    private static final String INTEGER = "INTEGER";
    private static final List<String> TABLE_TYPES = Collections.unmodifiableList(Arrays.asList(
            "ALIAS", "MATERIALIZED QUERY TABLE", "SYSTEM TABLE", "TABLE", "VIEW"));

    private static final String TABLES_SQL = "SELECT TABLE_CAT, TABLE_SCHEM, TABLE_NAME, TABLE_TYPE,"
            + " COALESCE(REMARKS, NULLIF(TABLE_TEXT, '')) AS REMARKS, TYPE_CAT, TYPE_SCHEM, TYPE_NAME,"
            + " SELF_REFERENCING_COL_NAME, REF_GENERATION FROM SYSIBM.SQLTABLES";
    private static final String COLUMNS_SQL = "SELECT TABLE_CAT, TABLE_SCHEM, TABLE_NAME, COLUMN_NAME,"
            + " JDBC_DATA_TYPE AS DATA_TYPE, TYPE_NAME, COLUMN_SIZE, BUFFER_LENGTH, DECIMAL_DIGITS,"
            + " NUM_PREC_RADIX, NULLABLE, COALESCE(REMARKS, NULLIF(COLUMN_TEXT, '')) AS REMARKS, COLUMN_DEF,"
            + " SQL_DATA_TYPE, SQL_DATETIME_SUB, CHAR_OCTET_LENGTH, ORDINAL_POSITION, IS_NULLABLE,"
            + " SCOPE_CATALOG, SCOPE_SCHEMA, SCOPE_TABLE, SOURCE_DATA_TYPE,"
            // HAS_DEFAULT is I/J for identity columns, N/Y for ordinary columns, and
            // another code for values the database generates (row change timestamps,
            // generated expressions, etc.).
            + " CASE WHEN HAS_DEFAULT IN ('I', 'J') THEN 'YES' ELSE 'NO' END AS IS_AUTOINCREMENT,"
            + " CASE WHEN HAS_DEFAULT IS NULL OR HAS_DEFAULT IN ('N', 'Y', 'I', 'J') THEN 'NO' ELSE 'YES' END"
            + " AS IS_GENERATEDCOLUMN FROM SYSIBM.SQLCOLUMNS";
    private static final String SCHEMAS_SQL = "SELECT TABLE_SCHEM, TABLE_CAT AS TABLE_CATALOG"
            + " FROM SYSIBM.SQLSCHEMAS";
    private static final String PRIMARY_KEYS_SQL = "SELECT TABLE_CAT, TABLE_SCHEM, TABLE_NAME, COLUMN_NAME,"
            + " KEY_SEQ, PK_NAME FROM SYSIBM.SQLPRIMARYKEYS";
    private static final String FOREIGN_KEYS_SQL = "SELECT PKTABLE_CAT, PKTABLE_SCHEM, PKTABLE_NAME,"
            + " PKCOLUMN_NAME, FKTABLE_CAT, FKTABLE_SCHEM, FKTABLE_NAME, FKCOLUMN_NAME, KEY_SEQ, UPDATE_RULE,"
            + " DELETE_RULE, FK_NAME, PK_NAME, DEFERRABILITY FROM SYSIBM.SQLFOREIGNKEYS";
    private static final String INDEX_INFO_SQL = "SELECT TABLE_CAT, TABLE_SCHEM, TABLE_NAME, NON_UNIQUE,"
            + " INDEX_QUALIFIER, INDEX_NAME, TYPE, ORDINAL_POSITION, COLUMN_NAME, ASC_OR_DESC, CARDINALITY,"
            + " PAGES, FILTER_CONDITION FROM SYSIBM.SQLSTATISTICS";
    private static final String PROCEDURES_SQL = "SELECT PROCEDURE_CAT, PROCEDURE_SCHEM, PROCEDURE_NAME,"
            + " NUM_INPUT_PARAMS, NUM_OUTPUT_PARAMS, NUM_RESULT_SETS, REMARKS, PROCEDURE_TYPE, SPECIFIC_NAME"
            + " FROM SYSIBM.SQLPROCEDURES";
    private static final String PROCEDURE_COLUMNS_SQL = "SELECT PROCEDURE_CAT, PROCEDURE_SCHEM,"
            + " PROCEDURE_NAME, COLUMN_NAME, COLUMN_TYPE, JDBC_DATA_TYPE AS DATA_TYPE, TYPE_NAME,"
            + " COLUMN_SIZE AS \"PRECISION\", BUFFER_LENGTH AS \"LENGTH\", DECIMAL_DIGITS AS \"SCALE\","
            + " NUM_PREC_RADIX AS \"RADIX\", NULLABLE, REMARKS, COLUMN_DEF, SQL_DATA_TYPE, SQL_DATETIME_SUB,"
            + " CHAR_OCTET_LENGTH, ORDINAL_POSITION, IS_NULLABLE, SPECIFIC_NAME FROM SYSIBM.SQLPROCEDURECOLS";
    private static final String FUNCTIONS_SQL = "SELECT FUNCTION_CAT, FUNCTION_SCHEM, FUNCTION_NAME, REMARKS,"
            + " FUNCTION_TYPE, SPECIFIC_NAME FROM SYSIBM.SQLFUNCTIONS";
    private static final String FUNCTION_COLUMNS_SQL = "SELECT FUNCTION_CAT, FUNCTION_SCHEM, FUNCTION_NAME,"
            + " COLUMN_NAME, COLUMN_TYPE, JDBC_DATA_TYPE AS DATA_TYPE, TYPE_NAME, COLUMN_SIZE AS \"PRECISION\","
            + " BUFFER_LENGTH AS \"LENGTH\", DECIMAL_DIGITS AS \"SCALE\", NUM_PREC_RADIX AS \"RADIX\", NULLABLE,"
            + " REMARKS, CHAR_OCTET_LENGTH, ORDINAL_POSITION, IS_NULLABLE, SPECIFIC_NAME"
            + " FROM SYSIBM.SQLFUNCTIONCOLS";
    private static final String TYPE_INFO_SQL = "SELECT TYPE_NAME, JDBC_DATA_TYPE AS DATA_TYPE,"
            + " COLUMN_SIZE AS \"PRECISION\", LITERAL_PREFIX, LITERAL_SUFFIX, CREATE_PARAMS, NULLABLE,"
            + " CASE_SENSITIVE, SEARCHABLE, UNSIGNED_ATTRIBUTE, FIXED_PREC_SCALE, AUTO_UNIQUE_VALUE AS AUTO_INCREMENT,"
            + " LOCAL_TYPE_NAME, MINIMUM_SCALE, MAXIMUM_SCALE, SQL_DATA_TYPE, SQL_DATETIME_SUB, NUM_PREC_RADIX"
            + " FROM SYSIBM.SQLTYPEINFO ORDER BY 2, 1";
    private static final String UDTS_SQL = "SELECT TYPE_CAT, TYPE_SCHEM, TYPE_NAME, CLASS_NAME, DATA_TYPE,"
            + " REMARKS, BASE_TYPE FROM SYSIBM.SQLUDTS";
    private static final String TABLE_PRIVILEGES_SQL = "SELECT TABLE_CAT, TABLE_SCHEM, TABLE_NAME, GRANTOR,"
            + " GRANTEE, PRIVILEGE, IS_GRANTABLE FROM SYSIBM.SQLTABLEPRIVILEGES";
    private static final String COLUMN_PRIVILEGES_SQL = "SELECT TABLE_CAT, TABLE_SCHEM, TABLE_NAME, COLUMN_NAME,"
            + " GRANTOR, GRANTEE, PRIVILEGE, IS_GRANTABLE FROM SYSIBM.SQLCOLPRIVILEGES";
    private static final String BEST_ROW_IDENTIFIER_SQL = "SELECT SCOPE, COLUMN_NAME, JDBC_DATA_TYPE AS DATA_TYPE,"
            + " TYPE_NAME, COLUMN_SIZE, BUFFER_LENGTH, DECIMAL_DIGITS, PSEUDO_COLUMN FROM SYSIBM.SQLSPECIALCOLUMNS";
    private static final String CATALOGS_SQL = "SELECT CURRENT SERVER AS TABLE_CAT FROM SYSIBM.SYSDUMMY1";
    private static final String USER_SQL = "SELECT USER FROM SYSIBM.SYSDUMMY1";
    private static final String VERSION_SQL = "SELECT OS_VERSION, OS_RELEASE FROM SYSIBMADM.ENV_SYS_INFO";

    private final MapepireConnection connection;
    private boolean versionLoaded;
    private int databaseMajorVersion;
    private int databaseMinorVersion;

    public MapepireDatabaseMetaData(MapepireConnection connection) {
        this.connection = connection;
    }

    /**
     * Runs a catalog query and returns a result set holding every row. The
     * Mapepire server returns rows in blocks, so this keeps fetching until the
     * query is done; only the first block carries column metadata.
     */
    private ResultSet executeQuery(String sql, List<Object> parameters) throws SQLException {
        Query query = null;
        try {
            QueryOptions options = new QueryOptions();
            if (!parameters.isEmpty()) {
                options.setParameters(parameters);
            }
            query = this.connection.getJob().query(sql, options);
            QueryResult<Object> result = this.connection.resolve(query.<Object>execute(METADATA_FETCH_SIZE));
            List<Object> rows = new ArrayList<>();
            if (result.getData() != null) {
                rows.addAll(result.getData());
            }

            QueryResult<Object> block = result;
            while (!block.getIsDone()) {
                block = this.connection.resolve(query.<Object>fetchMore(METADATA_FETCH_SIZE));
                if (block.getData() == null || block.getData().isEmpty()) {
                    break;
                }
                rows.addAll(block.getData());
            }

            result.setData(rows);
            result.setIsDone(true);
            return new MapepireResultSet(result);
        } catch (Exception e) {
            throw SqlExceptions.toSqlException(e);
        } finally {
            if (query != null) {
                try {
                    this.connection.resolve(query.close());
                } catch (Exception ignored) {
                }
            }
        }
    }

    private ResultSet executeQuery(Where where, String orderBy) throws SQLException {
        return executeQuery(where.toSql() + " ORDER BY " + orderBy, where.getParameters());
    }

    private String querySingleString(String sql) throws SQLException {
        try (ResultSet rs = executeQuery(sql, Collections.emptyList())) {
            if (!rs.next()) {
                throw new SQLException("Query returned no rows: " + sql);
            }
            String value = rs.getString(1);
            return value == null ? null : value.trim();
        }
    }

    private void loadDatabaseVersion() throws SQLException {
        if (this.versionLoaded) {
            return;
        }
        try (ResultSet rs = executeQuery(VERSION_SQL, Collections.emptyList())) {
            if (!rs.next()) {
                throw new SQLException("Unable to determine the IBM i release");
            }
            this.databaseMajorVersion = rs.getInt(1);
            this.databaseMinorVersion = rs.getInt(2);
        }
        this.versionLoaded = true;
    }

    /**
     * Builds a result set locally, for metadata that needs no server round trip
     * or that Db2 for i does not support (in which case {@code rows} is empty).
     */
    private static ResultSet localResult(List<ColumnMetadata> columns, List<Object> rows) {
        QueryResult<Object> result = new QueryResult<>();
        result.setSuccess(true);
        result.setHasResults(true);
        result.setIsDone(true);
        result.setMetadata(new QueryMetadata(columns.size(), columns, null, null));
        result.setData(rows);
        return new MapepireResultSet(result);
    }

    /**
     * Builds an empty result set with the given columns. Each column is given as
     * a name followed by a Db2 type name, e.g. {@code "TABLE_CAT", "VARCHAR"}.
     */
    private static ResultSet emptyResult(String... namesAndTypes) {
        List<ColumnMetadata> columns = new ArrayList<>();
        for (int i = 0; i < namesAndTypes.length; i += 2) {
            columns.add(column(namesAndTypes[i], namesAndTypes[i + 1]));
        }
        return localResult(columns, Collections.emptyList());
    }

    private static ColumnMetadata column(String name, String type) {
        int precision;
        int displaySize;
        switch (type) {
            case SMALLINT:
                precision = 5;
                displaySize = 6;
                break;
            case INTEGER:
                precision = 10;
                displaySize = 11;
                break;
            default:
                precision = 128;
                displaySize = 128;
                break;
        }
        return new ColumnMetadata(displaySize, name, name, type, precision, 0, false,
                ResultSetMetaData.columnNullable, true, false, null);
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

    // -------------------------------------------------------------------------
    // Product and driver information
    // -------------------------------------------------------------------------

    @Override
    public String getURL() throws SQLException {
        return this.connection.getUrl();
    }

    @Override
    public String getUserName() throws SQLException {
        return querySingleString(USER_SQL);
    }

    @Override
    public String getDatabaseProductName() throws SQLException {
        // Matches the name IBM's own JDBC driver reports, which ORMs and
        // migration tools use to select their Db2 for i dialect.
        return PRODUCT_NAME;
    }

    @Override
    public String getDatabaseProductVersion() throws SQLException {
        int major = getDatabaseMajorVersion();
        int minor = getDatabaseMinorVersion();
        return String.format("%02d.%02d.0000 V%dR%dm0", major, minor, major, minor);
    }

    @Override
    public int getDatabaseMajorVersion() throws SQLException {
        loadDatabaseVersion();
        return this.databaseMajorVersion;
    }

    @Override
    public int getDatabaseMinorVersion() throws SQLException {
        loadDatabaseVersion();
        return this.databaseMinorVersion;
    }

    @Override
    public String getDriverName() throws SQLException {
        return DRIVER_NAME;
    }

    @Override
    public String getDriverVersion() throws SQLException {
        return MapepireDriver.MAJOR_VERSION + "." + MapepireDriver.MINOR_VERSION;
    }

    @Override
    public int getDriverMajorVersion() {
        return MapepireDriver.MAJOR_VERSION;
    }

    @Override
    public int getDriverMinorVersion() {
        return MapepireDriver.MINOR_VERSION;
    }

    @Override
    public int getJDBCMajorVersion() throws SQLException {
        return 4;
    }

    @Override
    public int getJDBCMinorVersion() throws SQLException {
        return 2;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return this.connection;
    }

    // -------------------------------------------------------------------------
    // Catalog queries
    // -------------------------------------------------------------------------

    @Override
    public ResultSet getCatalogs() throws SQLException {
        return executeQuery(CATALOGS_SQL, Collections.emptyList());
    }

    @Override
    public ResultSet getSchemas() throws SQLException {
        return getSchemas(null, null);
    }

    @Override
    public ResultSet getSchemas(String catalog, String schemaPattern) throws SQLException {
        Where where = new Where(SCHEMAS_SQL)
                .exact("TABLE_CAT", catalog)
                .like("TABLE_SCHEM", schemaPattern);
        return executeQuery(where, "2, 1");
    }

    @Override
    public ResultSet getTableTypes() throws SQLException {
        List<Object> rows = new ArrayList<>();
        for (String type : TABLE_TYPES) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("TABLE_TYPE", type);
            rows.add(row);
        }
        return localResult(Collections.singletonList(column("TABLE_TYPE", VARCHAR)), rows);
    }

    @Override
    public ResultSet getTables(String catalog, String schemaPattern, String tableNamePattern, String[] types)
            throws SQLException {
        Where where = new Where(TABLES_SQL)
                .exact("TABLE_CAT", catalog)
                .like("TABLE_SCHEM", schemaPattern)
                .like("TABLE_NAME", tableNamePattern)
                .in("TABLE_TYPE", types == null ? null : Arrays.<Object>asList((Object[]) types));
        return executeQuery(where, "TABLE_TYPE, TABLE_CAT, TABLE_SCHEM, TABLE_NAME");
    }

    @Override
    public ResultSet getColumns(String catalog, String schemaPattern, String tableNamePattern,
            String columnNamePattern) throws SQLException {
        Where where = new Where(COLUMNS_SQL)
                .exact("TABLE_CAT", catalog)
                .like("TABLE_SCHEM", schemaPattern)
                .like("TABLE_NAME", tableNamePattern)
                .like("COLUMN_NAME", columnNamePattern);
        return executeQuery(where, "TABLE_CAT, TABLE_SCHEM, TABLE_NAME, ORDINAL_POSITION");
    }

    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        Where where = new Where(PRIMARY_KEYS_SQL)
                .exact("TABLE_CAT", catalog)
                .exact("TABLE_SCHEM", schema)
                .exact("TABLE_NAME", table);
        return executeQuery(where, "COLUMN_NAME");
    }

    @Override
    public ResultSet getImportedKeys(String catalog, String schema, String table) throws SQLException {
        Where where = new Where(FOREIGN_KEYS_SQL)
                .exact("FKTABLE_CAT", catalog)
                .exact("FKTABLE_SCHEM", schema)
                .exact("FKTABLE_NAME", table);
        return executeQuery(where, "PKTABLE_CAT, PKTABLE_SCHEM, PKTABLE_NAME, KEY_SEQ");
    }

    @Override
    public ResultSet getExportedKeys(String catalog, String schema, String table) throws SQLException {
        Where where = new Where(FOREIGN_KEYS_SQL)
                .exact("PKTABLE_CAT", catalog)
                .exact("PKTABLE_SCHEM", schema)
                .exact("PKTABLE_NAME", table);
        return executeQuery(where, "FKTABLE_CAT, FKTABLE_SCHEM, FKTABLE_NAME, KEY_SEQ");
    }

    @Override
    public ResultSet getCrossReference(String parentCatalog, String parentSchema, String parentTable,
            String foreignCatalog, String foreignSchema, String foreignTable) throws SQLException {
        Where where = new Where(FOREIGN_KEYS_SQL)
                .exact("PKTABLE_CAT", parentCatalog)
                .exact("PKTABLE_SCHEM", parentSchema)
                .exact("PKTABLE_NAME", parentTable)
                .exact("FKTABLE_CAT", foreignCatalog)
                .exact("FKTABLE_SCHEM", foreignSchema)
                .exact("FKTABLE_NAME", foreignTable);
        return executeQuery(where, "FKTABLE_CAT, FKTABLE_SCHEM, FKTABLE_NAME, KEY_SEQ");
    }

    @Override
    public ResultSet getIndexInfo(String catalog, String schema, String table, boolean unique,
            boolean approximate) throws SQLException {
        Where where = new Where(INDEX_INFO_SQL)
                .exact("TABLE_CAT", catalog)
                .exact("TABLE_SCHEM", schema)
                .exact("TABLE_NAME", table);
        if (unique) {
            // Keep the table statistics row (TYPE = tableIndexStatistic), whose
            // NON_UNIQUE is null, alongside the unique indexes.
            where.condition("(NON_UNIQUE = 0 OR TYPE = " + DatabaseMetaData.tableIndexStatistic + ")");
        }
        return executeQuery(where, "NON_UNIQUE, TYPE, INDEX_NAME, ORDINAL_POSITION");
    }

    @Override
    public ResultSet getProcedures(String catalog, String schemaPattern, String procedureNamePattern)
            throws SQLException {
        Where where = new Where(PROCEDURES_SQL)
                .exact("PROCEDURE_CAT", catalog)
                .like("PROCEDURE_SCHEM", schemaPattern)
                .like("PROCEDURE_NAME", procedureNamePattern);
        return executeQuery(where, "PROCEDURE_CAT, PROCEDURE_SCHEM, PROCEDURE_NAME, SPECIFIC_NAME");
    }

    @Override
    public ResultSet getProcedureColumns(String catalog, String schemaPattern, String procedureNamePattern,
            String columnNamePattern) throws SQLException {
        Where where = new Where(PROCEDURE_COLUMNS_SQL)
                .exact("PROCEDURE_CAT", catalog)
                .like("PROCEDURE_SCHEM", schemaPattern)
                .like("PROCEDURE_NAME", procedureNamePattern)
                .like("COLUMN_NAME", columnNamePattern);
        return executeQuery(where,
                "PROCEDURE_CAT, PROCEDURE_SCHEM, PROCEDURE_NAME, SPECIFIC_NAME, ORDINAL_POSITION");
    }

    @Override
    public ResultSet getFunctions(String catalog, String schemaPattern, String functionNamePattern)
            throws SQLException {
        Where where = new Where(FUNCTIONS_SQL)
                .exact("FUNCTION_CAT", catalog)
                .like("FUNCTION_SCHEM", schemaPattern)
                .like("FUNCTION_NAME", functionNamePattern);
        return executeQuery(where, "FUNCTION_CAT, FUNCTION_SCHEM, FUNCTION_NAME, SPECIFIC_NAME");
    }

    @Override
    public ResultSet getFunctionColumns(String catalog, String schemaPattern, String functionNamePattern,
            String columnNamePattern) throws SQLException {
        Where where = new Where(FUNCTION_COLUMNS_SQL)
                .exact("FUNCTION_CAT", catalog)
                .like("FUNCTION_SCHEM", schemaPattern)
                .like("FUNCTION_NAME", functionNamePattern)
                .like("COLUMN_NAME", columnNamePattern);
        return executeQuery(where,
                "FUNCTION_CAT, FUNCTION_SCHEM, FUNCTION_NAME, SPECIFIC_NAME, ORDINAL_POSITION");
    }

    @Override
    public ResultSet getTypeInfo() throws SQLException {
        return executeQuery(TYPE_INFO_SQL, Collections.emptyList());
    }

    @Override
    public ResultSet getUDTs(String catalog, String schemaPattern, String typeNamePattern, int[] types)
            throws SQLException {
        List<Object> typeCodes = null;
        if (types != null) {
            typeCodes = new ArrayList<>();
            for (int type : types) {
                typeCodes.add(type);
            }
        }
        Where where = new Where(UDTS_SQL)
                .exact("TYPE_CAT", catalog)
                .like("TYPE_SCHEM", schemaPattern)
                .like("TYPE_NAME", typeNamePattern)
                .in("DATA_TYPE", typeCodes);
        return executeQuery(where, "DATA_TYPE, TYPE_CAT, TYPE_SCHEM, TYPE_NAME");
    }

    @Override
    public ResultSet getTablePrivileges(String catalog, String schemaPattern, String tableNamePattern)
            throws SQLException {
        Where where = new Where(TABLE_PRIVILEGES_SQL)
                .exact("TABLE_CAT", catalog)
                .like("TABLE_SCHEM", schemaPattern)
                .like("TABLE_NAME", tableNamePattern);
        return executeQuery(where, "TABLE_CAT, TABLE_SCHEM, TABLE_NAME, PRIVILEGE");
    }

    @Override
    public ResultSet getColumnPrivileges(String catalog, String schema, String table, String columnNamePattern)
            throws SQLException {
        Where where = new Where(COLUMN_PRIVILEGES_SQL)
                .exact("TABLE_CAT", catalog)
                .exact("TABLE_SCHEM", schema)
                .exact("TABLE_NAME", table)
                .like("COLUMN_NAME", columnNamePattern);
        return executeQuery(where, "COLUMN_NAME, PRIVILEGE");
    }

    @Override
    public ResultSet getBestRowIdentifier(String catalog, String schema, String table, int scope,
            boolean nullable) throws SQLException {
        Where where = new Where(BEST_ROW_IDENTIFIER_SQL)
                .exact("TABLE_CAT", catalog)
                .exact("TABLE_SCHEM", schema)
                .exact("TABLE_NAME", table);
        if (!nullable) {
            where.condition("NULLABLE <> " + DatabaseMetaData.columnNullable);
        }
        return executeQuery(where, "SCOPE");
    }

    @Override
    public ResultSet getVersionColumns(String catalog, String schema, String table) throws SQLException {
        return emptyResult("SCOPE", SMALLINT, "COLUMN_NAME", VARCHAR, "DATA_TYPE", INTEGER, "TYPE_NAME", VARCHAR,
                "COLUMN_SIZE", INTEGER, "BUFFER_LENGTH", INTEGER, "DECIMAL_DIGITS", SMALLINT,
                "PSEUDO_COLUMN", SMALLINT);
    }

    @Override
    public ResultSet getSuperTypes(String catalog, String schemaPattern, String typeNamePattern)
            throws SQLException {
        return emptyResult("TYPE_CAT", VARCHAR, "TYPE_SCHEM", VARCHAR, "TYPE_NAME", VARCHAR,
                "SUPERTYPE_CAT", VARCHAR, "SUPERTYPE_SCHEM", VARCHAR, "SUPERTYPE_NAME", VARCHAR);
    }

    @Override
    public ResultSet getSuperTables(String catalog, String schemaPattern, String tableNamePattern)
            throws SQLException {
        return emptyResult("TABLE_CAT", VARCHAR, "TABLE_SCHEM", VARCHAR, "TABLE_NAME", VARCHAR,
                "SUPERTABLE_NAME", VARCHAR);
    }

    @Override
    public ResultSet getAttributes(String catalog, String schemaPattern, String typeNamePattern,
            String attributeNamePattern) throws SQLException {
        return emptyResult("TYPE_CAT", VARCHAR, "TYPE_SCHEM", VARCHAR, "TYPE_NAME", VARCHAR, "ATTR_NAME", VARCHAR,
                "DATA_TYPE", INTEGER, "ATTR_TYPE_NAME", VARCHAR, "ATTR_SIZE", INTEGER, "DECIMAL_DIGITS", INTEGER,
                "NUM_PREC_RADIX", INTEGER, "NULLABLE", INTEGER, "REMARKS", VARCHAR, "ATTR_DEF", VARCHAR,
                "SQL_DATA_TYPE", INTEGER, "SQL_DATETIME_SUB", INTEGER, "CHAR_OCTET_LENGTH", INTEGER,
                "ORDINAL_POSITION", INTEGER, "IS_NULLABLE", VARCHAR, "SCOPE_CATALOG", VARCHAR,
                "SCOPE_SCHEMA", VARCHAR, "SCOPE_TABLE", VARCHAR, "SOURCE_DATA_TYPE", SMALLINT);
    }

    @Override
    public ResultSet getClientInfoProperties() throws SQLException {
        return emptyResult("NAME", VARCHAR, "MAX_LEN", INTEGER, "DEFAULT_VALUE", VARCHAR, "DESCRIPTION", VARCHAR);
    }

    @Override
    public ResultSet getPseudoColumns(String catalog, String schemaPattern, String tableNamePattern,
            String columnNamePattern) throws SQLException {
        return emptyResult("TABLE_CAT", VARCHAR, "TABLE_SCHEM", VARCHAR, "TABLE_NAME", VARCHAR,
                "COLUMN_NAME", VARCHAR, "DATA_TYPE", INTEGER, "COLUMN_SIZE", INTEGER, "DECIMAL_DIGITS", INTEGER,
                "NUM_PREC_RADIX", INTEGER, "COLUMN_USAGE", VARCHAR, "REMARKS", VARCHAR,
                "CHAR_OCTET_LENGTH", INTEGER, "IS_NULLABLE", VARCHAR);
    }

    // -------------------------------------------------------------------------
    // Identifiers and SQL dialect
    // -------------------------------------------------------------------------

    @Override
    public String getIdentifierQuoteString() throws SQLException {
        return "\"";
    }

    @Override
    public String getSearchStringEscape() throws SQLException {
        return "\\";
    }

    @Override
    public String getExtraNameCharacters() throws SQLException {
        return "@#$";
    }

    @Override
    public String getSQLKeywords() throws SQLException {
        return "AFTER,ALIAS,ALLOW,APPLICATION,ASSOCIATE,ASUTIME,AUDIT,AUX,AUXILIARY,BEFORE,BINARY,BUFFERPOOL,"
                + "CACHE,CALLED,CAPTURE,CARDINALITY,CCSID,CLUSTER,COLLECTION,COLLID,COMMENT,CONCAT,CONTAINS,"
                + "COUNT_BIG,CURRENT_LC_CTYPE,CURRENT_PATH,CURRENT_SERVER,CURRENT_TIMEZONE,CYCLE,DATA,DATABASE,"
                + "DAYS,DBINFO,DEFAULTS,DEFINITION,DETERMINISTIC,DISALLOW,DO,DSSIZE,EACH,EDITPROC,ELSEIF,ENCODING,"
                + "ERASE,EXCLUDING,EXIT,FENCED,FIELDPROC,FILE,FINAL,FREE,GENERAL,GENERATED,GRAPHIC,HANDLER,HOLD,"
                + "HOURS,IF,INCLUDING,INCREMENT,INDEX,INHERIT,INTEGRITY,ITERATE,JAVA,LABEL,LC_CTYPE,LEAVE,LIBRARY,"
                + "LINKTYPE,LOCALE,LOCATOR,LOCATORS,LOCK,LOCKMAX,LOCKSIZE,LONG,LOOP,MAXVALUE,MICROSECOND,"
                + "MICROSECONDS,MINUTES,MINVALUE,MODE,MODIFIES,MONTHS,NEW,NEW_TABLE,NOCACHE,NOCYCLE,NODENAME,"
                + "NODENUMBER,NOMAXVALUE,NOMINVALUE,NOORDER,NULLS,NUMPARTS,OBID,OLD,OLD_TABLE,OPTIMIZATION,"
                + "OPTIMIZE,OVERRIDING,PACKAGE,PARAMETER,PART,PARTITION,PATH,PIECESIZE,PLAN,PRIQTY,PROGRAM,PSID,"
                + "QUERYNO,READS,RECOVERY,REFERENCING,RELEASE,RENAME,REPEAT,RESET,RESIGNAL,RESTART,RESULT,"
                + "RESULT_SET_LOCATOR,RETURN,RETURNS,ROUTINE,ROW,RRN,RUN,SAVEPOINT,SCRATCHPAD,SECONDS,SECQTY,"
                + "SECURITY,SENSITIVE,SIGNAL,SIMPLE,SOURCE,SPECIFIC,SQLID,STANDARD,START,STATIC,STAY,STOGROUP,"
                + "STORES,STYLE,SUBPAGES,SYNONYM,SYSFUN,SYSIBM,SYSPROC,SYSTEM,TABLESPACE,TRIGGER,TYPE,UNDO,UNTIL,"
                + "VALIDPROC,VARIABLE,VARIANT,VCAT,VOLUMES,WHILE,WLM,YEARS";
    }

    @Override
    public String getNumericFunctions() throws SQLException {
        return "ABS,ACOS,ASIN,ATAN,ATAN2,CEILING,COS,COT,DEGREES,EXP,FLOOR,LOG,LOG10,MOD,PI,POWER,RADIANS,RAND,"
                + "ROUND,SIGN,SIN,SQRT,TAN,TRUNCATE";
    }

    @Override
    public String getStringFunctions() throws SQLException {
        return "ASCII,CHAR,CONCAT,DIFFERENCE,INSERT,LCASE,LEFT,LENGTH,LOCATE,LTRIM,REPEAT,REPLACE,RIGHT,RTRIM,"
                + "SOUNDEX,SPACE,SUBSTRING,UCASE";
    }

    @Override
    public String getSystemFunctions() throws SQLException {
        return "DATABASE,IFNULL,USER";
    }

    @Override
    public String getTimeDateFunctions() throws SQLException {
        return "CURDATE,CURTIME,DAYNAME,DAYOFMONTH,DAYOFWEEK,DAYOFYEAR,HOUR,MINUTE,MONTH,MONTHNAME,NOW,QUARTER,"
                + "SECOND,TIMESTAMPADD,TIMESTAMPDIFF,WEEK,YEAR";
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws SQLException {
        return true;
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws SQLException {
        return true;
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws SQLException {
        return false;
    }

    @Override
    public String getSchemaTerm() throws SQLException {
        return "Schema";
    }

    @Override
    public String getProcedureTerm() throws SQLException {
        return "Procedure";
    }

    @Override
    public String getCatalogTerm() throws SQLException {
        return "Database";
    }

    @Override
    public boolean isCatalogAtStart() throws SQLException {
        return true;
    }

    @Override
    public String getCatalogSeparator() throws SQLException {
        return ".";
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws SQLException {
        return false;
    }

    // -------------------------------------------------------------------------
    // SQL feature support
    // -------------------------------------------------------------------------

    @Override
    public boolean allProceduresAreCallable() throws SQLException {
        return false;
    }

    @Override
    public boolean allTablesAreSelectable() throws SQLException {
        return false;
    }

    @Override
    public boolean isReadOnly() throws SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedHigh() throws SQLException {
        return true;
    }

    @Override
    public boolean nullsAreSortedLow() throws SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedAtStart() throws SQLException {
        return false;
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws SQLException {
        return false;
    }

    @Override
    public boolean usesLocalFiles() throws SQLException {
        return false;
    }

    @Override
    public boolean usesLocalFilePerTable() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsColumnAliasing() throws SQLException {
        return true;
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsConvert() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsConvert(int fromType, int toType) throws SQLException {
        return false;
    }

    @Override
    public boolean supportsTableCorrelationNames() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsOrderByUnrelated() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsGroupBy() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsGroupByUnrelated() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsLikeEscapeClause() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsNonNullableColumns() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsANSI92FullSQL() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsOuterJoins() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsFullOuterJoins() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsPositionedDelete() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsPositionedUpdate() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsSelectForUpdate() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsStoredProcedures() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSubqueriesInComparisons() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSubqueriesInExists() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSubqueriesInIns() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsUnion() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsUnionAll() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws SQLException {
        return false;
    }

    // -------------------------------------------------------------------------
    // Limits (0 means no limit or the limit is not known)
    // -------------------------------------------------------------------------

    @Override
    public int getMaxBinaryLiteralLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxCharLiteralLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnNameLength() throws SQLException {
        return 128;
    }

    @Override
    public int getMaxColumnsInGroupBy() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInIndex() throws SQLException {
        return 120;
    }

    @Override
    public int getMaxColumnsInOrderBy() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxColumnsInSelect() throws SQLException {
        return 8000;
    }

    @Override
    public int getMaxColumnsInTable() throws SQLException {
        return 8000;
    }

    @Override
    public int getMaxConnections() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxCursorNameLength() throws SQLException {
        return 128;
    }

    @Override
    public int getMaxIndexLength() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxSchemaNameLength() throws SQLException {
        return 128;
    }

    @Override
    public int getMaxProcedureNameLength() throws SQLException {
        return 128;
    }

    @Override
    public int getMaxCatalogNameLength() throws SQLException {
        return 18;
    }

    @Override
    public int getMaxRowSize() throws SQLException {
        return 32766;
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws SQLException {
        return false;
    }

    @Override
    public int getMaxStatementLength() throws SQLException {
        return 2097152;
    }

    @Override
    public int getMaxStatements() throws SQLException {
        return 0;
    }

    @Override
    public int getMaxTableNameLength() throws SQLException {
        return 128;
    }

    @Override
    public int getMaxTablesInSelect() throws SQLException {
        return 1000;
    }

    @Override
    public int getMaxUserNameLength() throws SQLException {
        return 10;
    }

    // -------------------------------------------------------------------------
    // Transactions
    // -------------------------------------------------------------------------

    @Override
    public int getDefaultTransactionIsolation() throws SQLException {
        // The Mapepire server connects through JTOpen, whose default isolation is *CHG.
        return Connection.TRANSACTION_READ_UNCOMMITTED;
    }

    @Override
    public boolean supportsTransactions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsTransactionIsolationLevel(int level) throws SQLException {
        switch (level) {
            case Connection.TRANSACTION_NONE:
            case Connection.TRANSACTION_READ_UNCOMMITTED:
            case Connection.TRANSACTION_READ_COMMITTED:
            case Connection.TRANSACTION_REPEATABLE_READ:
            case Connection.TRANSACTION_SERIALIZABLE:
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean supportsMultipleTransactions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly() throws SQLException {
        return false;
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws SQLException {
        return false;
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws SQLException {
        return true;
    }

    @Override
    public boolean supportsSavepoints() throws SQLException {
        return false;
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws SQLException {
        return false;
    }

    // -------------------------------------------------------------------------
    // Driver capabilities (what this driver implements, not just the database)
    // -------------------------------------------------------------------------

    @Override
    public boolean supportsMultipleResultSets() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsResultSetType(int type) throws SQLException {
        return type == ResultSet.TYPE_FORWARD_ONLY;
    }

    @Override
    public boolean supportsResultSetConcurrency(int type, int concurrency) throws SQLException {
        return type == ResultSet.TYPE_FORWARD_ONLY && concurrency == ResultSet.CONCUR_READ_ONLY;
    }

    @Override
    public boolean supportsResultSetHoldability(int holdability) throws SQLException {
        return holdability == ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return ResultSet.HOLD_CURSORS_OVER_COMMIT;
    }

    @Override
    public boolean ownUpdatesAreVisible(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean ownDeletesAreVisible(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean ownInsertsAreVisible(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean othersUpdatesAreVisible(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean othersDeletesAreVisible(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean othersInsertsAreVisible(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean updatesAreDetected(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean deletesAreDetected(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean insertsAreDetected(int type) throws SQLException {
        return false;
    }

    @Override
    public boolean supportsBatchUpdates() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsNamedParameters() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsMultipleOpenResults() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws SQLException {
        return false;
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws SQLException {
        return false;
    }

    @Override
    public int getSQLStateType() throws SQLException {
        return DatabaseMetaData.sqlStateSQL;
    }

    @Override
    public boolean locatorsUpdateCopy() throws SQLException {
        return false;
    }

    @Override
    public boolean supportsStatementPooling() throws SQLException {
        return false;
    }

    @Override
    public RowIdLifetime getRowIdLifetime() throws SQLException {
        return RowIdLifetime.ROWID_UNSUPPORTED;
    }

    /**
     * Accumulates a WHERE clause and its bound parameters. Null filter values
     * are skipped, which gives the JDBC "null means do not filter" semantics.
     */
    private static final class Where {
        private final StringBuilder sql;
        private final List<Object> parameters = new ArrayList<>();
        private boolean hasCondition;

        Where(String select) {
            this.sql = new StringBuilder(select);
        }

        Where exact(String column, String value) {
            if (value != null) {
                condition(column + " = ?");
                this.parameters.add(value);
            }
            return this;
        }

        Where like(String column, String pattern) {
            // "%" matches every name, so skip the predicate entirely.
            if (pattern != null && !"%".equals(pattern)) {
                condition(column + " LIKE ? ESCAPE '\\'");
                this.parameters.add(pattern);
            }
            return this;
        }

        Where in(String column, List<Object> values) {
            if (values != null && !values.isEmpty()) {
                StringBuilder markers = new StringBuilder();
                for (int i = 0; i < values.size(); i++) {
                    markers.append(i == 0 ? "?" : ", ?");
                }
                condition(column + " IN (" + markers + ")");
                this.parameters.addAll(values);
            }
            return this;
        }

        Where condition(String predicate) {
            this.sql.append(this.hasCondition ? " AND " : " WHERE ").append(predicate);
            this.hasCondition = true;
            return this;
        }

        String toSql() {
            return this.sql.toString();
        }

        List<Object> getParameters() {
            return this.parameters;
        }
    }
}
