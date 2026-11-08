package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;

/**
 * A live level meter on a decibel scale. The gate is drawn as a tick: below it the bar is faint,
 * above it the bar takes the {@code good} colour.
 *
 * <p>Fed with RMS values on the 16 bit scale (0 to 32767). The peak is held for a second, then
 * falls.
 */
public class MeterComponent extends BaseComponent {

    /** Bottom of the scale. -60 dBFS is below any microphone's noise floor. */
    public static final double FLOOR_DB = -60.0;

    private float level;
    private float peak;
    private long peakAt;
    private float gate = -1;
    public boolean live;

    public MeterComponent(Box box) {
        super(box);
    }

    /** 0 to 1 on the meter's scale for an RMS on the 16 bit scale. */
    public static float position(double rms) {
        if (rms <= 0) {
            return 0F;
        }
        double db = 20.0 * Math.log10(rms / 32767.0);
        return (float) Maths.clamp((db - FLOOR_DB) / -FLOOR_DB, 0.0, 1.0);
    }

    /** A new reading; {@code now} in milliseconds. */
    public void level(double rms, double gateRms, long now) {
        this.level = position(rms);
        this.gate = gateRms > 0 ? position(gateRms) : -1;
        if (this.level >= this.peak) {
            this.peak = this.level;
            this.peakAt = now;
        } else if (now - this.peakAt > 1_000L) {
            this.peak = Math.max(this.level, this.peak - 0.04F);
        }
    }

    public void reset() {
        this.level = 0;
        this.peak = 0;
    }

    public boolean aboveGate() {
        return this.gate >= 0 && this.level >= this.gate;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        theme.frame().well(d, p, this.x, this.y, this.w, this.h, this.live);
        int inner = this.w - 4;
        int top = this.y + 2;
        int bottom = this.y + this.h - 2;
        int filled = Math.round(this.level * inner);
        int color = !this.live ? p.disabledText() : aboveGate() ? p.good() : p.textFaint();
        if (filled > 0) {
            d.fill(this.x + 2, top, this.x + 2 + filled, bottom, color);
        }
        // Segments, so the bar reads as a meter and not as a progress bar.
        for (int sx = this.x + 2 + 4; sx < this.x + 2 + inner; sx += 5) {
            d.fill(sx, top, sx + 1, bottom, Shapes.alpha(p.well(), 160));
        }
        if (this.live && this.peak > 0) {
            int px = this.x + 2 + Math.min(inner - 1, Math.round(this.peak * inner));
            d.fill(px, top, px + 1, bottom, p.text());
        }
        if (this.gate >= 0) {
            int gx = this.x + 2 + Math.round(this.gate * inner);
            d.fill(gx, this.y, gx + 1, this.y + this.h, p.warn());
        }
    }
}
