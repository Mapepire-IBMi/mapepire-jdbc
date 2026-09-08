package io.github.mapepire_ibmi;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.mapepire_ibmi.types.DaemonServer;
import io.github.mapepire_ibmi.types.JDBCOptions;

public class MapepireDriver implements Driver {

    private static final int MAJOR_VERSION = 1;
    private static final int MINOR_VERSION = 0;
    private static final int DEFAULT_PORT = 8076;
    private static final String URL_REGEX = "^(?i)jdbc:mapepire://(.+?)(:(\\d+))?(;.+?=.+?)*$";
    private static final String HOST = "HOST";
    private static final String USER = "USER";
    private static final String PASSWORD = "PASSWORD";
    private static final String PORT = "PORT";
    private static final String REJECT_UNAUTHORIZED = "REJECTUNAUTHORIZED";

    @Override
    public int getMajorVersion() {
        return MAJOR_VERSION;
    }

    @Override
    public int getMinorVersion() {
        return MINOR_VERSION;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            throw new SQLException("Invalid URL");
        }

        try {
            final Properties p = new Properties();
            for (Object prop : Collections.list(info.propertyNames())) {
                if (prop instanceof CharSequence) {
                    p.put(prop.toString().toUpperCase(), info.get(prop));
                } else {
                    p.put(prop, info.get(prop));
                }
            }

            Pattern pattern = Pattern.compile(URL_REGEX);
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                p.put(HOST, matcher.group(1));
                if (matcher.group(3) != null) {
                    p.put(PORT, matcher.group(3));
                }
            } else {
                throw new SQLException("Invalid URL");
            }

            parseUrlProperties(url, p);

            DaemonServer server = new DaemonServer();
            Object prop = p.remove(HOST);
            if (prop != null) {
                server.setHost(prop.toString());
            }
            prop = p.remove(USER);
            if (prop != null) {
                server.setUser(prop.toString());
            }
            prop = p.remove(PASSWORD);
            if (prop != null) {
                server.setPassword(prop.toString());
            }
            prop = p.remove(PORT);
            server.setPort(prop != null ? Integer.parseInt(prop.toString()) : DEFAULT_PORT);
            prop = p.remove(REJECT_UNAUTHORIZED);
            if (prop == null) {
                server.setRejectUnauthorized(true);
            } else {
                String rejectUnauthorizedStr = prop.toString().trim();
                if (rejectUnauthorizedStr.equalsIgnoreCase("true")) {
                    server.setRejectUnauthorized(true);
                } else if (rejectUnauthorizedStr.equalsIgnoreCase("false")) {
                    server.setRejectUnauthorized(false);
                } else {
                    throw new SQLException("Invalid value for REJECTUNAUTHORIZED: '"
                            + rejectUnauthorizedStr + "' (expected true or false)");
                }
            }

            JDBCOptions options = new JDBCOptions(p);
            SqlJob job = new SqlJob(options);
            job.connect(server).get();
            return new MapepireConnection(job);
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    /**
     * Parses the semicolon-delimited property tail of a JDBC URL into {@code props}.
     * Splits on {@code ;} then the first {@code =} only — no Properties-file escape
     * processing, so values containing {@code \}, {@code #}, {@code !}, or {@code =}
     * are preserved verbatim.
     */
    static void parseUrlProperties(String url, Properties props) {
        if (!url.contains(";")) {
            return;
        }
        String tail = url.replaceFirst("^[^;]*;", "");
        for (String segment : tail.split(";", -1)) {
            int eq = segment.indexOf('=');
            if (eq > 0) {
                String key = segment.substring(0, eq).toUpperCase();
                String value = segment.substring(eq + 1);
                props.put(key, value);
            }
        }
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        if (url == null) {
            return false;
        } else {
            return url.matches(URL_REGEX);
        }
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return new DriverPropertyInfo[0];
    }

    @Override
    public boolean jdbcCompliant() {
        // TODO: Set to true once there is full support for the JDBC API and full
        // support for SQL 92 Entry Level
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return Logger.getGlobal();
    }
}
