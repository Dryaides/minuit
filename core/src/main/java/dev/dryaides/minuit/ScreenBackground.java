package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What a minuit screen draws behind its panel, picked by the player on the theme page for the whole game:
 * the game's own background ({@link #NONE}), a pattern tinted from the theme's palette, a tile of a vanilla
 * block read from the game's own textures, or a tile a resource pack adds with
 * {@code assets/<namespace>/minuit/backgrounds/<id>.json}:
 * <pre>{"texture": "mypack:textures/gui/bricks.png", "size": 16, "scale": 2, "tint": 0.6, "order": 100}</pre>
 * {@code size} is the texture's side in pixels, {@code scale} how many GUI pixels each texel takes,
 * {@code tint} how strongly the palette's well colour lies over the tile (0 for none, 1 for a plain fill).
 *
 * @param id      the id saved in the config
 * @param nameKey the lang key of its name on the theme page; a pack's own background shows its id when the key is missing
 * @param order   where the theme page lists it, smallest first
 * @param painter what it draws
 */
@Incubating
public record ScreenBackground(String id, String nameKey, int order, Painter painter) {

    /** The id of the game's own background: minuit draws nothing behind the panel. */
    public static final String NONE = "none";

    /** Draws a background over the whole screen. */
    @FunctionalInterface
    public interface Painter {
        void paint(Draw d, Palette p, int width, int height);
    }

    public void paint(Draw d, Palette p, int width, int height) {
        this.painter.paint(d, p, width, height);
    }

    private static final List<ScreenBackground> BUILT_IN = List.of(
            builtIn(NONE, 0, (d, p, w, h) -> { }),
            builtIn("plain", 10, (d, p, w, h) -> d.fill(0, 0, w, h, base(p))),
            builtIn("dots", 20, ScreenBackground::dots),
            builtIn("grid", 30, ScreenBackground::grid),
            builtIn("blueprint", 40, ScreenBackground::blueprint),
            builtIn("stripes", 50, ScreenBackground::stripes),
            builtIn("checker", 60, ScreenBackground::checker),
            builtIn("dirt", 70, tile("minecraft:textures/block/dirt.png", 16, 2, 0.75F)),
            builtIn("stone", 80, tile("minecraft:textures/block/stone.png", 16, 2, 0.75F)),
            builtIn("stars", 90, ScreenBackground::stars),
            builtIn("vignette", 100, ScreenBackground::vignette));

    private static volatile List<ScreenBackground> packs = List.of();

    private static ScreenBackground builtIn(String id, int order, Painter painter) {
        return new ScreenBackground(id, "minuit.background." + id, order, painter);
    }

    /** Every background in the order the theme page lists them: the kit's, then the resource packs'. */
    public static List<ScreenBackground> all() {
        List<ScreenBackground> out = new ArrayList<>(BUILT_IN);
        for (ScreenBackground pack : packs) {
            out.removeIf(b -> b.id.equals(pack.id) && !b.id.equals(NONE));
            if (!pack.id.equals(NONE)) {
                out.add(pack);
            }
        }
        out.sort(Comparator.comparingInt(ScreenBackground::order).thenComparing(ScreenBackground::id));
        return out;
    }

    /** The background {@code id}, or {@link #NONE}'s when no background has it (a pack since removed). */
    public static ScreenBackground byId(String id) {
        String key = id == null ? NONE : id.toLowerCase(Locale.ROOT);
        List<ScreenBackground> list = all();
        for (ScreenBackground b : list) {
            if (b.id.equals(key)) {
                return b;
            }
        }
        return list.get(0);
    }

    /** Replaces the resource packs' backgrounds; called by the theme file reload. */
    public static void load(List<ScreenBackground> fromPacks) {
        packs = List.copyOf(fromPacks);
    }

    /**
     * A tile of {@code texture} ({@code size} texels a side, {@code scale} GUI pixels a texel) repeated over the
     * screen, the palette's {@link #base} laid over it at {@code tint}. The texture is the game's or a pack's,
     * read where the screen draws, never copied into the kit.
     */
    public static Painter tile(String texture, int size, int scale, float tint) {
        return (d, p, w, h) -> {
            int step = size * Math.max(1, scale);
            for (int y = 0; y < h; y += step) {
                for (int x = 0; x < w; x += step) {
                    d.blitRegion(texture, x, y, step, step, 0, 0, size, size, size, size, 1F);
                }
            }
            if (tint > 0F) {
                d.fill(0, 0, w, h, alpha(base(p), Math.min(1F, tint)));
            }
        };
    }

