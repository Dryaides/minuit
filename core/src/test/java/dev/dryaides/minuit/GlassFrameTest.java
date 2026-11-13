package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ThemeFiles;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class GlassFrameTest {

    /** Blends every fill source-over into a pixel buffer, as a framebuffer does, and counts the fills per pixel. */
    private static final class Pixels extends Draw {
        static final int SIZE = 160;
        static final int AT = 20;
        final int[] px = new int[SIZE * SIZE];
        final int[] sent = new int[SIZE * SIZE];

        @Override
        protected void rect(int x0, int y0, int x1, int y1, int argb) {
            int a = argb >>> 24;
            for (int y = Math.max(0, y0); y < Math.min(SIZE, y1); y++) {
                for (int x = Math.max(0, x0); x < Math.min(SIZE, x1); x++) {
                    int dst = px[y * SIZE + x];
                    int out = 0xFF000000;
                    for (int s = 0; s <= 16; s += 8) {
                        int c = (argb >> s & 0xFF) * a + (dst >> s & 0xFF) * (255 - a);
                        out |= (c / 255) << s;
                    }
                    px[y * SIZE + x] = out;
                    sent[y * SIZE + x]++;
                }
            }
        }

        @Override protected void string(String text, int x, int y, int argb) { }
        @Override public int width(String text) { return 0; }
        @Override public String cut(String text, int maxWidth) { return text; }
        @Override public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH,
                                   float alpha) { }
        @Override protected void pushPose(float x, float y, float scale) { }
        @Override protected void popPose() { }
        @Override public void scissor(int x0, int y0, int x1, int y1) { }
        @Override public void endScissor() { }
        @Override public void tooltip(List<String> lines, int x, int y) { }
    }

    /** {@code half} with every role made opaque, so the layered drawing is the oracle for the flattened one. */
    private static Palette opaque(Palette half) throws Exception {
        RecordComponent[] roles = Palette.class.getRecordComponents();
        Object[] values = new Object[roles.length];
        Class<?>[] types = new Class<?>[roles.length];
        for (int i = 0; i < roles.length; i++) {
            int c = (int) roles[i].getAccessor().invoke(half);
            values[i] = c == 0 ? 0 : c | 0xFF000000;
            types[i] = int.class;
        }
        return Palette.class.getDeclaredConstructor(types).newInstance(values);
    }

    /**
     * Owner's rule for the glass mode: the family keeps its shapes. Drawn with opaque colours, the flattened part
     * covers the pixels the family's layered drawing covers, in the same colour, and sends each pixel once.
     */
    @Test
    void flattenedPartsMatchTheLayeredDrawing() throws Exception {
        Palette p = opaque(ThemeFiles.builtIn("bedrock", ThemeChoice.Variant.DARK));
        int[][] sizes = {{18, 12}, {60, 20}, {110, 90}};
        for (String family : new String[] {"vanilla", "bedrock", "slate", "bubbly", "soft"}) {
            FrameStyle base = FrameStyles.byName(family);
            GlassFrame glass = new GlassFrame(base);
            for (int[] s : sizes) {
                int w = s[0];
                int h = s[1];
                check(family + " panel " + w + "x" + h, base, glass,
                        (style, d) -> style.panel(d, p, Pixels.AT, Pixels.AT, w, h));
                check(family + " raised " + w + "x" + h, base, glass,
                        (style, d) -> style.raised(d, p, Pixels.AT, Pixels.AT, w, h, p.raised(), true));
                check(family + " well " + w + "x" + h, base, glass,
                        (style, d) -> style.well(d, p, Pixels.AT, Pixels.AT, w, h, false));
            }
        }
    }

    private static void check(String what, FrameStyle base, FrameStyle glass, BiConsumer<FrameStyle, Draw> part) {
        Pixels layered = new Pixels();
        part.accept(base, layered);
        Pixels flat = new Pixels();
        part.accept(glass, flat);
        boolean any = false;
        for (int i = 0; i < layered.px.length; i++) {
            any |= layered.sent[i] > 0;
            if ((layered.sent[i] > 0) != (flat.sent[i] > 0)) {
                fail(what + ": pixel " + i % Pixels.SIZE + "," + i / Pixels.SIZE + " covered by one drawing only");
            }
            if (!close(layered.px[i], flat.px[i])) {
                fail(what + ": pixel " + i % Pixels.SIZE + "," + i / Pixels.SIZE + " is "
                        + Integer.toHexString(flat.px[i]) + ", layered " + Integer.toHexString(layered.px[i]));
            }
            if (flat.sent[i] > 1) {
                fail(what + ": pixel " + i % Pixels.SIZE + "," + i / Pixels.SIZE + " sent " + flat.sent[i] + " times");
            }
        }
        assertTrue(any, what + " drew nothing");
    }

    /** The framebuffer truncates each blend and the flattening rounds once: a step apart per channel at most. */
    private static boolean close(int a, int b) {
        for (int s = 0; s <= 24; s += 8) {
            if (Math.abs((a >> s & 0xFF) - (b >> s & 0xFF)) > 1) {
                return false;
            }
        }
        return true;
    }
}
