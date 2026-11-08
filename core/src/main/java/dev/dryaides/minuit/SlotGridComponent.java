package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * A grid of slot wells, laid out by {@link SlotLayout#grid}. Over menu slots it draws the wells and the
 * hover under the game's items; in display mode ({@link #items} or a callback) it draws the items too
 * and a click only calls back. The keyboard sees one stop: the arrows move a cell cursor, Enter or Space
 * clicks the cell under it.
 */
public class SlotGridComponent extends BaseComponent implements ItemSource, ItemDropTarget {

    public final int rows;
    public final int cols;
    private final ItemView[] items;
    private final long[] counts;
    private final boolean[] marks;
    private BiConsumer<Integer, Integer> onClick;
    private BiPredicate<Integer, ItemView> onDrop;
    private int cursor;
    private int tipCell = -1;

    /** @param grid from {@link SlotLayout#grid}, {@code cols * 18} by {@code rows * 18} */
    public SlotGridComponent(Box grid, int rows, int cols) {
        super(grid);
        if (grid.w() != cols * SlotLayout.PITCH || grid.h() != rows * SlotLayout.PITCH) {
            throw new IllegalArgumentException(grid + " is not " + rows + " by " + cols + " slots of 18");
        }
        this.rows = rows;
        this.cols = cols;
        this.items = new ItemView[rows * cols];
        this.counts = new long[rows * cols];
        this.marks = new boolean[rows * cols];
    }

    /** Display mode: the items in reading order, null for an empty cell; cells past the list empty. */
    public SlotGridComponent items(List<ItemView> shown) {
        for (int i = 0; i < this.items.length; i++) {
            ItemView view = i < shown.size() ? shown.get(i) : null;
            item(i, view, view == null ? 0 : view.count());
        }
        return this;
    }

    /** Display mode: one cell, with a count past what a stack holds. */
    public SlotGridComponent item(int index, ItemView shown, long count) {
        this.items[index] = shown;
        this.counts[index] = count;
        return this;
    }

    public ItemView item(int index) {
        return this.items[index];
    }

    /** Display mode: the cell index and the mouse button of a click. */
    public SlotGridComponent onClick(BiConsumer<Integer, Integer> indexAndButton) {
        this.onClick = indexAndButton;
        return this;
    }

    /** Display mode: takes an item handed in from outside the kit on a cell; the test says whether it took it. */
    public SlotGridComponent onDrop(BiPredicate<Integer, ItemView> indexAndItem) {
        this.onDrop = indexAndItem;
        return this;
    }

    public SlotGridComponent marked(int index, boolean on) {
        this.marks[index] = on;
        return this;
    }

    /** True over menu slots: nothing shown and nothing to call, so the container screen gives the click to the game. */
    public boolean holdsMenu() {
        if (this.onClick != null || this.onDrop != null) {
            return false;
        }
        for (ItemView view : this.items) {
            if (view != null) {
                return false;
            }
        }
        return true;
    }

    /** The cell under the mouse, or -1 outside. */
    public int cellAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return -1;
        }
        int c = (int) ((mx - this.x) / SlotLayout.PITCH);
        int r = (int) ((my - this.y) / SlotLayout.PITCH);
        return r * this.cols + c;
    }

    /** The cell's well, screen coordinates. */
    public Box cellBox(int index) {
        return new Box("slot" + index, this.x + index % this.cols * SlotLayout.PITCH,
                this.y + index / this.cols * SlotLayout.PITCH, SlotLayout.PITCH, SlotLayout.PITCH);
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        int over = hovered ? cellAt(mx, my) : -1;
        this.tipCell = -1;
        for (int i = 0; i < this.items.length; i++) {
            Box cell = cellBox(i);
            boolean game = SlotComponent.cell(d, theme, cell.x(), cell.y(), this.items[i], this.counts[i], null,
                    i == over, this.marks[i], mx, my);
            if (game) {
                this.tipCell = i;
            }
        }
        renderOver(d, theme);
    }

    /** After the cells: what a subclass draws over them. */
    protected void renderOver(Draw d, Theme theme) {
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        int cell = cellAt(mx, my);
        if (cell < 0) {
            return null;
        }
        List<String> name = SlotComponent.nameTip(this.items[cell], cell == this.tipCell);
        return name != null ? name : super.tooltipAt(mx, my);
    }

    @Override
    public ItemView itemAt(double mx, double my) {
        int cell = cellAt(mx, my);
        return cell < 0 ? null : this.items[cell];
    }

    @Override
    public List<Drop> dropTargets() {
        if (this.onDrop == null || !this.active) {
            return List.of();
        }
        List<Drop> out = new ArrayList<>(this.items.length);
        for (int i = 0; i < this.items.length; i++) {
            int index = i;
            out.add(new Drop(cellBox(i), item -> this.onDrop.test(index, item)));
        }
        return out;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cell = cellAt(mx, my);
        if (this.onClick == null || !this.active || cell < 0) {
            return false;
        }
        this.cursor = cell;
        click();
        this.onClick.accept(cell, button);
        return true;
    }

    @Override
    protected boolean navigable() {
        return this.onClick != null;
    }

    @Override
    protected void enter(boolean forward) {
        this.cursor = forward ? 0 : this.items.length - 1;
    }

    /** The arrows move the cursor inside the grid; at an edge the key goes on to the next stop. */
    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        int r = this.cursor / this.cols;
        int c = this.cursor % this.cols;
        switch (key) {
            case LEFT -> c--;
            case RIGHT -> c++;
            case UP -> r--;
            case DOWN -> r++;
            default -> {
                return false;
            }
        }
        if (r < 0 || r >= this.rows || c < 0 || c >= this.cols) {
            return false;
        }
        this.cursor = r * this.cols + c;
        return true;
    }

    @Override
    protected boolean activate() {
        if (this.onClick == null || !this.active) {
            return false;
        }
        click();
        this.onClick.accept(this.cursor, 0);
        return true;
    }

    /** The cell under the keyboard cursor. */
    public int cursor() {
        return this.cursor;
    }

    @Override
    protected Box focusBox() {
        return cellBox(this.cursor);
    }

    @Override
    protected int focusCorner() {
        return 0;
    }
}
