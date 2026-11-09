package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a container's slots sit, in pure Java so a menu on a dedicated server and the screen on the
 * client read one layout. Positions are container-local and follow vanilla: a cell's {@code x, y} is
 * the item's corner, as {@code Slot} wants it, and its 18 px well starts 1 px up and left of it.
 * A grid's {@link Box} covers the wells, so it is the box the slot components draw in.
 */
public final class SlotLayout {

    public static final int PITCH = 18;
    /** Between the player's three inventory rows and the hotbar. */
    public static final int HOTBAR_GAP = 4;
    /** From a label's top to the item corners of the row under it. */
    public static final int LABEL_GAP = 11;
    /** From the container's edge to the first well. */
    public static final int EDGE = 7;

    /** One slot: its index in the container and the item corner, container-local. */
    public record Cell(int index, int x, int y) { }

    /**
     * Where a slot part shows a menu slot: the box the slot's 16 px item must sit in at the menu's place, and
     * how far the part shows that item from there (0, 0 for a well drawn around the item). {@code ghost} is
     * true when the part draws the empty slot's outline itself, so the game leaves its own out.
     */
    @Incubating
    public record Well(Box box, int shiftX, int shiftY, boolean ghost) {
        public Well(Box box, int shiftX, int shiftY) {
            this(box, shiftX, shiftY, false);
        }
    }

    private SlotLayout() {
    }

    /** A grid whose first item corner is at {@code x, y}: {@code cols * 18} by {@code rows * 18}. */
    public static Box grid(String name, int x, int y, int rows, int cols) {
        return new Box(name, x - 1, y - 1, cols * PITCH, rows * PITCH);
    }

    /** The grid's cells in reading order, indices from {@code firstIndex}. */
    public static List<Cell> cells(Box grid, int firstIndex) {
        int cols = grid.w() / PITCH;
        int rows = grid.h() / PITCH;
        List<Cell> out = new ArrayList<>(rows * cols);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                out.add(new Cell(firstIndex + r * cols + c, grid.x() + 1 + c * PITCH, grid.y() + 1 + r * PITCH));
            }
        }
        return out;
    }

    /** The player's 3 by 9 inventory and the hotbar under it, first item corner at {@code x, y}: 162 by 76. */
    public static Box playerInventory(String name, int x, int y) {
        return new Box(name, x - 1, y - 1, 9 * PITCH, 4 * PITCH + HOTBAR_GAP);
    }

    /** The three rows over the hotbar, a {@link #grid} box. */
    public static Box inventoryRows(Box inventory) {
        return new Box(inventory.name() + "/rows", inventory.x(), inventory.y(), 9 * PITCH, 3 * PITCH);
    }

    /** The hotbar under the rows, a {@link #grid} box. */
    public static Box hotbar(Box inventory) {
        return new Box(inventory.name() + "/hotbar", inventory.x(), inventory.y() + 3 * PITCH + HOTBAR_GAP, 9 * PITCH, PITCH);
    }

    /** The game's HUD hotbar sprite, 182 by 22: nine cells 20 px apart, the first 1 px in from the box. */
    public static final int HUD_HOTBAR_W = 182;
    public static final int HUD_HOTBAR_H = 22;
    public static final int HUD_HOTBAR_PITCH = 20;

    /** The HUD hotbar's box with its top left corner at {@code x, y}; an item sits 3 px in from the corner. */
    public static Box hudHotbar(String name, int x, int y) {
        return new Box(name, x, y, HUD_HOTBAR_W, HUD_HOTBAR_H);
    }

    /**
     * The HUD hotbar's bar under {@code inventory}, as {@code PlayerInventoryComponent(box, true)} draws it:
     * centred on the box, its items level with the classic hotbar row's.
     */
    @Incubating
    public static Box hudHotbar(Box inventory) {
        Box row = hotbar(inventory);
        return hudHotbar(row.name(), inventory.x() + (inventory.w() - HUD_HOTBAR_W) / 2, row.y() + 1 - 3);
    }

    /**
     * Rows as {@link #playerCells}, the hotbar on the HUD bar's item corners: for a menu built for the HUD look,
     * whose hotbar slots then sit where {@code PlayerInventoryComponent(box, true)} draws the items.
     */
    @Incubating
    public static List<Cell> playerCellsHud(Box inventory) {
        List<Cell> out = new ArrayList<>(cells(inventoryRows(inventory), 9));
        Box bar = hudHotbar(inventory);
        for (int i = 0; i < 9; i++) {
            out.add(new Cell(i, bar.x() + 3 + i * HUD_HOTBAR_PITCH, bar.y() + 3));
        }
        return out;
    }

    /** The player's slots in vanilla's order: indices 9 to 35 for the rows, then 0 to 8 for the hotbar. */
    public static List<Cell> playerCells(Box inventory) {
        List<Cell> out = new ArrayList<>(cells(inventoryRows(inventory), 9));
        out.addAll(cells(hotbar(inventory), 0));
        return out;
    }

    /** The first item row of the player inventory under a grid of {@code rows} starting at {@code gridTop}. */
    public static int inventoryTop(int gridTop, int rows) {
        return gridTop + rows * PITCH + 13;
    }
}
