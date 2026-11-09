package dev.dryaides.minuit.fabric;

import dev.dryaides.minuit.client.MinuitClient;
import dev.dryaides.minuit.client.ThemeReload;
import dev.dryaides.minuit.port.mc.Bridges;
import dev.dryaides.minuit.port.mc.BubbleLayer;
import dev.dryaides.minuit.port.mc.GameIds;
import dev.dryaides.minuit.client.form.FormScreen;
import dev.dryaides.minuit.formui.ClientForms;
import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.port.mc.WirePayload;
import dev.dryaides.minuit.internal.port.Platform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Reads the player's theme choice before any screen asks for a theme, and the theme files on each
 * reload. Sends the hello once the server accepts {@code minuit:hello}, and shows the forms it sends.
 * Draws the speech bubbles over entities: Fabric API on 1.16.5 has no HUD layers, so they come
 * after the whole HUD, the chat included.
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
        ResourceLocation themes = GameIds.of("minuit", ThemeReload.ID);
        ThemeReload reload = new ThemeReload();
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return themes;
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                reload.onResourceManagerReload(manager);
            }
        });
        HudRenderCallback.EVENT.register(BubbleLayer::render);

        ClientPlayNetworking.registerGlobalReceiver(WirePayload.HELLO,
                (client, handler, buf, sender) -> sender.sendPacket(WirePayload.HELLO, hello().buf()));
        ClientForms.connect((channel, bytes) -> {
            WirePayload payload = WirePayload.of(channel, bytes);
            ClientPlayNetworking.send(payload.channel(), payload.buf());
        }, FormScreen.HOST);
        ClientPlayNetworking.registerGlobalReceiver(WirePayload.SCREEN, (client, handler, buf, sender) -> {
            byte[] bytes = WirePayload.read(buf);
            client.execute(() -> ClientForms.screen(bytes));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            joined = false;
            client.execute(ClientForms::left);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            joined = true;
            if (ClientPlayNetworking.canSend(WirePayload.HELLO)) {
                sender.sendPacket(WirePayload.HELLO, hello().buf());
            }
        });
        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
            if (joined && channels.contains(WirePayload.HELLO)) {
                sender.sendPacket(WirePayload.HELLO, hello().buf());
            }
        });
    }

    private static WirePayload hello() {
        return new WirePayload(WirePayload.HELLO, Handshakes.clientHello());
    }
}
