package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Accent;
import java.util.Locale;

/**
 * A look a player picked for the screens of one mod, kept in {@code config/minuit-client.json}
 * ({@link ThemeChoices}). Three levels, see THEMES.md: a family (the shapes and materials, e.g.
 * {@code "slate"}), a chroma (a colour set, e.g. {@code "teal"}) and a variant of that chroma.
 *
 * @param family  family id, never blank
 * @param chroma  chroma id; blank or unknown means the family's first
 * @param variant light, dark, glass (the dark half as translucent smoke, every chroma has it), or auto (dark when
 *                the chroma ships it, else light; never glass)
 * @param accent  an {@link Accent} preset laid over the chroma's accent roles; blank for none
 */
public record ThemeChoice(String family, String chroma, Variant variant, String accent) {

    /**
     * The chroma id that stands for the palette the mod registered ({@link Themes#ownChroma}), on any family.
     * No chroma file can take it: '@' is not allowed in a resource path.
     */
    public static final String OWN_CHROMA = "@own";

    /** True when this choice wears the mod's registered palette. */
    public boolean ownChroma() {
        return OWN_CHROMA.equals(this.chroma);
    }

    /** A choice with no accent overlay. */
    public ThemeChoice(String family, String chroma, Variant variant) {
        this(family, chroma, variant, "");
    }

    public ThemeChoice {
        family = blankTo(family, "");
        if (family.isEmpty()) {
            throw new IllegalArgumentException("a theme choice needs a family");
        }
        chroma = blankTo(chroma, "");
        variant = variant == null ? Variant.AUTO : variant;
        accent = Accent.byId(accent) == null ? "" : Accent.byId(accent).id();
    }

    /** This choice under another accent, blank for none. */
    public ThemeChoice withAccent(String id) {
        return new ThemeChoice(this.family, this.chroma, this.variant, id);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Light or dark half of a chroma, or the glass mode derived from it. {@code AUTO} takes the dark one when the
     * chroma has it.
     */
    public enum Variant {
        LIGHT, DARK, GLASS, AUTO;

        /** {@code "light"}, {@code "dark"}, {@code "glass"} or {@code "auto"}; anything else reads as {@code AUTO}. */
        public static Variant parse(String text) {
            if (text != null) {
                for (Variant v : values()) {
                    if (v.id().equals(text.trim().toLowerCase(Locale.ROOT))) {
                        return v;
                    }
                }
            }
            return AUTO;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
