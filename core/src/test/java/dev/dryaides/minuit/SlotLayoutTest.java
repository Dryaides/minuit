package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Oracle: vanilla {@code ChestMenu} and {@code AbstractContainerMenu.addStandardInventorySlots}. */
class SlotLayoutTest {

    private static SlotLayout.Cell find(List<SlotLayout.Cell> cells, int index) {
        return cells.stream().filter(c -> c.index() == index).findFirst().orElseThrow();
    }

    /** A 3-row chest: grid at (8, 18), inventory 13 under it, hotbar 58 under the inventory. */
    @Test
    void threeRowChestMatchesVanilla() {
        List<SlotLayout.Cell> chest = SlotLayout.cells(SlotLayout.grid("chest", 8, 18, 3, 9), 0);
        List<SlotLayout.Cell> player = SlotLayout.playerCells(
                SlotLayout.playerInventory("inv", 8, SlotLayout.inventoryTop(18, 3)));
        assertEquals(new SlotLayout.Cell(0, 8, 18), chest.get(0));
        assertEquals(new SlotLayout.Cell(26, 152, 54), chest.get(26));
        assertEquals(new SlotLayout.Cell(9, 8, 85), find(player, 9));
        assertEquals(new SlotLayout.Cell(0, 8, 143), find(player, 0));
        List<SlotLayout.Cell> all = new ArrayList<>(chest);
        all.addAll(player);
        assertEquals(63, all.size());
    }

    @Test
    void sixRowChestInventoryStartsAt139() {
        List<SlotLayout.Cell> player = SlotLayout.playerCells(
                SlotLayout.playerInventory("inv", 8, SlotLayout.inventoryTop(18, 6)));
        assertEquals(new SlotLayout.Cell(9, 8, 139), find(player, 9));
    }
}
