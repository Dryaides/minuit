package bench.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Text for the bench. With the game jar Loom cached, it reads the game's own bitmap font, so widths
 * and glyphs match the game for everything but the unifont fallback. Without it, a logical Java font
 * with a fixed 6 px advance stands in.
 */
final class BenchFont {
    private static final int SPACE = 4;
    private static final int FALLBACK_ADVANCE = 6;

    private record Glyph(BufferedImage sheet, int cx, int cy, int cw, int ch, float scale, int top, int advance) { }

    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Font logical = new Font(Font.MONOSPACED, Font.PLAIN, 8);
    final boolean game;

    BenchFont(String mcJar) {
        boolean loaded = false;
        if (mcJar != null && !mcJar.isEmpty()) {
            try (ZipFile zip = new ZipFile(mcJar)) {
                load(zip);
                loaded = glyphs.containsKey((int) 'A');
            } catch (IOException | RuntimeException e) {
                System.err.println("bench: cannot read the font in " + mcJar + ": " + e);
            }
        }
        game = loaded;
    }

    private void load(ZipFile zip) throws IOException {
        ZipEntry json = zip.getEntry("assets/minecraft/font/include/default.json");
        if (json == null) {
            return;
        }
        JsonObject root;
        try (InputStream in = zip.getInputStream(json)) {
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        for (JsonElement e : root.getAsJsonArray("providers")) {
            JsonObject p = e.getAsJsonObject();
            if (!"bitmap".equals(p.get("type").getAsString())) {
                continue;
            }
            String file = p.get("file").getAsString();
            int colon = file.indexOf(':');
            String ns = colon < 0 ? "minecraft" : file.substring(0, colon);
            ZipEntry png = zip.getEntry("assets/" + ns + "/textures/" + file.substring(colon + 1));
            if (png == null) {
                continue;
            }
            BufferedImage sheet;
            try (InputStream in = zip.getInputStream(png)) {
                sheet = ImageIO.read(in);
            }
            addSheet(sheet, p.has("height") ? p.get("height").getAsInt() : 8, p.get("ascent").getAsInt(),
                    p.getAsJsonArray("chars"));
        }
    }

    private void addSheet(BufferedImage sheet, int height, int ascent, JsonArray chars) {
        int rows = chars.size();
        String first = chars.get(0).getAsString();
        int cols = first.codePointCount(0, first.length());
        int cw = sheet.getWidth() / cols;
        int ch = sheet.getHeight() / rows;
        float scale = height / (float) ch;
        for (int r = 0; r < rows; r++) {
            int[] line = chars.get(r).getAsString().codePoints().toArray();
            for (int c = 0; c < line.length; c++) {
                if (line[c] == 0 || glyphs.containsKey(line[c])) {
                    continue;
                }
                int used = 0;
                for (int x = cw - 1; x >= 0 && used == 0; x--) {
                    for (int y = 0; y < ch; y++) {
                        if ((sheet.getRGB(c * cw + x, r * ch + y) >>> 24) != 0) {
                            used = x + 1;
                            break;
                        }
                    }
                }
                glyphs.put(line[c], new Glyph(sheet, c * cw, r * ch, used, ch, scale, 7 - ascent,
                        (int) (0.5F + used * scale) + 1));
            }
        }
    }

    private int advance(int cp) {
        if (cp == ' ') {
            return SPACE;
        }
        Glyph g = glyphs.get(cp);
        return g != null ? g.advance : FALLBACK_ADVANCE;
    }

    /** Skips the game's section-sign codes, which take no room in game either. */
    int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xA7 && i < text.length()) {
                i += Character.charCount(text.codePointAt(i));
                continue;
            }
            w += advance(cp);
        }
        return w;
    }

    String cut(String text, int max) {
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int next = i + Character.charCount(cp);
            if (cp == 0xA7 && next < text.length()) {
                i = next + Character.charCount(text.codePointAt(next));
                continue;
            }
            w += advance(cp);
            if (w > max) {
                return text.substring(0, i);
            }
            i = next;
        }
        return text;
    }

    void draw(Graphics2D g, String text, int x, int y, Color color) {
        g.setColor(color);
        int pen = x;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xA7 && i < text.length()) {
                i += Character.charCount(text.codePointAt(i));
                continue;
            }
            Glyph glyph = glyphs.get(cp);
            if (glyph != null) {
                plot(g, glyph, pen, y);
            } else if (cp != ' ') {
                g.setFont(logical);
                g.drawString(new String(Character.toChars(cp)), pen, y + 7);
            }
            pen += advance(cp);
        }
    }

    private static void plot(Graphics2D g, Glyph glyph, int x, int y) {
        for (int py = 0; py < glyph.ch; py++) {
            for (int px = 0; px < glyph.cw; px++) {
                if ((glyph.sheet.getRGB(glyph.cx + px, glyph.cy + py) >>> 24) > 127) {
                    if (glyph.scale == 1F) {
                        g.fillRect(x + px, y + glyph.top + py, 1, 1);
                    } else {
                        g.fill(new Rectangle2D.Float(x + px * glyph.scale, y + glyph.top + py * glyph.scale,
                                glyph.scale, glyph.scale));
                    }
                }
            }
        }
    }
}
