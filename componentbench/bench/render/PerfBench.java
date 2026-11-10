package bench.render;

import bench.api.Demo;
import bench.api.Scene;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.TextMeasure;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.Words;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draw cost per family with no renderer behind the calls: every demo state drawn N times into a Draw that only
 * counts. In game each fill is one quad, so the fill count stands for the frame's draw cost.
 * {@code --hash <file>} also writes a digest of each state's bench picture, to prove a change draws the same pixels.
 */
public final class PerfBench {
    static final class CountingDraw extends Draw {
        final BenchFont font;
        long rects;
        long strings;
        long blits;
        long pushes;
        long scissors;

        CountingDraw(BenchFont font) {
            this.font = font;
        }

        @Override protected void rect(int x0, int y0, int x1, int y1, int argb) { rects++; }
        @Override protected void string(String text, int x, int y, int argb) { strings++; }
        @Override public int width(String text) { return font.width(text); }
        @Override protected String cut(String text, int maxWidth) { return font.cut(text, maxWidth); }
        @Override public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) { blits++; }
        @Override public void push(float x, float y, float scale) { pushes++; }
        @Override public void pop() { }
        @Override public void scissor(int x0, int y0, int x1, int y1) { scissors++; }
        @Override public void endScissor() { }
        @Override public void tooltip(List<String> lines, int x, int y) { strings += lines.size(); }

        long calls() {
            return rects + strings + blits + pushes + scissors;
        }
    }

    private static final class Row {
        long rects;
        long strings;
        long blits;
        long ns;
        long bytes;
        int states;
    }

    public static void main(String[] args) throws Exception {
        int frames = 200;
        String hashOut = null;
        String demoFilter = null;
        String themeArg = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--frames" -> frames = Integer.parseInt(args[++i]);
                case "--hash" -> hashOut = args[++i];
                case "--demo" -> demoFilter = args[++i];
                case "--themes" -> themeArg = args[++i];
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        Path assets = Path.of(System.getProperty("bench.assets"));
        String mcJar = System.getProperty("bench.mcjar", "");
        BenchFont font = new BenchFont(mcJar);
        Textures textures = new Textures(assets, mcJar);
        TextMeasure.font = font::width;
        Main.loadThemes(assets);
        Words words = Main.loadWords(assets);
        List<Main.Spec> specs = Main.specs(themeArg);
        List<Demo> demos = Main.discover(Path.of(System.getProperty("bench.classes")));

        Scene.Sim sim = new Scene.Sim();
        BaseComponent.clock = sim;
        BaseComponent.keys = sim;
        BaseComponent.reducedMotion = false;
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();

        Map<String, Row> perSpec = new LinkedHashMap<>();
        Map<String, Row> perDemo = new LinkedHashMap<>();
        PrintWriter hashes = hashOut == null ? null : new PrintWriter(Files.newBufferedWriter(Path.of(hashOut), StandardCharsets.UTF_8));
        for (Demo demo : demos) {
            if (demoFilter != null && !demo.name().toLowerCase().contains(demoFilter.toLowerCase())) {
                continue;
            }
            List<Main.StateDef> states = new ArrayList<>();
            demo.states((n, w, h, script) -> states.add(new Main.StateDef(n, w, h, script)));
            for (Main.StateDef s : states) {
                for (Main.Spec spec : specs) {
                    if (hashes != null) {
                        BufferedImage img = Main.render(s, spec, 2, sim, words, font, textures);
                        hashes.println(demo.name() + "/" + s.name() + "/" + spec.label() + " " + digest(img));
                    }
                    sim.now = 1_000_000L;
                    sim.ctrl = false;
                    sim.shift = false;
                    Theme theme = Themes.resolve(Themes.defaultTheme(), new ThemeChoice(spec.family(), spec.chroma(), spec.variant()));
                    Scene scene = new Scene(theme, words, s.w(), s.h(), sim);
                    s.script().accept(scene);
                    scene.waitMs(scene.settleMs());
                    CountingDraw probe = new CountingDraw(font);
                    scene.panel().draw(probe, scene.mouseX(), scene.mouseY(), 1F);
                    scene.panel().drawTooltip(probe, scene.mouseX(), scene.mouseY());
                    for (int i = 0; i < frames / 4; i++) {
                        scene.panel().draw(new CountingDraw(font), scene.mouseX(), scene.mouseY(), 1F);
                    }
                    CountingDraw d = new CountingDraw(font);
                    long b0 = mx.getThreadAllocatedBytes(tid);
                    long t0 = System.nanoTime();
                    for (int i = 0; i < frames; i++) {
                        scene.panel().draw(d, scene.mouseX(), scene.mouseY(), 1F);
                    }
                    long ns = (System.nanoTime() - t0) / frames;
                    long bytes = (mx.getThreadAllocatedBytes(tid) - b0) / frames;
                    for (Row r : new Row[] {
                            perSpec.computeIfAbsent(spec.label(), k -> new Row()),
                            perDemo.computeIfAbsent(spec.family() + "|" + demo.name(), k -> new Row())}) {
                        r.rects += probe.rects;
                        r.strings += probe.strings;
                        r.blits += probe.blits;
                        r.ns += ns;
                        r.bytes += bytes;
                        r.states++;
                    }
                }
            }
        }
        if (hashes != null) {
            hashes.close();
        }
        System.out.println("frames per state: " + frames);
        System.out.println("| theme | states | fills/state | strings/state | blits/state | ns/state | bytes/state |");
        System.out.println("|---|---|---|---|---|---|---|");
        for (Map.Entry<String, Row> e : perSpec.entrySet()) {
            Row r = e.getValue();
            System.out.printf("| %s | %d | %.1f | %.1f | %.1f | %d | %d |%n", e.getKey(), r.states, (double) r.rects / r.states,
                    (double) r.strings / r.states, (double) r.blits / r.states, r.ns / r.states, r.bytes / r.states);
        }
        System.out.println();
        System.out.println("| family | demo | fills/state | ns/state | bytes/state |");
        System.out.println("|---|---|---|---|---|");
        for (Map.Entry<String, Row> e : perDemo.entrySet()) {
            Row r = e.getValue();
            String[] k = e.getKey().split("\\|");
            System.out.printf("| %s | %s | %.1f | %d | %d |%n", k[0], k[1], (double) r.rects / r.states, r.ns / r.states, r.bytes / r.states);
        }
    }

    static String digest(BufferedImage img) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        byte[] buf = new byte[px.length * 4 + 8];
        int o = 0;
        for (int v : new int[] {img.getWidth(), img.getHeight()}) {
            buf[o++] = (byte) (v >>> 24); buf[o++] = (byte) (v >>> 16); buf[o++] = (byte) (v >>> 8); buf[o++] = (byte) v;
        }
        for (int v : px) {
            buf[o++] = (byte) (v >>> 24); buf[o++] = (byte) (v >>> 16); buf[o++] = (byte) (v >>> 8); buf[o++] = (byte) v;
        }
        byte[] h = md.digest(buf);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(String.format("%02x", h[i]));
        }
        return sb.toString();
    }
}
