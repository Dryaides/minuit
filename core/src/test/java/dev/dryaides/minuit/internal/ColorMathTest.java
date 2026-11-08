package dev.dryaides.minuit.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorMathTest {

    @Test
    void knownColoursFromHsv() {
        assertEquals(0xFFFF0000, ColorMath.argb(0, 1, 1, 255));
        assertEquals(0xFF00FF00, ColorMath.argb(120, 1, 1, 255));
        assertEquals(0xFF0000FF, ColorMath.argb(240, 1, 1, 255));
        assertEquals(0xFFFFFF00, ColorMath.argb(60, 1, 1, 255));
        assertEquals(0xFF6699CC, ColorMath.argb(210, 0.5F, 0.8F, 255));
        assertEquals(0xFFFF0000, ColorMath.argb(360, 1, 1, 255));
        assertEquals(0x80000000, ColorMath.argb(77, 0.3F, 0, 128));
    }

    @Test
    void knownColoursToHsv() {
        assertArrayEquals(new float[]{0, 1, 1}, ColorMath.hsv(0xFFFF0000), 1e-4F);
        assertArrayEquals(new float[]{300, 1, 1}, ColorMath.hsv(0xFFFF00FF), 1e-4F);
        assertArrayEquals(new float[]{210, 0.5F, 0.8F}, ColorMath.hsv(0xFF6699CC), 1e-4F);
        assertArrayEquals(new float[]{0, 0, 128 / 255F}, ColorMath.hsv(0xFF808080), 1e-4F);
    }

    @Test
    void everyRgbCubeCornerAndStepSurvivesARoundTrip() {
        for (int r = 0; r < 256; r += 17) {
            for (int g = 0; g < 256; g += 17) {
                for (int b = 0; b < 256; b += 17) {
                    int argb = 0xFF000000 | (r << 16) | (g << 8) | b;
                    float[] hsv = ColorMath.hsv(argb);
                    assertEquals(argb, ColorMath.argb(hsv[0], hsv[1], hsv[2], 255), Integer.toHexString(argb));
                }
            }
        }
    }
}
