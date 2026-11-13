package io.github.mapepire_ibmi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the in-memory Blob and Clob values and the stream conversions
 * used to send LOB values.
 */
class MapepireLobTest {
    private final MapepireConnection connection = new MapepireConnection(new SqlJob());

    @Test
    void blobGrowsAsItIsWrittenAndCanBeSearchedAndTruncated() throws Exception {
        Blob blob = this.connection.createBlob();
        assertEquals(3, blob.setBytes(1, new byte[] { 1, 2, 3 }));
        try (OutputStream out = blob.setBinaryStream(4)) {
            out.write(new byte[] { 4, 5 });
            out.write(6);
        }
        blob.setBytes(2, new byte[] { 9 });

        assertEquals(6, blob.length());
        assertArrayEquals(new byte[] { 9, 3, 4 }, blob.getBytes(2, 3));
        assertArrayEquals(new byte[] { 5, 6 }, blob.getBytes(5, 100));
        assertEquals(3, blob.position(new byte[] { 3, 4 }, 1));
        assertEquals(-1, blob.position(new byte[] { 3, 4 }, 4));
        blob.truncate(2);
        assertArrayEquals(new byte[] { 1, 9 }, Lobs.readBytes(blob.getBinaryStream()));
        // Writing may append but not leave a gap
        assertThrows(SQLException.class, () -> blob.setBytes(4, new byte[] { 1 }));
    }

    @Test
    void clobOverwritesAppendsAndSearches() throws Exception {
        Clob clob = this.connection.createClob();
        clob.setString(1, "hello world");
        clob.setString(7, "there, friend");
        try (Writer writer = clob.setCharacterStream(clob.length() + 1)) {
            writer.write("!");
        }

        assertEquals("hello there, friend!", clob.getSubString(1, (int) clob.length()));
        assertEquals("there", clob.getSubString(7, 5));
        assertEquals(14, clob.position("friend", 1));
        assertEquals(-1, clob.position("hello", 2));
        clob.truncate(5);
        assertEquals("hello", Lobs.readString(clob.getCharacterStream()));
    }

    @Test
    void freedLobsCannotBeUsed() throws SQLException {
        Blob blob = new MapepireBlob(new byte[] { 1 });
        Clob clob = new MapepireClob("a");
        blob.free();
        clob.free();
        assertThrows(SQLException.class, blob::length);
        assertThrows(SQLException.class, clob::length);
    }

    @Test
    void streamsAreReadToTheirGivenLength() throws SQLException {
        assertArrayEquals(new byte[] { 1, 2 }, Lobs.readBytes(new ByteArrayInputStream(new byte[] { 1, 2, 3 }), 2));
        assertEquals("ab", Lobs.readString(new StringReader("abc"), 2));
        SQLException e = assertThrows(SQLException.class,
                () -> Lobs.readBytes(new ByteArrayInputStream(new byte[] { 1 }), 2));
        assertEquals("The stream ended after 1 of the 2 bytes or characters given as its length", e.getMessage());
        assertThrows(SQLException.class, () -> Lobs.readString(new StringReader("abc"), -1));

        assertEquals("00FF7F", Lobs.toHex(new byte[] { 0, -1, 127 }));
        assertArrayEquals(new byte[] { 0, -1, 127 }, Lobs.fromHex("00ff7F"));
        assertThrows(SQLException.class, () -> Lobs.fromHex("0G"));
    }
}
