package bench.render;

import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.FrameStyle;
import dev.dryaides.minuit.FrameStyles;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.Shapes;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeFamily;
import dev.dryaides.minuit.internal.ImageFamily;
import dev.dryaides.minuit.internal.ThemeFiles;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Exports a code family's parts to nine-slice PNGs and a family.json, loads them back through ThemeFiles as a family
 * drawn from images, and counts the pixels where the two draw differently.
 * Args: family chroma light|dark outDir.
 */
public final class ImageFamilyProof {
    private static final String NS = "imgproof";
    private static final int M = 6;
    /** The width of the repeating middle written to a sprite. */
    private static final int MIDDLE = 16;

    /** Draws one part of a style into a box. */
    private interface Painter {
        void paint(FrameStyle s, Draw d, Palette p, int x, int y, int w, int h);
    }

    /** A sprite to export: drawn at {@code w} by {@code h}, or resizable when {@code fixed} is false. */
    private record Part(String name, Painter painter, boolean fixed, int w, int h) {
    }

    public static void main(String[] args) throws Exception {
        String family = args[0];
        String chroma = args[1];
        ThemeChoice.Variant variant = "dark".equals(args[2]) ? ThemeChoice.Variant.DARK : ThemeChoice.Variant.LIGHT;
        Path out = Path.of(args[3]);
        FrameStyle code = FrameStyles.byName(family);
        Palette p = ThemeFiles.builtIn(chroma, variant);
        Path assets = Path.of(System.getProperty("bench.assets"));
        String mcJar = System.getProperty("bench.mcjar", "");
        BenchFont font = new BenchFont(mcJar);
        Textures textures = new Textures(out.resolve("pack/assets"), mcJar);
        ImageFamily.install(new BenchImages(textures));

        List<Part> parts = parts(p);
        String imgFamily = family + "_img";
        Path dir = out.resolve("pack/assets/" + NS + "/minuit/themes/" + imgFamily);
        Files.createDirectories(dir.resolve("sprites"));
        StringBuilder json = new StringBuilder();
        json.append("{\"style\": \"").append(family).append("\", \"order\": 900, \"chromas\": [\"").append(chroma)
                .append("\"],\n \"sprites\": {\"folder\": \"sprites\", \"parts\": {");
        boolean first = true;
        for (Part part : parts) {
            String entry = export(code, p, part, dir.resolve("sprites/" + part.name + ".png"));
            json.append(first ? "\n" : ",\n").append("   \"").append(part.name).append("\": ").append(entry);
            first = false;
        }
        json.append("}}}\n");
        Files.writeString(dir.resolve("family.json"), json, StandardCharsets.UTF_8);

        // load back the way a resource reload does: the family file and the chroma it names
        List<ThemeFiles.Entry> entries = new ArrayList<>(ThemeFiles.fromDirectory(dir.getParent().getParent(), NS));
        String chromaText = Files.readString(assets.resolve("minuit/minuit/chromas/" + chroma + ".json"), StandardCharsets.UTF_8);
        entries.add(new ThemeFiles.Entry("minuit:minuit/chromas/" + chroma + ".json", "minuit", "chromas/" + chroma + ".json", chromaText));
        List<ThemeFamily> loaded = ThemeFiles.assemble(entries, m -> System.err.println("theme file: " + m));
        FrameStyle image = loaded.stream().filter(f -> f.id().equals(imgFamily)).findFirst().orElseThrow().frame();
        if (!(image instanceof ImageFamily)) {
            throw new IllegalStateException("family.json did not load as a family from images");
        }

        // every part at sizes other than the exported one, then the button calls with their bleed
        List<Painter> cases = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int[][] sizes = {{40, 40}, {23, 17}, {61, 30}, {12, 9}, {97, 22}};
        for (Part part : parts) {
            int[][] these = part.fixed ? new int[][]{{part.w, part.h}} : sizes;
            for (int[] s : these) {
                cases.add((st, d, pp, x, y, w, h) -> part.painter.paint(st, d, pp, x, y, s[0], s[1]));
                labels.add(part.name + " " + s[0] + "x" + s[1]);
            }
        }
        for (int[] s : new int[][]{{50, 20}, {80, 16}, {31, 14}}) {
            cases.add((st, d, pp, x, y, w, h) -> st.button(d, pp, x, y, s[0], s[1], null, pp.raised(), Shapes.Relief.RAISED));
            labels.add("button() raised " + s[0] + "x" + s[1]);
            cases.add((st, d, pp, x, y, w, h) -> st.button(d, pp, x, y, s[0], s[1], null, pp.primary(), Shapes.Relief.PRESSED));
            labels.add("button() primary pressed " + s[0] + "x" + s[1]);
            cases.add((st, d, pp, x, y, w, h) -> st.button(d, pp, x, y, s[0], s[1], null,
                    Shapes.mix(pp.raised(), pp.raisedHover(), 0.5F), Shapes.Relief.RAISED));
            labels.add("button() mid-hover " + s[0] + "x" + s[1]);
        }
        int cell = 120;
        int cols = 8;
        int rows = (cases.size() + cols - 1) / cols;
        BufferedImage a = sheet(cols * cell, rows * 60, cases, code, p, font, textures, cell, cols);
        int before = textures.gets;
        BufferedImage b = sheet(cols * cell, rows * 60, cases, image, p, font, textures, cell, cols);
        int spriteBlits = textures.gets - before;
        BufferedImage diff = new BufferedImage(a.getWidth(), a.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Map<String, Integer> perCase = new LinkedHashMap<>();
        int total = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int ca = a.getRGB(x, y);
                int cb = b.getRGB(x, y);
                boolean same = ca == cb;
                diff.setRGB(x, y, same ? (0x40000000 | (ca & 0xFFFFFF)) : 0xFFFF0000);
                if (!same) {
                    total++;
                    int i = (y / 60) * cols + x / cell;
                    perCase.merge(labels.get(i), 1, Integer::sum);
                }
            }
        }
        write(out.resolve("code_" + family + "_" + chroma + "_" + args[2] + ".png"), a, 3);
        write(out.resolve("image_" + family + "_" + chroma + "_" + args[2] + ".png"), b, 3);
        write(out.resolve("diff_" + family + "_" + chroma + "_" + args[2] + ".png"), diff, 3);
        StringBuilder report = new StringBuilder();
        report.append(family).append(' ').append(chroma).append(' ').append(args[2]).append(": ").append(parts.size())
                .append(" sprites, ").append(cases.size()).append(" cases, ").append(spriteBlits).append(" sprite blits, ").append(total).append(" px differ\n");
        perCase.forEach((k, v) -> report.append("  ").append(k).append(": ").append(v).append(" px\n"));
        Files.writeString(out.resolve("diff_" + family + "_" + chroma + "_" + args[2] + ".txt"), report, StandardCharsets.UTF_8);
        System.out.print(report);
        tinted(family, dir, textures, font, out, assets);
        if (!textures.missing.isEmpty()) {
            System.out.println("missing textures: " + textures.missing);
        }
    }

    /**
     * The tint path for the eye: the exported idle button and panel turned grey, mapped through
     * {@code frame_dark, face, frame_light} and {@code frame_dark, surface, frame_light}, worn by three chromas.
     */
    private static void tinted(String family, Path exported, Textures textures, BenchFont font, Path out, Path assets)
            throws Exception {
        String id = family + "_grey";
        Path dir = exported.resolveSibling(id);
        Files.createDirectories(dir.resolve("sprites"));
        for (String[] pair : new String[][]{{"button.raised", "button"}, {"panel", "panel"}}) {
            BufferedImage src = ImageIO.read(exported.resolve("sprites/" + pair[0] + ".png").toFile());
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    int c = src.getRGB(x, y);
                    int g = Math.round(0.299F * (c >> 16 & 0xFF) + 0.587F * (c >> 8 & 0xFF) + 0.114F * (c & 0xFF));
                    src.setRGB(x, y, (c & 0xFF000000) | g << 16 | g << 8 | g);
                }
            }
            ImageIO.write(src, "png", dir.resolve("sprites/" + pair[1] + ".png").toFile());
        }
        String exportedJson = Files.readString(exported.resolve("family.json"), StandardCharsets.UTF_8);
        String buttonInset = entry(exportedJson, "button.raised");
        String panelInset = entry(exportedJson, "panel");
        Files.writeString(dir.resolve("family.json"), "{\"style\": \"" + family + "\", \"order\": 901,\n \"sprites\": {\"parts\": {\n"
                + "   \"button\": " + withTint(buttonInset, "[\"frame_dark\", \"face\", \"frame_light\"]") + ",\n"
                + "   \"panel\": " + withTint(panelInset, "[\"frame_dark\", \"surface\", \"frame_light\"]")
                + "}}}\n", StandardCharsets.UTF_8);
        List<ThemeFiles.Entry> entries = new ArrayList<>(ThemeFiles.fromDirectory(dir.getParent().getParent(), NS));
        String[] chromas = {"bedrock", "teal", "lilac"};
        for (String c : chromas) {
            entries.add(new ThemeFiles.Entry(c, "minuit", "chromas/" + c + ".json",
                    Files.readString(assets.resolve("minuit/minuit/chromas/" + c + ".json"), StandardCharsets.UTF_8)));
        }
        FrameStyle grey = ThemeFiles.assemble(entries, m -> System.err.println("theme file: " + m)).stream()
                .filter(f -> f.id().equals(id)).findFirst().orElseThrow().frame();
        BufferedImage img = new BufferedImage(3 * 200, 70, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x808080));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();
        BenchDraw d = new BenchDraw(img, 1, font, textures);
        for (int i = 0; i < chromas.length; i++) {
            Palette p = ThemeFiles.builtIn(chromas[i], ThemeChoice.Variant.LIGHT);
            int x = i * 200 + 6;
            grey.panel(d, p, x, 6, 188, 58);
            int[] faces = {p.raised(), p.primary(), p.destructive(), p.disabled()};
            for (int k = 0; k < faces.length; k++) {
                grey.button(d, p, x + 6 + k * 45, 20, 40, 16, null, faces[k], Shapes.Relief.RAISED);
            }
        }
        d.finish();
        write(out.resolve("tinted_" + family + ".png"), img, 3);
    }

    private static String withTint(String entry, String stops) {
        return entry.substring(0, entry.length() - 1) + ", \"tint\": {\"map\": " + stops + "}}";
    }

    /** The json object written for {@code name} in an exported family.json. */
    private static String entry(String json, String name) {
        int at = json.indexOf("\"" + name + "\": ") + name.length() + 4;
        int depth = 0;
        for (int i = at; i < json.length(); i++) {
            char c = json.charAt(i);
            depth += c == '{' ? 1 : c == '}' ? -1 : 0;
            if (depth == 0) {
                return json.substring(at, i + 1);
            }
        }
        throw new IllegalStateException(name + " not in " + json);
    }

    private static List<Part> parts(Palette p) {
        List<Part> out = new ArrayList<>();
        out.add(new Part("panel", FrameStyle::panel, false, 0, 0));
        out.add(new Part("header", FrameStyle::header, false, 0, 0));
        out.add(new Part("section", FrameStyle::section, false, 0, 0));
        out.add(new Part("well", (s, d, pp, x, y, w, h) -> s.well(d, pp, x, y, w, h, false), false, 0, 0));
        out.add(new Part("well_focused", (s, d, pp, x, y, w, h) -> s.well(d, pp, x, y, w, h, true), false, 0, 0));
        out.add(new Part("field", (s, d, pp, x, y, w, h) -> s.field(d, pp, x, y, w, h, false), false, 0, 0));
        out.add(new Part("field_focused", (s, d, pp, x, y, w, h) -> s.field(d, pp, x, y, w, h, true), false, 0, 0));
        out.add(new Part("popup", FrameStyle::popup, false, 0, 0));
        Map<String, Integer> faces = new LinkedHashMap<>();
        faces.put("raised", p.raised());
        faces.put("raised_hover", p.raisedHover());
        faces.put("primary", p.primary());
        faces.put("primary_hover", p.primaryHover());
        faces.put("destructive", p.destructive());
        faces.put("destructive_hover", p.destructiveHover());
        faces.put("disabled", p.disabled());
        faces.forEach((role, c) -> out.add(new Part("button." + role,
                (s, d, pp, x, y, w, h) -> s.raised(d, pp, x, y, w, h, c, Shapes.Relief.RAISED), false, 0, 0)));
        for (String role : new String[]{"raised", "raised_hover", "primary", "primary_hover"}) {
            int c = faces.get(role);
            out.add(new Part("button_pressed." + role,
                    (s, d, pp, x, y, w, h) -> s.raised(d, pp, x, y, w, h, c, Shapes.Relief.PRESSED), false, 0, 0));
        }
        Map<String, Integer> tracks = new LinkedHashMap<>();
        tracks.put("track", p.track());
        tracks.put("accent", p.accent());
        tracks.put("disabled", p.disabled());
        tracks.forEach((role, c) -> out.add(new Part("track." + role,
                (s, d, pp, x, y, w, h) -> s.track(d, pp, x, y, w, h, c), false, 0, 0)));
        out.add(new Part("knob", (s, d, pp, x, y, w, h) -> s.knob(d, pp, x, y, false), true, 7, 8));
        out.add(new Part("knob_lit", (s, d, pp, x, y, w, h) -> s.knob(d, pp, x, y, true), true, 7, 8));
        out.add(new Part("slot", (s, d, pp, x, y, w, h) -> s.slot(d, pp, x, y, false, false), true, 18, 18));
        return out;
    }

    private static String export(FrameStyle code, Palette p, Part part, Path png) throws Exception {
        int bw = part.fixed ? part.w : 40;
        int bh = part.fixed ? part.h : 40;
        Cut c = cut(part.name, (d, x, y) -> part.painter.paint(code, d, p, x, y, bw, bh), bw, bh, part.fixed);
        ImageIO.write(c.sprite, "png", png.toFile());
        return "{\"inset\": " + sides(c.top, c.left, c.right, c.bottom)
                + (c.ot + c.ol + c.or + c.ob > 0 ? ", \"outset\": " + sides(c.ot, c.ol, c.or, c.ob) : "") + "}";
    }

    /** Draws something into a box at (x, y) of a {@link BenchDraw} at GUI scale 1. */
    interface BoxPainter {
        void paint(Draw d, int x, int y);
    }

    /**
     * A part cut to a nine-slice sprite: the insets around the repeating middle, and the outset, what the part
     * draws outside its box (a shadow, a lip).
     */
    record Cut(BufferedImage sprite, int top, int left, int right, int bottom, int ot, int ol, int or, int ob) {
    }

    /**
     * Draws the part on a clear canvas, keeps the box and whatever it draws outside it (the outset), then cuts the
     * repeating middle down to one row and one column: the insets are what stays around it. A fixed part is kept whole.
     */
    static Cut cut(String name, BoxPainter painter, int bw, int bh, boolean fixed) {
        BufferedImage canvas = new BufferedImage(bw + 2 * M, bh + 2 * M, BufferedImage.TYPE_INT_ARGB);
        BenchDraw d = new BenchDraw(canvas, 1, null, null);
        painter.paint(d, M, M);
        d.finish();
        int x0 = M;
        int y0 = M;
        int x1 = M + bw;
        int y1 = M + bh;
        for (int y = 0; y < canvas.getHeight(); y++) {
            for (int x = 0; x < canvas.getWidth(); x++) {
                if (canvas.getRGB(x, y) >>> 24 != 0) {
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    x1 = Math.max(x1, x + 1);
                    y1 = Math.max(y1, y + 1);
                }
            }
        }
        if (x0 == 0 || y0 == 0 || x1 == canvas.getWidth() || y1 == canvas.getHeight()) {
            throw new IllegalStateException(name + " draws past the export margin");
        }
        BufferedImage region = canvas.getSubimage(x0, y0, x1 - x0, y1 - y0);
        int ot = M - y0;
        int ol = M - x0;
        int or = x1 - (M + bw);
        int ob = y1 - (M + bh);
        if (fixed) {
            return new Cut(region, 0, 0, 0, 0, ot, ol, or, ob);
        }
        int rw = region.getWidth();
        int rh = region.getHeight();
        int midRow = ot + bh / 2;
        int midCol = ol + bw / 2;
        int t = midRow;
        while (t > 0 && sameRow(region, t - 1, midRow)) {
            t--;
        }
        int b = midRow;
        while (b < rh - 1 && sameRow(region, b + 1, midRow)) {
            b++;
        }
        int l = midCol;
        while (l > 0 && sameCol(region, l - 1, midCol)) {
            l--;
        }
        int r = midCol;
        while (r < rw - 1 && sameCol(region, r + 1, midCol)) {
            r++;
        }
        int top = t;
        int bottom = rh - 1 - b;
        int left = l;
        int right = rw - 1 - r;
        int midW = Math.min(MIDDLE, r - l + 1);
        int midH = Math.min(MIDDLE, b - t + 1);
        BufferedImage sprite = new BufferedImage(left + midW + right, top + midH + bottom, BufferedImage.TYPE_INT_ARGB);
        int[] xs = cut(rw, left, midW, right);
        int[] ys = cut(rh, top, midH, bottom);
        for (int y = 0; y < ys.length; y++) {
            for (int x = 0; x < xs.length; x++) {
                sprite.setRGB(x, y, region.getRGB(xs[x], ys[y]));
            }
        }
        return new Cut(sprite, top, left, right, bottom, ot, ol, or, ob);
    }

    /**
     * The source columns kept: the first {@code a}, {@code mid} copies of the repeating one (all alike, so a wider
     * middle only means fewer pieces when the game tiles it), the last {@code b}.
     */
    private static int[] cut(int n, int a, int mid, int b) {
        int[] out = new int[a + mid + b];
        for (int i = 0; i < a; i++) {
            out[i] = i;
        }
        for (int i = 0; i < mid; i++) {
            out[a + i] = a;
        }
        for (int i = 0; i < b; i++) {
            out[a + mid + i] = n - b + i;
        }
        return out;
    }

    private static String sides(int top, int left, int right, int bottom) {
        return "{\"top\": " + top + ", \"left\": " + left + ", \"right\": " + right + ", \"bottom\": " + bottom + "}";
    }

    private static boolean sameRow(BufferedImage img, int a, int b) {
        for (int x = 0; x < img.getWidth(); x++) {
            if (img.getRGB(x, a) != img.getRGB(x, b)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameCol(BufferedImage img, int a, int b) {
        for (int y = 0; y < img.getHeight(); y++) {
            if (img.getRGB(a, y) != img.getRGB(b, y)) {
                return false;
            }
        }
        return true;
    }

    private static BufferedImage sheet(int w, int h, List<Painter> cases, FrameStyle style, Palette p, BenchFont font,
                                       Textures textures, int cell, int cols) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x808080));
        g.fillRect(0, 0, w, h);
        g.dispose();
        BenchDraw d = new BenchDraw(img, 1, font, textures);
        for (int i = 0; i < cases.size(); i++) {
            int x = (i % cols) * cell + 8;
            int y = (i / cols) * 60 + 8;
            d.scissor(x - 8, y - 8, x - 8 + cell, y - 8 + 60);
            cases.get(i).paint(style, d, p, x, y, 0, 0);
            d.endScissor();
        }
        d.finish();
        return img;
    }

    private static void write(Path file, BufferedImage img, int scale) throws Exception {
        BufferedImage big = new BufferedImage(img.getWidth() * scale, img.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        g.drawImage(img, 0, 0, big.getWidth(), big.getHeight(), null);
        g.dispose();
        ImageIO.write(big, "png", file.toFile());
    }
}
