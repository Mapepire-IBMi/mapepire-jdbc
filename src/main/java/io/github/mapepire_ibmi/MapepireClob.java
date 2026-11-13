package io.github.mapepire_ibmi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.SQLException;

/**
 * A CLOB, DBCLOB or NCLOB value held in memory. A clob read from a result set
 * is a copy of the value (see
 * {@link java.sql.DatabaseMetaData#locatorsUpdateCopy()}), so changing it
 * changes nothing in the database until it is written back, for example with
 * {@link java.sql.PreparedStatement#setClob(int, Clob)}.
 */
final class MapepireClob implements NClob {
    private StringBuilder value;

    MapepireClob() {
        this("");
    }

    MapepireClob(String value) {
        this.value = new StringBuilder(value);
    }

    private void checkValid() throws SQLException {
        if (this.value == null) {
            throw new SQLException("Clob has been freed");
        }
    }

    /**
     * The string index of a 1-based position, which may be one past the end so
     * that text can be appended.
     */
    private int index(long pos) throws SQLException {
        checkValid();
        if (pos < 1 || pos > this.value.length() + 1L) {
            throw new SQLException("Invalid position " + pos + " in a clob of length " + this.value.length());
        }
        return (int) (pos - 1);
    }

    @Override
    public long length() throws SQLException {
        checkValid();
        return this.value.length();
    }

    @Override
    public String getSubString(long pos, int length) throws SQLException {
        int start = index(pos);
        if (length < 0) {
            throw new SQLException("Invalid length: " + length);
        }
        return this.value.substring(start, start + Math.min(length, this.value.length() - start));
    }

    @Override
    public Reader getCharacterStream() throws SQLException {
        checkValid();
        return new StringReader(this.value.toString());
    }

    @Override
    public Reader getCharacterStream(long pos, long length) throws SQLException {
        int start = index(pos);
        if (length < 0 || start + length > this.value.length()) {
            throw new SQLException("Invalid length " + length + " at position " + pos + " in a clob of length "
                    + this.value.length());
        }
        return new StringReader(this.value.substring(start, start + (int) length));
    }

    @Override
    public InputStream getAsciiStream() throws SQLException {
        checkValid();
        return new ByteArrayInputStream(this.value.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    @Override
    public long position(String searchstr, long start) throws SQLException {
        checkValid();
        if (start < 1) {
            throw new SQLException("Invalid start position: " + start);
        } else if (start > this.value.length() + 1L) {
            return -1;
        }
        int found = this.value.indexOf(searchstr, (int) (start - 1));
        return found < 0 ? -1 : found + 1;
    }

    @Override
    public long position(Clob searchstr, long start) throws SQLException {
        return position(Lobs.toString(searchstr), start);
    }

    @Override
    public int setString(long pos, String str) throws SQLException {
        return setString(pos, str, 0, str.length());
    }

    @Override
    public int setString(long pos, String str, int offset, int len) throws SQLException {
        int start = index(pos);
        if (offset < 0 || len < 0 || (long) offset + len > str.length()) {
            throw new SQLException("Invalid offset " + offset + " or length " + len + " for a string of length "
                    + str.length());
        }
        Lobs.checkLength((long) start + len);
        this.value.replace(start, Math.min(start + len, this.value.length()), str.substring(offset, offset + len));
        return len;
    }

    @Override
    public OutputStream setAsciiStream(long pos) throws SQLException {
        Writer writer = setCharacterStream(pos);
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                writer.write(b & 0xFF);
            }
        };
    }

    @Override
    public Writer setCharacterStream(long pos) throws SQLException {
        index(pos);
        return new Writer() {
            private long position = pos;

            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                try {
                    this.position += setString(this.position, new String(cbuf, off, len));
                } catch (SQLException e) {
                    throw new IOException(e);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public void truncate(long len) throws SQLException {
        checkValid();
        if (len < 0 || len > this.value.length()) {
            throw new SQLException("Invalid length " + len + " for a clob of length " + this.value.length());
        }
        this.value.setLength((int) len);
    }

    @Override
    public void free() throws SQLException {
        this.value = null;
    }
}
