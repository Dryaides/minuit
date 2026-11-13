package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShapesBandTest {

    /** Counts how many times each pixel of a 40 by 40 grid is painted. */
    private static final class Grid extends Draw {
        final int[][] hits = new int[40][40];

        @Override
        protected void rect(int x0, int y0, int x1, int y1, int argb) {
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    this.hits[y][x]++;
                }
            }
        }

        @Override
        protected void string(String text, int x, int y, int argb) {
        }

        @Override
        public int width(String text) {
            return 0;
        }

        @Override
        public String cut(String text, int maxWidth) {
            return text;
        }

        @Override
        public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) {
        }

        @Override
        protected void pushPose(float x, float y, float scale) {
        }

        @Override
        protected void popPose() {
        }

        @Override
        public void scissor(int x0, int y0, int x1, int y1) {
        }

        @Override
        public void endScissor() {
        }

        @Override
        public void tooltip(List<String> lines, int x, int y) {
        }
    }

    /** Card selected, vanilla: two 1 px rings of radius 2 left (x+1, y+1) unpainted, where the panel showed black. */
    @Test
    void twoPixelEdgeCoversTheInnerCorner() {
        Grid g = new Grid();
        Shapes.band(g, 4, 4, 20, 12, 2, 2, 0xFF000000);
        assertEquals(1, g.hits[5][5]);
        assertEquals(1, g.hits[14][22]);
    }

    /** A ring around a shape plus the shape fills the grown shape exactly once: no gap pixel, no overlap. */
    @Test
    void ringAroundAShapeTilesTheGrownShape() {
        for (int r = 0; r <= 4; r++) {
            for (int t = 1; t <= 2; t++) {
                Grid inner = new Grid();
                Shapes.rounded(inner, 10, 10, 15, 11, r, 0xFF000000);
                Grid band = new Grid();
                Shapes.band(band, 10 - t, 10 - t, 15 + 2 * t, 11 + 2 * t, r + t, t, 0xFF000000);
                Grid outer = new Grid();
                Shapes.rounded(outer, 10 - t, 10 - t, 15 + 2 * t, 11 + 2 * t, r + t, 0xFF000000);
                for (int y = 0; y < 40; y++) {
                    for (int x = 0; x < 40; x++) {
                        assertEquals(outer.hits[y][x], inner.hits[y][x] + band.hits[y][x],
                                "r " + r + " t " + t + " at " + x + "," + y);
                    }
                }
            }
        }
    }
}
