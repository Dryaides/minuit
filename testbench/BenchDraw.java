import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.SpriteIcon;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The kit without the game: a {@link Draw} that paints into an image with Minecraft's own bitmap
 * font, and remembers every string it was asked to draw. Copied from voicecore's
 * {@code testbench/UiMock.java}, with a scissor that cuts, since the scroll container depends on it.
 *
 * <p>The font is read from the Minecraft jar on the classpath, glyph widths included, so a string
 * measured here is as wide as the game will draw it. That is what lets the bench say a French
 * label fits its box instead of assuming six pixels a letter.
 */
final class BenchDraw {

    static final int PX = Integer.getInteger("bench.px", 3);

    private BenchDraw() {
    }

    // --- a JSON reader, because the bench has no library on its classpath ---------------------

    static final class Json {
        private final String text;
        private int at;

        private Json(String text) {
            this.text = text;
        }

        static Object parse(String text) {
            Json json = new Json(text);
            return json.value();
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> object(String text) {
            return (Map<String, Object>) parse(text);
        }

        private void space() {
            while (this.at < this.text.length() && Character.isWhitespace(this.text.charAt(this.at))) {
                this.at++;
            }
        }

        private Object value() {
            space();
            char c = this.text.charAt(this.at);
            if (c == '{') {
                Map<String, Object> map = new LinkedHashMap<>();
                this.at++;
                space();
                while (this.text.charAt(this.at) != '}') {
                    space();
                    String key = string();
                    space();
                    this.at++;
                    map.put(key, value());
                    space();
                    if (this.text.charAt(this.at) == ',') {
                        this.at++;
                        space();
                    }
                }
                this.at++;
                return map;
            }
            if (c == '[') {
                List<Object> list = new ArrayList<>();
                this.at++;
                space();
                while (this.text.charAt(this.at) != ']') {
                    list.add(value());
                    space();
                    if (this.text.charAt(this.at) == ',') {
                        this.at++;
                        space();
                    }
                }
                this.at++;
                return list;
            }
            if (c == '"') {
                return string();
            }
            int start = this.at;
            while (this.at < this.text.length() && ",]} \r\n\t".indexOf(this.text.charAt(this.at)) < 0) {
                this.at++;
            }
            String word = this.text.substring(start, this.at);
            return switch (word) {
                case "true" -> Boolean.TRUE;
                case "false" -> Boolean.FALSE;
                case "null" -> "";
                default -> Double.valueOf(word);
            };
        }

        private String string() {
            StringBuilder out = new StringBuilder();
            this.at++;
            while (this.text.charAt(this.at) != '"') {
                char c = this.text.charAt(this.at++);
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char escaped = this.text.charAt(this.at++);
                switch (escaped) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(this.text.substring(this.at, this.at + 4), 16));
                        this.at += 4;
                    }
                    default -> out.append(escaped);
                }
            }
            this.at++;
            return out.toString();
        }
    }

    static String resource(String path) {
        try (InputStream in = BenchDraw.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("not on the classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static BufferedImage image(String path) {
        try (InputStream in = BenchDraw.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("not on the classpath: " + path);
            }
            return argb(ImageIO.read(in));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static BufferedImage file(Path path) {
        try {
            return argb(ImageIO.read(path.toFile()));
        } catch (IOException e) {
            throw new IllegalStateException(path.toString(), e);
        }
    }

    private static BufferedImage argb(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return copy;
    }

    // --- Minecraft's default font -------------------------------------------------------------

    /** One glyph: its pixels, how far the pen moves after it, and where its top sits. */
    record Glyph(boolean[][] pixels, int advance, float scale, int top) {
    }

    static final class McFont {
        private final Map<Integer, Glyph> glyphs = new HashMap<>();
        final TreeSet<String> missing = new TreeSet<>();

        McFont() {
            // Same order as font/default.json: the space provider first, so its advance wins.
            Map<String, Object> space = Json.object(resource("/assets/minecraft/font/include/space.json"));
            for (Object entry : (List<?>) space.get("providers")) {
                Map<?, ?> provider = (Map<?, ?>) entry;
                Map<?, ?> advances = (Map<?, ?>) provider.get("advances");
                for (Map.Entry<?, ?> advance : advances.entrySet()) {
                    int code = ((String) advance.getKey()).codePointAt(0);
                    this.glyphs.put(code,
                            new Glyph(new boolean[0][0], ((Double) advance.getValue()).intValue(), 1F, 0));
                }
            }
            Map<String, Object> include = Json.object(resource("/assets/minecraft/font/include/default.json"));
            for (Object entry : (List<?>) include.get("providers")) {
                Map<?, ?> provider = (Map<?, ?>) entry;
                if ("bitmap".equals(provider.get("type"))) {
                    bitmap(provider);
                }
            }
        }

        /** The same arithmetic as the game's bitmap provider: trailing blank columns are cut. */
        private void bitmap(Map<?, ?> provider) {
            String file = ((String) provider.get("file")).replace("minecraft:", "");
            BufferedImage sheet = image("/assets/minecraft/textures/" + file);
            List<?> rows = (List<?>) provider.get("chars");
            int height = provider.containsKey("height") ? ((Double) provider.get("height")).intValue() : 8;
            int ascent = ((Double) provider.get("ascent")).intValue();
            int cellHeight = sheet.getHeight() / rows.size();
            int columns = ((String) rows.get(0)).codePointCount(0, ((String) rows.get(0)).length());
            int cellWidth = sheet.getWidth() / columns;
            float scale = (float) height / cellHeight;
            for (int row = 0; row < rows.size(); row++) {
                int[] codes = ((String) rows.get(row)).codePoints().toArray();
                for (int column = 0; column < codes.length; column++) {
                    if (codes[column] == 0 || this.glyphs.containsKey(codes[column])) {
                        continue;
                    }
                    boolean[][] pixels = new boolean[cellHeight][cellWidth];
                    int width = 0;
                    for (int y = 0; y < cellHeight; y++) {
                        for (int x = 0; x < cellWidth; x++) {
                            int argb = sheet.getRGB(column * cellWidth + x, row * cellHeight + y);
                            pixels[y][x] = (argb >>> 24) != 0;
                            if (pixels[y][x]) {
                                width = Math.max(width, x + 1);
                            }
                        }
                    }
                    int advance = (int) (0.5 + width * scale) + 1;
                    this.glyphs.put(codes[column], new Glyph(pixels, advance, scale, 7 - ascent));
                }
            }
        }

        Glyph glyph(int code) {
            Glyph glyph = this.glyphs.get(code);
            if (glyph == null) {
                this.missing.add(new String(Character.toChars(code)));
                return this.glyphs.get((int) '?');
            }
            return glyph;
        }

        int width(String text) {
            return text.codePoints().map(code -> glyph(code).advance()).sum();
        }

        String cut(String text, int maxWidth) {
            int width = 0;
            int end = 0;
            for (int code : text.codePoints().toArray()) {
                width += glyph(code).advance();
                if (width > maxWidth) {
                    break;
                }
                end += Character.charCount(code);
            }
            return text.substring(0, end);
        }
    }

    // --- the drawing surface ------------------------------------------------------------------

    /** A string that was drawn, in screen coordinates, with the scale it was drawn at. */
    record Drawn(String text, double x, double y, double width, double height, float scale) {
    }

    static final class MockDraw extends Draw {
        final BufferedImage image;
        final List<Drawn> texts = new ArrayList<>();
        private final Graphics2D g;
        private final McFont font;
        private final Map<String, BufferedImage> sprites = new HashMap<>();
        private final Deque<double[]> stack = new ArrayDeque<>();
        private double tx;
        private double ty;
        private double scale = 1;

        MockDraw(McFont font, int width, int height) {
            this.font = font;
            this.image = new BufferedImage(width * PX, height * PX, BufferedImage.TYPE_INT_ARGB);
            this.g = this.image.createGraphics();
            this.g.setComposite(AlphaComposite.SrcOver);
        }

        private void box(double x0, double y0, double x1, double y1, int argb) {
            this.g.setColor(new Color(argb, true));
            this.g.fill(new Rectangle2D.Double((this.tx + x0 * this.scale) * PX, (this.ty + y0 * this.scale) * PX,
                    (x1 - x0) * this.scale * PX, (y1 - y0) * this.scale * PX));
        }

        @Override
        protected void rect(int x0, int y0, int x1, int y1, int argb) {
            box(x0, y0, x1, y1, argb);
        }

        @Override
        protected void string(String text, int x, int y, int argb) {
            double sx = this.tx + x * this.scale;
            double sy = this.ty + y * this.scale;
            double sw = this.font.width(text) * this.scale;
            double sh = 8 * this.scale;
            int[] clip = this.clips.peek();
            if (clip != null) {
                // What the scissor hides is not drawn, so only the visible part is held to a box.
                double x0 = Math.max(sx, clip[0]);
                double y0 = Math.max(sy, clip[1]);
                double x1 = Math.min(sx + sw, clip[2]);
                double y1 = Math.min(sy + sh, clip[3]);
                if (x1 > x0 && y1 > y0) {
                    this.texts.add(new Drawn(text, x0, y0, x1 - x0, y1 - y0, (float) this.scale));
                }
            } else {
                this.texts.add(new Drawn(text, sx, sy, sw, sh, (float) this.scale));
            }
            double pen = x;
            for (int code : text.codePoints().toArray()) {
                Glyph glyph = this.font.glyph(code);
                boolean[][] pixels = glyph.pixels();
                for (int row = 0; row < pixels.length; row++) {
                    for (int column = 0; column < pixels[row].length; column++) {
                        if (pixels[row][column]) {
                            double gx = pen + column * glyph.scale();
                            double gy = y + glyph.top() + row * glyph.scale();
                            box(gx, gy, gx + glyph.scale(), gy + glyph.scale(), argb);
                        }
                    }
                }
                pen += glyph.advance();
            }
        }

        @Override
        public int width(String text) {
            return this.font.width(text);
        }

        @Override
        protected String cut(String text, int maxWidth) {
            return this.font.cut(text, maxWidth);
        }

        @Override
        public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) {
            BufferedImage source = this.sprites.computeIfAbsent(texture, name -> {
                String[] parts = name.split(":", 2);
                return BenchDraw.image("/assets/" + parts[0] + "/" + parts[1]);
            });
            for (int row = 0; row < h; row++) {
                for (int column = 0; column < w; column++) {
                    int argb = source.getRGB(u + column, v + row);
                    int a = Math.round((argb >>> 24) * alpha);
                    if (a > 0) {
                        box(x + column, y + row, x + column + 1, y + row + 1, (a << 24) | (argb & 0xFFFFFF));
                    }
                }
            }
        }

        /** Open scissors after the push offsets, each already cut to the one under it, in GUI pixels. */
        private final Deque<int[]> clips = new ArrayDeque<>();

        @Override
        public void scissor(int x0, int y0, int x1, int y1) {
            x0 = (int) Math.floor(this.tx + x0 * this.scale);
            y0 = (int) Math.floor(this.ty + y0 * this.scale);
            x1 = (int) Math.floor(this.tx + x1 * this.scale);
            y1 = (int) Math.floor(this.ty + y1 * this.scale);
            int[] clip = {x0, y0, x1, y1};
            int[] under = this.clips.peek();
            if (under != null) {
                clip = new int[]{Math.max(x0, under[0]), Math.max(y0, under[1]), Math.min(x1, under[2]), Math.min(y1, under[3])};
            }
            this.clips.push(clip);
            applyClip();
        }

        @Override
        public void endScissor() {
            this.clips.pop();
            applyClip();
        }

        private void applyClip() {
            int[] clip = this.clips.peek();
            if (clip == null) {
                this.g.setClip(null);
            } else {
                this.g.setClip(clip[0] * PX, clip[1] * PX, Math.max(0, clip[2] - clip[0]) * PX, Math.max(0, clip[3] - clip[1]) * PX);
            }
        }

        /** A plain world-like backdrop, so the panel's shadow and transparency read as in game. */
        void backdrop() {
            box(0, 0, this.image.getWidth() / (double) PX, this.image.getHeight() / (double) PX, 0xFF6A8F5A);
            box(0, this.image.getHeight() / (double) PX * 0.55, this.image.getWidth() / (double) PX,
                    this.image.getHeight() / (double) PX, 0xFF7B6A4F);
            box(0, 0, this.image.getWidth() / (double) PX, this.image.getHeight() / (double) PX, 0x70101010);
        }

        /**
         * The item's own texture when the bench has it (the small copied set the scenes show), else the
         * kit's stand-in.
         */
        @Override
        public void item(ItemView item, int x, int y) {
            String[] parts = item.id().split(":", 2);
            for (String folder : new String[]{"item", "block"}) {
                String texture = parts[0] + ":textures/" + folder + "/" + parts[1] + ".png";
                if (has(texture)) {
                    image(texture, x, y, 16, 16, 16, 16, 1F);
                    return;
                }
            }
            super.item(item, x, y);
        }

        @Override
        public void sprite(String atlas, String sprite, int x, int y, int w, int h, float alpha) {
            String[] parts = sprite.split(":", 2);
            String folder = SpriteIcon.GUI.equals(atlas) ? "textures/gui/sprites/" : "textures/";
            if (has(parts[0] + ":" + folder + parts[1] + ".png")) {
                super.sprite(atlas, sprite, x, y, w, h, alpha);
            } else {
                super.item(ItemView.of(sprite), x, y);
            }
        }

        private static boolean has(String texture) {
            String[] parts = texture.split(":", 2);
            return BenchDraw.class.getResource("/assets/" + parts[0] + "/" + parts[1]) != null;
        }

        @Override
        public int guiScale() {
            return PX;
        }

        @Override
        public void push(float x, float y, float scale) {
            this.stack.push(new double[]{this.tx, this.ty, this.scale});
            this.tx += x * this.scale;
            this.ty += y * this.scale;
            this.scale *= scale;
        }

        @Override
        public void pop() {
            double[] saved = this.stack.pop();
            this.tx = saved[0];
            this.ty = saved[1];
            this.scale = saved[2];
        }

        /** Drawn the way the game frames one: dark box, purple rim, wrapped at 190 pixels. */
        @Override
        public void tooltip(List<String> lines, int x, int y) {
            List<String> wrapped = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                StringBuilder line = new StringBuilder();
                for (String word : lines.get(i).split(" ")) {
                    if (line.length() > 0 && this.font.width(line + " " + word) > 190) {
                        wrapped.add(line.toString());
                        colors.add(i == 0 ? 0xFFFFFFFF : 0xFFAAAAAA);
                        line.setLength(0);
                    }
                    line.append(line.length() == 0 ? "" : " ").append(word);
                }
                wrapped.add(line.toString());
                colors.add(i == 0 ? 0xFFFFFFFF : 0xFFAAAAAA);
            }
            int width = wrapped.stream().mapToInt(this.font::width).max().orElse(0);
            int height = wrapped.size() * 10 - 2;
            int left = Math.min(x + 12, this.image.getWidth() / PX - width - 6);
            int top = Math.max(6, Math.min(y - 12, this.image.getHeight() / PX - height - 6));
            dev.dryaides.minuit.Shapes.gameTooltip(this, left, top, width, height);
            int kept = this.texts.size();
            for (int i = 0; i < wrapped.size(); i++) {
                string(wrapped.get(i), left, top + i * 10, colors.get(i));
            }
            // A tooltip floats over the layout, so its lines are not held to any box.
            this.texts.subList(kept, this.texts.size()).clear();
        }

        void save(File target) {
            try {
                target.getParentFile().mkdirs();
                ImageIO.write(this.image, "png", target);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
