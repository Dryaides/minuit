package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Accent;
import dev.dryaides.minuit.internal.ImageFamily;
import dev.dryaides.minuit.internal.ThemeFiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The presets, the families a player picks from, and the registry a screen is opened through.
 * Every colour comes from the theme files (THEMES.md); the presets read the kit's own.
 *
 * <p>The presets are named after their material, not after a mod: the kit never names a consumer.
 * A mod registers the preset it wears under its own id at client init,
 * {@code Themes.register(Themes.LEATHER.as("mymod"))}. That id is the mod's: every screen opened
 * with it wears the player's choice for that mod ({@link ThemeChoices}), and the mod's registered
 * theme until the player picks one. {@link #byId} gives the picked family's colours and shapes,
 * with the mod's own icons kept.
 */
public final class Themes {

    /** Leather cover, paper page, ink: leather's light half. */
    public static final Theme LEATHER = preset("leather", "paper", ThemeChoice.Variant.LIGHT, FrameStyles.LEATHER);

    /** Dark slate and a teal signal line, slate's dark half. */
    public static final Theme SLATE = preset("slate", "teal", ThemeChoice.Variant.DARK, FrameStyles.SLATE);

    /** Soft minimalist, its dark half when the chroma ships one (auto). */
    public static final Theme MINIMALIST = preset("soft", "minimalist", ThemeChoice.Variant.AUTO, FrameStyles.SOFT);

    /** The game's own grey panel and bevel, its light half. The kit's own: used when nothing else is asked for. */
    public static final Theme VANILLA = preset("vanilla", "vanilla", ThemeChoice.Variant.LIGHT, FrameStyles.VANILLA);

    private static final Map<String, Theme> BY_ID = new ConcurrentHashMap<>();
    /** The ids a mod registered, the kit's presets left out. */
    private static final Set<String> BY_MOD = ConcurrentHashMap.newKeySet();
    /** The name each mod gave its own palette, by mod id. */
    private static final Map<String, String> NAMES = new ConcurrentHashMap<>();
    /** Each registered palette as a chroma, by mod id, made on first use. */
    private static final Map<String, ThemeFamily.Chroma> OWN = new ConcurrentHashMap<>();
    /** Each family's frame in the glass mode, kept so its flattened parts outlive a frame. */
    private static final Map<FrameStyle, GlassFrame> GLASS = new ConcurrentHashMap<>();
    private static final List<ThemeFamily> FAMILIES = new ArrayList<>();
    private static volatile ThemeChoices choices = ThemeChoices.NONE;
    private static volatile int generation;

    static {
        for (Theme preset : List.of(LEATHER, SLATE, MINIMALIST, VANILLA)) {
            BY_ID.put(preset.id(), preset);
        }
    }

    private Themes() {
    }

    /** A preset is one half of one of the kit's own chroma files, drawn in a style. */
    private static Theme preset(String id, String chroma, ThemeChoice.Variant variant, FrameStyle frame) {
        return new Theme(id, ThemeFiles.builtIn(chroma, variant), frame);
    }

    /**
     * The kit's default look, today {@link #VANILLA}. A mod that wants to follow the kit's default as it
     * changes registers {@code Themes.defaultTheme().as("mymod")} instead of naming a preset.
     */
    public static Theme defaultTheme() {
        return VANILLA;
    }

    /** Client init. A second registration under the same id replaces the first. */
    public static void register(Theme theme) {
        BY_ID.put(theme.id(), theme);
        BY_MOD.add(theme.id());
        OWN.remove(theme.id());
        generation++;
    }

    /**
     * {@link #register(Theme)}, with the name the theme page gives the mod's palette in its colours, e.g. the mod's
     * display name. Without one the page shows the id, capitalised.
     */
    public static void register(Theme theme, String name) {
        if (name != null && !name.isBlank()) {
            NAMES.put(theme.id(), name.trim());
        }
        register(theme);
    }

    /** The name of {@code modId}'s own palette on the theme page. */
    public static String ownName(String modId) {
        String name = NAMES.get(modId);
        if (name != null) {
            return name;
        }
        return modId.isEmpty() ? modId : Character.toUpperCase(modId.charAt(0)) + modId.substring(1);
    }

    /**
     * The family whose shapes {@code modId}'s registered theme wears, among those its page offers, or null when it
     * registered none or draws with a frame no family has. The theme page's "Default" card stands for it.
     */
    public static ThemeFamily ownFamily(String modId) {
        if (!isRegistered(modId)) {
            return null;
        }
        FrameStyle frame = registered(modId).frame();
        for (ThemeFamily family : families(modId)) {
            if (family.frame() == frame) {
                return family;
            }
        }
        return null;
    }

    /**
     * The palette {@code own} registered as a chroma ({@link ThemeChoice#OWN_CHROMA}): its one half, light or dark
     * by its surface, and the glass mode derived from it.
     */
    public static ThemeFamily.Chroma ownChroma(Theme own) {
        ThemeFamily.Chroma cached = OWN.get(own.id());
        if (cached != null && cached.palette(ThemeChoice.Variant.AUTO) == own.palette()) {
            return cached;
        }
        Palette p = own.palette();
        boolean dark = Shapes.luminance(p.surface()) < 0.18;
        ThemeFamily.Chroma chroma = new ThemeFamily.Chroma(ThemeChoice.OWN_CHROMA, "", dark ? null : p, dark ? p : null);
        OWN.put(own.id(), chroma);
        return chroma;
    }

    /**
     * The families read from the theme files; they replace the ones before.
     * {@code client.ThemeReload} calls it on every resource reload, a bench may too.
     */
    public static void load(List<ThemeFamily> families) {
        synchronized (FAMILIES) {
            FAMILIES.clear();
            FAMILIES.addAll(families);
        }
        GLASS.clear();
        generation++;
    }

    /** The families {@code modId}'s theme page offers, in its order: the shared ones and its own. */
    public static List<ThemeFamily> families(String modId) {
        synchronized (FAMILIES) {
            return FAMILIES.stream().filter(f -> f.shared() || f.owner().equals(modId)).toList();
        }
    }

    /** The family {@code modId}'s theme page offers under {@code id}, its own first, or null. */
    public static ThemeFamily family(String modId, String id) {
        ThemeFamily shared = null;
        for (ThemeFamily family : families(modId)) {
            if (family.id().equals(id)) {
                if (!family.shared()) {
                    return family;
                }
                shared = family;
            }
        }
        return shared;
    }

    /** Sets the player's choices; {@code client.MinuitClient} calls it, a bench may too. */
    public static void choose(ThemeChoices picked) {
        choices = picked == null ? ThemeChoices.NONE : picked;
        generation++;
    }

    /**
     * A number that moves whenever {@link #byId} may answer differently: a registration, a reload of the
     * families, a new player choice. A caller that keeps a resolved theme across frames, such as
     * {@link HudLayer}, resolves it again when this changes.
     */
    public static int generation() {
        return generation;
    }

    public static ThemeChoices choices() {
        return choices;
    }

    /** True when a mod called {@link #register} under {@code id}, whatever palette it gave. */
    public static boolean isRegistered(String id) {
        return id != null && BY_MOD.contains(id);
    }

    /** The theme a mod registered under {@code id}, the player's choice left out, or the default look under {@code id}. */
    public static Theme registered(String id) {
        Theme theme = id == null ? null : BY_ID.get(id);
        return theme != null ? theme : defaultTheme().as(id == null ? defaultTheme().id() : id);
    }

    /** The theme a screen opened with {@code id} wears: {@link #registered} under the player's choice for that mod. */
    public static Theme byId(String id) {
        Theme own = registered(id);
        return resolve(own, choices.forMod(own.id()));
    }

    /**
     * {@code own} under {@code picked}: the picked family's shapes and chroma's palette (the family's
     * first chroma for an unknown one; {@code own}'s palette for {@link ThemeChoice#OWN_CHROMA} when its mod
     * registered one) under the picked accent, {@code own}'s id and icons. {@code own} unchanged for no
     * choice or a family {@code own}'s mod is not offered.
     */
    public static Theme resolve(Theme own, ThemeChoice picked) {
        ThemeFamily family = picked == null ? null : family(own.id(), picked.family());
        if (family == null) {
            return own;
        }
        ThemeFamily.Chroma chroma = picked.ownChroma() && isRegistered(own.id())
                ? ownChroma(own) : family.chroma(picked.chroma());
        if (chroma == null) {
            chroma = family.first();
        }
        FrameStyle frame = picked.variant() == ThemeChoice.Variant.GLASS
                || RasterCache.ENABLED && !(family.frame() instanceof ImageFamily)
                ? GLASS.computeIfAbsent(family.frame(), GlassFrame::new) : family.frame();
        return new Theme(own.id(), Accent.apply(chroma.palette(picked.variant()), picked.accent()), frame, own.icons());
    }
}
