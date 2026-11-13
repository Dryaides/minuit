package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.ExpandableHotbarComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.ItemDropTarget;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.PlayerInventoryComponent;
import dev.dryaides.minuit.SlotLayout;
import dev.dryaides.minuit.SlotGridComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rule from the class doc of {@code MinuitContainerScreen}: menu slots belong to the game, widgets over them to the panel. */
class ContainerLogicTest {

    @Test
    void menuSlotsGoToTheGameAndAButtonOverThemToThePanel() {
        int[] pressed = {0};
        SlotGridComponent grid = new SlotGridComponent(new Box("grid", 10, 10, 162, 54), 3, 9);
        ButtonComponent button = new ButtonComponent(new Box("b", 100, 20, 20, 20), "", () -> pressed[0]++);
        PanelComponent panel = ScreenLogicTest.panel(grid, button);
        ContainerLogic logic = new ContainerLogic(new HostCore(null), new ContainerLogic.MenuView() {
            @Override
            public ItemView hoveredSlotItem() {
                return null;
            }

            @Override
            public Box container() {
                return new Box("container", 0, 0, 176, 166);
            }
        });
        assertFalse(logic.mouseClicked(panel, 20, 20, 0));
        logic.mouseClicked(panel, 105, 25, 0);
        logic.mouseReleased(panel, 105, 25, 0);
        assertEquals(1, pressed[0]);
    }

    private static ContainerLogic logic() {
        return new ContainerLogic(new HostCore(null), new ContainerLogic.MenuView() {
            @Override
            public ItemView hoveredSlotItem() {
                return null;
            }

            @Override
            public Box container() {
                return new Box("container", 0, 0, 176, 166);
            }
        });
    }

    /** A release goes to the game only when the game had the press, or it acts on a press it never saw. */
    @Test
    void releaseFollowsItsPress() {
        SlotGridComponent grid = new SlotGridComponent(new Box("grid", 10, 10, 162, 54), 3, 9);
        ButtonComponent button = new ButtonComponent(new Box("b", 100, 20, 20, 20), "", () -> { });
        PanelComponent panel = ScreenLogicTest.panel(grid, button);
        ContainerLogic logic = logic();
        assertTrue(logic.mouseClicked(panel, 105, 25, 0));
        assertTrue(logic.mouseReleased(panel, 105, 25, 0));
        assertFalse(logic.mouseClicked(panel, 20, 20, 0));
        assertFalse(logic.mouseReleased(panel, 20, 20, 0));
    }

    /** Vanilla ContainerScreen, three rows: imageHeight 168, "Inventory" at (8, 168 - 94). */
    @Test
    void chestKitLabelSitsWhereVanillaPutsIt() {
        PanelComponent panel = ScreenLogicTest.panel(new PlayerInventoryComponent(SlotLayout.playerInventory("inv", 8, 85)));
        Box label = logic().inventoryLabel(panel);
        assertEquals(8, label.x());
        assertEquals(74, label.y());
    }

    /**
     * Bargain's drag (HotbarRules): a stack pressed on a slot and released on a target that refuses it goes
     * back to that slot, the game's click there; one the target takes stays with the target, and no slot is clicked.
     */
    @Test
    void refusedDropGoesBackToItsSlot() {
        ItemView apple = ItemView.of("minecraft:apple", 1);
        ItemView[] cursor = {null};
        SlotLayout.Cell[] under = {null};
        List<Integer> clicks = new ArrayList<>();
        boolean[] takes = {false};
        ExpandableHotbarComponent bar = ExpandableHotbarComponent.bottom(400, 300, 1, false);
        bar.targets(() -> List.of(new ItemDropTarget.Drop(new Box("spot", 150, 20, 40, 40), item -> takes[0])));
        PanelComponent panel = ScreenLogicTest.panel(bar);
        ContainerLogic logic = new ContainerLogic(new HostCore(null), new ContainerLogic.MenuView() {
            @Override
            public ItemView hoveredSlotItem() {
                return under[0] == null ? null : apple;
            }

            @Override
            public Box container() {
                return new Box("container", 0, 0, 176, 166);
            }

            @Override
            public SlotLayout.Cell hoveredCell() {
                return under[0];
            }

            @Override
            public ItemView carried() {
                return cursor[0];
            }

            @Override
            public void click(int slot) {
                clicks.add(slot);
            }

            @Override
            public boolean takes(int slot) {
                return slot == 36;
            }
        });
        logic.init(panel, List.of(), i -> true, "test", null);
        for (boolean accepted : new boolean[] {false, true}) {
            takes[0] = accepted;
            clicks.clear();
            under[0] = new SlotLayout.Cell(36, 120, 280);
            assertFalse(logic.mouseClicked(panel, 125, 285, 0), "the pick is the game's own click");
            cursor[0] = apple;
            under[0] = null;
            assertTrue(logic.mouseReleased(panel, 170, 40, 0));
            assertEquals(accepted ? List.of() : List.of(36), clicks);
            cursor[0] = null;
        }
    }

