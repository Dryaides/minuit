package dev.dryaides.minuit;

/**
 * Time left, counting down: a round timer, an event about to start, a vote closing, a cooldown. It reads
 * "m:ss", or "h:mm:ss" from an hour up, and rounds up, so "0:00" shows only once the time is out. It shows, it
 * does not take input.
 *
 * <pre>{@code
 * CountdownComponent round = add(new CountdownComponent(at(new Box("round", 10, 10, 60, 9)), 90_000))
 *         .onDone(this::endRound);
 * add(new CountdownComponent(at(new Box("cool", 10, 30, 60, CountdownComponent.BAR_HEIGHT)), 5_000).bar(true));
 * }</pre>
 *
 * <p>Time comes from {@link BaseComponent#clock}. The listener runs from {@link #tick}, once, on the first tick
 * after the time is out. The last {@link #warnAt} seconds read in the warning colour.
 */
public class CountdownComponent extends BaseComponent {

    /** A line of text with a bar under it. */
    public static final int BAR_HEIGHT = 14;

    private static final int BAR_H = 4;
    private static final int DEFAULT_WARN_S = 10;

    private long totalMs;
    /** The clock time the count ends at while it runs. */
    private long endsAt;
    /** The time left while paused; -1 while it runs. */
    private long heldMs = -1;
    private boolean bar;
    private int warnAtS = DEFAULT_WARN_S;
    private Runnable onDone = () -> { };
    private boolean doneFired;

    /** Counts {@code durationMs} down from now. */
    public CountdownComponent(Box box, long durationMs) {
        super(box);
        restart(durationMs);
    }

    /** Counts {@code durationMs} down again from now, running, and lets the listener run once more. */
    public CountdownComponent restart(long durationMs) {
        this.totalMs = Math.max(0L, durationMs);
        this.endsAt = clock().getAsLong() + this.totalMs;
        this.heldMs = -1;
        this.doneFired = false;
        return this;
    }

    /** Ends the count at {@code clockMs} on {@link BaseComponent#clock}; the bar runs from now to then. */
    public CountdownComponent until(long clockMs) {
        long now = clock().getAsLong();
        return restart(Math.max(0L, clockMs - now));
    }

    /** Stops the time where it is; {@link #resume} goes on from there. */
    public CountdownComponent pause() {
        if (this.heldMs < 0) {
            this.heldMs = remainingMs();
        }
        return this;
    }

    public CountdownComponent resume() {
        if (this.heldMs >= 0) {
            this.endsAt = clock().getAsLong() + this.heldMs;
            this.heldMs = -1;
        }
        return this;
    }

    public boolean paused() {
        return this.heldMs >= 0;
    }

    /** Runs once when the time is out, from {@link #tick}. */
    public CountdownComponent onDone(Runnable onDone) {
        this.onDone = onDone == null ? () -> { } : onDone;
        return this;
    }

    /** Draws a bar under the time that empties as it runs out; the box wants {@link #BAR_HEIGHT}. */
    public CountdownComponent bar(boolean bar) {
        this.bar = bar;
        return this;
    }

    /** The last seconds that read in the warning colour; 0 for none. 10 by default. */
    public CountdownComponent warnAt(int seconds) {
        this.warnAtS = Math.max(0, seconds);
        return this;
    }

    public CountdownComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public CountdownComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    public long remainingMs() {
        if (this.heldMs >= 0) {
            return this.heldMs;
        }
        return Math.max(0L, this.endsAt - clock().getAsLong());
    }

    public boolean done() {
        return remainingMs() == 0L;
    }

    /** What {@code ms} left reads as: whole seconds rounded up, "m:ss" or "h:mm:ss". */
    public static String format(long ms) {
        long s = (Math.max(0L, ms) + 999L) / 1000L;
        long hours = s / 3600L;
        long minutes = s / 60L % 60L;
        long seconds = s % 60L;
        StringBuilder out = new StringBuilder();
        if (hours > 0) {
            out.append(hours).append(':');
            if (minutes < 10) {
                out.append('0');
            }
        }
        out.append(minutes).append(':');
        if (seconds < 10) {
            out.append('0');
        }
        return out.append(seconds).toString();
    }

    @Override
    public void tick() {
        if (!this.doneFired && done()) {
            this.doneFired = true;
            this.onDone.run();
        }
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        Palette p = t.palette();
        long left = remainingMs();
        String text = format(left);
        int surface = FormLook.surface(p);
        int ink;
        if (!this.active) {
            ink = p.disabledText();
        } else if (left == 0L) {
            ink = FormLook.soft(p);
        } else if (left <= this.warnAtS * 1000L) {
            ink = FormLook.readable(p.warn(), p.text(), surface);
        } else {
            ink = p.text();
        }
        int textH = this.bar ? RowLook.FONT_H : this.h;
        int textW = TextMeasure.width(text) - 1;
        d.text(text, this.x + (this.w - textW) / 2, RowLook.textY(this.y, textH) + textH % 2, ink);
        if (this.bar) {
            float share = this.totalMs == 0L ? 0F : left / (float) this.totalMs;
            int fill = !this.active ? p.disabled() : left <= this.warnAtS * 1000L ? p.warn() : p.accent();
            t.frame().bar(d, p, this.x, this.y + this.h - BAR_H, this.w, BAR_H, share, fill);
        }
    }
}
