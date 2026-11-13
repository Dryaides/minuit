package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.HostState;
import dev.dryaides.minuit.internal.Maths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A search field over a grid of the game's items; a click or Enter picks one. Plain words match the
 * name or the id, {@code @mod} keeps one namespace, {@code #tag} one item tag ({@code #logs} tries
 * {@code minecraft:} and {@code c:}). A sort button cycles game order, name and mod, plus any mode
 * the author adds with {@link #sortBy}; a second button flips the order. Both are remembered under the
 * picker's box name for the session (a store the host may put on disk).
 *
 * <p>For item list mods (JEI, REI, EMI): {@link #itemAt} answers the hovered item, {@link #dropTargets}
 * and {@link #acceptDrop} take an item dropped from their list.
 */
public class ItemPickerComponent extends BaseComponent implements ItemSource, ItemDropTarget {

    /** Where pickers keep their sort between openings, by picker name. */
    public interface SortStore {
        String get(String picker);

        void put(String picker, String sort);

        static SortStore memory() {
            Map<String, String> map = new ConcurrentHashMap<>();
            return new SortStore() {
                @Override
                public String get(String picker) {
                    return map.get(picker);
                }

                @Override
                public void put(String picker, String sort) {
                    map.put(picker, sort);
                }
            };
        }
    }

    public static final int CELL = 18;
    private static final int FIELD = 16;
    /** Between the search row and the grid: past a button's lip, which reads as part of the gap otherwise. */
    private static final int ROW_GAP = 4;
    private static final String REGISTRY = "registry";

    /** The picked id, or null. */
    public String selected;

    private final String name;
    private final Words words;
    private final ItemLookup lookup;
    private final Consumer<String> onPick;
    private final TextFieldComponent field;
    private final ButtonComponent sortButton;
    private final ButtonComponent orderButton;
    /** Sort modes by key, in the order the button cycles them; the value null is game order. */
    private final Map<String, Comparator<ItemView>> modes = new LinkedHashMap<>();
    private final Map<String, String> modeNames = new LinkedHashMap<>();
    private String mode = REGISTRY;
    private boolean descending;
    private boolean blocksOnly;
    private Predicate<String> keep = id -> true;
    private List<String> shown;
    private final ScrollBar bar = new ScrollBar();
    private FrameStyle frame = FrameStyles.VANILLA;
    /** Rows scrolled off the top. */
    private int scroll;
    /** The keyboard's cell in {@link #shown}, or -1 while the keyboard is in the field. */
    private int cursor = -1;
    private List<String> itemLines;

    public ItemPickerComponent(Box box, Words words, ItemLookup items, Consumer<String> onPick) {
        super(box);
        this.name = box.name();
        this.words = words;
        this.lookup = items;
        this.onPick = onPick;
        this.field = new TextFieldComponent(new Box("search", this.x, this.y, this.w - 2 * FIELD - 4, FIELD), "",
                text -> refilter()).placeholder(words.get("minuit.items.search"));
        this.sortButton = new ButtonComponent(new Box("sort", 0, this.y, FIELD, FIELD), "", this::nextMode);
        this.orderButton = new ButtonComponent(new Box("order", 0, this.y, FIELD, FIELD), "", this::flipOrder);
        this.modes.put(REGISTRY, null);
        this.modes.put("name", Comparator.comparing(v -> this.lookup.name(v.id()).toLowerCase(Locale.ROOT)));
        this.modes.put("mod", Comparator.comparing(v -> namespace(v.id())));
        this.modeNames.put(REGISTRY, words.get("minuit.items.sort.registry"));
        this.modeNames.put("name", words.get("minuit.items.sort.name"));
        this.modeNames.put("mod", words.get("minuit.items.sort.mod"));
        String saved = HostState.sorts().get(this.name);
        if (saved != null) {
            restore(saved);
        }
        place();
    }

    /** Over the items the game registered ({@link ItemLookup#game()}). */
    public ItemPickerComponent(Box box, Words words, Consumer<String> onPick) {
        this(box, words, ItemLookup.game(), onPick);
    }

    /** Only items that place a block. */
    public ItemPickerComponent blocksOnly() {
        this.blocksOnly = true;
        this.shown = null;
        return this;
    }

    public ItemPickerComponent filter(Predicate<String> keep) {
        this.keep = keep;
        this.shown = null;
        return this;
    }

    public ItemPickerComponent selected(String id) {
        this.selected = id;
        return this;
    }

    /**
     * Adds a sort mode after the built-in ones: by count in a storage mod, by price in a shop. A mode
     * saved under this {@code key} comes back once it is added. Ties keep game order.
     *
     * @param label the mode's name in the player's language, for the button's tooltip
     */
    public ItemPickerComponent sortBy(String key, String label, Comparator<ItemView> order) {
        this.modes.put(key, order);
        this.modeNames.put(key, label);
        String saved = HostState.sorts().get(this.name);
        if (saved != null) {
            restore(saved);
        }
        this.shown = null;
        return this;
    }

    /** The ids that match the search now, in the order shown. */
    public List<String> shown() {
        if (this.shown == null) {
            refilter();
        }
        return this.shown;
    }

    public String query() {
        return this.field.text();
    }

    public void search(String query) {
        this.field.setText(query);
        refilter();
    }

    /** The item in the cell at the mouse, or null: what JEI, REI and EMI ask for their keys. */
    public ItemView itemAt(double mx, double my) {
        int index = indexAt(mx, my);
        return index < 0 ? null : ItemView.of(shown().get(index));
    }

    /** Where an item dragged from another mod's list may land: the grid. */
    public List<ItemDropTarget.Drop> dropTargets() {
        return this.visible && this.active ? List.of(new ItemDropTarget.Drop(gridBox(), item -> allowed(item.id())))
                : List.of();
    }

    /** An item dropped from outside; picked when this picker could show it, as if clicked. */
    public boolean acceptDrop(ItemView item) {
        if (!this.visible || !this.active || item == null || !allowed(item.id())) {
            return false;
        }
        pick(item.id());
        return true;
    }

    // --- layout --------------------------------------------------------------------------------

    private void place() {
        this.field.x = this.x;
        this.field.y = this.y;
        this.field.w = this.w - 2 * FIELD - 8;
        this.sortButton.x = this.x + this.w - 2 * FIELD - 4;
        this.orderButton.x = this.x + this.w - FIELD;
        this.sortButton.y = this.y;
        this.orderButton.y = this.y;
    }

    private Box gridBox() {
        return new Box("grid", this.x, this.y + FIELD + ROW_GAP, this.w, this.h - FIELD - ROW_GAP);
    }

    /** The well drawn round the columns: as wide as they are plus the family's scroll bar, so no empty band sits before it. */
    private Box wellBox(FrameStyle frame) {
        Box grid = gridBox();
        return new Box("well", grid.x(), grid.y(), Math.min(grid.w(), columns() * CELL + 4 + ScrollBar.room(frame)), grid.h());
    }

    /** As many cells as fit beside the 2 px rims and the widest scroll bar (a family with no gap before it). */
    private int columns() {
        return Math.max(1, (this.w - 4 - ScrollBar.WIDTH - 1) / CELL);
    }

    int scrolledRows() {
        return this.scroll;
    }

    private void syncBar() {
        this.bar.content(maxScroll() + visibleRows(), visibleRows());
        this.bar.offset(this.scroll);
    }

    private int barX(FrameStyle frame) {
        Box grid = gridBox();
        return ScrollBar.wellX(frame, grid.x(), grid.w());
    }

    private int visibleRows() {
        return Math.max(1, (this.h - FIELD - ROW_GAP - 4) / CELL);
    }

    private int maxScroll() {
        int rows = (shown().size() + columns() - 1) / columns();
        return Math.max(0, rows - visibleRows());
    }

    private int indexAt(double mx, double my) {
        Box grid = gridBox();
        double lx = mx - grid.x() - 2;
        double ly = my - grid.y() - 2;
        if (!this.visible || lx < 0 || ly < 0 || lx >= columns() * CELL || ly >= grid.h() - 4) {
            return -1;
        }
        int index = ((int) ly / CELL + this.scroll) * columns() + (int) lx / CELL;
        return index < shown().size() ? index : -1;
    }

    // --- search and sort -----------------------------------------------------------------------

    private boolean allowed(String id) {
        return (!this.blocksOnly || this.lookup.blocks().contains(id)) && this.keep.test(id);
    }

    private void refilter() {
        List<String> source = this.blocksOnly ? this.lookup.blocks() : this.lookup.items();
        String[] words = this.field.text().toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<String> out = new ArrayList<>();
        for (String id : source) {
            if (this.keep.test(id) && matches(id, words)) {
                out.add(id);
            }
        }
        Comparator<ItemView> order = this.modes.get(this.mode);
        if (order != null) {
            out.sort((a, b) -> order.compare(ItemView.of(a), ItemView.of(b)));
        }
        if (this.descending) {
            java.util.Collections.reverse(out);
        }
        this.shown = out;
        this.scroll = Math.min(this.scroll, maxScroll());
        if (this.cursor >= out.size()) {
            this.cursor = out.isEmpty() ? -1 : out.size() - 1;
        }
    }

    private boolean matches(String id, String[] words) {
        String name = null;
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (word.charAt(0) == '@') {
                if (!namespace(id).contains(word.substring(1))) {
                    return false;
                }
            } else if (word.charAt(0) == '#') {
                String tag = word.substring(1);
                boolean in = tag.indexOf(':') >= 0 ? this.lookup.tagged(id, tag)
                        : this.lookup.tagged(id, "minecraft:" + tag) || this.lookup.tagged(id, "c:" + tag);
                if (!in) {
                    return false;
                }
            } else {
                if (name == null) {
                    name = this.lookup.name(id).toLowerCase(Locale.ROOT);
                }
                if (!name.contains(word) && !id.contains(word)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    private void nextMode() {
        List<String> keys = new ArrayList<>(this.modes.keySet());
        this.mode = keys.get((keys.indexOf(this.mode) + 1) % keys.size());
        sorted();
    }

    private void flipOrder() {
        this.descending = !this.descending;
        sorted();
    }

    private void sorted() {
        HostState.sorts().put(this.name, this.mode + (this.descending ? ":down" : ":up"));
        this.scroll = 0;
        refilter();
    }

    private void restore(String saved) {
        int colon = saved.lastIndexOf(':');
        String key = colon < 0 ? saved : saved.substring(0, colon);
        if (this.modes.containsKey(key)) {
            this.mode = key;
            this.descending = saved.endsWith(":down");
        }
    }

    private void pick(String id) {
        click();
        this.selected = id;
        this.onPick.accept(id);
    }

    // --- drawing -------------------------------------------------------------------------------

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        place();
        if (!this.focused) {
            this.field.focused = false;
            this.cursor = -1;
        }
        String modeName = this.modeNames.get(this.mode);
        this.sortButton.label = modeName.isEmpty() ? "?" : modeName.substring(0, 1).toUpperCase(Locale.ROOT);
        this.sortButton.tooltip(this.words.get("minuit.items.sort", modeName));
        this.orderButton.label = this.descending ? "-" : "+";
        this.orderButton.tooltip(this.words.get(this.descending ? "minuit.items.order.down" : "minuit.items.order.up"));
        this.field.draw(d, theme, mx, my, this.delta);
        this.sortButton.draw(d, theme, mx, my, this.delta);
        this.orderButton.draw(d, theme, mx, my, this.delta);
        Box grid = wellBox(theme.frame());
        theme.frame().well(d, p, grid.x(), grid.y(), grid.w(), grid.h(), this.focused && this.cursor >= 0);
        List<String> items = shown();
        if (items.isEmpty()) {
            d.centered(this.words.get("minuit.items.none"), grid.x() + grid.w() / 2, grid.y() + (grid.h() - 8) / 2,
                    p.textFaint());
            this.itemLines = null;
            return;
        }
        int over = hovered ? indexAt(mx, my) : -1;
        int cols = columns();
        d.scissor(grid.x() + 1, grid.y() + 1, grid.x() + grid.w() - 1, grid.y() + grid.h() - 1);
        // The row cut by the grid's bottom rim is drawn too, clipped: it shows that the list goes on.
        for (int row = 0; row <= visibleRows(); row++) {
            for (int col = 0; col < cols; col++) {
                int index = (row + this.scroll) * cols + col;
                if (index >= items.size()) {
                    break;
                }
                String id = items.get(index);
                int cx = grid.x() + 2 + col * CELL;
                int cy = grid.y() + 2 + row * CELL;
                boolean lit = index == over || (this.focused && index == this.cursor);
                theme.frame().slot(d, p, cx, cy, lit, id.equals(this.selected));
                d.item(ItemView.of(id), cx + 1, cy + 1);
            }
        }
        d.endScissor();
        // Rows are the bar's unit; the family's own scroller draws it, as in every other scrolling area.
        this.frame = theme.frame();
        syncBar();
        int barX = barX(this.frame);
        boolean lit = this.bar.dragging() || this.active && this.bar.overThumb(mx, my, barX, grid.y() + 2, grid.h() - 4);
        this.bar.draw(d, theme, barX, grid.y() + 2, grid.h() - 4, lit);
        // The game's lines go to tooltipAt, so the page's TipDelay draws them in the theme's popup.
        this.itemLines = over < 0 ? null : d.itemTooltipLines(ItemView.of(items.get(over)));
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (this.sortButton.isOver(mx, my)) {
            return this.sortButton.tooltip;
        }
        if (this.orderButton.isOver(mx, my)) {
            return this.orderButton.tooltip;
        }
        int index = indexAt(mx, my);
        if (index < 0) {
            return super.tooltipAt(mx, my);
        }
        if (this.itemLines != null) {
            return this.itemLines;
        }
        String name = this.lookup.name(shown().get(index));
        return name == null || name.isBlank() ? null : List.of(name);
    }

    // --- input ---------------------------------------------------------------------------------

    @Override
    protected boolean takesFocus() {
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !isOver(mx, my)) {
            return false;
        }
        if (this.sortButton.mouseClicked(mx, my, button) || this.orderButton.mouseClicked(mx, my, button)) {
            return true;
        }
        if (this.field.isOver(mx, my)) {
            this.cursor = -1;
            this.field.focused = true;
            return this.field.mouseClicked(mx, my, button);
        }
        Box grid = gridBox();
        syncBar();
        if (button == 0 && this.bar.press(mx, my, barX(this.frame), grid.y() + 2, grid.h() - 4)) {
            this.scroll = this.bar.offset();
            return true;
        }
        int index = indexAt(mx, my);
        if (index >= 0 && button == 0) {
            this.cursor = index;
            this.field.focused = false;
            pick(shown().get(index));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (this.bar.dragging()) {
            this.bar.release();
            return true;
        }
        this.sortButton.mouseReleased(mx, my, button);
        this.orderButton.mouseReleased(mx, my, button);
        return this.field.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.bar.drag(my)) {
            this.scroll = this.bar.offset();
            return true;
        }
        return this.field.focused && this.field.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int next = Maths.clamp(this.scroll - (int) Math.signum(amount) * 3, 0, maxScroll());
        boolean moved = next != this.scroll;
        this.scroll = next;
        return moved || isOver(mx, my);
    }

    @Override
    protected void enter(boolean forward) {
        this.cursor = -1;
        this.field.focused = true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        List<String> items = shown();
        int cols = columns();
        if (this.cursor < 0) {
            this.field.focused = this.focused;
            if (key == InputKey.DOWN && !items.isEmpty()) {
                moveCursor(0);
                return true;
            }
            return key != InputKey.ESCAPE && key != InputKey.TAB && this.field.keyPressed(key, shift);
        }
        switch (key) {
            case LEFT -> moveCursor(Math.max(0, this.cursor - 1));
            case RIGHT -> moveCursor(Math.min(items.size() - 1, this.cursor + 1));
            case DOWN -> moveCursor(Math.min(items.size() - 1, this.cursor + cols));
            case UP -> {
                if (this.cursor < cols) {
                    this.cursor = -1;
                    this.field.focused = true;
                } else {
                    moveCursor(this.cursor - cols);
                }
            }
            case ENTER, SPACE -> pick(items.get(this.cursor));
            default -> {
                return false;
            }
        }
        return true;
    }

    private void moveCursor(int index) {
        this.cursor = index;
        this.field.focused = false;
        int row = index / columns();
        if (row < this.scroll) {
            this.scroll = row;
        } else if (row >= this.scroll + visibleRows()) {
            this.scroll = row - visibleRows() + 1;
        }
    }

    @Override
    public boolean charTyped(int codepoint) {
        this.cursor = -1;
        this.field.focused = true;
        return this.field.charTyped(codepoint);
    }
}
