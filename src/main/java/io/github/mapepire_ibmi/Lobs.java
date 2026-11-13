package io.github.mapepire_ibmi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;

/**
 * Conversions for binary and large object values. The Mapepire protocol is
 * JSON, so a value is always sent and received whole: streams are read into
 * memory before a value is sent, and values read from the server are held in
 * memory.
 */
final class Lobs {
    /** The largest value a Java array, and so a LOB held in memory, can hold. */
    static final int MAX_LENGTH = Integer.MAX_VALUE - 8;

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private static final int BUFFER_SIZE = 8192;
    private static final long TO_END = -1;

    private Lobs() {
    }

    /**
     * The value to send or store for a JDBC object: a {@link Blob}'s bytes, a
     * {@link Clob}'s text, a copy of a byte array, or the object itself.
     */
    static Object materialize(Object x) throws SQLException {
        if (x instanceof Blob) {
            return toBytes((Blob) x);
        } else if (x instanceof Clob) {
            return toString((Clob) x);
        } else if (x instanceof byte[]) {
            // A copy, so a buffer the caller reuses does not change a value already set
            return ((byte[]) x).clone();
        }
        return x;
    }

    static byte[] toBytes(Blob blob) throws SQLException {
        return blob == null ? null : blob.getBytes(1, checkLength(blob.length()));
    }

    static String toString(Clob clob) throws SQLException {
        return clob == null ? null : clob.getSubString(1, checkLength(clob.length()));
    }

    /** Read a stream to its end. */
    static byte[] readBytes(InputStream in) throws SQLException {
        return read(in, TO_END);
    }

    /** Read exactly {@code length} bytes from a stream. */
    static byte[] readBytes(InputStream in, long length) throws SQLException {
        checkStreamLength(length);
        return read(in, length);
    }

    /** Read a stream of one-byte characters to its end. */
    static String readAscii(InputStream in) throws SQLException {
        return ascii(read(in, TO_END));
    }

    /** Read exactly {@code length} one-byte characters from a stream. */
    static String readAscii(InputStream in, long length) throws SQLException {
        return ascii(readBytes(in, length));
    }

    private static String ascii(byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.ISO_8859_1);
    }

    /** Read a character stream to its end. */
    static String readString(Reader reader) throws SQLException {
        return read(reader, TO_END);
    }

    /** Read exactly {@code length} characters from a character stream. */
    static String readString(Reader reader, long length) throws SQLException {
        checkStreamLength(length);
        return read(reader, length);
    }

    /** Read {@code length} bytes from a stream, or to its end for {@link #TO_END}. */
    private static byte[] read(InputStream in, long length) throws SQLException {
        if (in == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = length == TO_END ? Long.MAX_VALUE : length;
        try {
            while (remaining > 0) {
                int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read the stream", e);
        }
        checkStreamEnd(length, remaining);
        return out.toByteArray();
    }

    /** Read {@code length} characters from a stream, or to its end for {@link #TO_END}. */
    private static String read(Reader reader, long length) throws SQLException {
        if (reader == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[BUFFER_SIZE];
        long remaining = length == TO_END ? Long.MAX_VALUE : length;
        try {
            while (remaining > 0) {
                int read = reader.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                out.append(buffer, 0, read);
                remaining -= read;
            }
        } catch (IOException e) {
            throw new SQLException("Failed to read the stream", e);
        }
        checkStreamEnd(length, remaining);
        return out.toString();
    }

    private static void checkStreamLength(long length) throws SQLException {
        if (length < 0) {
            throw new SQLException("Invalid stream length: " + length);
        }
        checkLength(length);
    }

    private static void checkStreamEnd(long length, long remaining) throws SQLException {
        if (length != TO_END && remaining > 0) {
            throw new SQLException("The stream ended after " + (length - remaining) + " of the " + length
                    + " bytes or characters given as its length");
        }
    }

    static int checkLength(long length) throws SQLException {
        if (length > MAX_LENGTH) {
            throw new SQLException("A value of " + length + " bytes or characters is too large; at most "
                    + MAX_LENGTH + " are supported");
        }
        return (int) length;
    }

    static String toHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            hex[2 * i] = HEX_DIGITS[(bytes[i] >> 4) & 0xF];
            hex[2 * i + 1] = HEX_DIGITS[bytes[i] & 0xF];
        }
        return new String(hex);
    }

    static byte[] fromHex(String hex) throws SQLException {
        if (hex.length() % 2 != 0) {
            throw new SQLException("Invalid hex value: odd number of digits");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(hex.charAt(2 * i), 16);
            int low = Character.digit(hex.charAt(2 * i + 1), 16);
            if (high < 0 || low < 0) {
                throw new SQLException("Invalid hex value: '" + hex.substring(2 * i, 2 * i + 2) + "'");
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
