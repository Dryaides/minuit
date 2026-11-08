package dev.dryaides.minuit.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadialMathTest {

    @Test
    void compassPointsLandOnTheirPartClockwiseFromTheTop() {
        // Four parts: top, right, bottom, left. Screen y points down, so "up" is dy < 0.
        assertEquals(0, RadialMath.sector(0, -10, 4));
        assertEquals(1, RadialMath.sector(10, 0, 4));
        assertEquals(2, RadialMath.sector(0, 10, 4));
        assertEquals(3, RadialMath.sector(-10, 0, 4));
        // Eight parts: up-right is the second one.
        assertEquals(1, RadialMath.sector(10, -10, 8));
    }

    @Test
    void aPartSpansHalfAPartEachSideOfItsAngle() {
        // Six parts of 60 degrees; part 0 covers -30 to +30 around the top.
        double r = 50;
        assertEquals(0, RadialMath.sector(r * Math.sin(Math.toRadians(29)), -r * Math.cos(Math.toRadians(29)), 6));
        assertEquals(1, RadialMath.sector(r * Math.sin(Math.toRadians(31)), -r * Math.cos(Math.toRadians(31)), 6));
        assertEquals(0, RadialMath.sector(r * Math.sin(Math.toRadians(-29)), -r * Math.cos(Math.toRadians(-29)), 6));
        assertEquals(5, RadialMath.sector(r * Math.sin(Math.toRadians(-31)), -r * Math.cos(Math.toRadians(-31)), 6));
    }

    @Test
    void deadZoneIsUnderPoint45OfTheRadius() {
        assertTrue(RadialMath.inDeadZone(0, 44, 100, 0.45));
        assertFalse(RadialMath.inDeadZone(0, 46, 100, 0.45));
        assertTrue(RadialMath.inDeadZone(30, 30, 100, 0.45)); // 42.4 px away
    }

    @Test
    void oneNotchIsOnePageAndWraps() {
        assertEquals(1, RadialMath.pageStep(0, -1, 3));
        assertEquals(2, RadialMath.pageStep(0, 1, 3));
        assertEquals(0, RadialMath.pageStep(2, -1, 3));
        assertEquals(0, RadialMath.pageStep(0, -1, 1));
    }

    @Test
    void slideEasesOutOver160Ms() {
        assertEquals(0F, RadialMath.slide(1000, 1000, 160), 1e-6);
        // Half the time covers three quarters of the way: 1 - (1 - 0.5)^2.
        assertEquals(0.75F, RadialMath.slide(1000, 1080, 160), 1e-6);
        assertEquals(1F, RadialMath.slide(1000, 1160, 160), 1e-6);
    }

    @Test
    void theDrawnPartUnderTheCursorIsTheAimedOne() {
        // Sweep every degree of a ring of 7 parts at its mid radius: a covered pixel belongs to
        // the part a release there would pick, and the borders between parts are left open.
        int covered = 0;
        for (int deg = 0; deg < 360; deg++) {
            double a = Math.toRadians(deg);
            double dx = 70 * Math.sin(a);
            double dy = -70 * Math.cos(a);
            int hit = RadialMath.ring(dx, dy, 7, 50, 90, 2, -1, 0);
            if (hit != RadialMath.OUTSIDE) {
                covered++;
                assertEquals(RadialMath.sector(dx, dy, 7), hit / 4, "degree " + deg);
            }
        }
        assertTrue(covered > 340 && covered < 360, "covered " + covered);
        // Border between parts 0 and 1 of 4 sits at 45 degrees.
        assertEquals(RadialMath.OUTSIDE, RadialMath.ring(70 * Math.sin(Math.PI / 4), -70 * Math.cos(Math.PI / 4),
                4, 50, 90, 2, -1, 0));
    }

    @Test
    void theLiftedPartStandsOutAndLeavesItsShadowBehind() {
        // Right part of 4 (index 1) lifted 3 px: it reaches past the outer radius, the others stop at it.
        assertEquals(1 * 4 + RadialMath.FACE, RadialMath.ring(91.5, 0, 4, 50, 90, 2, 1, 3));
        assertEquals(RadialMath.OUTSIDE, RadialMath.ring(0, 91.5, 4, 50, 90, 2, 1, 3));
        assertEquals(1 * 4 + RadialMath.SHADOW, RadialMath.ring(51.5, 0, 4, 50, 90, 2, 1, 3));
        assertEquals(2 * 4 + RadialMath.EDGE, RadialMath.ring(0, 50.5, 4, 50, 90, 2, 1, 3));
    }
}
