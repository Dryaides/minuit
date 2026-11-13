package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.EquipmentComponent;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.PlayerInventoryComponent;
import dev.dryaides.minuit.SlotLayout;
import dev.dryaides.minuit.SlotPlacement;
import dev.dryaides.minuit.Themes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shift-click and offhand guard on the game's InventoryMenu, whose layout is fixed since 1.9. */
class QuickMoveGuardTest {

    /**
     * InventoryMenu: 0 result, 1-4 craft, 5-8 armour head to feet (inventory 39 to 36), 9-35 rows (inventory
     * 9-35), 36-44 hotbar (inventory 0-8), 45 offhand (inventory 40).
     */
    static final IntUnaryOperator INVENTORY_MENU = i -> i <= 4 ? -1 : i <= 8 ? 44 - i : i <= 35 ? i : i <= 44 ? i - 36 : 40;
    static final int SIZE = 46;

    static List<SlotLayout.Cell> menuCells(int left, int top) {
        List<SlotLayout.Cell> out = new ArrayList<>();
        for (SlotLayout.Cell c : SlotLayout.playerMenuEquipment()) {
            out.add(new SlotLayout.Cell(c.index(), left + c.x(), top + c.y()));
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                out.add(new SlotLayout.Cell(9 + r * 9 + c, left + 8 + c * 18, top + 84 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            out.add(new SlotLayout.Cell(36 + c, left + 8 + c * 18, top + 142));
        }
        return out;
    }

    static PanelComponent panel(boolean equipment) {
        return new PanelComponent(Themes.MINIMALIST, null, 100, 50) {
            {
                add(new PlayerInventoryComponent(SlotLayout.playerInventory("inv", 108, 134)));
                if (equipment) {
                    add(new EquipmentComponent("eq", 300, 50, 100, 50, EquipmentComponent.Layout.ROW, true));
                }
            }

            @Override
            public int width() {
                return 176;
            }

            @Override
            public int height() {
                return 166;
            }

            @Override
            protected void drawBackground(Draw d, int mx, int my) {
            }
        };
    }

    @Test
    void inventoryOnlyKitRoutesRowsToHotbarAndHotbarToRows() {
        SlotPlacement placement = SlotPlacement.of(panel(false), menuCells(100, 50));
        assertEquals(IntStream.rangeClosed(36, 44).boxed().toList(),
                ContainerLogic.quickTargets(20, SIZE, INVENTORY_MENU, placement::finds));
        assertEquals(IntStream.rangeClosed(9, 35).boxed().toList(),
                ContainerLogic.quickTargets(40, SIZE, INVENTORY_MENU, placement::finds));
        assertNull(ContainerLogic.quickTargets(0, SIZE, INVENTORY_MENU, placement::finds), "the result is not a player slot");
        assertTrue(ContainerLogic.blocksOffhandSwap(SIZE, INVENTORY_MENU, placement::finds));
    }

    @Test
    void equipmentComponentPlacesEveryHiddenSlotSoTheGameKeepsItsRules() {
        SlotPlacement placement = SlotPlacement.of(panel(true), menuCells(100, 50));
        for (int i = 0; i < SIZE; i++) {
            assertTrue(placement.finds(i), "slot " + i);
        }
        assertNull(ContainerLogic.quickTargets(20, SIZE, INVENTORY_MENU, placement::finds));
        assertFalse(ContainerLogic.blocksOffhandSwap(SIZE, INVENTORY_MENU, placement::finds));
        // Row layout at 300, 50: helmet well first, its item 1 px in; the menu keeps the helmet at 108, 58.
        assertEquals(301 - 108, placement.shiftX(5));
        assertEquals(51 - 58, placement.shiftY(5));
    }

    /** Stacks as the game's left click moves them: pick up, place all into empty, merge up to 64, swap otherwise. */
    @Test
    void routeMergesThenFillsEmptyThenPutsTheRestBack() {
        String[] item = new String[46];
        int[] count = new int[46];
        item[20] = "stone"; count[20] = 64;
        item[36] = "stone"; count[36] = 60;
        item[37] = "dirt"; count[37] = 10;
        item[39] = "stone"; count[39] = 64;
        List<Integer> clicks = new ArrayList<>();
        String[] carried = {null};
        int[] carriedCount = {0};
        ContainerLogic.QuickMove menu = new ContainerLogic.QuickMove() {
            public boolean carrying() { return carriedCount[0] > 0; }
            public boolean empty(int s) { return count[s] == 0; }
            public boolean merges(int s) { return count[s] > 0 && item[s].equals(carried[0]) && count[s] < 64; }
            public void click(int s) {
                clicks.add(s);
                if (carriedCount[0] == 0) {
                    carried[0] = item[s]; carriedCount[0] = count[s]; item[s] = null; count[s] = 0;
                } else if (count[s] == 0) {
                    item[s] = carried[0]; count[s] = carriedCount[0]; carriedCount[0] = 0;
                } else if (item[s].equals(carried[0])) {
                    int moved = Math.min(64 - count[s], carriedCount[0]);
                    count[s] += moved; carriedCount[0] -= moved;
                } else {
                    String i = item[s]; int c = count[s];
                    item[s] = carried[0]; count[s] = carriedCount[0]; carried[0] = i; carriedCount[0] = c;
                }
            }
        };
        ContainerLogic.route(20, IntStream.rangeClosed(36, 44).boxed().toList(), menu);
        assertEquals(List.of(20, 36, 38), clicks);
        assertEquals(64, count[36]);
        assertEquals(60, count[38]);
        assertEquals(10, count[37], "the dirt is never swapped");
        assertFalse(menu.carrying());

        clicks.clear();
        ContainerLogic.route(38, List.of(36, 39), menu);
        assertEquals(List.of(38, 38), clicks, "both targets full: the stack goes back");
        assertEquals(60, count[38]);
    }
}
