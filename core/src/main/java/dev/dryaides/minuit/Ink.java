package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.OfficialPalettes;

/** Text and mark colours that stay readable on a face, in every palette. */
final class Ink {
    static final double TEXT = 4.5D;
    static final double MARK = 3D;

    private Ink() {
    }

    /**
     * A see-through face as it shows over the palette's surface. A see-through surface (the glass mode) is first
     * laid over white, the backdrop white ink fares worst on.
     */
    static int seen(Palette p, int face) {
        int alpha = face >>> 24;
        if (alpha == 0xFF) {
            return face;
        }
        int under = GlassPalette.over(p.surface(), GlassPalette.WORST);
        return Shapes.mix(under, face | 0xFF000000, alpha / 255F);
    }

    /** The ink that reads on a face. */
    static int on(Palette p, int face) {
        return Shapes.textOn(p, seen(p, face));
    }

    /** The quieter text when it reads on a face, otherwise {@link #on}. */
    static int soft(Palette p, int face) {
        return Shapes.contrast(p.textSoft(), seen(p, face)) >= TEXT ? p.textSoft() : on(p, face);
    }

    /**
     * The label of a control that is off: WCAG asks nothing of it, but it still has to be read, so it keeps the
     * palette's disabled ink up to the 3:1 of a mark, below the 4.5:1 of live text so it still looks off.
     */
    static int disabled(Palette p, int face) {
        return readable(p, p.disabledText(), seen(p, face), MARK);
    }

    /** True for a palette minuit ships: its contrast is checked at design time and never shifted at runtime. */
    static boolean official(Palette p) {
        return OfficialPalettes.is(p);
    }

    /** {@link #readable(int, int, double)} for a colour of {@code p}; an official palette's colour stays as drawn. */
    static int readable(Palette p, int color, int under, double least) {
        return official(p) ? color : readable(color, under, least);
    }

    /**
     * A mark colour nudged toward black or white, whichever is farther from {@code under}, until it reaches
     * {@code least}. For colours no palette owns (formatting codes); a palette's colours go through
     * {@link #readable(Palette, int, int, double)}.
     */
    static int readable(int color, int under, double least) {
        int solid = color | 0xFF000000;
        int back = under | 0xFF000000;
        int far = Shapes.contrast(0xFF000000, back) > Shapes.contrast(0xFFFFFFFF, back) ? 0xFF000000 : 0xFFFFFFFF;
        for (float toward = 0F; toward < 1F; toward += 0.1F) {
            int mixed = Shapes.mix(solid, far, toward);
            if (Shapes.contrast(mixed, back) >= least) {
                return mixed;
            }
        }
        return far;
    }
}
