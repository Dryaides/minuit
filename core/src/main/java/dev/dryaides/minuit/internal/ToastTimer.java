package dev.dryaides.minuit.internal;

/**
 * The time of one toast in the game's stack, read from the stack's own clock (how long the toast has
 * been fully visible). A replaced text starts its time again. Not API.
 */
public final class ToastTimer {

    private long startMs;
    private boolean restart;

    /** The next {@link #visible} call starts the time again, at the stack's time then. */
    public void restart() {
        this.restart = true;
    }

    /**
     * Whether the toast still wants to be shown at stack time {@code fullyVisibleForMs}: true until
     * {@code millis} times the player's Notification Time {@code multiplier} has gone by.
     */
    public boolean visible(long fullyVisibleForMs, int millis, double multiplier) {
        if (this.restart) {
            this.startMs = fullyVisibleForMs;
            this.restart = false;
        }
        return fullyVisibleForMs - this.startMs < millis * multiplier;
    }

    /** How long the current text has been shown, for the art's animation. */
    public long age(long fullyVisibleForMs) {
        return Math.max(0, fullyVisibleForMs - this.startMs);
    }
}
