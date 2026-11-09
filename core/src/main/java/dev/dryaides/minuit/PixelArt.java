package dev.dryaides.minuit;

/**
 * Round shapes and lines as pixel art on a grid of cells, one cell a GUI pixel (or a bigger cell the caller picks).
 * Every round component draws its blocky look from here, so all of them follow one set of rules.
 *
 * <h2>Discs and rings</h2>
 * A disc of {@code side} cells has its centre at {@code side / 2} and a radius of {@code side / 2}: on the corner
 * between four cells when the side is even, on the centre of the middle cell when it is odd, which gives one tip cell
 * on each axis. A cell is in when its centre is inside the radius; the same count serves rows and columns, so a disc
 * is the same under both mirrors and the diagonal. Row {@code k} counts from the middle outward ({@link #fromMiddle}):
 * its centre is {@code k + 1/2} from the middle on an even side, {@code k} on an odd one. A span counts the cells of
 * a row from the middle outward the same way, so cell {@code i} of row {@code k} is in when {@code i < span}.
 *
 * <p>A thin ring along the rim (a band, a gap, a rim, a halo) is the disc minus the disc pulled in by {@code t} cells
 * along rows and along columns: {@code t} cells thick read along whichever axis crosses it, the way a pixel artist
 * draws an outline {@code t} pixels wide (a ring of one cell is exact all round; a wider one can take one more cell
 * where its stair turns the diagonal). Rings measured from one rim never touch or part along the way.
 * A wide ring ends on the inner disc instead, which keeps a large hole round.
 *
 * <h2>Lines</h2>
 * {@link #lineTop} and {@link #lineBottom} give the rows a one-pixel line covers in one column: the row nearest the
 * line at the column's middle, plus the rows it crosses on its way to the next column. Neighbouring columns never
 * share a row, so slopes stay one pixel thin, and a slope of two rows a column gets runs of two, every time. A row the
 * line meets exactly on the edge between two columns goes to the column above it, which a left-right mirror keeps.
 */
final class PixelArt {
    private PixelArt() {
    }

    /** Cells on each side of the middle in row {@code k} of the disc of radius {@code r}, in cells, even side. */
    static int span(double r, int k) {
        return span(r, k, false);
    }

    /** The same on an odd side when {@code odd}: the middle cell counts as the first of each side. */
    static int span(double r, int k, boolean odd) {
        int s = centresIn(r, k, odd);
        // A diagonal cell barely inside sticks out of the stair as an L corner; pixel art leaves it out.
        return s == k + 1 && centresIn(r, k + 1, odd) < k ? k : s;
    }

    private static int centresIn(double r, int k, boolean odd) {
        double o = odd ? 0D : 0.5D;
        double h = k + o;
        double room = r * r - h * h;
        if (room < 0D) {
            return 0;
        }
        return Math.max(0, (int) Math.floor(Math.sqrt(room) - o + 1e-9D) + 1);
    }

    /** The same, of the disc pulled in by {@code t} cells along rows and columns: row {@code k} of a ring's inside. */
    static int span(double r, int k, int t) {
        return span(r, k, t, false);
    }

    static int span(double r, int k, int t, boolean odd) {
        if (t <= 0) {
            return span(r, k, odd);
        }
        return Math.max(0, Math.min(span(r, k, odd) - t, span(r, k + t, odd)));
    }

    /** How far a cell of a square of {@code side} cells is from the middle, as a row or column index from it. */
    static int fromMiddle(int index, int side) {
        int half = side / 2;
        if ((side & 1) != 0) {
            return Math.abs(index - half);
        }
        return index < half ? half - 1 - index : index - half;
    }

    /**
     * True when the cell at {@code (dx, dy)} from the middle, its centre, lies on the one-pixel ray from the middle at
     * {@code turn} (clockwise from the top, 0 to 1): one cell a row on a steep ray, one a column on a flat one, and
     * both cells where the ray runs exactly between two.
     */
    static boolean onRay(double turn, double dx, double dy) {
        double angle = 2D * Math.PI * turn;
        double sx = Math.sin(angle);
        double sy = -Math.cos(angle);
        if (sx * dx + sy * dy < 0D) {
            return false;
        }
        double across = Math.abs(sx * dy - sy * dx) / Math.max(Math.abs(sx), Math.abs(sy));
        return across <= 0.5D + 1e-9D;
    }

    /**
     * The top row a one-pixel line covers in a column, in rows with row {@code n} centred on {@code n}. {@code left},
     * {@code middle} and {@code right} are where the line is at the column's left edge, middle and right edge;
     * {@code high} and {@code low} the highest and lowest the line goes inside the column (a sample between them),
     * or NaN for none. A NaN edge means the line does not go that way.
     */
    static int lineTop(double left, double middle, double right, double high) {
        int top = (int) Math.round(middle);
        top = Math.min(top, edgeTop(left, middle));
        top = Math.min(top, edgeTop(right, middle));
        if (high == high) {
            top = Math.min(top, (int) Math.round(high));
        }
        return top;
    }

    /** The row under the last one the line covers in the column, as {@link #lineTop} counts. */
    static int lineBottom(double left, double middle, double right, double low) {
        int bottom = (int) Math.round(middle);
        bottom = Math.max(bottom, edgeBottom(left, middle));
        bottom = Math.max(bottom, edgeBottom(right, middle));
        if (low == low) {
            bottom = Math.max(bottom, (int) Math.round(low));
        }
        return bottom + 1;
    }

    private static int edgeTop(double edge, double middle) {
        return edge == edge && edge < middle ? (int) Math.floor(edge) + 1 : Integer.MAX_VALUE;
    }

    private static int edgeBottom(double edge, double middle) {
        return edge == edge && edge > middle ? (int) Math.floor(edge) : Integer.MIN_VALUE;
    }
}
