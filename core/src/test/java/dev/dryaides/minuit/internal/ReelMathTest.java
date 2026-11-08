package dev.dryaides.minuit.internal;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReelMathTest {

    @Test
    void positionFollowsMecchaCurve() {
        // Meccha: 3200 ms, offset = (1 - (1 - t)^3) * target * step. Hand values for 40 cards of 102 px.
        assertEquals(0.0, ReelMath.offset(ReelMath.progress(0, false), 40, 102), 1e-9);
        assertEquals(0.578125 * 4080, ReelMath.offset(ReelMath.progress(800, false), 40, 102), 1e-9);
        assertEquals(0.875 * 4080, ReelMath.offset(ReelMath.progress(1600, false), 40, 102), 1e-9);
        assertEquals(4080, ReelMath.offset(ReelMath.progress(3200, false), 40, 102), 1e-9);
        assertEquals(4080, ReelMath.offset(ReelMath.progress(9000, false), 40, 102), 1e-9);
        assertEquals(4080, ReelMath.offset(ReelMath.progress(0, true), 40, 102), 1e-9);
    }

    @Test
    void stripPutsTheTargetAfterAMecchaLead() {
        for (long seed = 0; seed < 50; seed++) {
            ReelMath.Strip s = ReelMath.strip(5, 3, new Random(seed));
            assertEquals(3, s.cards().get(s.landing()));
            assertEquals(true, s.landing() >= 30 && s.landing() <= 37, "lead " + s.landing());
            assertEquals(s.landing() + 11, s.cards().size());
        }
    }

    @Test
    void noCardTwiceWithinTheWindow() {
        // Owner's rule: ABA and ABAA never, ABCA fine; window 3 by default, a pool of 2 can only avoid AA.
        for (long seed = 0; seed < 200; seed++) {
            assertSpaced(ReelMath.strip(8, 5, new Random(seed)).cards(), 3);
            assertSpaced(ReelMath.strip(8, 5, new Random(seed), 5).cards(), 5);
            assertSpaced(ReelMath.strip(2, 1, new Random(seed)).cards(), 2);
        }
    }

    private static void assertSpaced(java.util.List<Integer> cards, int window) {
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < Math.min(cards.size(), i + window); j++) {
                assertEquals(true, !cards.get(i).equals(cards.get(j)), "cards " + i + " and " + j + " in " + cards);
            }
        }
    }
}
