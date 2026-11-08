package dev.dryaides.minuit.internal.port;

import dev.dryaides.minuit.internal.Projection;

/** The world as the bubble layer sees it, built by the variant each frame. Not API. */
public interface WorldView {

    /** False on the title screen or while the level loads. */
    boolean inWorld();

    /** Null when no entity has this network id on the client. */
    Speaker speaker(int entityId, float partial);

    CameraPose camera(float partial);

    boolean firstPerson();

    int guiScale();

    /**
     * The point in normalised device coordinates: x right and y up in -1..1 on screen, z over 1 behind
     * the camera. A variant with the game's own projection overrides it.
     */
    default double[] project(double x, double y, double z) {
        return Projection.ndc(camera(0F), x, y, z);
    }
}
