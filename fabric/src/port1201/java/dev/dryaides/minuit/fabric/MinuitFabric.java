package dev.dryaides.minuit.fabric;

import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.internal.game.ServerForms;
import dev.dryaides.minuit.port.mc.WirePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the minuit channels, keeps each player's hello and routes their form input on the server
 * side. 1.20.1 channels carry a raw buffer: the bytes are read before the game frees it.
 */
public final class MinuitFabric implements ModInitializer {

    private static final Logger LOG = LoggerFactory.getLogger("minuit");

    @Override
    public void onInitialize() {
        ServerForms.sender((player, bytes) -> ServerPlayNetworking.send(player, WirePayload.SCREEN,
                new WirePayload(WirePayload.SCREEN, bytes).buf()));
        ServerPlayNetworking.registerGlobalReceiver(WirePayload.HELLO, (server, player, handler, buf, sender) -> {
            byte[] bytes = WirePayload.read(buf);
            server.execute(() -> Handshakes.received(player.getUUID(), bytes, LOG::warn));
        });
        ServerPlayNetworking.registerGlobalReceiver(WirePayload.INPUT, (server, player, handler, buf, sender) -> {
            byte[] bytes = WirePayload.read(buf);
            server.execute(() -> ServerForms.input(player, bytes));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerForms.left(handler.getPlayer());
            Handshakes.left(handler.getPlayer().getUUID());
        });
    }
}
