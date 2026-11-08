package dev.dryaides.minuit.wire;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Writes the wire primitives into a growing byte array. */
public final class ByteWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    /** Minecraft's LEB128 varint; a negative int takes 5 bytes. */
    public ByteWriter varint(int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
        return this;
    }

    /** The zigzag varint, for signed values. */
    public ByteWriter svarint(int value) {
        return varint((value << 1) ^ (value >> 31));
    }

    public ByteWriter bool(boolean value) {
        out.write(value ? 1 : 0);
        return this;
    }

    public ByteWriter u8(int value) {
        out.write(value & 0xFF);
        return this;
    }

    /** A varint byte length, then UTF-8; {@code maxChars} is the field's cap in characters. */
    public ByteWriter str(String value, int maxChars) {
        if (value.codePointCount(0, value.length()) > maxChars) {
            throw new WireException("string over " + maxChars + " characters");
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        varint(bytes.length);
        out.write(bytes, 0, bytes.length);
        return this;
    }

    /** Raw bytes, with no length in front. */
    public ByteWriter bytes(byte[] value) {
        out.write(value, 0, value.length);
        return this;
    }

    public byte[] toBytes() {
        return out.toByteArray();
    }
}
