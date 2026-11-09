package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A sortable data table: a sticky header above a scrolling body that draws only the rows in view, so
 * thousands of rows stay cheap.
 *
 * <pre>{@code
 * TableComponent<Pet> pets = add(new TableComponent<Pet>(at(new Box("pets", 8, 8, 280, 120)), 14)
 *         .column("Name", c -> c.text(Pet::name).weight(2)
 *                 .comparator(Comparator.comparing(Pet::name, String.CASE_INSENSITIVE_ORDER)))
 *         .column("Age", c -> c.text(p -> p.age() + " d").fixed(44).align(TableComponent.Align.RIGHT)
 *                 .comparator(Comparator.comparingInt(Pet::age)))
 *         .column("Mood", c -> c.renderer((d, theme, pet, cell, hovered, selected) ->
 *                 d.fill(cell.x() + 3, cell.y() + 3, cell.right() - 3, cell.bottom() - 3, theme.palette().good())))
 *         .emptyText(words.get("mymod.pets.none"))
 *         .onSelect(this::show)
 *         .onActivate(this::open));
 * pets.setItems(allPets);
 * }</pre>
 *
 * <p>Click a sortable header to cycle ascending, descending, none; sorting is stable. Click a row to
 * select it, double click or press Enter to activate it. While the table has focus (click it or Tab to
 * it) the up, down, Home, End, Page Up and Page Down keys move the selection.
 */
public class TableComponent<T> extends BaseComponent implements ItemSource {
    /** The width kept for the scroll bar, when the rows overflow. */
    static final int BAR = ScrollBar.WIDTH;
    /** The row height that holds a 16 px item whole. */
    public static final int ITEM_ROW = ItemArt.ITEM_ROW;

    /** Where a cell's text sits. */
    public enum Align { LEFT, CENTER, RIGHT }

    /** How a column sorts the rows. */
    public enum SortDirection { NONE, ASCENDING, DESCENDING }

    /** Draws one cell. The cell is clipped to its box; {@code selected} rows lie on the selection's colour. */
    @FunctionalInterface
    public interface CellRenderer<T> {
        void draw(Draw draw, Theme theme, T item, Box cell, boolean hovered, boolean selected);
    }

    /** One column, set up fluently inside {@link TableComponent#column(String, Consumer)}. */
    public static final class Column<T> {
        private final String title;
        private int fixed;
        private float weight = 1F;
        private Align align = Align.LEFT;
        private Function<? super T, String> text;
        private CellRenderer<? super T> renderer;
        private Comparator<? super T> comparator;
        private Function<? super T, IconArt> icon;

        private Column(String title) {
            this.title = title == null ? "" : title;
        }

        /** A fixed width in pixels. */
        public Column<T> fixed(int width) {
            fixed = Math.max(0, width);
            return this;
        }

        /** A share of the width the fixed columns leave. Replaces any fixed width. The default is 1. */
        public Column<T> weight(float share) {
            fixed = 0;
            weight = Math.max(0F, share);
            return this;
        }

        /** Where the text and the title sit. Applies to text cells; a renderer places its own content. */
        public Column<T> align(Align to) {
            align = to == null ? Align.LEFT : to;
            return this;
        }

        /** The cell's text, cut with an ellipsis when too long. A null counts as empty. */
        public Column<T> text(Function<? super T, String> getter) {
            text = getter;
            return this;
        }

        /**
         * A picture before the cell's text; null for a row shows none. An {@link ItemView} gets its count, the
         * game's tooltip and the recipe keys; a 16 px item wants rows of 18 (shrunk crisply in lower rows).
         */
        public Column<T> iconArt(Function<? super T, IconArt> art) {
            icon = art;
            return this;
        }

        /** Draws the cell by hand, in place of the text. */
        public Column<T> renderer(CellRenderer<? super T> cell) {
            renderer = cell;
            return this;
        }

        /** Makes the column sortable. Descending is this order reversed; ties keep the items' order. */
        public Column<T> comparator(Comparator<? super T> order) {
            comparator = order;
            return this;
        }

