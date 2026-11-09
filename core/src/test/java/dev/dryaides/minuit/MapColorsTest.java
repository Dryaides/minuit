package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapColorsTest {
    /** {@code MapColor.col} by id, as written in net.minecraft.world.level.material.MapColor of the 26.3 jar. */
    private static final int[] GAME_COLORS = {
        0, 8368696, 16247203, 13092807, 16711680, 10526975,
        10987431, 31744, 16777215, 10791096, 9923917, 7368816,
        4210943, 9402184, 16776437, 14188339, 11685080, 6724056,
        15066419, 8375321, 15892389, 5000268, 10066329, 5013401,
        8339378, 3361970, 6704179, 6717235, 10040115, 1644825,
        16445005, 6085589, 4882687, 55610, 8476209, 7340544,
        13742497, 10441252, 9787244, 7367818, 12223780, 6780213,
        10505550, 3746083, 8874850, 5725276, 8014168, 4996700,
        4993571, 5001770, 9321518, 2430480, 12398641, 9715553,
        6035741, 1474182, 3837580, 5647422, 1356933, 6579300,
        14200723, 8365974,
    };
    /** {@code MapColor.Brightness.modifier}: LOW, NORMAL, HIGH, LOWEST. */
    private static final int[] GAME_BRIGHTNESS = {180, 220, 255, 135};

    /** The game's {@code getColorFromPackedId}: NONE and unknown ids are 0, else ARGB.scaleRGB(opaque(col), modifier). */
    private static int game(int packed) {
        int id = packed >> 2;
        if (id == 0 || id >= GAME_COLORS.length) {
            return 0;
        }
        int col = GAME_COLORS[id];
        int m = GAME_BRIGHTNESS[packed & 3];
        return 0xFF000000 | ((col >> 16 & 0xFF) * m / 255) << 16 | ((col >> 8 & 0xFF) * m / 255) << 8 | (col & 0xFF) * m / 255;
    }

    @Test
    void everyPackedIdMatchesTheGame() {
        for (int packed = 0; packed < 256; packed++) {
            assertEquals(Integer.toHexString(game(packed)), Integer.toHexString(MapColors.argb(packed)), "packed id " + packed);
        }
    }
}
