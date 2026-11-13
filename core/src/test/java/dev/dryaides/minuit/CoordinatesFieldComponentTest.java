package dev.dryaides.minuit;

import dev.dryaides.minuit.CoordinatesFieldComponent.Value;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CoordinatesFieldComponentTest {

    /** The three ways a player pastes a position: F3 style spaces, commas, and a command's tildes. */
    @Test
    void pasteSplitsIntoThreeParts() {
        assertEquals(new Value(10, 64, -20, false), CoordinatesFieldComponent.parse("10 64 -20", 0));
        assertEquals(new Value(1, 2, 3, false), CoordinatesFieldComponent.parse("1, 2, 3", 0));
        assertEquals(new Value(0, 5, -2, true), CoordinatesFieldComponent.parse("~ ~5 ~-2", 0));
    }

    /** A block position is the block the point is in: 12.7 is in block 12, -3.2 in block -4. */
    @Test
    void pasteOfDecimalsFloorsToTheBlock() {
        assertEquals(new Value(12, 64, -4, false), CoordinatesFieldComponent.parse("12.7 64 -3.2", 0));
        assertEquals(new Value(12.75, 64, -3.2, false), CoordinatesFieldComponent.parse("12.759 64 -3.2", 2));
    }

    /** Anything that is not three numbers leaves the clipboard to the normal paste. */
    @Test
    void pasteOfOtherTextIsRefused() {
        assertNull(CoordinatesFieldComponent.parse("1 2", 0));
        assertNull(CoordinatesFieldComponent.parse("1 2 3 4", 0));
        assertNull(CoordinatesFieldComponent.parse("home base spawn", 0));
        assertNull(CoordinatesFieldComponent.parse("~1 2 3", 0));
    }

    /** Overworld height runs from -64 to 320: below and above are held, inside is kept. */
    @Test
    void yIsHeldInsideTheWorld() {
        assertEquals(-64, CoordinatesFieldComponent.clampY(-70, -64, 320));
        assertEquals(320, CoordinatesFieldComponent.clampY(400, -64, 320));
        assertEquals(64, CoordinatesFieldComponent.clampY(64, -64, 320));
    }

    /** Whole widget: paste outside the range is held, Shift+Up nudges 10 and stops at the top, the listener sees each value. */
    @Test
    void pasteNudgeAndRangeThroughTheKeyboard() {
        List<Value> seen = new ArrayList<>();
        CoordinatesFieldComponent c = new CoordinatesFieldComponent(new Box("p", 0, 0, 234, 16), seen::add)
                .yRange(-64, 320);
        List<BaseComponent> stops = c.stops();
        assertEquals(3, stops.size());
        stops.get(1).enter(true);
        stops.get(1).focused = true;
        c.focused = true;
        TextFieldComponent.clipboard.set("10, 500, -20");
        c.keyPressed(InputKey.PASTE, false);
        assertEquals(new Value(10, 320, -20, false), seen.get(seen.size() - 1));
        c.keyPressed(InputKey.DOWN, true);
        assertEquals(310, seen.get(seen.size() - 1).y());
        c.keyPressed(InputKey.UP, true);
        c.keyPressed(InputKey.UP, true);
        assertEquals(320, seen.get(seen.size() - 1).y());
    }
}
