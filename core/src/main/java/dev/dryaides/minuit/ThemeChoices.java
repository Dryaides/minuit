package dev.dryaides.minuit;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Every look the player picked: one per mod, and a global preference for the mods with none.
 * A mod with neither wears the theme it registered. Kept in {@code config/minuit-client.json}.
 *
 * @param global the choice for every mod without its own, or null
 * @param mods   the choice of each mod, by mod id (the id its screens open with {@link Themes#byId})
 */
public record ThemeChoices(ThemeChoice global, Map<String, ThemeChoice> mods) {

    /** Nothing picked yet: every mod wears its own theme. */
    public static final ThemeChoices NONE = new ThemeChoices(null, Map.of());

    public ThemeChoices {
        Map<String, ThemeChoice> copy = new TreeMap<>();
        mods.forEach((id, choice) -> {
            if (choice != null) {
                copy.put(id.toLowerCase(Locale.ROOT), choice);
            }
        });
        mods = Collections.unmodifiableMap(copy);
    }

    /** The look of {@code modId}'s screens: its own choice, else the global one, else null (its own theme). */
    public ThemeChoice forMod(String modId) {
        ThemeChoice own = this.mods.get(modId.toLowerCase(Locale.ROOT));
        return own != null ? own : this.global;
    }

    /** These choices with {@code modId}'s replaced. */
    public ThemeChoices with(String modId, ThemeChoice choice) {
        Map<String, ThemeChoice> copy = new TreeMap<>(this.mods);
        copy.put(modId.toLowerCase(Locale.ROOT), choice);
        return new ThemeChoices(this.global, copy);
    }

    /** {@code choice} for every mod: the global preference, and no mod keeps another. */
    public ThemeChoices everyMod(ThemeChoice choice) {
        return new ThemeChoices(choice, Map.of());
    }
}
