package dev.dryaides.minuit.forge;

import dev.dryaides.minuit.client.ThemeScreen;
import dev.dryaides.minuit.internal.ContainerTab;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.client.ConfigScreenHandler;
import dev.dryaides.minuit.client.MinuitClient;
import dev.dryaides.minuit.client.ThemeReload;
import dev.dryaides.minuit.client.form.FormScreen;
import dev.dryaides.minuit.internal.formui.ClientForms;
import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.port.mc.Bridges;
import dev.dryaides.minuit.port.mc.BubbleLayer;
import dev.dryaides.minuit.port.mc.HudFeed;
import dev.dryaides.minuit.port.mc.WirePayload;
import dev.dryaides.minuit.wire.Channels;
import dev.dryaides.minuit.internal.port.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Reads the player's theme choice before any screen asks for a theme, and the theme files on each
 * reload. Sends the hello at join when the server has the {@code minuit:hello} channel, and the form
 * input. Draws the speech bubbles over entities under the chat. Client only: {@link MinuitForge} calls
 * it on the client alone.
 */
final class MinuitForgeClient {

    private MinuitForgeClient() {
    }

    static void init(IEventBus modBus) {
        Bridges.install();
        dev.dryaides.minuit.client.MinuitKeys.modifiers(new ForgeKeys());
        Platform.development(!FMLEnvironment.production);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () ->
                new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ThemeScreen(parent, ContainerTab.THEME_ID)));
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(MinuitClient::themeChoices));
        modBus.addListener((RegisterClientReloadListenersEvent event) -> event.registerReloadListener(new ThemeReload()));
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(),
                "bubbles", (gui, pose, partial, width, height) -> BubbleLayer.render(pose, partial)));
        modBus.addListener((RegisterGuiOverlaysEvent event) -> event.registerBelow(VanillaGuiOverlay.CHAT_PANEL.id(),
                "hud", (gui, pose, partial, width, height) -> HudFeed.render(pose, partial)));
        ClientForms.connect(MinuitForgeClient::send, FormScreen.HOST);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) ->
                Minecraft.getInstance().execute(ClientForms::left));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            if (MinuitForge.HELLO.isRemotePresent(event.getConnection())) {
                sendHello();
            }
        });
    }

    static void sendHello() {
        send(Channels.HELLO, Handshakes.clientHello());
    }

    private static void send(String channel, byte[] bytes) {
        WirePayload payload = WirePayload.of(channel, bytes);
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload.channel(), payload.buf()));
        }
    }
}
