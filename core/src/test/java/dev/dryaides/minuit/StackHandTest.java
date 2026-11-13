package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cases from vanilla's container clicks (AbstractContainerMenu.doClick: PICKUP, QUICK_MOVE with ChestMenu.quickMoveStack,
 * QUICK_CRAFT, PICKUP_ALL, SWAP, THROW; the gestures from AbstractContainerScreen): each expected state is the game's.
 */
class StackHandTest {

    private static StackHand.Stack s(String id, int n) {
        return new StackHand.Stack(id, n);
    }

    /** Left click with 10 stone onto 60 stone: the slot fills to 64, the 6 that do not fit stay at the mouse. */
    @Test
    void leftClickMergesUpToTheMaximumAndKeepsTheRest() {
        StackHand hand = new StackHand(1).set(0, s("minecraft:stone", 60)).carried(s("minecraft:stone", 10));
        hand.click(0, 0);
        assertEquals(s("minecraft:stone", 64), hand.get(0));
        assertEquals(s("minecraft:stone", 6), hand.carried());
    }

    /** Right click on 7 with an empty hand takes 4 (half, rounded up) and leaves 3; a second right click puts one back. */
    @Test
    void rightClickTakesHalfRoundedUpThenPlacesOne() {
        StackHand hand = new StackHand(2).set(0, s("minecraft:apple", 7));
        hand.click(0, 1);
        assertEquals(s("minecraft:apple", 3), hand.get(0));
        assertEquals(s("minecraft:apple", 4), hand.carried());
        hand.click(1, 1);
        assertEquals(s("minecraft:apple", 1), hand.get(1));
        assertEquals(s("minecraft:apple", 3), hand.carried());
    }

    /** Left click with 12 ender pearls onto a sword swaps them; then 10 pearls onto those 12 fill to 16, the item's own maximum. */
    @Test
    void differentItemsSwapAndASlotHoldsTheItemsOwnMaximum() {
        StackHand hand = new StackHand(2, id -> id.equals("minecraft:ender_pearl") ? 16 : 1)
                .set(0, s("minecraft:iron_sword", 1)).carried(s("minecraft:ender_pearl", 12));
        hand.click(0, 0);
        assertEquals(s("minecraft:ender_pearl", 12), hand.get(0));
        assertEquals(s("minecraft:iron_sword", 1), hand.carried());
        hand.carried(s("minecraft:ender_pearl", 10)).click(0, 0);
        assertEquals(s("minecraft:ender_pearl", 16), hand.get(0));
        assertEquals(s("minecraft:ender_pearl", 6), hand.carried());
        hand.carried(null).click(0, 0);
        assertNull(hand.get(0));
        assertEquals(s("minecraft:ender_pearl", 16), hand.carried());
    }

    /** A 2-row chest (slots 0 to 17) over the inventory bound hotbar first (18 to 26, rows 27 to 53), as the site's demo. */
    private static StackHand chest() {
        return new StackHand(54).containerOver(0, 18, 18);
    }

    /**
     * ChestMenu.quickMoveStack: from the chest, the stack first tops up 60 oak logs in the rows, then the rest
     * lands in the menu's last empty slot, the hotbar's rightmost; with every inventory slot taken it stays.
     */
    @Test
    void shiftClickFromTheChestTopsUpThenFillsTheHotbarFromTheRight() {
        StackHand hand = chest().set(4, s("minecraft:oak_log", 32)).set(30, s("minecraft:oak_log", 60));
        hand.press(4, 0, true, 0);
        hand.release(4, 0, true);
        assertNull(hand.get(4));
        assertEquals(s("minecraft:oak_log", 64), hand.get(30));
        assertEquals(s("minecraft:oak_log", 28), hand.get(26));
        assertNull(hand.carried());

        StackHand full = chest().set(4, s("minecraft:oak_log", 32));
        for (int i = 18; i < 54; i++) {
            full.set(i, s("minecraft:stone", 64));
        }
        full.press(4, 0, true, 0);
        full.release(4, 0, true);
        assertEquals(s("minecraft:oak_log", 32), full.get(4));
    }

    /** From the inventory, Shift + click goes into the chest's first empty slot. */
    @Test
    void shiftClickFromTheInventoryGoesToTheChestsFirstFreeSlot() {
        StackHand hand = chest().set(0, s("minecraft:diamond", 1)).set(19, s("minecraft:bread", 12));
        assertTrue(hand.quickMove(19));
        assertEquals(s("minecraft:bread", 12), hand.get(1));
        assertNull(hand.get(19));
    }

