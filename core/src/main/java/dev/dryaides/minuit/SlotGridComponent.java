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
    private final boolean[] dragged;
    private BiConsumer<Integer, Integer> onClick;
    private BiConsumer<Integer, Integer> onPress;
    private BiPredicate<Integer, ItemView> onDrop;
    private boolean keys;
    private BiPredicate<Integer, Boolean> menuKey;
    private int cursor;
    private int tipCell = -1;
    private final int pitch;
    private final int inset;
    private final boolean wells;

    /** @param grid from {@link SlotLayout#grid}, {@code cols * 18} by {@code rows * 18} */
    public SlotGridComponent(Box grid, int rows, int cols) {
        this(grid, rows, cols, SlotLayout.PITCH, 0, true);
    }

    /**
     * A grid of cells {@code pitch} apart, the first {@code inset} px in from the box, drawn as slot wells or,
     * with {@code wells} false, as bare 16 px items centred in the cell (the subclass draws what lies under).
     */
    protected SlotGridComponent(Box grid, int rows, int cols, int pitch, int inset, boolean wells) {
        super(grid);
        if (grid.w() != 2 * inset + cols * pitch || grid.h() != 2 * inset + rows * pitch) {
            throw new IllegalArgumentException(grid + " is not " + rows + " by " + cols + " cells of " + pitch);
        }
        this.pitch = pitch;
        this.inset = inset;
        this.wells = wells;
        this.rows = rows;
        this.cols = cols;
        this.items = new ItemView[rows * cols];
        this.counts = new long[rows * cols];
        this.marks = new boolean[rows * cols];
        this.dragged = new boolean[rows * cols];
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

    /**
     * Display mode: a mouse press on a cell goes here instead of {@link #onClick}, for a host that plays the
     * whole gesture (press, drag, release); Enter on the cursor still clicks.
     */
    public SlotGridComponent onPress(BiConsumer<Integer, Integer> indexAndButton) {
        this.onPress = indexAndButton;
        return this;
    }

    /** Display mode: takes an item handed in from outside the kit on a cell; the test says whether it took it. */
    public SlotGridComponent onDrop(BiPredicate<Integer, ItemView> indexAndItem) {
        this.onDrop = indexAndItem;
        return this;
    }

    /**
     * Over menu slots: the grid becomes a keyboard and controller stop. The arrows move the cell cursor,
     * Enter or Space is a left click on the menu slot under it and Shift+Enter a quick move, sent by the
     * container screen as the mouse would. Off by default.
     */
    public SlotGridComponent keys(boolean on) {
        this.keys = on;
        return this;
    }

    /**
     * Set by the container screen: what Enter ({@code false}) or Shift+Enter ({@code true}) does on a cell
     * while the grid holds the menu; true when the screen sent a click.
     */
    @Incubating
    public void menuKey(BiPredicate<Integer, Boolean> cellAndQuick) {
        this.menuKey = cellAndQuick;
    }

    private boolean menuKeys() {
        return this.keys && this.menuKey != null && holdsMenu();
    }

    /** Lights a cell like the hover while a carried stack is dragged over it, as the game lights the drag. */
    public SlotGridComponent dragged(int index, boolean on) {
        this.dragged[index] = on;
        return this;
    }

    public SlotGridComponent marked(int index, boolean on) {
        this.marks[index] = on;
        return this;
    }

    /** True over menu slots: nothing shown and nothing to call, so the container screen gives the click to the game. */
    public boolean holdsMenu() {
        if (this.onClick != null || this.onPress != null || this.onDrop != null) {
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
        double lx = mx - this.x - this.inset;
        double ly = my - this.y - this.inset;
        if (lx < 0 || ly < 0 || lx >= this.cols * this.pitch || ly >= this.rows * this.pitch) {
            return -1;
        }
        return (int) (ly / this.pitch) * this.cols + (int) (lx / this.pitch);
    }

    /** Screen x of the cell's 16 px item: 1 px into an 18 px well, 2 px into a 20 px HUD cell. */
    public int itemX(int index) {
        return cellBox(index).x() + (this.wells ? 1 : (this.pitch - 16) / 2);
    }

    /** Screen y of the cell's 16 px item. */
    public int itemY(int index) {
        return cellBox(index).y() + (this.wells ? 1 : (this.pitch - 16) / 2);
    }

    /** The cell's well, screen coordinates. */
    public Box cellBox(int index) {
        return new Box("slot" + index, this.x + this.inset + index % this.cols * this.pitch,
                this.y + this.inset + index / this.cols * this.pitch, this.pitch, this.pitch);
    }

    /** One well per cell while it holds the menu: an 18 px well, or a 20 px cell on a standalone HUD bar. */
    @Override
    protected List<SlotLayout.Well> menuWells() {
        if (!holdsMenu()) {
            return List.of();
        }
        List<SlotLayout.Well> out = new ArrayList<>(this.rows * this.cols);
        for (int i = 0; i < this.rows * this.cols; i++) {
            out.add(new SlotLayout.Well(cellBox(i), 0, 0));
        }
        return out;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        int over = hovered ? cellAt(mx, my) : -1;
        this.tipCell = -1;
        renderUnder(d, theme);
        for (int i = 0; i < this.items.length; i++) {
            Box cell = cellBox(i);
            boolean game = this.wells
                    ? SlotComponent.cell(d, theme, cell.x(), cell.y(), this.items[i], this.counts[i], null,
                            i == over || this.dragged[i], i == over, this.marks[i], mx, my)
                    : SlotComponent.items(d, theme, cell.x(), cell.y(), (this.pitch - 16) / 2, this.items[i],
                            this.counts[i], null, i == over || this.dragged[i], i == over, mx, my);
            if (game) {
                this.tipCell = i;
            }
        }
        renderOver(d, theme);
    }

    /** Before the cells: what a subclass draws under them. */
    protected void renderUnder(Draw d, Theme theme) {
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
        if (this.onClick == null && this.onPress == null || !this.active || cell < 0) {
            return false;
        }
        this.cursor = cell;
        (this.onPress != null ? this.onPress : this.onClick).accept(cell, button);
        return true;
    }

    @Override
    protected boolean navigable() {
        return this.onClick != null || menuKeys();
    }

    @Override
    protected void enter(boolean forward) {
        this.cursor = forward ? 0 : this.items.length - 1;
    }

    /**
     * Down or Up enters on the top or bottom row, in the column under {@code from}'s centre; Right or Left
     * enters on the first or last column, in the row beside it. A chest grid's bottom row then goes down
     * to the inventory cell below it, as a pad player expects.
     */
    @Override
    protected void enterFrom(InputKey key, Box from) {
        int cx = from.x() + from.w() / 2;
        int cy = from.y() + from.h() / 2;
        int r;
        int c;
        if (key == InputKey.DOWN || key == InputKey.UP) {
            r = key == InputKey.DOWN ? 0 : this.rows - 1;
            c = nearest(this.cols, i -> cellBox(i).x() + cellBox(i).w() / 2, cx);
        } else {
            c = key == InputKey.RIGHT ? 0 : this.cols - 1;
            r = nearest(this.rows, i -> cellBox(i * this.cols).y() + cellBox(i * this.cols).h() / 2, cy);
        }
        this.cursor = Math.min(r * this.cols + c, this.items.length - 1);
    }

    private static int nearest(int count, java.util.function.IntUnaryOperator pos, int target) {
        int best = 0;
        for (int i = 1; i < count; i++) {
            if (Math.abs(pos.applyAsInt(i) - target) < Math.abs(pos.applyAsInt(best) - target)) {
                best = i;
            }
        }
        return best;
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
            case ENTER, SPACE -> {
                if (!shift || !menuKeys() || !this.active) {
                    return false;
                }
                // Shift+Enter where the menu has no quick move must not fall through to activate()'s PICKUP.
                this.menuKey.test(this.cursor, true);
                return true;
            }
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
        if (menuKeys() && this.active) {
            return this.menuKey.test(this.cursor, false);
        }
        if (this.onClick == null || !this.active) {
            return false;
        }
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
