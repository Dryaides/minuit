package dev.dryaides.minuit;

/**
 * What the navigation components (breadcrumb, pagination, menubar, sidebar, command palette) draw the same
 * way: inks judged against what lies under them, the pointer's wash, and small notched shapes.
 */
final class NavLook {
    static final int WASH_HOVER = 0x28;
    static final int WASH_HELD = 0x50;
    private static final float DISABLED_FADE = 0.45F;
    /** A glyph's visible height in an 8-high line, which leaves its last row for descenders. */
    private static final int BADGE_H = 9;

    private NavLook() {
    }

    static int solid(int color) {
        return color | 0xFF000000;
    }

    /** The palette's ink that stands out on a solid colour; {@code text} unless another one clearly beats it. */
    static int ink(Palette p, int under) {
        return Ink.on(p, under);
    }

    /** The quieter ink, or the plain one if the quieter one would be too weak there. */
    static int soft(Palette p, int under) {
        return Ink.soft(p, under);
    }

    /**
     * A disabled ink, always weaker than {@link #soft} next to it and still visible: a palette's faint text
     * is either nearly the same as its soft text (soft, slate) or nearly gone on its own surface.
     */
    static int disabled(Palette p, int under) {
        return Shapes.mix(soft(p, under), under, DISABLED_FADE);
    }

    /** The light wash under the pointer, in the colour of the ink it sits under. */
    static void wash(Draw d, int x, int y, int w, int h, int ink, int alpha) {
        if (alpha > 0 && w > 0 && h > 0) {
            d.fill(x, y, x + w, y + h, Shapes.alpha(ink, alpha));
        }
    }

    /** A rectangle with its four corner pixels off, which reads as a pill at this size in every family. */
    static void notched(Draw d, int x, int y, int w, int h, int color) {
        if (w < 3 || h < 3) {
            d.fill(x, y, x + w, y + h, color);
            return;
        }
        d.fill(x + 1, y, x + w - 1, y + 1, color);
        d.fill(x, y + 1, x + w, y + h - 1, color);
        d.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
    }

    static int badgeWidth(String text) {
        return TextMeasure.width(text) + 3;
    }

    static int badgeHeight() {
        return BADGE_H;
    }

    /** A count or a short word on a pill, top at {@code y}. */
    static void badge(Draw d, String text, int x, int y, int face, int ink) {
        notched(d, x, y, badgeWidth(text), BADGE_H, face);
        d.text(text, x + 2, y + 1, ink);
    }

    /** A see-through family (glass) would show what is beneath through the words: hide it first. */
    static void backing(Draw d, Theme t, int x, int y, int w, int h) {
        int surface = t.palette().surface();
        if (surface >>> 24 == 0xFF) {
            return;
        }
        int color = solid(surface);
        int corner = Math.max(1, t.frame().corner());
        d.fill(x + corner, y + 1, x + w - corner, y + h - 1, color);
        d.fill(x + 1, y + corner, x + w - 1, y + h - corner, color);
    }
}
