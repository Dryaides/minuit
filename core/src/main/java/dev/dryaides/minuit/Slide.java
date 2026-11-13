package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;

/**
 * A side something glides in from: a screen's panel when another screen opens it (ease-out cubic
 * over {@link TabsComponent#SLIDE_MS}, from a quarter of the panel's size away), or any {@link Glide}.
 * Nothing moves under {@link BaseComponent#reducedMotion}.
 */
public enum Slide {
    /** From the left: back to the screen before. */
    LEFT(-1, 0),
    /** From the right: on to the next screen, as a tab further right. */
    RIGHT(1, 0),
    UP(0, -1),
    DOWN(0, 1);

    private final int dirX;
    private final int dirY;

    Slide(int dirX, int dirY) {
        this.dirX = dirX;
        this.dirY = dirY;
    }

    int dirX() {
        return this.dirX;
    }

    int dirY() {
        return this.dirY;
    }

    /** How far the panel still is from its place {@code elapsedMs} into the glide, as {x, y}. */
    public int[] offset(long elapsedMs, int width, int height) {
        if (BaseComponent.reducedMotion() || elapsedMs >= TabsComponent.SLIDE_MS) {
            return new int[]{0, 0};
        }
        float left = remaining(elapsedMs);
        return new int[]{Math.round(this.dirX * left * width / 4F), Math.round(this.dirY * left * height / 4F)};
    }

    /**
     * A mouse point on screen {@code elapsedMs} into the glide, moved into the panel's resting
     * coordinates, so a click lands on the widget drawn under the cursor.
     */
    public double[] toPanel(double mx, double my, long elapsedMs, int width, int height) {
        int[] off = offset(elapsedMs, width, height);
        return new double[]{mx - off[0], my - off[1]};
    }

    /** The part of the way left to go, 1 at the start and 0 at the end. */
    static float remaining(long elapsedMs) {
        return 1F - Glide.Easing.OUT_CUBIC.apply(Maths.clamp(elapsedMs / (float) TabsComponent.SLIDE_MS, 0F, 1F));
    }
}
