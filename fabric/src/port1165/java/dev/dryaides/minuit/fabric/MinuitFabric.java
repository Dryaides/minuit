package dev.dryaides.minuit.fabric;

import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.internal.game.ServerForms;
import dev.dryaides.minuit.port.mc.WirePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Registers the minUIt channels, keeps each player's hello and routes their form input on the server
 * side. 1.16.5 channels carry a raw buffer: the bytes are read before the game frees it.
 */
public final class MinuitFabric implements ModInitializer {

    private static final Logger LOG = LogManager.getLogger("minuit");

    @Override
    public void onInitialize() {
        FabricBundling.check();
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
            ServerForms.left(handler.player);
            Handshakes.left(handler.player.getUUID());
        });
    }
}
