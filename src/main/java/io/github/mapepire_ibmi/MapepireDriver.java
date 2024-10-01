package io.github.mapepire_ibmi;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import io.github.mapepire_ibmi.types.DaemonServer;
import io.github.mapepire_ibmi.types.JDBCOptions;
import io.github.mapepire_ibmi.types.exceptions.UnknownServerException;

public class MapepireDriver implements Driver {

    private static final int MAJOR_VERSION = 1;
    private static final int MINOR_VERSION = 0;

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
            return null;
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

            final String propertiesFromConnectionString = url.contains(";") ? url.replaceFirst("^[^;]*;", "") : "";
            Properties connStringProps = new Properties();
            connStringProps.load(new StringReader(propertiesFromConnectionString.replace(';', '\n')));
            for (Object prop : Collections.list(connStringProps.propertyNames())) {
                if (prop instanceof CharSequence) {
                    p.put(prop.toString().toUpperCase(), connStringProps.get(prop));
                } else {
                    p.put(prop, connStringProps.get(prop));
                }
            }

            DaemonServer server = new DaemonServer();
            Object prop = p.remove("HOST");
            if (prop != null) {
                server.setHost(prop.toString());
            }
            prop = p.remove("USER");
            if (prop != null) {
                server.setUser(prop.toString());
            }
            prop = p.remove("PASSWORD");
            if (prop != null) {
                server.setPassword(prop.toString());
            }
            prop = p.remove("PORT");
            if (prop != null) {
                server.setPort((Integer) prop);
            }
            server.setIgnoreUnauthorized(true);
            server.setCa("");

            JDBCOptions options = new JDBCOptions(p);
            SqlJob job = new SqlJob(options);
            job.connect(server);
            return new MapepireConnection(job);
        } catch (IOException | KeyManagementException | NoSuchAlgorithmException | InterruptedException
                | ExecutionException | URISyntaxException | UnknownServerException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url.matches("^(?i)jdbc:mapepire://(.+?)(:\\d+)?(;.+?=.+?)*$");
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
