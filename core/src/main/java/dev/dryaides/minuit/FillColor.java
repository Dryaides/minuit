package dev.dryaides.minuit;

/**
 * The fill colour of a bar as a function of its value, read again at each frame so the colour follows
 * the value and the theme. Used by {@link ProgressComponent#fillColor} and {@link HudBar#fillColor}.
 *
 * <pre>{@code
 * // red under a quarter, the warning colour under a half, the accent above
 * bar.fillColor(FillColor.zones(new float[] {0.25F, 0.5F}, HudBar.Tone.BAD, HudBar.Tone.WARN, HudBar.Tone.ACCENT));
 * }</pre>
 */
@FunctionalInterface
public interface FillColor {

    /** @param fraction the bar's value, 0 to 1 */
    int color(float fraction, Palette palette);

    /**
     * Palette tones by zone: {@code tones[i]} fills the bar while the value is under {@code limits[i]}, the last tone
     * from the highest limit up. A value on a limit belongs to the zone above it.
     *
     * @param limits ascending, between 0 and 1
     * @param tones  one more than {@code limits}
     */
    static FillColor zones(float[] limits, HudBar.Tone... tones) {
        if (tones.length != limits.length + 1) {
            throw new IllegalArgumentException("zones needs one tone more than limits: " + limits.length + " limits, "
                    + tones.length + " tones");
        }
        float[] at = limits.clone();
        HudBar.Tone[] by = tones.clone();
        return (fraction, palette) -> by[zone(at, fraction)].of(palette);
    }

    /** The index of the zone {@code fraction} falls in, 0 under the first limit. */
    static int zone(float[] limits, float fraction) {
        int i = 0;
        while (i < limits.length && fraction >= limits[i]) {
            i++;
        }
        return i;
    }
}