    /** A menu the tests drive: the slot under the pointer, the cursor stack, which slots take it, the clicks sent. */
    private static final class Menu implements ContainerLogic.MenuView {
        SlotLayout.Cell under;
        ItemView cursor;
        final Set<Integer> taking = new HashSet<>();
        final Set<Integer> player = new HashSet<>();
        final Set<Integer> refusing = new HashSet<>();
        final List<Integer> clicks = new ArrayList<>();

        @Override
        public ItemView hoveredSlotItem() {
            return under == null ? null : ItemView.of("minecraft:bread", 1);
        }

        @Override
        public Box container() {
            return new Box("container", 0, 0, 176, 166);
        }

        @Override
        public SlotLayout.Cell hoveredCell() {
            return under;
        }

        @Override
        public ItemView carried() {
            return cursor;
        }

        @Override
        public void click(int slot) {
            clicks.add(slot);
        }

        @Override
        public boolean takes(int slot) {
            return taking.contains(slot);
        }

        @Override
        public boolean playerSlot(int slot) {
            return player.contains(slot);
        }

        @Override
        public boolean mayPlace(int slot) {
            return !refusing.contains(slot);
        }
    }

    /**
     * Vanilla: press on emeralds in a hotbar slot, drag and release over the villager's output slot (MerchantResultSlot,
     * mayPlace false): nothing happens, no trade. Here the output (slot 2) is never clicked; the emeralds go back to
     * their slot (30).
     */
    @Test
    void releaseOverATradeOutputNeverClicksIt() {
        Menu menu = new Menu();
        menu.refusing.add(2);
        menu.taking.add(30);
        PanelComponent panel = ScreenLogicTest.panel(ExpandableHotbarComponent.bottom(400, 300, 1, false));
        ContainerLogic logic = new ContainerLogic(new HostCore(null), menu);
        logic.init(panel, List.of(new SlotLayout.Cell(2, 220, 37), new SlotLayout.Cell(30, 8, 142)), i -> true, "test", null);
        menu.under = new SlotLayout.Cell(30, 8, 142);
        logic.mouseClicked(panel, 10, 144, 0);
        menu.cursor = ItemView.of("minecraft:emerald", 5);
        menu.under = new SlotLayout.Cell(2, 220, 37);
        logic.mouseReleased(panel, 222, 39, 0);
        assertEquals(List.of(30), menu.clicks);
    }

    /**
     * Vanilla ResultSlot.mayPlace is false: a left click there with bread on the cursor takes a second craft.
     * A drag picked from the crafting table's result (slot 0) and released over nothing goes to the first
     * player slot that takes it (10), never back to slot 0; with none it stays on the cursor.
     */
    @Test
    void giveBackNeverClicksAResultSlot() {
        Menu menu = new Menu();
        PanelComponent panel = ScreenLogicTest.panel(ExpandableHotbarComponent.bottom(400, 300, 1, false));
        ContainerLogic logic = new ContainerLogic(new HostCore(null), menu);
        logic.init(panel, List.of(new SlotLayout.Cell(0, 124, 35), new SlotLayout.Cell(10, 8, 84)), i -> true, "test", null);
        menu.player.add(10);
        for (boolean roomLeft : new boolean[] {true, false}) {
            menu.taking.clear();
            if (roomLeft) {
                menu.taking.add(10);
            }
            menu.clicks.clear();
            menu.under = new SlotLayout.Cell(0, 124, 35);
            logic.mouseClicked(panel, 130, 40, 0);
            menu.cursor = ItemView.of("minecraft:bread", 1);
            menu.under = null;
            assertEquals(roomLeft, logic.mouseReleased(panel, 300, 100, 0));
            assertEquals(roomLeft ? List.of(10) : List.of(), menu.clicks);
            menu.cursor = null;
        }
    }

