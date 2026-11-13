package bench.ingame1201;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.BenchCountingDraw;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeFamily;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.Words;
import dev.dryaides.minuit.client.MinuitScreen;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frame time of one fixed demo state per case, theme after theme, round after round. Each case shows the state alone,
 * drops {@code warm} frames, then times {@code frames} frames: the interval between two renders of this screen (the
 * whole frame, world behind included), the panel's own draw call, and the Draw calls it made, per frame.
 */
final class FrameBench extends MinuitScreen {
    static final Logger LOG = LoggerFactory.getLogger("minuit-bench");
    static final String MOD = "minuit_components_bench";

    record Config(List<String> demos, List<String> themes, int warm, int frames, int rounds, int[] scales) { }

    private record Target(String title, int w, int h, Consumer<Scene> script) { }

    private record Look(String label, Theme theme) { }

    private record Case(int scale, int round, Target target, Look look) { }

    private record Result(double frameMs, double panelMs, double fills) { }

    private final List<Case> cases = new ArrayList<>();
    private final Map<String, List<Result>> results = new LinkedHashMap<>();
    private final BenchCountingDraw counter = new BenchCountingDraw();
    private int index;
    private int frame;
    private long lastFrameStart;
    private long frameNanos;
    private long panelNanos;
    private boolean caseDone;
    private boolean started;
    private boolean finished;

    FrameBench(Config config) {
        super("minuit frame bench");
        List<Target> targets = targets(config.demos());
        List<Look> looks = looks(config.themes());
        int[] scales = config.scales().length == 0 ? new int[] {0} : config.scales();
        for (int scale : scales) {
            for (int round = 1; round <= config.rounds(); round++) {
                for (Target t : targets) {
                    for (Look look : looks) {
                        cases.add(new Case(scale, round, t, look));
                    }
                }
            }
        }
        this.config = config;
        LOG.info("[bench] {} cases: {} states x {} themes x {} rounds x {} scales, {} warm + {} timed frames each",
                cases.size(), targets.size(), looks.size(), config.rounds(), scales.length, config.warm(), config.frames());
    }

    private final Config config;

