package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.HostState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepperComponentTest {

    private long now = 1000;

    @AfterEach
    void reset() {
        HostState.clock(System::currentTimeMillis);
    }

    /** A held button steps once on press, waits the delay, then repeats ever faster down to the floor. */
    @Test
    void holdWaitsThenRepeatsFasterAndFaster() {
        assertEquals(0, StepperComponent.repeatsDue(StepperComponent.REPEAT_DELAY_MS - 1));
        assertEquals(1, StepperComponent.repeatsDue(StepperComponent.REPEAT_DELAY_MS));
        int previousGap = Integer.MAX_VALUE;
        long at = StepperComponent.REPEAT_DELAY_MS;
        for (int n = 1; n < 40; n++) {
            long next = at;
            while (StepperComponent.repeatsDue(next) == n) {
                next++;
            }
            int gap = (int) (next - at);
            assertTrue(gap <= previousGap, "repeat " + n + " slower than the one before");
            assertTrue(gap >= StepperComponent.REPEAT_FASTEST_MS, "repeat " + n + " under the floor");
            previousGap = gap;
            at = next;
        }
        assertEquals(StepperComponent.REPEAT_FASTEST_MS, previousGap, "the repeat never reached its floor");
    }

    /** Pressed, held 1 s, released: the value moved by the press plus the repeats, commit fired once. */
    @Test
    void aHeldPlusStepsOnTheClockAndCommitsOnce() {
        HostState.clock(() -> this.now);
        int[] commits = {0};
        StepperComponent s = new StepperComponent(new Box("s", 0, 0, 60, 14), 0, 100, 10, v -> { })
                .onCommit(v -> commits[0]++);
        s.mouseClicked(55, 7, 0);
        assertEquals(11, s.value);
        this.now += 1000;
        s.mouseReleased(55, 7, 0);
        assertEquals(11 + StepperComponent.repeatsDue(1000), s.value);
        assertEquals(1, commits[0]);
        this.now += 1000;
        s.mouseReleased(55, 7, 0);
        assertEquals(1, commits[0]);
    }

    /**
     * Meccha's room cap: the seeker count may not pass the player count (4). Plus at 4 stays at 4 and
     * reports the refusal once; a held plus repeating into the cap reports nothing more; minus still works.
     */
    @Test
    void canStepRefusesPastTheCapAndReportsThePressOnly() {
        HostState.clock(() -> this.now);
        int players = 4;
        java.util.List<int[]> vetoes = new java.util.ArrayList<>();
        StepperComponent s = new StepperComponent(new Box("s", 0, 0, 60, 14), 1, 16, 3, v -> { })
                .canStep((from, to) -> to <= players).onVeto((from, to) -> vetoes.add(new int[]{from, to}));
        s.mouseClicked(55, 7, 0);
        this.now += 2000;
        s.mouseReleased(55, 7, 0);
        assertEquals(4, s.value);
        assertEquals(0, vetoes.size());
        s.mouseClicked(55, 7, 0);
        s.mouseReleased(55, 7, 0);
        assertEquals(4, s.value);
        assertEquals(1, vetoes.size());
        assertEquals(5, vetoes.get(0)[1]);
        s.keyPressed(InputKey.END, false);
        assertEquals(4, s.value);
        s.mouseClicked(5, 7, 0);
        s.mouseReleased(5, 7, 0);
        assertEquals(3, s.value);
    }

    /** Meccha's stepper moves 5 with Shift, a default one 10; the key path and the click path agree. */
    @Test
    void shiftMultiplierIsPerStepper() {
        HostState.keys(new HeldKeys() {
            public boolean ctrl() { return false; }
            public boolean shift() { return true; }
        });
        try {
            StepperComponent meccha = new StepperComponent(new Box("s", 0, 0, 60, 14), 0, 100, 0, v -> { }).shiftFactor(5);
            StepperComponent plain = new StepperComponent(new Box("s", 0, 0, 60, 14), 0, 100, 0, v -> { });
            meccha.mouseClicked(55, 7, 0);
            plain.mouseClicked(55, 7, 0);
            assertEquals(5, meccha.value);
            assertEquals(10, plain.value);
            meccha.keyPressed(InputKey.RIGHT, true);
            assertEquals(10, meccha.value);
        } finally {
            HostState.keys(HeldKeys.NONE);
        }
    }
}
