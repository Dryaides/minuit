package dev.dryaides.minuit.internal;

/**
 * How an entity preview sits in its box: the model scale that fits 80% of the box height, the lift to
 * its middle, and the pitch as a tilt in radians. Not API.
 */
public record EntityPose(float size, float lift, float tilt) {

    public static EntityPose of(int y0, int y1, float boxHeight, float pitch) {
        float size = (y1 - y0) * 0.8F / Math.max(0.1F, boxHeight);
        return new EntityPose(size, boxHeight / 2F, pitch * (float) (Math.PI / 180.0));
    }
}
