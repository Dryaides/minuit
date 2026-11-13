package dev.dryaides.minuit.internal;

import com.google.gson.JsonParser;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.FrameStyles;
import dev.dryaides.minuit.internal.ImageSource;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.Shapes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tint values worked by hand from the multiply and gradient-map definitions, rounded to the nearest byte. */
class ImageFamilyTest {

    private static final String ROOT = "pack:minuit/themes/stone/sprites/";

    @AfterEach
    void uninstall() {
        ImageFamily.install(null);
    }

    /**
     * A grey #808080 button sprite tinted by the face wears a primary #4080C0 as 0.502 times each channel:
     * #204060. The disabled variant has no tint and is blitted as the pack's own file; the panel has no sprite and
     * the base style draws it.
     */
    @Test
    void faceTintPicksVariantAndFallsBackToBase() throws Exception {
        Map<String, ImageSource.Pixels> files = new HashMap<>();
        files.put(ROOT + "button.png", new ImageSource.Pixels(3, 3, fillOf(9, 0xFF808080)));
        files.put(ROOT + "button.disabled.png", new ImageSource.Pixels(3, 3, fillOf(9, 0xFF101010)));
        List<int[]> bakes = new ArrayList<>();
        ImageFamily.install(new ImageSource() {
            @Override
            public Pixels read(String id) {
                return files.get(id);
            }

            @Override
            public String bake(String key, Pixels image) {
                bakes.add(image.argb());
                return "baked:" + key;
            }
        });
        ImageFamily.Sheet sheet = ImageFamily.parse(JsonParser.parseString(
                "{\"parts\": {\"button\": {\"inset\": 1, \"tint\": {\"multiply\": \"face\"}}, \"button.disabled\": {\"tint\": \"none\"}}}"));
        ImageFamily family = new ImageFamily(FrameStyles.SLATE, sheet, ROOT);
        Palette p = palette(0xFF4080C0, 0xFF999999);
        Recorder d = new Recorder();

        family.raised(d, p, 0, 0, 20, 10, p.primary(), Shapes.Relief.RAISED);
        assertEquals(1, bakes.size());
        assertEquals(0xFF204060, bakes.get(0)[4]);
        assertTrue(d.blits.stream().allMatch(t -> t.startsWith("baked:")));
        assertEquals(0, d.fills);

        d.blits.clear();
        family.raised(d, p, 0, 0, 20, 10, p.disabled(), Shapes.Relief.RAISED);
        assertEquals(1, bakes.size());
        assertTrue(!d.blits.isEmpty() && d.blits.stream().allMatch(t -> t.equals(ROOT + "button.disabled.png")));

        d.blits.clear();
        family.panel(d, p, 0, 0, 40, 30);
        assertTrue(d.blits.isEmpty());
        assertTrue(d.fills > 0);
    }

    /**
     * A pack lists a panel whose PNG is missing and a header whose PNG is 3x3 at scale 2: each is read once however
     * often it is drawn, both draw in the base style, and a reload (F3+T) reads them again.
     */
    @Test
    void missingOrOffScalePartIsReadOncePerReload() throws Exception {
        Map<String, Integer> reads = new HashMap<>();
        Map<String, ImageSource.Pixels> files = new HashMap<>();
        files.put(ROOT + "header.png", new ImageSource.Pixels(3, 3, fillOf(9, 0xFF808080)));
        ImageFamily.install(new ImageSource() {
            @Override
            public Pixels read(String id) {
                reads.merge(id, 1, Integer::sum);
                return files.get(id);
            }

            @Override
            public String bake(String key, Pixels image) {
                return "baked:" + key;
            }
        });
        ImageFamily.Sheet sheet = ImageFamily.parse(JsonParser.parseString(
                "{\"scale\": 2, \"parts\": {\"panel\": {}, \"header\": {}}}"));
        ImageFamily family = new ImageFamily(FrameStyles.SLATE, sheet, ROOT);
        Palette p = palette(0xFF4080C0, 0xFF999999);
        Recorder d = new Recorder();
        for (int i = 0; i < 5; i++) {
            family.panel(d, p, 0, 0, 40, 30);
            family.header(d, p, 0, 0, 40, 12);
            family.toast(d, p, 0, 0, 40, 30);
        }
        assertEquals(1, reads.get(ROOT + "panel.png"));
        assertEquals(1, reads.get(ROOT + "header.png"));
        assertTrue(d.blits.isEmpty());

        ImageFamily.reloaded();
        family.panel(d, p, 0, 0, 40, 30);
        assertEquals(2, reads.get(ROOT + "panel.png"));
    }

