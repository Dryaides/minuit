package dev.dryaides.minuit.internal.port;

/**
 * An entity that says a bubble, at the frame's partial tick: feet position, eye height and collision
 * box height in blocks, and whether it is the camera entity. Not API.
 */
public record Speaker(double x, double y, double z, double eyeHeight, double bbHeight,
                      boolean alive, boolean showsName, boolean player, boolean camera) {
}
