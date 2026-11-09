package dev.dryaides.minuit;

/**
 * The nine hotbar slots in one row. Two looks: the inventory's, nine 18 px slot wells ({@link #HotbarComponent(Box)}),
 * and the game's HUD bar ({@link #hud}), 182 by 22 with cells 20 px apart. In display mode {@link #selected}
 * frames the held slot as the game's hotbar does.
 */
public class HotbarComponent extends SlotGridComponent {

    /** The held slot, 0 to 8, or -1 for none. */
    public int selected = -1;
    /** False inside an inventory, which never shows the held slot, as in the game. */
    public boolean framesSelection = true;
    private final boolean hud;

    /** @param grid {@link SlotLayout#hotbar}, or any {@link SlotLayout#grid} of one row of nine */
    public HotbarComponent(Box grid) {
        super(grid, 1, 9);
        this.hud = false;
    }

    private HotbarComponent(Box bar, boolean hud) {
        super(bar, 1, 9, SlotLayout.HUD_HOTBAR_PITCH, 1, false);
        this.hud = hud;
    }

    /** The game's HUD hotbar: the theme's bar sprite with bare items on it, cells 20 px apart. */
    public static HotbarComponent hud(String name, int x, int y) {
        return new HotbarComponent(SlotLayout.hudHotbar(name, x, y), true);
    }

    @Override
    protected void renderUnder(Draw d, Theme theme) {
        if (this.hud) {
            theme.frame().hotbar(d, theme.palette(), this.x, this.y);
        }
    }

    @Override
    protected void renderOver(Draw d, Theme theme) {
        if (!this.framesSelection || this.selected < 0 || this.selected >= 9) {
            return;
        }
        Box cell = cellBox(this.selected);
        // hotbarSelection takes the corner of an 18 px well; the HUD item sits 2 px into its 20 px cell, the well 1.
        int inset = this.hud ? 1 : 0;
        theme.frame().hotbarSelection(d, theme.palette(), cell.x() + inset, cell.y() + inset);
    }
}
