package dev.dryaides.minuit.internal.port;

/**
 * The camera entity's eye, its view angles in the game's convention (yaw 0 looks along +Z, pitch
 * positive looks down, both in degrees), the vertical field of view in degrees, and the GUI size. Not API.
 */
public record CameraPose(double x, double y, double z, float yaw, float pitch, double fov, int guiWidth, int guiHeight) {
}
