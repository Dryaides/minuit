package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.HostState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ZoneGaugeComponentTest {

    private static final LongSupplier SYSTEM = BaseComponent.clock();
    private long now;

    @AfterEach
    void restoreClock() {
        HostState.clock(SYSTEM);
    }

    /**
     * The forging bar: good from 0.35 to 0.65 with perfect from 0.45 to 0.55, passes of 2 s. Ping-pong: at
     * 1.0 s the cursor is mid-strip (perfect), at 0.7 s at 0.35 (good), on the way back at 3.3 s at 0.35 again
     * (good), at 3.4 s at 0.30 (miss). One way: 2.5 s is half a second into the second pass, 0.25 (miss).
     */
    @Test
    void hitNamesTheZoneUnderTheCursor() {
        HostState.clock(() -> this.now);
        ZoneGaugeComponent bar = new ZoneGaugeComponent(new Box("bar", 0, 0, 182, 26))
                .zones(ZoneGaugeComponent.centred(0.30F, 0.10F)).speed(2000, ZoneGaugeComponent.Sweep.PING_PONG).start();
        assertEquals(ZoneGaugeComponent.Role.PERFECT, at(bar, 1000).role());
        assertEquals(ZoneGaugeComponent.Role.GOOD, at(bar, 700).role());
        assertEquals(ZoneGaugeComponent.Role.GOOD, at(bar, 3300).role());
        assertEquals(ZoneGaugeComponent.Role.MISS, at(bar, 3400).role());
        bar.speed(2000, ZoneGaugeComponent.Sweep.ONE_WAY);
        assertEquals(ZoneGaugeComponent.Role.MISS, at(bar, 2500).role());
        assertEquals(ZoneGaugeComponent.Role.PERFECT, at(bar, 3000).role());
    }

    /** A gap between zones is no zone; a stopped cursor keeps the zone it stopped in. */
    @Test
    void gapIsNullAndStopHolds() {
        HostState.clock(() -> this.now);
        ZoneGaugeComponent bar = new ZoneGaugeComponent(new Box("bar", 0, 0, 182, 26))
                .zones(java.util.List.of(new ZoneGaugeComponent.Zone(0.6F, 0.8F, ZoneGaugeComponent.Role.GOOD)))
                .speed(1000, ZoneGaugeComponent.Sweep.ONE_WAY).start();
        assertNull(at(bar, 300));
        this.now = 700;
        bar.stop();
        this.now = 950;
        assertEquals(ZoneGaugeComponent.Role.GOOD, bar.hit().role());
    }

    private ZoneGaugeComponent.Zone at(ZoneGaugeComponent bar, long ms) {
        this.now = ms;
        return bar.hit();
    }
}
