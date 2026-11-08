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
 * @param variant light, dark, or auto (dark when the chroma ships it, else light)
 * @param accent  an {@link Accent} preset laid over the chroma's accent roles; blank for none
 */
public record ThemeChoice(String family, String chroma, Variant variant, String accent) {

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

    /** Light or dark half of a chroma. {@code AUTO} takes the dark one when the chroma has it. */
    public enum Variant {
        LIGHT, DARK, AUTO;

        /** {@code "light"}, {@code "dark"} or {@code "auto"}; anything else reads as {@code AUTO}. */
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
