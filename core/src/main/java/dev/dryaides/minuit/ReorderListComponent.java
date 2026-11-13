package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Redraw;
import dev.dryaides.minuit.internal.FitCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A vertical list the player reorders by dragging a row's handle. Only the rows in view are drawn, so
 * thousands of rows cost what a screenful costs; a drag asks {@code locked} once about every row.
 *
 * <pre>{@code
 * ReorderListComponent<Pack> packs = add(new ReorderListComponent<>(at(new Box("packs", 8, 8, 180, 120)),
 *         config.packs, Pack::name)
 *         .locked(Pack::required)
 *         .deletable(true)
 *         .onReorder((order, moved, from, to) -> config.packs = order)
 *         .onDelete((pack, index) -> config.remove(pack)));
 * }</pre>
 *
 * <p>Press a handle and move to lift the row: it floats under the pointer, the others slide apart, and an
 * outlined slot marks where it will land. Near the top or bottom edge the list scrolls by itself. Escape puts
 * everything back. Click a row to select it, twice to activate it. With focus: up and down move the
 * selection, home and end jump, page up and page down go a page, Enter or Space activates, Delete removes
 * the row if it can be deleted, and Alt or Ctrl with an arrow, home or end moves the focused row.
 *
 * <p>A locked row keeps its index: the others pass over it, and nothing can land on it.
 * {@link #onReorder} runs only when the order really changed, so not for a drag that ends where it began,
 * a row passing an equal twin, a cancelled drag, {@link #setItems}, {@link #move} or a deletion.
 *
 * <h2>What the public API leaves open</h2>
 * {@link HeldKeys} knows Ctrl and Shift, not Alt, and a key's release reaches no widget. So the list sees
 * Alt's press as a raw key and takes it as held for {@value #ALT_LATCH_MS} ms, longer while Alt's own key
 * repeat or further moves arrive. Alt released just before an arrow still counts, and an Alt held longer
 * with no repeat does not. Ctrl with an arrow is exact, and so is {@link #altHeld}, for a mod that can read
 * the key itself. Rows are matched by {@code equals} only across {@link #setItems}: equal rows stay
 * separate rows, and an open drag ends there without a callback.
 */
public class ReorderListComponent<T> extends BaseComponent implements ItemSource {
    /** Draws what sits between the handle and the delete button; the list has drawn the row's background. */
    @FunctionalInterface
    public interface RowRenderer<T> {
        void draw(Draw d, Theme theme, T item, Box row, boolean hovered, boolean selected);
    }

    /** The new order, what moved, and its index before and after. The list is a copy. */
    @FunctionalInterface
    public interface OrderListener<T> {
        void changed(List<T> order, T moved, int from, int to);
    }

    /** Width of the scroll bar's strip. */
    static final int BAR = ScrollBar.WIDTH;
    /** Width of the handle's strip, where a drag starts. */
    public static final int HANDLE = 11;
    /** Width of the delete button's strip. */
    public static final int DELETE = 11;
    private static final int ROW_H = 14;
    private static final int ICON_GAP = 2;
    private static final int MIN_TEXT = 16;
    private static final int DRAG_SLOP = 2;
    private static final long DOUBLE_CLICK_MS = 350L;
    private static final int SLIDE_MS = 110;
    private static final int REFUSED_MS = 300;
    private static final long ALT_LATCH_MS = 1000L;
    private static final long NEVER = Long.MIN_VALUE / 2;
    private static final int GRIP_W = 3;
    private static final int GRIP_H = 5;
    private static final int CROSS = 5;
    private static final int WASH = 0x30;
    private static final int WASH_HELD = 0x58;
    private static final int CROSS_REST = 0x90;
    private static final float GAP_WASH = 0.6F;
    private static final int LOCK_W = 5;
    private static final int LOCK_H = 6;
    private static final double MARK_CONTRAST = 2D;
    private static final float AUTO_MIN = 40F;
    private static final float AUTO_MAX = 160F;
    // The longer the pointer holds at the edge the faster it goes, so that thousands of rows stay in reach.
    private static final float AUTO_BOOST = 3.5F;
    private static final float AUTO_BOOST_MAX = 40F;
    private static final long MAX_STEP_MS = 100L;

    private static final int RAW_ESCAPE = 256;
    private static final int RAW_DOWN = 264;
    private static final int RAW_UP = 265;
    private static final int RAW_PAGE_UP = 266;
    private static final int RAW_PAGE_DOWN = 267;
    private static final int RAW_HOME = 268;
    private static final int RAW_END = 269;
    private static final int RAW_SHIFT = 340;
    private static final int RAW_CTRL = 341;
    private static final int RAW_ALT = 342;
    private static final int RAW_SUPER = 343;
    private static final int RAW_RIGHT_SHIFT = 344;
    private static final int RAW_RIGHT_CTRL = 345;
    private static final int RAW_RIGHT_ALT = 346;
    private static final int RAW_RIGHT_SUPER = 347;

    /** One row. Its identity follows it through a move, so equal items stay apart and a slide keeps its row. */
    private static final class Entry<T> {
        final T item;
        int index;
        int fromY;
        long animAt = NEVER;

        Entry(T item, int index) {
            this.item = item;
            this.index = index;
        }
    }

    private final FitCache fits = new FitCache();
    private final Function<T, String> label;
    private List<Entry<T>> rows = new ArrayList<>();

    private Function<T, Icon> icon;
    private Function<T, IconArt> iconArt;
    private RowRenderer<T> renderer;
    private Function<T, List<String>> rowTooltip;
    private Predicate<T> locked;
    private Predicate<T> deletable;
    private BooleanSupplier altHeld;
    private List<String> deleteTooltip = List.of();
    private List<String> lockedTooltip = List.of();
    private OrderListener<T> onReorder;
    private BiConsumer<T, Integer> onDelete;
    private Consumer<T> onSelect;
    private Consumer<T> onActivate;
    private String emptyText;
    private int rowHeight = ROW_H;
    /** False keeps the box at its declared height and scrolls on any overflow (see {@link ScrollComponent}). */
    public boolean fit = true;
    /** The declared height: the most the box takes. */
    public int max;
    private boolean rowHeightSet;
    private boolean itemTooltips = true;
    private boolean itemTipShown;

    private Entry<T> selected;
    private Entry<T> cursor;
    private final ScrollBar bar = new ScrollBar();
    private Box[] rowBoxes = new Box[0];
    private Entry<T> lastClicked;
    private long lastClickAt;
    private Theme lastTheme;
    private final RowLook.HoverFade hover = new RowLook.HoverFade();
    private long altAt = NEVER;
    private Entry<T> refused;
    private long refusedAt;

    private Entry<T> heldDelete;
    private Entry<T> armed;
    private int armY;

    // A drag. slots holds the index of every row that can move, ascending; the row lands in slots[toSlot].
    private Entry<T> dragged;
    private int[] slots = new int[0];
    private int fromSlot;
    private int toSlot;
    private int grab;
    private int dragY;
    private int liftY;
    private int liftScroll;
    // The autoscroll's fraction of a pixel, kept between frames so a slow scroll still moves.
    private double autoCarry;
    private long lastStep;
    private long edgeSince = NEVER;

    // Rows sliding to their place, and the one row gliding back down after a drag.
    private final List<Entry<T>> moving = new ArrayList<>();
    private Entry<T> settling;

    public ReorderListComponent(Box box, Collection<? extends T> items, Function<T, String> label) {
        super(box);
        this.max = box.h();
        this.label = Objects.requireNonNull(label, "label");
        setItems(items);
    }

    /** A theme icon before the label; null for an item shows none. */
    public ReorderListComponent<T> icon(Function<T, Icon> icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Your own picture before the label, instead of a theme icon; null for an item shows none. An
     * {@link ItemView} is drawn with its count and durability, and its row answers {@link #itemAt} for the
     * recipe keys. Rows grow to 18 px for a 16 px item, unless {@link #rowHeight} was set.
     */
    public ReorderListComponent<T> iconArt(Function<T, IconArt> iconArt) {
        this.iconArt = iconArt;
        if (!rowHeightSet) {
            rowHeight = iconArt != null ? ItemArt.ITEM_ROW : ROW_H;
            clampScroll();
        }
        return this;
    }

    /** Whether an item row shows the game's own tooltip (its name without a world). On by default; a {@link #rowTooltip} wins. */
    public ReorderListComponent<T> itemTooltips(boolean itemTooltips) {
        this.itemTooltips = itemTooltips;
        return this;
    }

    /** Replaces the icon and label. The box it gets is what the handle and delete button leave. */
    public ReorderListComponent<T> renderer(RowRenderer<T> renderer) {
        this.renderer = renderer;
        return this;
    }

    /** Lines for the row under the pointer. Null or empty falls back to {@link #tooltip}. */
    public ReorderListComponent<T> rowTooltip(Function<T, List<String>> rowTooltip) {
        this.rowTooltip = rowTooltip;
        return this;
    }

    /** Lines for the delete button, ahead of the row's own. */
    public ReorderListComponent<T> deleteTooltip(String... lines) {
        deleteTooltip = List.of(lines);
        return this;
    }

    /** Lines for a locked row's padlock, ahead of the row's own. */
    public ReorderListComponent<T> lockedTooltip(String... lines) {
        lockedTooltip = List.of(lines);
        return this;
    }

    /** Which rows stay at their index. A row's answer is read when a drag or a move starts. */
    public ReorderListComponent<T> locked(Predicate<T> locked) {
        this.locked = locked;
        return this;
    }

    /** True shows a delete button on every row that is not locked. Off by default. */
    public ReorderListComponent<T> deletable(boolean on) {
        deletable = on ? item -> !isLocked(item) : null;
        return this;
    }

    /** Which rows show a delete button, locked or not. */
    public ReorderListComponent<T> deletable(Predicate<T> deletable) {
        this.deletable = deletable;
        return this;
    }

    /** Whether Alt is down, for a mod that can read it: this replaces the guess described on the class. */
    public ReorderListComponent<T> altHeld(BooleanSupplier altHeld) {
        this.altHeld = altHeld;
        return this;
    }

    /** Called when a drag or a key has changed the order, never otherwise. */
    public ReorderListComponent<T> onReorder(OrderListener<T> onReorder) {
        this.onReorder = onReorder;
        return this;
    }

    /** Called after a row was removed by its button or the Delete key, with the index it had. */
    public ReorderListComponent<T> onDelete(BiConsumer<T, Integer> onDelete) {
        this.onDelete = onDelete;
        return this;
    }

    /** Called when the selection changes through a click or a key, with null if the last row went. */
    public ReorderListComponent<T> onSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /** Called on a double click, Enter or Space. */
    public ReorderListComponent<T> onActivate(Consumer<T> onActivate) {
        this.onActivate = onActivate;
        return this;
    }

    /** Text shown when there are no rows. */
    public ReorderListComponent<T> emptyText(String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    public ReorderListComponent<T> rowHeight(int rowHeight) {
        this.rowHeight = Math.max(1, rowHeight);
        rowHeightSet = true;
        clampScroll();
        return this;
    }

    public ReorderListComponent<T> active(boolean active) {
        this.active = active;
        if (!active) {
            bar.release();
            dropGestures();
        }
        return this;
    }

    @Override
    public ReorderListComponent<T> tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** New rows. Selection, cursor and scroll stay, matched by equality. An open drag ends. Null means none. */
    public final void setItems(Collection<? extends T> items) {
        fits.clear();
        T keepSelected = selected == null ? null : selected.item;
        T keepCursor = cursor == null ? null : cursor.item;
        int oldCursor = cursor == null ? 0 : cursor.index;
        dragged = null;
        armed = null;
        heldDelete = null;
        settling = null;
        moving.clear();
        List<Entry<T>> fresh = new ArrayList<>();
        if (items != null) {
            for (T item : items) {
                if (item != null) {
                    fresh.add(new Entry<>(item, fresh.size()));
                }
            }
        }
        rows = fresh;
        selected = keepSelected == null ? null : find(keepSelected);
        cursor = keepCursor == null ? null : find(keepCursor);
        if (cursor == null && !rows.isEmpty()) {
            cursor = rows.get(Math.min(oldCursor, rows.size() - 1));
        }
        clampScroll();
    }

    /** The rows in their current order. A copy: a drag in progress does not show until it lands. */
    public List<T> items() {
        List<T> out = new ArrayList<>(rows.size());
        for (Entry<T> row : rows) {
            out.add(row.item);
        }
        return Collections.unmodifiableList(out);
    }

    int rowCount() {
        return rows.size();
    }

    public T selected() {
        return selected == null ? null : selected.item;
    }

    /** The index of the selected row, or -1. */
    public int selectedIndex() {
        return selected == null ? -1 : selected.index;
    }

    /** Sets the selection without calling {@link #onSelect}; the cursor follows if the row is there. */
    public void select(T item) {
        selected = item == null ? null : find(item);
        if (selected != null) {
            setCursor(selected);
        }
    }

    /** The item the keyboard is on, or null when the list is empty. */
    public T cursor() {
        return cursor == null ? null : cursor.item;
    }

    double scroll() {
        return bar.offset();
    }

    public void setScroll(double value) {
        clampScroll();
        bar.offset((int) Math.round(value));
    }

    /** The row's place on screen, for its index; the whole row, as if nothing were moving. */
    Box rowBox(int index) {
        return new Box("row", viewX(), rowY(index), viewW(), rowHeight);
    }

    /** Where a drag of that row starts. */
    Box handleBox(int index) {
        return new Box("handle", handleX(), rowY(index), HANDLE, rowHeight);
    }

    /** Where that row's delete button is, if it has one. */
    Box deleteBox(int index) {
        return new Box("delete", deleteX(), rowY(index), DELETE, rowHeight);
    }

    /** The item of the row at a point, or null. */
    public T rowAt(double mx, double my) {
        int i = indexAt(mx, my);
        return i < 0 ? null : rows.get(i).item;
    }

    public boolean isLocked(T item) {
        return locked != null && locked.test(item);
    }

    /** True while a row is lifted. */
    public boolean isDragging() {
        return dragged != null;
    }

    /** The item lifted, or null. */
    public T draggedItem() {
        return dragged == null ? null : dragged.item;
    }

    /** The index a drag would land at now, or -1 with no drag. */
    int dropIndex() {
        return dragged == null ? -1 : slots[toSlot];
    }

    /** True while rows are sliding to their place. */
    boolean isAnimating() {
        return settling != null || !moving.isEmpty();
    }

    /** As Escape does during a drag: everything goes back, with no callback. */
    public void cancelDrag() {
        armed = null;
        if (dragged != null) {
            endDrag(false);
        }
    }

    /**
     * Moves a row to another index, passing over locked rows, without calling {@link #onReorder}.
     *
     * @return false if either index is locked or out of range, or they are the same
     */
    public boolean move(int from, int to) {
        if (from < 0 || to < 0 || from >= rows.size() || to >= rows.size() || dragged != null) {
            return false;
        }
        int[] now = freeSlots();
        int a = Arrays.binarySearch(now, from);
        int b = Arrays.binarySearch(now, to);
        if (a < 0 || b < 0 || a == b) {
            return false;
        }
        slide(() -> rotate(now, a, b));
        return true;
    }

    private Entry<T> find(T item) {
        for (Entry<T> row : rows) {
            if (row.item.equals(item)) {
                return row;
            }
        }
        return null;
    }

    private int[] freeSlots() {
        int[] out = new int[rows.size()];
        int count = 0;
        for (int i = 0; i < out.length; i++) {
            if (!isLocked(rows.get(i).item)) {
                out[count++] = i;
            }
        }
        return Arrays.copyOf(out, count);
    }

    /** Moves the row in s[a] to s[b]; the rows between shift by one slot, and locked rows stay. */
    private void rotate(int[] s, int a, int b) {
        Entry<T> row = rows.get(s[a]);
        int step = a < b ? 1 : -1;
        for (int k = a; k != b; k += step) {
            Entry<T> next = rows.get(s[k + step]);
            rows.set(s[k], next);
            next.index = s[k];
        }
        rows.set(s[b], row);
        row.index = s[b];
    }

    private int viewX() { return x + 1; }
    private int viewY() { return y + 1; }
    private int viewH() { return Math.max(0, h - 2); }
    /** Sizes the box by the scroll rule ({@link ScrollComponent#fitted}), never under one row plus the well's edges. */
    private void fitHeight() { h = ScrollComponent.fitted(Math.max(rowHeight, contentH()) + 2, max, fit); }
    private int contentH() { return (int) Math.min(Integer.MAX_VALUE, (long) rows.size() * rowHeight); }
    private boolean overflows() { return contentH() > viewH(); }
    private int viewW() { return Math.max(0, w - 2 - (overflows() ? BAR : 0)); }
    private int barX() { return x + w - BAR - 1; }
    private int trackY() { return viewY() + 1; }
    private int trackH() { return Math.max(1, viewH() - 2); }
    private int handleX() { return viewX() + 1; }
    private int deleteX() { return viewX() + viewW() - DELETE - 1; }
    private boolean roomForHandle() { return viewW() >= HANDLE + 2; }

    private void clampScroll() {
        bar.content(contentH(), viewH());
    }

    private int rowY(int index) {
        return viewY() + index * rowHeight - bar.offset();
    }

    private int indexAt(double mx, double my) {
        if (mx < viewX() || mx >= viewX() + viewW() || my < viewY() || my >= viewY() + viewH()) {
            return -1;
        }
        int i = (int) Math.floor((my - viewY() + bar.offset()) / rowHeight);
        return i >= 0 && i < rows.size() ? i : -1;
    }

    private boolean overHandle(double mx) {
        return roomForHandle() && mx >= handleX() && mx < handleX() + HANDLE;
    }

    private boolean hasDelete(T item) {
        return deletable != null && viewW() >= HANDLE + DELETE + MIN_TEXT && deletable.test(item);
    }

    private boolean overDelete(Entry<T> row, double mx) {
        return mx >= deleteX() && mx < deleteX() + DELETE && hasDelete(row.item);
    }

    private boolean overBar(double mx, double my) {
        return overflows() && mx >= barX() && mx < x + w && my >= y && my < y + h;
    }

    private void setCursor(Entry<T> row) {
        cursor = row;
        reveal(row.index);
    }

    private void reveal(int index) {
        int top = index * rowHeight;
        clampScroll();
        if (top < bar.offset()) {
            bar.offset(top);
        } else if (top + rowHeight > bar.offset() + viewH()) {
            bar.offset(top + rowHeight - viewH());
        }
    }

    /** The index a row shows at, which is its own unless a drag has pushed it along. */
    private int shownIndex(Entry<T> row) {
        if (dragged == null) {
            return row.index;
        }
        if (row == dragged) {
            return slots[toSlot];
        }
        int k = Arrays.binarySearch(slots, row.index);
        if (k < 0) {
            return row.index;
        }
        if (fromSlot < k && k <= toSlot) {
            return slots[k - 1];
        }
        if (toSlot <= k && k < fromSlot) {
            return slots[k + 1];
        }
        return row.index;
    }

    /** The row showing at an index; the reverse of {@link #shownIndex}. */
    private Entry<T> rowShownAt(int index) {
        if (dragged == null) {
            return rows.get(index);
        }
        int k = Arrays.binarySearch(slots, index);
        if (k < 0) {
            return rows.get(index);
        }
        if (k == toSlot) {
            return dragged;
        }
        if (fromSlot <= k && k < toSlot) {
            return rows.get(slots[k + 1]);
        }
        if (toSlot < k && k <= fromSlot) {
            return rows.get(slots[k - 1]);
        }
        return rows.get(index);
    }

    private int firstInView() {
        return Math.max(0, bar.offset() / rowHeight);
    }

    private int endOfView() {
        return Math.min(rows.size(), firstInView() + viewH() / rowHeight + 2);
    }

    private static float eased(long age) {
        float t = 1F - Math.max(0F, (float) age) / SLIDE_MS;
        return 1F - t * t * t;
    }

    private boolean isMoving(Entry<T> row, long now) {
        return row.animAt != NEVER && now - row.animAt < SLIDE_MS;
    }

    /** A row's top within the content, part way through its slide if it has one. */
    private int visualY(Entry<T> row, long now) {
        int target = shownIndex(row) * rowHeight;
        if (!isMoving(row, now)) {
            return target;
        }
        return row.fromY + Math.round((target - row.fromY) * eased(now - row.animAt));
    }

    /** Runs a change of the order, then slides each row seen before it from where it was to its new place. */
    private void slide(Runnable change) {
        if (reducedMotion()) {
            change.run();
            stopSliding();
            return;
        }
        long now = clock().getAsLong();
        Map<Entry<T>, Integer> before = new LinkedHashMap<>();
        for (int i = firstInView(); i < endOfView(); i++) {
            Entry<T> row = rowShownAt(i);
            before.put(row, visualY(row, now));
        }
        for (Entry<T> row : moving) {
            before.put(row, visualY(row, now));
        }
        change.run();
        for (Map.Entry<Entry<T>, Integer> seen : before.entrySet()) {
            Entry<T> row = seen.getKey();
            if (row == dragged || row == settling || row.index < 0) {
                continue;
            }
            int was = seen.getValue();
            if (was != shownIndex(row) * rowHeight) {
                row.fromY = was;
                row.animAt = now;
                if (!moving.contains(row)) {
                    moving.add(row);
                }
            }
        }
    }

    private void stopSliding() {
        for (Entry<T> row : moving) {
            row.animAt = NEVER;
        }
        moving.clear();
        settling = null;
    }

    private void prune(long now) {
        if (reducedMotion()) {
            stopSliding();
            return;
        }
        for (int i = moving.size() - 1; i >= 0; i--) {
            Entry<T> row = moving.get(i);
            if (!isMoving(row, now) || row.index < 0) {
                row.animAt = NEVER;
                moving.remove(i);
            }
        }
        if (settling != null && !isMoving(settling, now)) {
            settling.animAt = NEVER;
            settling = null;
        }
    }

    private void lift(Entry<T> row, int atY) {
        armed = null;
        int[] free = freeSlots();
        int k = Arrays.binarySearch(free, row.index);
        if (k < 0 || free.length < 2) {
            return;
        }
        if (settling != null) {
            settling.animAt = NEVER;
            settling = null;
        }
        slots = free;
        fromSlot = k;
        toSlot = k;
        grab = armY - rowY(row.index);
        dragged = row;
        dragY = atY;
        liftY = armY;
        liftScroll = bar.offset();
        autoCarry = 0D;
        lastStep = clock().getAsLong();
        edgeSince = NEVER;
        row.animAt = NEVER;
        moving.remove(row);
        BaseComponent.click();
        aim();
    }

    /** The top of the floating row on screen; it stays in the view. */
    private int floatTop() {
        int low = viewY();
        int high = Math.max(low, viewY() + viewH() - rowHeight);
        return Math.max(low, Math.min(high, dragY - grab));
    }

    /** Picks the slot nearest the floating row, and slides the rows if that changed. */
    private void aim() {
        int top = floatTop() - viewY() + bar.offset();
        int index = Math.max(0, Math.min(rows.size() - 1, Math.floorDiv(top + rowHeight / 2, rowHeight)));
        int slot = nearestSlot(index);
        if (slot != toSlot) {
            slide(() -> toSlot = slot);
        }
    }

    private int nearestSlot(int index) {
        int k = Arrays.binarySearch(slots, index);
        if (k >= 0) {
            return k;
        }
        int after = -k - 1;
        int before = after - 1;
        if (before < 0) {
            return after;
        }
        if (after >= slots.length) {
            return before;
        }
        // Over locked rows: the nearer side, and on a tie the side it is on, so that it does not flicker.
        int toBefore = index - slots[before];
        int toAfter = slots[after] - index;
        if (toBefore == toAfter && toSlot == after) {
            return after;
        }
        return toBefore <= toAfter ? before : after;
    }

    /** Scrolls when the pointer holds near an edge it has moved towards; a row grabbed at the edge stays still. */
    private void stepDrag() {
        long now = clock().getAsLong();
        long elapsed = Math.max(0L, Math.min(MAX_STEP_MS, now - lastStep));
        lastStep = now;
        int vy = viewY();
        int vh = viewH();
        int zone = Math.min(Math.max(rowHeight, 8), Math.max(1, vh / 3));
        boolean up = dragY < vy + zone && dragY < liftY;
        boolean down = dragY >= vy + vh - zone && dragY > liftY;
        if ((!up && !down) || !overflows()) {
            edgeSince = NEVER;
            return;
        }
        if (edgeSince == NEVER) {
            edgeSince = now;
        }
        int depthIn = up ? vy + zone - dragY : dragY - (vy + vh - zone) + 1;
        float depth = Math.min(1F, depthIn / (float) zone);
        float boost = Math.min(AUTO_BOOST_MAX, 1F + AUTO_BOOST * (now - edgeSince) / 1000F);
        float speed = (AUTO_MIN + (AUTO_MAX - AUTO_MIN) * depth) * boost;
        int before = bar.offset();
        autoCarry += (up ? -1 : 1) * speed * elapsed / 1000F;
        int step = (int) autoCarry;
        autoCarry -= step;
        clampScroll();
        bar.offset(before + step);
        if (bar.offset() != before) {
            aim();
        }
    }

    private void endDrag(boolean commit) {
        Entry<T> row = dragged;
        int glideFrom = floatTop() - viewY();
        boolean changed = commit && toSlot != fromSlot;
        int from = row.index;
        if (commit) {
            if (changed) {
                List<T> before = items();
                rotate(slots, fromSlot, toSlot);
                changed = !before.equals(items());
            }
            dragged = null;
        } else {
            slide(() -> toSlot = fromSlot);
            dragged = null;
            clampScroll();
            bar.offset(liftScroll);
        }
        reveal(row.index);
        if (!reducedMotion()) {
            row.fromY = glideFrom + bar.offset();
            row.animAt = clock().getAsLong();
            settling = row;
        }
        if (changed) {
            BaseComponent.click();
            if (onReorder != null) {
                onReorder.changed(items(), row.item, from, row.index);
            }
        }
    }

    private void dropGestures() {
        armed = null;
        heldDelete = null;
        if (dragged != null) {
            endDrag(false);
        }
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        lastTheme = theme;
        itemTipShown = false;
        fitHeight();
        Palette p = theme.palette();
        if (!active) {
            dropGestures();
        }
        long now = Redraw.quietClock();
        prune(now);
        if (!moving.isEmpty() || settling != null) {
            Redraw.ask();
        }
        theme.frame().well(d, p, x, y, w, h, focused && active);
        if (rows.isEmpty()) {
            hover.step(-1);
            RowLook.drawEmpty(d, p, emptyText, active, viewX(), viewY(), viewW(), viewH());
            return;
        }
        if (dragged != null) {
            stepDrag();
        }
        int hoverIndex = active && hovered && !bar.dragging() && dragged == null && armed == null ? indexAt(mx, my) : -1;
        if (heldDelete != null && hoverIndex != heldDelete.index) {
            hoverIndex = -1; // the row whose button is held keeps its hover; no other row lights up meanwhile
        }
        hover.step(hoverIndex);

        int vx = viewX();
        int vy = viewY();
        int vw = viewW();
        int vh = viewH();
        d.scissor(vx, vy, vx + vw, vy + vh);
        drawRows(d, theme, mx, hoverIndex, now);
        d.endScissor();
        if (hoverIndex >= 0 && hoverIndex < rows.size() && !overHandle(mx) && !overDelete(rows.get(hoverIndex), mx)) {
            itemTipShown = showItemTip(d, rows.get(hoverIndex).item, mx, my);
        }
        if (overflows() && RowLook.barFits(w, h, BAR)) {
            bar.draw(d, theme, barX(), trackY(), trackH(), active && hovered && dragged == null && overBar(mx, my));
            if (!active) {
                RowLook.dimBar(d, p, barX(), trackY(), BAR, trackH());
            }
        }
        if (dragged != null) {
            drawMarker(d, theme);
        }
        if (dragged != null || settling != null) {
            // Kept to the view: a theme's shadow would otherwise fall on the well's border and the scroll bar.
            d.scissor(vx, vy, vx + vw, vy + vh);
            if (dragged != null) {
                drawFloating(d, theme, dragged, floatTop());
            } else {
                drawFloating(d, theme, settling, vy + visualY(settling, now) - bar.offset());
            }
            d.endScissor();
        }
    }

    private void drawRows(Draw d, Theme theme, int mx, int hoverIndex, long now) {
        Palette p = theme.palette();
        int vx = viewX();
        int vy = viewY();
        int vw = viewW();
        int vh = viewH();
        int top = bar.offset();
        for (int i = firstInView(); i < endOfView(); i++) {
            Entry<T> row = rowShownAt(i);
            int ry = vy + i * rowHeight - top;
            if (ry + rowHeight <= vy || ry >= vy + vh) {
                continue;
            }
            if (row == dragged || row == settling) {
                theme.frame().row(d, p, vx, ry, vw, rowHeight, RowLook.tint(p, GAP_WASH));
            } else if (!isMoving(row, now)) {
                drawRow(d, theme, row, ry, row.index == hoverIndex, mx);
            }
        }
        for (Entry<T> row : moving) {
            if (row != dragged && row != settling) {
                int ry = vy + visualY(row, now) - top;
                if (ry + rowHeight > vy && ry < vy + vh) {
                    drawRow(d, theme, row, ry, false, mx);
                }
            }
        }
    }

    private void drawRow(Draw d, Theme theme, Entry<T> row, int ry, boolean isHovered, int mx) {
        Palette p = theme.palette();
        int vx = viewX();
        int vw = viewW();
        boolean isSelected = row == selected;
        int face = RowLook.face(p, active, focused, isSelected, isHovered, hover.fade());
        theme.frame().row(d, p, vx, ry, vw, rowHeight, face >>> 24 != 0 ? face : 0);
        if (RowLook.textCut(ry, rowHeight, viewY(), viewH())) {
            return;
        }
        int ink = RowLook.ink(p, face, isSelected, active);
        int faint = !active || isSelected || isHovered ? ink : RowLook.softInk(p, ink);
        boolean heldHere = armed == row;
        drawHandle(d, p, row, ry, ink, faint, isHovered && overHandle(mx), heldHere);
        boolean del = hasDelete(row.item);
        if (del) {
            drawDelete(d, p, ry, ink, isSelected, isHovered && overDelete(row, mx), heldDelete == row);
        }
        drawContent(d, theme, row, ry, ink, faint, isHovered, isSelected, del);
    }

    private void drawContent(Draw d, Theme theme, Entry<T> row, int ry, int ink, int faint, boolean isHovered, boolean isSelected, boolean del) {
        int cx = contentX();
        int right = viewX() + viewW() - (del ? DELETE + 1 : 0);
        if (renderer != null) {
            renderer.draw(d, theme, row.item, rendererBox(row.index, cx, ry, Math.max(0, right - cx)), isHovered, isSelected);
            return;
        }
        IconArt art = artOf(theme, row.item);
        if (art != null) {
            cx += drawArt(d, theme.palette(), art, cx, ry, faint) + ICON_GAP;
        }
        d.text(fits.fit(d, row.item, textOf(row.item), Math.max(0, right - cx - 2)).cut(), cx, RowLook.textY(ry, rowHeight), ink);
    }

    /** Where a row's own content starts; clear of the handle's wash. */
    private int contentX() {
        return viewX() + 1 + (roomForHandle() ? HANDLE + ICON_GAP : 0);
    }

    /** Two columns of dots, or the padlock on a locked row. */
    private void drawHandle(Draw d, Palette p, Entry<T> row, int ry, int ink, int faint, boolean over, boolean held) {
        if (!roomForHandle()) {
            return;
        }
        int hx = handleX();
        boolean isLocked = isLocked(row.item);
        if (isLocked) {
            // A colour, not a motion, so reduced motion keeps it.
            boolean refusing = refused == row && clock().getAsLong() - refusedAt < REFUSED_MS;
            if (refusing) {
                // The delete button's red: a red padlock alone is lost on some themes' selected rows.
                d.fill(hx, ry + 1, hx + HANDLE, ry + rowHeight - 1, p.destructive());
            }
            padlock(d, hx + (HANDLE - LOCK_W) / 2, ry + (rowHeight - LOCK_H) / 2, refusing ? p.destructiveText() : faint);
            return;
        }
        if (active && (over || held)) {
            d.fill(hx, ry + 1, hx + HANDLE, ry + rowHeight - 1, Shapes.alpha(ink, held ? WASH_HELD : WASH));
        }
        grip(d, hx + (HANDLE - GRIP_W) / 2, ry + (rowHeight - GRIP_H) / 2, active && (over || held) ? ink : faint);
    }

    /** Drawn by hand, as the grip it stands in for: the theme's lock icon fills the strip and crowds the text. */
    private static void padlock(Draw d, int lx, int ly, int color) {
        d.fill(lx + 1, ly, lx + 4, ly + 1, color);
        d.fill(lx + 1, ly + 1, lx + 2, ly + 2, color);
        d.fill(lx + 3, ly + 1, lx + 4, ly + 2, color);
        d.fill(lx, ly + 2, lx + LOCK_W, ly + 4, color);
        d.fill(lx, ly + 4, lx + 2, ly + 5, color);
        d.fill(lx + 3, ly + 4, lx + LOCK_W, ly + 5, color);
        d.fill(lx, ly + 5, lx + LOCK_W, ly + LOCK_H, color);
    }

    private static void grip(Draw d, int gx, int gy, int color) {
        for (int line = 0; line < GRIP_H; line += 2) {
            d.fill(gx, gy + line, gx + 1, gy + line + 1, color);
            d.fill(gx + 2, gy + line, gx + 3, gy + line + 1, color);
        }
    }

    /** Drawn by hand: the theme's close icon is as tall as the text and would shout on every row. */
    private void drawDelete(Draw d, Palette p, int ry, int ink, boolean isSelected, boolean over, boolean held) {
        int dx = deleteX();
        // Quieter at rest on a bare row; a selected face is mid-toned in some themes and would swallow it.
        int color = !active || isSelected ? ink : Shapes.alpha(ink, CROSS_REST);
        if (active && (over || held)) {
            d.fill(dx, ry + 1, dx + DELETE, ry + rowHeight - 1, held ? p.destructiveHover() : p.destructive());
            color = p.destructiveText();
        }
        int left = dx + (DELETE - CROSS) / 2;
        int top = ry + (rowHeight - CROSS) / 2;
        for (int i = 0; i < CROSS; i++) {
            d.fill(left + i, top + i, left + i + 1, top + i + 1, color);
            if (i != CROSS / 2) {
                d.fill(left + CROSS - 1 - i, top + i, left + CROSS - i, top + i + 1, color);
            }
        }
    }

    /** The box handed to the renderer, kept per row so a still list allocates none; a clash only costs a new box. */
    private Box rendererBox(int index, int bx, int by, int bw) {
        int room = viewH() / Math.max(1, rowHeight) + 8;
        if (rowBoxes.length < room) {
            rowBoxes = new Box[room];
        }
        int slot = index % rowBoxes.length;
        Box box = rowBoxes[slot];
        if (box == null || box.x() != bx || box.y() != by || box.w() != bw || box.h() != rowHeight) {
            box = new Box("row", bx, by, bw, rowHeight);
            rowBoxes[slot] = box;
        }
        return box;
    }

    /** The lifted row: a raised primary face on a solid back, so nothing shows through it in glass. */
    private void drawFloating(Draw d, Theme theme, Entry<T> row, int fy) {
        Palette p = theme.palette();
        FrameStyle frame = theme.frame();
        int vx = viewX();
        int vw = viewW();
        d.nextLayer(); // the text beneath may sort above a rectangle otherwise
        frame.dropShadow(d, p, vx, fy, vw, rowHeight, 2);
        frame.plate(d, p, vx, fy, vw, rowHeight, p.surface() | 0xFF000000, 0);
        int face = active ? p.primary() : RowLook.tint(p, 1F);
        frame.raised(d, p, vx, fy, vw, rowHeight, face, Shapes.Relief.FLAT);
        // A row still settling when the list is disabled has the disabled face: primaryText can vanish on it.
        int ink = active ? p.primaryText() : RowLook.ink(p, face, true, false);
        if (roomForHandle()) {
            grip(d, handleX() + (HANDLE - GRIP_W) / 2, fy + (rowHeight - GRIP_H) / 2, ink);
        }
        boolean del = hasDelete(row.item);
        drawContent(d, theme, row, fy, ink, ink, false, true, del);
    }

    /**
     * An outline around the place where the row will land, under the floating row: the row hides one edge at
     * most, so the mark never jumps as the pointer crosses the slot, and it never runs over the row's text.
     */
    private void drawMarker(Draw d, Theme theme) {
        Palette p = theme.palette();
        int vx = viewX();
        int vy = viewY();
        int vw = viewW();
        int top = vy + slots[toSlot] * rowHeight - bar.offset();
        int solid = p.primary() | 0xFF000000;
        int color = Shapes.contrast(solid, p.well() | 0xFF000000) >= MARK_CONTRAST ? solid : p.text() | 0xFF000000;
        d.nextLayer();
        d.scissor(vx, vy, vx + vw, vy + viewH());
        theme.frame().plate(d, p, vx, top, vw, rowHeight, 0, color);
        d.endScissor();
    }

    /** Draws the art at the row's middle; an item fitted crisply, with its decorations at full size. Returns its width. */
    private int drawArt(Draw d, Palette p, IconArt art, int ax, int ry, int color) {
        if (!ItemArt.isItem(art)) {
            art.draw(d, ax, ry + (rowHeight - art.height()) / 2, color, 1F);
            return art.width();
        }
        int room = artWidth(art);
        int top = ry + (rowHeight - room) / 2;
        ItemArt.fit(d, p, art, ax, top, room, color, active);
        if (room >= art.height()) {
            ItemArt.decorations(d, (ItemView) art, ax, top);
        }
        return room;
    }

    private int artWidth(IconArt art) {
        return ItemArt.isItem(art) ? Math.min(art.height(), rowHeight) : art.width();
    }

    /** The game's tooltip for an item row, when no tooltip of yours is set. */
    private boolean showItemTip(Draw d, T item, int mx, int my) {
        if (!itemTooltips || renderer != null || iconArt == null || hasOwnTip(item)) {
            return false;
        }
        return ItemArt.hoverTooltip(d, iconArt.apply(item), mx, my);
    }

    private boolean hasOwnTip(T item) {
        if (rowTooltip != null) {
            List<String> lines = rowTooltip.apply(item);
            if (lines != null && !lines.isEmpty()) {
                return true;
            }
        }
        return tooltip != null && !tooltip.isEmpty();
    }

    /** The item of the row under the pointer, for the recipe keys of JEI, REI or EMI. */
    @Override
    public ItemView itemAt(double mx, double my) {
        if (!visible || iconArt == null || dragged != null) {
            return null;
        }
        int i = indexAt(mx, my);
        return i >= 0 && i < rows.size() && iconArt.apply(rows.get(i).item) instanceof ItemView view ? view : null;
    }

    private IconArt artOf(Theme theme, T item) {
        if (iconArt != null) {
            IconArt art = iconArt.apply(item);
            if (art != null) {
                return art;
            }
        }
        if (icon != null) {
            Icon named = icon.apply(item);
            if (named != null) {
                return theme.icon(named);
            }
        }
        return null;
    }

    private String textOf(T item) {
        String text = label.apply(item);
        return text == null ? "" : text;
    }

    /**
     * The colour the list's own text has in a row, for a {@link RowRenderer} that draws over the row's
     * face: read it inside the renderer, since it follows the focus and {@code active}.
     */
    public int cellInk(Theme theme, boolean isSelected, boolean isHovered) {
        Palette p = theme.palette();
        return RowLook.ink(p, RowLook.face(p, active, focused, isSelected, isHovered, hover.fade()), isSelected, active);
    }

    private boolean cursorInView() {
        if (cursor == null) {
            return false;
        }
        int ry = rowY(cursor.index);
        return ry >= viewY() && ry + rowHeight <= viewY() + viewH();
    }

    @Override
    protected Box focusBox() {
        if (cursorInView()) {
            // The ring grows by 2 on each side: this lays it 1 px inside the row all round, so a selected face
            // frames it evenly and the well's border stays apart from it.
            return new Box("row", viewX() + 3, rowY(cursor.index) + 3, Math.max(0, viewW() - 6), Math.max(0, rowHeight - 6));
        }
        return super.focusBox();
    }

    /** Square on a row, which is square: round corners would leave the selected face's corners outside the ring. */
    @Override
    protected int focusCorner() {
        return cursorInView() ? 0 : super.focusCorner();
    }

    /** No ring while a row floats: the row it marked has left its place. */
    @Override
    protected boolean drawFocus(Draw d, Theme theme) {
        return dragged != null || super.drawFocus(d, theme);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (dragged != null || armed != null) {
            return List.of();
        }
        if (visible && active) {
            int i = indexAt(mx, my);
            if (i >= 0) {
                return rowTip(rows.get(i), mx, my);
            }
        }
        return super.tooltipAt(mx, my);
    }

    private List<String> rowTip(Entry<T> row, double mx, double my) {
        if (overDelete(row, mx) && !deleteTooltip.isEmpty()) {
            return deleteTooltip;
        }
        if (overHandle(mx) && isLocked(row.item) && !lockedTooltip.isEmpty()) {
            return lockedTooltip;
        }
        if (rowTooltip != null) {
            List<String> lines = rowTooltip.apply(row.item);
            if (lines != null && !lines.isEmpty()) {
                return lines;
            }
        }
        List<String> base = super.tooltipAt(mx, my);
        if (base != null && !base.isEmpty()) {
            return base;
        }
        if (itemTooltips && renderer == null && iconArt != null && iconArt.apply(row.item) instanceof ItemView view) {
            return itemTipShown ? List.of() : ItemArt.nameTip(view);
        }
        if (renderer == null && isCut(row)) {
            return List.of(textOf(row.item));
        }
        return base;
    }

    private boolean isCut(Entry<T> row) {
        int cx = contentX();
        if (lastTheme != null) {
            IconArt art = artOf(lastTheme, row.item);
            if (art != null) {
                cx += artWidth(art) + ICON_GAP;
            }
        }
        int right = viewX() + viewW() - (hasDelete(row.item) ? DELETE + 1 : 0);
        return TextMeasure.width(textOf(row.item)) > right - cx - 2;
    }

    private boolean accepts() {
        return visible && active && settled();
    }

    @Override
    public void tick() {
        super.tick();
        if (!visible || !active) {
            dropGestures();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!accepts() || button != 0 || !isOver(mx, my)) {
            return false;
        }
        if (dragged != null || armed != null) {
            return true;
        }
        if (overBar(mx, my)) {
            bar.press(mx, my, barX(), trackY(), trackH());
            return true;
        }
        int i = indexAt(mx, my);
        if (i < 0) {
            return true;
        }
        Entry<T> row = rows.get(i);
        if (overDelete(row, mx)) {
            heldDelete = row;
            lastClicked = null;
            return true;
        }
        BaseComponent.click();
        setCursor(row);
        choose(row);
        if (overHandle(mx) && isLocked(row.item)) {
            refused = row;
            refusedAt = clock().getAsLong();
        } else if (overHandle(mx)) {
            armed = row;
            armY = (int) my;
            lastClicked = null;
            return true;
        }
        long now = clock().getAsLong();
        boolean twice = lastClicked == row && now - lastClickAt <= DOUBLE_CLICK_MS;
        if (twice) {
            lastClicked = null;
            fire();
        } else {
            lastClicked = row;
            lastClickAt = now;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (bar.drag(my)) {
            return true;
        }
        if (armed != null && accepts() && Math.abs((int) my - armY) >= DRAG_SLOP) {
            lift(armed, (int) my);
        }
        if (dragged != null) {
            dragY = (int) my;
            aim();
            return true;
        }
        return armed != null || heldDelete != null;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) {
            return dragged != null;
        }
        boolean was = bar.dragging() || armed != null || heldDelete != null || dragged != null;
        bar.release();
        armed = null;
        if (dragged != null) {
            if (visible && active) {
                endDrag(true);
            } else {
                endDrag(false);
            }
        }
        Entry<T> held = heldDelete;
        heldDelete = null;
        if (held != null && accepts() && indexAt(mx, my) == held.index && overDelete(held, mx)) {
            delete(held);
        }
        return was;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || !settled() || !isOver(mx, my) || !overflows()) {
            return dragged != null;
        }
        clampScroll();
        bar.wheel(amount, ScrollBar.WHEEL_ROWS * rowHeight);
        if (dragged != null) {
            aim();
        }
        return true;
    }

    private void choose(Entry<T> row) {
        boolean changed = row != selected;
        selected = row;
        if (changed && onSelect != null) {
            onSelect.accept(row == null ? null : row.item);
        }
    }

    private void fire() {
        if (onActivate != null && cursor != null) {
            onActivate.accept(cursor.item);
        }
    }

    private void moveTo(int index) {
        Entry<T> row = rows.get(Math.max(0, Math.min(rows.size() - 1, index)));
        setCursor(row);
        choose(row);
    }

    private void delete(Entry<T> row) {
        int at = row.index;
        boolean wasSelected = row == selected;
        if (settling == row) {
            settling = null;
        }
        slide(() -> {
            rows.remove(at);
            for (int i = at; i < rows.size(); i++) {
                rows.get(i).index = i;
            }
            row.index = -1;
            clampScroll();
        });
        Entry<T> near = rows.isEmpty() ? null : rows.get(Math.min(at, rows.size() - 1));
        if (cursor == row) {
            cursor = near;
            if (near != null) {
                reveal(near.index);
            }
        }
        BaseComponent.click();
        if (wasSelected) {
            choose(near);
        }
        if (onDelete != null) {
            onDelete.accept(row.item, at);
        }
    }

    /** Moves the cursor's row by a slot, or to the far end, and reports the change. */
    private void moveCursorRow(int step, boolean toEnd) {
        if (isLocked(cursor.item)) {
            refused = cursor;
            refusedAt = clock().getAsLong();
            return;
        }
        int[] free = freeSlots();
        int a = Arrays.binarySearch(free, cursor.index);
        int b = toEnd ? (step < 0 ? 0 : free.length - 1) : a + step;
        if (b < 0 || b >= free.length || b == a) {
            return;
        }
        Entry<T> row = cursor;
        int from = row.index;
        List<T> before = items();
        slide(() -> rotate(free, a, b));
        reveal(row.index);
        if (before.equals(items())) {
            return;
        }
        BaseComponent.click();
        if (onReorder != null) {
            onReorder.changed(items(), row.item, from, row.index);
        }
    }

    private boolean moveKeyHeld() {
        if (heldKeys().ctrl()) {
            return true;
        }
        if (altHeld != null) {
            return altHeld.getAsBoolean();
        }
        long now = clock().getAsLong();
        if (now - altAt < ALT_LATCH_MS) {
            altAt = now;
            return true;
        }
        return false;
    }

    @Override
    protected boolean takesFocus() {
        return active;
    }

    @Override
    protected boolean navigable() {
        return visible && active && settled() && !rows.isEmpty();
    }

    @Override
    protected boolean activate() {
        if (!accepts() || cursor == null) {
            return false;
        }
        BaseComponent.click();
        choose(cursor);
        fire();
        return true;
    }

    @Override
    protected boolean capturesKeys() {
        return active;
    }

    /** Takes Escape during a drag and the page keys, and watches for Alt. Every other key goes on to {@link #keyPressed}. */
    @Override
    public boolean keyCaptured(KeyPress press) {
        if (press.kind() != KeyPress.Kind.KEY) {
            altAt = NEVER;
            return false;
        }
        int code = press.code();
        switch (code) {
            case RAW_ALT, RAW_RIGHT_ALT -> {
                altAt = clock().getAsLong();
                return false;
            }
            case RAW_ESCAPE -> {
                altAt = NEVER;
                if (dragged != null || armed != null) {
                    cancelDrag();
                    return true;
                }
                return false;
            }
            case RAW_PAGE_UP, RAW_PAGE_DOWN -> {
                if (!accepts() || rows.isEmpty() || dragged != null || armed != null || cursor == null) {
                    return dragged != null;
                }
                int page = Math.max(1, viewH() / rowHeight - 1);
                moveTo(cursor.index + (code == RAW_PAGE_UP ? -page : page));
                return true;
            }
            case RAW_UP, RAW_DOWN, RAW_HOME, RAW_END, RAW_SHIFT, RAW_CTRL, RAW_SUPER, RAW_RIGHT_SHIFT, RAW_RIGHT_CTRL, RAW_RIGHT_SUPER -> {
                return false;
            }
            default -> {
                altAt = NEVER;
                return false;
            }
        }
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (dragged != null || armed != null) {
            if (key == InputKey.ESCAPE) {
                cancelDrag();
            }
            return true;
        }
        if (!accepts() || rows.isEmpty()) {
            return false;
        }
        Entry<T> at = cursor == null ? rows.get(0) : cursor;
        cursor = at;
        switch (key) {
            case UP, DOWN -> {
                int step = key == InputKey.UP ? -1 : 1;
                if (moveKeyHeld()) {
                    moveCursorRow(step, false);
                } else {
                    moveTo(at.index + step);
                }
            }
            case HOME, END -> {
                int step = key == InputKey.HOME ? -1 : 1;
                if (moveKeyHeld()) {
                    moveCursorRow(step, true);
                } else {
                    moveTo(step < 0 ? 0 : rows.size() - 1);
                }
            }
            case DELETE -> {
                if (!hasDelete(at.item)) {
                    return false;
                }
                delete(at);
            }
            case ENTER, SPACE -> {
                return activate();
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
