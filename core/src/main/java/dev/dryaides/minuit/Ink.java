package dev.dryaides.minuit;

/** Text and mark colours that stay readable on a face, in every palette. */
final class Ink {
    static final double TEXT = 4.5D;
    static final double MARK = 3D;

    private Ink() {
    }

    /** A see-through face as it shows over the palette's surface. */
    static int seen(Palette p, int face) {
        int alpha = face >>> 24;
        return alpha == 0xFF ? face : Shapes.mix(p.surface() | 0xFF000000, face | 0xFF000000, alpha / 255F);
    }

    /** The ink that reads on a face. */
    static int on(Palette p, int face) {
        return Shapes.textOn(p, seen(p, face));
    }

    /** The quieter text when it reads on a face, otherwise {@link #on}. */
    static int soft(Palette p, int face) {
        return Shapes.contrast(p.textSoft(), seen(p, face)) >= TEXT ? p.textSoft() : on(p, face);
    }

    /** A mark colour nudged toward black or white, whichever is farther from {@code under}, until it reaches {@code least}. */
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
