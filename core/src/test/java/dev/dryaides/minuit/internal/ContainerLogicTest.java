package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.SlotGridComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
