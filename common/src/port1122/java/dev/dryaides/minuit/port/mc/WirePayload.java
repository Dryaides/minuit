package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.wire.Channels;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

/**
 * A message on one of the three minuit channels, kept as raw bytes: the protocol module decodes them, so
 * this side holds no format. Forge 1.12.2 names a channel by a plain string of at most 20 chars,
 * {@code minuit:screen} and the others fit.
 */
public final class WirePayload {

    public static final String HELLO = name(Channels.HELLO);
    public static final String SCREEN = name(Channels.SCREEN);
    public static final String INPUT = name(Channels.INPUT);

    private WirePayload() {
    }

    /** The channel name of one of {@code Channels}' paths. */
    public static String name(String path) {
        return Channels.NAMESPACE + ":" + path;
    }

    /** {@code bytes} as a packet on {@code channel}, a full channel name. */
    public static FMLProxyPacket packet(String channel, byte[] bytes) {
        return new FMLProxyPacket(new PacketBuffer(Unpooled.wrappedBuffer(bytes)), channel);
    }

    /** The whole message; read on the netty thread, before the buffer is released. */
    public static byte[] read(FMLProxyPacket packet) {
        ByteBuf buf = packet.payload();
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        return bytes;
    }
}
