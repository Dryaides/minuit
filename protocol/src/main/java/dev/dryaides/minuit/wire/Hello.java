package dev.dryaides.minuit.wire;

import java.util.ArrayList;
import java.util.List;

/**
 * The client hello on {@code minuit:hello}: {@code varint protocol}, {@code str version} (32
 * characters), then a varint count of capabilities (at most 64), each a {@code str} (64).
 * Bytes after the last capability are left for a newer 1.x and skipped.
 */
public record Hello(int protocol, String version, List<String> caps) {

    /** The protocol of all of minuit 1.x. */
    public static final int PROTOCOL = 1;
    public static final int MAX_VERSION = 32;
    public static final int MAX_CAPS = 64;
    public static final int MAX_CAP = 64;

    public Hello {
        caps = List.copyOf(caps);
    }

    public byte[] encode() {
        ByteWriter out = new ByteWriter().varint(protocol).str(version, MAX_VERSION);
        writeCaps(out, caps);
        return out.toBytes();
    }

    public static Hello decode(byte[] bytes) {
        ByteReader in = new ByteReader(bytes);
        return new Hello(in.varint(), in.str(MAX_VERSION), readCaps(in));
    }

    static void writeCaps(ByteWriter out, List<String> caps) {
        if (caps.size() > MAX_CAPS) {
            throw new WireException(caps.size() + " capabilities, cap " + MAX_CAPS);
        }
        out.varint(caps.size());
        for (String cap : caps) {
            out.str(cap, MAX_CAP);
        }
    }

    static List<String> readCaps(ByteReader in) {
        int count = in.varint();
        if (count < 0 || count > MAX_CAPS) {
            throw new WireException(count + " capabilities, cap " + MAX_CAPS);
        }
        List<String> caps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            caps.add(in.str(MAX_CAP));
        }
        return caps;
    }
}
