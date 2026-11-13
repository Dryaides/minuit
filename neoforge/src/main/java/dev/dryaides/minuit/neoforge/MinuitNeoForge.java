package dev.dryaides.minuit.neoforge;

import dev.dryaides.minuit.internal.formui.ClientForms;
import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.internal.game.ServerForms;
import dev.dryaides.minuit.port.mc.WirePayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge entry point: the three minuit channels, all optional so a vanilla client or server
 * still connects, each player's hello and their form input on the server side. The client half
 * is {@link MinuitNeoForgeClient}.
 */
@Mod("minuit")
public final class MinuitNeoForge {

    private static final Logger LOG = LoggerFactory.getLogger("minuit");

    public MinuitNeoForge(IEventBus modBus) {
        NeoForgeBundling.check();
        modBus.addListener(RegisterPayloadHandlersEvent.class, MinuitNeoForge::register);
        ServerForms.sender((player, bytes) -> PacketDistributor.sendToPlayer(player, new WirePayload(WirePayload.SCREEN, bytes)));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ServerForms.left(player);
            }
            Handshakes.left(event.getEntity().getUUID());
        });
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playBidirectional(WirePayload.HELLO, WirePayload.codec(WirePayload.HELLO), (payload, context) -> {
            if (context.flow() == PacketFlow.SERVERBOUND) {
                context.enqueueWork(() -> Handshakes.received(context.player().getUUID(), payload.bytes(), LOG::warn));
            } else {
                context.reply(new WirePayload(WirePayload.HELLO, Handshakes.clientHello()));
            }
        });
        registrar.playToClient(WirePayload.SCREEN, WirePayload.codec(WirePayload.SCREEN),
                (payload, context) -> context.enqueueWork(() -> ClientForms.screen(payload.bytes())));
        registrar.playToServer(WirePayload.INPUT, WirePayload.codec(WirePayload.INPUT),
                (payload, context) -> context.enqueueWork(() -> ServerForms.input((ServerPlayer) context.player(), payload.bytes())));
    }
}
