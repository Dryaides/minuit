package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;

/**
 * The scroll state and bar of one scrolling area: the offset, the wheel, the thumb's maths, paging on a press beside
 * the thumb and the thumb's drag. Lengths are in any one unit, pixels or rows. The bar is drawn by {@link FrameStyle#scroller}.
 */
final class ScrollBar {
    static final int WIDTH = ScrollComponent.BAR;
    private static final int MIN_THUMB = 10;
    /** Rows one wheel notch moves in every list-like area. */
    static final int WHEEL_ROWS = 1;

    /** The bar's x when its drawn track's outer edge lies on {@code right}, the content column's right edge. */
    static int x(FrameStyle frame, int right) {
        return right - WIDTH + frame.scrollerGap();
    }

    /** What the bar takes from the content column's width, one pixel of gap before its track included. */
    static int room(FrameStyle frame) {
        return WIDTH + 1 - frame.scrollerGap();
    }

    /** The bar's x in a well or a field: its content column ends one pixel of face inside the 1 px border. */
    static int wellX(FrameStyle frame, int x, int w) {
        return x(frame, x + w - 2);
    }

    /** What the bar takes from a well's inner width, when the rows run up to the border without it. */
    static int wellRoom(FrameStyle frame) {
        return room(frame) + 1;
    }

    private int total;
    private int view;
    private int offset;
    /** The mouse y on the thumb when a drag started, or -1. */
    private double grab = -1;
    private int grabOffset;
    private int grabTrack;

    /** The content's length and the visible length; the offset stays within them. */
    void content(int total, int view) {
        this.total = Math.max(0, total);
        this.view = Math.max(0, view);
        offset(this.offset);
    }

    int offset() {
        return this.offset;
    }

    void offset(int value) {
        this.offset = Maths.clamp(value, 0, max());
    }

    int max() {
        return Math.max(0, this.total - this.view);
    }

    /** Moves by {@code amount} notches of {@code step}; true when it moved. */
    boolean wheel(double amount, int step) {
        int before = this.offset;
        offset(this.offset - (int) Math.round(amount * step));
        return this.offset != before;
    }

    /** The thumb's length on a track {@code h} long. */
    int thumbLength(int h) {
        return Math.min(h, Math.max(MIN_THUMB, h * this.view / Math.max(1, this.total)));
    }

    /** The thumb's top on a track from {@code y} over {@code h}. */
    int thumbTop(int y, int h) {
        int max = max();
        return y + (max == 0 ? 0 : (h - thumbLength(h)) * this.offset / max);
    }

    /** True when (mx, my) is on the thumb of a bar at x on a track from y over h. */
    boolean overThumb(double mx, double my, int x, int y, int h) {
        int top = thumbTop(y, h);
        return mx >= x && mx < x + WIDTH && my >= top && my < top + thumbLength(h);
    }

    /** A press on the bar's track from y over h: pages toward the click beside the thumb, then holds the thumb. */
    boolean press(double mx, double my, int x, int y, int h) {
        if (max() == 0 || mx < x || mx >= x + WIDTH) {
            return false;
        }
        int top = thumbTop(y, h);
        if (my < top || my >= top + thumbLength(h)) {
            offset(this.offset + (my < top ? -this.view : this.view));
        }
        this.grab = my;
        this.grabOffset = this.offset;
        this.grabTrack = h;
        return true;
    }

    /** Follows the mouse while the thumb is held; true while it is. */
    boolean drag(double my) {
        if (this.grab < 0) {
            return false;
        }
        int travel = this.grabTrack - thumbLength(this.grabTrack);
        if (travel > 0) {
            offset(this.grabOffset + (int) Math.round((my - this.grab) * max() / travel));
        }
        return true;
    }

    void release() {
        this.grab = -1;
    }

    boolean dragging() {
        return this.grab >= 0;
    }

    /** The bar at x, its track from y over h; nothing when all the content shows. */
    void draw(Draw d, Theme t, int x, int y, int h, boolean lit) {
        if (max() > 0) {
            t.frame().scroller(d, t.palette(), x, y, h, thumbTop(y, h), thumbLength(h), lit || dragging());
        }
    }
}
