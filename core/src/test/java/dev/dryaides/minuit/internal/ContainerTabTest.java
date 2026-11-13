package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Box;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerTabTest {

    /** The survival inventory at GUI scale 3 on 1920x1080: 176x166 at (232, 97); the recipe book moves it 77 right. */
    private static final int LEFT = 232;
    private static final int TOP = 97;
    private static final int IMAGE_W = 176;
    private static final int BOOK_SHIFT = 77;

    /** The tab stands on the container's top edge, inside its width, and goes along with the recipe book. */
    @Test
    void restsOnTheTopEdgeAndFollowsTheRecipeBook() {
        Box tab = ContainerTab.at(ClientConfigFile.TabPlace.DEFAULT, LEFT, TOP);
        assertEquals(TOP, tab.y() + tab.h());
        assertTrue(tab.x() >= LEFT && tab.x() + tab.w() <= LEFT + IMAGE_W / 2,
                "left half of the edge, clear of Inventory Profiles Next's top right buttons");
        Box shifted = ContainerTab.at(ClientConfigFile.TabPlace.DEFAULT, LEFT + BOOK_SHIFT, TOP);
        assertEquals(tab.x() + BOOK_SHIFT, shifted.x());
        assertEquals(tab.y(), shifted.y());
    }

    /** A screen as tall as the window leaves no room above: no tab rather than one cut off. */
    @Test
    void noTabWithoutRoomAbove() {
        assertNull(ContainerTab.at(ClientConfigFile.TabPlace.DEFAULT, LEFT, 4));
        assertNull(ContainerTab.at(new ClientConfigFile.TabPlace(false, 0, 0), LEFT, TOP));
    }

    /** An older file has no containerTab: the tab shows. The player's offset and hide come from the file. */
    @Test
    void fileKeysPlaceTheTab() {
        assertEquals(ClientConfigFile.TabPlace.DEFAULT, ClientConfigFile.parse("{\"mods\": {}}").containerTab());
        ClientConfigFile.TabPlace moved = ClientConfigFile.parse(
                "{\"containerTab\": {\"offsetX\": 40, \"offsetY\": -2}}").containerTab();
        assertTrue(moved.shown());
        Box tab = ContainerTab.at(moved, LEFT, TOP);
        assertEquals(TOP - 2, tab.y() + tab.h());
        assertFalse(ClientConfigFile.parse("{\"containerTab\": {\"shown\": false}}").containerTab().shown());
    }
}
