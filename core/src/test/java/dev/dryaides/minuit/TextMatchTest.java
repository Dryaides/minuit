package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TextMatchTest {

    /** The labels that match, best first; the order is the rule's: prefix, word start, inside, scattered. */
    private static List<String> search(String query, String... labels) {
        String folded = TextMatch.foldQuery(query);
        List<String> found = new ArrayList<>();
        for (String label : labels) {
            if (TextMatch.rank(TextMatch.fold(label), folded) != TextMatch.NONE) {
                found.add(label);
            }
        }
        found.sort(Comparator.comparingLong(label -> TextMatch.rank(TextMatch.fold(label), folded)));
        return found;
    }

    /** A player typing "st" wants Stone, then a word starting with it, then the labels holding it, earliest first. */
    @Test
    void prefixThenWordThenInside() {
        assertEquals(List.of("Stone", "Smooth Stone", "Sandstone", "Cobblestone"),
                search("st", "Sandstone", "Dirt", "Cobblestone", "Smooth Stone", "Stone"));
    }

    /** A French player types without accents and still finds the accented label; the mark lands on its É. */
    @Test
    void accentsFoldAway() {
        assertEquals(List.of("Éclair"), search("eclair", "Éclair", "Pomme"));
        assertArrayEquals(new int[] {0, 1}, TextMatch.marks(TextMatch.fold("Éclair"), TextMatch.foldQuery("ec")));
    }

    /** Letters in order but apart match after any run of them, the tighter fit first; letters out of order do not. */
    @Test
    void scatteredLettersRankLast() {
        assertEquals(List.of("Dirt", "Rotten Flesh", "Redstone Torch"), search("rt", "Redstone Torch", "Rotten Flesh", "Dirt"));
        assertEquals(TextMatch.NONE, TextMatch.rank(TextMatch.fold("Dirt"), TextMatch.foldQuery("tdr")));
        assertNotEquals(TextMatch.NONE, TextMatch.rank(TextMatch.fold("Dirt"), TextMatch.foldQuery("drt")));
    }
}
