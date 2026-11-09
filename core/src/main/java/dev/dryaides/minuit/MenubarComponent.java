package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Menus across the top of a screen. The menus themselves are a {@link ContextMenuComponent}: the same rows,
 * submenus, checks, shortcuts and keys. While one is open, the pointer on another title switches to it, and left
 * and right walk the bar.
 *
 * <pre>{@code
 * MenubarComponent bar = add(new MenubarComponent(at(new Box("bar", 0, 0, 300, 14)), this)
 *         .menu("File",
 *                 Item.action("New", this::create).icon(Icon.PLUS).shortcut("Ctrl+N"),
 *                 Item.separator(),
 *                 Item.submenu("Recent", Item.action("Survival", this::openSurvival)))
 *         .menu("View", Item.check("Grid", grid, on -> grid = on)));
 * // ... the other widgets ...
 * add(bar.popup()); // last
 * }</pre>
 *
 * <h2>Putting it in a panel</h2>
 * Add {@link #popup()} after every other widget, and before any {@link ContextMenuComponent} of the panel,
 * which insists on being the very last. The box of the popup is where menus may go: the panel by default.
 *
 * <h2>Keys</h2>
 * On the bar: left and right move and wrap, Home and End jump, Down, Enter or Space open with the first item
 * lit. In a menu: its own keys, and left at the first level or right on an item with no submenu move to the
 * next menu. Escape comes back to the bar. A title that does not fit moves into an ellipsis menu at the bar's end.
 *
 * <p>{@code active = false} or {@code visible = false} closes an open menu.
 */
public class MenubarComponent extends BaseComponent {
    private static final int PAD = 5;
    private static final int MAX_TITLE = 100;
    private static final int RING_ROOM = 2;
    private static final int OVERFLOW = -1;

    /** One title and its items. */
    private static final class Menu {
        final String label;
        final List<ContextMenuComponent.Item> items;
        boolean enabled = true;

        Menu(String label, List<ContextMenuComponent.Item> items) {
            this.label = label == null ? "" : label;
            this.items = items;
        }
    }

    /**
     * Holds the real menu alone, so that its rule about coming last and its grip on the focus stay between the
     * two of them. The real panel's focus is on the {@link Overlay}, which can then read left and right first.
     */
    private final class Shelf extends PanelComponent {
        final Box area;

        Shelf(Box area) {
            super(host.theme(), host.words, 0, 0);
            this.area = area;
        }

        ContextMenuComponent hold(ContextMenuComponent menu) {
            return add(menu);
        }

        @Override public Theme theme() { return host.theme(); }
        @Override public int width() { return area.w(); }
        @Override public int height() { return area.h(); }
        @Override protected void drawBackground(Draw d, int mx, int my) { }
    }

    /** Draws the open menu and takes all input while it is open. */
    private final class Overlay extends BaseComponent {
        Overlay(Box area) {
            super(area);
        }

        private int lastMx;
        private int lastMy;

        @Override
        protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
            boolean moved = mx != lastMx || my != lastMy;
            lastMx = mx;
            lastMy = my;
            if (!sync()) {
                return;
            }
            if (host.focus() != this) {
                host.focus(this); // the bar takes the focus after the press that opened the menu
            }
            // Only a pointer that moved: one at rest on a title must not fight the arrow keys.
            int title = moved ? slotAt(mx, my) : -1;
            if (title >= 0 && title != openSlot && slotEnabled(title)) {
                openSlot(title, false);
            }
            menu.draw(d, t, mx, my, delta);
        }

        @Override
        public void tick() {
            if (sync()) {
                menu.tick();
            }
        }

        @Override
        public boolean isOver(double mx, double my) {
            return menu.isOpen();
        }

        @Override
        protected boolean takesFocus() {
            return menu.isOpen();
        }

        @Override
        protected boolean navigable() {
            return menu.isOpen();
        }

        @Override
        public List<BaseComponent> stops() {
            return menu.isOpen() ? List.of(this) : List.of();
        }

        @Override
        protected boolean drawFocus(Draw d, Theme t) {
            return true;
        }

        @Override
        protected boolean activate() {
            return sync() && menu.keyPressed(InputKey.ENTER, false);
        }

        @Override
        public List<String> tooltipAt(double mx, double my) {
            return sync() ? menu.tooltipAt(mx, my) : null;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (!sync()) {
                return false;
            }
            int title = slotAt(mx, my);
            if (title >= 0) {
                if (title == openSlot) {
                    BaseComponent.click();
                    menu.close();
                } else if (slotEnabled(title)) {
                    openSlot(title, false);
                }
                return true;
            }
            return menu.mouseClicked(mx, my, button);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            return menu.isOpen() && menu.mouseDragged(mx, my, button, dx, dy);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            return menu.isOpen() && menu.mouseReleased(mx, my, button);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double amount) {
            return sync() && menu.mouseScrolled(mx, my, amount);
        }

        @Override
        public boolean keyPressed(InputKey key, boolean shift) {
            if (!sync()) {
                return false;
            }
            if (key == InputKey.LEFT && menu.depth() == 1) {
                walkOpen(-1);
                return true;
            }
            if (key == InputKey.RIGHT) {
                int depth = menu.depth();
                String lit = menu.currentLabel();
                menu.keyPressed(key, shift);
                if (menu.isOpen() && menu.depth() == depth && Objects.equals(menu.currentLabel(), lit)) {
                    walkOpen(1); // the item had no submenu to go into
                }
                return true;
            }
            return menu.keyPressed(key, shift);
        }

        @Override
        public boolean charTyped(int codePoint) {
            return sync() && menu.charTyped(codePoint);
        }
    }

    private final PanelComponent host;
    private final List<Menu> menus = new ArrayList<>();
    private final RowLook.HoverFade hover = new RowLook.HoverFade();
    private ContextMenuComponent menu;
    /** By item id, the last {@code disabled(id, ...)} call's flag and tip; a later call replaces it. */
    private final Map<String, Object[]> itemOffs = new LinkedHashMap<>();
    private Overlay overlay;
    private String overflowLabel = Draw.ELLIPSIS;
    private boolean background = true;

    private boolean stale = true;
    private int laidW = -1;
    private int count;
    private int[] slotMenu = new int[0];
    private int[] slotX = new int[0];
    private int[] slotW = new int[0];
    private String[] slotText = new String[0];
    private int firstHidden = -1;

    private int openSlot = -1;
    private int cursor = -1;

    /**
     * @param box the bar
     * @param host the panel this bar is added to
     */
    public MenubarComponent(Box box, PanelComponent host) {
        super(box);
        this.host = Objects.requireNonNull(host, "host");
    }

    /** Adds a menu at the bar's end. A menu with no items shows greyed. */
    public MenubarComponent menu(String label, ContextMenuComponent.Item... items) {
        return menu(label, Arrays.asList(items));
    }

    /** Adds a menu at the bar's end. A menu with no items shows greyed. */
    public MenubarComponent menu(String label, List<ContextMenuComponent.Item> items) {
        menus.add(new Menu(label, items == null ? new ArrayList<>() : new ArrayList<>(items)));
        stale = true;
        return this;
    }

    /** Greys a menu's title, by its place in the order of adding. The {@code tip} lines are not shown by a menubar. */
    public MenubarComponent disabled(int index, boolean disabled, String... tip) {
        menus.get(index).enabled = !disabled;
        return this;
    }

    /**
     * Greys out the menu item labelled {@code id}, at any depth, as {@link ContextMenuComponent#disabled} does. The
     * {@code tip} lines show over the item. Can be called before {@link #popup()}.
     */
    public MenubarComponent disabled(String id, boolean disabled, String... tip) {
        Objects.requireNonNull(id, "id");
        itemOffs.put(id, new Object[] {disabled, tip});
        if (menu != null) {
            menu.disabled(id, disabled, tip);
        }
        return this;
    }

    /** False draws the titles straight on what is beneath. On by default. */
    public MenubarComponent background(boolean background) {
        this.background = background;
        return this;
    }

    /** What the menus draw with: add it after every other widget. Menus stay in the panel's bounds. */
    public BaseComponent popup() {
        return popup(host.bounds());
    }

    /** What the menus draw with: add it after every other widget. Menus stay in this box. */
    public BaseComponent popup(Box area) {
        if (overlay == null) {
            overlay = new Overlay(area);
            Shelf shelf = new Shelf(area);
            menu = shelf.hold(new ContextMenuComponent(area, shelf));
            menu.onClose(this::closed);
            itemOffs.forEach((id, off) -> menu.disabled(id, (Boolean) off[0], (String[]) off[1]));
        }
        return overlay;
    }

    @Override
    public MenubarComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    public boolean isOpen() {
        return menu != null && menu.isOpen();
    }

    /** The menu showing now, or null: for its current item and its places. */
    public ContextMenuComponent openMenu() {
        return isOpen() ? menu : null;
    }

    /** The place on the bar of the open menu, or -1. */
    public int openIndex() {
        return isOpen() ? openSlot : -1;
    }

    int slotCount() {
        layout();
        return count;
    }

    Box slotBox(int slot) {
        layout();
        return new Box("title" + slot, x + slotX[slot], y, slotW[slot], h);
    }

    String slotText(int slot) {
        layout();
        return slotText[slot];
    }

    int cursorSlot() {
        return cursor;
    }

    private int titleWidth(String label) {
        return Math.min(MAX_TITLE, TextMeasure.width(label) + 2 * PAD);
    }

    private void layout() {
        if (!stale && laidW == w) {
            return;
        }
        stale = false;
        laidW = w;
        int n = menus.size();
        if (slotMenu.length < n + 1) {
            slotMenu = new int[n + 1];
            slotX = new int[n + 1];
            slotW = new int[n + 1];
            slotText = new String[n + 1];
        }
        count = 0;
        firstHidden = -1;
        int total = 0;
        for (Menu m : menus) {
            total += titleWidth(m.label);
        }
        Draw measure = TextMeasure.draw();
        int at = 0;
        int reserve = total <= w ? 0 : titleWidth(overflowLabel);
        for (int i = 0; i < n; i++) {
            int width = titleWidth(menus.get(i).label);
            if (at + width + reserve > w) {
                firstHidden = i;
                break;
            }
            pushSlot(i, at, width, measure.ellipsize(menus.get(i).label, width - 2 * PAD));
            at += width;
        }
        if (firstHidden >= 0 && at + reserve <= w) {
            pushSlot(OVERFLOW, at, reserve, overflowLabel);
        }
        if (cursor >= count) {
            cursor = count - 1;
        }
    }

    private void pushSlot(int index, int at, int width, String text) {
        slotMenu[count] = index;
        slotX[count] = at;
        slotW[count] = width;
        slotText[count] = text;
        count++;
    }

    private int slotAt(double mx, double my) {
        layout();
        if (my < y || my >= y + h) {
            return -1;
        }
        for (int s = 0; s < count; s++) {
            if (mx >= x + slotX[s] && mx < x + slotX[s] + slotW[s]) {
                return s;
            }
        }
        return -1;
    }

    private boolean slotEnabled(int slot) {
        int index = slotMenu[slot];
        if (index == OVERFLOW) {
            return true;
        }
        Menu m = menus.get(index);
        return m.enabled && !m.items.isEmpty();
    }

    private int walk(int from, int by) {
        for (int i = 1; i <= count; i++) {
            int at = Math.floorMod(from + by * i, count);
            if (slotEnabled(at)) {
                return at;
            }
        }
        return -1;
    }

    private List<ContextMenuComponent.Item> itemsOf(int slot) {
        int index = slotMenu[slot];
        if (index != OVERFLOW) {
            return menus.get(index).items;
        }
        List<ContextMenuComponent.Item> items = new ArrayList<>();
        for (int i = firstHidden; i < menus.size(); i++) {
            Menu m = menus.get(i);
            ContextMenuComponent.Item sub = ContextMenuComponent.Item.submenu(m.label, m.items);
            items.add(m.enabled ? sub : sub.enabled(false));
        }
        return items;
    }

    private void openSlot(int slot, boolean lightFirst) {
        if (menu == null || !slotEnabled(slot)) {
            return;
        }
        host.closeOverlay();
        BaseComponent.click();
        openSlot = slot;
        cursor = slot;
        menu.items(itemsOf(slot));
        Box anchor = new Box("title", x + slotX[slot], y, slotW[slot], h);
        boolean opened = lightFirst ? menu.openBelow(anchor) : menu.open(anchor.x(), anchor.bottom() + RING_ROOM);
        if (opened) {
            host.focus(overlay);
        } else {
            openSlot = -1;
        }
    }

    private void walkOpen(int by) {
        int next = walk(openSlot, by);
        if (next >= 0 && next != openSlot) {
            openSlot(next, true);
        }
    }

    private void closed() {
        openSlot = -1;
        if (host.focus() == overlay) {
            host.focus(this);
        }
    }

    /** Hidden or disabled closes the menu. Returns whether one is open. */
    private boolean sync() {
        if (menu == null) {
            return false;
        }
        if (menu.isOpen() && (!visible || !active)) {
            menu.close();
        }
        return menu.isOpen();
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        layout();
        Palette p = t.palette();
        if (background) {
            t.frame().section(d, p, x, y, w, h);
        }
        int surface = NavLook.solid(p.surface());
        int section = p.section();
        int under = background ? Shapes.mix(surface, NavLook.solid(section), (section >>> 24) / 255F) : surface;
        boolean open = sync();
        int over = active && hovered && !open ? slotAt(mx, my) : -1;
        if (over >= 0 && !slotEnabled(over)) {
            over = -1;
        }
        hover.step(over);
        d.scissor(x, y, x + w, y + h);
        int textY = RowLook.textY(y, h);
        for (int s = 0; s < count; s++) {
            int sx = x + slotX[s];
            int sw = slotW[s];
            boolean lit = open && s == openSlot;
            int ink;
            if (lit) {
                t.frame().row(d, p, sx, y, sw, h, p.primary());
                ink = p.primaryText();
            } else if (!active || !slotEnabled(s)) {
                ink = NavLook.disabled(p, under);
            } else {
                ink = Ink.on(p, under);
                if (s == over) {
                    t.frame().row(d, p, sx, y, sw, h, Shapes.alpha(ink, Math.round(NavLook.WASH_HOVER * hover.fade())));
                }
            }
            d.text(slotText[s], sx + PAD, textY, ink);
        }
        d.endScissor();
    }

    @Override
    protected Box focusBox() {
        layout();
        if (cursor >= 0 && cursor < count) {
            // A panel grows this by 2 for its ring, which then lies on the title's edges, inside the bar.
            return new Box("title", x + slotX[cursor] + 2, y + 2, Math.max(0, slotW[cursor] - 4), Math.max(0, h - 4));
        }
        return super.focusBox();
    }

    @Override
    protected int focusCorner() {
        return cursor >= 0 && cursor < count ? 0 : super.focusCorner();
    }

    /** While a menu is open its title is lit: a ring there would only add noise. */
    @Override
    protected boolean drawFocus(Draw d, Theme t) {
        return isOpen() || super.drawFocus(d, t);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        layout();
        int s = slotAt(mx, my);
        if (s >= 0 && slotMenu[s] != OVERFLOW && !slotText[s].equals(menus.get(slotMenu[s]).label)) {
            return List.of(menus.get(slotMenu[s]).label);
        }
        return tooltip;
    }

    @Override
    protected boolean takesFocus() {
        return visible && active;
    }

    @Override
    protected boolean navigable() {
        layout();
        return visible && active && settled() && walk(-1, 1) >= 0 && count > 0;
    }

    @Override
    protected void enter(boolean forward) {
        layout();
        if (cursor < 0 || cursor >= count || !slotEnabled(cursor)) {
            cursor = count == 0 ? -1 : forward ? walk(-1, 1) : walk(0, -1);
        }
    }

    @Override
    protected boolean activate() {
        layout();
        if (!active || !visible || count == 0) {
            return false;
        }
        if (cursor < 0 || !slotEnabled(cursor)) {
            cursor = walk(-1, 1);
        }
        if (cursor < 0) {
            return false;
        }
        openSlot(cursor, true);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!active || !visible || button != 0) {
            return false;
        }
        int s = slotAt(mx, my);
        if (s < 0) {
            return false;
        }
        if (slotEnabled(s)) {
            openSlot(s, false);
        }
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        layout();
        if (!active || !visible || count == 0) {
            return false;
        }
        switch (key) {
            case LEFT, RIGHT -> {
                int next = walk(cursor < 0 ? (key == InputKey.LEFT ? 0 : -1) : cursor, key == InputKey.LEFT ? -1 : 1);
                if (next >= 0) {
                    cursor = next;
                }
                return true;
            }
            case HOME -> {
                cursor = walk(-1, 1);
                return true;
            }
            case END -> {
                cursor = walk(0, -1);
                return true;
            }
            case DOWN, ENTER, SPACE -> {
                return activate();
            }
            default -> {
                return false;
            }
        }
    }
}
