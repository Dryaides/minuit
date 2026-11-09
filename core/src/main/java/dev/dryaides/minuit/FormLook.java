package dev.dryaides.minuit;

/**
 * What the form widgets draw the same way: readable inks on the surface and on a field, small pixel art and
 * the caret's blink. Palettes differ a lot in contrast, so every ink that carries meaning is checked here.
 */
final class FormLook {
    static final int FONT_H = RowLook.FONT_H;
    static final double READABLE = 4.5D;
    private static final int DISABLED_HINT_ALPHA = 0xB0;
    /** Palettes have no colour for the field. Every family draws it near black or near white, under its fieldText. */
    private static final int DARK_FIELD = 0xFF16161C;
    private static final int LIGHT_FIELD = 0xFFF2F2F2;

    private FormLook() {
    }

    static int solid(int color) {
        return color | 0xFF000000;
    }

    static int surface(Palette p) {
        return solid(p.surface());
    }

    /** {@code wanted}, moved towards {@code fallback} until it reads on {@code under}: some reds and greys do not. */
    static int readable(int wanted, int fallback, int under) {
        int from = solid(wanted);
        int to = solid(fallback);
        for (float toward = 0F; toward < 0.8F; toward += 0.15F) {
            int color = Shapes.mix(from, to, toward);
            if (Shapes.contrast(color, under) >= READABLE) {
                return color;
            }
        }
        return to;
    }

    /** The edge of a small box or circle: the palette's when it stands out from the surface, stronger when not (soft light). */
    static int edge(Palette p) {
        int surface = surface(p);
        return Shapes.contrast(solid(p.wellEdge()), surface) >= 2D ? p.wellEdge() : Shapes.mix(solid(p.text()), surface, 0.5F);
    }

    /**
     * The outline of a small box or circle under the mouse: the text colour, or white where the text is as dark as
     * the resting {@link #edge} (vanilla), as checkbox_highlighted.png turns the outline white.
     */
    static int hoverEdge(Palette p) {
        int text = solid(p.text());
        return Shapes.contrast(text, solid(edge(p))) < 1.5D ? 0xFFFFFFFF : text;
    }

    /** The face of a disabled small box: never brighter than the enabled one, which the palette's disabled colour can be. */
    static int disabledFace(Palette p) {
        return Shapes.mix(surface(p), solid(p.disabledText()), 0.3F);
    }

    /** The error colour for a line round something, which needs less contrast than text: pure red where it shows at all. */
    static int alertEdge(Palette p) {
        int bad = solid(p.bad());
        return Shapes.contrast(bad, surface(p)) >= 1.5D ? bad : alert(p);
    }

    /** A neutral face that shows on the surface, where {@code raised} can be the surface's own colour (soft light). */
    static int quietFace(Palette p) {
        int surface = surface(p);
        int raised = solid(p.raised());
        return Shapes.contrast(raised, surface) >= 1.25D ? raised : Shapes.mix(surface, solid(p.text()), 0.18F);
    }

    /** The error colour on the surface. */
    static int alert(Palette p) {
        return readable(p.bad(), p.text(), surface(p));
    }

    /** The quieter text on the surface; the plain text where the quieter one is too weak (vanilla dark). */
    static int soft(Palette p) {
        return Ink.soft(p, surface(p));
    }

    /** The cells across {@link #RADIO_RING}. */
    static final int RADIO_ART = 9;
    static final String[] RADIO_RING = {
            "..#####..",
            ".#.....#.",
            "#.......#",
            "#.......#",
            "#.......#",
            "#.......#",
            "#.......#",
            ".#.....#.",
            "..#####.."};
    static final String[] RADIO_INSIDE = {
            ".........",
            "..#####..",
            ".#######.",
            ".#######.",
            ".#######.",
            ".#######.",
            ".#######.",
            "..#####..",
            "........."};
    static final String[] RADIO_DOT = {
            ".###.",
            "#####",
            "#####",
            "#####",
            ".###."};
    /** The cells inside a check box's edge at its smallest. */
    private static final int CHECK_ART = 7;
    private static final String[] CHECK = {
            "......#",
            ".....##",
            "#...##.",
            "##.##..",
            ".###...",
            "..#....",
            "......."};

    /** A tick in whole cells, centred in a box {@code side} by {@code side}. */
    static void tick(Draw d, int left, int top, int side, int ink) {
        int cell = Math.max(1, (side - 2) / CHECK_ART);
        int inset = (side - CHECK_ART * cell) / 2;
        pixels(d, CHECK, left + inset, top + inset + (cell + 1) / 2, cell, ink);
    }

    /** The dash of a mixed check box, its height even so it sits in the middle of the box. */
    static void dash(Draw d, int left, int top, int side, int ink) {
        int thick = Math.max(1, side / 5);
        if ((side - thick) % 2 != 0) {
            thick++;
        }
        int margin = Math.max(2, side / 4);
        int from = top + (side - thick) / 2;
        d.fill(left + margin, from, left + side - margin, from + thick, ink);
    }

    static int fieldBack(Palette p, FrameStyle frame) {
        return Shapes.luminance(solid(frame.fieldText(p))) > 0.3D ? DARK_FIELD : LIGHT_FIELD;
    }

    /**
     * The palette's disabled text is made for its surface. Light families have a dark field, where it reads
     * brighter than an enabled hint: the hint at reduced strength is used there instead.
     */
    static int fieldDisabled(Palette p, FrameStyle frame) {
        int text = frame.fieldText(p);
        int hint = frame.fieldHint(p);
        return Shapes.contrast(p.disabledText(), text) >= Shapes.contrast(hint, text) ? p.disabledText() : Shapes.alpha(hint, DISABLED_HINT_ALPHA);
    }

    /** Draws art made of rows of {@code #} at one pixel a cell. */
    static void pixels(Draw d, String[] art, int left, int top, int color) {
        pixels(d, art, left, top, 1, color);
    }

    /** Draws art made of rows of {@code #}, each cell {@code size} pixels wide and high. */
    static void pixels(Draw d, String[] art, int left, int top, int size, int color) {
        for (int row = 0; row < art.length; row++) {
            String line = art[row];
            int from = -1;
            for (int col = 0; col <= line.length(); col++) {
                boolean on = col < line.length() && line.charAt(col) == '#';
                if (on && from < 0) {
                    from = col;
                } else if (!on && from >= 0) {
                    d.fill(left + from * size, top + row * size, left + col * size, top + (row + 1) * size, color);
                    from = -1;
                }
            }
        }
    }

    /** True while a caret that was moved at {@code since} shows. Steady under reduced motion. */
    static boolean caretShown(long since) {
        if (BaseComponent.reducedMotion) {
            return true;
        }
        long age = BaseComponent.clock.getAsLong() - since;
        return age < 0 || age % (2 * TextFieldComponent.BLINK_MS) < TextFieldComponent.BLINK_MS;
    }
}
