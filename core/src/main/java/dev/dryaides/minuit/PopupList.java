package dev.dryaides.minuit;

/**
 * The floating list of a widget: placement beside an anchor, rows on whole-row scroll, the pointer's hover fade, the
 * keyboard's lit row and the scroll bar, drawn with {@link FrameStyle#popup} and {@link FrameStyle#row}. The owner keeps
 * its options and draws each row's content; this class holds no option and no text but the empty one.
 */
final class PopupList {
    static final int MARGIN = 2;
    private static final int GAP = 2;
    private static final int TEXT_LEFT = RowLook.POPUP_PAD;

    /** What a row shows; called for the rows in view only. */
    interface Rows {
        /** The highlight of a row, 0 for none; {@code fade} is the pointer's hover fade on that row. */
        int face(Palette p, int row, boolean hover, float fade);

        /** The row's content over its face; {@code face} is what {@link #face} returned. */
        void draw(Draw d, Theme t, int row, int x, int y, int w, int h, int face);
    }

    final int rowH;
    private final ScrollBar bar = new ScrollBar();
    private final RowLook.HoverFade hover = new RowLook.HoverFade();
    private int count;
    private int lit = -1;
    private boolean above;
    private Insets inset = new Insets(5, 5, 5, 5);
    private FrameStyle frame = FrameStyles.VANILLA;
    int x;
    int y;
    int w;
    int h;

    /**
     * Where a popup's content starts on each side: the frame's inner edge, one pixel of face, then one pixel for the
     * ring around a lit row, so the ring never touches the bevel. The scroll bar's outer edge sits on the same line.
     */
    static Insets inset(FrameStyle frame) {
        Insets in = frame.panelInsets();
        return new Insets(in.top() + 2, in.left() + 2, in.right() + 2, in.bottom() + 2);
    }

    PopupList(int rowH) {
        this.rowH = rowH;
    }

    /** The number of rows; the lit row and the scroll stay within it. */
    void count(int count) {
        this.count = Math.max(0, count);
        if (this.lit >= this.count) {
            this.lit = -1;
        }
        this.bar.content(this.count, viewRows());
    }

    int count() {
        return this.count;
    }

    int lit() {
        return this.lit;
    }

    void lit(int row) {
        this.lit = row >= 0 && row < this.count ? row : -1;
        reveal();
    }

    /** The first row in view. */
    int offset() {
        return this.bar.offset();
    }

    void offset(int row) {
        this.bar.offset(row);
    }

    boolean above() {
        return this.above;
    }

    /** Forgets the side and the scroll, for a list that opens anew. */
    void reset() {
        this.above = false;
        this.lit = -1;
        this.bar.offset(0);
        this.bar.release();
    }

    /**
     * Puts the list under the anchor (ax, ay, aw, ah), or above when that holds more of it, inside {@code area}. It
     * keeps its side while it fits. {@code width} 0 takes the anchor's.
     */
    void place(Theme t, int ax, int ay, int aw, int ah, Box area, int width, int minWidth, int maxRows) {
        this.frame = t.frame();
        this.inset = inset(this.frame);
        int top = area.y() + MARGIN;
        int bottom = area.bottom() - MARGIN;
        int roomBelow = bottom - (ay + ah + GAP);
        int roomAbove = ay - GAP - top;
        int wanted = Math.max(1, Math.min(maxRows, this.count));
        int fitHere = rowsFit(this.above ? roomAbove : roomBelow);
        int fitThere = rowsFit(this.above ? roomBelow : roomAbove);
        if (fitHere < wanted && fitThere > fitHere) {
            this.above = !this.above;
            fitHere = fitThere;
        }
        int rows = Math.max(1, Math.min(wanted, fitHere));
        this.h = rows * this.rowH + this.inset.top() + this.inset.bottom();
        this.y = this.above ? ay - GAP - this.h : ay + ah + GAP;
        this.y = Math.max(top, Math.min(this.y, bottom - this.h));
        int fitW = Math.max(1, area.w() - 2 * MARGIN);
        this.w = Math.min(fitW, Math.max(minWidth, width > 0 ? width : aw));
        this.x = Math.max(area.x() + MARGIN, Math.min(ax, area.right() - MARGIN - this.w));
        this.bar.content(this.count, viewRows());
    }

    private int rowsFit(int room) {
        return Math.max(0, (room - this.inset.top() - this.inset.bottom()) / this.rowH);
    }

    private int viewY() {
        return this.y + this.inset.top();
    }

    private int viewH() {
        return Math.max(0, this.h - this.inset.top() - this.inset.bottom());
    }

    private int viewRows() {
        return viewH() / this.rowH;
    }

