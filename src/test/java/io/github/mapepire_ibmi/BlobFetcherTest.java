package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import io.github.mapepire_ibmi.types.DaemonServer;

/**
 * Unit tests for downloading BLOB references, against a mocked HTTP
 * connection.
 */
class BlobFetcherTest {
    private final HttpURLConnection http = mock(HttpURLConnection.class);
    private URL opened;

    private final BlobFetcher fetcher = new BlobFetcher(new DaemonServer("ibmi.example.com", 8076, "user", "pass")) {
        @Override
        HttpURLConnection open(URL url) {
            opened = url;
            return http;
        }
    };

    @Test
    void fetchDownloadsFromTheServerWithTheConnectionCredentials() throws Exception {
        when(this.http.getResponseCode()).thenReturn(200);
        when(this.http.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 1, 2, 3 }));

        assertArrayEquals(new byte[] { 1, 2, 3 }, this.fetcher.fetch("/blob/0f3a-91c2", 5000));
        assertEquals("https://ibmi.example.com:8076/blob/0f3a-91c2", this.opened.toString());
        // Base64 of "user:pass", as sent with the WebSocket connection
        verify(this.http).setRequestProperty("Authorization", "Basic dXNlcjpwYXNz");
        verify(this.http).setReadTimeout(5000);
    }

    @Test
    void fetchExplainsAValueThatIsNoLongerAvailable() throws Exception {
        when(this.http.getResponseCode()).thenReturn(404);

        SQLException e = assertThrows(SQLException.class, () -> this.fetcher.fetch("/blob/abc", 0));
        assertTrue(e.getMessage().contains("BLOB_TOKEN_TTL"), e.getMessage());
    }

    @Test
    void fetchOnlySendsCredentialsToTheBlobEndpoint() {
        String[] paths = { null, "/version", "/blob/../version", "//evil.example.com/blob/abc", "/blob/abc?x=1" };
        for (String path : paths) {
            assertThrows(SQLException.class, () -> this.fetcher.fetch(path, 0), path);
        }
        assertNull(this.opened);
    }
}
