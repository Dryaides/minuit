package dev.dryaides.minuit;

/**
 * The game's map palette, copied from {@code net.minecraft.world.level.material.MapColor}: 62 base colours
 * ({@code MapColor.col}, ids 0 to 61) times four brightnesses ({@code MapColor.Brightness}: LOW 180, NORMAL 220,
 * HIGH 255, LOWEST 135). A map's byte is a packed id, {@code base * 4 + brightness}, as in
 * {@code MapItemSavedData.colors}; {@link #argb(int)} gives what {@code MapColor.getColorFromPackedId} gives,
 * with id 0 to 3 (NONE) transparent: the paper shows through, as in game.
 */
final class MapColors {
    /** Map sides in pixels: {@code MapRenderer.WIDTH} and {@code HEIGHT}. */
    public static final int SIZE = 128;

    public static final int NONE = 0;
    public static final int GRASS = 1;
    public static final int SAND = 2;
    public static final int WOOL = 3;
    public static final int FIRE = 4;
    public static final int ICE = 5;
    public static final int METAL = 6;
    public static final int PLANT = 7;
    public static final int SNOW = 8;
    public static final int CLAY = 9;
    public static final int DIRT = 10;
    public static final int STONE = 11;
    public static final int WATER = 12;
    public static final int WOOD = 13;
    public static final int QUARTZ = 14;
    public static final int COLOR_ORANGE = 15;
    public static final int COLOR_YELLOW = 18;
    public static final int COLOR_GREEN = 27;
    public static final int COLOR_RED = 28;
    public static final int GOLD = 30;
    public static final int PODZOL = 34;
    public static final int NETHER = 35;
    public static final int DEEPSLATE = 59;

    public static final int LOW = 0;
    public static final int NORMAL = 1;
    public static final int HIGH = 2;
    public static final int LOWEST = 3;

    private static final int[] BASE = {
        0x000000, 0x7FB238, 0xF7E9A3, 0xC7C7C7, 0xFF0000, 0xA0A0FF, 0xA7A7A7, 0x007C00,
        0xFFFFFF, 0xA4A8B8, 0x976D4D, 0x707070, 0x4040FF, 0x8F7748, 0xFFFCF5, 0xD87F33,
        0xB24CD8, 0x6699D8, 0xE5E533, 0x7FCC19, 0xF27FA5, 0x4C4C4C, 0x999999, 0x4C7F99,
        0x7F3FB2, 0x334CB2, 0x664C33, 0x667F33, 0x993333, 0x191919, 0xFAEE4D, 0x5CDBD5,
        0x4A80FF, 0x00D93A, 0x815631, 0x700200, 0xD1B1A1, 0x9F5224, 0x95576C, 0x706C8A,
        0xBA8524, 0x677535, 0xA04D4E, 0x392923, 0x876B62, 0x575C5C, 0x7A4958, 0x4C3E5C,
        0x4C3223, 0x4C522A, 0x8E3C2E, 0x251610, 0xBD3031, 0x943F61, 0x5C191D, 0x167E86,
        0x3A8E8C, 0x562C3E, 0x14B485, 0x646464, 0xD8AF93, 0x7FA796,
    };
    private static final int[] SHADE = {180, 220, 255, 135};
    private static final int[] PACKED = new int[256];

    static {
        for (int id = 4; id < BASE.length * 4; id++) {
            int c = BASE[id >> 2];
            int m = SHADE[id & 3];
            int r = ((c >> 16) & 0xFF) * m / 255;
            int g = ((c >> 8) & 0xFF) * m / 255;
            int b = (c & 0xFF) * m / 255;
            PACKED[id] = 0xFF000000 | r << 16 | g << 8 | b;
        }
    }

    private MapColors() {
    }

    /** The packed id's colour, ARGB; 0 (transparent) for NONE and ids past the table. */
    public static int argb(int packed) {
        return PACKED[packed & 0xFF];
    }

    /** {@code MapColor.getPackedId}: base colour and brightness to the byte a map stores. */
    public static byte packed(int base, int brightness) {
        return (byte) (base << 2 | (brightness & 3));
    }

    /** How many base colours the table knows (62 in 26.3). */
    public static int baseCount() {
        return BASE.length;
    }
}
