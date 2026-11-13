package dev.dryaides.minuit;

/**
 * Grey shapes that hold the place of something still loading, so that the screen does not jump when it
 * arrives. A lighter band crosses them in steps of two pixels, not smoothly; under reduced motion, or with
 * {@code active = false}, the shapes rest without it. Skeletons on one screen move together, as the band
 * follows the clock. Set {@code visible = false} once the thing is there.
 *
 * <pre>{@code
 * SkeletonComponent loading = add(SkeletonComponent.row(at(new Box("player", 10, 10, 160, 22))));
 * add(SkeletonComponent.lines(at(new Box("about", 10, 38, 160, 40)), 4));
 * }</pre>
 *
 * Shapes never leave the box: a box too small for all the lines shows the lines that fit whole. A head is
 * square, as a player's head is. It takes no input and no focus.
 */
public class SkeletonComponent extends BaseComponent {
    private enum Kind { LINES, BLOCK, HEAD, ROW, CARD }

    private static final int BONE_LINE = 6;
    private static final int LINE_STEP = 10;
    private static final int LINE_TOP = 1;
    private static final int GAP = 4;
    private static final int PERIOD_MS = 1400;
    private static final int BAND = 18;
    private static final int EDGE = 6;
    private static final int STEP = 2;
    private static final int BASE_ALPHA = 0x26;
    private static final int BAND_ALPHA = 0x24;
    private static final int EDGE_ALPHA = 0x10;
    private static final int DISABLED_ALPHA = 0x14;
    // The lines end unevenly, as text does.
    private static final int[] WIDTHS = {100, 94, 98, 88, 96, 91};
    private static final int LAST_LINE = 62;

    private final Kind kind;
    private final int wanted;
    private boolean shimmer = true;

    private int[] bones = new int[0];
    private int laidW = -1;
    private int laidH = -1;

    private SkeletonComponent(Box box, Kind kind, int wanted) {
        super(box);
        this.kind = kind;
        this.wanted = wanted;
    }

    /** Lines of text, the last one shorter. Fewer when the box holds fewer. */
    public static SkeletonComponent lines(Box box, int count) {
        return new SkeletonComponent(box, Kind.LINES, Math.max(0, count));
    }

    /** One block: a picture, a button, a chart. */
    public static SkeletonComponent block(Box box) {
        return new SkeletonComponent(box, Kind.BLOCK, 1);
    }

    /** A square head, centred in the box. */
    public static SkeletonComponent head(Box box) {
        return new SkeletonComponent(box, Kind.HEAD, 1);
    }

    /** A head with two lines beside it, for a row of a list. */
    public static SkeletonComponent row(Box box) {
        return new SkeletonComponent(box, Kind.ROW, 2);
    }

    /** A picture with lines under it, for a card. */
    public static SkeletonComponent card(Box box) {
        return new SkeletonComponent(box, Kind.CARD, 2);
    }

    /** False to keep the shapes still in every case. On by default. */
    public SkeletonComponent shimmer(boolean shimmer) {
        this.shimmer = shimmer;
        return this;
    }

    public SkeletonComponent active(boolean active) {
        this.active = active;
        return this;
    }

    /** How many shapes the box holds. */
    int boneCount() {
        layout();
        return bones.length / 4;
    }

    /** One shape's place, for a script or a test. */
    Box boneBox(int index) {
        layout();
        int at = index * 4;
        return new Box("bone" + index, x + bones[at], y + bones[at + 1], bones[at + 2], bones[at + 3]);
    }

    /** True when the band is moving: not with reduced motion, not with {@code active = false}. */
    public boolean shimmering() {
        return shimmer && active && !reducedMotion();
    }

    /** The band's left edge from the box's left; negative while it is off the box. */
    public int bandAt() {
        int travel = Math.max(1, w) + 2 * (BAND + 2 * EDGE);
        long now = clock().getAsLong();
        int at = (int) ((now % PERIOD_MS) * travel / PERIOD_MS) - BAND - 2 * EDGE;
        return Math.floorDiv(at, STEP) * STEP;
    }

    // ---- layout ----

