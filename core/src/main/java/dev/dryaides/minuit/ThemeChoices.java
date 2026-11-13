package dev.dryaides.minuit;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Every look the player picked: one per mod, and a global preference for the mods with none.
 * A mod with neither wears the theme it registered. Kept in {@code config/minuit-client.json}.
 *
 * @param global the choice for every mod without its own, or null
 * @param mods   the choice of each mod, by mod id (the id its screens open with {@link Themes#byId})
 * @param ownLook ids of the mods the player sent back to their own theme while a global choice is set: the
 *               marker wins over the global choice, a choice in {@code mods} wins over the marker
 */
@Internal
public record ThemeChoices(ThemeChoice global, Map<String, ThemeChoice> mods, Set<String> ownLook) {

    public ThemeChoices(ThemeChoice global, Map<String, ThemeChoice> mods) {
        this(global, mods, Set.of());
    }

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
        Set<String> marks = new TreeSet<>();
        ownLook.forEach(id -> marks.add(id.toLowerCase(Locale.ROOT)));
        ownLook = Collections.unmodifiableSet(marks);
    }

    /** The look of {@code modId}'s screens: its own choice, else null if marked own-look, else the global one, else null (its own theme). */
    public ThemeChoice forMod(String modId) {
        String id = modId.toLowerCase(Locale.ROOT);
        ThemeChoice own = this.mods.get(id);
        if (own != null) {
            return own;
        }
        return this.ownLook.contains(id) ? null : this.global;
    }

    /** These choices with {@code modId}'s replaced. */
    public ThemeChoices with(String modId, ThemeChoice choice) {
        Map<String, ThemeChoice> copy = new TreeMap<>(this.mods);
        copy.put(modId.toLowerCase(Locale.ROOT), choice);
        Set<String> marks = new TreeSet<>(this.ownLook);
        marks.remove(modId.toLowerCase(Locale.ROOT));
        return new ThemeChoices(this.global, copy, marks);
    }

    /**
     * These choices with {@code modId}'s entry removed, so it wears its own theme again. While a global choice
     * is set, the mod is marked own-look so the global one does not reach it.
     */
    public ThemeChoices without(String modId) {
        String id = modId.toLowerCase(Locale.ROOT);
        Map<String, ThemeChoice> copy = new TreeMap<>(this.mods);
        copy.remove(id);
        Set<String> marks = new TreeSet<>(this.ownLook);
        if (this.global != null) {
            marks.add(id);
        }
        return new ThemeChoices(this.global, copy, marks);
    }

    /** {@code choice} for every mod: the global preference, and no mod keeps another. */
    public ThemeChoices everyMod(ThemeChoice choice) {
        return new ThemeChoices(choice, Map.of());
    }
}
