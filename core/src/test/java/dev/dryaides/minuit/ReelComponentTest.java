package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.HostState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReelComponentTest {

    private long now;
    private final List<Float> ticks = new ArrayList<>();
    private int lands;

    private ReelComponent<String> reel(List<String> landedOn) {
        HostState.clock(() -> this.now);
        HostState.sound(new UiSound() {
            @Override
            public void tick(float frac) {
                ticks.add(frac);
            }

            @Override
            public void land() {
                lands++;
            }
        });
        return new ReelComponent<>(new Box("r", 0, 0, 300, 80), List.of("A", "B", "C", "D", "E"), s -> s)
                .onLand(landedOn::add);
    }

    @AfterEach
    void reset() {
        HostState.clock(System::currentTimeMillis);
        HostState.sound(UiSound.NONE);
        HostState.reducedMotion(false);
    }

    @Test
    void oneTickPerCardThenOneLandingOnTheTarget() {
        List<String> landed = new ArrayList<>();
        ReelComponent<String> r = reel(landed).spin(2, 42L);
        int lead = dev.dryaides.minuit.internal.ReelMath.strip(5, 2, new java.util.Random(42L)).landing();
        for (long t = 0; t <= 3300; t++) {
            this.now = t;
            r.update();
        }
        // Every card from the first to the target passes under the marker once, at 1 ms frames.
        assertEquals(lead + 1, ticks.size());
        for (int i = 1; i < ticks.size(); i++) {
            assertTrue(ticks.get(i) > ticks.get(i - 1), "pitch rises toward the stop");
        }
        assertEquals(1, lands);
        assertEquals(List.of("C"), landed);
        assertEquals("C", r.result());
    }

    @Test
    void reducedMotionLandsAtOnceWithoutTicks() {
        List<String> landed = new ArrayList<>();
        HostState.reducedMotion(true);
        ReelComponent<String> r = reel(landed).spin(4, 7L);
        r.update();
        assertEquals(0, ticks.size());
        assertEquals(1, lands);
        assertEquals(List.of("E"), landed);
    }

    @Test
    void aHiddenReelStillLandsFromTheTick() {
        List<String> landed = new ArrayList<>();
        ReelComponent<String> r = reel(landed).spin(1, 3L);
        r.visible = false;
        for (long t = 0; t <= 10_000; t += 50) {
            this.now = t;
            r.tick();
        }
        assertEquals(1, lands);
        assertEquals(List.of("B"), landed);
    }
}
