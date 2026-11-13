package bench.render;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.TextMeasure;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeFamily;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.Words;
import dev.dryaides.minuit.internal.ThemeFiles;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/** Renders every demo under every theme, then writes the gallery. */
public final class Main {
    record Spec(String family, String chroma, ThemeChoice.Variant variant) {
        String label() { return family + "/" + chroma + "/" + variant.id(); }
        String file() { return family + "-" + chroma + "-" + variant.id(); }
    }

    record StateDef(String name, int w, int h, Consumer<Scene> script) { }

    private static final String[] DEFAULT_FAMILIES = {"soft", "bedrock", "slate", "bubbly", "vanilla"};
    /** The modes each default family is drawn in; glass is a mode of every chroma since 2026-11-11. */
    private static final List<ThemeChoice.Variant> DEFAULT_VARIANTS =
            List.of(ThemeChoice.Variant.LIGHT, ThemeChoice.Variant.DARK, ThemeChoice.Variant.GLASS);

    public static void main(String[] args) throws Exception {
        String demoFilter = null;
        java.util.Set<String> stateFilter = null;
        String themeArg = null;
        String onlyArg = null;
        boolean add = false;
        boolean list = false;
        int scale = 2;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--demo" -> demoFilter = args[++i];
                case "--state" -> stateFilter = new java.util.HashSet<>(List.of(args[++i].split(",")));
                case "--themes" -> themeArg = args[++i];
                case "--only" -> onlyArg = args[++i];
                case "--add" -> add = true;
                case "--list" -> list = true;
                case "--scale" -> scale = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }

        Path assets = Path.of(System.getProperty("bench.assets"));
        Path out = Path.of(System.getProperty("bench.out"));
        String mcJar = System.getProperty("bench.mcjar", "");
        BenchFont font = new BenchFont(mcJar);
        Textures textures = new Textures(assets, mcJar);
        dev.dryaides.minuit.internal.ImageFamily.install(new BenchImages(textures));
        TextMeasure.font = font::width;
        loadThemes(assets);
        Words words = loadWords(assets);
        System.out.println("font: " + (font.game ? "the game's bitmap font (" + mcJar + ")" : "stand-in (no game jar found)"));

        // --only draws just these looks while the stage heights still fit every look of --themes, so that
        // passes of one look each (build.py, to keep the heap small) give pictures of the same size;
        // --add keeps the pictures and page entries of the earlier passes.
        List<Spec> specs = specs(themeArg);
        List<Spec> drawn = onlyArg == null ? specs : specs(onlyArg);
        if (list) {
            specs.forEach(sp -> System.out.println(sp.family + ":" + sp.chroma + ":" + sp.variant.id()));
            return;
        }
        List<Demo> demos = discover(Path.of(System.getProperty("bench.classes")));
        if (demos.isEmpty()) {
            System.out.println("no demo found in bench/demos");
        }
        Files.createDirectories(out.resolve("img"));
        if (demoFilter == null && !add) {
            removeStale(out.resolve("img"), demos);
        }

        Scene.Sim sim = new Scene.Sim();
        HostState.clock(sim);
        HostState.keys(sim);
        HostState.reducedMotion(false);

