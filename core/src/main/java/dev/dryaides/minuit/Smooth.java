package dev.dryaides.minuit;

import java.util.Arrays;

/**
 * A smooth picture made of rectangles only: a grid finer than the GUI, down to one screen pixel, where each cell holds
 * how much of it a shape covers, turned into see-through cells along the edges and merged into as few rectangles as
 * it can. Shared by the charts, the gauge and the 3D views when they are asked for {@code smooth(true)}.
 *
 * <p>One layer is one colour (or one picture a caller fills itself through {@link #set}). A frame only replays the
 * rectangles; the grid is only worked out again when the caller says something changed.
 *
 * <h2>Steps</h2>
 * {@link #ladder} lists what is tried, finest first: a cell of one screen pixel ({@code sub} = the GUI scale) with
 * six levels of see-through, then three levels, then coarser cells, then one GUI unit with four and two levels.
 * Fewer levels let more edge cells share a colour, so they merge. A caller takes the first step that keeps under its
 * budget of rectangles, and draws its old pixel look when none does.
 *
 * <p>Drawing pushes a scale of {@code 1/sub}, so a cell lands on whole screen pixels when {@code sub} divides the GUI
 * scale, which every step does.
 */
final class Smooth {
    /** Most cells a layer may hold. A step that needs more is passed over. */
    static final int MAX_CELLS = 640_000;
    private static final int RUN = 5;
    private static final int[][] LADDERS = new int[9][];

    private int w;
    private int h;
    private int sub = 1;
    private int levels = 4;
    private float[] cov = new float[0];
    private int[] grid = new int[0];
    private int[] runs = new int[RUN * 64];
    private int runCount;
    private int[] other = new int[RUN * 64];
    private int[] prevRow = new int[0];
    private int[] curRow = new int[0];
    private int clipL;
    private int clipT;
    private int clipR;
    private int clipB;

    /** The GUI scale to work at, 1 to 8. */
    static int scale(Draw d) {
        return scaleOf(d.guiScale());
    }

    static int scaleOf(int gs) {
        return Math.max(1, Math.min(8, gs));
    }

    /**
     * What is tried for a GUI scale, finest first, as {@code sub, levels} pairs: {@code sub} cells to a GUI unit and
     * {@code levels} steps of see-through on an edge.
     */
    static int[] ladder(int gs) {
        int scale = Math.max(1, Math.min(8, gs));
        int[] known = LADDERS[scale];
        if (known != null) {
            return known;
        }
        int[] steps = new int[16];
        int n = 0;
        if (scale > 1) {
            steps[n++] = scale;
            steps[n++] = 6;
            steps[n++] = scale;
            steps[n++] = 3;
            for (int s = scale / 2; s > 1; s--) {
                if (scale % s == 0) {
                    steps[n++] = s;
                    steps[n++] = 3;
                    break;
                }
            }
            steps[n++] = 1;
            steps[n++] = 4;
        } else {
            steps[n++] = 1;
            steps[n++] = 6;
            steps[n++] = 1;
            steps[n++] = 3;
        }
        steps[n++] = 1;
        steps[n++] = 2;
        int[] made = Arrays.copyOf(steps, n);
        LADDERS[scale] = made;
        return made;
    }

    // ---- the grid ----

    /** Starts an empty grid of {@code w x h} cells, {@code sub} to a GUI unit. */
    void begin(int w, int h, int sub, int levels) {
        this.w = Math.max(0, w);
        this.h = Math.max(0, h);
        this.sub = Math.max(1, sub);
        this.levels = Math.max(1, levels);
        int cells = this.w * this.h;
        if (cov.length < cells) {
            cov = new float[cells];
            grid = new int[cells];
        }
        Arrays.fill(cov, 0, cells, 0F);
        Arrays.fill(grid, 0, cells, 0);
        unclip();
        runCount = 0;
    }

    int width() { return w; }
    int height() { return h; }
    int sub() { return sub; }
    int levels() { return levels; }

    /** Shapes after this only cover cells inside these bounds, in cells, right and bottom left out. */
    void clip(int left, int top, int right, int bottom) {
        clipL = Math.max(0, left);
        clipT = Math.max(0, top);
        clipR = Math.min(w, right);
        clipB = Math.min(h, bottom);
    }

    void unclip() {
        clipL = 0;
        clipT = 0;
        clipR = w;
        clipB = h;
    }

