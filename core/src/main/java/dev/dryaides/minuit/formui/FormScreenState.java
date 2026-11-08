package dev.dryaides.minuit.formui;

/**
 * Which form screen is up, for the game's form screen: the server may close a form while the theme
 * page is over it, and a screen that goes without the player closing it marks the form replaced.
 * Not API.
 */
public final class FormScreenState {

    /** The form screen up, or away on the theme page, or null. */
    private static FormScreenState up;

    private final FormPanel form;
    private boolean byPlayer;
    /** The server closed the form while the theme page was up. */
    private boolean over;

    public FormScreenState(FormPanel form) {
        this.form = form;
    }

    public FormPanel form() {
        return this.form;
    }

    /**
     * The server closed {@code form}: true when its screen is the one up and must go now; while
     * {@code away} on the theme page it goes when it comes back instead.
     */
    public static boolean close(FormPanel form, boolean away) {
        if (up == null || up.form != form) {
            return false;
        }
        if (away) {
            up.over = true;
            return false;
        }
        return true;
    }

    /** The screen is built: true when the server closed the form while it was away, so it must go. */
    public boolean built() {
        up = this;
        return this.over;
    }

    /** The player closed the screen. */
    public void closedByPlayer() {
        this.byPlayer = true;
    }

    /**
     * The screen went for good (not off to the theme page): true when another screen replaced the
     * form, which {@code client} is told.
     */
    public boolean removed(FormClient client) {
        if (up == this) {
            up = null;
        }
        if (!this.byPlayer && client.shown() == this.form) {
            client.replaced();
            return true;
        }
        return false;
    }
}
