package dev.dryaides.minuit.neoforge;

import dev.dryaides.minuit.client.MinuitClient;
import dev.dryaides.minuit.client.ThemeReload;
import dev.dryaides.minuit.port.mc.Bridges;
import dev.dryaides.minuit.port.mc.BubbleLayer;
import dev.dryaides.minuit.client.form.FormScreen;
import dev.dryaides.minuit.formui.ClientForms;
import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.port.mc.WirePayload;
import dev.dryaides.minuit.internal.port.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Reads the player's theme choice before any screen asks for a theme, and the theme files on each
 * reload. Sends the hello at join when the server accepts {@code minuit:hello}, and the form input.
 * Draws the speech bubbles over entities under the chat.
 */
@Mod(value = "minuit", dist = Dist.CLIENT)
public final class MinuitNeoForgeClient {

    public MinuitNeoForgeClient(IEventBus modBus) {
        Bridges.install();
        Platform.development(!FMLLoader.getCurrent().isProduction());
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork((Runnable) MinuitClient::themeChoices));
        modBus.addListener(AddClientReloadListenersEvent.class, event -> event.addListener(
                Identifier.fromNamespaceAndPath("minuit", ThemeReload.ID), new ThemeReload()));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerBelow(VanillaGuiLayers.CHAT,
                Identifier.fromNamespaceAndPath("minuit", "bubbles"), BubbleLayer::render));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, event -> event.register(WirePayload.HELLO,
                (payload, context) -> context.reply(new WirePayload(WirePayload.HELLO, Handshakes.clientHello()))));
        ClientForms.connect((channel, bytes) -> ClientPacketDistributor.sendToServer(WirePayload.of(channel, bytes)), FormScreen.HOST);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class,
                event -> Minecraft.getInstance().execute(ClientForms::left));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null && connection.hasChannel(WirePayload.HELLO)) {
                ClientPacketDistributor.sendToServer(new WirePayload(WirePayload.HELLO, Handshakes.clientHello()));
            }
        });
    }
}