    /**
     * QUICK_CRAFT: 10 carried, left-dragged over 3 slots, puts 3 in each and keeps 1; right-dragged, one each.
     * A slot already at 63 of the same item takes only what fits up to 64.
     */
    @Test
    void dragSpreadsEvenlyWithLeftAndOneEachWithRight() {
        StackHand left = chest().carried(s("minecraft:stone", 10));
        left.press(0, 0, false, 0);
        left.drag(0);
        left.drag(1);
        left.drag(2);
        left.release(2, 0, false);
        assertEquals(s("minecraft:stone", 3), left.get(0));
        assertEquals(s("minecraft:stone", 3), left.get(2));
        assertEquals(s("minecraft:stone", 1), left.carried());

        StackHand right = chest().carried(s("minecraft:stone", 10));
        right.press(0, 1, false, 0);
        right.drag(0);
        right.drag(1);
        right.drag(2);
        right.release(2, 1, false);
        assertEquals(s("minecraft:stone", 1), right.get(1));
        assertEquals(s("minecraft:stone", 7), right.carried());

        StackHand capped = chest().set(0, s("minecraft:stone", 63)).carried(s("minecraft:stone", 64));
        capped.press(0, 0, false, 0);
        capped.drag(0);
        capped.drag(1);
        capped.release(1, 0, false);
        assertEquals(s("minecraft:stone", 64), capped.get(0));
        assertEquals(s("minecraft:stone", 32), capped.get(1));
        assertEquals(s("minecraft:stone", 31), capped.carried());
    }

    /** A click with an empty hand acts on the press only: the release of the same click does not put the stack back. */
    @Test
    void aPlainClickPicksUpOnceAndAClickWithAStackPlacesOnRelease() {
        StackHand hand = chest().set(0, s("minecraft:apple", 3));
        hand.press(0, 0, false, 0);
        hand.release(0, 0, false);
        assertNull(hand.get(0));
        assertEquals(s("minecraft:apple", 3), hand.carried());
        hand.press(5, 0, false, 1000);
        hand.release(5, 0, false);
        assertEquals(s("minecraft:apple", 3), hand.get(5));
        assertNull(hand.carried());
    }

    /** PICKUP_ALL: double-click gathers the same item, the 5 of a partial stack before the full 64. */
    @Test
    void doubleClickGathersPartialStacksBeforeFullOnes() {
        StackHand hand = chest().set(0, s("minecraft:apple", 10)).set(1, s("minecraft:apple", 64))
                .set(2, s("minecraft:apple", 5));
        hand.press(0, 0, false, 1000);
        hand.release(0, 0, false);
        hand.press(0, 0, false, 1100);
        hand.release(0, 0, false);
        assertEquals(s("minecraft:apple", 64), hand.carried());
        assertNull(hand.get(2));
        assertEquals(s("minecraft:apple", 15), hand.get(1));
    }

    /** SWAP and THROW, empty hand: key 3 over a chest slot trades with hotbar slot 2; Q takes one item away. */
    @Test
    void numberKeySwapsWithTheHotbarAndQThrowsOne() {
        StackHand hand = chest().set(5, s("minecraft:diamond", 1)).set(20, s("minecraft:torch", 64));
        assertTrue(hand.swap(5, 2));
        assertEquals(s("minecraft:torch", 64), hand.get(5));
        assertEquals(s("minecraft:diamond", 1), hand.get(20));
        assertTrue(hand.throwFrom(5, false));
        assertEquals(s("minecraft:torch", 63), hand.get(5));
        hand.carried(s("minecraft:apple", 1));
        assertFalse(hand.swap(5, 0));
    }

    /**
     * AbstractContainerScreen.mouseReleased clears lastClickTime when the hand ends empty: placing 10 dirt in
     * an empty slot and pressing it again within 250 ms picks those 10 back up, no PICKUP_ALL of the rest.
     */
    @Test
    void aPressAfterPlacingIsAPickupNotADoubleClick() {
        StackHand hand = chest().set(1, s("minecraft:dirt", 20)).carried(s("minecraft:dirt", 10));
        hand.press(0, 0, false, 1000);
        hand.release(0, 0, false);
        hand.press(0, 0, false, 1100);
        hand.release(0, 0, false);
        assertEquals(s("minecraft:dirt", 10), hand.carried());
        assertEquals(s("minecraft:dirt", 20), hand.get(1));
    }

    /** Keys match by code as KeyMapping does: AZERTY's top row sends GLFW 49 to 57 too, and a host rebinds them. */
    @Test
    void hotbarAndDropKeysMatchByKeyCode() {
        StackHand hand = new StackHand(10).hotbar(1).set(0, s("minecraft:diamond", 1)).set(3, s("minecraft:torch", 64));
        hand.bind(new SlotGridComponent(new Box("g", 0, 0, 180, 18), 1, 10), 0);
        assertTrue(hand.keyPressed(5, 5, 51));
        assertEquals(s("minecraft:torch", 64), hand.get(0));
        assertTrue(hand.keyPressed(5, 5, 81));
        assertEquals(s("minecraft:torch", 63), hand.get(0));
        hand.keys(new int[] {90, 91, 92, 93, 94, 95, 96, 97, 98}, 70);
        assertFalse(hand.keyPressed(5, 5, 51));
        assertTrue(hand.keyPressed(5, 5, 92));
        assertEquals(s("minecraft:diamond", 1), hand.get(0));
    }
}