    /**
     * One frame draws a tinted button under 70 palettes, past the 64 kept by default: the cap grows, so the next
     * frame bakes nothing again. A later frame with 59 more palettes passes the grown cap (128): the 70 images it
     * did not use are queued, freed two clock steps later and never during the frame that may still render them.
     */
    @Test
    void capGrowsForOneFrameAndDropsWaitForALaterFrame() throws Exception {
        long[] now = {100};
        ImageFamily.clock = () -> now[0];
        int[] bakes = {0};
        List<String> drops = new ArrayList<>();
        Map<String, ImageSource.Pixels> files = new HashMap<>();
        files.put(ROOT + "button.png", new ImageSource.Pixels(3, 3, fillOf(9, 0xFF808080)));
        ImageFamily.install(new ImageSource() {
            @Override
            public Pixels read(String id) {
                return files.get(id);
            }

            @Override
            public String bake(String key, Pixels image) {
                bakes[0]++;
                return "baked:" + key;
            }

            @Override
            public void drop(String texture) {
                drops.add(texture);
            }
        });
        try {
            ImageFamily.Sheet sheet = ImageFamily.parse(JsonParser.parseString(
                    "{\"parts\": {\"button\": {\"inset\": 1, \"tint\": {\"multiply\": \"face\"}}}}"));
            ImageFamily family = new ImageFamily(FrameStyles.SLATE, sheet, ROOT);
            Recorder d = new Recorder();
            for (int frame = 0; frame < 2; frame++) {
                for (int i = 0; i < 70; i++) {
                    Palette p = palette(0xFF000000 | i, 0xFF999999);
                    family.raised(d, p, 0, 0, 20, 10, p.primary(), Shapes.Relief.RAISED);
                }
                now[0]++;
            }
            assertEquals(70, bakes[0]);
            assertTrue(drops.isEmpty());

            for (int i = 70; i < 129; i++) {
                Palette p = palette(0xFF000000 | i, 0xFF999999);
                family.raised(d, p, 0, 0, 20, 10, p.primary(), Shapes.Relief.RAISED);
            }
            assertEquals(129, bakes[0]);
            assertTrue(drops.isEmpty());
            now[0]++;
            Palette last = palette(0xFF000080, 0xFF999999);
            family.raised(d, last, 0, 0, 20, 10, last.primary(), Shapes.Relief.RAISED);
            assertTrue(drops.isEmpty());
            now[0]++;
            family.raised(d, last, 0, 0, 20, 10, last.primary(), Shapes.Relief.RAISED);
            assertEquals(70, drops.size());
        } finally {
            ImageFamily.clock = () -> System.nanoTime() >>> 26;
        }
    }

    /** Grey 64 and 192 on the stops black, red, white: halfway to red (#800000), then 129/255 of red to white (#FF8181). */
    @Test
    void gradientMapRunsAlongTheStops() {
        int[] stops = {0xFF000000, 0xFFFF0000, 0xFFFFFFFF};
        assertEquals(0xFF800000, ImageFamily.tint(0xFF404040, ImageFamily.Tint.Kind.MAP, stops));
        assertEquals(0xFFFF8181, ImageFamily.tint(0xFFC0C0C0, ImageFamily.Tint.Kind.MAP, stops));
        // the sprite's alpha carries over: half-transparent white maps to half-transparent white
        assertEquals(0x80FFFFFF, ImageFamily.tint(0x80FFFFFF, ImageFamily.Tint.Kind.MAP, stops));
    }

    private static int[] fillOf(int n, int argb) {
        int[] out = new int[n];
        java.util.Arrays.fill(out, argb);
        return out;
    }

    private static Palette palette(int primary, int disabled) {
        int g = 0xFF777777;
        return new Palette(g, g, g, g, g, g, g, g, g, g, g, g, g, g, g, primary, g, g, g, g, g, g, g, g, g, g, g, disabled, g, g);
    }

    private static final class Recorder extends Draw {
        final List<String> blits = new ArrayList<>();
        int fills;

        @Override protected void rect(int x0, int y0, int x1, int y1, int argb) { fills++; }
        @Override protected void string(String text, int x, int y, int argb) { }
        @Override public int width(String text) { return text.length() * 6; }
        @Override public String cut(String text, int maxWidth) { return text; }
        @Override public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) { blits.add(texture); }
        @Override public void blitRegion(String texture, int x, int y, int w, int h, int u, int v, int rw, int rh, int texW, int texH, float alpha) { blits.add(texture); }
        @Override protected void pushPose(float x, float y, float scale) { }
        @Override protected void popPose() { }
        @Override public void scissor(int x0, int y0, int x1, int y1) { }
        @Override public void endScissor() { }
        @Override public void tooltip(List<String> lines, int x, int y) { }
    }
}
