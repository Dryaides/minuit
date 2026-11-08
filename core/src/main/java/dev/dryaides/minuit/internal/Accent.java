package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.Shapes;
import dev.dryaides.minuit.ThemeChoice;

import java.util.List;
import java.util.Locale;

/**
 * An accent overlay: one colour laid over any chroma that replaces only its accent roles (accent,
 * accentDeep, primary and its hover and text), so toggles, sliders, the primary action and the
 * selection take that colour while the rest of the chroma stays. Picked apart from the chroma,
 * kept in {@link ThemeChoice#accent()}; the blank id is no overlay.
 *
 * @param id    the preset's id, as saved in the config
 * @param light the colour on a light variant
 * @param dark  the colour on a dark variant, one step lighter so it holds on a dark surface
 */
public record Accent(String id, int light, int dark) {

    public static final Accent BLUE = new Accent("blue", 0xFF2563EB, 0xFF3B82F6);
    public static final Accent GREEN = new Accent("green", 0xFF16A34A, 0xFF22C55E);
    public static final Accent ORANGE = new Accent("orange", 0xFFEA580C, 0xFFF97316);
    public static final Accent ROSE = new Accent("rose", 0xFFE11D48, 0xFFF43F5E);
    public static final Accent VIOLET = new Accent("violet", 0xFF7C3AED, 0xFF8B5CF6);
    /** minuit's own: midnight blue, after the night gradient of the logo. */
    public static final Accent MIDNIGHT = new Accent("midnight", 0xFF3B3F9E, 0xFF8E96F5);

    /** The presets in the order a theme page shows them. */
    public static final List<Accent> PRESETS = List.of(BLUE, GREEN, ORANGE, ROSE, VIOLET, MIDNIGHT);

    /** The preset saved under {@code id}, or null for blank or unknown (no overlay). */
    public static Accent byId(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (Accent a : PRESETS) {
            if (a.id.equals(key)) {
                return a;
            }
        }
        return null;
    }

    /** True when {@code p} is a dark variant: its surface is darker than mid grey. */
    public static boolean dark(Palette p) {
        return Shapes.luminance(p.surface()) < 0.18;
    }

    /** The colour this accent takes on {@code p}. */
    public int on(Palette p) {
        return dark(p) ? this.dark : this.light;
    }

    /**
     * {@code p} with its accent roles in this colour. The hover steps toward the text (darker on
     * light, lighter on dark); the text on the primary fill is picked by contrast.
     */
    public Palette over(Palette p) {
        boolean dark = dark(p);
        int c = dark ? this.dark : this.light;
        int deep = Shapes.mix(c, 0xFF000000, 0.25F);
        int hover = Shapes.mix(c, dark ? 0xFFFFFFFF : 0xFF000000, 0.15F);
        int onFill = Shapes.contrast(0xFFFFFFFF, c) >= Shapes.contrast(0xFF09090B, c) ? 0xFFFFFFFF : 0xFF09090B;
        return new Palette(p.frame(), p.frameDark(), p.frameLight(), p.trim(),
                p.surface(), p.surfaceEdge(), p.section(), p.well(), p.wellEdge(),
                p.text(), p.textSoft(), p.textFaint(),
                p.raised(), p.raisedHover(), p.raisedText(),
                c, hover, onFill,
                c, deep,
                p.good(), p.warn(), p.bad(),
                p.destructive(), p.destructiveHover(), p.destructiveText(),
                p.track(), p.disabled(), p.disabledText(), p.shadow());
    }

    /** {@code p} under the accent saved as {@code id}; {@code p} itself for none. */
    public static Palette apply(Palette p, String id) {
        Accent a = byId(id);
        return a == null ? p : a.over(p);
    }
}
