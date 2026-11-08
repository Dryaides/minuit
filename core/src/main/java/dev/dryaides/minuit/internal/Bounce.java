package dev.dryaides.minuit.internal;

/**
 * The moves of {@link dev.dryaides.minuit.MotionStyle#BOUNCY} as whole-pixel keyframes, never a half pixel:
 * a toggle knob runs to its end, swings 2 px past it, 1 px back short of it, then rests on it; a button
 * plate lets go of its 1 px squash by popping 1 px above rest, then rests (or, with {@link #popStyle}, dips
 * 1 px below rest before resting).
 */
public final class Bounce {

    /** The knob's run to its end, ease-out cubic rounded to the pixel. */
    public static final long KNOB_TRAVEL_MS = 120;
    /** How long each swing step (+2 px past the end, then 1 px short of it) is held. */
    public static final long KNOB_STEP_MS = 50;
    public static final long KNOB_MS = KNOB_TRAVEL_MS + 2 * KNOB_STEP_MS;
    /** How long the plate stays 1 px above rest after the release. */
    public static final long POP_MS = 60;
    /** With the pop style on, the pop also swings 1 px below rest for {@link #POP_MS}: +1, -1, 0 instead of +1, 0. */
    public static volatile boolean popStyle;

    private Bounce() {
    }

    /** Pixels the knob has moved from its start {@code ms} after a flip over a run of {@code run} px. */
    public static int knob(long ms, int run) {
        if (ms <= 0) {
            return 0;
        }
        if (ms < KNOB_TRAVEL_MS) {
            double u = 1.0 - ms / (double) KNOB_TRAVEL_MS;
            return (int) Math.round(run * (1.0 - u * u * u));
        }
        if (ms < KNOB_TRAVEL_MS + KNOB_STEP_MS) {
            return run + 2;
        }
        if (ms < KNOB_MS) {
            return run - 1;
        }
        return run;
    }

    /** The plate's offset in px, down positive, {@code ms} after the release: -1 for the pop, then 0. */
    public static int pop(long ms) {
        if (ms < 0) {
            return 0;
        }
        if (ms < POP_MS) {
            return -1;
        }
        return popStyle && ms < 2 * POP_MS ? 1 : 0;
    }
}
