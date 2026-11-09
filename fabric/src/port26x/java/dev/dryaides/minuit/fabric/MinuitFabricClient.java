package dev.dryaides.minuit.fabric;

import dev.dryaides.minuit.client.MinuitClient;
import dev.dryaides.minuit.client.ThemeReload;
import dev.dryaides.minuit.port.mc.Bridges;
import dev.dryaides.minuit.port.mc.BubbleLayer;
import dev.dryaides.minuit.client.form.FormScreen;
import dev.dryaides.minuit.formui.ClientForms;
import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.port.mc.WirePayload;
import dev.dryaides.minuit.internal.port.Platform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

/**
 * Reads the player's theme choice before any screen asks for a theme, and the theme files on each
 * reload. Sends the hello once the server accepts {@code minuit:hello}, and shows the forms it sends.
 * Draws the speech bubbles over entities under the chat.
 */
public final class MinuitFabricClient implements ClientModInitializer {

    // Fabric fires the channel REGISTER event for the channels it learnt in login or configuration while the
    // connection is still in that phase, where a play packet ends the join; the hello waits for JOIN.
    private static volatile boolean joined;

    @Override
    public void onInitializeClient() {
        Bridges.install();
        Platform.development(FabricLoader.getInstance().isDevelopmentEnvironment());
        MinuitClient.themeChoices();
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath("minuit", ThemeReload.ID), new ThemeReload());
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("minuit", "bubbles"), BubbleLayer::render);

        ClientPlayNetworking.registerGlobalReceiver(WirePayload.HELLO,
                (payload, context) -> ClientPlayNetworking.send(hello()));
        ClientForms.connect((channel, bytes) -> ClientPlayNetworking.send(WirePayload.of(channel, bytes)), FormScreen.HOST);
        ClientPlayNetworking.registerGlobalReceiver(WirePayload.SCREEN,
                (payload, context) -> context.client().execute(() -> ClientForms.screen(payload.bytes())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            joined = false;
            client.execute(ClientForms::left);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            joined = true;
            if (ClientPlayNetworking.canSend(WirePayload.HELLO)) {
                sender.sendPacket(hello());
            }
        });
        ServerboundPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
            if (joined && channels.contains(WirePayload.HELLO.id())) {
                sender.sendPacket(hello());
            }
        });
    }

    private static WirePayload hello() {
        return new WirePayload(WirePayload.HELLO, Handshakes.clientHello());
    }
}
