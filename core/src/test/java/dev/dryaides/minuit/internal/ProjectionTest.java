package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.internal.port.CameraPose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionTest {

    /**
     * Facing west (yaw 90 looks along -X in the game) with a 70 degree vertical FOV: a point 10 blocks
     * ahead lands at the screen centre, one 10 * tan(35) blocks above it on the top edge, and one
     * behind the camera past z = 1.
     */
    @Test
    void aheadIsCentreAndFovEdgeIsTop() {
        CameraPose camera = new CameraPose(5, 64, -3, 90F, 0F, 70, 320, 240);
        double[] ahead = Projection.ndc(camera, 5 - 10, 64, -3);
        assertEquals(0, ahead[0], 1e-9);
        assertEquals(0, ahead[1], 1e-9);
        double[] edge = Projection.ndc(camera, 5 - 10, 64 + 10 * Math.tan(Math.toRadians(35)), -3);
        assertEquals(0, edge[0], 1e-9);
        assertEquals(1, edge[1], 1e-9);
        assertTrue(Projection.ndc(camera, 5 + 10, 64, -3)[2] > 1);
    }
}