        public String title() { return title; }
        public boolean sortable() { return comparator != null; }
    }

    private static final int CELL_PAD = 3;
    private static final int ICON_GAP = 2;
    private static final int MARK_W = 5;
    private static final int MARK_ROOM = MARK_W + 2;
    private static final long DOUBLE_CLICK_MS = 300;
    private static final float SCROLL_EASE_MS = 45F;
    // Page Up and Page Down have no InputKey: while focused, the table reads the raw key codes (GLFW's).
    private static final int RAW_PAGE_UP = 266;
    private static final int RAW_PAGE_DOWN = 267;

    private final int rowHeight;
    private final List<Column<T>> columns = new ArrayList<>();
    private int headerHeight;
    private String emptyText;
    private Consumer<T> onSelect = item -> { };
    private Consumer<T> onActivate = item -> { };
    private BiConsumer<Integer, SortDirection> onSort = (column, direction) -> { };

    private List<T> items = new ArrayList<>();
    private List<T> view = items;
    private int sortColumn = -1;
    private SortDirection sortDirection = SortDirection.NONE;
    private T selected;
    private int selectedIndex = -1;

    private final ScrollBar bar = new ScrollBar();
    private FrameStyle frame = FrameStyles.VANILLA;
    private double shownScroll;
    private long lastFrame = -1L;
    private Box[] cellBoxes = new Box[0];
    private final RowLook.HoverFade hover = new RowLook.HoverFade();
    private boolean itemTooltips = true;
    private boolean itemTipShown;
    private int lastClickRow = -1;
    private long lastClickAt;

    /** @param rowHeight the height of a row in pixels; the header starts at the same height, at least 14 */
    public TableComponent(Box box, int rowHeight) {
        super(box);
        this.rowHeight = Math.max(1, rowHeight);
        this.headerHeight = Math.max(14, this.rowHeight);
    }

    /** Adds a column, left to right. */
    public TableComponent<T> column(String title, Consumer<Column<T>> setup) {
        Column<T> column = new Column<>(title);
        setup.accept(column);
        columns.add(column);
        return this;
    }

    /** Adds a plain column of text with an equal share of the width. */
    public TableComponent<T> textColumn(String title, Function<? super T, String> getter) {
        return column(title, c -> c.text(getter));
    }

    /**
     * A column of items: the item, then its name in the player's language. Each cell shows the game's tooltip
     * and answers the recipe keys. Build the table with rows of {@link #ITEM_ROW} for full-size items.
     */
    public TableComponent<T> itemColumn(String title, Function<? super T, ItemView> item) {
        Objects.requireNonNull(item, "item");
        return column(title, c -> c.iconArt(row -> item.apply(row)).text(row -> ItemArt.name(item.apply(row))));
    }

    /** Whether an item cell shows the game's own tooltip (its name without a world). On by default; {@link #tooltip} wins. */
    public TableComponent<T> itemTooltips(boolean on) {
        itemTooltips = on;
        return this;
    }

    public TableComponent<T> headerHeight(int height) {
        headerHeight = Math.max(0, height);
        return this;
    }

    /** What the body shows while there are no rows. */
    public TableComponent<T> emptyText(String text) {
        emptyText = text;
        return this;
    }

    /** Called when the player changes the selection. */
    public TableComponent<T> onSelect(Consumer<T> callback) {
        onSelect = callback == null ? item -> { } : callback;
        return this;
    }

    /** Called on a double click, and on Enter or Space with a row selected. */
    public TableComponent<T> onActivate(Consumer<T> callback) {
        onActivate = callback == null ? item -> { } : callback;
        return this;
    }

    /** Called when the player changes the sort, with the column's index (-1 for no sort) and the new direction. */
    public TableComponent<T> onSort(BiConsumer<Integer, SortDirection> callback) {
        onSort = callback == null ? (column, direction) -> { } : callback;
        return this;
    }

