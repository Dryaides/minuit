package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A vanilla inventory's two ways to move a stack: click, click; or press, drag, release. */
class CarryTest {

    @Test
    void clickToPickThenClickToPut() {
        assertEquals(Carry.Act.PICK, Carry.press(false, Carry.Under.SLOT, true));
        assertEquals(Carry.Act.KEEP, Carry.release(true, Carry.Under.SLOT, true));
        assertEquals(Carry.Act.PLACE, Carry.press(true, Carry.Under.SLOT, false));
    }

    @Test
    void dragEndsWhereItIsReleased() {
        assertEquals(Carry.Act.DROP, Carry.release(true, Carry.Under.TARGET, false));
        assertEquals(Carry.Act.RETURN, Carry.release(true, Carry.Under.NOTHING, false));
    }

    @Test
    void anEmptySlotGivesNothingToPick() {
        assertEquals(Carry.Act.NOTHING, Carry.press(false, Carry.Under.SLOT, false));
    }
}
