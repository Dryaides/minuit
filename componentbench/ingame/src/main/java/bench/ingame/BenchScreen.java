package bench.ingame;

import bench.api.Demo;
import bench.api.Scene;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.Scene3DComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.VoxelModel;
import dev.dryaides.minuit.Words;
import dev.dryaides.minuit.client.MinuitScreen;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Walks through every state of every demo with two buttons. The theme tab changes the look. */
public final class BenchScreen extends MinuitScreen {
    private static final String MOD = "minuit_components_bench";
    private static final boolean SHOTS = Boolean.getBoolean("bench.shots");
    private static final int SHOT_TICKS = 20;
    private static final int COST_TICKS = 100;
    private static final String COST = "cost: ";
    private static Scene3DComponent costScene;
    /** With shots on: this many states of each demo, spread evenly, because a full pass is over a thousand pictures. 0 is all of them. */
    private static final int PER_DEMO = Integer.getInteger("bench.perDemo", 0);

    private record Entry(String title, int w, int h, Consumer<Scene> script) { }

    private final List<Entry> entries = load();
    private long frames;
    private long drawNanos;
    private long firstFrameNanos;
    private int index;
    private int sinceShot;

    public BenchScreen() {
        super("minuit bench");
    }

    private static List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        try (InputStream in = BenchScreen.class.getResourceAsStream("/bench-demos.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String name : r.lines().toList()) {
                Object o = Class.forName("bench.demos." + name).getDeclaredConstructor().newInstance();
                if (o instanceof Demo demo) {
                    List<Entry> states = new ArrayList<>();
                    demo.states((state, w, h, script) -> states.add(new Entry(demo.name() + ": " + state, w, h, script)));
                    if (SHOTS && PER_DEMO > 0 && states.size() > PER_DEMO && !demo.name().equals("Scene3D")) {
                        for (int i = 0; i < PER_DEMO; i++) {
                            out.add(states.get(i * states.size() / PER_DEMO));
                        }
                    } else {
                        out.addAll(states);
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot list the demos", e);
        }
        if (SHOTS) {
            addCosts(out);
        }
        return out;
    }

    /** What 3D costs in the real game: the same turning box with more and more triangles, and nothing at all as the baseline. */
    private static void addCosts(List<Entry> out) {
        out.add(new Entry(COST + "empty", 160, 120, s -> { }));
        for (int size : new int[] {12, 24, 40, 64}) {
            out.add(new Entry(COST + size + " voxels wide", 160, 120, s -> costScene = s.add(new Scene3DComponent(new Box("cost", 0, 0, 160, 120))
                    .model(land(size)).autoRotate(true, 60F))));
        }
        out.add(new Entry(COST + "64 voxels wide, pixelSize 2", 160, 120, s -> costScene = s.add(new Scene3DComponent(new Box("cost", 0, 0, 160, 120))
                .model(land(64)).pixelSize(2).autoRotate(true, 60F))));
        out.add(new Entry(COST + "64 voxels wide, big box", 400, 300, s -> costScene = s.add(new Scene3DComponent(new Box("cost", 0, 0, 400, 300))
                .model(land(64)).autoRotate(true, 60F))));
    }

    /** A bumpy land where each column has its own colour, so that few faces merge. */
    private static VoxelModel land(int size) {
        VoxelModel m = new VoxelModel(size, 6, size);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int mix = x * 31 + z * 17;
                int height = 1 + Math.floorMod(x * 7 ^ z * 13, 5);
                int colour = 0xFF000000 | (40 + mix % 80) << 16 | (120 + mix % 100) << 8 | (40 + mix % 60);
                for (int y = 0; y < height; y++) {
                    m.set(x, y, z, colour);
                }
            }
        }
        return m;
    }

    @Override
    protected PanelComponent build(int width, int height) {
        return new Host(Themes.byId(MOD), words(), width, height);
    }

    private void go(int step) {
        frames = 0;
        drawNanos = 0;
        index = Math.floorMod(index + step, entries.size());
        rebuildWidgets();
    }