    public TableComponent<T> active(boolean on) {
        active = on;
        return this;
    }

    /**
     * Replaces the rows; the list is copied. The current sort applies, and the selection stays if its item
     * is still there. Otherwise it clears, without calling {@link #onSelect}.
     */
    public void setItems(List<T> rows) {
        items = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        rebuildView();
        clampScroll();
    }

    /** Sorts again, after the items' values changed. */
    public void resort() {
        rebuildView();
    }

    /** The rows in the order given. */
    public List<T> items() {
        return Collections.unmodifiableList(items);
    }

    /** The rows in the order shown. */
    public List<T> view() {
        return Collections.unmodifiableList(view);
    }

    public int rowHeight() { return rowHeight; }

    public List<Column<T>> columns() {
        return Collections.unmodifiableList(columns);
    }

    /**
     * Sorts by a column, without calling {@link #onSort}.
     *
     * @param column the column's index; ignored for {@link SortDirection#NONE}
     * @throws IllegalArgumentException if the column has no comparator
     */
    public void sortBy(int column, SortDirection direction) {
        if (direction == null || direction == SortDirection.NONE) {
            sortColumn = -1;
            sortDirection = SortDirection.NONE;
        } else {
            if (column < 0 || column >= columns.size() || !columns.get(column).sortable()) {
                throw new IllegalArgumentException("column " + column + " cannot sort");
            }
            sortColumn = column;
            sortDirection = direction;
        }
        rebuildView();
    }

    /** The column the rows sort by, or -1. */
    public int sortColumn() { return sortColumn; }

    public SortDirection sortDirection() { return sortDirection; }

    private void rebuildView() {
        lastClickRow = -1;
        if (sortColumn < 0 || sortColumn >= columns.size() || sortDirection == SortDirection.NONE) {
            view = items;
        } else {
            Comparator<? super T> order = columns.get(sortColumn).comparator;
            List<T> sorted = new ArrayList<>(items);
            // List.sort is stable, and a reversed comparator still keeps ties in the items' order.
            sorted.sort(sortDirection == SortDirection.DESCENDING ? Collections.reverseOrder(order) : order);
            view = sorted;
        }
        relocateSelection();
    }

    private void relocateSelection() {
        if (selectedIndex < 0) {
            return;
        }
        for (int i = 0; i < view.size(); i++) {
            if (view.get(i) == selected) {
                selectedIndex = i;
                return;
            }
        }
        for (int i = 0; i < view.size(); i++) {
            if (Objects.equals(view.get(i), selected)) {
                selectedIndex = i;
                selected = view.get(i);
                return;
            }
        }
        selectedIndex = -1;
        selected = null;
    }

    private void cycleSort(int column) {
        if (!columns.get(column).sortable()) {
            return;
        }
        SortDirection next = SortDirection.ASCENDING;
        if (column == sortColumn) {
            next = sortDirection == SortDirection.ASCENDING ? SortDirection.DESCENDING : SortDirection.NONE;
        }
        sortBy(column, next);
        BaseComponent.click();
        onSort.accept(sortColumn, sortDirection);
    }

    /** The selected item, or null. */
    public T selected() { return selected; }

    /** The selected row's place in {@link #view()}, or -1. */
    public int selectedIndex() { return selectedIndex; }

    /** Selects an item if it is in the table, or nothing for null, without calling {@link #onSelect}. */
    public void select(T item) {
        selected = item;
        selectedIndex = item == null ? -1 : 0;
        relocateSelection();
    }

    /** Selects {@code item} as the arrow keys do: brought into view, {@link #onSelect} called. */
    void selectByPlayer(T item) {
        int index = view.indexOf(item);
        if (index >= 0) {
            selectIndex(index);
        }
    }

    private void selectIndex(int index) {
        if (view.isEmpty()) {
            return;
        }
        int to = Math.max(0, Math.min(view.size() - 1, index));
        revealIndex(to);
        if (to != selectedIndex) {
            selectedIndex = to;
            selected = view.get(to);
            onSelect.accept(selected);
        }
    }

