package bench.demos;

import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.ItemLookup;
import java.util.List;
import java.util.Locale;

/** Game data the demos show: item names as the game writes them and the default player skins. Not a demo. */
public final class Mc {
    /** The game's nine default skins, by the names it gives them. */
    public static final List<String> PLAYERS = List.of("Steve", "Alex", "Ari", "Efe", "Kai", "Makena", "Noor", "Sunny", "Zuri");

    private Mc() {
    }

    /** The item's name from the plugged lookup; without one, the id's path in title case, as most English names read. */
    public static String name(String id) {
        String n = ItemLookup.game().name(id);
        if (n != null && !n.isBlank() && !n.equals(id)) {
            return n;
        }
        StringBuilder out = new StringBuilder();
        for (String word : id.substring(id.indexOf(':') + 1).split("_")) {
            if (!word.isEmpty()) {
                out.append(out.length() > 0 ? " " : "").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    /** The default skin of {@code player}, one of {@link #PLAYERS}. */
    public static AsyncImage skin(String player) {
        return AsyncImage.of("minecraft:textures/entity/player/wide/" + player.toLowerCase(Locale.ROOT) + ".png", 64, 64);
    }
}
