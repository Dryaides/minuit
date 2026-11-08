package dev.dryaides.minuit.internal;

import java.util.List;
import java.util.Random;

/**
 * The motion of {@link dev.dryaides.minuit.ReelComponent}, after Meccha Crafteleon's map roulette: a strip
 * that eases out over 3200 ms onto its target card, one tick each time a new card reaches the marker.
 */
public final class ReelMath {

    public static final long SPIN_MS = 3200;
    /** Cards drawn at random before the target: 30, plus up to 7 more. */
    public static final int LEAD_MIN = 30;
    public static final int LEAD_SPREAD = 8;
    /** Cards after the target, so the strip does not end at the marker. */
    public static final int TRAIL = 10;

    private ReelMath() {
    }

    /** Ease-out cubic: fast at first, slowing to a stop at 1. */
    public static double ease(double t) {
        double u = 1.0 - t;
        return 1.0 - u * u * u;
    }

    /** Progress 0 to 1 after {@code elapsed} ms; reduced motion jumps to 1. */
    public static double progress(long elapsed, boolean reduced) {
        return reduced ? 1.0 : Maths.clamp(elapsed / (double) SPIN_MS, 0.0, 1.0);
    }

    /** How far the strip has moved, in pixels, when it lands {@code target} cards along at {@code step} px each. */
    public static double offset(double t, int target, int step) {
        return ease(t) * target * (double) step;
    }

    /** The index of the card under the marker. */
    public static int under(double offset, int step) {
        return (int) Math.round(offset / step);
    }

    /** No card twice among this many consecutive cards, unless the component sets another window. */
    public static final int NO_REPEAT = 3;

    public static Strip strip(int size, int target, Random rng) {
        return strip(size, target, rng, NO_REPEAT);
    }

    /**
     * A strip of indices into a pool of {@code size} cards: random lead, then {@code target}, then a
     * random tail, with no card twice among any {@code window} consecutive ones (capped at {@code size};
     * 1 allows repeats). The target sits at index {@link Strip#landing}. The same seed gives the same
     * strip, so every player of a room can spin the one the server drew.
     */
    public static Strip strip(int size, int target, Random rng, int window) {
        int w = Maths.clamp(window, 1, size);
        int lead = LEAD_MIN + rng.nextInt(LEAD_SPREAD);
        int[] cards = new int[lead + 1 + TRAIL];
        cards[lead] = target;
        // The lead is drawn backwards from the target, so each card only has to avoid the w - 1 after it
        // and a pick always exists.
        for (int i = lead - 1; i >= 0; i--) {
            cards[i] = pick(size, rng, cards, i + 1, Math.min(cards.length, i + w));
        }
        for (int i = lead + 1; i < cards.length; i++) {
            cards[i] = pick(size, rng, cards, Math.max(0, i - w + 1), i);
        }
        return new Strip(java.util.Arrays.stream(cards).boxed().toList(), lead);
    }

    /** A random card outside {@code cards[from, to)}, which holds distinct cards. */
    private static int pick(int size, Random rng, int[] cards, int from, int to) {
        int n = rng.nextInt(size - (to - from));
        for (int c = 0; c < size; c++) {
            boolean taken = false;
            for (int j = from; j < to; j++) {
                taken |= cards[j] == c;
            }
            if (!taken && n-- == 0) {
                return c;
            }
        }
        throw new IllegalStateException();
    }

    /** @param cards indices into the pool; @param landing where the target sits in {@code cards} */
    public record Strip(List<Integer> cards, int landing) {
    }
}