    /** Moves the selection up by one page of rows. */
    public void pageUp() {
        selectIndex((selectedIndex < 0 ? firstVisibleRow() : selectedIndex) - pageRows());
    }

    /** Moves the selection down by one page of rows. */
    public void pageDown() {
        selectIndex((selectedIndex < 0 ? firstVisibleRow() : selectedIndex) + pageRows());
    }

    private int pageRows() {
        return Math.max(1, rowsHeight() / rowHeight - 1);
    }

    private int firstVisibleRow() {
        return bar.offset() / rowHeight;
    }

    private void fireActivate() {
        BaseComponent.click();
        onActivate.accept(selected);
    }

    /** The scroll position in pixels, where it is heading. */
    double scroll() { return bar.offset(); }

    /** Jumps to a scroll position, with no animation. */
    public void setScroll(double pixels) {
        clampScroll();
        bar.offset((int) Math.round(pixels));
        shownScroll = bar.offset();
    }

    /** Scrolls so that an item's row is in view. */
    public void reveal(T item) {
        for (int i = 0; i < view.size(); i++) {
            if (view.get(i) == item || Objects.equals(view.get(i), item)) {
                revealIndex(i);
                return;
            }
        }
    }

    private void revealIndex(int index) {
        int top = index * rowHeight;
        clampScroll();
        if (top < bar.offset()) {
            bar.offset(top);
        } else if (top + rowHeight > bar.offset() + rowsHeight()) {
            bar.offset(top + rowHeight - rowsHeight());
        }
    }

    private void clampScroll() {
        bar.content(view.size() * rowHeight, rowsHeight());
        shownScroll = Math.max(0D, Math.min(bar.max(), shownScroll));
    }

    private boolean overflows() {
        return view.size() * rowHeight > rowsHeight();
    }

    private void easeScroll() {
        long now = BaseComponent.clock.getAsLong();
        long elapsed = lastFrame < 0L ? 0L : Math.max(0L, now - lastFrame);
        lastFrame = now;
        int target = bar.offset();
        if (BaseComponent.reducedMotion || bar.dragging()) {
            shownScroll = target;
            return;
        }
        shownScroll += (target - shownScroll) * (1D - Math.exp(-elapsed / (double) SCROLL_EASE_MS));
        if (Math.abs(target - shownScroll) < 0.5D) {
            shownScroll = target;
        }
    }

    private int bodyX() { return x + 1; }
    private int bodyWidth() { return Math.max(0, w - 2); }
    private int headerTop() { return y + 1; }
    private int shownHeaderHeight() { return Math.min(headerHeight, Math.max(0, h - 2)); }
    private int rowsTop() { return headerTop() + shownHeaderHeight(); }
    private int rowsHeight() { return Math.max(0, h - 2 - shownHeaderHeight()); }
    private int contentWidth() { return Math.max(0, bodyWidth() - (overflows() ? ScrollBar.wellRoom(frame) : 0)); }
    private int barX() { return ScrollBar.wellX(frame, x, w); }

    /** The left edges of the columns relative to the body, and the end of the last one. */
    private int[] columnEdges() {
        int width = contentWidth();
        int fixedTotal = 0;
        float weightTotal = 0F;
        for (Column<T> column : columns) {
            fixedTotal += column.fixed;
            weightTotal += column.fixed > 0 ? 0F : column.weight;
        }
        int free = Math.max(0, width - fixedTotal);
        int[] edges = new int[columns.size() + 1];
        int at = 0;
        float weightSoFar = 0F;
        int freeUsed = 0;
        for (int i = 0; i < columns.size(); i++) {
            Column<T> column = columns.get(i);
            edges[i] = Math.min(at, width);
            if (column.fixed > 0) {
                at += column.fixed;
            } else if (weightTotal > 0F) {
                weightSoFar += column.weight;
                int freeNow = Math.round(free * (weightSoFar / weightTotal));
                at += freeNow - freeUsed;
                freeUsed = freeNow;
            }
        }
        edges[columns.size()] = Math.min(at, width);
        return edges;
    }

