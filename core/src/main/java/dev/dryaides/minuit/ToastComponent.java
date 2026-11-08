package dev.dryaides.minuit;

/**
 * One toast inside a panel, drawn as the game's toast stack would: for a site demo or a settings
 * preview. Its time starts at its first frame and again whenever {@link #toast} changes.
 */
public class ToastComponent extends BaseComponent {

    public ToastSpec toast;
    private ToastSpec timed;
    private long since = -1;

    public ToastComponent(Box box, ToastSpec toast) {
        super(box);
        this.toast = toast;
    }

    /** Starts the time as if the toast came in {@code ms} ago: a preview of its fade. */
    public ToastComponent shownFor(long ms) {
        this.timed = this.toast;
        this.since = clock.getAsLong() - ms;
        return this;
    }

    /** Milliseconds since the toast came in, for a bench that draws one moment of it. */
    public long shownMs() {
        return this.since < 0 ? 0 : clock.getAsLong() - this.since;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        if (this.toast == null) {
            return;
        }
        if (this.toast != this.timed || this.since < 0) {
            this.timed = this.toast;
            this.since = clock.getAsLong();
        }
        ToastArt.draw(d, theme, this.toast, this.x, this.y, shownMs());
    }
}
