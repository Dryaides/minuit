package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ThemeFiles;

/**
 * The colours of a chart's series, shared by every data widget so a donut and a chart agree, in the
 * {@link SeriesPalette} a widget asks for. A fixed scheme gives its list. {@link SeriesPalette#THEME}: the list a
 * chroma file gives, else the theme's strongest colours in a fixed order, each moved towards the text until it reads
 * on the well, without two that look alike; a chroma's accent is sometimes its primary, so some are skipped, and what
 * is still missing is a known colour mixed further towards the text. {@link SeriesPalette#SHADES}: the accent towards
 * black and white in turn.
 */
final class SeriesColors {
    static final int COUNT = 8;
    private static final double MIN_CONTRAST = 3D;
    /** Summed over red, green and blue: two shades of one hue fall under it, two hues do not. */
    private static final int MIN_COLOUR_GAP = 150;
    private static final int MIN_HUE_APART = 40;
    private static final int MIN_HUE_SPREAD = 40;
    /** Towards black below 0, towards white above, alternating so two neighbours are never close shades. */
    private static final float[] SHADE_STEPS = {0F, -0.45F, 0.45F, -0.25F, 0.25F, -0.65F, 0.65F, -0.1F};

    private static final int[] THEME = new int[COUNT];
    private static Palette themeFor;
    private static final int[] SHADES = new int[COUNT];
    private static Palette shadesFor;

    private SeriesColors() {
    }

    /** The theme's colour for this place in a chart's order; the ninth reuses the first. */
    static int color(Palette p, int order) {
        return color(p, SeriesPalette.THEME, order);
    }

    /** The colour for this place in a chart's order in this scheme; past the end the list starts again. */
    static synchronized int color(Palette p, SeriesPalette scheme, int order) {
        int[] fixed = scheme.fixed();
        if (fixed != null) {
            return fixed[Math.floorMod(order, fixed.length)];
        }
        if (scheme == SeriesPalette.SHADES) {
            if (shadesFor != p) {
                int accent = opaque(p.accent());
                for (int i = 0; i < COUNT; i++) {
                    float step = SHADE_STEPS[i];
                    SHADES[i] = step < 0 ? Shapes.mix(accent, 0xFF000000, -step) : Shapes.mix(accent, 0xFFFFFFFF, step);
                }
                shadesFor = p;
            }
            return SHADES[Math.floorMod(order, COUNT)];
        }
        int[] listed = ThemeFiles.series(p);
        if (listed != null) {
            return opaque(listed[Math.floorMod(order, listed.length)]);
        }
        if (themeFor != p) {
            pick(p, THEME);
            themeFor = p;
        }
        return THEME[Math.floorMod(order, COUNT)];
    }

    private static void pick(Palette p, int[] colours) {
        int back = Shapes.mix(opaque(p.surface()), opaque(p.well()), (p.well() >>> 24) / 255F);
        int text = opaque(RowLook.ink(p, 0, false, true));
        int[] roles = {p.primary(), p.accent(), p.good(), p.warn(), p.bad(), p.accentDeep(), p.destructive(), p.raised()};
        int count = 0;
        for (int i = 0; i < roles.length && count < COUNT; i++) {
            int color = readable(opaque(roles[i]), text, back);
            if (farFromAll(color, colours, count, back)) {
                colours[count++] = color;
            }
        }
        for (int i = 0; count < COUNT; i++) {
            colours[count] = Shapes.mix(colours[i % Math.max(1, count)], text, 0.45F);
            count++;
        }
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    private static boolean farFromAll(int color, int[] colours, int count, int back) {
        for (int i = 0; i < count; i++) {
            if (gap(color, colours[i]) < MIN_COLOUR_GAP && !otherHue(color, colours[i])) {
                return false;
            }
        }
        return gap(color, back) >= MIN_COLOUR_GAP || otherHue(color, back);
    }

    private static int gap(int a, int b) {
        return Math.abs((a >> 16 & 0xFF) - (b >> 16 & 0xFF)) + Math.abs((a >> 8 & 0xFF) - (b >> 8 & 0xFF)) + Math.abs((a & 0xFF) - (b & 0xFF));
    }

    /** True for two clearly coloured colours on different sides of the wheel, however close in brightness. */
    private static boolean otherHue(int a, int b) {
        int spreadA = spread(a);
        int spreadB = spread(b);
        if (spreadA < MIN_HUE_SPREAD || spreadB < MIN_HUE_SPREAD) {
            return false;
        }
        int apart = Math.abs(hue(a) - hue(b));
        return Math.min(apart, 360 - apart) >= MIN_HUE_APART;
    }

    private static int spread(int color) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    /** The hue, in degrees. */
    private static int hue(int color) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        int top = Math.max(r, Math.max(g, b));
        int range = Math.max(1, spread(color));
        float at;
        if (top == r) {
            at = (g - b) / (float) range;
        } else if (top == g) {
            at = 2F + (b - r) / (float) range;
        } else {
            at = 4F + (r - g) / (float) range;
        }
        return Math.floorMod(Math.round(at * 60F), 360);
    }

    private static int readable(int color, int text, int back) {
        // On a mid grey, as in vanilla light, going to the text's white alone ends up at white for every colour.
        int other = Shapes.contrast(text, 0xFF000000) > Shapes.contrast(text, 0xFFFFFFFF) ? 0xFF000000 : 0xFFFFFFFF;
        for (float toward = 0F; toward < 0.8F; toward += 0.1F) {
            int mixed = Shapes.mix(color, text, toward);
            if (Shapes.contrast(mixed, back) >= MIN_CONTRAST) {
                return mixed;
            }
            mixed = Shapes.mix(color, other, toward);
            if (Shapes.contrast(mixed, back) >= MIN_CONTRAST) {
                return mixed;
            }
        }
        return text;
    }
}
