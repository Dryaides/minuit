package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.PlayerInventoryComponent;
import dev.dryaides.minuit.SlotLayout;
import dev.dryaides.minuit.SlotGridComponent;
import org.junit.jupiter.api.Test;

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
}
