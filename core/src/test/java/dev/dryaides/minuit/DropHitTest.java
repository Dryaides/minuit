package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DropHitTest {

    private static final ItemView SWORD = ItemView.of("minecraft:iron_sword");
    private static final ItemView APPLE = ItemView.of("minecraft:apple");

    @Test
    void aRectTargetKeepsItsRectAndTakesAnyItem() {
        ItemDropTarget.Drop drop = new ItemDropTarget.Drop(new Box("r", 10, 10, 20, 20), item -> true);
        assertTrue(drop.canDrop(10, 10, APPLE));
        assertTrue(drop.canDrop(29, 29, APPLE));
        assertFalse(drop.canDrop(30, 20, APPLE));
        assertFalse(drop.canDrop(9, 20, APPLE));
    }

    @Test
    void aHitTargetTakesOnlyInsideTheShapeAndOnlyAFittingItem() {
        // a disc of radius 10 around (50,50); its bounding box also holds the corners, which are outside
        ItemDropTarget.Hit disc = (x, y) -> (x - 50) * (x - 50) + (y - 50) * (y - 50) <= 100;
        ItemDropTarget.Drop drop = new ItemDropTarget.Drop(new Box("d", 40, 40, 20, 20), disc,
                item -> item.id().endsWith("sword"), item -> true);
        assertTrue(drop.canDrop(50, 50, SWORD));
        assertFalse(drop.canDrop(41, 41, SWORD));
        assertFalse(drop.canDrop(50, 50, APPLE));
    }

    @Test
    void aPointIsCoveredUnderAnOpenPanelAndNotOutside() {
        PanelComponent panel = new PanelComponent(null, null, 100, 50) {
            @Override
            public int width() {
                return 80;
            }

            @Override
            public int height() {
                return 60;
            }

            @Override
            protected void drawBackground(Draw d, int mx, int my) {
            }
        };
        assertTrue(ScreenAreas.covers(panel, 100, 50));
        assertTrue(ScreenAreas.covers(panel, 179, 109));
        assertFalse(ScreenAreas.covers(panel, 180, 80));
        assertFalse(ScreenAreas.covers(panel, 99, 80));
    }
}
