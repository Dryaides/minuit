package dev.dryaides.minuit.forge;

import dev.dryaides.minuit.formui.ClientForms;
import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.internal.game.ServerForms;
import dev.dryaides.minuit.port.mc.WirePayload;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Forge entry point: the three minuit channels as raw event channels, all optional so a vanilla client
 * or server still connects, each player's hello and their form input on the server side. A message is
 * the vanilla custom payload packet on the channel's id; Forge hands it to the channel of that name.
 * The client half is {@link MinuitForgeClient}.
 */
@Mod("minuit")
public final class MinuitForge {

    private static final Logger LOG = LoggerFactory.getLogger("minuit");
    private static final String VERSION = "1";

    static final EventNetworkChannel HELLO = channel(WirePayload.HELLO);
    static final EventNetworkChannel SCREEN = channel(WirePayload.SCREEN);
    static final EventNetworkChannel INPUT = channel(WirePayload.INPUT);

    public MinuitForge() {
        // Present on one side only: a vanilla client or server shows no mismatch in the server list.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, server) -> true));
        ServerForms.sender((player, bytes) -> player.connection.send(
                new ClientboundCustomPayloadPacket(WirePayload.SCREEN, new WirePayload(WirePayload.SCREEN, bytes).buf())));
        HELLO.addListener(MinuitForge::hello);
        SCREEN.addListener(MinuitForge::screen);
        INPUT.addListener(MinuitForge::input);
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerForms.left(player);
            }
            Handshakes.left(event.getEntity().getUUID());
        });
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinuitForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }

    private static EventNetworkChannel channel(ResourceLocation id) {
        return NetworkRegistry.newEventChannel(id, () -> VERSION,
                NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
    }

    /** A client's hello on the server; on the client, a server asking for one. */
    private static void hello(NetworkEvent event) {
        NetworkEvent.Context context = event.getSource().get();
        if (event instanceof NetworkEvent.ClientCustomPayloadEvent && context.getSender() != null) {
            ServerPlayer player = context.getSender();
            byte[] bytes = WirePayload.read(event.getPayload());
            context.enqueueWork(() -> Handshakes.received(player.getUUID(), bytes, LOG::warn));
            context.setPacketHandled(true);
        } else if (event instanceof NetworkEvent.ServerCustomPayloadEvent) {
            context.enqueueWork(MinuitForgeClient::sendHello);
            context.setPacketHandled(true);
        }
    }

    private static void screen(NetworkEvent event) {
        if (event instanceof NetworkEvent.ServerCustomPayloadEvent) {
            NetworkEvent.Context context = event.getSource().get();
            byte[] bytes = WirePayload.read(event.getPayload());
            context.enqueueWork(() -> ClientForms.screen(bytes));
            context.setPacketHandled(true);
        }
    }

    private static void input(NetworkEvent event) {
        NetworkEvent.Context context = event.getSource().get();
        if (event instanceof NetworkEvent.ClientCustomPayloadEvent && context.getSender() != null) {
            ServerPlayer player = context.getSender();
            byte[] bytes = WirePayload.read(event.getPayload());
            context.enqueueWork(() -> ServerForms.input(player, bytes));
            context.setPacketHandled(true);
        }
    }
}
