package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.HostState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oracles: vanilla's InventoryMenu slot corners ({@link SlotPlacementTest#playerMenu}) and vanilla Gui's HUD
 * hotbar (a 182 px bar centred at the bottom of the screen, item i at w / 2 - 90 + 20i + 2, h - 19).
 * Invariant: what the pointer finds is what the bar draws, never a row cell still under the bar.
 */
class ExpandableHotbarTest {
    private static final int W = 427;
    private static final int H = 240;
    private static final Box MENU_INVENTORY = SlotLayout.playerInventory("inv", 8, 84);
    private static final LongSupplier CLOCK = BaseComponent.clock();
    private long now = 1_000;

    @AfterEach
    void restoreClock() {
        HostState.clock(CLOCK);
    }

    private ExpandableHotbarComponent hotbar() {
        HostState.clock(() -> this.now);
        return ExpandableHotbarComponent.bottom(W, H, 3, true).menuSlots(MENU_INVENTORY);
    }

    @Test
    void foldedBarShowsTheMenuHotbarWhereTheGameDrawsItsHud() {
        ExpandableHotbarComponent bar = hotbar();
        SlotPlacement placement = SlotPlacement.of(SlotPlacementTest.panel(W, bar), SlotPlacementTest.playerMenu());
        for (int c = 0; c < 9; c++) {
            int slot = 36 + c;
            assertTrue(placement.placed(slot), "hotbar slot " + slot);
            assertEquals(W / 2 - 90 + 20 * c + 2, 8 + 18 * c + placement.shiftX(slot), "x of hotbar slot " + slot);
            assertEquals(H - 19, 142 + placement.shiftY(slot), "y of hotbar slot " + slot);
        }
        for (int slot = 0; slot < 36; slot++) {
            assertFalse(placement.placed(slot), "slot " + slot + " while folded");
        }
    }

    @Test
    void unfoldingRowsAreFoundOnlyWhereDrawnAndClearOfTheBar() {
        ExpandableHotbarComponent bar = hotbar();
        PanelComponent panel = SlotPlacementTest.panel(W, bar);
        bar.hold(true);
        Box hud = bar.bar.box();
        int seenPartial = 0;
        for (int ms = 0; ms <= 200; ms += 10) {
            this.now = 1_000 + ms;
            SlotPlacement placement = SlotPlacement.of(panel, SlotPlacementTest.playerMenu());
            int placedRows = 0;
            for (int slot = 9; slot < 36; slot++) {
                if (!placement.placed(slot)) {
                    continue;
                }
                placedRows++;
                int r = (slot - 9) / 9;
                int c = (slot - 9) % 9;
                int x = 8 + 18 * c + placement.shiftX(slot);
                int y = 84 + 18 * r + placement.shiftY(slot);
                assertTrue(y + 17 <= hud.y(), "row slot " + slot + " at " + ms + " ms overlaps the bar");
                assertTrue(bar.coveredArea().contains(new Box("item", x, y, 16, 16)), "row slot " + slot + " off the bar's area");
                if (c > 0) {
                    assertEquals(x - 18, 8 + 18 * (c - 1) + placement.shiftX(slot - 1), "row order at slot " + slot);
                }
            }
            if (placedRows > 0 && placedRows < 27) {
                seenPartial++;
            }
            if (ms >= ExpandableHotbarComponent.UNFOLD_MS) {
                assertEquals(27, placedRows, "every row slot at rest, " + ms + " ms");
            }
        }
        assertTrue(seenPartial > 0, "rows come out row by row");
    }
}
