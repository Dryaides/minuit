package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ImageSource;
import dev.dryaides.minuit.internal.DrawStats;
import dev.dryaides.minuit.internal.ImageFamily;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Frame parts {@link GlassFrame} flattened into many rects, baked once into a texture through the game's
 * {@link ImageSource} and drawn as one blit. On 1.12.2 to 1.20.x each fill is one draw call, so a panel of a few
 * hundred rects becomes one call.
 *
 * <p>A part is baked on its {@link #MIN_USES}th draw with the same key (part, palette, size, state), so animated
 * parts whose colour changes every frame stay as rects. The texture holds the flattened colours at one texel per
 * GUI pixel: a nearest blit of it lands the same pixels the rects did, at any GUI scale. Textures past the
 * {@link #BUDGET} go least recently drawn first, freed two clock steps later (26.x renders the GUI after the draw
 * calls). A resource reload frees every baked image at the source, so the cache forgets them all.
 *
 * <p>Off unless the JVM runs with {@code -Dminuit.raster=true}, until an in-game measure confirms the gain.
 */
final class RasterCache {
    static final boolean ENABLED = Boolean.getBoolean("minuit.raster");
    /** Parts with fewer rects stay fills: a blit binds a texture and breaks the fill batch on 26.x. */
    static final int MIN_RECTS = 8;
    static final int MIN_USES = 3;
    private static final int MAX_SIDE = 1024;
    /** Texels kept baked, all textures together: 8 MB of RGBA. */
    private static final long BUDGET = 2L << 20;
    /** Keys remembered while they wait for their {@link #MIN_USES}th draw or hold a texture. */
    private static final int KEYS = 2048;

    /** Two families may draw a part under one palette and state: the key carries the frame. */
    private record Owned(Object frame, Object part) {
    }

    private static final class Entry {
        int uses;
        String texture;
        int x0;
        int y0;
        int w;
        int h;
    }

    private static final Map<Object, Entry> ENTRIES = new LinkedHashMap<>(256, 0.75F, true);
    private static final List<String> DROPPING = new ArrayList<>();
    private static long droppedAt;
    private static long texels;
    private static int seen = ImageFamily.generation();
    private static int serial;

    private RasterCache() {
    }

    /**
     * Draws {@code plan} ({@link GlassFrame#flatten}'s rects of {@code frame}'s {@code part}) at x, y as one blit when it is baked or due to be.
     *
     * @return false when the caller sends the rects itself
     */
    static boolean draw(Draw d, Object frame, Object part, int[] plan, int x, int y) {
        int rects = (plan.length - 1) / 5;
        if (!ENABLED || rects < MIN_RECTS) {
            return false;
        }
        ImageSource src = ImageFamily.source();
        if (src == null) {
            return false;
        }
        Entry e;
        synchronized (ENTRIES) {
            begin(src);
            Owned key = new Owned(frame, part);
            e = ENTRIES.get(key);
            if (e == null) {
                e = new Entry();
                ENTRIES.put(key, e);
                trim();
            }
            if (e.texture == null && ++e.uses >= MIN_USES && !bake(src, e, plan)) {
                return false;
            }
            if (e.texture == null) {
                return false;
            }
            DrawStats.rasterHits++;
            DrawStats.rectsSaved += rects - 1;
        }
        d.blit(e.texture, x + e.x0, y + e.y0, 0, 0, e.w, e.h, e.w, e.h, 1F);
        return true;
    }

    /** A reload freed every baked image; drops queued two clock steps ago are freed now. */
    private static void begin(ImageSource src) {
        int gen = ImageFamily.generation();
        if (gen != seen) {
            seen = gen;
            ENTRIES.clear();
            DROPPING.clear();
            texels = 0;
        }
        if (!DROPPING.isEmpty() && ImageFamily.clockStep() >= droppedAt + 2) {
            for (String id : DROPPING) {
                src.drop(id);
            }
            DROPPING.clear();
        }
    }

    private static boolean bake(ImageSource src, Entry e, int[] plan) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int i = 1; i < plan.length; i += 5) {
            minX = Math.min(minX, plan[i]);
            minY = Math.min(minY, plan[i + 1]);
            maxX = Math.max(maxX, plan[i + 2]);
            maxY = Math.max(maxY, plan[i + 3]);
        }
        int w = maxX - minX;
        int h = maxY - minY;
        if (w <= 0 || h <= 0 || w > MAX_SIDE || h > MAX_SIDE || (long) w * h > BUDGET / 4) {
            return false;
        }
        int[] argb = new int[w * h];
        for (int i = 1; i < plan.length; i += 5) {
            int c = plan[i + 4];
            for (int yy = plan[i + 1]; yy < plan[i + 3]; yy++) {
                int at = (yy - minY) * w - minX;
                for (int xx = plan[i]; xx < plan[i + 2]; xx++) {
                    argb[at + xx] = c;
                }
            }
        }
        // A fresh id per bake: an id dropped earlier may still be queued for this frame's render.
        e.texture = src.bake("raster/" + serial++, new ImageSource.Pixels(w, h, argb));
        e.x0 = minX;
        e.y0 = minY;
        e.w = w;
        e.h = h;
        texels += (long) w * h;
        DrawStats.rasterBaked++;
        evict(e);
        return true;
    }

    /** Least recently drawn textures go until the baked ones fit the budget; {@code keep} was just made. */
    private static void evict(Entry keep) {
        Iterator<Entry> it = ENTRIES.values().iterator();
        while (texels > BUDGET && it.hasNext()) {
            Entry old = it.next();
            if (old != keep && old.texture != null) {
                release(old);
                it.remove();
            }
        }
    }

    /** Keys past {@link #KEYS} go least recently drawn first. */
    private static void trim() {
        Iterator<Entry> it = ENTRIES.values().iterator();
        while (ENTRIES.size() > KEYS && it.hasNext()) {
            Entry old = it.next();
            release(old);
            it.remove();
        }
    }

    private static void release(Entry e) {
        if (e.texture != null) {
            droppedAt = ImageFamily.clockStep();
            DROPPING.add(e.texture);
            texels -= (long) e.w * e.h;
            e.texture = null;
        }
    }
}
