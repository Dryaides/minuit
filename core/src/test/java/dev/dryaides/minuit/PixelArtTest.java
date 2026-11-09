package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixelArtTest {
    private static boolean[][] ring(int side, int t) {
        double r = side / 2D;
        boolean odd = (side & 1) != 0;
        boolean[][] in = new boolean[side][side];
        for (int row = 0; row < side; row++) {
            int k = PixelArt.fromMiddle(row, side);
            for (int col = 0; col < side; col++) {
                int i = PixelArt.fromMiddle(col, side);
                in[row][col] = i < PixelArt.span(r, k, odd) && i >= PixelArt.span(r, k, t, odd);
            }
        }
        return in;
    }

    private static int run(boolean[][] in, int row, int col, int dRow, int dCol) {
        int n = 0;
        for (int r = row, c = col; r >= 0 && c >= 0 && r < in.length && c < in.length && in[r][c]; r += dRow, c += dCol) {
            n++;
        }
        return n;
    }

    /**
     * A ring drawn as pixel art, on an even side or an odd one, is the same under both mirrors and the diagonal, and
     * is {@code t} cells thick read along the axis that crosses it: the shorter of the row run and the column run
     * through any of its cells. An odd side holds its middle cell on each edge, the tip on that axis.
     */
    @Test
    void ringsAreSymmetricAndKeepTheirWidth() {
        for (int side = 24; side <= 161; side++) {
            for (int t = 1; t <= 3; t++) {
                boolean[][] in = ring(side, t);
                if ((side & 1) != 0) {
                    assertTrue(in[0][side / 2], "side=" + side + " has no tip cell");
                }
                for (int row = 0; row < side; row++) {
                    for (int col = 0; col < side; col++) {
                        String at = "side=" + side + " t=" + t + " (" + col + "," + row + ")";
                        assertEquals(in[row][col], in[row][side - 1 - col], at);
                        assertEquals(in[row][col], in[side - 1 - row][col], at);
                        assertEquals(in[row][col], in[col][row], at);
                        if (!in[row][col]) {
                            continue;
                        }
                        int across = run(in, row, col, 0, -1) + run(in, row, col, 0, 1) - 1;
                        int down = run(in, row, col, -1, 0) + run(in, row, col, 1, 0) - 1;
                        boolean nearDiagonal = Math.abs(PixelArt.fromMiddle(row, side) - PixelArt.fromMiddle(col, side)) <= 2;
                        if (t == 1 || !nearDiagonal) {
                            assertEquals(t, Math.min(across, down), at);
                        }
                    }
                }
            }
        }
    }

    /** A peak of two rows a column: runs of two in every column, no row shared by two columns, the sides mirrored. */
    @Test
    void lineIsOnePixelThinAndMirrored() {
        int half = 12;
        int[] top = new int[2 * half + 1];
        int[] bottom = new int[2 * half + 1];
        for (int c = -half; c <= half; c++) {
            double left = 2D * Math.abs(c - 0.5D);
            double middle = 2D * Math.abs(c);
            double right = 2D * Math.abs(c + 0.5D);
            top[c + half] = PixelArt.lineTop(c == -half ? Double.NaN : left, middle, c == half ? Double.NaN : right, Double.NaN);
            bottom[c + half] = PixelArt.lineBottom(c == -half ? Double.NaN : left, middle, c == half ? Double.NaN : right, Double.NaN);
        }
        for (int c = 0; c < top.length; c++) {
            assertEquals(top[c], top[top.length - 1 - c], "mirror top " + c);
            assertEquals(bottom[c], bottom[top.length - 1 - c], "mirror bottom " + c);
            if (c != half && c != half - 1 && c != half + 1 && c > 0 && c < top.length - 1) {
                assertEquals(2, bottom[c] - top[c], "rows in column " + c);
            }
            if (c > 0) {
                boolean touches = bottom[c - 1] == top[c] || bottom[c] == top[c - 1] || top[c] == top[c - 1];
                assertTrue(touches, "columns " + (c - 1) + " and " + c + " neither touch nor share a row");
            }
        }
    }
}
