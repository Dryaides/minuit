package dev.dryaides.minuit;

import java.util.List;

/** The items the game knows, for a picker: the game plugs in its registry, a bench a fixed list. */
public interface ItemLookup {

    /** Every item id in registry order, air left out. */
    List<String> items();

    /** The ids whose item places a block. */
    List<String> blocks();

    /** The item's name in the player's language. */
    String name(String id);

    /** True when item {@code id} is in item tag {@code tag} ({@code namespace:path}); false where tags are unknown. */
    default boolean tagged(String id, String tag) {
        return false;
    }

    ItemLookup NONE = new ItemLookup() {
        @Override
        public List<String> items() {
            return List.of();
        }

        @Override
        public List<String> blocks() {
            return List.of();
        }

        @Override
        public String name(String id) {
            return id;
        }
    };

    /** What the host plugged in, {@link #NONE} until then. */
    static ItemLookup game() {
        return Holder.game;
    }

    /** Plugged in by the host; an interface cannot hold a settable field, hence the method. */
    static void plugIn(ItemLookup lookup) {
        Holder.game = lookup == null ? NONE : lookup;
    }

    /** Holds {@link #game()}. */
    final class Holder {
        private static volatile ItemLookup game = NONE;

        private Holder() {
        }
    }
}