    private boolean overflows() {
        return this.bar.max() > 0;
    }

    int rowX() {
        return this.x + this.inset.left();
    }

    int rowW() {
        return Math.max(0, this.w - this.inset.left() - this.inset.right() - (overflows() ? ScrollBar.room(this.frame) : 0));
    }

    private int barX() {
        return ScrollBar.x(this.frame, this.x + this.w - this.inset.right());
    }

    boolean contains(double mx, double my) {
        return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
    }

    /** The row under the pointer, or -1. */
    int rowAt(double mx, double my) {
        if (mx < rowX() || mx >= rowX() + rowW() || my < viewY() || my >= viewY() + viewH()) {
            return -1;
        }
        int row = ((int) Math.floor(my) - viewY()) / this.rowH + this.bar.offset();
        return row < this.count ? row : -1;
    }

    /** The top of a row on screen, wherever the scroll is. */
    int rowY(int row) {
        return viewY() + (row - this.bar.offset()) * this.rowH;
    }

    /** A press in the list: true when the scroll bar took it; otherwise the owner asks {@link #rowAt}. */
    boolean pressBar(double mx, double my) {
        return overflows() && this.bar.press(mx, my, barX(), viewY(), viewH());
    }

    boolean drag(double my) {
        return this.bar.drag(my);
    }

    boolean release() {
        boolean was = this.bar.dragging();
        this.bar.release();
        return was;
    }

    boolean dragging() {
        return this.bar.dragging();
    }

    boolean wheel(double amount) {
        return this.bar.wheel(amount, ScrollBar.WHEEL_ROWS);
    }

    /** Moves the lit row by {@code by}, wrapping; the first move from none lights the first or last row. */
    void move(int by) {
        if (this.count == 0) {
            return;
        }
        this.lit = this.lit < 0 ? (by > 0 ? 0 : this.count - 1) : Math.floorMod(this.lit + by, this.count);
        reveal();
    }

    /** Arrows, Home and End move the lit row; true when the key was one of them. Enter and Escape stay with the owner. */
    boolean key(InputKey key) {
        switch (key) {
            case DOWN -> move(1);
            case UP -> move(-1);
            case HOME -> lit(this.count == 0 ? -1 : 0);
            case END -> lit(this.count - 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Scrolls the lit row into view. */
    void reveal() {
        if (this.lit < 0) {
            return;
        }
        int rows = Math.max(1, viewRows());
        if (this.lit < this.bar.offset()) {
            this.bar.offset(this.lit);
        } else if (this.lit >= this.bar.offset() + rows) {
            this.bar.offset(this.lit - rows + 1);
        }
    }

    /**
     * Draws the card, the rows in view and the bar. {@code shown} from 0 to 1 unrolls the card from the anchor's side
     * while it opens; rows and bar wait for it to finish. {@code empty} shows when there is no row.
     */
    void draw(Draw d, Theme t, int mx, int my, Rows rows, String empty, float shown) {
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        d.nextLayer(); // the text beneath may sort above a rectangle otherwise
        boolean moving = shown < 1F;
        if (moving) {
            int showing = Math.max(1, Math.round(this.h * Math.max(0F, shown)));
            int clipTop = this.above ? this.y + this.h - showing : this.y;
            d.scissor(this.x - MARGIN, clipTop, this.x + this.w + MARGIN, clipTop + showing);
        }
        frame.popup(d, p, this.x, this.y, this.w, this.h);
        if (moving) {
            d.endScissor();
            this.hover.step(-1);
            return;
        }
        int vx = rowX();
        int vw = rowW();
        int hoverRow = this.bar.dragging() ? -1 : rowAt(mx, my);
        this.hover.step(hoverRow);
        if (vw > 0 && viewH() > 0) {
            d.scissor(vx, viewY(), vx + vw, viewY() + viewH());
            if (this.count == 0) {
                d.textFit(empty, vx + TEXT_LEFT, RowLook.textY(viewY(), this.rowH), Math.max(0, vw - 2 * TEXT_LEFT),
                        Ink.soft(p, p.surface()));
            } else {
                int first = this.bar.offset();
                int last = Math.min(this.count, first + viewRows());
                for (int row = first; row < last; row++) {
                    int ry = rowY(row);
                    boolean on = row == hoverRow;
                    int face = rows.face(p, row, on, on ? this.hover.fade() : 0F);
                    frame.row(d, p, vx, ry, vw, this.rowH, face);
                    rows.draw(d, t, row, vx, ry, vw, this.rowH, face);
                }
            }
            d.endScissor();
        }
        this.bar.draw(d, t, barX(), viewY(), viewH(), this.bar.dragging() || mx >= barX() && contains(mx, my));
    }
}
