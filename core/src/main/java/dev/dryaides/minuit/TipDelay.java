package dev.dryaides.minuit;

import java.util.List;

/** Holds a tooltip back until the same tip has stayed under the mouse for {@link #MS}, on {@link BaseComponent#clock}. */
public final class TipDelay {

    public static final long MS = 500;

    private List<String> lines;
    private long since;

    /** Draws {@code shown} (null for none) once it has been the same tip for {@link #MS}. */
    public void draw(Draw d, List<String> shown, int mx, int my) {
        long now = BaseComponent.clock.getAsLong();
        if (shown == null || !shown.equals(this.lines)) {
            this.lines = shown;
            this.since = now;
        }
        if (shown != null && now - this.since >= MS) {
            d.tooltip(shown, mx, my);
        }
    }
}
