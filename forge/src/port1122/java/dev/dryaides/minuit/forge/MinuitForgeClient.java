package dev.dryaides.minuit.forge;

import dev.dryaides.minuit.client.ContainerTabs;
import dev.dryaides.minuit.client.MinuitClient;
import dev.dryaides.minuit.client.MinuitMenuScreens;
import dev.dryaides.minuit.client.ThemeReload;
import dev.dryaides.minuit.client.form.FormScreen;
import dev.dryaides.minuit.internal.formui.ClientForms;
import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.internal.port.Platform;
import dev.dryaides.minuit.port.mc.Bridges;
import dev.dryaides.minuit.port.mc.BubbleLayer;
import dev.dryaides.minuit.port.mc.HudFeed;
import dev.dryaides.minuit.port.mc.WirePayload;
import dev.dryaides.minuit.wire.Channels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Hands core the game, reads the player's theme choice before any screen asks for a theme and the theme
 * files on each reload, swaps container screens as they open, puts the theme tab on the game's container
 * screens, draws the speech bubbles under the chat,
 * and carries the client end of the wire. Client only: {@link MinuitForge} calls it on the client alone.
 *
 * <p>The hello goes out on the first client tick with a player after a connect, never from the connect
 * event itself, which FML posts on the netty thread during the login handshake.
 */
final class MinuitForgeClient {

    private static volatile boolean helloDue;

    private MinuitForgeClient() {
    }

    static void preInit() {
        Bridges.install();
        Platform.development(Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment")));
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new ThemeReload());
        ClientForms.connect(MinuitForgeClient::send, FormScreen.HOST);
        MinecraftForge.EVENT_BUS.register(new Events());
    }

    static void init() {
        MinuitClient.themeChoices();
    }

    /** A message from the server, on the netty thread. */
    static void received(String channel, byte[] bytes) {
        Minecraft mc = Minecraft.getMinecraft();
        if (WirePayload.SCREEN.equals(channel)) {
            mc.addScheduledTask(() -> ClientForms.screen(bytes));
        } else if (WirePayload.HELLO.equals(channel)) {
            mc.addScheduledTask(MinuitForgeClient::sendHello);
        }
    }

    private static void sendHello() {
        send(Channels.HELLO, Handshakes.clientHello());
    }

    private static void send(String channel, byte[] bytes) {
        if (Minecraft.getMinecraft().getConnection() == null) {
            return;
        }
        String name = WirePayload.name(channel);
        MinuitForge.channel(name).sendToServer(WirePayload.packet(name, bytes));
    }

    public static final class Events {

        @SubscribeEvent
        public void onOpen(GuiOpenEvent event) {
            MinuitMenuScreens.onOpen(event);
        }

        @SubscribeEvent
        public void onBackground(GuiScreenEvent.BackgroundDrawnEvent event) {
            ContainerTabs.background(event);
        }

        @SubscribeEvent
        public void onDrawn(GuiScreenEvent.DrawScreenEvent.Post event) {
            ContainerTabs.drawn(event);
        }

        @SubscribeEvent
        public void onInit(GuiScreenEvent.InitGuiEvent.Post event) {
            ContainerTabs.initialised(event);
        }

        @SubscribeEvent
        public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
            ContainerTabs.mouse(event);
        }

        /** Under the chat: drawn as the chat's turn begins. */
        @SubscribeEvent
        public void onOverlay(RenderGameOverlayEvent.Pre event) {
            if (event.getType() == RenderGameOverlayEvent.ElementType.CHAT) {
                BubbleLayer.render(event.getPartialTicks());
                HudFeed.render(event.getPartialTicks());
            }
        }

        @SubscribeEvent
        public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
            helloDue = true;
        }

        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            helloDue = false;
            Minecraft.getMinecraft().addScheduledTask(ClientForms::left);
        }

        @SubscribeEvent
        public void onTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END && helloDue && Minecraft.getMinecraft().player != null) {
                helloDue = false;
                sendHello();
            }
        }
    }
}
