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