    /** Covers a cell at least this much: overlapping shapes of one layer do not add up. */
    void cover(int x, int y, float c) {
        if (x < clipL || y < clipT || x >= clipR || y >= clipB || c <= 0F) {
            return;
        }
        int at = y * w + x;
        if (c > cov[at]) {
            cov[at] = c > 1F ? 1F : c;
        }
    }

    /** How much a cell is covered, 0 to 1. */
    float coverage(int x, int y) {
        return x < 0 || y < 0 || x >= w || y >= h ? 0F : cov[y * w + x];
    }

    /**
     * A segment with round ends, {@code r} cells either side of it, in cell units with 0 at the grid's corner. A cell
     * is covered by how far its centre is inside the edge, which is close to the true area for edges straighter than a
     * cell: the usual distance-based anti-aliasing.
     */
    void capsule(double ax, double ay, double bx, double by, double r) {
        int x0 = Math.max(clipL, (int) Math.floor(Math.min(ax, bx) - r - 1D));
        int x1 = Math.min(clipR - 1, (int) Math.ceil(Math.max(ax, bx) + r + 1D));
        int y0 = Math.max(clipT, (int) Math.floor(Math.min(ay, by) - r - 1D));
        int y1 = Math.min(clipB - 1, (int) Math.ceil(Math.max(ay, by) + r + 1D));
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        for (int y = y0; y <= y1; y++) {
            double py = y + 0.5D - ay;
            int at = y * w;
            for (int x = x0; x <= x1; x++) {
                double px = x + 0.5D - ax;
                double t = len2 <= 0D ? 0D : (px * dx + py * dy) / len2;
                t = t < 0D ? 0D : t > 1D ? 1D : t;
                double ex = px - dx * t;
                double ey = py - dy * t;
                double c = r + 0.5D - Math.sqrt(ex * ex + ey * ey);
                if (c > 0D) {
                    float f = c >= 1D ? 1F : (float) c;
                    if (f > cov[at + x]) {
                        cov[at + x] = f;
                    }
                }
            }
        }
    }

    /** A round dot of radius {@code r} cells. */
    void disc(double cx, double cy, double r) {
        capsule(cx, cy, cx, cy, r);
    }

    /** One column covered from {@code top} to {@code bottom}, in cells, each cell by its share, times {@code strength}. */
    void column(int x, double top, double bottom, float strength) {
        if (x < clipL || x >= clipR || bottom <= top) {
            return;
        }
        int y0 = Math.max(clipT, (int) Math.floor(top));
        int y1 = Math.min(clipB - 1, (int) Math.ceil(bottom) - 1);
        for (int y = y0; y <= y1; y++) {
            double share = Math.min(bottom, y + 1D) - Math.max(top, y);
            if (share > 0D) {
                cover(x, y, (float) Math.min(1D, share) * strength);
            }
        }
    }

    /** A coverage rounded to this layer's levels. */
    float quantize(float c) {
        if (c <= 0F) {
            return 0F;
        }
        if (c >= 1F) {
            return 1F;
        }
        return Math.round(c * levels) / (float) levels;
    }

    /** The colour with its alpha times a coverage, rounded to this layer's levels; 0 when nothing shows. */
    int tint(int color, float c) {
        float q = quantize(c);
        if (q <= 0F) {
            return 0;
        }
        int a = Math.round((color >>> 24) * q);
        return a <= 0 ? 0 : (a << 24) | (color & 0xFFFFFF);
    }

    /** Sets a cell's colour directly, for a caller that works out its own picture. 0 is empty. */
    void set(int x, int y, int argb) {
        grid[y * w + x] = argb;
    }

    /** Colours the covered cells, then makes the rectangles. */
    void paint(int color) {
        int cells = w * h;
        for (int i = 0; i < cells; i++) {
            float c = cov[i];
            grid[i] = c > 0F ? tint(color, c) : 0;
        }
        merge();
    }

    // ---- rectangles ----

    /**
     * Turns the grid into rectangles: runs of one colour along rows, equal runs of rows joined, and the same across
     * columns; the one that gives fewer is kept. Lines that are steep merge better by column, areas by row.
     */
    void merge() {
        int byRows = build(false);
        int[] swap = runs;
        runs = other;
        other = swap;
        int byCols = build(true);
        if (byCols < byRows) {
            swap = runs;
            runs = other;
            other = swap;
            runCount = byCols;
        } else {
            runCount = byRows;
        }
    }

