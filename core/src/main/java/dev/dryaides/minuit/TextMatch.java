package dev.dryaides.minuit;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The text search of the list widgets: folds text, ranks a label against a query and finds the letters
 * to mark. Whole-prefix matches rank first, then the start of a word, then anywhere, then scattered letters,
 * then a match in a keyword. Spaces in the query only count for matches in a row.
 */
final class TextMatch {
    static final long NONE = -1L;
    private static final int PREFIX = 0;
    private static final int WORD = 1;
    private static final int INSIDE = 2;
    private static final int SCATTERED = 3;
    static final int KEYWORD = 4;
    private static final int INDEX_BITS = 31;
    private static final int GAP_BITS = 20;
    static final long INDEX_MASK = (1L << INDEX_BITS) - 1;
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private TextMatch() {
    }

    /** A label ready to search: lower case, no accents, and each char's place in the label when they differ. */
    static final class Folded {
        final String text;
        final int[] map;

        Folded(String text, int[] map) {
            this.text = text;
            this.map = map;
        }
    }

    static Folded fold(String text) {
        int length = text.length();
        boolean plain = true;
        for (int i = 0; i < length && plain; i++) {
            plain = text.charAt(i) < 0x80;
        }
        if (plain) {
            return new Folded(text.toLowerCase(Locale.ROOT), null);
        }
        StringBuilder out = new StringBuilder(length);
        int[] places = new int[length * 2 + 1];
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            String bare = c < 0x80 ? String.valueOf(c)
                    : Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            bare = MARKS.matcher(bare).replaceAll("");
            bare = bare.toLowerCase(Locale.ROOT);
            for (int k = 0; k < bare.length(); k++) {
                if (out.length() >= places.length) {
                    places = Arrays.copyOf(places, places.length * 2);
                }
                places[out.length()] = i;
                out.append(bare.charAt(k));
            }
        }
        return new Folded(out.toString(), Arrays.copyOf(places, out.length()));
    }

    static String foldQuery(String query) {
        return fold(query.strip()).text;
    }

    private static boolean wordStart(String text, int at) {
        return at == 0 || !Character.isLetterOrDigit(text.charAt(at - 1));
    }

    static long rankOf(int tier, int looseness) {
        long gap = Math.min(looseness, (1 << GAP_BITS) - 1);
        return ((long) tier << (GAP_BITS + INDEX_BITS)) | (gap << INDEX_BITS);
    }

    /** The rank with room for an index in its low bits, or {@link #NONE}. */
    static long rank(String text, String wanted) {
        return rank(text, wanted, null);
    }

    /** {@link #rank(String, String)} on a label folded once by {@link #fold}. */
    static long rank(Folded label, String wanted) {
        return rank(label.text, wanted, null);
    }

    private static long rank(String text, String wanted, int[][] out) {
        int count = wanted.length();
        if (count == 0) {
            return rankOf(PREFIX, 0);
        }
        if (text.startsWith(wanted)) {
            return run(out, 0, count, PREFIX, 0);
        }
        int first = text.indexOf(wanted);
        if (first >= 0) {
            for (int at = first; at >= 0; at = text.indexOf(wanted, at + 1)) {
                if (wordStart(text, at)) {
                    return run(out, at, count, WORD, at);
                }
            }
            return run(out, first, count, INSIDE, first);
        }
        return scattered(text, wanted, out);
    }

    private static long run(int[][] out, int from, int count, int tier, int looseness) {
        if (out != null) {
            int[] places = new int[count];
            for (int i = 0; i < count; i++) {
                places[i] = from + i;
            }
            out[0] = places;
        }
        return rankOf(tier, looseness);
    }

    /** Forward to the end of the first fit, then back from there for the tightest fit that ends there. */
    private static long scattered(String text, String wanted, int[][] out) {
        String letters = wanted.indexOf(' ') < 0 ? wanted : wanted.replace(" ", "");
        int count = letters.length();
        if (count == 0) {
            return NONE;
        }
        int at = 0;
        for (int i = 0; i < count; i++) {
            at = text.indexOf(letters.charAt(i), at);
            if (at < 0) {
                return NONE;
            }
            at++;
        }
        int end = at - 1;
        at = end;
        int[] places = out == null ? null : new int[count];
        for (int i = count - 1; i >= 0; i--) {
            at = text.lastIndexOf(letters.charAt(i), at);
            if (places != null) {
                places[i] = at;
            }
            at--;
        }
        int start = at + 1;
        if (out != null) {
            out[0] = places;
        }
        return rankOf(SCATTERED, (end - start + 1 - count) * 2 + (wordStart(text, start) ? 0 : 1));
    }

    /** The label's char indices that matched, ascending; empty for none. */
    static int[] marks(Folded label, String wanted) {
        if (wanted.isEmpty()) {
            return new int[0];
        }
        int[][] out = new int[1][];
        if (rank(label.text, wanted, out) == NONE || out[0] == null) {
            return new int[0];
        }
        int[] result = new int[out[0].length];
        int count = 0;
        for (int place : out[0]) {
            int inLabel = label.map == null ? place : label.map[place];
            if (count == 0 || result[count - 1] != inLabel) {
                result[count++] = inLabel;
            }
        }
        return Arrays.copyOf(result, count);
    }
}
