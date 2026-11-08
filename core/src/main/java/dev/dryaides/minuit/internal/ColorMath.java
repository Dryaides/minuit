package dev.dryaides.minuit.internal;

/** HSV and RGB, as the colour picker holds and hands out a colour. */
public final class ColorMath {

    private ColorMath() {
    }

    /**
     * {@code 0xAARRGGBB} of a hue in degrees (any value, taken modulo 360), a saturation and a
     * value from 0 to 1, and an alpha from 0 to 255.
     */
    public static int argb(float hue, float sat, float val, int alpha) {
        float h = ((hue % 360F) + 360F) % 360F / 60F;
        float s = Maths.clamp(sat, 0F, 1F);
        float v = Maths.clamp(val, 0F, 1F);
        float c = v * s;
        float x = c * (1F - Math.abs(h % 2F - 1F));
        float r;
        float g;
        float b;
        switch ((int) h) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        float m = v - c;
        return (Maths.clamp(alpha, 0, 255) << 24) | (channel(r + m) << 16) | (channel(g + m) << 8) | channel(b + m);
    }

    private static int channel(float f) {
        return Maths.clamp(Math.round(f * 255F), 0, 255);
    }

    /** Hue in degrees (0 for a grey), saturation and value from 0 to 1. Alpha is ignored. */
    public static float[] hsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255F;
        float g = ((argb >> 8) & 0xFF) / 255F;
        float b = (argb & 0xFF) / 255F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float hue;
        if (d == 0) {
            hue = 0;
        } else if (max == r) {
            hue = 60F * (((g - b) / d) % 6F);
        } else if (max == g) {
            hue = 60F * ((b - r) / d + 2F);
        } else {
            hue = 60F * ((r - g) / d + 4F);
        }
        if (hue < 0) {
            hue += 360F;
        }
        return new float[]{hue, max == 0 ? 0 : d / max, max};
    }
}