    /** Runs along one direction, joined across the other, written to {@code other}. Returns how many. */
    private int build(boolean byColumn) {
        int lines = byColumn ? w : h;
        int along = byColumn ? h : w;
        if (prevRow.length < along + 1) {
            prevRow = new int[along + 1];
            curRow = new int[along + 1];
        }
        int count = 0;
        int prevCount = 0;
        int[] prev = prevRow;
        int[] cur = curRow;
        int[] out = other;
        for (int line = 0; line < lines; line++) {
            int curCount = 0;
            int walk = 0;
            int i = 0;
            while (i < along) {
                int ink = byColumn ? grid[i * w + line] : grid[line * w + i];
                if (ink == 0) {
                    i++;
                    continue;
                }
                int from = i;
                do {
                    i++;
                } while (i < along && (byColumn ? grid[i * w + line] : grid[line * w + i]) == ink);
                while (walk < prevCount && out[prev[walk] * RUN] < from) {
                    walk++;
                }
                int same = -1;
                if (walk < prevCount) {
                    int at = prev[walk] * RUN;
                    if (out[at] == from && out[at + 2] == i && out[at + 4] == ink) {
                        same = prev[walk];
                    }
                }
                if (same >= 0) {
                    out[same * RUN + 3] = line + 1;
                    cur[curCount++] = same;
                } else {
                    if ((count + 1) * RUN > out.length) {
                        out = Arrays.copyOf(out, out.length * 2);
                    }
                    int at = count * RUN;
                    out[at] = from;
                    out[at + 1] = line;
                    out[at + 2] = i;
                    out[at + 3] = line + 1;
                    out[at + 4] = ink;
                    cur[curCount++] = count++;
                }
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
            prevCount = curCount;
        }
        prevRow = prev;
        curRow = cur;
        if (byColumn) {
            // Stored as (along, line): swap to (x, y).
            for (int k = 0, at = 0; k < count; k++, at += RUN) {
                int a0 = out[at];
                int a1 = out[at + 2];
                out[at] = out[at + 1];
                out[at + 2] = out[at + 3];
                out[at + 1] = a0;
                out[at + 3] = a1;
            }
        }
        other = out;
        return count;
    }

    /** Rectangles {@link #emit} draws. */
    int rects() {
        return runCount;
    }

    /** Forgets the rectangles. */
    void clear() {
        runCount = 0;
    }

    /** Draws the layer, its top left at a GUI point. */
    void emit(Draw d, int x, int y) {
        emit(d, x, y, 255);
    }

    /** Draws the layer with its alpha times {@code alpha} of 255, for a disabled look. */
    void emit(Draw d, int x, int y, int alpha) {
        if (runCount == 0) {
            return;
        }
        boolean scaled = sub > 1;
        if (scaled) {
            d.push(x, y, 1F / sub);
        }
        int ox = scaled ? 0 : x;
        int oy = scaled ? 0 : y;
        int[] r = runs;
        for (int i = 0, at = 0; i < runCount; i++, at += RUN) {
            int c = r[at + 4];
            if (alpha < 255) {
                c = (((c >>> 24) * alpha / 255) << 24) | (c & 0xFFFFFF);
            }
            d.fill(ox + r[at], oy + r[at + 1], ox + r[at + 2], oy + r[at + 3], c);
        }
        if (scaled) {
            d.pop();
        }
    }

    /** Draws rectangles kept elsewhere in the same {@code x0, y0, x1, y1, colour} form, at {@code sub} cells a unit. */
    static void emitRuns(Draw d, int[] r, int count, int x, int y, int sub, int alpha) {
        boolean scaled = sub > 1;
        if (scaled) {
            d.push(x, y, 1F / sub);
        }
        int ox = scaled ? 0 : x;
        int oy = scaled ? 0 : y;
        for (int i = 0, at = 0; i < count; i++, at += RUN) {
            int c = r[at + 4];
            if (alpha < 255) {
                c = (((c >>> 24) * alpha / 255) << 24) | (c & 0xFFFFFF);
            }
            d.fill(ox + r[at], oy + r[at + 1], ox + r[at + 2], oy + r[at + 3], c);
        }
        if (scaled) {
            d.pop();
        }
    }

    /** Mixes two colours with their alpha, {@code t} of the way from a to b. */
    static int lerp(int a, int b, float t) {
        if (t <= 0F) {
            return a;
        }
        if (t >= 1F) {
            return b;
        }
        int aa = a >>> 24;
        int ba = b >>> 24;
        int oa = Math.round(aa + (ba - aa) * t);
        int or = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int og = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int ob = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return oa << 24 | or << 16 | og << 8 | ob;
    }
}
