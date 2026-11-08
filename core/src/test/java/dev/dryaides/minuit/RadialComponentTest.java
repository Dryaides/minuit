package dev.dryaides.minuit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadialComponentTest {

    private long now = 1000;

    @AfterEach
    void reset() {
        BaseComponent.clock = System::currentTimeMillis;
    }

    /** The site's quick menu, clicked as the owner did: a pick, then spam on the page card and the wheel. */
    @Test
    void clickWheelKeepsTurningPagesAfterAPick() {
        BaseComponent.clock = () -> this.now;
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
}
