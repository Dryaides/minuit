package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;

/**
 * Listening bars scaled by a live level: 2 px bars on a 4 px pitch, flat when nobody speaks, full
 * height when the level is full.
 */
public class WaveComponent extends BaseComponent {

    private float level;
    public boolean live;
    /** The colour of a loud bar; 0 keeps the theme's accent. */
    public int color;

    public WaveComponent(Box box) {
        super(box);
    }

    /** 0 to 1, on {@link MeterComponent#position}'s scale. */
    public void level(float value) {
        this.level = Maths.clamp(value, 0F, 1F);
    }

    public float level() {
        return this.level;
    }

    /** Half height of bar {@code i} at {@code ticks}: a travelling wave times the level. */
    public static int half(double ticks, int i, float level) {
        double wave = Math.sin(ticks * 0.45 + i * 0.7) * Math.sin(ticks * 0.13 + i * 0.21);
        return 1 + (int) Math.round(Math.abs(wave) * 5 * level);
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        theme.frame().well(d, p, this.x, this.y, this.w, this.h, false);
        double ticks = BaseComponent.clock.getAsLong() / 50.0;
        int lit = this.color != 0 ? this.color : Shapes.mix(p.accent(), 0xFFFFFFFF, 0.35F);
        int middle = this.y + this.h / 2;
        int bars = (this.w - 12) / 4;
        // Bars stay inside the well's 2 px edge, whatever the level or the height.
        int room = Math.max(1, this.h / 2 - 2);
        for (int i = 0; i < bars; i++) {
            int half = Math.min(room, this.live ? half(ticks, i, this.level) : 1);
            int bx = this.x + 6 + i * 4;
            d.fill(bx, middle - half, bx + 2, middle + half,
                    this.live ? Shapes.mix(p.raisedHover(), lit, half / 6F) : p.wellEdge());
        }
    }
}