    private void layout() {
        if (w == laidW && h == laidH) {
            return;
        }
        laidW = w;
        laidH = h;
        switch (kind) {
            case BLOCK -> bones = new int[] {0, 0, w, h};
            case HEAD -> {
                int side = Math.min(w, h);
                bones = new int[] {(w - side) / 2, (h - side) / 2, side, side};
            }
            case ROW -> layoutRow();
            case CARD -> layoutCard();
            default -> bones = linesIn(0, 0, w, h, wanted);
        }
    }

    private void layoutRow() {
        int side = Math.min(h, w);
        int left = side + GAP;
        int room = w - left;
        if (room < 8 || h < BONE_LINE + 2) {
            bones = new int[] {0, (h - side) / 2, side, side};
            return;
        }
        int block = lineBlock(wanted);
        int[] text = linesIn(left, Math.max(0, (h - block) / 2), room, Math.min(h, block), wanted);
        bones = new int[4 + text.length];
        bones[1] = (h - side) / 2;
        bones[2] = side;
        bones[3] = side;
        System.arraycopy(text, 0, bones, 4, text.length);
    }

    private void layoutCard() {
        int textH = lineBlock(wanted);
        int pictureH = h - textH - GAP;
        if (pictureH < 8) {
            bones = new int[] {0, 0, w, h};
            return;
        }
        int[] text = linesIn(0, pictureH + GAP, w, textH, wanted);
        bones = new int[4 + text.length];
        bones[2] = w;
        bones[3] = pictureH;
        System.arraycopy(text, 0, bones, 4, text.length);
    }

    private static int lineBlock(int count) {
        return count <= 0 ? 0 : (count - 1) * LINE_STEP + BONE_LINE + 2 * LINE_TOP;
    }

    /** Lines inside a span of this height that starts at {@code top}. */
    private static int[] linesIn(int left, int top, int width, int height, int count) {
        int fit = height < BONE_LINE + LINE_TOP ? 0 : 1 + (height - BONE_LINE - LINE_TOP) / LINE_STEP;
        int shown = width <= 0 ? 0 : Math.min(count, fit);
        int[] out = new int[shown * 4];
        for (int i = 0; i < shown; i++) {
            int percent = i == shown - 1 && shown > 1 ? LAST_LINE : WIDTHS[i % WIDTHS.length];
            int at = i * 4;
            out[at] = left;
            out[at + 1] = top + LINE_TOP + i * LINE_STEP;
            out[at + 2] = Math.max(1, width * percent / 100);
            out[at + 3] = BONE_LINE;
        }
        return out;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        if (w <= 0 || h <= 0) {
            return;
        }
        layout();
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        int text = p.text();
        int base = Shapes.alpha(text, active ? BASE_ALPHA : DISABLED_ALPHA);
        boolean band = shimmering();
        int at = band ? x + bandAt() : 0;
        for (int i = 0; i + 3 < bones.length; i += 4) {
            int bx = x + bones[i];
            int by = y + bones[i + 1];
            int bw = bones[i + 2];
            int bh = bones[i + 3];
            if (bw <= 0 || bh <= 0) {
                continue;
            }
            frame.plate(d, p, bx, by, bw, bh, base, 0);
            if (band) {
                glint(d, frame, p, bx, by, bw, bh, at, at + EDGE, Shapes.alpha(text, EDGE_ALPHA));
                glint(d, frame, p, bx, by, bw, bh, at + EDGE, at + EDGE + BAND, Shapes.alpha(text, BAND_ALPHA));
                glint(d, frame, p, bx, by, bw, bh, at + EDGE + BAND, at + EDGE + BAND + EDGE, Shapes.alpha(text, EDGE_ALPHA));
            }
        }
    }

    /** A bone's shape cut to a span of columns. */
    private static void glint(Draw d, FrameStyle frame, Palette p, int bx, int by, int bw, int bh, int from, int to, int color) {
        int l = Math.max(bx, from);
        int r = Math.min(bx + bw, to);
        if (l < r) {
            d.scissor(l, by, r, by + bh);
            frame.plate(d, p, bx, by, bw, bh, color, 0);
            d.endScissor();
        }
    }
}
