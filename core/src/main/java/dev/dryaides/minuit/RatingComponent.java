package dev.dryaides.minuit;

import java.util.function.DoubleConsumer;

/**
 * A row of stars or hearts that rates something: a map, a server, a build. The mouse shows the rating it would
 * give before the click; Left and Right change it one step, Home and End go to the ends. Read only, it shows a
 * rating, half stars included.
 *
 * <pre>{@code
 * add(new RatingComponent(at(new Box("rate", 10, 10, 0, RatingComponent.HEIGHT)), 5, 3, v -> vote(v)));
 * add(new RatingComponent(at(new Box("avg", 10, 30, 0, RatingComponent.HEIGHT)), 5, 4.5, v -> { })
 *         .halves(true).readOnly(true));
 * }</pre>
 *
 * <p>A box of width 0 takes the width of its marks. Filled marks take the accent, empty ones the track; a newly
 * filled mark fades in over {@link #FADE_MS}, at once with {@link MotionStyle#NONE}.
 */
public class RatingComponent extends BaseComponent {

    /** The shape of each mark. */
    public enum Mark { STAR, HEART }

    public static final int HEIGHT = 12;
    public static final int FADE_MS = 140;

    private static final int GAP = 2;
    private static final String[] STAR = {
            ".....X.....",
            ".....X.....",
            "....XXX....",
            "....XXX....",
            "XXXXXXXXXXX",
            ".XXXXXXXXX.",
            "..XXXXXXX..",
            "..XXXXXXX..",
            ".XXXX.XXXX.",
            ".XX.....XX.",
    };
    private static final String[] HEART = {
            ".XXX...XXX.",
            "XXXXX.XXXXX",
            "XXXXXXXXXXX",
            "XXXXXXXXXXX",
            "XXXXXXXXXXX",
            ".XXXXXXXXX.",
            "..XXXXXXX..",
            "...XXXXX...",
            "....XXX....",
            ".....X.....",
    };
    private static final int CELL = STAR[0].length();
    private static final int ART_H = STAR.length;
    /** The column a half mark is cut after: the centre one belongs to the filled half. */
    private static final int HALF_COLS = CELL / 2 + 1;

    private static final Glyph[][] PARTS = {parts(STAR), parts(HEART)};

    private final int givenW;
    private final int count;
    private final DoubleConsumer onChange;
    private double value;
    private boolean halves;
    private boolean readOnly;
    private boolean clearable;
    private Mark mark = Mark.STAR;
    /** The value before the last change and when it happened, for the fade; -1 when nothing fades. */
    private double fadeFrom;
    private long changedAt = -1;

    public RatingComponent(Box box, int count, double value, DoubleConsumer onChange) {
        super(box);
        this.givenW = box.w();
        this.count = Math.max(1, count);
        this.onChange = onChange == null ? v -> { } : onChange;
        this.value = clamp(value);
    }

    /** Lets a rating stop on half marks: a click on a mark's left half, or a key step of a half. */
    public RatingComponent halves(boolean halves) {
        this.halves = halves;
        return this;
    }

    /** Shows the rating and takes no input: an average, a difficulty. */
    public RatingComponent readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    /** Lets a click on the rating already given take it back to 0. */
    public RatingComponent clearable(boolean clearable) {
        this.clearable = clearable;
        return this;
    }

    public RatingComponent mark(Mark mark) {
        this.mark = mark == null ? Mark.STAR : mark;
        return this;
    }

    public RatingComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public RatingComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    public double value() {
        return this.value;
    }

    public int count() {
        return this.count;
    }

    /**
     * Sets the rating from outside, with no fade and no call to the listener. It shows as given, an average of
     * 3.7 as three marks and a half with {@link #halves}; the keys and the mouse then move it by whole steps.
     */
    public RatingComponent value(double value) {
        this.value = clamp(value);
        this.changedAt = -1;
        return this;
    }

    private double step() {
        return this.halves ? 0.5D : 1D;
    }

    private double snap(double v) {
        double s = step();
        return clamp(Math.round(v / s) * s);
    }

    private double clamp(double v) {
        return Double.isNaN(v) ? 0D : Math.max(0D, Math.min(this.count, v));
    }

    public int naturalWidth() {
        return this.count * CELL + (this.count - 1) * GAP;
    }

    private void fit() {
        if (this.givenW <= 0) {
            this.w = naturalWidth();
        }
    }

    @Override
    public boolean isOver(double mx, double my) {
        fit();
        return super.isOver(mx, my);
    }

    private boolean takesInput() {
        return this.active && !this.readOnly;
    }

    /** The rating a click at {@code mx} gives: the mark under it, its half on a half rating, the nearer mark across a gap. */
    double valueAt(double mx) {
        int rel = (int) Math.floor(mx) - this.x;
        int index = Math.max(0, Math.min(this.count - 1, rel / (CELL + GAP)));
        int inMark = rel - index * (CELL + GAP);
        if (this.halves && inMark < HALF_COLS) {
            return index + 0.5D;
        }
        return index + 1D;
    }

