package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.BaseComponent;

/**
 * Whether the frame just drawn will look different on a later frame with no input: a host that draws on demand
 * (the site) takes it after each frame and skips the next one when nothing asked. The game draws every frame and
 * never takes it.
 */
public final class Redraw {

    private static boolean asked;

    private Redraw() {
    }

    /** Something drawn is still moving: draw again. */
    public static void ask() {
        asked = true;
    }

    /** Whether anything asked since the last call. */
    public static boolean take() {
        boolean was = asked;
        asked = false;
        return was;
    }

    /** {@link BaseComponent#clock} for a reader that asks by itself only while its value moves. */
    public static long quietClock() {
        boolean was = asked;
        long now = BaseComponent.clock().getAsLong();
        asked = was;
        return now;
    }
}
