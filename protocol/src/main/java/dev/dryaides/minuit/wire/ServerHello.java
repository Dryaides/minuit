package dev.dryaides.minuit.wire;

import java.util.List;

/**
 * The server hello on {@code minuit:hello}: {@code varint protocol}, then the capabilities the
 * server uses, in the same form as {@link Hello}. A client answers it with its own hello.
 */
public record ServerHello(int protocol, List<String> caps) {

    public ServerHello {
        caps = List.copyOf(caps);
    }

    public byte[] encode() {
        ByteWriter out = new ByteWriter().varint(protocol);
        Hello.writeCaps(out, caps);
        return out.toBytes();
    }

    public static ServerHello decode(byte[] bytes) {
        ByteReader in = new ByteReader(bytes);
        return new ServerHello(in.varint(), Hello.readCaps(in));
    }
}