    private static int alignedLeft(Align align, int left, int width, int textWidth) {
        switch (align) {
            case RIGHT: return left + width - textWidth;
            case CENTER: return left + (width - textWidth) / 2;
            default: return left;
        }
    }

    private int columnAt(double mouseX) {
        int[] edges = columnEdges();
        double inside = mouseX - bodyX();
        for (int i = 0; i < columns.size(); i++) {
            if (inside >= edges[i] && inside < edges[i + 1]) {
                return i;
            }
        }
        return -1;
    }

    private boolean inHeader(double mouseX, double mouseY) {
        return isOver(mouseX, mouseY) && mouseY >= headerTop() && mouseY < rowsTop();
    }

    private boolean inBar(double mouseX, double mouseY) {
        return overflows() && isOver(mouseX, mouseY) && mouseX >= barX() && mouseY >= rowsTop();
    }

    /** The row's place in {@link #view()} under a point, or -1. */
    int indexAt(double mouseX, double mouseY) {
        if (!isOver(mouseX, mouseY) || mouseY < rowsTop() || mouseY >= rowsTop() + rowsHeight()
                || mouseX < bodyX() || mouseX >= bodyX() + contentWidth()) {
            return -1;
        }
        int index = (int) Math.floor((mouseY - rowsTop() + Math.round(shownScroll)) / rowHeight);
        return index >= 0 && index < view.size() ? index : -1;
    }

    /** The item under a point, or null. */
    public T rowAt(double mouseX, double mouseY) {
        int index = indexAt(mouseX, mouseY);
        return index < 0 ? null : view.get(index);
    }

    @Override
    protected void render(Draw d, Theme theme, int mouseX, int mouseY, boolean hovered) {
        clampScroll();
        easeScroll();
        Palette p = theme.palette();
        frame = theme.frame();
        frame.well(d, p, x, y, w, h, focused && active);
        int[] edges = columnEdges();
        drawRows(d, theme, edges, mouseX, mouseY, hovered);
        drawHeader(d, theme, edges, mouseX, mouseY, hovered);
        itemTipShown = active && hovered && !bar.dragging() && showItemTip(d, mouseX, mouseY);
        if (overflows() && rowsHeight() > 0 && RowLook.barFits(w, h, BAR)) {
            bar.draw(d, theme, barX(), trackY(), trackH(), active && hovered && inBar(mouseX, mouseY));
            if (!active) {
                RowLook.dimBar(d, p, barX(), trackY(), BAR, trackH());
            }
        }
    }

    // 1 px clear of the header's line and the well's border, as in the Tree, the ReorderList and the TextArea.
    private int trackY() {
        return rowsTop() + (rowsHeight() > 2 ? 1 : 0);
    }

    private int trackH() {
        return rowsHeight() > 2 ? rowsHeight() - 2 : rowsHeight();
    }

    private void drawRows(Draw d, Theme theme, int[] edges, int mouseX, int mouseY, boolean hovered) {
        int top = rowsTop();
        int height = rowsHeight();
        if (height <= 0) {
            return;
        }
        Palette p = theme.palette();
        if (view.isEmpty()) {
            hover.step(-1);
            RowLook.drawEmpty(d, p, emptyText, active, bodyX(), top, bodyWidth(), height);
            return;
        }
        hover.step(active && hovered && !bar.dragging() ? indexAt(mouseX, mouseY) : -1);
        int left = bodyX();
        int width = contentWidth();
        int offset = (int) Math.round(shownScroll);
        int first = Math.max(0, offset / rowHeight);
        int last = Math.min(view.size() - 1, (offset + height - 1) / rowHeight);
        d.scissor(left, top, left + bodyWidth(), top + height);
        for (int row = first; row <= last; row++) {
            int rowY = top + row * rowHeight - offset;
            boolean isSelected = row == selectedIndex;
            boolean isHovered = row == hover.row();
            int face = rowFace(p, isSelected, isHovered);
            theme.frame().row(d, p, left, rowY, width, rowHeight, face >>> 24 != 0 ? face : 0);
            if (!RowLook.textCut(rowY, rowHeight, top, height)) {
                int ink = RowLook.ink(p, face, isSelected, active);
                drawCells(d, theme, edges, view.get(row), row - first, rowY, isHovered, isSelected, ink);
            }
        }
        d.endScissor();
    }

