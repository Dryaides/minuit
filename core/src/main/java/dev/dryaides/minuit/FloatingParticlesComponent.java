package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Redraw;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Small sprites that rise from the bottom of an area and fade out, such as hearts over a panel when a pet is
 * pleased. Lay it over a component with the same box, or on the HUD inside a {@link HudWidget}; it never takes
 * the mouse. Nothing shows until {@link #burst()}. Under reduced motion, or {@link MotionStyle#NONE}, a burst
 * shows nothing: the event the particles mark has to be told some other way too.
 *
 * <pre>{@code
 * FloatingParticlesComponent hearts = new FloatingParticlesComponent(panel.box(),
 *         AtlasIcon.whole("minecraft:textures/particle/heart.png", 8)).count(5).lifeMs(1400);
 * add(hearts);
 * // later
 * hearts.burst();
 * }</pre>
 */
public class FloatingParticlesComponent extends BaseComponent {

    private record Particle(int x, int sway, long start) {
    }

    private final IconArt sprite;
    private final List<Particle> live = new ArrayList<>();
    private Random random = new Random();
    private int count = 6;
    private long lifeMs = 1200;
    private int rise = -1;
    private int color;

    public FloatingParticlesComponent(Box area, IconArt sprite) {
        super(area);
        this.sprite = sprite;
    }

    /** How many sprites one {@link #burst()} lets go. */
    public FloatingParticlesComponent count(int n) {
        this.count = Math.max(1, n);
        return this;
    }

    /** How long one sprite lives, from the bottom of the area to its last frame. */
    public FloatingParticlesComponent lifeMs(long ms) {
        this.lifeMs = Math.max(100, ms);
        return this;
    }

    /** How far a sprite climbs over its life, in GUI pixels; the area's height by default. */
    public FloatingParticlesComponent rise(int px) {
        this.rise = px;
        return this;
    }

    /** The tint of a kit glyph; 0 for the palette's accent. A texture sprite keeps its own colours. */
    public FloatingParticlesComponent color(int argb) {
        this.color = argb;
        return this;
    }

    /** A fixed seed, so the same bursts land in the same places (the bench, a replay). */
    public FloatingParticlesComponent seed(long seed) {
        this.random = new Random(seed);
        return this;
    }

    public void burst() {
        burst(this.count);
    }

    /** Lets {@code n} sprites go, a little apart in time so they do not rise as one row. */
    public void burst(int n) {
        if (motion() == MotionStyle.NONE) {
            return;
        }
        long now = clock().getAsLong();
        int room = Math.max(1, this.w - this.sprite.width());
        for (int i = 0; i < n; i++) {
            long delay = i * this.lifeMs / (3L * Math.max(1, n));
            this.live.add(new Particle(this.x + this.random.nextInt(room), 2 + this.random.nextInt(3), now + delay));
        }
        Redraw.ask();
    }

    /** Sprites still rising, waiting ones included. */
    public int alive() {
        return this.live.size();
    }

    @Override
    public boolean isOver(double mx, double my) {
        return false;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        if (this.live.isEmpty()) {
            return;
        }
        if (motion() == MotionStyle.NONE) {
            this.live.clear();
            return;
        }
        long now = clock().getAsLong();
        int tint = this.color != 0 ? this.color : theme.palette().accent();
        int climb = this.rise >= 0 ? this.rise : this.h;
        int bottom = this.y + this.h - this.sprite.height();
        this.live.removeIf(p -> now - p.start() >= this.lifeMs);
        for (Particle p : this.live) {
            long age = now - p.start();
            if (age < 0) {
                continue;
            }
            float t = age / (float) this.lifeMs;
            float eased = 1F - (1F - t) * (1F - t);
            int px = p.x() + Math.round((float) Math.sin(t * Math.PI * 2) * p.sway());
            int py = bottom - Math.round(eased * climb);
            float alpha = Math.min(1F, Math.min(t * 8F, (1F - t) * 2.5F));
            this.sprite.draw(d, px, py, tint, alpha);
        }
        Redraw.ask();
    }
}
