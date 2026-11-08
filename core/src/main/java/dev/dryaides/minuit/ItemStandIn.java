package dev.dryaides.minuit;

/**
 * What a renderer without the item's texture draws in its place: a 12 by 12 token centred in the
 * 16 pixel slot, one of four original shapes picked by the id, lit from the top left. Never a
 * Mojang texture, and never a blank slot.
 */
final class ItemStandIn {

    private static final int[] RAMP = {
            0xFF4FB3C9, 0xFFC9A04F, 0xFF8C5FC4, 0xFF5FAF5A, 0xFFC4584F, 0xFF9AA3AD, 0xFFD08A3C, 0xFF4F6FC9};

    /** Gem, ingot, block, orb; '#' is the token, the rim is drawn around it. */
    private static final String[][] SHAPES = {
            {
                    "....####....",
                    "...######...",
                    "..########..",
                    ".##########.",
                    "############",
                    "############",
                    ".##########.",
                    "..########..",
                    "...######...",
                    "....####....",
                    ".....##.....",
                    "............"},
            {
                    "............",
                    "............",
                    "............",
                    "...######...",
                    "..########..",
                    ".##########.",
                    "############",
                    "############",
                    "############",
                    "............",
                    "............",
                    "............"},
            {
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############",
                    "############"},
            {
                    "....####....",
                    "..########..",
                    ".##########.",
                    ".##########.",
                    "############",
                    "############",
                    "############",
                    "############",
                    ".##########.",
                    ".##########.",
                    "..########..",
                    "....####...."}};

    private ItemStandIn() {
    }

    /** The token for item {@code id} in the 16 by 16 slot at {@code x, y}. */
    public static void draw(Draw d, String id, int x, int y) {
        int hash = id == null ? 0 : id.hashCode();
        String[] shape = SHAPES[Math.floorMod(hash, SHAPES.length)];
        int face = RAMP[Math.floorMod(hash >>> 2, RAMP.length)];
        int light = Shapes.bevelLight(face);
        int dark = Shapes.bevelDark(face);
        int rim = Shapes.mix(dark, 0xFF000000, 0.5F);
        int x0 = x + 2;
        int y0 = y + 2;
        for (int row = 0; row < 12; row++) {
            for (int col = 0; col < 12; col++) {
                if (!on(shape, row, col)) {
                    continue;
                }
                int color;
                if (!on(shape, row - 1, col) || !on(shape, row, col - 1)) {
                    color = light;
                } else if (!on(shape, row + 1, col) || !on(shape, row, col + 1)) {
                    color = rim;
                } else if (!on(shape, row + 2, col) || !on(shape, row, col + 2)) {
                    color = dark;
                } else {
                    color = face;
                }
                d.fill(x0 + col, y0 + row, x0 + col + 1, y0 + row + 1, color);
            }
        }
    }

    private static boolean on(String[] shape, int row, int col) {
        return row >= 0 && row < 12 && col >= 0 && col < 12 && shape[row].charAt(col) == '#';
    }
}
