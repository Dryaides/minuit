package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An author's edge value (an empty panel, a zero size, a null entry, an infinite number) never makes
 * a component throw at build, render or input, and what it draws stays in its box.
 */
class EdgeValuesTest {

    /** Every rectangle drawn, in screen coordinates. */
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
            return text.substring(0, Math.max(0, Math.min(text.length(), maxWidth / 6)));
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

    private static Recorder drawInside(BaseComponent c) {
        Recorder d = new Recorder();
        c.draw(d, Themes.MINIMALIST, c.box().x() + 1, c.box().y() + 1, 0F);
        Box box = c.box();
        for (int[] r : d.rects) {
            assertTrue(r[0] >= box.x() && r[2] <= box.right() && r[1] >= box.y() && r[3] <= box.bottom(),
                    "drawn at " + Arrays.toString(r) + " outside " + box);
        }
        return d;
    }

    /** A window built before its panel is filled: the panel is 0 x 0. */
    @Test
    void windowOverAnEmptyPanel() {
        WindowComponent.layouts = LayoutStore.memory();
        PanelComponent empty = new PanelComponent(null, null, 0, 0) {
            @Override
            public int width() {
                return 0;
            }

            @Override
            public int height() {
                return 0;
            }

            @Override
            protected void drawBackground(Draw d, int mx, int my) {
            }
        };
        WindowComponent window = assertDoesNotThrow(() -> new WindowComponent("t:empty", empty, new Box("w", 10, 10, 0, 0)));
        assertTrue(Float.isFinite(window.scale()) && window.scale() > 0F, "scale " + window.scale());
        assertDoesNotThrow(() -> window.draw(new Recorder(), Themes.MINIMALIST, 0, 0, 0F));
    }

    /** Rows 0 px high and a null entry from a lookup that missed. */
    @Test
    void listWithZeroRowHeightAndANullItem() {
        List<String> drawn = new ArrayList<>();
        ListComponent<String> list = new ListComponent<String>(new Box("l", 0, 0, 80, 40), 0,
                (d, theme, item, row, hovered, selected) -> drawn.add(item), item -> { });
        list.setItems(Arrays.asList("a", null, "b"));
        list.selected = "b";
        drawInside(list);
        assertEquals(List.of("a", "b"), drawn);
        assertDoesNotThrow(() -> list.mouseClicked(5, 5, 0));
        assertDoesNotThrow(() -> list.mouseScrolled(5, 5, -1));
    }

    /** A grid asked for 0 columns gets one, and the arrow keys still move. */
    @Test
    void gridWithZeroColumns() {
        GridComponent<String> grid = assertDoesNotThrow(() -> new GridComponent<String>(new Box("g", 0, 0, 60, 0), 0, 10, 2, 2,
                Arrays.asList("a", null, "b"), (d, theme, item, cell, hovered, selected) -> { }, item -> { }));
        assertEquals(List.of("a", "b"), grid.items());
        assertEquals(10 + 2 + 10, grid.box().h(), "two rows of one card");
        drawInside(grid);
        assertDoesNotThrow(() -> grid.keyPressed(InputKey.DOWN, false));
    }

    /** A negative length limit and null text, from a config value that was never set. */
    @Test
    void textFieldWithNegativeLimitAndNullText() {
        TextFieldComponent field = assertDoesNotThrow(() -> new TextFieldComponent(new Box("f", 0, 0, 80, 20), null, t -> { }));
        assertEquals("", field.text());
        field.setText("hello");
        assertDoesNotThrow(() -> field.maxLength(-1));
        assertEquals("", field.text(), "no room left");
        field.maxLength(3);
        field.setText(null);
        assertEquals("", field.text());
        field.setText("hello");
        assertEquals("hel", field.text());
        drawInside(field);
    }

    /** Infinity and NaN keep the last good value; an infinite limit means no limit. */
    @Test
    void numberFieldWithNonFiniteValues() {
        NumberFieldComponent n = new NumberFieldComponent(new Box("n", 0, 0, 80, 20), 0, 10, 4, v -> { });
        assertDoesNotThrow(() -> n.setValue(Double.POSITIVE_INFINITY));
        assertEquals(4D, n.value());
        n.setValue(Double.NaN);
        assertEquals(4D, n.value());
        NumberFieldComponent open = assertDoesNotThrow(() -> new NumberFieldComponent(new Box("o", 0, 0, 80, 20),
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, v -> { }));
        assertEquals(0D, open.value(), "no good value yet: the default 0, inside the limits");
        drawInside(n);
    }

    /** A translation lookup that returned null inside a label list keeps its place as a blank name. */
    @Test
    void chartLabelsWithANullName() {
        assertDoesNotThrow(() -> new ChartComponent(new Box("c", 0, 0, 120, 60)).categories(Arrays.asList("Mon", null, "Wed")));
        HeatmapComponent map = new HeatmapComponent(new Box("h", 0, 0, 120, 60)).grid(2, 3, 1, 2, 3, 4, 5, 6);
        assertDoesNotThrow(() -> map.rowLabels(Arrays.asList(null, "b")).colLabels(Arrays.asList("x", null, "z")));
        assertDoesNotThrow(() -> map.draw(new Recorder(), Themes.MINIMALIST, 0, 0, 0F));
    }
}