    private int rowFace(Palette p, boolean isSelected, boolean isHovered) {
        return RowLook.face(p, active, focused, isSelected, isHovered, hover.fade());
    }

    /**
     * The colour the table's own text has in a row, for a {@link CellRenderer} that draws over the row's
     * face: read it inside the renderer, since it follows the focus and {@code active}.
     */
    public int cellInk(Theme theme, boolean isSelected, boolean isHovered) {
        Palette p = theme.palette();
        return RowLook.ink(p, rowFace(p, isSelected, isHovered), isSelected, active);
    }

    private void drawCells(Draw d, Theme theme, int[] edges, T item, int slot, int rowY, boolean isHovered, boolean isSelected, int ink) {
        int left = bodyX();
        for (int i = 0; i < columns.size(); i++) {
            int cellLeft = left + edges[i];
            int cellWidth = edges[i + 1] - edges[i];
            if (cellWidth <= 0) {
                continue;
            }
            Column<T> column = columns.get(i);
            if (column.renderer != null) {
                Box cell = cellBox(slot * columns.size() + i, cellLeft, rowY, cellWidth);
                d.scissor(cellLeft, rowY, cellLeft + cellWidth, rowY + rowHeight);
                column.renderer.draw(d, theme, item, cell, isHovered, isSelected);
                d.endScissor();
                continue;
            }
            String text = column.text == null ? null : column.text.apply(item);
            boolean hasText = text != null && !text.isEmpty();
            int textLeft = cellLeft + CELL_PAD;
            int room = cellWidth - 2 * CELL_PAD;
            IconArt art = column.icon == null ? null : column.icon.apply(item);
            if (art != null && room > 0) {
                int artW = artWidth(art);
                int artX = hasText ? textLeft : alignedLeft(column.align, textLeft, room, artW);
                d.scissor(cellLeft, rowY, cellLeft + cellWidth, rowY + rowHeight);
                drawArt(d, theme.palette(), art, artX, rowY, ink);
                d.endScissor();
                textLeft += artW + ICON_GAP;
                room -= artW + ICON_GAP;
            }
            if (hasText) {
                String fitted = room > 0 ? d.ellipsize(text, room) : "";
                int textX = alignedLeft(column.align, textLeft, room, d.width(fitted));
                d.text(fitted, textX, RowLook.textY(rowY, rowHeight), ink);
            }
        }
    }

    /** The box handed to a renderer, kept per visible cell so a still table allocates none. */
    private Box cellBox(int index, int cellLeft, int rowY, int cellWidth) {
        if (index >= cellBoxes.length) {
            cellBoxes = java.util.Arrays.copyOf(cellBoxes, index + 1 + columns.size());
        }
        Box box = cellBoxes[index];
        if (box == null || box.x() != cellLeft || box.y() != rowY || box.w() != cellWidth || box.h() != rowHeight) {
            box = new Box("cell", cellLeft, rowY, cellWidth, rowHeight);
            cellBoxes[index] = box;
        }
        return box;
    }

    /** Draws the art at the row's middle; an item fitted crisply, with its decorations at full size. Returns its width. */
    int drawArt(Draw d, Palette p, IconArt art, int ax, int rowY, int color) {
        if (!ItemArt.isItem(art)) {
            art.draw(d, ax, rowY + (rowHeight - art.height()) / 2, color, 1F);
            return art.width();
        }
        int room = artWidth(art);
        int top = rowY + (rowHeight - room) / 2;
        ItemArt.fit(d, p, art, ax, top, room, color, active);
        if (room >= art.height()) {
            ItemArt.decorations(d, (ItemView) art, ax, top);
        }
        return room;
    }

