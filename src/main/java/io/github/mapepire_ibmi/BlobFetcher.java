package io.github.mapepire_ibmi;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.sql.SQLException;
import java.util.Base64;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import io.github.mapepire_ibmi.types.DaemonServer;

/**
 * Downloads the binary values that mapepire-server 2.4.0 and later return as
 * references, {@code {"blob_url": "/blob/<token>", "size": n}}, instead of
 * inline. The server serves each value once, over HTTPS on the daemon's port,
 * to the user that ran the query, and discards it after its
 * {@code BLOB_TOKEN_TTL} (60 seconds by default).
 */
class BlobFetcher {
    private static final Pattern BLOB_PATH = Pattern.compile("/blob/[A-Za-z0-9-]+");

    private final DaemonServer server;
    private SSLSocketFactory socketFactory;

    BlobFetcher(DaemonServer server) {
        this.server = server;
    }

    /**
     * Download a value.
     *
     * @param path          The {@code blob_url} the server returned.
     * @param timeoutMillis The connect and read timeout, or 0 for none.
     */
    byte[] fetch(String path, int timeoutMillis) throws SQLException {
        // Only ever send the credentials to the server's own blob endpoint
        if (path == null || !BLOB_PATH.matcher(path).matches()) {
            throw new SQLException("Invalid BLOB reference from the server: " + path);
        }
        try {
            HttpURLConnection http = open(new URL("https", this.server.getHost(), this.server.getPort(), path));
            http.setConnectTimeout(timeoutMillis);
            http.setReadTimeout(timeoutMillis);
            http.setUseCaches(false);
            String credentials = this.server.getUser() + ":" + this.server.getPassword();
            http.setRequestProperty("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));

            int status = http.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                http.disconnect();
                throw new SQLException(status == HttpURLConnection.HTTP_NOT_FOUND
                        ? "The BLOB value is no longer available on the server. The server keeps each value for "
                                + "BLOB_TOKEN_TTL seconds (60 by default) after it returns the row, and serves it "
                                + "once, so read binary columns soon after fetching their rows"
                        : "Failed to download a BLOB value from the server: HTTP " + status);
            }
            try (InputStream in = http.getInputStream()) {
                return Lobs.readBytes(in);
            }
        } catch (IOException e) {
            throw new SQLException("Failed to download a BLOB value from the server", e);
        }
    }

    /**
     * Open an HTTPS connection that trusts the server as the WebSocket
     * connection does. Like the WebSocket, it does not use a proxy.
     */
    HttpURLConnection open(URL url) throws IOException, SQLException {
        HttpsURLConnection https = (HttpsURLConnection) url.openConnection(Proxy.NO_PROXY);
        https.setSSLSocketFactory(socketFactory());
        if (!this.server.getRejectUnauthorized()) {
            https.setHostnameVerifier((host, session) -> true);
        }
        return https;
    }

    /**
     * The JVM's default trust store, or trust in any certificate when
     * {@code rejectUnauthorized} is false, as for the WebSocket of a connection
     * opened by the driver, which sets no custom CA. The JVM-wide default
     * context cannot be used: the SDK replaces it with the trust of whichever
     * connection opened last.
     */
    private synchronized SSLSocketFactory socketFactory() throws SQLException {
        if (this.socketFactory == null) {
            try {
                TrustManager[] trustManagers;
                if (this.server.getRejectUnauthorized()) {
                    TrustManagerFactory factory = TrustManagerFactory
                            .getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    factory.init((KeyStore) null);
                    trustManagers = factory.getTrustManagers();
                } else {
                    trustManagers = new TrustManager[] { new TrustAll() };
                }
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, trustManagers, null);
                this.socketFactory = context.getSocketFactory();
            } catch (GeneralSecurityException e) {
                throw new SQLException("Failed to set up TLS to download BLOB values", e);
            }
        }
        return this.socketFactory;
    }

    private static final class TrustAll implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
