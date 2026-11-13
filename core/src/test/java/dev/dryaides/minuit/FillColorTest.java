package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FillColorTest {

    /** A need bar: red under a quarter, the warning colour under a half, the accent from a half up, edges included. */
    @Test
    void needBarZones() {
        Palette p = Themes.SLATE.palette();
        FillColor need = FillColor.zones(new float[] {0.25F, 0.5F}, HudBar.Tone.BAD, HudBar.Tone.WARN, HudBar.Tone.ACCENT);
        assertEquals(p.bad(), need.color(0F, p));
        assertEquals(p.bad(), need.color(0.2F, p));
        assertEquals(p.warn(), need.color(0.25F, p));
        assertEquals(p.warn(), need.color(0.49F, p));
        assertEquals(p.accent(), need.color(0.5F, p));
        assertEquals(p.accent(), need.color(1F, p));
    }

    @Test
    void zonesNeedOneToneMoreThanLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> FillColor.zones(new float[] {0.25F, 0.5F}, HudBar.Tone.BAD, HudBar.Tone.ACCENT));
    }
}
