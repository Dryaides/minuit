package dev.dryaides.minuit.forge;

import dev.dryaides.minuit.internal.Bundling;
import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.internal.game.ServerForms;
import dev.dryaides.minuit.port.mc.WirePayload;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The 1.12.2 entry point: the three minuit channels as FML event channels, each player's hello and their
 * form input on the server side. Either side may lack minuit, so any remote mod list is accepted. The
 * client half is {@link MinuitForgeClient}, which this class calls on the client alone.
 */
@Mod(modid = "minuit", name = "minuit", acceptableRemoteVersions = "*", guiFactory = "dev.dryaides.minuit.forge.MinuitGuiFactory")
public final class MinuitForge {

    private static final Logger LOG = LogManager.getLogger("minuit");

    private static FMLEventChannel hello;
    private static FMLEventChannel screen;
    private static FMLEventChannel input;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        bundling(event);
        hello = NetworkRegistry.INSTANCE.newEventDrivenChannel(WirePayload.HELLO);
        screen = NetworkRegistry.INSTANCE.newEventDrivenChannel(WirePayload.SCREEN);
        input = NetworkRegistry.INSTANCE.newEventDrivenChannel(WirePayload.INPUT);
        Packets packets = new Packets();
        hello.register(packets);
        screen.register(packets);
        input.register(packets);
        ServerForms.sender((player, bytes) -> screen.sendTo(WirePayload.packet(WirePayload.SCREEN, bytes), player));
        MinecraftForge.EVENT_BUS.register(new Leaves());
        if (event.getSide().isClient()) {
            MinuitForgeClient.preInit();
        }
    }

    /** ContainedDeps: Forge extracts a mod's nested jars out of the mods folder, then loads them as mods. */
    private static void bundling(FMLPreInitializationEvent event) {
        File mods = new File(event.getModConfigurationDirectory().getParentFile(), "mods");
        if (!Bundling.extractedDep(event.getSourceFile().toPath(), mods.toPath(), Loader.MC_VERSION)) {
            return;
        }
        List<ModContainer> containers = new ArrayList<>(Loader.instance().getActiveModList());
        List<Path> jars = new ArrayList<>();
        for (ModContainer container : containers) {
            jars.add(container.getSource() == null || "minuit".equals(container.getModId()) ? null : container.getSource().toPath());
        }
        int parent = Bundling.containedDepsParent(jars);
        Bundling.found(parent < 0 ? null : containers.get(parent).getName() + " (" + containers.get(parent).getModId() + ")");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            MinuitForgeClient.init();
        }
    }

    /** The channel of a full name, one of {@link WirePayload}'s. */
    static FMLEventChannel channel(String name) {
        return WirePayload.HELLO.equals(name) ? hello : WirePayload.SCREEN.equals(name) ? screen : input;
    }

    /** A vanilla client or server, or one without minuit, connects all the same. */
    @NetworkCheckHandler
    public boolean accepts(Map<String, String> remoteMods, Side remote) {
        return true;
    }

    /** The messages of the three channels; FML hands them over on the netty thread. */
    public static final class Packets {

        @SubscribeEvent
        public void onServer(FMLNetworkEvent.ServerCustomPacketEvent event) {
            EntityPlayerMP player = ((NetHandlerPlayServer) event.getHandler()).player;
            String channel = event.getPacket().channel();
            byte[] bytes = WirePayload.read(event.getPacket());
            if (WirePayload.HELLO.equals(channel)) {
                player.getServerWorld().addScheduledTask(() -> Handshakes.received(player.getUniqueID(), bytes, LOG::warn));
            } else if (WirePayload.INPUT.equals(channel)) {
                player.getServerWorld().addScheduledTask(() -> ServerForms.input(player, bytes));
            }
        }

        @SubscribeEvent
        public void onClient(FMLNetworkEvent.ClientCustomPacketEvent event) {
            MinuitForgeClient.received(event.getPacket().channel(), WirePayload.read(event.getPacket()));
        }
    }

    public static final class Leaves {

        @SubscribeEvent
        public void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.player instanceof EntityPlayerMP) {
                ServerForms.left((EntityPlayerMP) event.player);
            }
            Handshakes.left(event.player.getUniqueID());
        }
    }
}
