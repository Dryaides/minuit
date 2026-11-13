package dev.dryaides.minuit.wire;

import java.nio.charset.StandardCharsets;

/** Reads the wire primitives; any break of the format throws {@link WireException}. */
public final class ByteReader {

    private final byte[] bytes;
    private int at;

    public ByteReader(byte[] bytes) {
        this.bytes = bytes;
    }

    public int varint() {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = u8();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new WireException("varint longer than 5 bytes");
    }

    /** A varint over 64 bits, at most 9 bytes so the value stays 0 or more. */
    public long varlong() {
        long value = 0;
        for (int shift = 0; shift < 63; shift += 7) {
            int b = u8();
            value |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new WireException("varlong longer than 9 bytes");
    }

    public int svarint() {
        int raw = varint();
        return (raw >>> 1) ^ -(raw & 1);
    }

    public boolean bool() {
        int b = u8();
        if (b > 1) {
            throw new WireException("bool byte " + b);
        }
        return b == 1;
    }

    public int u8() {
        if (at >= bytes.length) {
            throw new WireException("message ends early");
        }
        return bytes[at++] & 0xFF;
    }

    /** Drops the message when the byte length exceeds 4 times {@code maxChars}, or the text does. */
    public String str(int maxChars) {
        int length = varint();
        if (length < 0 || length > 4 * maxChars) {
            throw new WireException("string of " + length + " bytes, cap " + maxChars + " characters");
        }
        if (length > bytes.length - at) {
            throw new WireException("message ends early");
        }
        String value = new String(bytes, at, length, StandardCharsets.UTF_8);
        at += length;
        if (value.codePointCount(0, value.length()) > maxChars) {
            throw new WireException("string over " + maxChars + " characters");
        }
        return value;
    }

    /** The next {@code length} bytes as they are. */
    public byte[] bytes(int length) {
        if (length < 0 || length > bytes.length - at) {
            throw new WireException("message ends early");
        }
        byte[] slice = java.util.Arrays.copyOfRange(bytes, at, at + length);
        at += length;
        return slice;
    }

    /** Bytes not read yet. */
    public int remaining() {
        return bytes.length - at;
    }
}
