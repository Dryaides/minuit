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

    /** Ids of the accents the kit ships, as {@code accents/<id>.json} in its jar, in the order the picker shows them. */
    static final List<String> BUILT_IN = List.of("blue", "green", "orange", "rose", "violet", "midnight");

    private static volatile List<Accent> presets;

    /**
     * The presets in the order a theme page shows them: those the last resource reload found, or
     * the kit's own before any reload.
     */
    public static List<Accent> presets() {
        List<Accent> list = presets;
        if (list == null) {
            list = ThemeFiles.builtInAccents();
            presets = list;
        }
        return list;
    }

    /** Replaces the presets with {@code list}; called by the theme file reload. */
    static void load(List<Accent> list) {
        presets = List.copyOf(list);
    }

    /** The preset saved under {@code id}, or null for blank or unknown (no overlay). */
    public static Accent byId(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(Locale.ROOT);
        for (Accent a : presets()) {
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
     * {@code p} with its accent roles in this colour. The text on the primary fill is picked by
     * contrast, and the hover steps away from that text (darker under white, lighter under black), so
     * the label reads on the hover at least as well as on the rest face.
     */
    public Palette over(Palette p) {
        Palette twin = DarkTwin.of(p);
        Palette out = paint(p);
        if (twin != p) {
            DarkTwin.pair(out, paint(twin));
        }
        return out;
    }

    private Palette paint(Palette p) {
        boolean dark = dark(p);
        int c = dark ? this.dark : this.light;
        int deep = Shapes.mix(c, 0xFF000000, 0.25F);
        int onFill = Shapes.contrast(0xFFFFFFFF, c) >= Shapes.contrast(0xFF09090B, c) ? 0xFFFFFFFF : 0xFF09090B;
        int hover = Shapes.mix(c, onFill == 0xFFFFFFFF ? 0xFF000000 : 0xFFFFFFFF, 0.15F);
        return OfficialPalettes.derive(p, new Palette(p.frame(), p.frameDark(), p.frameLight(), p.trim(),
                p.surface(), p.surfaceEdge(), p.section(), p.well(), p.wellEdge(),
                p.text(), p.textSoft(), p.textFaint(),
                p.raised(), p.raisedHover(), p.raisedText(),
                c, hover, onFill,
                c, deep,
                p.good(), p.warn(), p.bad(),
                p.destructive(), p.destructiveHover(), p.destructiveText(),
                p.track(), p.disabled(), p.disabledText(), p.shadow(), p.link(), p.backdrop(), p.line(), 0, p.slot()));
    }

    /** {@code p} under the accent saved as {@code id}; {@code p} itself for none. */
    public static Palette apply(Palette p, String id) {
        Accent a = byId(id);
        return a == null ? p : a.over(p);
    }
}
