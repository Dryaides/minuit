package dev.dryaides.minuit;

/**
 * What the data widgets (donut, gauge, sparkline, heatmap, steps, timeline, kanban) draw the same way: the
 * {@link SeriesColors}, the inks on the panel's surface, and pictures made of whole pixels.
 */
final class DataLook {
    static final int COLOURS = SeriesColors.COUNT;
    private static final int TEXT_WASH = 0x28;

    private DataLook() {
    }

    /** The chart's colour for this place in its order. */
    static int seriesColour(Theme theme, int order) {
        return SeriesColors.color(theme.palette(), order);
    }

    /** The same in a chosen scheme. */
    static int seriesColour(Theme theme, SeriesPalette scheme, int order) {
        return SeriesColors.color(theme.palette(), scheme, order);
    }

    static int opaque(int color) {
        return color | 0xFF000000;
    }

    /** The surface as a solid colour, for judging contrast: a see-through family is judged on its own tint. */
    static int back(Palette p) {
        return opaque(p.surface());
    }

    /** The text on the panel's surface. */
    static int ink(Palette p, boolean active) {
        return active ? bestInk(p, back(p)) : p.disabledText();
    }

    /** The quieter text on the panel's surface; the plain one when the quieter one would be too weak. */
    static int softInk(Palette p, boolean active) {
        return active ? Ink.soft(p, back(p)) : p.disabledText();
    }

    /** The palette's ink that stands out most on a solid colour. */
    static int bestInk(Palette p, int under) {
        return Ink.on(p, under);
    }

    /** A colour moved towards the text until a shape of it reads on the surface. */
    static int readable(Palette p, int color) {
        return Ink.readable(color, back(p), Ink.MARK);
    }

    /** A well of nothing: the part of a shape that is not filled in. */
    static int wash(Palette p) {
        return Shapes.alpha(opaque(bestInk(p, back(p))), TEXT_WASH);
    }

    /** A colour faded for a disabled widget. */
    static int faded(Palette p, int color) {
        return Shapes.mix(opaque(color), back(p), 0.55F);
    }

    /** The clockwise turn from the top, from 0 to 1. */
    static double turnOf(double dx, double dy) {
        double turn = Math.atan2(dx, -dy) / (2D * Math.PI);
        return turn < 0D ? turn + 1D : turn;
    }

    /**
     * Draws a square picture of cells, one a pixel, as horizontal runs: runs never overlap, so a translucent colour
     * stays even and no seam can show between two rectangles of a curve. A cell of -1 is left out, and so is a
     * colour with no alpha. Allocates nothing.
     */
    static void cells(Draw d, int left, int top, int side, int[] cells, int[] colours) {
        int at = 0;
        for (int row = 0; row < side; row++) {
            int runColor = 0;
            int runFrom = 0;
            for (int col = 0; col <= side; col++) {
                int color = 0;
                if (col < side) {
                    int cell = cells[at++];
                    color = cell < 0 ? 0 : colours[cell];
                }
                if (color != runColor) {
                    if (runColor >>> 24 != 0) {
                        d.fill(left + runFrom, top + row, left + col, top + row + 1, runColor);
                    }
                    runColor = color;
                    runFrom = col;
                }
            }
        }
    }

    /** A one pixel outline, corners included once, so a translucent colour stays even. */
    static void outline(Draw d, int left, int top, int width, int height, int color) {
        d.fill(left, top, left + width, top + 1, color);
        d.fill(left, top + height - 1, left + width, top + height, color);
        d.fill(left, top + 1, left + 1, top + height - 1, color);
        d.fill(left + width - 1, top + 1, left + width, top + height - 1, color);
    }

    /** A small picture from strings, {@code #} for a pixel. */
    static void art(Draw d, String[] rows, int left, int top, int color) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) == '#') {
                    d.fill(left + col, top + row, left + col + 1, top + row + 1, color);
                }
            }
        }
    }

    /** A number without the locale's marks and without a useless ".0". */
    static String number(double value) {
        if (value != value) {
            return "-";
        }
        return ChartComponent.plain(value, Math.abs(value) >= 100D ? 0 : 1, true);
    }
}
