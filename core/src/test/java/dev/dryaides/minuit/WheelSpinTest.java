package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.HostState;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WheelSpinTest {
    private static final String[] PRIZES = {"Diamond", "Emerald", "Gold", "Iron", "Coal"};
    private final LongSupplier clock = BaseComponent.clock();

    @AfterEach
    void restoreClock() {
        HostState.clock(clock);
    }

    private static WheelComponent wheel() {
        WheelComponent w = new WheelComponent(new Box("wheel", 0, 0, 120, 120));
        for (String prize : PRIZES) {
            w.add(prize);
        }
        return w;
    }

    /** The server's spin and a client given the same landing, seed and start stop at one angle, on the landing. */
    @Test
    void twoSpinsWithOneSeedLandOnOneAngleInsideTheLanding() {
        long start = 1_000_000L;
        long[] now = {start};
        HostState.clock(() -> now[0]);
        int landing = 2;
        long seed = 0x5EEDL;

        WheelComponent server = wheel().spin(landing, seed);
        WheelComponent client = wheel().restore(new WheelComponent.Spin(landing, seed, start, 0D, WheelComponent.DEFAULT_DURATION, false));
        now[0] = start + WheelComponent.DEFAULT_DURATION + 1L;
        server.tick();
        client.tick();

        assertTrue(server.landed() && client.landed());
        assertEquals(server.rotation(), client.rotation(), 1e-12);
        // Equal weights: segment i spans [i/5, (i+1)/5) of a turn clockwise from the pointer at the top.
        double underPointer = -server.rotation() - Math.floor(-server.rotation());
        assertTrue(underPointer > landing / 5D && underPointer < (landing + 1) / 5D, "stopped at " + underPointer);
    }
}
