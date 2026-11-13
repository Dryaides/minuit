package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Redraw;
import dev.dryaides.minuit.internal.Maths;

/**
 * A slide in and out on {@link BaseComponent#clock}: a widget, a region or a whole panel comes from
 * the side {@link #from()} to its place when shown and goes back when hidden. Read it on every frame.
 *
 * <pre>{@code
 * Glide rows = new Glide(Slide.DOWN, 170);
 * rows.snap(false);                        // folded at first
 * inv.rows.glide(rows, 58);                // the widget draws 58 px down while folded
 * rows.show();                             // on hover; hide() folds, from wherever it is
 * }</pre>
 *
 * <p>A change of target mid-way turns back from the current point, so nothing jumps. Under
 * {@link BaseComponent#reducedMotion}, or when the motion style resolves to {@link MotionStyle#NONE},
 * every read gives the target at once.
 */
public final class Glide {

    /** How the way covered maps to the time spent. */
    public enum Easing {
        /** Fast start, soft landing. */
        OUT_CUBIC,
        /** Soft start and soft landing. */
        IN_OUT_CUBIC,
        LINEAR;

        /** The part of the way covered after the part {@code t} of the time, both 0 to 1. */
        public float apply(float t) {
            switch (this) {
                case OUT_CUBIC:
                    return (float) Ease.outCubic(t);
                case IN_OUT_CUBIC:
                    return (float) Ease.inOutCubic(t);
                default:
                    return t;
            }
        }
    }

    private final Slide from;
    private final int durationMs;
    private Easing easing = Easing.OUT_CUBIC;
    private boolean shown = true;
    /** The part of the time covered toward shown, 0 to 1, at {@link #at}. */
    private float phase = 1F;
    /** The clock when {@link #phase} was taken; negative starts the clock on the next read. */
    private long at;

    /** Shown and at rest; {@code from} is the side it hides to, {@code durationMs} a full way. */
    public Glide(Slide from, int durationMs) {
        this.from = from;
        this.durationMs = Math.max(1, durationMs);
        this.at = BaseComponent.clock().getAsLong();
    }

    public Slide from() {
        return this.from;
    }

    public Glide easing(Easing easing) {
        this.easing = easing;
        return this;
    }

    /** Glides to its place, from where it is now. */
    public void show() {
        retarget(true);
    }

    /** Glides away, from where it is now. */
    public void hide() {
        retarget(false);
    }

    /** Away at once, then glides in, the clock starting on the next frame: a page or a screen that opens. */
    public void replay() {
        this.phase = 0F;
        this.shown = true;
        this.at = -1;
    }

    /** At its place or away at once, with no motion. */
    public void snap(boolean shown) {
        this.shown = shown;
        this.phase = shown ? 1F : 0F;
        this.at = BaseComponent.clock().getAsLong();
    }

    /** The target: true while shown or showing. */
    public boolean shown() {
        return this.shown;
    }

    /** True while on the way; false at rest, shown or away. */
    public boolean moving() {
        float p = phase();
        return !still() && p != (this.shown ? 1F : 0F);
    }

    /** True at rest away: nothing to draw. */
    public boolean away() {
        return !this.shown && !moving();
    }

    /** The part of the way to its place covered now, eased: 0 away, 1 at its place. */
    public float progress() {
        if (still()) {
            return this.shown ? 1F : 0F;
        }
        return this.easing.apply(phase());
    }

    /** The x offset from its place now, for a glide over {@code distance} px. */
    public float offsetX(float distance) {
        return this.from.dirX() * distance * (1F - progress());
    }

    /** The y offset from its place now, for a glide over {@code distance} px. */
    public float offsetY(float distance) {
        return this.from.dirY() * distance * (1F - progress());
    }

    private void retarget(boolean to) {
        if (to == this.shown) {
            return;
        }
        this.phase = phase();
        this.at = BaseComponent.clock().getAsLong();
        this.shown = to;
    }

    private float phase() {
        long now = Redraw.quietClock();
        if (this.at < 0) {
            this.at = now;
        }
        float step = (now - this.at) / (float) this.durationMs;
        float p = Maths.clamp(this.phase + (this.shown ? step : -step), 0F, 1F);
        if (p != (this.shown ? 1F : 0F)) {
            Redraw.ask();
        }
        return p;
    }

    private static boolean still() {
        return MotionStyle.resolve(BaseComponent.reducedMotion(), BaseComponent.playerMotion(), null,
                BaseComponent.screenMotion()) == MotionStyle.NONE;
    }
}
