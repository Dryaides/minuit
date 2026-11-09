package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fixed series schemes promise colours a viewer names apart at a glance: CIEDE2000 between any two. */
class SeriesColorsTest {

    /** About the smallest difference two chart colours can have and still be named apart at a glance. */
    private static final double MIN_DELTA_E = 10.0;

    @Test
    void fixedSchemesHoldColoursThatDiffer() {
        List<String> failures = new ArrayList<>();
        for (SeriesPalette scheme : new SeriesPalette[] {SeriesPalette.CONTRAST, SeriesPalette.VIVID, SeriesPalette.PASTEL}) {
            for (int i = 0; i < SeriesColors.COUNT; i++) {
                for (int k = i + 1; k < SeriesColors.COUNT; k++) {
                    double de = deltaE2000(SeriesColors.color(null, scheme, i), SeriesColors.color(null, scheme, k));
                    if (de < MIN_DELTA_E) {
                        failures.add(String.format("%s %d/%d deltaE %.1f", scheme, i, k, de));
                    }
                }
            }
        }
        assertTrue(failures.isEmpty(), failures.size() + " failures: " + failures);
    }

    private static double[] lab(int rgb) {
        double[] lin = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = (rgb >> (16 - 8 * i) & 0xFF) / 255.0;
            lin[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        double x = (0.4124564 * lin[0] + 0.3575761 * lin[1] + 0.1804375 * lin[2]) / 0.95047;
        double y = 0.2126729 * lin[0] + 0.7151522 * lin[1] + 0.0721750 * lin[2];
        double z = (0.0193339 * lin[0] + 0.1191920 * lin[1] + 0.9503041 * lin[2]) / 1.08883;
        double fx = f(x);
        double fy = f(y);
        double fz = f(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double f(double t) {
        return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
    }

    /** CIEDE2000 (Sharma, Wu, Dalal 2005). */
    static double deltaE2000(int rgb1, int rgb2) {
        double[] a = lab(rgb1);
        double[] b = lab(rgb2);
        double c1 = Math.hypot(a[1], a[2]);
        double c2 = Math.hypot(b[1], b[2]);
        double cm = (c1 + c2) / 2;
        double g = 0.5 * (1 - Math.sqrt(Math.pow(cm, 7) / (Math.pow(cm, 7) + Math.pow(25, 7))));
        double a1 = (1 + g) * a[1];
        double a2 = (1 + g) * b[1];
        double cp1 = Math.hypot(a1, a[2]);
        double cp2 = Math.hypot(a2, b[2]);
        double h1 = hue(a[2], a1);
        double h2 = hue(b[2], a2);
        double dL = b[0] - a[0];
        double dC = cp2 - cp1;
        double dh = 0;
        if (cp1 * cp2 != 0) {
            dh = h2 - h1;
            if (dh > 180) {
                dh -= 360;
            } else if (dh < -180) {
                dh += 360;
            }
        }
        double dH = 2 * Math.sqrt(cp1 * cp2) * Math.sin(Math.toRadians(dh / 2));
        double lm = (a[0] + b[0]) / 2;
        double cpm = (cp1 + cp2) / 2;
        double hm = h1 + h2;
        if (cp1 * cp2 != 0) {
            hm = Math.abs(h1 - h2) <= 180 ? (h1 + h2) / 2 : (h1 + h2 < 360 ? (h1 + h2 + 360) / 2 : (h1 + h2 - 360) / 2);
        }
        double t = 1 - 0.17 * Math.cos(Math.toRadians(hm - 30)) + 0.24 * Math.cos(Math.toRadians(2 * hm))
                + 0.32 * Math.cos(Math.toRadians(3 * hm + 6)) - 0.20 * Math.cos(Math.toRadians(4 * hm - 63));
        double sl = 1 + 0.015 * Math.pow(lm - 50, 2) / Math.sqrt(20 + Math.pow(lm - 50, 2));
        double sc = 1 + 0.045 * cpm;
        double sh = 1 + 0.015 * cpm * t;
        double dTheta = 30 * Math.exp(-Math.pow((hm - 275) / 25, 2));
        double rc = 2 * Math.sqrt(Math.pow(cpm, 7) / (Math.pow(cpm, 7) + Math.pow(25, 7)));
        double rt = -Math.sin(Math.toRadians(2 * dTheta)) * rc;
        double l = dL / sl;
        double c = dC / sc;
        double h = dH / sh;
        return Math.sqrt(l * l + c * c + h * h + rt * c * h);
    }

    private static double hue(double b, double a) {
        if (a == 0 && b == 0) {
            return 0;
        }
        double h = Math.toDegrees(Math.atan2(b, a));
        return h < 0 ? h + 360 : h;
    }
}
