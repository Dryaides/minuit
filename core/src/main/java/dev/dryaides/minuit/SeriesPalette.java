package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ThemeFiles;

import java.util.Objects;

/**
 * The colour scheme of a chart's series, picked per chart like a pie chart's scheme in a spreadsheet. Every
 * widget that colours series by their order (chart, donut, wheel) takes its colours from it, the first again
 * after the last. {@link #THEME} and {@link #SHADES} follow the theme; every other scheme is a fixed list: a
 * built-in one, a list from code ({@link #of}), or one a mod or resource pack ships at
 * {@code assets/<namespace>/minuit/palettes/<id>.json} ({@link #byId}).
 */
@Incubating
public final class SeriesPalette {
    /** The theme's own colours: a chroma's {@code series} list when it has one, otherwise its strongest roles. */
    public static final SeriesPalette THEME = new SeriesPalette("minuit:theme", null);
    /** Shades of the theme's accent, darker and lighter in turn so neighbours differ. */
    public static final SeriesPalette SHADES = new SeriesPalette("minuit:shades", null);
    /** Okabe and Ito's colour-blind safe set, its black swapped for Paul Tol's wine so it reads on dark panels. */
    public static final SeriesPalette CONTRAST = new SeriesPalette("minuit:contrast", new int[] {
            0xFFE69F00, 0xFF56B4E9, 0xFF009E73, 0xFFF0E442, 0xFF0072B2, 0xFFD55E00, 0xFFCC79A7, 0xFF882255});
    /** Eight saturated hues. */
    public static final SeriesPalette VIVID = new SeriesPalette("minuit:vivid", new int[] {
            0xFFE53935, 0xFF1E88E5, 0xFFFDD835, 0xFF43A047, 0xFF8E24AA, 0xFFFB8C00, 0xFF00ACC1, 0xFFD81B60});
    /** Eight soft hues, for light panels. */
    public static final SeriesPalette PASTEL = new SeriesPalette("minuit:pastel", new int[] {
            0xFFF4A3A3, 0xFFA3C4F4, 0xFFF7DC8F, 0xFFA8DCA0, 0xFFCDB0EA, 0xFFF8C49A, 0xFF9EDDE3, 0xFFF0AACB});

    private static final SeriesPalette[] BUILT_IN = {THEME, SHADES, CONTRAST, VIVID, PASTEL};

    private final String id;
    private final int[] colors;

    private SeriesPalette(String id, int[] colors) {
        this.id = id;
        this.colors = colors;
    }

    /** A fixed list, in series order; an alpha of 0 counts as opaque. */
    public static SeriesPalette of(int... colors) {
        if (Objects.requireNonNull(colors, "colors").length == 0) {
            throw new IllegalArgumentException("a series palette needs at least one colour");
        }
        int[] copy = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            copy[i] = colors[i] | 0xFF000000;
        }
        return new SeriesPalette(null, copy);
    }

    /**
     * A built-in scheme ({@code minuit:theme}, {@code minuit:shades}, {@code minuit:contrast}, {@code minuit:vivid},
     * {@code minuit:pastel}) or one loaded from {@code assets/<namespace>/minuit/palettes/<id>.json} as
     * {@code <namespace>:<id>}; null when none has that id. Data files load with the themes and change on reload.
     */
    public static SeriesPalette byId(String id) {
        for (SeriesPalette p : BUILT_IN) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        int[] loaded = ThemeFiles.seriesPalette(id);
        return loaded == null ? null : new SeriesPalette(id, loaded);
    }

    /** Its id, or null for a list from {@link #of}. */
    public String id() {
        return id;
    }

    /** The fixed list it holds; null for a scheme that follows the theme. */
    int[] fixed() {
        return colors;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SeriesPalette other && Objects.equals(id, other.id)
                && java.util.Arrays.equals(colors, other.colors);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(id) + java.util.Arrays.hashCode(colors);
    }

    @Override
    public String toString() {
        return id != null ? id : "SeriesPalette" + java.util.Arrays.toString(colors);
    }
}