    int artWidth(IconArt art) {
        return ItemArt.isItem(art) ? Math.min(art.height(), rowHeight) : art.width();
    }

    /** The picture of the cell under a point, or null. */
    IconArt artAt(double mouseX, double mouseY) {
        int row = indexAt(mouseX, mouseY);
        int column = row < 0 ? -1 : columnAt(mouseX);
        if (column < 0) {
            return null;
        }
        Column<T> col = columns.get(column);
        return col.icon == null ? null : col.icon.apply(view.get(row));
    }

    /** The game's tooltip for the item cell under the pointer, when no tooltip of yours is set. */
    private boolean showItemTip(Draw d, int mouseX, int mouseY) {
        if (!itemTooltips || (tooltip != null && !tooltip.isEmpty())) {
            return false;
        }
        IconArt art = artAt(mouseX, mouseY);
        return art != null && ItemArt.hoverTooltip(d, art, mouseX, mouseY);
    }

    /** The item of the cell under the pointer, for the recipe keys of JEI, REI or EMI. */
    @Override
    public ItemView itemAt(double mouseX, double mouseY) {
        return visible && artAt(mouseX, mouseY) instanceof ItemView item ? item : null;
    }

    private void drawHeader(Draw d, Theme theme, int[] edges, int mouseX, int mouseY, boolean hovered) {
        int top = headerTop();
        int height = shownHeaderHeight();
        if (height <= 0) {
            return;
        }
        Palette p = theme.palette();
        int left = bodyX();
        d.fill(left, top, left + bodyWidth(), top + height, p.section());
        d.fill(left, top + height - 1, left + bodyWidth(), top + height, p.wellEdge());
        int hoverColumn = active && hovered && !bar.dragging() && inHeader(mouseX, mouseY) ? columnAt(mouseX) : -1;
        int textY = top + (height - 1 - RowLook.FONT_H) / 2 + 1;
        for (int i = 0; i < columns.size(); i++) {
            int cellLeft = left + edges[i];
            int cellWidth = edges[i + 1] - edges[i];
            if (cellWidth <= 0) {
                continue;
            }
            Column<T> column = columns.get(i);
            boolean lit = i == hoverColumn && column.sortable();
            if (lit) {
                d.fill(cellLeft, top, cellLeft + cellWidth, top + height - 1, RowLook.tint(p, 1F));
            }
            int ink = active ? p.text() : p.disabledText();
            int room = cellWidth - 2 * CELL_PAD - (column.sortable() ? MARK_ROOM : 0);
            String title = room > 0 ? d.ellipsize(column.title, room) : "";
            int titleWidth = d.width(title);
            boolean markOnLeft = column.align == Align.RIGHT;
            int titleX = alignedLeft(column.align, cellLeft + CELL_PAD + (markOnLeft && column.sortable() ? MARK_ROOM : 0), room, titleWidth);
            d.text(title, titleX, textY, ink);
            if (column.sortable() && i == sortColumn && sortDirection != SortDirection.NONE) {
                // width() counts the space after the last letter, so both sides leave the same 2 px.
                int markX = markOnLeft ? titleX - MARK_ROOM : titleX + titleWidth + 1;
                drawSortMark(d, markX, top + (height - 1) / 2, sortDirection == SortDirection.ASCENDING, ink);
            }
        }
    }

    /** A small triangle: tip up when ascending, down when descending. */
    private static void drawSortMark(Draw d, int markX, int middleY, boolean up, int ink) {
        int rows = (MARK_W + 1) / 2;
        int rowTop = middleY - rows / 2;
        for (int i = 0; i < rows; i++) {
            int inset = up ? rows - 1 - i : i;
            d.fill(markX + inset, rowTop + i, markX + MARK_W - inset, rowTop + i + 1, ink);
        }
    }