        int rendered = 0;
        int failed = 0;
        for (Demo demo : demos) {
            String name = demo.name();
            if (demoFilter != null && !name.toLowerCase().contains(demoFilter.toLowerCase())) {
                continue;
            }
            List<StateDef> states = new ArrayList<>();
            demo.states((n, w, h, script) -> states.add(new StateDef(n, w, h, script)));
            for (int i = 0; i < states.size(); i++) {
                StateDef d = states.get(i);
                states.set(i, new StateDef(d.name, d.w, fitHeight(d, specs, sim, words), d.script));
            }
            Path dir = out.resolve("img").resolve(name);
            // --state redraws only the named states and keeps the demo's other pictures, as --add does.
            Gallery.DemoPage earlier = add || stateFilter != null ? Gallery.readPage(dir) : null;
            if (earlier == null) {
                deleteTree(dir);
            }
            Files.createDirectories(dir);
            Gallery.DemoPage page = earlier != null ? earlier : new Gallery.DemoPage(name, new ArrayList<>());
            for (StateDef s : states) {
                if (stateFilter != null && !stateFilter.contains(s.name)) {
                    continue;
                }
                Gallery.StateRow row = page.states().stream().filter(r -> r.name.equals(s.name)).findFirst().orElse(null);
                boolean fresh = row == null;
                if (fresh) {
                    row = new Gallery.StateRow(s.name, s.w * scale, s.h * scale, new ArrayList<>());
                }
                for (Spec spec : drawn) {
                    String file = safe(s.name) + "__" + spec.file() + ".png";
                    String error = null;
                    BufferedImage img;
                    try {
                        img = render(s, spec, scale, sim, words, font, textures);
                        rendered++;
                    } catch (Throwable t) {
                        failed++;
                        error = t.toString();
                        System.err.println(name + " / " + s.name + " / " + spec.label() + ":");
                        t.printStackTrace();
                        img = errorTile(s.w * scale, s.h * scale);
                    }
                    ImageIO.write(img, "png", dir.resolve(file).toFile());
                    row.cells().removeIf(c -> c.file.equals(file));
                    row.cells().add(new Gallery.Cell(spec.family, spec.chroma, spec.variant.id(), file, error));
                }
                if (fresh) {
                    page.states().add(row);
                }
            }
            Gallery.writePage(dir, page);
            if (!add) {
                Gallery.sheet(dir, page, out);
            }
        }
        Gallery.writeIndex(out, font.game, scale);
        System.out.println("rendered " + rendered + " pictures, " + failed + " failed");
        if (!textures.missing.isEmpty()) {
            System.out.println("textures not found (drawn in magenta and black): " + textures.missing);
        }
        System.out.println("gallery: " + out.resolve("index.html"));
        if (failed > 0) {
            System.exit(1);
        }
    }

    /**
     * The stage height that leaves the same margin under the last widget as above the first, over every
     * look. Measured by playing the script, then again at the new height until it holds (a popup may
     * open another way when the room changes). The declared height is the floor when a popup is open.
     */
    static int fitHeight(StateDef s, List<Spec> specs, Scene.Sim sim, Words words) {
        int h = s.h;
        for (int pass = 0; pass < 3; pass++) {
            int best = -1;
            for (Spec spec : specs) {
                sim.now = 1_000_000L;
                sim.ctrl = false;
                sim.shift = false;
                Theme theme = Themes.resolve(Themes.defaultTheme(), new ThemeChoice(spec.family, spec.chroma, spec.variant));
                Scene scene = new Scene(theme, words, s.w, h, sim);
                s.script.accept(scene);
                scene.waitMs(scene.settleMs());
                int fit = scene.fitHeight();
                if (fit < 0) {
                    return s.h;
                }
                best = Math.max(best, scene.panel().overlay() != null ? Math.max(fit, s.h) : fit);
            }
            if (best == h) {
                break;
            }
            h = best;
        }
        return h;
    }

    static BufferedImage render(StateDef s, Spec spec, int scale, Scene.Sim sim, Words words, BenchFont font, Textures textures) {
        sim.now = 1_000_000L;
        sim.ctrl = false;
        sim.shift = false;
        Theme theme = Themes.resolve(Themes.defaultTheme(), new ThemeChoice(spec.family, spec.chroma, spec.variant));
        Scene scene = new Scene(theme, words, s.w, s.h, sim);
        s.script.accept(scene);
        scene.waitMs(scene.settleMs());

        BufferedImage img = new BufferedImage(s.w * scale, s.h * scale, BufferedImage.TYPE_INT_ARGB);
        backdrop(img, spec.variant, theme.palette().backdrop());
        BenchDraw d = new BenchDraw(img, scale, font, textures);
        scene.panel().draw(d, scene.mouseX(), scene.mouseY(), 1F);
        scene.panel().drawTooltip(d, scene.mouseX(), scene.mouseY());
        d.finish();
        return img;
    }

    /**
     * The glass mode gets a world to show through it. Every other mode sits on flat neutral grey, so the open
     * corner steps of a panel show nothing coloured. A chroma with a backdrop role sits on that colour.
     */
    static void backdrop(BufferedImage img, ThemeChoice.Variant variant, int page) {
        Graphics2D g = img.createGraphics();
        int w = img.getWidth();
        int h = img.getHeight();
        if (variant != ThemeChoice.Variant.GLASS) {
            g.setColor(new Color(page != 0 ? page & 0xFFFFFF : 0x808080));
            g.fillRect(0, 0, w, h);
            g.dispose();
            return;
        }
        g.setPaint(new GradientPaint(0, 0, new Color(0x6C8FB0), 0, h, new Color(0x4A6B45)));
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x60C9A66B, true));
        g.fillOval(w / 8, h / 2, w / 2, h);
        g.setColor(new Color(0x509A6FB5, true));
        g.fillOval(w / 2, -h / 3, w / 2, h);
        g.dispose();
    }

    static BufferedImage errorTile(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0xB00020));
        g.fillRect(0, 0, w, h);
        g.setColor(Color.WHITE);
        g.drawString("error, see the console", 6, 16);
        g.dispose();
        return img;
    }

    static void loadThemes(Path assets) throws IOException {
        // the kit's own themes, then any other namespace's (a resource pack merged in through BENCH_PACKS)
        List<ThemeFiles.Entry> entries = new ArrayList<>(ThemeFiles.fromDirectory(assets.resolve("minuit/minuit"), "minuit"));
        try (var namespaces = Files.list(assets)) {
            for (Path ns : namespaces.sorted().toList()) {
                String name = ns.getFileName().toString();
                if (!name.equals("minuit") && Files.isDirectory(ns.resolve("minuit"))) {
                    entries.addAll(ThemeFiles.fromDirectory(ns.resolve("minuit"), name));
                }
            }
        }
        String result = ThemeFiles.reload(entries, m -> System.err.println("theme file: " + m));
        if (Themes.family("bench", "soft") == null) {
            throw new IllegalStateException("themes did not load (" + result + "), entries: " + entries.size());
        }
    }

    static Words loadWords(Path assets) throws IOException {
        Path lang = assets.resolve("minuit/lang/en_us.json");
        JsonObject table = JsonParser.parseString(Files.readString(lang, StandardCharsets.UTF_8)).getAsJsonObject();
        return (key, a) -> {
            if (!table.has(key)) {
                return key;
            }
            String text = table.get(key).getAsString();
            return a.length == 0 ? text : String.format(text, a);
        };
    }

    static List<Spec> specs(String arg) {
        List<Spec> out = new ArrayList<>();
        if (arg != null) {
            for (String part : arg.split(",")) {
                String[] p = part.split(":");
                if (p.length != 3) {
                    throw new IllegalArgumentException("a theme is family:chroma:light|dark|glass, got " + part);
                }
                out.add(new Spec(p[0], p[1], ThemeChoice.Variant.parse(p[2])));
            }
            return out;
        }
        for (String id : DEFAULT_FAMILIES) {
            ThemeFamily family = Themes.family("bench", id);
            if (family == null) {
                System.err.println("no family " + id);
                continue;
            }
            ThemeFamily.Chroma first = family.first();
            for (ThemeChoice.Variant v : DEFAULT_VARIANTS) {
                if (first.has(v)) {
                    out.add(new Spec(id, first.id(), v));
                }
            }
        }
        return out;
    }

    static List<Demo> discover(Path classes) throws Exception {
        Path dir = classes.resolve("bench/demos");
        List<Demo> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        String[] names = dir.toFile().list((d, n) -> n.endsWith(".class") && !n.contains("$"));
        Arrays.sort(names);
        for (String n : names) {
            Class<?> c = Class.forName("bench.demos." + n.substring(0, n.length() - 6));
            if (Demo.class.isAssignableFrom(c) && !c.isInterface() && !Modifier.isAbstract(c.getModifiers())) {
                out.add((Demo) c.getDeclaredConstructor().newInstance());
            }
        }
        return out;
    }

    static void removeStale(Path img, List<Demo> demos) throws IOException {
        List<String> live = demos.stream().map(Demo::name).toList();
        File[] dirs = img.toFile().listFiles(File::isDirectory);
        if (dirs != null) {
            for (File f : dirs) {
                if (!live.contains(f.getName())) {
                    deleteTree(f.toPath());
                }
            }
        }
        File[] sheets = img.resolveSibling("sheets").toFile().listFiles();
        if (sheets != null) {
            for (File f : sheets) {
                if (!live.contains(f.getName().replaceFirst("[.]png$", ""))) {
                    Files.delete(f.toPath());
                }
            }
        }
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]+", "_");
    }
}
