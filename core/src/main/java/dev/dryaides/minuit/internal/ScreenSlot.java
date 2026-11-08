package dev.dryaides.minuit.internal;

/**
 * The one open screen of a kind, so a fresh page finds it without asking the game which screen is up.
 * Set in {@code init}, cleared in {@code removed}, both on the render thread. The body of
 * {@code MinuitScreen.Open}. Not API.
 */
public class ScreenSlot<P, S extends ScreenSlot.Page<P>> {

    /** A screen that can tell whether it shows a page. */
    public interface Page<P> {
        boolean isSame(P page);
    }

    private S screen;

    public void set(S opened) {
        this.screen = opened;
    }

    public void clear(S closing) {
        if (this.screen == closing) {
            this.screen = null;
        }
    }

    /** The open screen, or null. */
    public S get() {
        return this.screen;
    }

    /** The open screen when it shows the same thing as {@code page}, or null. */
    public S showing(P page) {
        S current = this.screen;
        return current != null && current.isSame(page) ? current : null;
    }
}
