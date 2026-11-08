package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SpinnerComponentTest {

    @Test
    void theLitDotAdvancesOneStepPerHundredMsAndWrapsAfterEight() {
        assertEquals(0, SpinnerComponent.head(0));
        assertEquals(2, SpinnerComponent.head(250));
        assertEquals(7, SpinnerComponent.head(799));
        assertEquals(0, SpinnerComponent.head(800));
    }

    @Test
    void theRingRunsClockwiseFromTheTopThroughTheFourCompassPoints() {
        int size = 16;
        int dot = 2;
        assertArrayEquals(new int[]{7, 0}, SpinnerComponent.dotAt(0, size, dot));
        assertArrayEquals(new int[]{14, 7}, SpinnerComponent.dotAt(2, size, dot));
        assertArrayEquals(new int[]{7, 14}, SpinnerComponent.dotAt(4, size, dot));
        assertArrayEquals(new int[]{0, 7}, SpinnerComponent.dotAt(6, size, dot));
    }
}
