package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.internal.port.CameraPose;

/** Perspective projection from a {@link CameraPose}, for variants without the game's {@code projectPointToScreen}. */
public final class Projection {

    private static final double NEAR = 0.05;

    private Projection() {
    }

    /** {x, y, z}: x right and y up in -1..1 on screen, z in 0..1 in front of the camera and 2 behind it. */
    public static double[] ndc(CameraPose camera, double x, double y, double z) {
        double yaw = Math.toRadians(camera.yaw());
        double pitch = Math.toRadians(camera.pitch());
        double dx = x - camera.x();
        double dy = y - camera.y();
        double dz = z - camera.z();
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double rz = -Math.sin(yaw);
        // up = right x forward
        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;
        double forward = dx * fx + dy * fy + dz * fz;
        if (forward <= NEAR) {
            return new double[] {0, 0, 2};
        }
        double right = dx * rx + dz * rz;
        double up = dx * ux + dy * uy + dz * uz;
        double tan = Math.tan(Math.toRadians(camera.fov()) / 2.0);
        double aspect = camera.guiHeight() <= 0 ? 1.0 : camera.guiWidth() / (double) camera.guiHeight();
        return new double[] {right / forward / (tan * aspect), up / forward / tan, 1.0 - NEAR / forward};
    }
}
