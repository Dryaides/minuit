package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Spring3Test {

    /** The exact step is the point of the class: one long frame must land where a thousand short ones do. */
    @Test
    void oneSlowFrameLandsWhereManyFastOnesDo() {
        Spring3 slow = new Spring3();
        Spring3 fast = new Spring3();
        slow.update(10, -4, 2, 8, 0.5);
        for (int i = 0; i < 500; i++) {
            fast.update(10, -4, 2, 8, 0.001);
        }
        assertEquals(fast.x(), slow.x(), 1e-9);
        assertEquals(fast.y(), slow.y(), 1e-9);
        assertEquals(fast.speed(), slow.speed(), 1e-9);
    }

    /** Critical damping: the point closes on the target from one side and never passes it. */
    @Test
    void neverOvershootsTheTarget() {
        Spring3 s = new Spring3();
        s.snap(0, 0, 0);
        for (int i = 0; i < 200; i++) {
            s.update(1, 0, 0, 12, 1 / 60.0);
            assertTrue(s.x() <= 1, "passed the target at frame " + i);
        }
        assertEquals(1, s.x(), 1e-3);
    }
}
