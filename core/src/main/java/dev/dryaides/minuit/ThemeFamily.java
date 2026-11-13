package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A family of looks a player picks from: one {@link FrameStyle} (the shapes and materials) and the
 * chromas drawn with it. See THEMES.md.
 *
 * @param id          e.g. {@code "slate"}; its name is the translation key {@code minuit.family.<id>}
 * @param owner       the mod whose theme page alone offers this family, or null for every mod's
 * @param frame       how every chroma of the family draws its surfaces
 * @param recommended the chromas the family suggests, shown first; the first is its default
 * @param others      every other chroma, shown after them
 */
public record ThemeFamily(String id, String owner, FrameStyle frame, List<Chroma> recommended, List<Chroma> others) {

    public ThemeFamily {
        id = id.trim().toLowerCase(Locale.ROOT);
        owner = owner == null || owner.isBlank() ? null : owner.trim().toLowerCase(Locale.ROOT);
        recommended = List.copyOf(recommended);
        others = List.copyOf(others);
        if (recommended.isEmpty() && others.isEmpty()) {
            throw new IllegalArgumentException("family " + id + " has no chroma");
        }
    }

    public String nameKey() {
        return "minuit.family." + this.id;
    }

    /** True when every mod's theme page offers this family. */
    public boolean shared() {
        return this.owner == null;
    }

    /** The recommended chromas, then the others: the theme page's order. */
    public List<Chroma> chromas() {
        List<Chroma> all = new ArrayList<>(this.recommended);
        all.addAll(this.others);
        return all;
    }

    /** The chroma with this id, or null. */
    public Chroma chroma(String chromaId) {
        for (Chroma chroma : chromas()) {
            if (chroma.id().equals(chromaId)) {
                return chroma;
            }
        }
        return null;
    }

    /** The family's default chroma. */
    public Chroma first() {
        return this.recommended.isEmpty() ? this.others.get(0) : this.recommended.get(0);
    }

    /**
     * One colour set, with its light and dark halves and the glass mode derived from them, as a family draws it
     * (its overrides applied).
     *
     * @param id      e.g. {@code "teal"}
     * @param nameKey its name's translation key, {@code minuit.chroma.<id>}
     * @param light   the light half, or null when the chroma ships only a dark one
     * @param dark    the dark half, or null when the chroma ships only a light one
     * @param glass   the glass mode, {@link GlassPalette#of} the dark half (else the light one) when null
     */
    public record Chroma(String id, String nameKey, Palette light, Palette dark, Palette glass) {

        public Chroma {
            id = id.trim().toLowerCase(Locale.ROOT);
            if (light == null && dark == null) {
                throw new IllegalArgumentException("chroma " + id + " has no palette");
            }
            if (glass == null) {
                glass = GlassPalette.of(dark != null ? dark : light);
            }
        }

        /** A chroma whose glass mode is derived from its halves. */
        public Chroma(String id, String nameKey, Palette light, Palette dark) {
            this(id, nameKey, light, dark, null);
        }

        public boolean has(ThemeChoice.Variant variant) {
            return switch (variant) {
                case LIGHT -> this.light != null;
                case DARK -> this.dark != null;
                case GLASS, AUTO -> true;
            };
        }

        /** The variant {@code auto} means here: dark when the chroma has one, else light. */
        public ThemeChoice.Variant auto() {
            return this.dark != null ? ThemeChoice.Variant.DARK : ThemeChoice.Variant.LIGHT;
        }

        /** The palette for {@code variant}; a variant the chroma lacks gives the one it has. */
        public Palette palette(ThemeChoice.Variant variant) {
            if (variant == ThemeChoice.Variant.GLASS) {
                return this.glass;
            }
            ThemeChoice.Variant shown = variant == ThemeChoice.Variant.AUTO || !has(variant) ? auto() : variant;
            return shown == ThemeChoice.Variant.DARK ? this.dark : this.light;
        }
    }
}
