package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.wire.Channels;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * The body of a message on one of the three minuit channels, kept as raw bytes: the protocol module
 * decodes them, so this side holds no format. 1.20.1 has no payload types: a message is its channel id
 * and a byte buffer, which the loader sends on that channel.
 */
public record WirePayload(ResourceLocation channel, byte[] bytes) {

    public static final ResourceLocation HELLO = id(Channels.HELLO);
    public static final ResourceLocation SCREEN = id(Channels.SCREEN);
    public static final ResourceLocation INPUT = id(Channels.INPUT);

    /** The payload of {@code bytes} on {@code channel}, one of {@code Channels}' paths. */
    public static WirePayload of(String channel, byte[] bytes) {
        ResourceLocation id = switch (channel) {
            case Channels.HELLO -> HELLO;
            case Channels.SCREEN -> SCREEN;
            case Channels.INPUT -> INPUT;
            default -> throw new IllegalArgumentException("not a minuit channel: " + channel);
        };
        return new WirePayload(id, bytes);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Channels.NAMESPACE, path);
    }

    /** The whole buffer is the message. */
    public FriendlyByteBuf buf() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(this.bytes));
    }

    /** What is left to read in {@code buf}, all of it. */
    public static byte[] read(FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }
}
