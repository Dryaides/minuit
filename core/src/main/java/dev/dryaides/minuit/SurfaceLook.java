package dev.dryaides.minuit;

/** What the surfaces share: the look of a floating card, the inks that stay readable, and a small tween. */
final class SurfaceLook {
    static final int FONT_H = 8;
    static final int LINE = 10;
    /** Opacity of a modal's dim, over the palette's shadow colour: the same as a dialog's. */
    static final int DIM_ALPHA = 0xA0;

    private SurfaceLook() {
    }

    /** A see-through palette (the glass mode) would show what is beneath through the words: hide it first. */
    static void backing(Draw d, Theme t, int x, int y, int w, int h) {
        int surface = t.palette().surface();
        if (surface >>> 24 == 0xFF) {
            return;
        }
        int solid = surface | 0xFF000000;
        int corner = Math.max(1, t.frame().corner());
        if (w <= 2 * corner || h <= 2 * corner) {
            return;
        }
        d.fill(x + corner, y + 1, x + w - corner, y + h - 1, solid);
        d.fill(x + 1, y + corner, x + corner, y + h - corner, solid);
        d.fill(x + w - corner, y + corner, x + w - 1, y + h - corner, solid);
    }

    /** A card over other widgets: its shadow, what hides the widgets, and the theme's panel. */
    static void floating(Draw d, Theme t, int x, int y, int w, int h) {
        t.frame().popup(d, t.palette(), x, y, w, h);
    }

    /** The palette's ink that reads best on a colour. */
    static int bestInk(Palette p, int under) {
        return Ink.on(p, under);
    }

    /** The surface's own text if it reads, as a panel's words do; otherwise the palette's best. */
    static int textOn(Palette p, int under) {
        return Ink.on(p, under);
    }

    /** A value eased on the clock that can start from anywhere, which a {@link Glide} cannot: a drag lets go mid-way. */
    static final class Tween {
        private final int ms;
        private float from;
        private float to;
        private long start;
        private boolean moving;

        Tween(int ms, float value) {
            this.ms = ms;
            from = value;
            to = value;
        }

        float value() {
            if (!moving) {
                return to;
            }
            float t = (BaseComponent.clock().getAsLong() - start) / (float) ms;
            if (t >= 1F || BaseComponent.reducedMotion()) {
                moving = false;
                return to;
            }
            float inv = 1F - Math.max(0F, t);
            return to + (from - to) * inv * inv * inv;
        }

        void go(float target) {
            if (target == to) {
                return;
            }
            from = value();
            to = target;
            if (from == to || BaseComponent.reducedMotion()) {
                moving = false;
                from = to;
                return;
            }
            start = BaseComponent.clock().getAsLong();
            moving = true;
        }

        void snap(float value) {
            from = value;
            to = value;
            moving = false;
        }

        float target() {
            return to;
        }

        boolean moving() {
            value();
            return moving;
        }
    }
}
