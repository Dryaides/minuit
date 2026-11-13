// Adapted from Cinematic Interactions (MIT, Copyright (c) 2026 Southware), client/Anim.java.
package dev.dryaides.minuit;

/**
 * Easing curves on [0, 1], with the clamps, windows and frame-rate independent approach they go with. Every
 * curve clamps {@code t} first, maps 0 to 0 and 1 to 1. {@link Glide.Easing} names the ones a glide takes.
 */
public final class Ease {
    private Ease() {
    }

    public static double clamp01(double t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    /** {@code t} remapped from [a, b] onto [0, 1], clamped: one phase of a longer animation. */
    public static double window(double t, double a, double b) {
        return clamp01((t - a) / (b - a));
    }

    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** Fast start, soft landing. */
    public static double outCubic(double t) {
        double r = 1 - clamp01(t);
        return 1 - r * r * r;
    }

    public static double inCubic(double t) {
        t = clamp01(t);
        return t * t * t;
    }

    /** Soft start and soft landing. */
    public static double inOutCubic(double t) {
        t = clamp01(t);
        if (t < 0.5) {
            return 4 * t * t * t;
        }
        double s = -2 * t + 2;
        return 1 - s * s * s / 2;
    }

    public static double inOutSine(double t) {
        return -(Math.cos(Math.PI * clamp01(t)) - 1) / 2;
    }

    /** Perlin's smootherstep: flat speed and acceleration at both ends. */
    public static double smoother(double t) {
        t = clamp01(t);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** Overshoots 1 by an amount set by {@code overshoot} (1.70158 is the usual), then settles on it. */
    public static double outBack(double t, double overshoot) {
        double u = clamp01(t) - 1;
        return 1 + (overshoot + 1) * u * u * u + overshoot * u * u;
    }

    /** A sine hump: 0 at both ends, 1 in the middle. */
    public static double hump(double t) {
        return Math.sin(Math.PI * clamp01(t));
    }

    /** {@code current} moved toward {@code target} over {@code dt} seconds at {@code rate} per second, the same at any frame rate. */
    public static double approach(double current, double target, double rate, double dt) {
        return target + (current - target) * Math.exp(-rate * dt);
    }

    /** Degrees wrapped into [-180, 180). */
    public static double wrapDegrees(double deg) {
        double d = deg % 360;
        if (d >= 180) {
            d -= 360;
        } else if (d < -180) {
            d += 360;
        }
        return d;
    }
}
