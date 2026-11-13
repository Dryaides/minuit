package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FillFoldTest {

    /** Blends every fill source-over into a pixel buffer, the way a framebuffer does. */
    private static final class Pixels extends Draw {
        static final int SIZE = 64;
        final int[] px = new int[SIZE * SIZE];

        @Override
        protected void rect(int x0, int y0, int x1, int y1, int argb) {
            int a = argb >>> 24;
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int dst = px[y * SIZE + x];
                    int out = 0xFF000000;
                    for (int s = 0; s <= 16; s += 8) {
                        int c = (argb >> s & 0xFF) * a + (dst >> s & 0xFF) * (255 - a);
                        out |= (c / 255) << s;
                    }
                    px[y * SIZE + x] = out;
                }
            }
        }

        @Override protected void string(String text, int x, int y, int argb) { }
        @Override public int width(String text) { return 0; }
        @Override public String cut(String text, int maxWidth) { return text; }
        @Override public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) { }
        @Override protected void pushPose(float x, float y, float scale) { }
        @Override protected void popPose() { }
        @Override public void scissor(int x0, int y0, int x1, int y1) { }
        @Override public void endScissor() { }
        @Override public void tooltip(List<String> lines, int x, int y) { }
    }

    /** A drop shadow, a face, a translucent highlight row and opaque edge columns over the face. */
    private static final FillFold.Geometry BUTTON = (d, w, h, extra) -> {
        d.fill(1, 1, w + 1, h + 1, 0);
        d.fill(0, 0, w, h, 1);
        d.fill(0, 0, w, extra, 2);
        d.fill(0, 0, 1, h, 3);
        d.fill(w - 1, 0, w, h, 3);
    };

    private static final int[] COLORS = {0x80000000, 0xFF3366AA, 0x60FFFFFF, 0xFF112233};

    @Test
    void unseenSizeDrawsRawThenFoldsWithSamePixels() {
        int w = 41;
        int h = 29;
        int extra = 3;
        Pixels expected = new Pixels();
        for (int[] f : new int[][] {{1, 1, w + 1, h + 1, 0}, {0, 0, w, h, 1}, {0, 0, w, extra, 2},
                {0, 0, 1, h, 3}, {w - 1, 0, w, h, 3}}) {
            expected.fill(f[0] + 5, f[1] + 7, f[2] + 5, f[3] + 7, COLORS[f[4]]);
        }

        int before = FillFold.plansBuilt();
        Pixels first = new Pixels();
        FillFold.draw(first, 61, BUTTON, 5, 7, w, h, extra, COLORS, COLORS.length);
        assertEquals(before, FillFold.plansBuilt(), "a size seen once builds no plan");
        assertArrayEquals(expected.px, first.px);

        Pixels second = new Pixels();
        FillFold.draw(second, 61, BUTTON, 5, 7, w, h, extra, COLORS, COLORS.length);
        assertEquals(before + 1, FillFold.plansBuilt(), "the size coming back builds its plan");
        assertArrayEquals(expected.px, second.px);
    }
}
