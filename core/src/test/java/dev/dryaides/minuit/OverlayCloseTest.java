package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.HostCore;
import dev.dryaides.minuit.internal.ScreenLogic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlayCloseTest {

    /**
     * A controller's back button calls the screen's onClose with no key press: the first call closes
     * the open dropdown, the second finds nothing open and lets the screen close.
     */
    @Test
    void backClosesTheDropdownBeforeTheScreen() {
        PanelComponent[] holder = new PanelComponent[1];
        DropdownComponent dropdown = new DropdownComponent(new Box("sort", 10, 10, 60, 14), "Sort", 10, 60,
                list -> holder[0].overlay = list);
        PanelComponent panel = new PanelComponent(Themes.MINIMALIST, null, 0, 0) {
            {
                add(dropdown);
            }

            @Override
            public int width() {
                return 200;
            }

            @Override
            public int height() {
                return 100;
            }

            @Override
            protected void drawBackground(Draw d, int mx, int my) {
            }
        };
        holder[0] = panel;
        ScreenLogic logic = new ScreenLogic(new HostCore(null));

        panel.mouseClicked(20, 15, 0);
        assertNotNull(panel.overlay());

        assertTrue(logic.closeOverlay(panel), "first back: the dropdown closes, the screen stays");
        assertNull(panel.overlay());
        assertFalse(logic.closeOverlay(panel), "second back: nothing open, the screen closes");
        assertFalse(logic.closeOverlay(null));
    }
}
