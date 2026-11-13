package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ThemeFiles;

/**
 * The colours of a chart's series, shared by every data widget so a donut and a chart agree, in the
 * {@link SeriesPalette} a widget asks for. A fixed scheme gives its list. {@link SeriesPalette#THEME}: the list a
 * chroma file gives, else the theme's coloured roles in a fixed order, brought to a mid-light lightness with their hue
 * and chroma kept, without two that look alike; what is still missing is the first colour turned round the hue wheel.
 * Every colour stands apart from the plot face (the surface) in OKLab, moved in lightness only, never mixed towards the text: on a
 * light grey face a 3:1 ratio leaves only near-black lines. {@link SeriesPalette#SHADES}: the accent
 * towards black and white in turn.
 */
final class SeriesColors {
    static final int COUNT = 8;
    /** OKLab distance under which two chart colours read as one at a glance (two greens of one family fall under it). */
    private static final double MIN_DISTANCE = 0.12D;
    /** OKLab distance a line keeps from the plot face: hue counts as much as lightness, so a green line reads on grey. */
    static final double LINE_GAP = 0.15D;
    /** The same for a filled area, which reads by its size and the lines between neighbours. */
    static final double FILL_GAP = 0.08D;
    /** OKLab lightness above which a plot face counts as light: custom colours then only darken. */
    private static final double LIGHT_FACE = 0.6D;
    private static final double LINE_CONTRAST = 2.0D;
    private static final double FILL_CONTRAST = 1.5D;
    /** The OKLab lightness band a role is brought into: dark roles (a deep green, a brown) turn lively. */
    private static final double LIVE_LOW = 0.62D;
    private static final double LIVE_HIGH = 0.82D;
    /** Least OKLab chroma of a colour turned round the wheel, so a near-grey chroma still gets hues told apart. */
    private static final double TURN_CHROMA = 0.12D;
    /** Hue turns, in degrees, tried in turn for the colours the roles do not give. */
    private static final int[] TURNS = {180, 120, -120, 60, -60, 150, -150, 90, -90, 30, -30};
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
            // A listed colour is the chroma's own choice: it moves only when the well would swallow it.
            return fit(opaque(listed[Math.floorMod(order, listed.length)]), well(p), LINE_GAP, false);
        }
        if (themeFor != p) {
            pick(p, THEME, LINE_GAP);
            themeFor = p;
        }
        return THEME[Math.floorMod(order, COUNT)];
    }

    private static final int[] FILL = new int[COUNT];
    private static Palette fillFor;

    /**
     * The colour for a filled area in this place of the order (a wheel's segment): the same as {@link #color} except
     * for {@link SeriesPalette#THEME}, whose areas keep a smaller distance from the well since their size carries them.
     */
    static synchronized int fill(Palette p, SeriesPalette scheme, int order) {
        if (scheme != SeriesPalette.THEME || ThemeFiles.series(p) != null) {
            return color(p, scheme, order);
        }
        if (fillFor != p) {
            pick(p, FILL, FILL_GAP);
            fillFor = p;
        }
        return FILL[Math.floorMod(order, COUNT)];
    }

    private static boolean distinct(int color, int[] colours, int count) {
        for (int i = 0; i < count; i++) {
            if (distance(color, colours[i]) < MIN_DISTANCE) {
                return false;
            }
        }
        return true;
    }

    private static void pick(Palette p, int[] colours, double gap) {
        int back = well(p);
        int[] roles = {p.primary(), p.accent(), p.good(), p.warn(), p.bad(), p.accentDeep(), p.destructive()};
        int count = 0;
        for (int i = 0; i < roles.length && count < COUNT; i++) {
            int role = opaque(roles[i]);
            if (spread(role) < MIN_HUE_SPREAD) {
                continue;
            }
            int color = fit(role, back, gap, true);
            if (distinct(color, colours, count)) {
                colours[count++] = color;
            }
        }
        int first = count > 0 ? colours[0] : opaque(p.accent());
        for (int i = 0; i < TURNS.length && count < COUNT; i++) {
            int color = fit(turn(first, TURNS[i]), back, gap, true);
            if (distinct(color, colours, count)) {
                colours[count++] = color;
            }
        }
        // A muted chroma (cream light) turns on a small hue circle that holds only three colours 0.12 apart; walk
        // the wheel again at both ends of the lively band, then repeat only what no turn could keep apart.
        for (double lightness : new double[] {LIVE_HIGH, LIVE_LOW}) {
            for (int degrees = 0; degrees < 360 && count < COUNT; degrees += 15) {
                double[] lab = oklab(turn(first, degrees));
                int color = fit(fromOklab(lightness, lab[1], lab[2]), back, gap, true);
                if (distinct(color, colours, count)) {
                    colours[count++] = color;
                }
            }
        }
        for (int i = 0; count < COUNT; i++) {
            colours[count] = colours[i % Math.max(1, count)];
            count++;
        }
    }

    /**
     * A colour the mod chose, kept in hue and chroma. On a light face it only ever darkens, until it is the gap away
     * and holds a contrast ratio (2:1 a line, 1.5:1 an area): a pale ore colour keeps its hue where a gap made of
     * chroma alone still leaves it faint. On a dark face it moves like a listed colour.
     */
    static int custom(Palette p, int color, boolean filled) {
        int back = well(p);
        double gap = filled ? FILL_GAP : LINE_GAP;
        if (oklab(back)[0] <= LIGHT_FACE) {
            return fit(opaque(color), back, gap, false);
        }
        double ratio = filled ? FILL_CONTRAST : LINE_CONTRAST;
        double[] lab = oklab(opaque(color));
        for (double l = lab[0]; l > 0.05D; l -= 0.01D) {
            int moved = l == lab[0] ? opaque(color) : fromOklab(l, lab[1], lab[2]);
            if (distance(moved, back) >= gap && Shapes.contrast(moved, back) >= ratio) {
                return moved;
            }
        }
        return fromOklab(0.05D, lab[1], lab[2]);
    }

    /**
     * The colour with its OKLab lightness brought into the lively band when {@code live}, then moved in lightness
     * alone, on the side away from the well first, by the smallest step that puts it {@code gap} from the well.
     */
    private static int fit(int color, int back, double gap, boolean live) {
        double[] lab = oklab(color);
        double start = live ? Math.max(LIVE_LOW, Math.min(LIVE_HIGH, lab[0])) : lab[0];
        boolean up = start >= oklab(back)[0];
        for (int step = 0; step <= 60; step++) {
            for (int side = 0; side < 2; side++) {
                double l = start + ((side == 0) == up ? step : -step) * 0.01D;
                if (l < 0.2D || l > 0.97D) {
                    continue;
                }
                int moved = step == 0 && !live ? color : fromOklab(l, lab[1], lab[2]);
                if (distance(moved, back) >= gap) {
                    return moved;
                }
            }
        }
        return fromOklab(start, lab[1], lab[2]);
    }

    /** The colour turned round the OKLab hue wheel by this many degrees, its chroma raised to {@link #TURN_CHROMA}. */
    private static int turn(int color, int degrees) {
        double[] lab = oklab(color);
        double chroma = Math.max(TURN_CHROMA, Math.hypot(lab[1], lab[2]));
        double hue = Math.atan2(lab[2], lab[1]) + Math.toRadians(degrees);
        return fromOklab(lab[0], chroma * Math.cos(hue), chroma * Math.sin(hue));
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    /** The face a chart draws on. */
    private static int well(Palette p) {
        return DataLook.plotFace(p);
    }

    /** Euclidean distance in OKLab (Ottosson 2020): about 0.02 is a just-visible step, 0.1 and more two named colours. */
    static double distance(int a, int b) {
        double[] x = oklab(a);
        double[] y = oklab(b);
        return Math.sqrt((x[0] - y[0]) * (x[0] - y[0]) + (x[1] - y[1]) * (x[1] - y[1]) + (x[2] - y[2]) * (x[2] - y[2]));
    }

    private static double[] oklab(int color) {
        double r = linear(color >> 16 & 0xFF);
        double g = linear(color >> 8 & 0xFF);
        double b = linear(color & 0xFF);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[] {
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }

    /** Back from OKLab, the chroma cut until the colour fits in sRGB so the hue holds. */
    private static int fromOklab(double lightness, double a, double b) {
        double k = 1D;
        double[] rgb = toLinear(lightness, a, b);
        for (int i = 0; i < 40 && !inGamut(rgb); i++) {
            k *= 0.93D;
            rgb = toLinear(lightness, a * k, b * k);
        }
        return 0xFF000000 | encode(rgb[0]) << 16 | encode(rgb[1]) << 8 | encode(rgb[2]);
    }

    private static double[] toLinear(double lightness, double a, double b) {
        double l = Math.pow(lightness + 0.3963377774 * a + 0.2158037573 * b, 3);
        double m = Math.pow(lightness - 0.1055613458 * a - 0.0638541728 * b, 3);
        double s = Math.pow(lightness - 0.0894841775 * a - 1.2914855480 * b, 3);
        return new double[] {
                4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s};
    }

    private static boolean inGamut(double[] rgb) {
        for (double v : rgb) {
            if (v < -1e-4 || v > 1 + 1e-4) {
                return false;
            }
        }
        return true;
    }

    private static int encode(double linear) {
        double v = Math.max(0D, Math.min(1D, linear));
        v = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
        return (int) Math.round(v * 255D);
    }

    private static double linear(int channel) {
        double v = channel / 255D;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static int spread(int color) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }
}
