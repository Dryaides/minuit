package dev.dryaides.minuit.internal;

/**
 * How an entity preview sits in its box: the model scale that fits 80% of the box height, the lift to
 * its middle, and the pitch as a tilt in radians. Not API.
 */
public record EntityPose(float size, float lift, float tilt) {

    /** Blocks above the hitbox the model may reach (hair, hat, a model taller than its box). */
    private static final float HEAD_ROOM = 0.25F;

    public static EntityPose of(int y0, int y1, float boxHeight, float pitch) {
        float span = Math.max(0.1F, boxHeight) + HEAD_ROOM;
        float size = (y1 - y0) * 0.8F / span;
        return new EntityPose(size, span / 2F, pitch * (float) (Math.PI / 180.0));
    }
}
