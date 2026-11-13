package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorPickerComponentTest {

    /** Every rectangle drawn, to check none leaves the component's box. */
    private static final class Recorder extends Draw {
        final List<int[]> rects = new ArrayList<>();

        @Override
        protected void rect(int x0, int y0, int x1, int y1, int argb) {
            this.rects.add(new int[]{x0, y0, x1, y1});
        }

        @Override
        protected void string(String text, int x, int y, int argb) {
        }

        @Override
        public int width(String text) {
            return 6 * text.length();
        }

        @Override
        public String cut(String text, int maxWidth) {
            return text.substring(0, Math.min(text.length(), maxWidth / 6));
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

    /** The owner's report: after many picks a second row of recents spilled under a one-row card. */
    @Test
    void manyPicksWithOneRecentRowStayInsideTheBoxAndDropTheOldest() {
        List<Integer> recent = new ArrayList<>();
        ColorPickerComponent picker = new ColorPickerComponent(new Box("p", 10, 20, 0, 0), 0xFF40A0E0, c -> { })
                .recent(recent).recentRows(1);
        int first = -1;
        for (int i = 0; i < 12; i++) {
            picker.mouseClicked(10 + 5 + 6 * i, 20 + 70 - 5 * i, 0);
            picker.mouseReleased(10 + 5 + 6 * i, 20 + 70 - 5 * i, 0);
            if (i == 0) {
                first = picker.color();
            }
        }
        assertFalse(recent.contains(first), "the oldest pick dropped out");
        assertEquals(picker.color(), recent.get(0), "the newest pick comes first");

        Recorder d = new Recorder();
        picker.draw(d, Themes.MINIMALIST, 0, 0, 0F);
        Box box = picker.box();
        assertTrue(d.rects.size() > recent.size());
        for (int[] r : d.rects) {
            assertTrue(r[0] >= box.x() - 2 && r[2] <= box.right() + 2 && r[1] >= box.y() - 2 && r[3] <= box.bottom() + 2,
                    "drawn at " + java.util.Arrays.toString(r) + " outside " + box);
        }
    }
}
