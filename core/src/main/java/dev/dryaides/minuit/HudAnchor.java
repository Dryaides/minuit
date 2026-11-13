package dev.dryaides.minuit;

/**
 * Where a {@link HudPart} starts on the screen, and which way it moves when the vanilla HUD or another part is
 * already there: parts at the top and at the centre move down, parts at the bottom and at the middle of a side
 * move up. A side part moving inward would land in the middle of the screen when the chat or the scoreboard
 * fills that side, as the chat does at GUI scale 4.
 */
@Incubating
public enum HudAnchor {
    TOP_LEFT(0, 0), TOP_CENTER(1, 0), TOP_RIGHT(2, 0),
    MIDDLE_LEFT(0, 1), CENTER(1, 1), MIDDLE_RIGHT(2, 1),
    BOTTOM_LEFT(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_RIGHT(2, 2);

    /** Space kept between a part and the screen edge, in GUI pixels. */
    public static final int MARGIN = 2;

    private final int column;
    private final int row;

    HudAnchor(int column, int row) {
        this.column = column;
        this.row = row;
    }

    /**
     * The left edge of a part {@code w} wide on a screen {@code screenW} wide. A centred part uses
     * {@code (screenW - w) / 2}, which is what the game's {@code screenW / 2 - 91} for the hotbar comes to, so a
     * part centred over the hotbar lines up with it at every width.
     */
    public int originX(int screenW, int w) {
        return switch (this.column) {
            case 0 -> MARGIN;
            case 1 -> (screenW - w) / 2;
            default -> screenW - MARGIN - w;
        };
    }

    public int originY(int screenH, int h) {
        return switch (this.row) {
            case 0 -> MARGIN;
            case 1 -> (screenH - h) / 2;
            default -> screenH - MARGIN - h;
        };
    }

    /** -1 or 1: the way a blocked part moves down the screen. */
    public int pushY() {
        return this.row == 0 || this.column == 1 && this.row == 1 ? 1 : -1;
    }
}
