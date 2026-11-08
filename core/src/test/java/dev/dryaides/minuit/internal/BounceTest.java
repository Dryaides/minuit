package dev.dryaides.minuit.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BounceTest {

    /**
     * The owner's rule for a 12 px knob: travel to the end, swing 2 px past it, 1 px short of it, rest. By hand at 60 ms the ease
     * is 1 - 0.5^3 = 0.875, so 10.5 rounds to 11 px.
     */
    @Test
    void knobSwingsTwoPastThenOneShortThenRests() {
        int[][] expected = {{0, 0}, {60, 11}, {130, 14}, {190, 11}, {230, 12}, {1000, 12}};
        for (int[] e : expected) {
            assertEquals(e[1], Bounce.knob(e[0], 12), "at " + e[0] + " ms");
        }
    }

    @Test
    void popIsOnePixelUpThenRest() {
        assertEquals(-1, Bounce.pop(0));
        assertEquals(-1, Bounce.pop(50));
        assertEquals(0, Bounce.pop(70));
    }
}
