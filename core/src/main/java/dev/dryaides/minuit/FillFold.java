package dev.dryaides.minuit;

import java.util.Arrays;
import java.util.List;

/**
 * A shape drawn as stacked layers of fills (a shadow, a face, edge rows over the face) sent as fewer rects with the
 * same pixels. A rect may run under a later opaque layer, so one rect covers what the layered drawing sent as one
 * fill per row. On 1.12.2 to 1.20.x each fill is one draw call, so the count is the frame's cost there.
 *
 * <p>The fold depends only on the geometry and on which layers are translucent, so it is worked out once and kept;
 * a frame looks it up and sends its rects in the frame's colours. A translucent layer still lands exactly once on
 * every pixel where it showed, after the same layers as before, so every blend comes out the same.
 */
final class FillFold {
    /** A shape that draws itself at 0, 0 with each layer's index as its colour, the layers in index order. */
    interface Geometry {
        void draw(Draw d, int w, int h, int extra);
    }

    /** Layers per shape: a pixel's layer stack packs one 4-bit entry per layer into a long. */
    static final int MAX_LAYERS = 15;
    private static final int LIMIT = 0xFFF;
    private static final int SLOTS = 1024;
    private static final long[] KEYS = new long[SLOTS];
    private static final int[][] PLANS = new int[SLOTS][];
    private static int used;
    private static int plansBuilt;
    private static final ThreadLocal<int[]> COLORS = ThreadLocal.withInitial(() -> new int[MAX_LAYERS]);

    private FillFold() {
    }

    /** This thread's colour slots for one {@link #draw}, so a frame allocates nothing. */
    static int[] colors() {
        return COLORS.get();
    }

    /**
     * Draws {@code shape} at x, y, layer i in {@code colors[i]}. {@code kind} tells shapes apart in the cache (below
     * 64); {@code extra} is whatever else the geometry depends on (below 4096).
     */
    static void draw(Draw d, int kind, Geometry shape, int x, int y, int w, int h, int extra, int[] colors, int layers) {
        if (w < 0 || h < 0 || w > LIMIT || h > LIMIT || extra < 0 || extra > LIMIT) {
            shape.draw(new Mapped(d, x, y, colors), w, h, extra);
            return;
        }
        int mask = 0;
        for (int i = 0; i < layers; i++) {
            if (colors[i] >>> 24 != 0xFF) {
                mask |= 1 << i;
            }
        }
        long key = Long.MIN_VALUE | (long) kind << 52 | (long) w << 40 | (long) h << 28 | (long) extra << 16 | mask;
        int[] plan = plan(key, shape, w, h, extra, mask);
        if (plan == null) {
            shape.draw(new Mapped(d, x, y, colors), w, h, extra);
            return;
        }
        for (int i = 0; i < plan.length; i += 5) {
            d.fill(x + plan[i], y + plan[i + 1], x + plan[i + 2], y + plan[i + 3], colors[plan[i + 4]]);
        }
    }

    private static synchronized int[] plan(long key, Geometry shape, int w, int h, int extra, int mask) {
        int slot = slot(key);
        while (KEYS[slot] != 0) {
            if (KEYS[slot] == key) {
                if (PLANS[slot] == null) {
                    PLANS[slot] = build(shape, w, h, extra, mask);
                }
                return PLANS[slot];
            }
            slot = (slot + 1) & (SLOTS - 1);
        }
        if (used >= SLOTS / 2) {
            Arrays.fill(KEYS, 0L);
            Arrays.fill(PLANS, null);
            used = 0;
            slot = slot(key);
        }
        // A size seen once (an animation frame) draws raw; the plan is built when the size comes back.
        KEYS[slot] = key;
        used++;
        return null;
    }

    private static int[] build(Geometry shape, int w, int h, int extra, int mask) {
        Recorder rec = new Recorder();
        shape.draw(rec, w, h, extra);
        plansBuilt++;
        return fold(rec.fills(), mask);
    }

    /** Plans built so far, for tests. */
    static synchronized int plansBuilt() {
        return plansBuilt;
    }

    private static int slot(long key) {
        return (int) ((key * 0x9E3779B97F4A7C15L) >>> 54) & (SLOTS - 1);
    }

