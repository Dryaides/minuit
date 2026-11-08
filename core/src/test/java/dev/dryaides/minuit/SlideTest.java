package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SlideTest {

    /** A panel 200 wide gliding in from the right starts a quarter of its width (50) to the right of its place. */
    @Test
    void clickDuringGlideHitsTheWidgetDrawnUnderIt() {
        double widgetX = 40;
        assertArrayEquals(new double[]{widgetX, 30}, Slide.RIGHT.toPanel(widgetX + 50, 30, 0, 200, 100));
        assertArrayEquals(new double[]{widgetX, 30}, Slide.RIGHT.toPanel(widgetX, 30, TabsComponent.SLIDE_MS, 200, 100));
    }
}
