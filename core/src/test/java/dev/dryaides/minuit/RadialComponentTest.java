package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.HostState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadialComponentTest {

    private long now = 1000;

    @AfterEach
    void reset() {
        HostState.clock(System::currentTimeMillis);
    }

    /** The site's quick menu, clicked as the owner did: a pick, then spam on the page card and the wheel. */
    @Test
    void clickWheelKeepsTurningPagesAfterAPick() {
        HostState.clock(() -> this.now);
        List<String> picked = new ArrayList<>();
        RadialComponent<String> wheel = RadialComponent.<String>builder(new Box("screen", 0, 0, 330, 230), 76)
                .slot(40, 20)
                .page("Actions", List.of("Delete", "Lock", "Back", "Warn", "Stop", "Reload", "Play", "Folder"))
                .page("More", List.of("Download", "Web", "Colours"))
                .onPick(picked::add)
                .build();
        this.now += 500;
        // Down-right of the centre (165, 115) is Warn, the fourth sector clockwise from the top.
        wheel.mouseClicked(205, 155, 0);
        assertEquals(List.of("Warn"), picked);
        // The right "More" card sits just past the ring's outer edge at the centre's height.
        wheel.mouseClicked(290, 115, 0);
        assertEquals(1, wheel.page());
        wheel.mouseScrolled(165, 115, 1);
        assertEquals(0, wheel.page());
        wheel.mouseClicked(205, 155, 0);
        assertEquals(List.of("Warn", "Warn"), picked);
    }

    /** A held emote wheel with three pages: keys page round both ways, and a released wheel no longer pages. */
    @Test
    void heldWheelPagesWithKeysUntilReleased() {
        HostState.clock(() -> this.now);
        List<String> cancelled = new ArrayList<>();
        RadialComponent<String> wheel = RadialComponent.<String>builder(new Box("screen", 0, 0, 330, 230), 76)
                .page("Greet", List.of("Wave", "Bow"))
                .page("Play", List.of("Dance", "Jump"))
                .page("Rest", List.of("Sit", "Sleep"))
                .holdToSelect(() -> true)
                .onCancel(() -> cancelled.add("cancel"))
                .build();
        wheel.keyPressed(InputKey.RIGHT, false);
        wheel.turnPage(1);
        assertEquals(2, wheel.page());
        wheel.turnPage(1);
        assertEquals(0, wheel.page());
        wheel.keyPressed(InputKey.LEFT, false);
        assertEquals(2, wheel.page());
        wheel.release();
        assertEquals(List.of("cancel"), cancelled);
        assertEquals(false, wheel.turnPage(1));
        assertEquals(2, wheel.page());
    }
}