    /**
     * Folds recorded fills (x0, y0, x1, y1, layer per fill) into rects with the same pixels; the fills themselves
     * when the fold would not be smaller or does not check out.
     */
    static int[] fold(int[] fills, int mask) {
        if (fills.length == 0) {
            return fills;
        }
        int[] xs = edges(fills, 0);
        int[] ys = edges(fills, 1);
        long[][] fine;
        try {
            fine = stacks(fills, xs, ys, mask);
        } catch (IllegalStateException deep) {
            return fills;
        }
        // Coarse grid: neighbouring columns (rows) with the same stacks everywhere are one cell.
        int[] cx = distinct(fine, xs.length - 1, true);
        int[] cy = distinct(fine, ys.length - 1, false);
        int cw = cx.length;
        int ch = cy.length;
        long[][] cell = new long[ch][cw];
        int[][] area = new int[ch][cw];
        for (int j = 0; j < ch; j++) {
            int y1 = j + 1 < ch ? ys[cy[j + 1]] : ys[ys.length - 1];
            for (int i = 0; i < cw; i++) {
                int x1 = i + 1 < cw ? xs[cx[i + 1]] : xs[xs.length - 1];
                cell[j][i] = fine[cy[j]][cx[i]];
                area[j][i] = (x1 - xs[cx[i]]) * (y1 - ys[cy[j]]);
            }
        }
        int top = 0;
        for (int f = 4; f < fills.length; f += 5) {
            top = Math.max(top, fills[f]);
        }
        int[] out = new int[fills.length];
        int n = 0;
        boolean[][] req = new boolean[ch][cw];
        boolean[][] allow = new boolean[ch][cw];
        boolean[][] done = new boolean[ch][cw];
        for (int layer = 0; layer <= top; layer++) {
            boolean seeThrough = (mask >> layer & 1) != 0;
            boolean any = false;
            for (int j = 0; j < ch; j++) {
                for (int i = 0; i < cw; i++) {
                    long s = cell[j][i];
                    int base = (int) (s & 0xF) - 1;
                    req[j][i] = holds(s, layer);
                    allow[j][i] = req[j][i] || base > layer && (mask >> base & 1) == 0;
                    done[j][i] = false;
                    any |= req[j][i];
                }
            }
            while (any) {
                int i0 = -1;
                int j0 = -1;
                for (int j = 0; j < ch && i0 < 0; j++) {
                    for (int i = 0; i < cw; i++) {
                        if (req[j][i] && !done[j][i]) {
                            i0 = i;
                            j0 = j;
                            break;
                        }
                    }
                }
                if (i0 < 0) {
                    break;
                }
                long best = -1;
                int ba = i0;
                int bb = i0;
                int bt = j0;
                int bu = j0;
                for (int a = i0; a >= 0 && open(req, allow, done, seeThrough, a, j0); a--) {
                    for (int b = i0; b < cw && open(req, allow, done, seeThrough, b, j0); b++) {
                        int t = j0;
                        while (t > 0 && openRow(req, allow, done, seeThrough, t - 1, a, b)) {
                            t--;
                        }
                        int u = j0;
                        while (u < ch - 1 && openRow(req, allow, done, seeThrough, u + 1, a, b)) {
                            u++;
                        }
                        long gain = 0;
                        for (int j = t; j <= u; j++) {
                            for (int i = a; i <= b; i++) {
                                if (req[j][i] && !done[j][i]) {
                                    gain += area[j][i];
                                }
                            }
                        }
                        if (gain > best) {
                            best = gain;
                            ba = a;
                            bb = b;
                            bt = t;
                            bu = u;
                        }
                    }
                }
                for (int j = bt; j <= bu; j++) {
                    for (int i = ba; i <= bb; i++) {
                        if (req[j][i]) {
                            done[j][i] = true;
                        }
                    }
                }
                if (n + 5 > out.length) {
                    return fills;
                }
                out[n++] = xs[cx[ba]];
                out[n++] = ys[cy[bt]];
                out[n++] = bb + 1 < cw ? xs[cx[bb + 1]] : xs[xs.length - 1];
                out[n++] = bu + 1 < ch ? ys[cy[bu + 1]] : ys[ys.length - 1];
                out[n++] = layer;
            }
        }
        int[] plan = Arrays.copyOf(out, n);
        try {
            return n < fills.length && Arrays.deepEquals(fine, stacks(plan, xs, ys, mask)) ? plan : fills;
        } catch (IllegalStateException deep) {
            return fills;
        }
    }

    private static boolean open(boolean[][] req, boolean[][] allow, boolean[][] done, boolean seeThrough, int i, int j) {
        return allow[j][i] && !(seeThrough && req[j][i] && done[j][i]);
    }

    private static boolean openRow(boolean[][] req, boolean[][] allow, boolean[][] done, boolean seeThrough, int j, int a, int b) {
        for (int i = a; i <= b; i++) {
            if (!open(req, allow, done, seeThrough, i, j)) {
                return false;
            }
        }
        return true;
    }

    private static boolean holds(long stack, int layer) {
        for (long s = stack; s != 0; s >>>= 4) {
            if ((s & 0xF) == layer + 1) {
                return true;
            }
        }
        return false;
    }

