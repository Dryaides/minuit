package dev.dryaides.minuit.neoforge;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Reads the player's theme choice before any screen asks for a theme, and the theme files on each
 * reload. Sends the hello at join when the server accepts {@code minuit:hello}, and the form input.
 * Draws the speech bubbles over entities under the chat.
 */
@Mod(value = "minuit", dist = Dist.CLIENT)
public final class MinuitNeoForgeClient {

    public MinuitNeoForgeClient(IEventBus modBus) {
        Bridges.install();
        Platform.development(!FMLEnvironment.production);
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork((Runnable) MinuitClient::themeChoices));
        modBus.addListener(RegisterClientReloadListenersEvent.class,
                event -> event.registerReloadListener(new ThemeReload()));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerBelow(VanillaGuiLayers.CHAT,
                GameIds.of("minuit", "bubbles"), BubbleLayer::render));
        ClientForms.connect((channel, bytes) -> PacketDistributor.sendToServer(WirePayload.of(channel, bytes)), FormScreen.HOST);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class,
                event -> Minecraft.getInstance().execute(ClientForms::left));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null && connection.hasChannel(WirePayload.HELLO)) {
                PacketDistributor.sendToServer(new WirePayload(WirePayload.HELLO, Handshakes.clientHello()));
            }
        });
    }
}