    /** The page tone: the palette's backdrop where it sets one, else its well, opaque. */
    static int base(Palette p) {
        return (p.backdrop() != 0 ? p.backdrop() : p.well()) | 0xFF000000;
    }

    private static int alpha(int colour, float a) {
        return Math.round(a * 255) << 24 | colour & 0xFFFFFF;
    }

    /** The site's dot grid: one dot every 16 px. */
    private static void dots(Draw d, Palette p, int w, int h) {
        d.fill(0, 0, w, h, base(p));
        int ink = alpha(p.textFaint(), 0.35F);
        for (int y = 8; y < h; y += 16) {
            for (int x = 8; x < w; x += 16) {
                d.fill(x, y, x + 1, y + 1, ink);
            }
        }
    }

    /** A fine grid: a 1 px line every 8 px in the well's edge. */
    private static void grid(Draw d, Palette p, int w, int h) {
        d.fill(0, 0, w, h, base(p));
        lines(d, w, h, 8, alpha(p.wellEdge(), 0.45F));
    }

    /** Blueprint paper: the accent's deep shade, fine lines every 8 px and stronger ones every 32 px. */
    private static void blueprint(Draw d, Palette p, int w, int h) {
        d.fill(0, 0, w, h, p.accentDeep() | 0xFF000000);
        lines(d, w, h, 8, 0x1FFFFFFF);
        lines(d, w, h, 32, 0x40FFFFFF);
    }

    private static void lines(Draw d, int w, int h, int step, int ink) {
        for (int x = step - 1; x < w; x += step) {
            d.fill(x, 0, x + 1, h, ink);
        }
        for (int y = step - 1; y < h; y += step) {
            d.fill(0, y, w, y + 1, ink);
        }
    }

    /** Diagonal stripes, 6 px wide every 16 px, stepped two rows at a time so each edge stays a clean pixel stair. */
    private static void stripes(Draw d, Palette p, int w, int h) {
        d.fill(0, 0, w, h, base(p));
        int ink = alpha(p.wellEdge(), 0.4F);
        for (int y = 0; y < h; y += 2) {
            int shift = y % 16;
            for (int x = -16 + shift; x < w; x += 16) {
                d.fill(Math.max(0, x), y, Math.min(w, x + 6), Math.min(h, y + 2), ink);
            }
        }
    }

    /** A checkerboard of 8 px squares: the base and the well's edge over it. */
    private static void checker(Draw d, Palette p, int w, int h) {
        d.fill(0, 0, w, h, base(p));
        int ink = alpha(p.wellEdge(), 0.35F);
        for (int y = 0; y < h; y += 8) {
            for (int x = (y / 8 % 2) * 8; x < w; x += 16) {
                d.fill(x, y, Math.min(w, x + 8), Math.min(h, y + 8), ink);
            }
        }
    }

    /** Stars at fixed places (a hash of each 16 px cell): faint dots, now and then a small cross. */
    private static void stars(Draw d, Palette p, int w, int h) {
        d.fill(0, 0, w, h, base(p));
        int faint = alpha(p.textFaint(), 0.6F);
        int bright = alpha(p.textSoft(), 0.8F);
        for (int cy = 0; cy < h; cy += 16) {
            for (int cx = 0; cx < w; cx += 16) {
                int hash = (cx * 73856093) ^ (cy * 19349663);
                hash ^= hash >>> 13;
                hash *= 0x5bd1e995;
                hash ^= hash >>> 15;
                int sx = cx + 2 + (hash >>> 4 & 11);
                int sy = cy + 2 + (hash >>> 8 & 11);
                if ((hash & 0xF) < 4) {
                    d.fill(sx, sy, sx + 1, sy + 1, faint);
                } else if ((hash & 0x3F) == 7) {
                    d.fill(sx - 1, sy, sx + 2, sy + 1, bright);
                    d.fill(sx, sy - 1, sx + 1, sy + 2, bright);
                }
            }
        }
    }

    /** A light vignette: the base, its edges shaded in the palette's shadow fading out over a sixth of the screen. */
    private static void vignette(Draw d, Palette p, int w, int h) {
        d.fill(0, 0, w, h, base(p));
        int shade = p.shadow();
        int clear = shade & 0xFFFFFF;
        int bx = Math.max(1, w / 6);
        int by = Math.max(1, h / 6);
        d.gradient(0, 0, w, by, shade, shade, clear, clear);
        d.gradient(0, h - by, w, h, clear, clear, shade, shade);
        d.gradient(0, 0, bx, h, shade, clear, shade, clear);
        d.gradient(w - bx, 0, w, h, clear, shade, clear, shade);
    }
}
