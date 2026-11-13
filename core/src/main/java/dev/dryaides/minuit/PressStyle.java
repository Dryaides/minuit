package dev.dryaides.minuit;

/**
 * How a segmented control (toggle group, tabs, side rail) shows which segment is pushed. Every style draws
 * with the family's own button; the segments stand joined, sharing one outline, except in {@link #SEPARATE}.
 */
@Incubating
public enum PressStyle {
    /** The default: each segment stands on its lip; the selected one sinks by the press travel, loses its lip and sits behind its neighbours. */
    OVERLAP,
    /** The inverse of {@link #OVERLAP}, an option only: every segment pushed flat, the selected one raised on its lip. */
    RAISED_SELECTED,
    /** Segments drawn apart as plain buttons, the selected one wearing the family's selected face. */
    SEPARATE;

    /** True when a segment draws pushed (sunk, lipless). */
    public boolean pushed(boolean selected) {
        return this == RAISED_SELECTED ? !selected : this == OVERLAP && selected;
    }

    /** How far a pushed segment of a family sinks: its lip, or its press travel when its buttons have none. */
    static int sink(FrameStyle style, int lip) {
        return lip > 0 ? lip : Math.max(1, style.pressTravel());
    }

    /** One segment in the family's button, {@code sunk} rows into its press (0 stands it on its lip). */
    static void segment(Draw d, Palette p, FrameStyle style, int x, int y, int w, int h, int face, int sunk) {
        style.button(d, p, x, y, w, h, null, face, Shapes.Relief.RAISED, sunk);
    }

    /** The outline of a selected segment, in the primary colour's dark shade. */
    static int selectedEdge(Palette p) {
        return Shapes.mix(p.primary(), 0xFF000000, 0.45F);
    }

    /** Redraws the 1 px outline of the box {@code x, y, w, h} in {@code color}, corners left to the family. */
    static void outline(Draw d, int x, int y, int w, int h, int color) {
        d.fill(x + 1, y, x + w - 1, y + 1, color);
        d.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        d.fill(x, y + 1, x + 1, y + h - 1, color);
        d.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * Draws a joined horizontal row of segments with this style: pushed ones first so their raised neighbours
     * cover the shared outline column. Each cell {@code i} spans {@code xs[i]} to {@code xs[i + 1]}; neighbours
     * overlap by one column. Returns, per segment, how far its label moves down (the sink of a pushed one).
     */
    int[] row(Draw d, Palette p, FrameStyle style, int[] xs, int y, int h, int[] faces, boolean[] selected) {
        int n = faces.length;
        int lip = style.buttonLip(h);
        int travel = sink(style, lip);
        int[] shift = new int[n];
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                boolean pushed = pushed(selected[i]);
                if (pushed != (pass == 0)) {
                    continue;
                }
                int x0 = i == 0 ? xs[i] : xs[i] - 1;
                int w = xs[i + 1] - x0;
                int sunk = pushed ? travel : 0;
                segment(d, p, style, x0, y, w, h, faces[i], sunk);
                if (selected[i]) {
                    outline(d, x0, y + sunk, w, h - sunk, selectedEdge(p));
                }
                shift[i] = sunk;
            }
        }
        return shift;
    }

    /** The rows a label centres in: the face above the lip a raised segment stands on. */
    static int faceHeight(FrameStyle style, int h) {
        int lip = style.buttonLip(h);
        return h - sink(style, lip);
    }
}