    /** The sorted distinct x (axis 0) or y (axis 1) bounds of the fills. */
    private static int[] edges(int[] fills, int axis) {
        int[] v = new int[fills.length / 5 * 2];
        for (int f = 0, k = 0; f < fills.length; f += 5) {
            v[k++] = fills[f + axis];
            v[k++] = fills[f + axis + 2];
        }
        Arrays.sort(v);
        int n = 0;
        for (int i = 0; i < v.length; i++) {
            if (n == 0 || v[i] != v[n - 1]) {
                v[n++] = v[i];
            }
        }
        return Arrays.copyOf(v, n);
    }

    /**
     * Each cell's layer stack after the fills, oldest first, 4 bits per layer: an opaque layer replaces the stack, a
     * translucent one goes on top of it.
     */
    private static long[][] stacks(int[] fills, int[] xs, int[] ys, int mask) {
        long[][] s = new long[ys.length - 1][xs.length - 1];
        for (int f = 0; f < fills.length; f += 5) {
            int layer = fills[f + 4];
            int i0 = Arrays.binarySearch(xs, fills[f]);
            int i1 = Arrays.binarySearch(xs, fills[f + 2]);
            int j0 = Arrays.binarySearch(ys, fills[f + 1]);
            int j1 = Arrays.binarySearch(ys, fills[f + 3]);
            if (layer < 0 || layer >= MAX_LAYERS || i0 < 0 || i1 < 0 || j0 < 0 || j1 < 0) {
                throw new IllegalStateException("fill off the grid");
            }
            boolean seeThrough = (mask >> layer & 1) != 0;
            for (int j = j0; j < j1; j++) {
                for (int i = i0; i < i1; i++) {
                    if (!seeThrough) {
                        s[j][i] = layer + 1;
                        continue;
                    }
                    int depth = 0;
                    while (depth < 16 && (s[j][i] >>> (4 * depth) & 0xF) != 0) {
                        depth++;
                    }
                    if (depth == 16) {
                        throw new IllegalStateException("stack too deep");
                    }
                    s[j][i] |= (long) (layer + 1) << (4 * depth);
                }
            }
        }
        return s;
    }

    /** The first column (columns) or row of each run of identical ones. */
    private static int[] distinct(long[][] s, int count, boolean columns) {
        int[] keep = new int[count];
        int n = 0;
        for (int k = 0; k < count; k++) {
            boolean same = k > 0;
            if (same) {
                if (columns) {
                    for (long[] row : s) {
                        if (row[k] != row[k - 1]) {
                            same = false;
                            break;
                        }
                    }
                } else {
                    same = Arrays.equals(s[k], s[k - 1]);
                }
            }
            if (!same) {
                keep[n++] = k;
            }
        }
        return Arrays.copyOf(keep, n);
    }

    /** Records fills with their layer index for a fold. */
    private static final class Recorder extends Draw {
        private int[] fills = new int[80];
        private int size;

        @Override
        protected void rect(int x0, int y0, int x1, int y1, int argb) {
            if (size + 5 > fills.length) {
                fills = Arrays.copyOf(fills, fills.length * 2);
            }
            fills[size++] = x0;
            fills[size++] = y0;
            fills[size++] = x1;
            fills[size++] = y1;
            fills[size++] = argb;
        }

        int[] fills() {
            return Arrays.copyOf(fills, size);
        }

        @Override protected void string(String text, int x, int y, int argb) { }
        @Override public int width(String text) { return 0; }
        @Override public String cut(String text, int maxWidth) { return text; }
        @Override public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) { }
        @Override protected void pushPose(float x, float y, float scale) { }
        @Override protected void popPose() { }
        @Override public void scissor(int x0, int y0, int x1, int y1) { }
        @Override public void endScissor() { }
        @Override public void tooltip(List<String> lines, int x, int y) { }
    }

    /** Sends a geometry's fills straight to a Draw in the given colours, for a shape too large to key. */
    private static final class Mapped extends Draw {
        private final Draw out;
        private final int x;
        private final int y;
        private final int[] colors;

        Mapped(Draw out, int x, int y, int[] colors) {
            this.out = out;
            this.x = x;
            this.y = y;
            this.colors = colors;
        }

        @Override
        protected void rect(int x0, int y0, int x1, int y1, int argb) {
            out.fill(x + x0, y + y0, x + x1, y + y1, colors[argb]);
        }

        @Override protected void string(String text, int x, int y, int argb) { }
        @Override public int width(String text) { return 0; }
        @Override public String cut(String text, int maxWidth) { return text; }
        @Override public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) { }
        @Override protected void pushPose(float x, float y, float scale) { }
        @Override protected void popPose() { }
        @Override public void scissor(int x0, int y0, int x1, int y1) { }
        @Override public void endScissor() { }
        @Override public void tooltip(List<String> lines, int x, int y) { }
    }
}
