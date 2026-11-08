package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.wire.Channels;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The body of a message on one of the three minuit channels, kept as raw bytes: the protocol
 * module decodes them, so this side holds no format.
 */
public record WirePayload(Type<WirePayload> type, byte[] bytes) implements CustomPacketPayload {

    public static final Type<WirePayload> HELLO = type(Channels.HELLO);
    public static final Type<WirePayload> SCREEN = type(Channels.SCREEN);
    public static final Type<WirePayload> INPUT = type(Channels.INPUT);

    /** The payload of {@code bytes} on {@code channel}, one of {@code Channels}' paths. */
    public static WirePayload of(String channel, byte[] bytes) {
        Type<WirePayload> type = switch (channel) {
            case Channels.HELLO -> HELLO;
            case Channels.SCREEN -> SCREEN;
            case Channels.INPUT -> INPUT;
            default -> throw new IllegalArgumentException("not a minuit channel: " + channel);
        };
        return new WirePayload(type, bytes);
    }

    private static Type<WirePayload> type(String path) {
        return new Type<>(Identifier.fromNamespaceAndPath(Channels.NAMESPACE, path));
    }

    /** The codec of one channel: the whole payload is the message. */
    public static StreamCodec<FriendlyByteBuf, WirePayload> codec(Type<WirePayload> type) {
        return StreamCodec.of(
                (buf, payload) -> buf.writeBytes(payload.bytes),
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new WirePayload(type, bytes);
                });
    }
}