    private void change(double to) {
        double next = snap(to);
        if (next == this.value) {
            return;
        }
        this.fadeFrom = this.value;
        this.changedAt = clock().getAsLong();
        this.value = next;
        click();
        this.onChange.accept(next);
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        fit();
        Palette p = t.palette();
        boolean preview = hovered && takesInput();
        double shown = preview ? valueAt(mx) : shown(this.value);
        int full = this.active ? FormLook.solid(p.accent()) : FormLook.solid(p.disabledText());
        int fullEdge = this.active ? FormLook.solid(p.accentDeep()) : FormLook.disabledFace(p);
        int empty = this.active ? FormLook.solid(p.track()) : FormLook.disabledFace(p);
        int emptyEdge = this.active ? FormLook.edge(p) : FormLook.disabledFace(p);
        if (preview && shown != this.value) {
            full = Shapes.mix(empty, full, 0.6F);
            fullEdge = Shapes.mix(emptyEdge, fullEdge, 0.6F);
        }
        float fade = 1F;
        if (this.changedAt >= 0 && !preview) {
            long since = clock().getAsLong() - this.changedAt;
            fade = motion() == MotionStyle.NONE ? 1F : Math.min(1F, Math.max(0F, since / (float) FADE_MS));
            if (fade >= 1F) {
                this.changedAt = -1;
            }
        }
        Glyph[] parts = PARTS[this.mark.ordinal()];
        int top = this.y + (this.h - ART_H) / 2;
        for (int i = 0; i < this.count; i++) {
            int left = this.x + i * (CELL + GAP);
            float fill = fillOf(shown, i);
            float before = fade < 1F ? fillOf(shown(this.fadeFrom), i) : fill;
            int inner = fillColour(empty, full, before, fill, fade);
            int edge = fillColour(emptyEdge, fullEdge, before, fill, fade);
            if (fill == 0.5F || before == 0.5F && fade < 1F) {
                int leftInner = fillColour(empty, full, Math.min(1F, before * 2F), Math.min(1F, fill * 2F), fade);
                int leftEdge = fillColour(emptyEdge, fullEdge, Math.min(1F, before * 2F), Math.min(1F, fill * 2F), fade);
                int rightInner = fillColour(empty, full, Math.max(0F, before * 2F - 1F), Math.max(0F, fill * 2F - 1F), fade);
                int rightEdge = fillColour(emptyEdge, fullEdge, Math.max(0F, before * 2F - 1F), Math.max(0F, fill * 2F - 1F), fade);
                d.icon(parts[2], left, top, leftEdge);
                d.icon(parts[3], left, top, leftInner);
                d.icon(parts[4], left, top, rightEdge);
                d.icon(parts[5], left, top, rightInner);
            } else {
                d.icon(parts[0], left, top, edge);
                d.icon(parts[1], left, top, inner);
            }
        }
    }

    /** A rating as the marks show it: to the nearest half with {@link #halves}, else to the nearest whole mark. */
    private double shown(double v) {
        return this.halves ? Math.floor(v * 2D + 0.5D) / 2D : Math.floor(v + 0.5D);
    }

    /** How much of mark {@code i} a rating of {@code v} fills: 0, a half or 1. */
    private static float fillOf(double v, int i) {
        double left = v - i;
        return left >= 1D ? 1F : left >= 0.5D ? 0.5F : 0F;
    }

    /** The colour of a part that went from {@code before} to {@code now} filled, {@code fade} of the way there. */
    private static int fillColour(int empty, int full, float before, float now, float fade) {
        int from = before >= 1F ? full : empty;
        int to = now >= 1F ? full : empty;
        return from == to ? to : Shapes.mix(from, to, fade);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!takesInput() || !isOver(mx, my) || button != 0) {
            return false;
        }
        double at = valueAt(mx);
        change(this.clearable && at == this.value ? 0D : at);
        return true;
    }

    @Override
    protected boolean navigable() {
        return takesInput();
    }

    /** Left and Right move one step, a half with {@link #halves}; Home clears it, End gives every mark. */
    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!takesInput()) {
            return false;
        }
        double target = switch (key) {
            case LEFT -> this.value - step();
            case RIGHT -> this.value + step();
            case HOME -> 0D;
            case END -> this.count;
            default -> Double.NaN;
        };
        if (Double.isNaN(target)) {
            return false;
        }
        change(target);
        return true;
    }

    /**
     * The glyphs of a shape: its outline, its inside, then the same two cut to the left half (centre column
     * included) and to the right half.
     */
    private static Glyph[] parts(String[] art) {
        int rows = art.length;
        int cols = art[0].length();
        StringBuilder[] edge = new StringBuilder[rows];
        StringBuilder[] inner = new StringBuilder[rows];
        for (int r = 0; r < rows; r++) {
            edge[r] = new StringBuilder();
            inner[r] = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                boolean on = on(art, r, c);
                boolean border = on && !(on(art, r - 1, c) && on(art, r + 1, c) && on(art, r, c - 1) && on(art, r, c + 1));
                edge[r].append(border ? 'X' : '.');
                inner[r].append(on && !border ? 'X' : '.');
            }
        }
        return new Glyph[]{
                glyph(edge, 0, cols), glyph(inner, 0, cols),
                glyph(edge, 0, HALF_COLS), glyph(inner, 0, HALF_COLS),
                glyph(edge, HALF_COLS, cols), glyph(inner, HALF_COLS, cols)};
    }

    private static boolean on(String[] art, int r, int c) {
        return r >= 0 && r < art.length && c >= 0 && c < art[r].length() && art[r].charAt(c) == 'X';
    }

    /** The rows with every column outside [from, to) blanked, so the glyph keeps its full width and position. */
    private static Glyph glyph(StringBuilder[] rows, int from, int to) {
        String[] out = new String[rows.length];
        for (int r = 0; r < rows.length; r++) {
            StringBuilder line = new StringBuilder(rows[r]);
            for (int c = 0; c < line.length(); c++) {
                if (c < from || c >= to) {
                    line.setCharAt(c, '.');
                }
            }
            out[r] = line.toString();
        }
        return new Glyph(out);
    }
}
