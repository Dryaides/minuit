package dev.dryaides.minuit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlideTest {

    private long now = 1000;

    @AfterEach
    void restore() {
        BaseComponent.clock = System::currentTimeMillis;
        BaseComponent.reducedMotion = false;
    }

    /** A drawer that opens starts a full distance away and lands exactly at its place after the duration. */
    @Test
    void startsAwayAndLandsAtItsPlace() {
        BaseComponent.clock = () -> this.now;
        Glide rows = new Glide(Slide.DOWN, 170);
        rows.replay();
        assertEquals(58F, rows.offsetY(58), 1e-4);
        this.now += 170;
        assertEquals(0F, rows.offsetY(58), 1e-4);
        assertFalse(rows.moving());
    }

    /** The owner's case: the drawer folds while it unfolds; the rows turn back from where they are, no jump, and get home in the time spent. */
    @Test
    void turningBackMidWayDoesNotJump() {
        BaseComponent.clock = () -> this.now;
        Glide rows = new Glide(Slide.DOWN, 170);
        rows.snap(false);
        rows.show();
        this.now += 60;
        float before = rows.offsetY(58);
        rows.hide();
        assertEquals(before, rows.offsetY(58), 1e-4);
        assertTrue(rows.moving());
        this.now += 60;
        assertEquals(58F, rows.offsetY(58), 1e-4);
        assertTrue(rows.away());
    }

    /** A player with reduced motion sees the end state on the first frame. */
    @Test
    void reducedMotionGivesTheTargetAtOnce() {
        BaseComponent.clock = () -> this.now;
        BaseComponent.reducedMotion = true;
        Glide rows = new Glide(Slide.DOWN, 170);
        rows.replay();
        assertEquals(0F, rows.offsetY(58), 1e-4);
        rows.hide();
        assertEquals(58F, rows.offsetY(58), 1e-4);
        assertFalse(rows.moving());
    }
}
