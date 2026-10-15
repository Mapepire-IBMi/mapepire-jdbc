package io.github.mapepire_ibmi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The single table a query selects from, as written in the query's
 * {@code FROM} clause.
 * <p>
 * The Mapepire server does not report the schema of result columns, so an
 * updatable result set takes its target table from the SQL text instead. The
 * parser recognizes {@code SELECT ... FROM [schema.]table [[AS] alias]}
 * followed by nothing or by a clause that does not add tables or group rows
 * ({@code WHERE}, {@code ORDER BY}, {@code FOR UPDATE}, ...). Anything else,
 * such as a join, a second table, a nested table expression, a set operation,
 * or {@code GROUP BY}, is not a single-table query.
 */
final class TableReference {
    private static final Set<String> FOLLOWING_CLAUSES = new HashSet<>(Arrays.asList(
            "WHERE", "ORDER", "FOR", "FETCH", "OFFSET", "LIMIT", "OPTIMIZE", "WITH", ";"));
    private static final Set<String> MULTI_ROW_SOURCE_KEYWORDS = new HashSet<>(Arrays.asList(
            "UNION", "EXCEPT", "INTERSECT", "GROUP", "HAVING"));

    private final String schema;
    private final String separator;
    private final String table;

    private TableReference(String schema, String separator, String table) {
        this.schema = schema;
        this.separator = separator;
        this.table = table;
    }

    /**
     * Returns the table a single-table {@code SELECT} reads from, or null if the
     * query is not a single-table {@code SELECT}.
     */
    static TableReference fromQuery(String sql) {
        if (sql == null) {
            return null;
        }
        List<String> tokens = tokenize(sql);
        if (tokens.isEmpty() || !tokens.get(0).equals("SELECT")) {
            return null;
        }
        int from = indexOfTopLevel(tokens, "FROM", 0);
        if (from < 0 || containsTopLevel(tokens, MULTI_ROW_SOURCE_KEYWORDS, from)) {
            return null;
        }

        int i = from + 1;
        String first = identifierAt(tokens, i++);
        if (first == null) {
            return null;
        }
        String schema = null;
        String separator = null;
        String table = first;
        if (i < tokens.size() && (tokens.get(i).equals(".") || tokens.get(i).equals("/"))) {
            separator = tokens.get(i++);
            schema = first;
            table = identifierAt(tokens, i++);
            if (table == null) {
                return null;
            }
        }

        // An optional correlation name, with or without AS
        if (i < tokens.size() && tokens.get(i).equals("AS")) {
            i += 2;
        } else if (identifierAt(tokens, i) != null && !FOLLOWING_CLAUSES.contains(tokens.get(i))) {
            i++;
        }

        if (i < tokens.size() && !FOLLOWING_CLAUSES.contains(tokens.get(i))) {
            return null;
        }
        return new TableReference(schema, separator, table);
    }

    /**
     * The schema the query qualifies the table with, or null if it is unqualified.
     */
    String getSchema() {
        return this.schema;
    }

    String getTable() {
        return this.table;
    }

    /**
     * The table as a delimited SQL identifier, qualified the same way as in the
     * original query. An unqualified table therefore resolves exactly as it did
     * for the query, under either SQL or system naming.
     */
    String toSql() {
        String name = quoteIdentifier(this.table);
        return this.schema == null ? name : quoteIdentifier(this.schema) + this.separator + name;
    }

    @Override
    public String toString() {
        return this.schema == null ? this.table : this.schema + this.separator + this.table;
    }

    static String quoteIdentifier(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    /**
     * Splits SQL into tokens. Ordinary identifiers and keywords are folded to
     * upper case, as Db2 does; delimited identifiers and string literals keep their
     * quotes; comments and whitespace are dropped; any other character is a token
     * by itself.
     */
    private static List<String> tokenize(String sql) {
        List<String> tokens = new ArrayList<>();
        int length = sql.length();
        int i = 0;
        while (i < length) {
            char c = sql.charAt(i);
            int end;
            if (Character.isWhitespace(c)) {
                end = i + 1;
            } else if (sql.startsWith("--", i)) {
                end = sql.indexOf('\n', i);
                end = end < 0 ? length : end;
            } else if (sql.startsWith("/*", i)) {
                end = sql.indexOf("*/", i + 2);
                end = end < 0 ? length : end + 2;
            } else if (c == '\'' || c == '"') {
                end = endOfQuoted(sql, i);
                tokens.add(sql.substring(i, end));
            } else if (isIdentifierPart(c)) {
                end = i + 1;
                while (end < length && isIdentifierPart(sql.charAt(end))) {
                    end++;
                }
                tokens.add(sql.substring(i, end).toUpperCase(Locale.ROOT));
            } else {
                end = i + 1;
                tokens.add(String.valueOf(c));
            }
            i = end;
        }
        return tokens;
    }

    private static int endOfQuoted(String sql, int start) {
        char quote = sql.charAt(start);
        int i = start + 1;
        while (i < sql.length()) {
            if (sql.charAt(i) == quote) {
                // A doubled quote is an escaped quote character
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return sql.length();
    }

    private static boolean isIdentifierPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#' || c == '@';
    }

    /**
     * Returns the identifier at the given token index, unquoted, or null if that
     * token is not an identifier.
     */
    private static String identifierAt(List<String> tokens, int index) {
        if (index >= tokens.size()) {
            return null;
        }
        String token = tokens.get(index);
        char first = token.charAt(0);
        if (first == '"') {
            return token.length() < 2 ? null : token.substring(1, token.length() - 1).replace("\"\"", "\"");
        }
        return isIdentifierPart(first) && !Character.isDigit(first) ? token : null;
    }

    private static int indexOfTopLevel(List<String> tokens, String keyword, int start) {
        int depth = 0;
        for (int i = start; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals("(")) {
                depth++;
            } else if (token.equals(")")) {
                depth--;
            } else if (depth == 0 && token.equals(keyword)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsTopLevel(List<String> tokens, Set<String> keywords, int start) {
        for (String keyword : keywords) {
            if (indexOfTopLevel(tokens, keyword, start) >= 0) {
                return true;
            }
        }
        return false;
    }
}
