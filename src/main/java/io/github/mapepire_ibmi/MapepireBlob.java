package io.github.mapepire_ibmi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * A BLOB value held in memory. A blob read from a result set is a copy of the
 * value (see {@link java.sql.DatabaseMetaData#locatorsUpdateCopy()}), so
 * changing it changes nothing in the database until it is written back, for
 * example with {@link java.sql.PreparedStatement#setBlob(int, Blob)}.
 */
final class MapepireBlob implements Blob {
    private byte[] data;
    private int length;

    MapepireBlob() {
        this(new byte[0]);
    }

    /** A blob over the given bytes, which it takes ownership of. */
    MapepireBlob(byte[] data) {
        this.data = data;
        this.length = data.length;
    }

    private void checkValid() throws SQLException {
        if (this.data == null) {
            throw new SQLException("Blob has been freed");
        }
    }

    /**
     * The array index of a 1-based position, which may be one past the end so
     * that a value can be appended.
     */
    private int index(long pos) throws SQLException {
        checkValid();
        if (pos < 1 || pos > this.length + 1L) {
            throw new SQLException("Invalid position " + pos + " in a blob of length " + this.length);
        }
        return (int) (pos - 1);
    }

    @Override
    public long length() throws SQLException {
        checkValid();
        return this.length;
    }

    @Override
    public byte[] getBytes(long pos, int length) throws SQLException {
        int start = index(pos);
        if (length < 0) {
            throw new SQLException("Invalid length: " + length);
        }
        return Arrays.copyOfRange(this.data, start, start + Math.min(length, this.length - start));
    }

    @Override
    public InputStream getBinaryStream() throws SQLException {
        checkValid();
        return new ByteArrayInputStream(this.data, 0, this.length);
    }

    @Override
    public InputStream getBinaryStream(long pos, long length) throws SQLException {
        int start = index(pos);
        if (length < 0 || start + length > this.length) {
            throw new SQLException("Invalid length " + length + " at position " + pos + " in a blob of length "
                    + this.length);
        }
        return new ByteArrayInputStream(this.data, start, (int) length);
    }

    @Override
    public long position(byte[] pattern, long start) throws SQLException {
        checkValid();
        if (start < 1) {
            throw new SQLException("Invalid start position: " + start);
        }
        for (long i = start - 1; i + pattern.length <= this.length; i++) {
            if (matchesAt(pattern, (int) i)) {
                return i + 1;
            }
        }
        return -1;
    }

    private boolean matchesAt(byte[] pattern, int index) {
        for (int j = 0; j < pattern.length; j++) {
            if (this.data[index + j] != pattern[j]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public long position(Blob pattern, long start) throws SQLException {
        return position(Lobs.toBytes(pattern), start);
    }

    @Override
    public int setBytes(long pos, byte[] bytes) throws SQLException {
        return setBytes(pos, bytes, 0, bytes.length);
    }

    @Override
    public int setBytes(long pos, byte[] bytes, int offset, int len) throws SQLException {
        int start = index(pos);
        if (offset < 0 || len < 0 || (long) offset + len > bytes.length) {
            throw new SQLException("Invalid offset " + offset + " or length " + len + " for an array of length "
                    + bytes.length);
        }
        int end = Lobs.checkLength((long) start + len);
        if (end > this.data.length) {
            // Grow geometrically so that writing a stream a little at a time stays linear
            this.data = Arrays.copyOf(this.data, (int) Math.max(end, Math.min(Lobs.MAX_LENGTH, 2L * this.data.length)));
        }
        System.arraycopy(bytes, offset, this.data, start, len);
        this.length = Math.max(this.length, end);
        return len;
    }

    @Override
    public OutputStream setBinaryStream(long pos) throws SQLException {
        index(pos);
        return new OutputStream() {
            private long position = pos;

            @Override
            public void write(int b) throws IOException {
                write(new byte[] { (byte) b }, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                try {
                    this.position += setBytes(this.position, b, off, len);
                } catch (SQLException e) {
                    throw new IOException(e);
                }
            }
        };
    }

    @Override
    public void truncate(long len) throws SQLException {
        checkValid();
        if (len < 0 || len > this.length) {
            throw new SQLException("Invalid length " + len + " for a blob of length " + this.length);
        }
        this.length = (int) len;
    }

    @Override
    public void free() throws SQLException {
        this.data = null;
    }
}
