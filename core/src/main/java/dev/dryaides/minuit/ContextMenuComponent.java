package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A right-click menu: actions with an icon and a shortcut hint, separators, section titles, checkable
 * items and nested submenus. It opens at the pointer, flips to stay inside its area, and scrolls when
 * it is taller than the area.
 *
 * <pre>{@code
 * public class FilesPanel extends PanelComponent {
 *     public FilesPanel(Theme theme, Words words, int left, int top) {
 *         super(theme, words, left, top);
 *         TreeComponent<File> files = add(new TreeComponent<>(...));
 *         // Last, so that it is drawn on top and offered every input first.
 *         ContextMenuComponent menu = add(new ContextMenuComponent(bounds(), this)
 *                 .on(files)
 *                 .itemsAt((x, y) -> List.of(
 *                         Item.title(files.rowAt(x, y).getName()),
 *                         Item.action("Open", this::open).icon(Icon.FOLDER).shortcut("Enter"),
 *                         Item.separator(),
 *                         Item.check("Show hidden", hidden, on -> hidden = on),
 *                         Item.submenu("Sort by", Item.action("Name", this::byName), Item.action("Date", this::byDate)),
 *                         Item.action("Delete", this::delete).icon(Icon.TRASH))));
 *         // Greys "Delete" for as long as there is nothing to delete; the tip says why.
 *         menu.disabled("Delete", !canDelete, "Nothing selected");
 *     }
 * }
 * }</pre>
 *
 * <h2>Putting it in a panel</h2>
 * Add the menu as a normal widget, <b>after every other widget</b> (only a dialog may come later, and
 * it then blocks the menu). {@link #open(int, int)} throws otherwise. The box is the area the menu has
 * to stay in, in the panel's coordinates: pass the screen's box for a menu that may leave the panel.
 *
 * <p>A right click in a region given to {@link #on(Box)} or {@link #on(BaseComponent)} opens the menu.
 * Other clicks there go to the widgets beneath as usual. {@link #openBelow(Box)} is for a button or a
 * key: it opens under a box with the first item lit.
 *
 * <h2>Input while open</h2>
 * The menu takes all input until it closes, and gives the panel's focus back when it does. The first
 * click outside only closes it (a right click in a region reopens it at the new place), so a click
 * meant to dismiss the menu presses nothing beneath.
 * <ul>
 * <li>Up and down move and wrap, home and end jump; disabled items, titles and separators are skipped.</li>
 * <li>Right, Enter or Space open a submenu and go in. Left and Escape come back one level. Escape in
 * the first level and Tab close the menu.</li>
 * <li>Enter or Space run an item. Typing moves to the next item starting with those letters, ignoring
 * case and accents; the letters are forgotten after a pause, and repeating one letter cycles.</li>
 * <li>Hovering an item with a submenu opens it after a short wait. The wait to leave one is longer, so
 * the pointer can cross other rows on its way in.</li>
 * </ul>
 *
 * <p>{@code active = false} or {@code visible = false} closes an open menu and lets input through. A
 * panel's {@code drawForeground} and its {@code overlay} draw above the menu, and opening closes that
 * overlay for this reason. A container screen draws its slot items over the panel's widgets, so they
 * show above the menu there. A panel also draws its Tab ring after every widget, so a menu opened by a
 * program over a widget ringed that way has the ring on it, and the widgets beneath still show their
 * hover, because a panel gives them the real pointer position.
 */
public final class ContextMenuComponent extends BaseComponent {
    /** What the menu holds for a click at a point: read each time it opens. Null or empty means nothing to show. */
    @FunctionalInterface
    public interface ItemsAt {
        List<Item> at(int x, int y);
    }

    /** One row of a menu. The setters may be called while the menu is open, except that widths stay as measured. */
    public static final class Item {
        private enum Kind { ACTION, CHECK, SUBMENU, SEPARATOR, TITLE }

        private final Kind kind;
        private final String label;
        private final Runnable onPress;
        private final Consumer<Boolean> onToggle;
        private final List<Item> children;
        private Icon icon;
        private IconArt iconArt;
        private String shortcut = "";
        private List<String> tooltip;
        /** The disabled reason, kept apart so re-enabling gives the item's own tooltip back. */
        private List<String> reason;
        private boolean enabled = true;
        private boolean checked;
        private boolean keepOpen;
        private String folded;

        private Item(Kind kind, String label, Runnable onPress, Consumer<Boolean> onToggle, List<Item> children) {
            this.kind = kind;
            this.label = label == null ? "" : label;
            this.onPress = onPress;
            this.onToggle = onToggle;
            this.children = children;
        }

        /** Runs the callback, which may be null, after the menu has closed. */
        public static Item action(String label, Runnable onPress) {
            return new Item(Kind.ACTION, label, onPress, null, List.of());
        }

        /** An item with a check mark. A press flips it and passes the new value. The callback may be null. */
        public static Item check(String label, boolean on, Consumer<Boolean> onToggle) {
            Item item = new Item(Kind.CHECK, label, null, onToggle, List.of());
            item.checked = on;
            return item;
        }

        /** An item that opens another menu. With no children it reads as disabled. */
        public static Item submenu(String label, Item... children) {
            return submenu(label, Arrays.asList(children));
        }

        /** An item that opens another menu. With no children it reads as disabled. */
        public static Item submenu(String label, List<Item> children) {
            return new Item(Kind.SUBMENU, label, null, null, children == null ? List.of() : new ArrayList<>(children));
        }

        /** A thin line. One at either end of a menu, or right after another, is not drawn. */
        public static Item separator() {
            return new Item(Kind.SEPARATOR, "", null, null, List.of());
        }

        /** The name of a section: quieter text that cannot be picked. */
        public static Item title(String label) {
            return new Item(Kind.TITLE, label, null, null, List.of());
        }

        /** A theme icon before the label. */
        public Item icon(Icon icon) {
            this.icon = icon;
            return this;
        }

        /** Your own picture before the label, instead of a theme icon. */
        public Item iconArt(IconArt iconArt) {
            this.iconArt = iconArt;
            return this;
        }

        /** Text at the right edge, such as {@code "Ctrl+C"}. It is only a hint: the key is yours to handle. A submenu's arrow takes its place. */
        public Item shortcut(String shortcut) {
            this.shortcut = shortcut == null ? "" : shortcut;
            return this;
        }

        Item enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        Item reason(String... lines) {
            this.reason = lines == null || lines.length == 0 ? null : List.of(lines);
            return this;
        }

        /** Lines for the pointer on this row. A label cut short shows in full without this. */
        public Item tooltip(String... lines) {
            this.tooltip = lines == null || lines.length == 0 ? null : List.of(lines);
            return this;
        }

        /** Stays open when pressed, to flip several checks in a row. */
        public Item keepOpen() {
            this.keepOpen = true;
            return this;
        }

        /** Sets the check mark without calling back. */
        public Item checked(boolean checked) {
            this.checked = checked;
            return this;
        }

        public boolean isChecked() {
            return checked;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String label() {
            return label;
        }

        private String folded() {
            if (folded == null) {
                folded = TextMatch.fold(label).text;
            }
            return folded;
        }

        private boolean pickable() {
            return enabled && (kind == Kind.ACTION || kind == Kind.CHECK || (kind == Kind.SUBMENU && !children.isEmpty()));
        }

        private boolean greyed() {
            return !enabled || (kind == Kind.SUBMENU && children.isEmpty());
        }

        private boolean hasIcon() {
            return icon != null || iconArt != null;
        }

        private IconArt art(Theme theme) {
            return iconArt != null ? iconArt : icon != null ? theme.icon(icon) : null;
        }

        /** The height of an action's row in the level now open: 18 when the level shows a 16 px item. */
        private int lineH = ROW;

        private int height() {
            return switch (kind) {
                case SEPARATOR -> SEPARATOR_H;
                case TITLE -> DropdownComponent.HEADER;
                default -> lineH;
            };
        }
    }

    /** One open menu: the first, or a submenu. */
    private static final class Level {
        final List<Item> items;
        final int parentRow;
        final int[] tops;
        final int contentH;
        final Glide glide = new Glide(Slide.DOWN, OPEN_MS);
        boolean revealsUp;
        int x;
        int y;
        int w;
        int h;
        int checkW;
        int iconW;
        int sideW;
        Insets inset = new Insets(0, 0, 0, 0);
        FrameStyle frame = FrameStyles.VANILLA;
        final ScrollBar bar = new ScrollBar();
        int cursor = -1;

        Level(List<Item> items, int parentRow, Theme theme) {
            this.items = items;
            this.parentRow = parentRow;
            this.tops = new int[items.size()];
            // One row height per level: a 16 px item takes a row of 18, a pixel above and below it.
            int rowH = ROW;
            for (Item item : items) {
                if (item.hasIcon()) {
                    rowH = Math.max(rowH, Math.min(ART_MAX, item.art(theme).height()) + 2);
                }
            }
            for (Item item : items) {
                item.lineH = rowH;
            }
            int top = 0;
            for (int i = 0; i < tops.length; i++) {
                tops[i] = top;
                top += items.get(i).height();
            }
            this.contentH = top;
        }

        int viewY() { return y + inset.top(); }
        int viewH() { return Math.max(0, h - inset.top() - inset.bottom()); }
        boolean overflows() { return contentH > viewH(); }
        int rowX() { return x + inset.left(); }
        int rowW() { return Math.max(0, w - inset.left() - inset.right() - (overflows() ? ScrollBar.room(frame) : 0)); }
        int barX() { return ScrollBar.x(frame, x + w - inset.right()); }
        int rowY(int row) { return viewY() + tops[row] - bar.offset(); }

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        boolean overBar(double mx, double my) {
            return overflows() && mx >= barX() && mx < barX() + ScrollBar.WIDTH && my >= viewY() && my < viewY() + viewH();
        }

        int rowAt(double mx, double my) {
            if (mx < rowX() || mx >= rowX() + rowW() || my < viewY() || my >= viewY() + viewH()) {
                return -1;
            }
            int inner = (int) Math.floor(my) - viewY() + bar.offset();
            for (int i = 0; i < tops.length; i++) {
                if (inner >= tops[i] && inner < tops[i] + items.get(i).height()) {
                    return i;
                }
            }
            return -1;
        }

        void reveal(int row) {
            int top = tops[row];
            int bottom = top + items.get(row).height();
            // A title right above the first item comes along when the list goes back to the top.
            if (top < bar.offset()) {
                bar.offset(row <= 1 ? 0 : top);
            } else if (bottom > bar.offset() + viewH()) {
                bar.offset(bottom - viewH());
            }
        }

        int step(int from, int by) {
            int count = items.size();
            int at = from;
            for (int i = 0; i < count; i++) {
                at = Math.floorMod(at + by, count);
                if (items.get(at).pickable()) {
                    return at;
                }
            }
            return -1;
        }

        int first() { return step(-1, 1); }
        int last() { return step(0, -1); }
    }

    /** Height of a row of a menu, the same as the rows of a dropdown's list. */
    private static final int ROW = RowLook.POPUP_ROW;
    /** Art taller than an item is shrunk to 16 (by n/gs, crisp). */
    private static final int ART_MAX = 16;
    private static final int SEPARATOR_H = 5;
    private static final int MARGIN = 2;
    /** A panel draws its keyboard ring after every widget, 2 outside the box: an opener's ring must not land on the menu. */
    private static final int RING_ROOM = 2;
    private static final int LEFT = RowLook.POPUP_PAD;
    private static final int COLUMN_GAP = 3;
    private static final int RIGHT = RowLook.POPUP_PAD;
    private static final int SIDE_GAP = 10;
    private static final int MIN_WIDTH = 60;
    private static final int OVERLAP = 1;
    private static final int OPEN_MS = 110;
    private static final int HOVER_OPEN_MS = 160;
    private static final int HOVER_SWITCH_MS = 280;
    private static final int TYPE_RESET_MS = 700;
    private static final int TRAIL_ALPHA = 0x80;
    private static final int RULE_ALPHA = 0x40;
    private static final float DISABLED_FADE = 0.45F;
    private static final double READABLE_DISABLED = 2.2D;

    private final PanelComponent host;
    private final List<BiPredicate<Double, Double>> regions = new ArrayList<>();
    private ItemsAt source = (px, py) -> List.of();
    private final Map<String, Veto> overrides = new HashMap<>();
    private String emptyText;
    private int maxWidth = 220;
    private int maxHeight = Integer.MAX_VALUE;
    private Runnable onClose;

    private final List<Level> levels = new ArrayList<>();
    private BaseComponent focusBefore;
    private boolean open;
    /** Set by a widget that opens this menu for its own parts: false closes it, as hiding it does. */
    private BooleanSupplier holder;
    private int current;
    private boolean keyed;

    private int lastMx;
    private int lastMy;
    private boolean pointerKnown;
    private int pendingLevel = -1;
    private int pendingRow = -1;
    private long pendingAt;
    private int pendingWait;

    private Level dragged;

    private String typed = "";
    private long typedAt;

    /**
     * @param area the box the menu stays in, in the panel's coordinates; {@code PanelComponent.bounds()} or the screen's box
     * @param host the panel this menu is added to
     */
    public ContextMenuComponent(Box area, PanelComponent host) {
        super(area);
        this.host = Objects.requireNonNull(host, "host");
    }

    /** The same items wherever it opens. */
    public ContextMenuComponent items(Item... items) {
        return items(Arrays.asList(items));
    }

    /** The same items wherever it opens. */
    public ContextMenuComponent items(List<Item> items) {
        List<Item> copy = items == null ? List.of() : new ArrayList<>(items);
        source = (px, py) -> copy;
        return this;
    }

    /**
     * Greys out the item whose label is {@code id}, at any depth, or makes it pickable again: drawn disabled, skipped
     * by clicks and by the keyboard. The {@code tip} lines show over it, to say why; none keeps its own tooltip. It holds for
     * items a source builds each time the menu opens, and for a menu that is already open.
     */
    public ContextMenuComponent disabled(String id, boolean disabled, String... tip) {
        Objects.requireNonNull(id, "id");
        Veto o = new Veto(disabled, tip);
        overrides.put(id, o);
        for (Level l : levels) {
            for (Item item : l.items) {
                if (item.label.equals(id)) {
                    o.apply(item);
                }
            }
        }
        return this;
    }

    /** Items worked out when it opens, for what is under the pointer. */
    public ContextMenuComponent itemsAt(ItemsAt source) {
        this.source = Objects.requireNonNull(source, "source");
        return this;
    }

    /** A right click in this box opens the menu. Can be called several times. */
    public ContextMenuComponent on(Box region) {
        Objects.requireNonNull(region, "region");
        regions.add(region::contains);
        return this;
    }

    /** A right click on this widget opens the menu, while the widget is visible. Can be called several times. */
    public ContextMenuComponent on(BaseComponent widget) {
        Objects.requireNonNull(widget, "widget");
        regions.add((mx, my) -> widget.visible && widget.isOver(mx, my));
        return this;
    }

    /** A row of this text shows when there are no items. Without it, such a menu does not open at all. */
    public ContextMenuComponent emptyText(String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    /** A cap on each menu, on top of the area it has to fit in. 220 wide by default, and as tall as the area. */
    public ContextMenuComponent maxSize(int width, int height) {
        maxWidth = Math.max(1, width);
        maxHeight = Math.max(1, height);
        return this;
    }

    /** Runs whenever the menu closes, for any reason, before the callback of an item that was pressed. */
    public ContextMenuComponent onClose(Runnable onClose) {
        this.onClose = onClose;
        return this;
    }

    @Override
    public ContextMenuComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /**
     * Opens with its corner at a point, nothing lit, flipping to the other side of the point if it would not fit.
     *
     * @return false when there is nothing to show, or the menu is hidden or disabled
     * @throws IllegalStateException if the panel does not hold the menu, or if another kind of widget comes after it
     */
    public boolean open(int px, int py) {
        Level level = begin(px, py);
        if (level == null) {
            return false;
        }
        place(level, px, py);
        return true;
    }

    /** Opens under a box with the first item lit, or above the box if it would not fit; for a button or a key. */
    public boolean openBelow(Box anchor) {
        Level level = begin(anchor.x(), anchor.bottom());
        if (level == null) {
            return false;
        }
        placeBelow(level, anchor);
        keyed = true;
        moveCursor(level, level.first());
        return true;
    }

    private Level begin(int px, int py) {
        checkPlace();
        closeSilently();
        if (!visible || !active) {
            return null;
        }
        List<Item> items = tidy(source.at(px, py));
        if (items.isEmpty()) {
            if (emptyText == null) {
                return null;
            }
            items = List.of(Item.title(emptyText));
        }
        host.closeOverlay();
        BaseComponent before = host.focus();
        focusBefore = before == this ? null : before;
        open = true;
        current = 0;
        keyed = false;
        pointerKnown = false;
        typed = "";
        Level level = build(items, -1);
        levels.add(level);
        host.focus(this);
        return level;
    }

    /** Closes the whole menu without running anything but {@link #onClose}, and gives the panel's focus back. */
    public void close() {
        if (open) {
            closeSilently();
            if (onClose != null) {
                onClose.run();
            }
        }
    }

    private void closeSilently() {
        if (!open) {
            return;
        }
        open = false;
        levels.clear();
        dragged = null;
        clearPending();
        if (host.focus() == this) {
            host.focus(focusBefore);
        }
        focusBefore = null;
    }

    public boolean isOpen() {
        return open;
    }

    /** How many menus are open: 0 when closed, 1 with no submenu. */
    public int depth() {
        return levels.size();
    }

    /** The place of an open menu, 0 being the first. */
    Box levelBox(int level) {
        Level l = levels.get(level);
        return new Box("level" + level, l.x, l.y, l.w, l.h);
    }

    /** The place of a row of an open menu, before the view's cut, for a script or a test. */
    Box rowBox(int level, int row) {
        Level l = levels.get(level);
        return new Box("row" + row, l.rowX(), l.rowY(row), l.rowW(), l.items.get(row).height());
    }

    /** The label of the item lit in the menu that has the keyboard, or null. */
    public String currentLabel() {
        if (!open) {
            return null;
        }
        Level l = levels.get(current);
        return l.cursor < 0 ? null : l.items.get(l.cursor).label;
    }

    /** How far an open menu has scrolled. */
    int scroll(int level) {
        return levels.get(level).bar.offset();
    }

    private void checkPlace() {
        List<BaseComponent> widgets = host.widgets();
        int at = widgets.indexOf(this);
        if (at < 0) {
            throw new IllegalStateException("Add the menu to the panel before opening it");
        }
        for (int i = at + 1; i < widgets.size(); i++) {
            BaseComponent later = widgets.get(i);
            if (!(later instanceof ContextMenuComponent) && !(later instanceof DialogComponent)) {
                throw new IllegalStateException("A context menu must come after every other widget of its panel");
            }
        }
    }

    /** Drops nulls, and separators at the ends or next to each other: a list built from conditions often has them. */
    private static final class Veto {
        final boolean disabled;
        final String[] tip;

        Veto(boolean disabled, String[] tip) {
            this.disabled = disabled;
            this.tip = tip;
        }

        void apply(Item item) {
            item.enabled(!disabled).reason(disabled ? tip : null);
        }
    }

    private List<Item> tidy(List<Item> items) {
        List<Item> out = new ArrayList<>();
        if (items == null) {
            return out;
        }
        for (Item item : items) {
            if (item == null || (item.kind == Item.Kind.SEPARATOR && (out.isEmpty() || out.get(out.size() - 1).kind == Item.Kind.SEPARATOR))) {
                continue;
            }
            Veto o = overrides.get(item.label);
            if (o != null && item.kind != Item.Kind.SEPARATOR && item.kind != Item.Kind.TITLE) {
                o.apply(item);
            }
            out.add(item);
        }
        if (!out.isEmpty() && out.get(out.size() - 1).kind == Item.Kind.SEPARATOR) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private Level build(List<Item> items, int parentRow) {
        Theme theme = host.theme();
        Level l = new Level(items, parentRow, theme);
        l.frame = theme.frame();
        l.inset = PopupList.inset(l.frame);
        int labelW = 0;
        int plainW = 0;
        for (Item item : items) {
            if (item.kind == Item.Kind.CHECK) {
                l.checkW = Math.max(l.checkW, theme.icon(Icon.CHECK).width());
            }
            if (item.hasIcon()) {
                l.iconW = Math.max(l.iconW, Math.min(ART_MAX, item.art(theme).width()));
            }
            if (item.kind == Item.Kind.SUBMENU) {
                l.sideW = Math.max(l.sideW, theme.icon(Icon.NEXT).width());
            } else if (!item.shortcut.isEmpty() && item.kind != Item.Kind.TITLE) {
                l.sideW = Math.max(l.sideW, TextMeasure.width(item.shortcut));
            }
            if (item.kind == Item.Kind.TITLE) {
                plainW = Math.max(plainW, TextMeasure.width(item.label));
            } else {
                labelW = Math.max(labelW, TextMeasure.width(item.label));
            }
        }
        int fitW = Math.max(1, Math.min(maxWidth, w - 2 * MARGIN));
        int fitH = Math.max(1, Math.min(maxHeight, h - 2 * MARGIN));
        int across = l.inset.left() + l.inset.right();
        int down = l.inset.top() + l.inset.bottom();
        l.h = Math.min(fitH, l.contentH + down);
        l.bar.content(l.contentH, l.viewH());
        int bar = l.contentH + down > fitH ? ScrollBar.room(l.frame) : 0;
        int body = Math.max(columns(l) + labelW + (l.sideW > 0 ? SIDE_GAP + l.sideW : 0), plainW) + LEFT + RIGHT;
        l.w = Math.min(fitW, Math.max(MIN_WIDTH, body + across + bar));
        return l;
    }

    /** What sits before a label: the check column and the icon column, each with its gap, when the menu has any. */
    private static int columns(Level l) {
        return (l.checkW > 0 ? l.checkW + COLUMN_GAP : 0) + (l.iconW > 0 ? l.iconW + COLUMN_GAP : 0);
    }

    private void place(Level l, int px, int py) {
        int left = x + MARGIN;
        int top = y + MARGIN;
        int right = x + w - MARGIN;
        int bottom = y + h - MARGIN;
        int at = px;
        if (at + l.w > right) {
            at = px - l.w >= left ? px - l.w : right - l.w;
        }
        int down = py;
        if (down + l.h > bottom) {
            if (py - l.h >= top) {
                down = py - l.h;
                l.revealsUp = true;
            } else {
                down = bottom - l.h;
            }
        }
        l.x = Math.max(left, at);
        l.y = Math.max(top, down);
        start(l);
    }

    private void placeBelow(Level l, Box anchor) {
        int top = y + MARGIN;
        int bottom = y + h - MARGIN;
        int down = anchor.bottom() + RING_ROOM;
        if (down + l.h > bottom) {
            if (anchor.y() - RING_ROOM - l.h >= top) {
                down = anchor.y() - RING_ROOM - l.h;
                l.revealsUp = true;
            } else {
                down = bottom - l.h;
            }
        }
        l.x = Math.max(x + MARGIN, Math.min(anchor.x(), x + w - MARGIN - l.w));
        l.y = Math.max(top, down);
        start(l);
    }

    private void placeChild(Level parent, Level child, int row) {
        int left = x + MARGIN;
        int top = y + MARGIN;
        int right = x + w - MARGIN;
        int bottom = y + h - MARGIN;
        int onRight = parent.x + parent.w - OVERLAP;
        int onLeft = parent.x - child.w + OVERLAP;
        int at;
        if (onRight + child.w <= right) {
            at = onRight;
        } else if (onLeft >= left) {
            at = onLeft;
        } else {
            at = right - onRight >= onLeft + child.w - left ? right - child.w : left;
        }
        child.x = Math.max(left, Math.min(at, right - child.w));
        int aligned = parent.rowY(row) - child.inset.top();
        child.y = Math.max(top, Math.min(aligned, bottom - child.h));
        start(child);
    }

    private static void start(Level l) {
        if (reducedMotion) {
            l.glide.snap(true);
        } else {
            l.glide.replay();
        }
    }

    private void clearPending() {
        pendingLevel = -1;
        pendingRow = -1;
    }

    private void schedule(int level, int row, int wait) {
        if (pendingLevel == level && pendingRow == row) {
            return;
        }
        pendingLevel = level;
        pendingRow = row;
        pendingWait = wait;
        pendingAt = clock.getAsLong();
    }

    private void runPending() {
        if (pendingLevel < 0 || clock.getAsLong() - pendingAt < pendingWait || pendingLevel >= levels.size()) {
            return;
        }
        int level = pendingLevel;
        int row = pendingRow;
        clearPending();
        truncate(level + 1);
        if (row >= 0) {
            openChild(level, row);
        }
    }

    /** Keeps only the first {@code count} menus. */
    private void truncate(int count) {
        while (levels.size() > count) {
            Level gone = levels.remove(levels.size() - 1);
            if (dragged == gone) {
                dragged = null;
            }
        }
        current = Math.min(current, levels.size() - 1);
    }

    private Level openChild(int level, int row) {
        Level parent = levels.get(level);
        Level child = build(tidy(parent.items.get(row).children), row);
        placeChild(parent, child, row);
        levels.add(child);
        return child;
    }

    private void moveCursor(Level l, int row) {
        l.cursor = row;
        if (row >= 0) {
            l.reveal(row);
        }
    }

    private int levelAt(double mx, double my) {
        for (int i = levels.size() - 1; i >= 0; i--) {
            if (levels.get(i).contains(mx, my)) {
                return i;
            }
        }
        return -1;
    }

    /** Puts the rows lit along the way back on the items that opened each submenu. */
    private void relightParents(int below) {
        for (int i = 0; i < below && i + 1 < levels.size(); i++) {
            levels.get(i).cursor = levels.get(i + 1).parentRow;
        }
    }

    private void pointer(int mx, int my) {
        keyed = false;
        int at = levelAt(mx, my);
        if (at < 0) {
            clearPending();
            relightParents(levels.size());
            levels.get(levels.size() - 1).cursor = -1;
            current = levels.size() - 1;
            return;
        }
        Level l = levels.get(at);
        int row = l.rowAt(mx, my);
        boolean hasChild = at + 1 < levels.size();
        relightParents(at);
        current = at;
        if (hasChild && row == levels.get(at + 1).parentRow) {
            // Back on the item that opened the submenu: anything deeper goes, the submenu stays.
            truncate(at + 2);
            levels.get(at + 1).cursor = -1;
            l.cursor = row;
            clearPending();
            return;
        }
        if (row >= 0 && l.items.get(row).pickable()) {
            l.cursor = row;
            if (l.items.get(row).kind == Item.Kind.SUBMENU) {
                schedule(at, row, hasChild ? HOVER_SWITCH_MS : HOVER_OPEN_MS);
            } else if (hasChild) {
                schedule(at, -1, HOVER_SWITCH_MS);
            } else {
                clearPending();
            }
            return;
        }
        if (hasChild) {
            l.cursor = levels.get(at + 1).parentRow;
            clearPending();
        } else {
            l.cursor = -1;
            clearPending();
        }
    }

    private void press(int level, int row, boolean byKey) {
        Level l = levels.get(level);
        Item item = l.items.get(row);
        if (!item.pickable()) {
            return;
        }
        clearPending();
        click();
        if (item.kind == Item.Kind.SUBMENU) {
            boolean already = level + 1 < levels.size() && levels.get(level + 1).parentRow == row;
            truncate(already ? level + 2 : level + 1);
            Level child = already ? levels.get(level + 1) : openChild(level, row);
            l.cursor = row;
            if (byKey) {
                current = level + 1;
                moveCursor(child, child.first());
            }
            return;
        }
        if (item.kind == Item.Kind.CHECK) {
            item.checked = !item.checked;
        }
        if (!item.keepOpen) {
            close();
        }
        if (item.kind == Item.Kind.CHECK) {
            if (item.onToggle != null) {
                item.onToggle.accept(item.checked);
            }
        } else if (item.onPress != null) {
            item.onPress.run();
        }
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        if (!sync()) {
            return;
        }
        if (host.focus() != this) {
            host.focus(this); // a button that opened the menu takes the focus after its press
        }
        if (pointerKnown && (mx != lastMx || my != lastMy) && dragged == null) {
            pointer(mx, my);
        }
        lastMx = mx;
        lastMy = my;
        pointerKnown = true;
        runPending();
        for (int i = 0; i < levels.size(); i++) {
            d.nextLayer(); // the text beneath may sort above a rectangle otherwise
            drawLevel(d, t, levels.get(i), i);
        }
    }

    private void drawLevel(Draw d, Theme t, Level l, int index) {
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        boolean moving = !reducedMotion && l.glide.moving();
        int clipTop = l.y;
        int clipBottom = l.y + l.h;
        if (moving) {
            int shown = Math.max(1, Math.round(l.h * Math.max(0F, Math.min(1F, l.glide.progress()))));
            clipTop = l.revealsUp ? clipBottom - shown : clipTop;
            clipBottom = clipTop + shown;
            d.scissor(l.x - MARGIN, clipTop, l.x + l.w + MARGIN, clipBottom);
        }
        frame.popup(d, p, l.x, l.y, l.w, l.h);
        if (moving) {
            d.endScissor();
        }
        int viewTop = Math.max(l.viewY(), clipTop);
        int viewBottom = Math.min(l.viewY() + l.viewH(), clipBottom);
        if (viewBottom > viewTop && l.rowW() > 0) {
            d.scissor(l.rowX(), viewTop, l.rowX() + l.rowW(), viewBottom);
            for (int i = 0; i < l.items.size(); i++) {
                int ry = l.rowY(i);
                if (ry + l.items.get(i).height() > l.viewY() && ry < l.viewY() + l.viewH()) {
                    drawRow(d, t, l, i, ry, index);
                }
            }
            d.endScissor();
            if (!moving && keyed && index == current) {
                drawRing(d, frame, p, l);
            }
        }
        if (!moving) {
            boolean lit = dragged == l || (pointerKnown && l.overBar(lastMx, lastMy));
            l.bar.draw(d, t, l.barX(), l.viewY(), l.viewH(), lit);
        }
    }

    /**
     * The keyboard's ring, in the colour of a panel's ring. It sits just outside the row, where the row's
     * own text and icons never reach, and outside the view's cut: the row is kept wholly in view.
     */
    private static void drawRing(Draw d, FrameStyle frame, Palette p, Level l) {
        if (l.cursor < 0 || !l.items.get(l.cursor).pickable()) {
            return;
        }
        int ry = l.rowY(l.cursor);
        int rh = l.items.get(l.cursor).height();
        if (ry >= l.viewY() && ry + rh <= l.viewY() + l.viewH()) {
            frame.ringAround(d, p, l.rowX(), ry, l.rowW(), rh, 1, p.text());
        }
    }

    private void drawRow(Draw d, Theme t, Level l, int row, int ry, int index) {
        Palette p = t.palette();
        Item item = l.items.get(row);
        int rx = l.rowX();
        int rw = l.rowW();
        if (item.kind == Item.Kind.SEPARATOR) {
            d.fill(rx + LEFT, ry + SEPARATOR_H / 2, rx + rw - RIGHT, ry + SEPARATOR_H / 2 + 1, Shapes.alpha(p.text(), RULE_ALPHA));
            return;
        }
        boolean cut = RowLook.textCut(ry, item.height(), l.viewY(), l.viewH());
        int textY = RowLook.textY(ry, item.height());
        if (item.kind == Item.Kind.TITLE) {
            // Low in its row, as it belongs with the items below, but its descenders stay clear of the ring on the next row.
            if (!cut) {
                d.textFit(item.label, rx + LEFT, ry + item.height() - RowLook.FONT_H - 2, Math.max(0, rw - LEFT - RIGHT), Ink.soft(p, surfaceOf(p)));
            }
            return;
        }
        boolean lit = row == l.cursor && item.pickable();
        boolean trail = lit && index != current;
        int under = surfaceOf(p);
        if (lit) {
            int face = trail ? Shapes.alpha(p.primary(), TRAIL_ALPHA) : p.primary();
            t.frame().row(d, p, rx, ry, rw, item.height(), face);
            under = Shapes.mix(under, p.primary() | 0xFF000000, (face >>> 24) / 255F);
        }
        int ink;
        int soft;
        if (item.greyed()) {
            ink = disabledInk(p, under);
            soft = ink;
        } else if (lit && !trail) {
            ink = p.primaryText();
            soft = ink;
        } else if (lit) {
            ink = Ink.on(p, under);
            soft = ink;
        } else {
            ink = Ink.on(p, under);
            soft = Ink.soft(p, under);
        }
        if (cut) {
            return;
        }
        int cx = rx + LEFT;
        if (l.checkW > 0) {
            if (item.kind == Item.Kind.CHECK && item.checked) {
                IconArt mark = t.icon(Icon.CHECK);
                mark.draw(d, cx + (l.checkW - mark.width()) / 2, ry + (item.height() - mark.height()) / 2, ink, 1F);
            }
            cx += l.checkW + COLUMN_GAP;
        }
        if (l.iconW > 0) {
            IconArt art = item.art(t);
            if (art != null) {
                // Fitted (crisp n/gs shrink), veiled when greyed: an item ignores the grey ink. No item tooltip: decorative.
                int room = Math.min(ART_MAX, item.height() - 2);
                int artW = ItemArt.rowWidth(art, room);
                ItemArt.inRow(d, p, art, cx + (l.iconW - artW) / 2, ry, item.height(), room, soft, !item.greyed());
            }
            cx += l.iconW + COLUMN_GAP;
        }
        int right = rx + rw - RIGHT;
        int labelRoom = right - cx;
        if (item.kind == Item.Kind.SUBMENU) {
            IconArt arrow = t.icon(Icon.NEXT);
            arrow.draw(d, right - arrow.width(), ry + (item.height() - arrow.height()) / 2, soft, 1F);
            labelRoom -= arrow.width() + COLUMN_GAP;
        } else if (!item.shortcut.isEmpty()) {
            int side = Math.min(TextMeasure.width(item.shortcut), Math.max(0, labelRoom / 2));
            if (side > 0) {
                d.textFitRight(item.shortcut, right, textY, side, soft);
                labelRoom -= side + COLUMN_GAP;
            }
        }
        if (labelRoom > 0) {
            d.textFit(item.label, cx, textY, labelRoom, ink);
        }
    }

    private static int surfaceOf(Palette p) {
        return p.surface() | 0xFF000000;
    }

    private static int disabledInk(Palette p, int under) {
        return Shapes.contrast(p.textFaint(), under) >= READABLE_DISABLED ? p.textFaint() : Shapes.mix(Ink.on(p, under), under, DISABLED_FADE);
    }

    /** The panel must not ring the whole area: the menu rings its own row. */
    @Override
    protected boolean drawFocus(Draw d, Theme t) {
        return true;
    }

    @Override
    public void tick() {
        sync();
    }

    void holdWhile(BooleanSupplier holder) {
        this.holder = holder;
    }

    /** Hidden or disabled while open, or let go by its holder, closes it. Returns whether it is open. */
    private boolean sync() {
        if (open && (!visible || !active || (holder != null && !holder.getAsBoolean()))) {
            close();
        }
        return open;
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!sync()) {
            return null; // closed, the widgets beneath answer
        }
        int at = levelAt(mx, my);
        if (at < 0) {
            return List.of(); // empty, not null: a panel goes on to the widgets beneath on null
        }
        Level l = levels.get(at);
        int row = l.rowAt(mx, my);
        if (row < 0) {
            return List.of();
        }
        Item item = l.items.get(row);
        List<String> lines = (!item.enabled && item.reason != null ? item.reason : item.tooltip);
        if (lines != null) {
            return lines;
        }
        return isCut(l, item) ? List.of(item.label) : List.of();
    }

    private boolean isCut(Level l, Item item) {
        int width = TextMeasure.width(item.label);
        if (item.kind == Item.Kind.TITLE) {
            return width > l.rowW() - LEFT - RIGHT;
        }
        if (item.kind == Item.Kind.SEPARATOR) {
            return false;
        }
        int room = l.rowW() - LEFT - RIGHT - columns(l);
        if (item.kind == Item.Kind.SUBMENU) {
            room -= l.sideW + COLUMN_GAP;
        } else if (!item.shortcut.isEmpty()) {
            room -= Math.min(TextMeasure.width(item.shortcut), Math.max(0, room / 2)) + COLUMN_GAP;
        }
        return width > room;
    }

    private boolean inRegion(double mx, double my) {
        for (BiPredicate<Double, Double> region : regions) {
            if (region.test(mx, my)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Only while open. A panel gives its focus to the top widget under the pointer before offering the
     * click to every widget, so a closed menu must not stand in front of a text field in its region.
     */
    @Override
    public boolean isOver(double mx, double my) {
        return open;
    }

    @Override
    protected boolean takesFocus() {
        return open;
    }

    @Override
    protected boolean navigable() {
        return open;
    }

    @Override
    public List<BaseComponent> stops() {
        return open ? List.of(this) : List.of();
    }

    @Override
    protected boolean activate() {
        return sync() && pressCurrent();
    }

    private boolean pressCurrent() {
        Level l = levels.get(current);
        if (l.cursor >= 0) {
            press(current, l.cursor, true);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!sync()) {
            return button == 1 && visible && active && inRegion(mx, my) && open((int) mx, (int) my);
        }
        int at = levelAt(mx, my);
        if (at < 0) {
            click();
            close();
            if (button == 1 && inRegion(mx, my)) {
                open((int) mx, (int) my);
            }
            return true;
        }
        if (button != 0) {
            return true;
        }
        Level l = levels.get(at);
        if (l.overBar(mx, my)) {
            l.bar.press(mx, my, l.barX(), l.viewY(), l.viewH());
            if (l.bar.dragging()) {
                dragged = l;
            }
            return true;
        }
        int row = l.rowAt(mx, my);
        if (row >= 0) {
            keyed = false;
            current = at;
            press(at, row, false);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragged != null) {
            dragged.bar.drag(my);
            return true;
        }
        return open;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = dragged != null;
        if (was) {
            dragged.bar.release();
        }
        dragged = null;
        return was || open;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!sync()) {
            return false;
        }
        int at = levelAt(mx, my);
        if (at >= 0 && levels.get(at).overflows()) {
            Level l = levels.get(at);
            truncate(at + 1); // a submenu would no longer sit against the row that opened it
            clearPending();
            l.bar.wheel(amount, ScrollBar.WHEEL_ROWS * ROW);
            pointer((int) mx, (int) my);
        }
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!sync()) {
            return false;
        }
        clearPending();
        Level l = levels.get(current);
        switch (key) {
            case UP, DOWN, HOME, END -> {
                truncate(current + 1);
                keyed = true;
                typed = "";
                moveCursor(l, switch (key) {
                    case UP -> l.cursor < 0 ? l.last() : l.step(l.cursor, -1);
                    case DOWN -> l.cursor < 0 ? l.first() : l.step(l.cursor, 1);
                    case HOME -> l.first();
                    default -> l.last();
                });
            }
            case RIGHT -> enterSubmenu(l);
            case LEFT -> {
                keyed = true;
                if (current > 0) {
                    truncate(current);
                    current = levels.size() - 1;
                    click();
                } else if (levels.size() > 1) {
                    truncate(1);
                }
            }
            case ESCAPE -> {
                click();
                if (levels.size() > 1) {
                    keyed = true;
                    truncate(levels.size() - 1);
                    current = levels.size() - 1;
                } else {
                    close();
                }
            }
            case TAB -> close();
            case ENTER -> {
                keyed = true;
                pressCurrent();
            }
            case SPACE -> {
                // In the middle of a word, a space is part of it: the character comes next, through charTyped.
                if (!typingNow()) {
                    keyed = true;
                    pressCurrent();
                }
            }
            default -> { }
        }
        return true;
    }

    private void enterSubmenu(Level l) {
        keyed = true;
        if (l.cursor < 0 || l.items.get(l.cursor).kind != Item.Kind.SUBMENU || !l.items.get(l.cursor).pickable()) {
            return;
        }
        if (current + 1 < levels.size() && levels.get(current + 1).parentRow == l.cursor) {
            truncate(current + 2);
            current++;
            Level child = levels.get(current);
            moveCursor(child, child.first());
        } else {
            press(current, l.cursor, true);
        }
    }

    private boolean typingNow() {
        return !typed.isEmpty() && clock.getAsLong() - typedAt <= TYPE_RESET_MS;
    }

    @Override
    public boolean charTyped(int codePoint) {
        if (!sync()) {
            return false;
        }
        if (codePoint == ' ' && !typingNow()) {
            return true; // the key already pressed the item
        }
        if (Character.isISOControl(codePoint)) {
            return true;
        }
        if (!typingNow()) {
            typed = "";
        }
        typed += new String(Character.toChars(codePoint));
        typedAt = clock.getAsLong();
        clearPending();
        truncate(current + 1);
        Level l = levels.get(current);
        String query = TextMatch.fold(typed).text;
        int found = -1;
        if (query.codePointCount(0, query.length()) > 1) {
            found = find(l, query, Math.max(0, l.cursor)); // the lit item may simply fit the longer word too
        }
        if (found < 0 && sameLetter(query)) {
            found = find(l, query.substring(0, query.offsetByCodePoints(0, 1)), l.cursor + 1);
        }
        if (found >= 0) {
            keyed = true;
            moveCursor(l, found);
        }
        return true;
    }

    private static boolean sameLetter(String query) {
        if (query.isEmpty()) {
            return false;
        }
        int first = query.codePointAt(0);
        return query.codePoints().allMatch(c -> c == first);
    }

    private static int find(Level l, String prefix, int from) {
        int count = l.items.size();
        for (int i = 0; i < count; i++) {
            int at = Math.floorMod(from + i, count);
            Item item = l.items.get(at);
            if (item.pickable() && item.folded().startsWith(prefix)) {
                return at;
            }
        }
        return -1;
    }
}