    /**
     * Vanilla: a left click outside the container with a stack on the cursor throws it. Stack A placed in
     * slot 9 by a left click, half of stack B picked from slot 20 by a right click, then a left click outside:
     * the game's click throws B, and slot 9 is not clicked.
     */
    @Test
    void rightClickPickThenOutsideThrows() {
        Menu menu = new Menu();
        menu.taking.add(9);
        menu.taking.add(20);
        PanelComponent panel = ScreenLogicTest.panel(ExpandableHotbarComponent.bottom(400, 300, 1, false));
        ContainerLogic logic = new ContainerLogic(new HostCore(null), menu);
        logic.init(panel, List.of(new SlotLayout.Cell(9, 8, 84), new SlotLayout.Cell(20, 26, 102)), i -> true, "test", null);
        menu.cursor = ItemView.of("minecraft:apple", 4);
        menu.under = new SlotLayout.Cell(9, 8, 84);
        assertFalse(logic.mouseClicked(panel, 10, 86, 0), "the place is the game's own click");
        logic.mouseReleased(panel, 10, 86, 0);
        menu.cursor = null;
        menu.under = new SlotLayout.Cell(20, 26, 102);
        assertFalse(logic.mouseClicked(panel, 28, 104, 1), "the right pick is the game's own click");
        logic.mouseReleased(panel, 28, 104, 1);
        menu.cursor = ItemView.of("minecraft:bread", 8);
        menu.under = null;
        assertFalse(logic.mouseClicked(panel, 300, 100, 0), "the game's click outside throws the stack");
        assertEquals(List.of(), menu.clicks);
    }
    /**
     * Vanilla ChestMenu, three rows: the player rows are slots 27 to 53 with item corners at (8 + 18 col,
     * 84 + 18 row), the hotbar 54 to 62 at (8 + 18 col, 142). AbstractContainerScreen sends a left click as
     * PICKUP button 0 and a shift left click as QUICK_MOVE on the slot under the mouse; Enter and Shift+Enter
     * on the keyboard cell send the same to the slot drawn there. Down off the bottom player row lands on
     * the hotbar cell below it (column 1, slot 55), as a pad moves. Without {@code keys} the grid is no stop.
     */
    @Test
    void enterClicksTheMenuSlotUnderTheKeyboardCell() {
        List<SlotLayout.Cell> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            slots.add(new SlotLayout.Cell(27 + i, 8 + i % 9 * 18, 84 + i / 9 * 18));
        }
        for (int i = 0; i < 9; i++) {
            slots.add(new SlotLayout.Cell(54 + i, 8 + i * 18, 142));
        }
        for (boolean keys : new boolean[] {false, true}) {
            List<String> sent = new ArrayList<>();
            PlayerInventoryComponent inv = new PlayerInventoryComponent(SlotLayout.playerInventory("inv", 8, 84)).keys(keys);
            PanelComponent panel = ScreenLogicTest.panel(inv);
            ContainerLogic logic = new ContainerLogic(new HostCore(null), new ContainerLogic.MenuView() {
                @Override
                public ItemView hoveredSlotItem() {
                    return null;
                }

                @Override
                public Box container() {
                    return new Box("container", 0, 0, 176, 168);
                }

                @Override
                public void click(int slot) {
                    sent.add("pickup " + slot);
                }

                @Override
                public boolean quickMove(int slot) {
                    sent.add("quick " + slot);
                    return true;
                }
            });
            logic.init(panel, slots, i -> true, "test", null);
            panel.keyPressed(InputKey.TAB, false);
            panel.keyPressed(InputKey.RIGHT, false);
            panel.keyPressed(InputKey.ENTER, false);
            for (int i = 0; i < 3; i++) {
                panel.keyPressed(InputKey.DOWN, false);
            }
            panel.keyPressed(InputKey.ENTER, true);
            assertEquals(keys ? List.of("pickup 28", "quick 55") : List.of(), sent);
        }
    }

    /** A menu without QUICK_MOVE: Shift+Enter on a slot is used and does nothing; it never becomes a PICKUP. */
    @Test
    void shiftEnterWithoutQuickMoveDoesNotPickUp() {
        List<String> sent = new ArrayList<>();
        PlayerInventoryComponent inv = new PlayerInventoryComponent(SlotLayout.playerInventory("inv", 8, 84)).keys(true);
        PanelComponent panel = ScreenLogicTest.panel(inv);
        ContainerLogic logic = new ContainerLogic(new HostCore(null), new ContainerLogic.MenuView() {
            @Override
            public ItemView hoveredSlotItem() {
                return null;
            }

            @Override
            public Box container() {
                return new Box("container", 0, 0, 176, 168);
            }

            @Override
            public void click(int slot) {
                sent.add("pickup " + slot);
            }
        });
        logic.init(panel, List.of(new SlotLayout.Cell(27, 8, 84)), i -> true, "test", null);
        panel.keyPressed(InputKey.TAB, false);
        assertTrue(panel.keyPressed(InputKey.ENTER, true));
        assertEquals(List.of(), sent);
    }
}