    @Override
    public List<String> tooltipAt(double mouseX, double mouseY) {
        List<String> own = super.tooltipAt(mouseX, mouseY);
        if (own != null && !own.isEmpty()) {
            return own;
        }
        if (itemTooltips && active && artAt(mouseX, mouseY) instanceof ItemView item) {
            return itemTipShown ? List.of() : ItemArt.nameTip(item);
        }
        String cut = cutTextAt(mouseX, mouseY);
        return cut == null ? own : List.of(cut);
    }

    /** The whole text under the mouse, when it is too long for its cell. */
    private String cutTextAt(double mouseX, double mouseY) {
        if (!active || !isOver(mouseX, mouseY)) {
            return null;
        }
        int column = columnAt(mouseX);
        if (column < 0) {
            return null;
        }
        Column<T> col = columns.get(column);
        int[] edges = columnEdges();
        int room = edges[column + 1] - edges[column] - 2 * CELL_PAD;
        if (inHeader(mouseX, mouseY)) {
            room -= col.sortable() ? MARK_ROOM : 0;
            return TextMeasure.width(col.title) > room ? col.title : null;
        }
        int row = indexAt(mouseX, mouseY);
        if (row < 0 || col.renderer != null || col.text == null) {
            return null;
        }
        IconArt art = col.icon == null ? null : col.icon.apply(view.get(row));
        if (art != null) {
            room -= artWidth(art) + ICON_GAP;
        }
        String text = col.text.apply(view.get(row));
        return text != null && TextMeasure.width(text) > room ? text : null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || button != 0 || !isOver(mouseX, mouseY)) {
            return false;
        }
        if (inHeader(mouseX, mouseY)) {
            int column = columnAt(mouseX);
            if (column >= 0) {
                cycleSort(column);
            }
            return true;
        }
        if (inBar(mouseX, mouseY)) {
            bar.press(mouseX, mouseY, barX(), trackY(), trackH());
            return true;
        }
        int row = indexAt(mouseX, mouseY);
        if (row < 0) {
            return true;
        }
        long now = BaseComponent.clock.getAsLong();
        boolean twice = row == lastClickRow && now - lastClickAt <= DOUBLE_CLICK_MS;
        lastClickRow = twice ? -1 : row;
        lastClickAt = now;
        selectIndex(row);
        if (twice) {
            fireActivate();
        } else {
            BaseComponent.click();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return bar.drag(mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!bar.dragging()) {
            return false;
        }
        bar.release();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!active || !overflows() || !isOver(mouseX, mouseY)) {
            return false;
        }
        clampScroll();
        bar.wheel(amount, ScrollBar.WHEEL_ROWS * rowHeight);
        return true;
    }

    @Override
    protected boolean takesFocus() {
        return active;
    }

    @Override
    protected boolean navigable() {
        return active && visible;
    }

    @Override
    protected boolean activate() {
        if (!active || selectedIndex < 0) {
            return false;
        }
        fireActivate();
        return true;
    }

    @Override
    protected boolean capturesKeys() {
        return active;
    }

    /** Takes only the page keys, which have no InputKey. Every other key goes on to {@link #keyPressed}. */
    @Override
    public boolean keyCaptured(KeyPress press) {
        if (!active || press.kind() != KeyPress.Kind.KEY || view.isEmpty()) {
            return false;
        }
        if (press.code() == RAW_PAGE_UP) {
            pageUp();
            return true;
        }
        if (press.code() == RAW_PAGE_DOWN) {
            pageDown();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!active || view.isEmpty()) {
            return false;
        }
        switch (key) {
            case UP:
                selectIndex(selectedIndex < 0 ? firstVisibleRow() : selectedIndex - 1);
                return true;
            case DOWN:
                selectIndex(selectedIndex < 0 ? firstVisibleRow() : selectedIndex + 1);
                return true;
            case HOME:
                selectIndex(0);
                return true;
            case END:
                selectIndex(view.size() - 1);
                return true;
            case ENTER:
            case SPACE:
                return activate();
            default:
                return false;
        }
    }
}