    private static List<Target> targets(List<String> wanted) {
        List<Demo> demos = new ArrayList<>();
        try (InputStream in = FrameBench.class.getResourceAsStream("/bench-demos.txt");
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String name : r.lines().toList()) {
                try {
                    if (Class.forName("bench.demos." + name).getDeclaredConstructor().newInstance() instanceof Demo demo) {
                        demos.add(demo);
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    // Helpers such as Mc and Shots are no demos; a demo that cannot load is only missing from the run.
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("cannot list the demos", e);
        }
        List<Target> out = new ArrayList<>();
        for (String want : wanted) {
            String demoName = want.contains("/") ? want.substring(0, want.indexOf('/')) : want;
            String stateName = want.contains("/") ? want.substring(want.indexOf('/') + 1).toLowerCase(Locale.ROOT) : null;
            Target found = null;
            for (Demo demo : demos) {
                if (!demo.name().equalsIgnoreCase(demoName)) {
                    continue;
                }
                List<Target> states = new ArrayList<>();
                demo.states((state, w, h, script) -> states.add(new Target(demo.name() + "/" + state, w, h, script)));
                for (Target t : states) {
                    if (stateName == null || t.title().toLowerCase(Locale.ROOT).contains(stateName)) {
                        found = t;
                        break;
                    }
                }
            }
            if (found == null) {
                LOG.warn("[bench] no demo state matches {}", want);
            } else {
                out.add(found);
            }
        }
        return out;
    }

    private static List<Look> looks(List<String> wanted) {
        List<Look> out = new ArrayList<>();
        for (String want : wanted) {
            String[] p = want.split(":");
            ThemeChoice choice;
            if (p.length == 3) {
                choice = new ThemeChoice(p[0], p[1], ThemeChoice.Variant.parse(p[2]));
            } else {
                ThemeFamily family = Themes.family(MOD, p[0]);
                if (family == null) {
                    LOG.warn("[bench] no theme family {}", p[0]);
                    continue;
                }
                choice = new ThemeChoice(p[0], family.first().id(), ThemeChoice.Variant.LIGHT);
            }
            String label = choice.family() + ":" + choice.chroma() + ":" + choice.variant().name().toLowerCase(Locale.ROOT);
            out.add(new Look(label, Themes.resolve(Themes.defaultTheme(), choice)));
        }
        return out;
    }

    @Override
    protected boolean showsThemeTab() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected PanelComponent build(int width, int height) {
        Theme theme = cases.isEmpty() ? Themes.defaultTheme() : cases.get(Math.min(index, cases.size() - 1)).look().theme();
        return new Host(theme, words(), width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        long start = System.nanoTime();
        if (started && !caseDone && !finished && lastFrameStart != 0 && frame > config.warm()) {
            frameNanos += start - lastFrameStart;
        }
        lastFrameStart = start;
        super.render(graphics, mouseX, mouseY, partial);
        if (started && !caseDone && !finished && ++frame >= config.warm() + config.frames()) {
            caseDone = true;
            report();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (finished) {
            return;
        }
        if (cases.isEmpty()) {
            finish();
            return;
        }
        if (!started) {
            started = true;
            start(0);
        } else if (caseDone) {
            if (index + 1 >= cases.size()) {
                finish();
                return;
            }
            start(index + 1);
        }
    }

    private void start(int next) {
        Minecraft mc = Minecraft.getInstance();
        int before = index < cases.size() ? cases.get(index).scale() : 0;
        index = next;
        frame = 0;
        frameNanos = 0;
        panelNanos = 0;
        counter.reset();
        caseDone = false;
        int scale = cases.get(index).scale();
        if (scale > 0 && (next == 0 || scale != before || mc.options.guiScale().get() != scale)) {
            mc.options.guiScale().set(scale);
            mc.resizeDisplay();
        }
        rebuildWidgets();
        lastFrameStart = 0;
    }

    private void report() {
        Case c = cases.get(index);
        Minecraft mc = Minecraft.getInstance();
        int n = config.frames();
        double frameMs = frameNanos / 1e6 / Math.max(1, n - 1);
        double panelMs = panelNanos / 1e6 / n;
        double fills = counter.rects / (double) n;
        LOG.info(String.format(Locale.ROOT,
                "[bench] case %d/%d scale=%d round=%d %s %s: %.3f ms/frame (%.0f fps), panel %.3f ms; per frame fills %.0f text %.0f styled %.0f blits %.0f images %.0f guiParts %.0f items %.0f scissors %.0f pushes %.0f",
                index + 1, cases.size(), mc.getWindow().getGuiScale() > 0 ? (int) mc.getWindow().getGuiScale() : c.scale(), c.round(),
                c.target().title(), c.look().label(), frameMs, 1000 / frameMs, panelMs, fills,
                counter.strings / (double) n, counter.styled / (double) n, counter.blits / (double) n, counter.images / (double) n,
                counter.guiParts / (double) n, counter.items / (double) n, counter.scissors / (double) n, counter.pushes / (double) n));
        results.computeIfAbsent(key(c.scale(), c.target().title(), c.look().label()), k -> new ArrayList<>())
                .add(new Result(frameMs, panelMs, fills));
    }

    private static String key(int scale, String title, String look) {
        return scale + "\t" + title + "\t" + look;
    }

    private void finish() {
        finished = true;
        Map<String, Map<String, Result>> byState = new LinkedHashMap<>();
        for (Map.Entry<String, List<Result>> e : results.entrySet()) {
            String[] k = e.getKey().split("\t");
            List<Result> rs = e.getValue();
            double[] f = rs.stream().mapToDouble(Result::frameMs).sorted().toArray();
            double[] p = rs.stream().mapToDouble(Result::panelMs).sorted().toArray();
            Result median = new Result(f[f.length / 2], p[p.length / 2], rs.get(0).fills());
            byState.computeIfAbsent(k[0] + "\t" + k[1], x -> new LinkedHashMap<>()).put(k[2], median);
        }
        for (Map.Entry<String, Map<String, Result>> e : byState.entrySet()) {
            String[] k = e.getKey().split("\t");
            StringBuilder line = new StringBuilder(String.format(Locale.ROOT, "[bench] MEDIAN scale=%s %s:", k[0].equals("0") ? "current" : k[0], k[1]));
            Result first = null;
            for (Map.Entry<String, Result> t : e.getValue().entrySet()) {
                Result r = t.getValue();
                line.append(String.format(Locale.ROOT, " | %s %.3f ms/frame, panel %.3f ms, fills %.0f", t.getKey(), r.frameMs(), r.panelMs(), r.fills()));
                if (first == null) {
                    first = r;
                } else {
                    line.append(String.format(Locale.ROOT, " (frame x%.2f, panel x%.2f of the first)", r.frameMs() / first.frameMs(), r.panelMs() / first.panelMs()));
                }
            }
            LOG.info(line.toString());
        }
        LOG.info("[bench] DONE {} cases", cases.size());
        Minecraft.getInstance().setScreen(null);
    }

    /** The case's state alone, centred, with one line naming it. */
    private final class Host extends PanelComponent {
        private final int screenW;
        private final int screenH;

        Host(Theme theme, Words words, int screenW, int screenH) {
            super(theme, words, 0, 0);
            this.screenW = screenW;
            this.screenH = screenH;
            if (cases.isEmpty() || finished) {
                return;
            }
            Target t = cases.get(index).target();
            // As the 26.3 shots run: the script plays on simulated time, then the real clock takes over.
            Scene.Sim sim = new Scene.Sim();
            java.util.function.LongSupplier realClock = BaseComponent.clock();
            HostState.clock(sim);
            Scene scene = new Scene(theme, words, t.w(), t.h(), sim);
            try {
                t.script().accept(scene);
            } catch (RuntimeException | AssertionError failure) {
                LOG.warn("[bench] SCRIPT {}: {}", t.title(), failure.toString());
            } finally {
                HostState.clock(realClock);
            }
            add(new SceneView(new Box("scene", (screenW - t.w()) / 2, Math.max(24, (screenH - t.h()) / 2), t.w(), t.h()), scene));
        }

        @Override public int width() { return screenW; }
        @Override public int height() { return screenH; }

        @Override
        protected void drawBackground(Draw d, int mx, int my) {
            if (!cases.isEmpty() && !finished) {
                Case c = cases.get(index);
                d.text((index + 1) + "/" + cases.size() + "  " + c.target().title() + "  " + c.look().label(), 8, 8, 0xFFFFFFFF);
            }
        }
    }

    /** Draws the scene's panel at the scripted pointer, so the real mouse never hovers anything. */
    private final class SceneView extends BaseComponent {
        private final Scene scene;
        private boolean threw;

        SceneView(Box box, Scene scene) {
            super(box);
            this.scene = scene;
        }

        @Override
        protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
            boolean timed = started && !caseDone && frame >= config.warm();
            Draw target = timed ? counter.on(d) : d;
            long started = System.nanoTime();
            d.push(x, y, 1F);
            try {
                scene.panel().draw(target, scene.mouseX(), scene.mouseY(), 1F);
            } catch (RuntimeException e) {
                if (!threw) {
                    threw = true;
                    LOG.warn("[bench] THREW " + e, e);
                }
            }
            d.pop();
            if (timed) {
                panelNanos += System.nanoTime() - started;
            }
        }

        @Override
        public void tick() {
            scene.panel().tickAll();
        }
    }

    static int[] ints(String csv) {
        return csv == null || csv.isBlank() ? new int[0]
                : Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).mapToInt(Integer::parseInt).toArray();
    }
}
