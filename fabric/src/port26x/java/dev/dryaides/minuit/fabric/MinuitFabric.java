package dev.dryaides.minuit.fabric;

import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.internal.game.ServerForms;
import dev.dryaides.minuit.port.mc.WirePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers the three minuit channels, keeps each player's hello and routes their form input on the server side. */
public final class MinuitFabric implements ModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger("minuit");

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(WirePayload.HELLO, WirePayload.codec(WirePayload.HELLO));
        PayloadTypeRegistry.clientboundPlay().register(WirePayload.HELLO, WirePayload.codec(WirePayload.HELLO));
        PayloadTypeRegistry.clientboundPlay().register(WirePayload.SCREEN, WirePayload.codec(WirePayload.SCREEN));
        PayloadTypeRegistry.serverboundPlay().register(WirePayload.INPUT, WirePayload.codec(WirePayload.INPUT));

        ServerForms.sender((player, bytes) -> ServerPlayNetworking.send(player, new WirePayload(WirePayload.SCREEN, bytes)));
        ServerPlayNetworking.registerGlobalReceiver(WirePayload.HELLO,
                (payload, context) -> context.server().execute(() -> Handshakes.received(context.player().getUUID(), payload.bytes(), LOG::warn)));
        ServerPlayNetworking.registerGlobalReceiver(WirePayload.INPUT,
                (payload, context) -> context.server().execute(() -> ServerForms.input(context.player(), payload.bytes())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerForms.left(handler.getPlayer());
            Handshakes.left(handler.getPlayer().getUUID());
        });
    }
}