    @Override
    public void tick() {
        super.tick();
        boolean cost = !entries.isEmpty() && entries.get(index).title.startsWith(COST);
        if (SHOTS && ++sinceShot >= (cost ? COST_TICKS : SHOT_TICKS)) {
            sinceShot = 0;
            if (cost && frames > 1) {
                double seconds = (System.nanoTime() - firstFrameNanos) / 1e9;
                System.out.printf(java.util.Locale.ROOT, "[bench] %s: %.1f frames/s, %.2f ms per frame, draw call %.3f ms%n",
                        entries.get(index).title, frames / seconds, seconds * 1000 / frames, drawNanos / 1e6 / frames);
            }
            Minecraft mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, "bench-" + index + ".png", mc.gameRenderer.mainRenderTarget(), 1, message -> { });
            System.out.printf(java.util.Locale.ROOT, "[bench] %d fps=%d draw=%.2fms %s%n", index, mc.getFps(),
                    frames == 0 ? 0 : drawNanos / 1e6 / frames, entries.get(index).title);
            if (index + 1 >= entries.size()) {
                mc.stop();
            } else {
                go(1);
            }
        }
    }

    /** The buttons above, the state's own panel below, centred. */
    private final class Host extends PanelComponent {
        private final int screenW;
        private final int screenH;

        Host(Theme theme, Words words, int screenW, int screenH) {
            super(theme, words, 0, 0);
            this.screenW = screenW;
            this.screenH = screenH;
            add(new ButtonComponent(new Box("prev", 8, 8, 20, 18), "<", () -> go(-1)));
            add(new ButtonComponent(new Box("next", 32, 8, 20, 18), ">", () -> go(1)));
            if (entries.isEmpty()) {
                return;
            }
            Entry e = entries.get(index);
            // The scripts count on simulated time, as on the bench: they play on it, then the real clock takes over.
            Scene.Sim sim = SHOTS ? new Scene.Sim() : null;
            java.util.function.LongSupplier realClock = BaseComponent.clock;
            if (sim != null) {
                BaseComponent.clock = sim;
            }
            Scene scene = new Scene(theme, words, e.w, e.h, sim);
            try {
                e.script.accept(scene);
            } catch (RuntimeException | AssertionError failure) {
                // The checks were written for the bench. In the game a failed one is a difference to report, not the end of the run.
                if (!SHOTS) {
                    throw failure;
                }
                System.out.println("[bench] SCRIPT " + e.title + ": " + failure);
                StackTraceElement[] where = failure.getStackTrace();
                for (int i = 0; i < Math.min(where.length, 6); i++) {
                    System.out.println("[bench]     at " + where[i]);
                }
            }
            BaseComponent.clock = realClock;
            add(new SceneView(new Box("scene", (screenW - e.w) / 2, Math.max(32, (screenH - e.h) / 2), e.w, e.h), scene));
        }

        @Override public int width() { return screenW; }
        @Override public int height() { return screenH; }

        @Override
        protected void drawBackground(Draw d, int mx, int my) {
            String title = entries.isEmpty() ? "no demo in bench/demos"
                    : (index + 1) + "/" + entries.size() + "  " + entries.get(index).title;
            d.text(title, 58, 13, 0xFFFFFFFF);
        }
    }

    /** Hosts the scene's panel as one widget: its coordinates start at the widget's corner. */
    private final class SceneView extends BaseComponent {
        private final Scene scene;
        private boolean threw;

        SceneView(Box box, Scene scene) {
            super(box);
            this.scene = scene;
        }

        @Override protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
            if (frames++ == 0) {
                firstFrameNanos = System.nanoTime();
            }
            long started = System.nanoTime();
            d.push(x, y, 1F);
            try {
                scene.panel().draw(d, mx - x, my - y, 1F);
            } catch (RuntimeException e) {
                if (SHOTS && !threw) {
                    threw = true;
                    System.out.println("[bench] THREW " + e);
                    e.printStackTrace(System.out);
                }
                if (!SHOTS) {
                    throw e;
                }
            }
            d.pop();
            drawNanos += System.nanoTime() - started;
        }

        @Override public List<String> tooltipAt(double mx, double my) { return scene.panel().tooltipAt(mx - x, my - y); }
        @Override public void tick() { scene.panel().tickAll(); }
        @Override public boolean mouseClicked(double mx, double my, int b) { return scene.panel().mouseClicked(mx - x, my - y, b); }
        @Override public boolean mouseReleased(double mx, double my, int b) { return scene.panel().mouseReleased(mx - x, my - y, b); }
        @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) { return scene.panel().mouseDragged(mx - x, my - y, b, dx, dy); }
        @Override public boolean mouseScrolled(double mx, double my, double amount) { return scene.panel().mouseScrolled(mx - x, my - y, amount); }
        @Override public boolean keyPressed(InputKey key, boolean shift) { return scene.panel().keyPressed(key, shift); }
        @Override public boolean charTyped(int cp) { return scene.panel().charTyped(cp); }
        @Override protected boolean takesFocus() { return true; }
        @Override protected boolean capturesKeys() { return scene.panel().capturing(); }
        @Override public boolean keyCaptured(KeyPress press) { return scene.panel().rawKey(press); }
    }
}
