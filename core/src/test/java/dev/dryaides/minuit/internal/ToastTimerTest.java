package dev.dryaides.minuit.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToastTimerTest {

    /** ToastSpec: millis is how long it stays before the player's Notification Time option scales it. */
    @Test
    void hidesAfterMillisTimesMultiplierAndRestartsOnSameKey() {
        ToastTimer timer = new ToastTimer();
        assertTrue(timer.visible(0, 5000, 2.0));
        assertTrue(timer.visible(9999, 5000, 2.0));
        assertFalse(timer.visible(10000, 5000, 2.0));

        ToastTimer replaced = new ToastTimer();
        assertTrue(replaced.visible(3000, 5000, 1.0));
        replaced.restart();
        assertTrue(replaced.visible(3000, 5000, 1.0));
        assertTrue(replaced.visible(7999, 5000, 1.0));
        assertFalse(replaced.visible(8000, 5000, 1.0));
    }
}
