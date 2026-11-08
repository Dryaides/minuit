package dev.dryaides.minuit;

/** The nine hotbar slots in one row; in display mode {@link #selected} frames the held slot as the game's hotbar does. */
public class HotbarComponent extends SlotGridComponent {

    /** The held slot, 0 to 8, or -1 for none. */
    public int selected = -1;
    /** False inside an inventory, which never shows the held slot, as in the game. */
    public boolean framesSelection = true;

    /** @param grid {@link SlotLayout#hotbar}, or any {@link SlotLayout#grid} of one row of nine */
    public HotbarComponent(Box grid) {
        super(grid, 1, 9);
    }

    @Override
    protected void renderOver(Draw d, Theme theme) {
        if (!this.framesSelection || this.selected < 0 || this.selected >= 9) {
            return;
        }
        Box cell = cellBox(this.selected);
        theme.frame().hotbarSelection(d, theme.palette(), cell.x(), cell.y());
    }
}
