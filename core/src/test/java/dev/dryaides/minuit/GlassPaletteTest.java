package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ThemeFiles;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlassPaletteTest {

    /** WCAG 2 relative luminance, written from the spec rather than taken from the kit. */
    private static double luminance(int argb) {
        double sum = 0;
        double[] weight = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) {
            double v = (argb >> (16 - 8 * i) & 0xFF) / 255.0;
            sum += weight[i] * (v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
        }
        return sum;
    }

    private static double ratio(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    /** {@code top} as it shows over the opaque {@code under}. */
    private static int over(int top, int under) {
        double a = (top >>> 24) / 255.0;
        int out = 0xFF000000;
        for (int s = 0; s <= 16; s += 8) {
            out |= (int) Math.round((top >> s & 0xFF) * a + (under >> s & 0xFF) * (1 - a)) << s;
        }
        return out;
    }

    /**
     * WCAG 1.4.3 (4.5:1 for body text): in the glass mode of every shipped chroma, each text role reads on its face,
     * the face laid on the panel's smoke and the smoke on the brightest backdrop (a white sky, no dim) and the
     * darkest (black).
     */
    @Test
    void glassTextReadsOverWhiteAndBlack() throws Exception {
        Themes.load(ThemeFiles.assemble(ThemeFiles.fromDirectory(
                Path.of("src/main/resources/assets/minuit/minuit"), "minuit"), s -> { }));
        List<ThemeFamily> families = Themes.families(null);
        assertFalse(families.isEmpty());
        for (ThemeFamily family : families) {
            for (ThemeFamily.Chroma chroma : family.chromas()) {
                Palette g = chroma.palette(ThemeChoice.Variant.GLASS);
                for (int backdrop : new int[] {0xFFFFFFFF, 0xFF000000}) {
                    int panel = over(g.surface(), backdrop);
                    String where = family.id() + "." + chroma.id() + " over " + Integer.toHexString(backdrop);
                    assertTrue(ratio(g.text(), panel) >= 4.5, where + ": text");
                    assertTrue(ratio(g.raisedText(), over(g.raised(), panel)) >= 4.5, where + ": raisedText");
                    assertTrue(ratio(g.primaryText(), over(g.primary(), panel)) >= 4.5, where + ": primaryText");
                    assertTrue(ratio(g.destructiveText(), over(g.destructive(), panel)) >= 4.5,
                            where + ": destructiveText");
                }
            }
        }
    }
}
