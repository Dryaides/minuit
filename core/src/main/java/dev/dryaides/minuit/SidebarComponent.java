package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A column of navigation that folds into a rail of icons: groups, icons, badges and nested items.
 *
 * <pre>{@code
 * SidebarComponent nav = add(new SidebarComponent(at(new Box("nav", 0, 0, 110, 200)), this)
 *         .groups(Group.of("World",
 *                         Item.of("Map").icon(Icon.GLOBE),
 *                         Item.of("Players").icon(Icon.CHIP).badge("12"),
 *                         Item.of("Files").icon(Icon.FOLDER).children(Item.of("Saves"), Item.of("Logs").badge("!", Tone.WARN))),
 *                 Group.of("Look", Item.of("Theme").icon(Icon.PALETTE)))
 *         .onSelect(this::open)
 *         .onCollapse(rail -> placeContent(nav.currentWidth())));
 * // ... the other widgets ...
 * add(nav.menu()); // last: what a nested item opens from the rail
 * }</pre>
 *
 * <p>The box is the open size. The widget's {@code w} follows the fold, {@link #currentWidth()} reads it. The
 * rail shows top items only: an icon, or the label's first letter without one, a dot for a badge, and the label
 * as a tooltip. Icons keep their column, so they stay in place while it folds. Under reduced motion it folds at once.
 *
 * <p>A pressed item with children opens or shuts them. On the rail it opens them in a {@link ContextMenuComponent}
 * if {@link #menu()} was added (with that component's rule: after every other widget), and opens the sidebar
 * otherwise. {@link Item#selectable(boolean)} makes an item with children a place too. A shut parent, or an item
 * on the rail, with the selection inside it shows it at half strength.
 *
 * <h2>Keys</h2>
 * Up and down move, Home and End jump, Right opens a parent and then goes in, Left shuts it and then goes to its
 * parent, Enter or Space press. The last stop is the row that folds the sidebar.
 */
public class SidebarComponent extends BaseComponent {
    /** The colour of a badge. */
    public enum Tone { PRIMARY, GOOD, WARN, BAD }

    /** One row of navigation. */
    public static final class Item {
        private final String label;
        private Icon icon;
        private IconArt iconArt;
        private String badge = "";
        private Tone tone = Tone.PRIMARY;
        private List<Item> children = List.of();
        private boolean expanded;
        private boolean enabled = true;
        private boolean selectable;
        private List<String> tooltip;
        /** The disabled reason, kept apart so re-enabling gives the item's own tooltip back. */
        private List<String> reason;
        private Runnable onClick;

        private Item(String label) {
            this.label = label == null ? "" : label;
        }

        public static Item of(String label) {
            return new Item(label);
        }

        public Item icon(Icon icon) {
            this.icon = icon;
            return this;
        }

        public Item iconArt(IconArt iconArt) {
            this.iconArt = iconArt;
            return this;
        }

        /** A count or a short word at the row's end; empty or null removes it. May change at any time. */
        public Item badge(String badge) {
            this.badge = badge == null ? "" : badge;
            return this;
        }

        public Item badge(String badge, Tone tone) {
            this.tone = tone == null ? Tone.PRIMARY : tone;
            return badge(badge);
        }

        public Item children(Item... children) {
            this.children = new ArrayList<>(Arrays.asList(children));
            return this;
        }

        /** Whether its children show. Shut by default. */
        public Item expanded(boolean expanded) {
            this.expanded = expanded;
            return this;
        }

        /** An item with children is only a folder unless this is on. A leaf is always a place. */
        public Item selectable(boolean selectable) {
            this.selectable = selectable;
            return this;
        }

        public Item tooltip(String... lines) {
            this.tooltip = lines == null || lines.length == 0 ? null : List.of(lines);
            return this;
        }

        /** Runs when the player picks this item, after the sidebar's own callback. */
        public Item onClick(Runnable onClick) {
            this.onClick = onClick;
            return this;
        }

        public String label() {
            return label;
        }

        public boolean isExpanded() {
            return expanded;
        }

        private boolean hasChildren() {
            return !children.isEmpty();
        }

        private boolean holds(Item target) {
            for (Item child : children) {
                if (child == target || child.holds(target)) {
                    return true;
                }
            }
            return false;
        }

        private IconArt art(Theme t) {
            return iconArt != null ? iconArt : icon != null ? t.icon(icon) : null;
        }
    }

    /** Items under a title. A null title gives a group with only a line above it. */
    public static final class Group {
        private final String title;
        private final List<Item> items;

        private Group(String title, List<Item> items) {
            this.title = title == null ? "" : title;
            this.items = items;
        }

        public static Group of(String title, Item... items) {
            return new Group(title, new ArrayList<>(Arrays.asList(items)));
        }

        public static Group of(String title, List<Item> items) {
            return new Group(title, items == null ? new ArrayList<>() : new ArrayList<>(items));
        }
    }

    private enum Kind { TITLE, RULE, ITEM }

    /** One line of the list, rebuilt when the list changes, not on every frame. */
    private static final class Row {
        Kind kind;
        String text;
        Item item;
        int depth;
        int parent = -1;
        int top;
        int height;
        boolean inner;
        int cutFor = -1;
        String cut;
    }

    /** Rows here are taller than a menu's: this is the screen's main way around, pressed often. */
    private static final int ROW = 14;
    /** Art taller than an item is shrunk to 16 (by n/gs, crisp). */
    private static final int ART_MAX = 16;
    /** The narrowest rail that holds a 16 px item with a pixel or two on each side. */
    private static final int ITEM_RAIL = 20;
    private static final int TITLE_H = 13;
    private static final int RULE_H = 5;
    private static final int INSET = 2;
    private static final int BAR = 6;
    private static final int RIGHT = 3;
    private static final int GAP = 3;
    private static final int NEST = 10;
    private static final int GUIDE_ALPHA = 0x30;
    private static final int RULE_ALPHA = 0x40;
    private static final int FOLD_MS = 130;
    private static final int DOT = 3;
    private static final int OFF_BADGE_ALPHA = 0x20;

    private final PanelComponent host;
    private final int fullW;
    private final Glide fold = new Glide(Slide.LEFT, FOLD_MS);
    private final RowLook.HoverFade hover = new RowLook.HoverFade();
    private final List<Group> groups = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private ContextMenuComponent menu;

    private int railW = 20;
    private boolean foldable = true;
    private boolean collapsed;
    /** Counts {@code groups(...)} calls, so a flyout built from older groups closes. */
    private int groupsVersion;
    private int menuVersion;
    private String toggleLabel;
    private String expandLabel;
    private String emptyText;
    private Words words;
    private Consumer<Item> onSelect;
    private Consumer<Boolean> onCollapse;

    private boolean stale = true;
    private boolean laidRail;
    private boolean anyIcon;
    /** The height of an item's row: 14, or 18 when an item shows 16 px art such as a Minecraft item. */
    private int itemRowH = ROW;
    private int contentH;
    private Item selected;
    private Item cursorItem;
    private boolean cursorOnToggle;
    private int cursor = -1;
    private final ScrollBar bar = new ScrollBar();
    private final Map<String, Object[]> disabledIds = new HashMap<>();

    /**
     * @param box the sidebar's open size
     * @param host the panel this sidebar is added to
     */
    public SidebarComponent(Box box, PanelComponent host) {
        super(box);
        this.host = Objects.requireNonNull(host, "host");
        this.fullW = box.w();
        fold.snap(true);
    }

    public SidebarComponent groups(Group... groups) {
        return groups(Arrays.asList(groups));
    }

    public final SidebarComponent groups(List<Group> list) {
        groupsVersion++;
        groups.clear();
        if (list != null) {
            for (Group group : list) {
                if (group != null) {
                    groups.add(group);
                }
            }
        }
        for (Map.Entry<String, Object[]> e : disabledIds.entrySet()) {
            applyDisabled(e.getKey(), e.getValue());
        }
        cursorItem = null;
        stale = true;
        return this;
    }

    /**
     * Greys out the items labelled {@code id}, at any depth, or makes them pickable again: drawn disabled, skipped by
     * clicks and by the keyboard. The {@code tip} lines show over the row, to say why; none keeps its own tooltip. It holds
     * for groups set later. A sidebar item has no id of its own, so its label is the id.
     */
    public SidebarComponent disabled(String id, boolean disabled, String... tip) {
        Objects.requireNonNull(id, "id");
        Object[] how = {disabled, tip};
        disabledIds.put(id, how);
        applyDisabled(id, how);
        return this;
    }

    private void applyDisabled(String id, Object[] how) {
        for (Group group : groups) {
            for (Item item : group.items) {
                applyDisabled(item, id, how);
            }
        }
        stale = true;
    }

    private static void applyDisabled(Item item, String id, Object[] how) {
        if (item.label.equals(id)) {
            item.enabled = !(Boolean) how[0];
            String[] tip = (String[]) how[1];
            item.reason = item.enabled || tip == null || tip.length == 0 ? null : List.of(tip);
        }
        for (Item child : item.children) {
            applyDisabled(child, id, how);
        }
    }

    /** Called when the player picks a place. Not through {@link #select}. */
    public SidebarComponent onSelect(Consumer<Item> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /** Called with true when the player folds the sidebar, false when opened. Not through {@link #setCollapsed}. */
    public SidebarComponent onCollapse(Consumer<Boolean> onCollapse) {
        this.onCollapse = onCollapse;
        return this;
    }

    /** The width of the rail. 20 by default. */
    public SidebarComponent railWidth(int width) {
        railW = Math.max(12, Math.min(fullW, width));
        stale = true;
        return this;
    }

    /** False removes the row that folds the sidebar. */
    public SidebarComponent foldable(boolean foldable) {
        this.foldable = foldable;
        stale = true;
        return this;
    }

    /** The words of the row that folds the sidebar, open and on the rail, instead of the lang entries. */
    public SidebarComponent toggleLabels(String collapse, String expand) {
        toggleLabel = collapse == null ? "" : collapse;
        expandLabel = expand == null ? "" : expand;
        return this;
    }

    /** The text shown with no items, instead of the lang entry {@code minuit.empty}. */
    public SidebarComponent emptyText(String text) {
        emptyText = text == null ? "" : text;
        return this;
    }

    /** Starts folded or open, with no motion and no callback. */
    public SidebarComponent collapsed(boolean collapsed) {
        this.collapsed = collapsed;
        fold.snap(!collapsed);
        stale = true;
        applyWidth();
        return this;
    }

    /** The menu of a nested item on the rail, in the panel's bounds: add it after every other widget. */
    public ContextMenuComponent menu() {
        return menu(host.bounds());
    }

    /** The menu of a nested item on the rail, staying in this box: add it after every other widget. */
    public ContextMenuComponent menu(Box area) {
        if (menu == null) {
            menu = new ContextMenuComponent(area, host);
            menu.holdWhile(() -> visible && active && collapsed && menuVersion == groupsVersion);
        }
        return menu;
    }

    @Override
    public SidebarComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    public Item selected() {
        return selected;
    }

    /** Selects a place without calling back, opening what holds it. Null clears it. */
    public void select(Item item) {
        selected = item;
        if (item != null) {
            for (Group group : groups) {
                for (Item top : group.items) {
                    openPathTo(top, item);
                }
            }
        }
        stale = true;
    }

    private static boolean openPathTo(Item from, Item target) {
        if (from == target) {
            return true;
        }
        for (Item child : from.children) {
            if (openPathTo(child, target)) {
                from.expanded = true;
                return true;
            }
        }
        return false;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    /** The width right now, moving while it folds. */
    public int currentWidth() {
        applyWidth();
        return w;
    }

    /** Folds or opens it with the usual motion, without calling back. */
    public void setCollapsed(boolean wanted) {
        if (wanted == collapsed) {
            return;
        }
        collapsed = wanted;
        if (reducedMotion()) {
            fold.snap(!wanted);
        } else if (wanted) {
            fold.hide();
        } else {
            fold.show();
        }
        applyWidth();
        stale = true;
    }

    /** Folds or opens as a press on its row would, with the callback. */
    public void toggle() {
        setCollapsed(!collapsed);
        if (onCollapse != null) {
            onCollapse.accept(collapsed);
        }
    }

    @Override
    void words(Words words) {
        this.words = words;
    }

    private String collapseText() {
        return text(toggleLabel, words, "minuit.sidebar.collapse", "Collapse");
    }

    private String expandText() {
        return text(expandLabel, words, "minuit.sidebar.expand", "Expand");
    }

    int rowCount() {
        layout();
        return rows.size();
    }

    /** The label of a row: an item's, a title's, or empty for a line. */
    String rowText(int row) {
        layout();
        return rows.get(row).text;
    }

    Box rowBox(int row) {
        layout();
        Row r = rows.get(row);
        return new Box("row" + row, rowX(), rowY(r), rowW(), r.height);
    }

    /** The row of an item shown now, or -1. */
    int rowOf(Item item) {
        layout();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).item == item) {
                return i;
            }
        }
        return -1;
    }

    Box toggleBox() {
        layout();
        return showsToggle() ? new Box("toggle", rowX(), toggleY(), rowW(), ROW) : null;
    }

    /** The text of the row the keyboard is on, or null. */
    String cursorText() {
        layout();
        if (cursorOnToggle) {
            return collapsed ? expandText() : collapseText();
        }
        return cursor >= 0 && cursor < rows.size() ? rows.get(cursor).text : null;
    }

    int scrollOffset() {
        return bar.offset();
    }

    private boolean moving() {
        return !reducedMotion() && fold.moving();
    }

    /** The rail's list applies once folded, and goes as soon as the opening starts. */
    private boolean rail() {
        return collapsed && !moving();
    }

    private void applyWidth() {
        float shown;
        if (moving()) {
            shown = Math.max(0F, Math.min(1F, fold.progress()));
        } else {
            shown = collapsed ? 0F : 1F;
        }
        int rail = railSpan();
        w = rail + Math.round((fullW - rail) * shown);
    }

    private void layout() {
        boolean railNow = rail();
        if (!stale && railNow == laidRail) {
            return;
        }
        stale = false;
        laidRail = railNow;
        rows.clear();
        anyIcon = false;
        itemRowH = Math.max(ROW, Math.min(ART_MAX, artHeight()) + 2);
        boolean first = true;
        for (Group group : groups) {
            if (group.items.isEmpty()) {
                continue;
            }
            if (railNow || group.title.isEmpty()) {
                if (!first) {
                    addLine(Kind.RULE, "", RULE_H);
                }
            } else {
                addLine(Kind.TITLE, group.title, TITLE_H);
            }
            first = false;
            for (Item item : group.items) {
                addItem(item, 0, -1, railNow);
            }
        }
        int top = 0;
        for (Row row : rows) {
            row.top = top;
            top += row.height;
        }
        contentH = top;
        cursor = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (cursorItem != null && rows.get(i).item == cursorItem) {
                cursor = i;
            }
        }
        if (cursor < 0 && cursorItem != null) {
            cursorItem = null; // it went: folded away, or removed
        }
        clampScroll();
    }

    private void addLine(Kind kind, String text, int height) {
        Row row = new Row();
        row.kind = kind;
        row.text = text;
        row.height = height;
        rows.add(row);
    }

    private void addItem(Item item, int depth, int parent, boolean railNow) {
        if (item == null) {
            return;
        }
        Row row = new Row();
        row.kind = Kind.ITEM;
        row.text = item.label;
        row.item = item;
        row.depth = depth;
        row.parent = parent;
        row.height = itemRowH;
        boolean open = item.expanded && !railNow;
        row.inner = selected != null && !open && item.holds(selected);
        rows.add(row);
        anyIcon |= item.icon != null || item.iconArt != null;
        int self = rows.size() - 1;
        if (open) {
            for (Item child : item.children) {
                addItem(child, depth + 1, self, false);
            }
        }
    }

    /** The tallest art of your own among all items, nested ones too; 0 without any. */
    private int artHeight() {
        int tallest = 0;
        for (Group group : groups) {
            for (Item item : group.items) {
                tallest = Math.max(tallest, artHeight(item));
            }
        }
        return tallest;
    }

    private static int artHeight(Item item) {
        if (item == null) {
            return 0;
        }
        int tallest = item.iconArt == null ? 0 : item.iconArt.height();
        for (Item child : item.children) {
            tallest = Math.max(tallest, artHeight(child));
        }
        return tallest;
    }

    /** The rail's width: the one set, but never under 20 when it holds a Minecraft item (16 px and its margins). */
    private int railSpan() {
        if (railW >= ITEM_RAIL) {
            return railW;
        }
        for (Group group : groups) {
            for (Item item : group.items) {
                if (item != null && item.iconArt instanceof ItemView) {
                    return Math.min(fullW, ITEM_RAIL);
                }
            }
        }
        return railW;
    }

    private boolean showsToggle() {
        return foldable && h >= 2 * ROW + 2 * INSET + 1;
    }

    private int footer() {
        return showsToggle() ? ROW + 1 : 0;
    }

    private int viewY() { return y + INSET; }
    private int viewH() { return Math.max(0, h - 2 * INSET - footer()); }
    private int toggleY() { return y + h - INSET - ROW; }
    private boolean overflows() { return contentH > viewH(); }
    private boolean showsBar() { return overflows() && !rail() && !moving() && RowLook.barFits(w, h, BAR + 1 + 2 * INSET); }
    private int rowX() { return x + INSET; }

    /** During the fold, rows keep their open width and the box cuts them: nothing reflows on each frame. */
    private int rowW() {
        int width = moving() ? fullW : w;
        return Math.max(0, width - 2 * INSET - (showsBar() ? BAR + 1 : 0));
    }

    private int rowY(Row row) { return viewY() + row.top - bar.offset(); }
    private int barX() { return x + w - INSET - BAR; }

    private void clampScroll() {
        bar.content(contentH, viewH());
    }

    private int rowAt(double mx, double my) {
        if (mx < rowX() || mx >= rowX() + rowW() || my < viewY() || my >= viewY() + viewH()) {
            return -1;
        }
        int inner = (int) Math.floor(my) - viewY() + bar.offset();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (inner >= row.top && inner < row.top + row.height) {
                return row.kind == Kind.ITEM ? i : -1;
            }
        }
        return -1;
    }

    private boolean overToggle(double mx, double my) {
        return showsToggle() && mx >= rowX() && mx < rowX() + rowW() && my >= toggleY() && my < toggleY() + ROW;
    }

    private boolean overBar(double mx, double my) {
        return showsBar() && mx >= barX() && mx < barX() + BAR && my >= viewY() && my < viewY() + viewH();
    }

    private void reveal(int row) {
        Row r = rows.get(row);
        int top = r.top;
        if (row > 0 && rows.get(row - 1).kind == Kind.TITLE) {
            top = rows.get(row - 1).top; // a title comes along with its first item
        }
        clampScroll();
        if (top < bar.offset()) {
            bar.offset(top);
        } else if (r.top + r.height > bar.offset() + viewH()) {
            bar.offset(r.top + r.height - viewH());
        }
    }

    @Override
    public void tick() {
        applyWidth();
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        applyWidth();
        layout();
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        boolean live = focused && active;
        frame.well(d, p, x, y, w, h, live);
        boolean pointer = active && hovered && !moving() && !bar.dragging();
        int overRow = pointer ? rowAt(mx, my) : -1;
        boolean onToggle = pointer && overToggle(mx, my);
        if (overRow >= 0 && !rows.get(overRow).item.enabled) {
            overRow = -1;
        }
        hover.step(onToggle ? Integer.MAX_VALUE : overRow);
        int boxRight = x + w;
        int viewTop = viewY();
        int viewBottom = viewTop + viewH();
        if (rows.isEmpty()) {
            if (!rail()) {
                RowLook.drawEmpty(d, p, text(emptyText, words, "minuit.empty", "Nothing here"), active, x + 1, y + 1, Math.max(0, w - 2), Math.max(0, viewH() + INSET));
            }
        } else if (viewBottom > viewTop) {
            d.scissor(x + 1, viewTop, boxRight - 1, viewBottom);
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int ry = rowY(row);
                if (ry + row.height > viewTop && ry < viewBottom) {
                    drawRow(d, t, p, row, ry, i == overRow, live);
                }
            }
            d.endScissor();
        }
        if (showsToggle()) {
            d.fill(x + INSET, toggleY() - 1, boxRight - INSET, toggleY(), Shapes.alpha(p.text(), RULE_ALPHA));
            d.scissor(x + 1, y, boxRight - 1, y + h);
            drawToggle(d, t, p, onToggle);
            d.endScissor();
        }
        if (showsBar()) {
            clampScroll();
            bar.draw(d, t, barX(), viewTop, viewH(), overBar(mx, my));
            if (!active) {
                RowLook.dimBar(d, p, barX(), viewTop, BAR, viewH());
            }
        }
    }

    private void drawRow(Draw d, Theme t, Palette p, Row row, int ry, boolean over, boolean live) {
        int rx = rowX();
        int rw = rowW();
        if (row.kind == Kind.RULE) {
            d.fill(rx + 1, ry + RULE_H / 2, rx + rw - 1, ry + RULE_H / 2 + 1, Shapes.alpha(p.text(), RULE_ALPHA));
            return;
        }
        boolean cut = RowLook.textCut(ry, row.height, viewY(), viewH());
        if (row.kind == Kind.TITLE) {
            if (!cut) {
                int ink = RowLook.ink(p, p.well(), false, active);
                int room = Math.max(0, rw - 4);
                if (row.cutFor != room) {
                    row.cutFor = room;
                    row.cut = TextMeasure.draw().ellipsize(row.text, room);
                }
                d.text(row.cut, rx + 2, ry + row.height - RowLook.FONT_H - 1, active ? RowLook.softInk(p, ink) : ink);
            }
            return;
        }
        Item item = row.item;
        boolean itemOn = active && item.enabled;
        boolean chosen = item == selected;
        boolean lit = chosen || row.inner;
        // The selection shows in full only on the place itself, with the focus: a parent holding it stays at half.
        int face = RowLook.face(p, itemOn, live && chosen, lit, over, hover.fade());
        if (face >>> 24 != 0) {
            t.frame().row(d, p, rx, ry, rw, row.height, face);
        }
        int ink = RowLook.ink(p, face, lit, itemOn);
        if (cut) {
            return;
        }
        if (rail()) {
            drawRailCell(d, t, p, row, ry, ink, face);
        } else {
            drawOpenRow(d, t, p, row, ry, ink, face, lit);
        }
    }

    /** Where the icon column is centred: the middle of the rail, so that a fold leaves it in place. */
    private int iconCenter() {
        return x + railSpan() / 2;
    }

    private void drawRailCell(Draw d, Theme t, Palette p, Row row, int ry, int ink, int face) {
        Item item = row.item;
        IconArt art = item.art(t);
        int middle = iconCenter();
        int artW = 0;
        if (art != null) {
            artW = drawArt(d, p, item, art, middle, -1, ry, row.height, ink);
        } else if (!item.label.isEmpty()) {
            if (row.cut == null) {
                row.cut = item.label.substring(0, item.label.offsetByCodePoints(0, 1));
            }
            d.text(row.cut, middle - (d.width(row.cut) - 1) / 2, RowLook.textY(ry, row.height), ink);
        }
        if (!item.badge.isEmpty()) {
            // Kept inside the rail: a 16 px item would push the dot past its edge.
            int dotX = Math.min(middle + (art == null ? 3 : artW / 2), rowX() + rowW() - DOT - 1);
            d.fill(dotX, ry + 1, dotX + DOT, ry + 1 + DOT, badgeFace(p, item, face));
        }
    }

    private void drawOpenRow(Draw d, Theme t, Palette p, Row row, int ry, int ink, int face, boolean lit) {
        Item item = row.item;
        int rx = rowX();
        int right = rx + rowW() - RIGHT;
        int rh = row.height;
        int textY = RowLook.textY(ry, rh);
        int left = anyIcon ? x + railSpan() : rx + 2;
        if (row.depth > 0) {
            left += (row.depth - 1) * NEST;
            int guide = (anyIcon ? iconCenter() : rx + 2) + (row.depth - 1) * NEST;
            if (!anyIcon) {
                left = guide + NEST / 2 + 1;
            }
            if (face >>> 24 == 0) {
                d.fill(guide, ry, guide + 1, ry + rh, Shapes.alpha(p.text(), GUIDE_ALPHA));
            }
        }
        IconArt art = item.art(t);
        if (art != null) {
            int drawn = row.depth == 0 ? drawArt(d, p, item, art, iconCenter(), -1, ry, rh, ink) : drawArt(d, p, item, art, -1, left, ry, rh, ink);
            if (row.depth > 0) {
                left += drawn + GAP;
            }
        }
        if (item.hasChildren()) {
            IconArt arrow = t.icon(item.expanded ? Icon.CARET : Icon.NEXT);
            arrow.draw(d, right - arrow.width(), ry + (rh - arrow.height()) / 2, ink, 1F);
            right -= arrow.width() + GAP;
        }
        if (!item.badge.isEmpty()) {
            int bw = NavLook.badgeWidth(item.badge);
            if (right - bw - GAP - left >= 0) {
                int by = ry + (rh - NavLook.badgeHeight()) / 2;
                int bf = badgeFace(p, item, face);
                t.frame().plate(d, p, right - bw, by, bw, NavLook.badgeHeight(), bf, 0);
                d.text(item.badge, right - bw + 2, by + 1, badgeInk(p, item, bf));
                right -= bw + GAP;
            }
        }
        int room = right - left;
        if (room > 0) {
            if (row.cutFor != room) {
                row.cutFor = room;
                row.cut = TextMeasure.draw().ellipsize(item.label, room);
            }
            d.text(row.cut, left, textY, ink);
        }
    }

    /**
     * An item's art, centred on {@code center} or starting at {@code left} (the other is -1), fitted to the row
     * (crisp n/gs shrink), veiled when off: an item ignores the grey ink. Decorative: no item tooltip. Returns its width.
     */
    private int drawArt(Draw d, Palette p, Item item, IconArt art, int center, int left, int ry, int rh, int ink) {
        int room = Math.min(ART_MAX, rh - 2);
        int width = ItemArt.rowWidth(art, room);
        int ax = center >= 0 ? center - width / 2 : left;
        ItemArt.inRow(d, p, art, ax, ry, rh, room, ink, active && item.enabled);
        return width;
    }

    private int badgeFace(Palette p, Item item, int rowFace) {
        if (!active || !item.enabled) {
            return Shapes.alpha(p.text(), OFF_BADGE_ALPHA);
        }
        int color = switch (item.tone) {
            case GOOD -> p.good();
            case WARN -> p.warn();
            case BAD -> p.bad();
            default -> p.primary();
        };
        return Ink.readable(p, color, Ink.seen(p, rowFace), Ink.MARK);
    }

    private int badgeInk(Palette p, Item item, int face) {
        if (!active || !item.enabled) {
            return RowLook.ink(p, p.well(), false, false);
        }
        if (face == p.primaryText() && item.tone == Tone.PRIMARY) {
            return p.primary();
        }
        return Ink.on(p, face);
    }

    private void drawToggle(Draw d, Theme t, Palette p, boolean over) {
        int rx = rowX();
        int rw = rowW();
        int ry = toggleY();
        int ink = RowLook.ink(p, p.well(), false, active);
        if (over) {
            t.frame().row(d, p, rx, ry, rw, ROW, RowLook.tint(p, hover.fade()));
        }
        int color = !active || over ? ink : RowLook.softInk(p, ink);
        IconArt art = t.icon(collapsed ? Icon.NEXT : Icon.PREV);
        art.draw(d, iconCenter() - art.width() / 2, ry + (ROW - art.height()) / 2, color, 1F);
        if (!rail()) {
            d.textFit(collapsed ? expandText() : collapseText(), x + railSpan(), RowLook.textY(ry, ROW), Math.max(0, rx + rw - RIGHT - x - railSpan()), color);
        }
    }

    @Override
    protected Box focusBox() {
        layout();
        if (cursorOnToggle && showsToggle()) {
            return new Box("toggle", rowX() + 2, toggleY() + 2, Math.max(0, rowW() - 4), ROW - 4);
        }
        if (cursorVisible()) {
            Row row = rows.get(cursor);
            return new Box("row", rowX() + 2, rowY(row) + 2, Math.max(0, rowW() - 4), row.height - 4);
        }
        return super.focusBox();
    }

    @Override
    protected int focusCorner() {
        return (cursorOnToggle && showsToggle()) || cursorVisible() ? 0 : super.focusCorner();
    }

    private boolean cursorVisible() {
        if (cursor < 0 || cursor >= rows.size()) {
            return false;
        }
        int ry = rowY(rows.get(cursor));
        return ry >= viewY() && ry + rows.get(cursor).height <= viewY() + viewH();
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        layout();
        if (overToggle(mx, my)) {
            return rail() ? List.of(expandText()) : tooltip;
        }
        int at = rowAt(mx, my);
        if (at < 0) {
            return tooltip;
        }
        Item item = rows.get(at).item;
        List<String> lines = (!item.enabled && item.reason != null ? item.reason : item.tooltip);
        if (lines != null) {
            return lines;
        }
        if (rail()) {
            return item.badge.isEmpty() ? List.of(item.label) : List.of(item.label + " (" + item.badge + ")");
        }
        Row row = rows.get(at);
        return row.cut != null && !row.cut.equals(item.label) ? List.of(item.label) : tooltip;
    }

    private boolean accepts() {
        return visible && active;
    }

    private void pick(Item item) {
        selected = item;
        stale = true;
        if (onSelect != null) {
            onSelect.accept(item);
        }
        if (item.onClick != null) {
            item.onClick.run();
        }
    }

    private void press(int at, boolean byKey) {
        Row row = rows.get(at);
        Item item = row.item;
        if (!item.enabled) {
            return;
        }
        BaseComponent.click();
        cursorItem = item;
        cursor = at;
        cursorOnToggle = false;
        if (!item.hasChildren()) {
            pick(item);
            return;
        }
        if (rail()) {
            pressOnRail(item, row, byKey);
            return;
        }
        item.expanded = !item.expanded;
        stale = true;
        if (item.selectable) {
            pick(item);
        }
    }

    private void pressOnRail(Item item, Row row, boolean byKey) {
        if (menu == null) {
            item.expanded = true;
            cursorItem = item;
            setCollapsed(false);
            if (onCollapse != null) {
                onCollapse.accept(false);
            }
            return;
        }
        List<ContextMenuComponent.Item> items = new ArrayList<>();
        items.add(ContextMenuComponent.Item.title(item.label));
        if (item.selectable) {
            items.add(ContextMenuComponent.Item.action(item.label, () -> pick(item)));
        }
        for (Item child : item.children) {
            items.add(flyoutItem(child));
        }
        menu.items(items);
        menuVersion = groupsVersion;
        int mx = x + w + 1;
        int my = rowY(row);
        if (byKey) {
            menu.openBelow(new Box("rail", mx, my - 3, 1, 1));
        } else {
            menu.open(mx, my);
        }
    }

    private ContextMenuComponent.Item flyoutItem(Item item) {
        ContextMenuComponent.Item out;
        if (item.hasChildren()) {
            List<ContextMenuComponent.Item> inner = new ArrayList<>();
            for (Item child : item.children) {
                inner.add(flyoutItem(child));
            }
            out = ContextMenuComponent.Item.submenu(item.label, inner);
        } else {
            out = ContextMenuComponent.Item.action(item.label, () -> pick(item));
            if (!item.badge.isEmpty()) {
                out.shortcut(item.badge);
            }
        }
        if (item.tooltip != null) {
            out.tooltip(item.tooltip.toArray(new String[0]));
        }
        if (item.reason != null) {
            out.reason(item.reason.toArray(new String[0]));
        }
        return out.icon(item.icon).iconArt(item.iconArt).enabled(item.enabled);
    }

    @Override
    public boolean isOver(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected boolean takesFocus() {
        return accepts();
    }

    @Override
    protected boolean navigable() {
        layout();
        return accepts() && settled() && (firstStop() >= 0 || showsToggle());
    }

    @Override
    protected void enter(boolean forward) {
        layout();
        if (cursor < 0 && !cursorOnToggle) {
            int at = selected == null ? -1 : rowOf(selected);
            moveTo(at >= 0 ? at : forward ? firstStop() : lastStop());
        }
    }

    @Override
    protected boolean activate() {
        layout();
        if (!accepts()) {
            return false;
        }
        if (cursorOnToggle) {
            BaseComponent.click();
            toggle();
            return true;
        }
        if (cursor < 0) {
            moveTo(firstStop());
        }
        if (cursor < 0) {
            return false;
        }
        press(cursor, true);
        return true;
    }

    private boolean stop(int row) {
        Row r = rows.get(row);
        return r.kind == Kind.ITEM && r.item.enabled;
    }

    private int firstStop() {
        return stepFrom(-1, 1);
    }

    private int lastStop() {
        return stepFrom(rows.size(), -1);
    }

    private int stepFrom(int from, int by) {
        for (int i = from + by; i >= 0 && i < rows.size(); i += by) {
            if (stop(i)) {
                return i;
            }
        }
        return -1;
    }

    private void moveTo(int row) {
        if (row < 0) {
            return;
        }
        cursor = row;
        cursorItem = rows.get(row).item;
        cursorOnToggle = false;
        reveal(row);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        layout();
        if (!accepts() || button != 0 || !isOver(mx, my)) {
            return false;
        }
        if (overBar(mx, my)) {
            clampScroll();
            bar.press(mx, my, barX(), viewY(), viewH());
            return true;
        }
        if (overToggle(mx, my)) {
            BaseComponent.click();
            toggle();
            return true;
        }
        int at = rowAt(mx, my);
        if (at >= 0) {
            press(at, false);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return bar.drag(my);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = bar.dragging();
        bar.release();
        return was;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!accepts() || !isOver(mx, my) || !overflows()) {
            return false;
        }
        clampScroll();
        bar.wheel(amount, ScrollBar.WHEEL_ROWS * ROW);
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        layout();
        if (!accepts()) {
            return false;
        }
        switch (key) {
            case UP -> {
                if (cursorOnToggle) {
                    moveTo(lastStop());
                } else {
                    int next = cursor < 0 ? lastStop() : stepFrom(cursor, -1);
                    if (next >= 0) {
                        moveTo(next);
                    }
                }
                return true;
            }
            case DOWN -> {
                if (cursorOnToggle) {
                    return true;
                }
                int next = cursor < 0 ? firstStop() : stepFrom(cursor, 1);
                if (next >= 0) {
                    moveTo(next);
                } else if (showsToggle()) {
                    cursor = -1;
                    cursorItem = null;
                    cursorOnToggle = true;
                }
                return true;
            }
            case HOME -> {
                moveTo(firstStop());
                return true;
            }
            case END -> {
                if (showsToggle()) {
                    cursor = -1;
                    cursorItem = null;
                    cursorOnToggle = true;
                } else {
                    moveTo(lastStop());
                }
                return true;
            }
            case RIGHT -> {
                keyRight();
                return true;
            }
            case LEFT -> {
                keyLeft();
                return true;
            }
            case ENTER, SPACE -> {
                return activate();
            }
            default -> {
                return false;
            }
        }
    }

    private void keyRight() {
        if (cursor < 0) {
            return;
        }
        Item item = rows.get(cursor).item;
        if (!item.hasChildren() || !item.enabled) {
            return;
        }
        if (rail()) {
            press(cursor, true);
        } else if (item.expanded) {
            int next = stepFrom(cursor, 1);
            if (next >= 0 && rows.get(next).parent == cursor) {
                moveTo(next);
            }
        } else {
            BaseComponent.click();
            item.expanded = true;
            stale = true;
        }
    }

    private void keyLeft() {
        if (cursor < 0 || rail()) {
            return;
        }
        Row row = rows.get(cursor);
        if (row.item.hasChildren() && row.item.expanded) {
            BaseComponent.click();
            row.item.expanded = false;
            stale = true;
        } else if (row.parent >= 0) {
            moveTo(row.parent);
        }
    }
}
