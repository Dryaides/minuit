package dev.dryaides.minuit.neoforge;

import dev.dryaides.minuit.client.ThemeScreen;
import dev.dryaides.minuit.internal.ContainerTab;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import dev.dryaides.minuit.client.GameTooltips;
import dev.dryaides.minuit.client.MinuitClient;
import dev.dryaides.minuit.client.ThemeReload;
import dev.dryaides.minuit.port.mc.Bridges;
import dev.dryaides.minuit.port.mc.BubbleLayer;
import dev.dryaides.minuit.port.mc.HudFeed;
import dev.dryaides.minuit.port.mc.GameIds;
import dev.dryaides.minuit.client.form.FormScreen;
import dev.dryaides.minuit.internal.formui.ClientForms;
import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.port.mc.WirePayload;
import dev.dryaides.minuit.internal.port.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Reads the player's theme choice before any screen asks for a theme, and the theme files on each
 * reload. Sends the hello at join when the server accepts {@code minuit:hello}, and the form input.
 * Draws the speech bubbles over entities under the chat, and the tooltip box in the player's theme when they chose so.
 */
@Mod(value = "minuit", dist = Dist.CLIENT)
public final class MinuitNeoForgeClient {

    public MinuitNeoForgeClient(IEventBus modBus, ModContainer container) {
        Bridges.install();
        dev.dryaides.minuit.client.MinuitKeys.modifiers(new NeoForgeKeys());
        Platform.development(!FMLEnvironment.production);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ThemeScreen(parent, ContainerTab.THEME_ID));
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork((Runnable) MinuitClient::themeChoices));
        modBus.addListener(RegisterClientReloadListenersEvent.class,
                event -> event.registerReloadListener(new ThemeReload()));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerBelow(VanillaGuiLayers.CHAT,
                GameIds.of("minuit", "bubbles"), BubbleLayer::render));
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerBelow(VanillaGuiLayers.CHAT,
                GameIds.of("minuit", "hud"), (graphics, delta) -> HudFeed.render(graphics, delta.getGameTimeDeltaPartialTick(false))));
        ClientForms.connect((channel, bytes) -> PacketDistributor.sendToServer(WirePayload.of(channel, bytes)), FormScreen.HOST);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderTooltipEvent.Color.class,
                MinuitNeoForgeClient::themedTooltip);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class,
                event -> Minecraft.getInstance().execute(ClientForms::left));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> {
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null && connection.hasChannel(WirePayload.HELLO)) {
                PacketDistributor.sendToServer(new WirePayload(WirePayload.HELLO, Handshakes.clientHello()));
            }
        });
    }

    /**
     * NeoForge draws the tooltip box through the colour overload, after this event: the theme's frame goes first and
     * the game's fills turn see-through. Colours another mod set keep the game's box in them.
     */
    private static void themedTooltip(RenderTooltipEvent.Color event) {
        boolean gameBox = event.getBackgroundStart() == event.getOriginalBackgroundStart()
                && event.getBackgroundEnd() == event.getOriginalBackgroundEnd()
                && event.getBorderStart() == event.getOriginalBorderStart()
                && event.getBorderEnd() == event.getOriginalBorderEnd();
        if (GameTooltips.frame(event.getGraphics(), event.getX(), event.getY(),
                GameTooltips.width(event.getComponents(), event.getFont()), GameTooltips.height(event.getComponents()),
                GameTooltips.DEPTH, gameBox)) {
            event.setBackground(0);
            event.setBorderStart(0);
            event.setBorderEnd(0);
        }
    }
}
