package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Accent;
import dev.dryaides.minuit.internal.ThemeFiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static final List<ThemeFamily> FAMILIES = new ArrayList<>();
    private static volatile ThemeChoices choices = ThemeChoices.NONE;

    static {
        register(LEATHER);
        register(SLATE);
        register(MINIMALIST);
        register(VANILLA);
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
    }

    public static ThemeChoices choices() {
        return choices;
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
     * first chroma for an unknown one) under the picked accent, {@code own}'s id and icons. {@code own} unchanged for no
     * choice or a family {@code own}'s mod is not offered.
     */
    public static Theme resolve(Theme own, ThemeChoice picked) {
        ThemeFamily family = picked == null ? null : family(own.id(), picked.family());
        if (family == null) {
            return own;
        }
        ThemeFamily.Chroma chroma = family.chroma(picked.chroma());
        if (chroma == null) {
            chroma = family.first();
        }
        return new Theme(own.id(), Accent.apply(chroma.palette(picked.variant()), picked.accent()), family.frame(),
                own.icons());
    }
}
