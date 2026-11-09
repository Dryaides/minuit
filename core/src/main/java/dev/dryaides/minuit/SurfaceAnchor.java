package dev.dryaides.minuit;

/** Where a floating panel goes next to its anchor: on the wanted side if it fits, flipped if the other holds more, always inside its area. */
final class SurfaceAnchor {
    private static final int MARGIN = 2;
    static final int ARROW_H = 3;
    private static final int ARROW_W = 2 * ARROW_H - 1;
    private static final double EDGE_CONTRAST = 1.8D;

    private final BaseComponent widget;
    private final Box box;
    private final PanelComponent host;

    SurfaceSide side = SurfaceSide.BOTTOM;
    SurfaceAlign align = SurfaceAlign.START;
    int gap = 3 + ARROW_H;
    Box area;
    boolean arrow = true;

    /** The result of {@link #place}. */
    int x;
    int y;
    int w;
    int h;
    SurfaceSide placed = SurfaceSide.BOTTOM;
    /** The arrow's centre along the card's edge, from the card's start; -1 for no arrow. */
    int arrowAt = -1;

    SurfaceAnchor(BaseComponent widget, Box box, PanelComponent host) {
        this.widget = widget;
        this.box = box;
        this.host = host;
    }

    int left() { return widget != null ? widget.x : box.x(); }
    int top() { return widget != null ? widget.y : box.y(); }
    int width() { return widget != null ? widget.w : box.w(); }
    int height() { return widget != null ? widget.h : box.h(); }
    boolean shown() { return widget == null || widget.visible; }
    BaseComponent widget() { return widget; }

    boolean contains(double mx, double my) {
        return shown() && mx >= left() && mx < left() + width() && my >= top() && my < top() + height();
    }

    Box areaBox() {
        return area != null ? area : host.bounds();
    }

    private int room(SurfaceSide s, Box a) {
        return switch (s) {
            case TOP -> top() - a.y() - MARGIN;
            case BOTTOM -> a.bottom() - MARGIN - top() - height();
            case LEFT -> left() - a.x() - MARGIN;
            default -> a.right() - MARGIN - left() - width();
        };
    }

    /** The widest the card can be and stay in the area. */
    int maxWidth() {
        return Math.max(1, areaBox().w() - 2 * MARGIN);
    }

    int maxHeight() {
        return Math.max(1, areaBox().h() - 2 * MARGIN);
    }

    void place(int cardW, int cardH) {
        Box a = areaBox();
        w = Math.min(cardW, maxWidth());
        h = Math.min(cardH, maxHeight());
        int need = (side.vertical() ? h : w) + gap;
        placed = side;
        if (room(side, a) < need && room(side.opposite(), a) > room(side, a)) {
            placed = side.opposite();
        }
        int ax = left();
        int ay = top();
        int aw = width();
        int ah = height();
        switch (placed) {
            case TOP -> {
                y = ay - gap - h;
                x = along(ax, aw, w);
            }
            case BOTTOM -> {
                y = ay + ah + gap;
                x = along(ax, aw, w);
            }
            case LEFT -> {
                x = ax - gap - w;
                y = along(ay, ah, h);
            }
            default -> {
                x = ax + aw + gap;
                y = along(ay, ah, h);
            }
        }
        x = Math.max(a.x() + MARGIN, Math.min(x, a.right() - MARGIN - w));
        y = Math.max(a.y() + MARGIN, Math.min(y, a.bottom() - MARGIN - h));
        placeArrow(ax, ay, aw, ah);
    }

    private int along(int start, int length, int size) {
        return switch (align) {
            case START -> start;
            case CENTER -> start + (length - size) / 2;
            default -> start + length - size;
        };
    }

    private void placeArrow(int ax, int ay, int aw, int ah) {
        arrowAt = -1;
        if (!arrow || gap < ARROW_H) {
            return;
        }
        // No arrow when the area pushed the card against its anchor: it would point at nothing.
        boolean clear = switch (placed) {
            case TOP -> y + h + host.theme().frame().panelShadow() + ARROW_H <= ay;
            case BOTTOM -> y - ARROW_H >= ay + ah;
            case LEFT -> x + w + ARROW_H <= ax;
            default -> x - ARROW_H >= ax + aw;
        };
        if (!clear) {
            return;
        }
        int inset = ARROW_W / 2 + 3;
        int start = placed.vertical() ? x : y;
        int size = placed.vertical() ? w : h;
        int spanFrom = Math.max(placed.vertical() ? ax : ay, start + inset);
        int spanTo = Math.min(placed.vertical() ? ax + aw : ay + ah, start + size - inset);
        if (spanFrom > spanTo) {
            return;
        }
        int middle = placed.vertical() ? ax + aw / 2 : ay + ah / 2;
        arrowAt = Math.max(spanFrom, Math.min(middle, spanTo - 1)) - start;
    }

    /** A small pointer on the card's edge, towards the anchor. Draw after the card. */
    void drawArrow(Draw d, Theme t) {
        if (arrowAt < 0) {
            return;
        }
        int edge = edgeOf(t) | 0xFF000000;
        int shadow = t.frame().panelShadow();
        // Solid: a hollow one reads as a stray mark on a bevel. Its base row touches the card.
        switch (placed) {
            case BOTTOM -> Shapes.arrow(d, x + arrowAt, y - ARROW_H, ARROW_H, Shapes.Point.UP, edge);
            case TOP -> Shapes.arrow(d, x + arrowAt, y + h + shadow + ARROW_H - 1, ARROW_H, Shapes.Point.DOWN, edge);
            case RIGHT -> Shapes.arrow(d, x - ARROW_H, y + arrowAt, ARROW_H, Shapes.Point.LEFT, edge);
            default -> Shapes.arrow(d, x + w + ARROW_H - 1, y + arrowAt, ARROW_H, Shapes.Point.RIGHT, edge);
        }
    }

    /** What the card's own border is closest to, so that the arrow reads as part of it. */
    private static int edgeOf(Theme t) {
        Palette p = t.palette();
        int surface = p.surface() | 0xFF000000;
        int best = p.frameDark();
        best = stronger(best, p.frame(), surface);
        best = stronger(best, p.surfaceEdge(), surface);
        return stronger(best, p.textFaint(), surface);
    }

    private static int stronger(int best, int other, int surface) {
        double now = Shapes.contrast(best | 0xFF000000, surface);
        return now < EDGE_CONTRAST && Shapes.contrast(other | 0xFF000000, surface) > now ? other : best;
    }

    /** Where a card glides in from, so that it seems to leave its anchor. */
    Slide slideFrom() {
        return switch (placed) {
            case TOP -> Slide.DOWN;
            case BOTTOM -> Slide.UP;
            case LEFT -> Slide.RIGHT;
            default -> Slide.LEFT;
        };
    }
}
