package dev.dryaides.minuit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TipPlaceTest {

    @Test
    void tipNearRightEdgeOfAreaFlipsLeftAndStaysInside() {
        Box area = new Box("canvas", 0, 0, 200, 150);
        int[] at = TipDelay.place(190, 50, 60, 20, area, 0, 0);
        assertTrue(at[0] + 60 <= 200, "right edge inside the 200 px area");
        assertTrue(at[0] < 190, "left of the pointer");
    }

    @Test
    void noAreaAndNoScreenLeavesTheTipWhereItWas() {
        int[] at = TipDelay.place(190, 50, 60, 20, null, 0, 0);
        assertEquals(202, at[0]);
    }
}
