package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The layout and pixel rules of THEMES.md, checked in every family the kit ships. */
class LayoutRulesTest {

    private static final class Grid extends Draw {
        final boolean[][] on = new boolean[60][60];

        @Override
        protected void rect(int x0, int y0, int x1, int y1, int argb) {
            for (int y = Math.max(0, y0); y < Math.min(60, y1); y++) {
                for (int x = Math.max(0, x0); x < Math.min(60, x1); x++) {
                    this.on[y][x] = true;
                }
            }
        }

        int rightmost() {
            int right = -1;
            for (boolean[] row : this.on) {
                for (int x = 0; x < row.length; x++) {
                    if (row[x]) {
                        right = Math.max(right, x);
                    }
                }
            }
            return right;
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

    private static final Palette P = Themes.byId("goodboy").palette();

    /** R6: whatever the family draws for a bar, its track's outer edge lies on the content column's right edge. */
    @Test
    void scrollTrackEndsOnTheContentEdge() {
        int right = 40;
        for (String name : FrameStyles.names()) {
            FrameStyle frame = FrameStyles.byName(name);
            Grid g = new Grid();
            frame.scroller(g, P, ScrollBar.x(frame, right), 5, 40, 10, 12, false);
            assertEquals(right - 1, g.rightmost(), name);
        }
    }

    /** R5: a chosen card's ring stays inside the panel's own silhouette: no pixel of it pokes past a corner. */
    @Test
    void panelEdgeStaysInsideThePanelOutline() {
        for (String name : FrameStyles.names()) {
            FrameStyle frame = FrameStyles.byName(name);
            Grid shape = new Grid();
            frame.shadow(shape, P, 5, 5, 40, 30, 0xFF000000);
            Grid edge = new Grid();
            frame.panelEdge(edge, P, 5, 5, 40, 30, 2, 0xFF000000);
            for (int y = 0; y < 60; y++) {
                for (int x = 0; x < 60; x++) {
                    assertTrue(!edge.on[y][x] || shape.on[y][x], name + " pokes out at " + x + "," + y);
                }
            }
        }
    }
}
