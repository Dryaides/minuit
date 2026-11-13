package bench.ingame1201;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Opens the frame bench once, {@code startTicks} after the player is in a world with no screen open, when
 * config/minuit_bench.properties exists. Without that file the mod does nothing.
 * Keys (defaults): demos=Dialog,Accordion,Card,FormField,Table (a demo name, or Demo/part of a state name),
 * themes=bubbly,bedrock (a family, or family:chroma:light|dark), warm=120, frames=600, rounds=3,
 * scales=2,3 (empty keeps the current GUI scale), startTicks=60.
 */
public final class FrameBenchMod implements ClientModInitializer {
    private boolean opened;
    private int inWorld;

    @Override
    public void onInitializeClient() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("minuit_bench.properties");
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            FrameBench.LOG.warn("[bench] cannot read {}: {}", file, e.toString());
            return;
        }
        FrameBench.Config config = new FrameBench.Config(
                list(p.getProperty("demos", "Dialog,Accordion,Card,FormField,Table")),
                list(p.getProperty("themes", "bubbly,bedrock")),
                Integer.parseInt(p.getProperty("warm", "120").trim()),
                Integer.parseInt(p.getProperty("frames", "600").trim()),
                Integer.parseInt(p.getProperty("rounds", "3").trim()),
                FrameBench.ints(p.getProperty("scales", "2,3")));
        int startTicks = Integer.parseInt(p.getProperty("startTicks", "60").trim());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (opened || mc.level == null || mc.player == null || mc.screen != null || mc.getOverlay() != null) {
                return;
            }
            if (++inWorld >= startTicks) {
                opened = true;
                uncapFrames(mc);
                mc.setScreen(new FrameBench(config));
            }
        });
    }

    /** Vsync and the frame cap would clamp every case to the same frame time. */
    private static void uncapFrames(Minecraft mc) {
        mc.options.enableVsync().set(false);
        mc.getWindow().updateVsync(false);
        mc.options.framerateLimit().set(260);
        mc.getWindow().setFramerateLimit(260);
        FrameBench.LOG.info("[bench] vsync off, frame cap off, window {}x{}", mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    private static List<String> list(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
