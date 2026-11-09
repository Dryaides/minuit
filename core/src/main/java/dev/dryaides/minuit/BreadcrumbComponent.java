package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * A path of places, last one being where the player is. When the path does not fit, the middle folds into
 * an ellipsis that opens a menu of what it hides.
 *
 * <pre>{@code
 * BreadcrumbComponent path = add(new BreadcrumbComponent(at(new Box("path", 10, 10, 260, 14)), this, this::goTo)
 *         .crumbs("Worlds", "Survival", "Backups", "Today"));
 * // ... the other widgets ...
 * add(path.menu()); // last: the menu of the ellipsis
 * }</pre>
 *
 * <p>The callback gets the index of a crumb. The last crumb is the current place: it is not pressable. What
 * is kept when space runs out, in order: the first crumb, the ellipsis and as many crumbs from the end as fit;
 * then the same with the last one cut short; then the ellipsis and the last one; then the last one alone.
 *
 * <h2>Keys</h2>
 * Left and right move between crumbs and go on to the panel's next widget past either end, Home and End jump,
 * Enter or Space press. The ellipsis opens its menu, which comes back here when it closes.
 *
 * <p>Without {@link #menu()} added, the ellipsis shows but does nothing. The menu is a real
 * {@link ContextMenuComponent}, with its rule: it comes after every other widget of the panel.
 */
public class BreadcrumbComponent extends BaseComponent {
    /** One place of the path. */
    public static final class Crumb {
        private final String label;
        private Icon icon;
        private IconArt iconArt;
        private boolean enabled = true;
        private List<String> tooltip;

        private Crumb(String label) {
            this.label = label == null ? "" : label;
        }

        public static Crumb of(String label) {
            return new Crumb(label);
        }

        public Crumb icon(Icon icon) {
            this.icon = icon;
            return this;
        }

        public Crumb iconArt(IconArt iconArt) {
            this.iconArt = iconArt;
            return this;
        }

        /** Lines for the pointer on this crumb. A label cut short shows in full without this. */
        public Crumb tooltip(String... lines) {
            this.tooltip = lines == null || lines.length == 0 ? null : List.of(lines);
            return this;
        }

        public String label() {
            return label;
        }

        private IconArt art(Theme t) {
            return iconArt != null ? iconArt : icon != null ? t.icon(icon) : null;
        }
    }

    private static final int PAD = 3;
    private static final int ICON_GAP = 3;
    private static final int SEP_GAP = 3;
    /** Below this a label is no use: something else gives way first. */
    private static final int MIN_CUT = 48;
    private static final int ELLIPSIS_SLOT = -1;

    private final PanelComponent host;
    private final IntConsumer onPick;
    private final RowLook.HoverFade hover = new RowLook.HoverFade();
    private final List<Crumb> crumbs = new ArrayList<>();
    private ContextMenuComponent menu;
    private int maxCrumb = 110;

    // The layout, worked out when the path, the width or the theme changes, not on every frame.
    private boolean stale = true;
    private Theme laidTheme;
    private int laidW = -1;
    private int count;
    private int[] slotCrumb = new int[0];
    private int[] slotX = new int[0];
    private int[] slotW = new int[0];
    private String[] slotText = new String[0];
    private int hiddenFrom = -1;
    private int hiddenTo = -1;
    private int sepW;

    private int cursor = -1;
    private int held = -1;

    /**
     * @param box where the path is drawn: it starts at the left end and does not leave the box
     * @param host the panel this breadcrumb is added to
     * @param onPick called with a crumb's index; may be null
     */
    public BreadcrumbComponent(Box box, PanelComponent host, IntConsumer onPick) {
        super(box);
        this.host = Objects.requireNonNull(host, "host");
        this.onPick = onPick;
    }

    // ---- setup ----

    public BreadcrumbComponent crumbs(String... labels) {
        List<Crumb> list = new ArrayList<>();
        for (String label : labels) {
            list.add(Crumb.of(label));
        }
        return crumbs(list);
    }

    /**
     * Greys out crumb {@code index}: it stays on the path but cannot be gone to. The {@code tip} lines show over
     * it, to say why; none shows nothing.
     */
    public BreadcrumbComponent disabled(int index, boolean disabled, String... tip) {
        Crumb crumb = crumbs.get(index);
        crumb.enabled = !disabled;
        crumb.tooltip(tip);
        return this;
    }

    public final BreadcrumbComponent crumbs(List<Crumb> path) {
        crumbs.clear();
        if (path != null) {
            for (Crumb crumb : path) {
                if (crumb != null) {
                    crumbs.add(crumb);
                }
            }
        }
        cursor = -1;
        held = -1;
        stale = true;
        return this;
    }

    /** The widest a crumb gets before its label is cut, the last one included. 110 by default. */
    public BreadcrumbComponent maxCrumbWidth(int width) {
        maxCrumb = Math.max(MIN_CUT, width);
        stale = true;
        return this;
    }

    /** The menu of the ellipsis, in the panel's bounds: add it after every other widget. */
    public ContextMenuComponent menu() {
        return menu(host.bounds());
    }

    /** The menu of the ellipsis, staying in this box: add it after every other widget. */
    public ContextMenuComponent menu(Box area) {
        if (menu == null) {
            menu = new ContextMenuComponent(area, host);
        }
        return menu;
    }

    @Override
    public BreadcrumbComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- reading, for a script or a test ----

    int crumbCount() {
        return crumbs.size();
    }

    /** How many places are drawn, the ellipsis being one. Up to date after the first frame. */
    public int slotCount() {
        layout();
        return count;
    }

    /** The crumb of a place, or -1 for the ellipsis. */
    int slotCrumb(int slot) {
        layout();
        return slotCrumb[slot];
    }

    Box slotBox(int slot) {
        layout();
        return new Box("slot" + slot, x + slotX[slot], y, slotW[slot], h);
    }

    public String slotText(int slot) {
        layout();
        return slotText[slot];
    }

    /** The first and last crumb the ellipsis hides, or -1 twice. */
    public int[] hiddenRange() {
        layout();
        return new int[] {hiddenFrom, hiddenTo};
    }

    /** The place the keyboard is on, or -1. */
    public int cursorSlot() {
        return cursor;
    }

    // ---- layout ----

    private void ensureSlots(int size) {
        if (slotCrumb.length < size) {
            slotCrumb = new int[size];
            slotX = new int[size];
            slotW = new int[size];
            slotText = new String[size];
        }
    }

    private int fullWidth(Crumb crumb, Theme t) {
        IconArt art = crumb.art(t);
        int inner = TextMeasure.width(crumb.label) + (art == null ? 0 : artSide(art) + (crumb.label.isEmpty() ? 0 : ICON_GAP));
        return Math.min(maxCrumb, inner + 2 * PAD);
    }

    /** The height art may take in the bar: a pixel above and below. */
    private int artRoom() {
        return Math.max(1, h - 2);
    }

    /** The width art takes once fitted to the bar. */
    private int artSide(IconArt art) {
        return ItemArt.rowWidth(art, artRoom());
    }

    private void layout() {
        Theme t = host.theme();
        if (!stale && t == laidTheme && w == laidW) {
            return;
        }
        stale = false;
        laidTheme = t;
        laidW = w;
        int n = crumbs.size();
        ensureSlots(Math.max(n + 1, 3));
        count = 0;
        hiddenFrom = -1;
        hiddenTo = -1;
        sepW = t.icon(Icon.NEXT).width() + 2 * SEP_GAP;
        if (n > 0 && w > 0) {
            int[] full = new int[n];
            int total = -sepW;
            for (int i = 0; i < n; i++) {
                full[i] = fullWidth(crumbs.get(i), t);
                total += full[i] + sepW;
            }
            if (total <= w) {
                for (int i = 0; i < n; i++) {
                    push(i, full[i]);
                }
            } else {
                fold(full, n);
            }
            place(t);
        }
        if (cursor >= count) {
            cursor = lastStop();
        }
    }

    private void fold(int[] full, int n) {
        int last = n - 1;
        int ellipsis = TextMeasure.width(Draw.ELLIPSIS) + 2 * PAD;
        if (n == 1) {
            push(0, Math.min(full[0], w));
            return;
        }
        int minLast = Math.min(full[last], MIN_CUT);
        if (n == 2) {
            int first = Math.min(full[0], Math.max(MIN_CUT, w / 3));
            if (first + sepW + minLast <= w) {
                push(0, first);
                push(1, Math.min(full[1], w - first - sepW));
            } else {
                push(1, Math.min(full[1], w));
            }
            return;
        }
        int base = full[0] + 2 * sepW + ellipsis;
        if (base + full[last] <= w) {
            int used = base + full[last];
            int keepFrom = last;
            while (keepFrom - 1 > 1 && used + sepW + full[keepFrom - 1] <= w) {
                keepFrom--;
                used += sepW + full[keepFrom];
            }
            push(0, full[0]);
            pushEllipsis(ellipsis, 1, keepFrom - 1);
            for (int i = keepFrom; i < n; i++) {
                push(i, full[i]);
            }
        } else if (base + minLast <= w) {
            push(0, full[0]);
            pushEllipsis(ellipsis, 1, last - 1);
            push(last, w - base);
        } else if (ellipsis + sepW + minLast <= w) {
            pushEllipsis(ellipsis, 0, last - 1);
            push(last, Math.min(full[last], w - ellipsis - sepW));
        } else {
            push(last, Math.min(full[last], w));
        }
    }

    private void push(int crumb, int width) {
        slotCrumb[count] = crumb;
        slotW[count] = width;
        count++;
    }

    private void pushEllipsis(int width, int from, int to) {
        hiddenFrom = from;
        hiddenTo = to;
        push(ELLIPSIS_SLOT, width);
    }

    /** Places the slots and cuts their labels: done here once, so that a frame only reads strings. */
    private void place(Theme t) {
        Draw measure = TextMeasure.draw();
        int at = 0;
        for (int s = 0; s < count; s++) {
            slotX[s] = at;
            at += slotW[s] + sepW;
            if (slotCrumb[s] == ELLIPSIS_SLOT) {
                slotText[s] = Draw.ELLIPSIS;
            } else {
                Crumb crumb = crumbs.get(slotCrumb[s]);
                IconArt art = crumb.art(t);
                int room = slotW[s] - 2 * PAD - (art == null ? 0 : artSide(art) + (crumb.label.isEmpty() ? 0 : ICON_GAP));
                slotText[s] = room <= 0 ? "" : measure.ellipsize(crumb.label, room);
            }
        }
    }

    private boolean isCurrent(int slot) {
        return slotCrumb[slot] == crumbs.size() - 1;
    }

    private boolean pressable(int slot) {
        int crumb = slotCrumb[slot];
        if (crumb == ELLIPSIS_SLOT) {
            return menu != null;
        }
        return !isCurrent(slot) && crumbs.get(crumb).enabled;
    }

    private int firstStop() {
        return stepFrom(-1, 1);
    }

    private int lastStop() {
        return stepFrom(count, -1);
    }

    private int stepFrom(int from, int by) {
        for (int s = from + by; s >= 0 && s < count; s += by) {
            if (pressable(s)) {
                return s;
            }
        }
        return -1;
    }

    private int slotAt(double mx, double my) {
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

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        layout();
        Palette p = t.palette();
        int under = NavLook.solid(p.surface());
        int over = active && hovered ? slotAt(mx, my) : -1;
        if (over >= 0 && !pressable(over)) {
            over = -1;
        }
        hover.step(over);
        d.scissor(x, y, x + w, y + h);
        IconArt sep = t.icon(Icon.NEXT);
        int sepInk = active ? Ink.soft(p, under) : NavLook.disabled(p, under);
        for (int s = 0; s < count; s++) {
            drawSlot(d, t, p, s, under, s == over);
            if (s + 1 < count) {
                sep.draw(d, x + slotX[s] + slotW[s] + SEP_GAP, y + (h - sep.height()) / 2, sepInk, 1F);
            }
        }
        d.endScissor();
    }

    private void drawSlot(Draw d, Theme t, Palette p, int s, int under, boolean over) {
        int sx = x + slotX[s];
        int sw = slotW[s];
        int crumbIndex = slotCrumb[s];
        boolean greyed = !active || (crumbIndex != ELLIPSIS_SLOT && !crumbs.get(crumbIndex).enabled);
        int ink;
        if (greyed) {
            ink = NavLook.disabled(p, under);
        } else if (over || (crumbIndex != ELLIPSIS_SLOT && isCurrent(s))) {
            ink = Ink.on(p, under);
        } else {
            ink = Ink.soft(p, under);
        }
        if (over) {
            int alpha = held == s ? NavLook.WASH_HELD : Math.round(NavLook.WASH_HOVER * hover.fade());
            t.frame().row(d, p, sx, y, sw, h, alpha > 0 ? Shapes.alpha(Ink.on(p, under), alpha) : 0);
        }
        int textY = RowLook.textY(y, h);
        int cx = sx + PAD;
        if (crumbIndex == ELLIPSIS_SLOT) {
            d.text(slotText[s], cx, textY, ink);
            return;
        }
        IconArt art = crumbs.get(crumbIndex).art(t);
        if (art != null) {
            // Fitted to h - 2 (crisp n/gs shrink), veiled when greyed: an item ignores the grey ink. No item tooltip.
            ItemArt.inRow(d, p, art, cx, y, h, artRoom(), ink, !greyed);
            cx += artSide(art) + ICON_GAP;
        }
        if (!slotText[s].isEmpty()) {
            d.text(slotText[s], cx, textY, ink);
        }
    }

    /** The ring goes round the crumb the keyboard is on, on its edges: a panel grows the box by 2 for its ring. */
    @Override
    protected Box focusBox() {
        layout();
        int at = cursor >= 0 && cursor < count ? cursor : -1;
        if (at < 0) {
            return super.focusBox();
        }
        return new Box("crumb", x + slotX[at] + 2, y + 2, Math.max(0, slotW[at] - 4), Math.max(0, h - 4));
    }

    @Override
    protected int focusCorner() {
        return cursor >= 0 && cursor < count ? 0 : super.focusCorner();
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        layout();
        int s = slotAt(mx, my);
        if (s < 0) {
            return tooltip;
        }
        int crumbIndex = slotCrumb[s];
        if (crumbIndex == ELLIPSIS_SLOT) {
            return tooltip;
        }
        Crumb crumb = crumbs.get(crumbIndex);
        if (crumb.tooltip != null) {
            return crumb.tooltip;
        }
        return slotText[s].equals(crumb.label) ? tooltip : List.of(crumb.label);
    }

    // ---- input ----

    private void press(int slot) {
        BaseComponent.click();
        if (slotCrumb[slot] == ELLIPSIS_SLOT) {
            openMenu(slot);
        } else if (onPick != null) {
            onPick.accept(slotCrumb[slot]);
        }
    }

    private void openMenu(int slot) {
        List<ContextMenuComponent.Item> items = new ArrayList<>();
        for (int i = hiddenFrom; i <= hiddenTo; i++) {
            Crumb crumb = crumbs.get(i);
            int index = i;
            items.add(ContextMenuComponent.Item.action(crumb.label, () -> {
                if (onPick != null) {
                    onPick.accept(index);
                }
            }).icon(crumb.icon).iconArt(crumb.iconArt).enabled(crumb.enabled));
        }
        menu.items(items);
        menu.openBelow(new Box("ellipsis", x + slotX[slot], y, slotW[slot], h));
    }

    @Override
    protected boolean takesFocus() {
        return visible && active;
    }

    @Override
    protected boolean navigable() {
        layout();
        return visible && active && settled() && firstStop() >= 0;
    }

    @Override
    protected void enter(boolean forward) {
        layout();
        cursor = forward ? firstStop() : lastStop();
    }

    @Override
    protected boolean activate() {
        layout();
        if (!active || !visible || count == 0) {
            return false;
        }
        if (cursor < 0 || cursor >= count || !pressable(cursor)) {
            cursor = firstStop();
        }
        if (cursor < 0) {
            return false;
        }
        press(cursor);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        layout();
        if (!active || !visible || button != 0) {
            return false;
        }
        int s = slotAt(mx, my);
        if (s < 0 || !pressable(s)) {
            return false;
        }
        held = s;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        int was = held;
        held = -1;
        if (was < 0 || was >= count || button != 0) {
            return false;
        }
        if (slotAt(mx, my) == was && pressable(was)) {
            cursor = was;
            press(was);
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
                int by = key == InputKey.LEFT ? -1 : 1;
                int next = cursor < 0 ? (by > 0 ? firstStop() : lastStop()) : stepFrom(cursor, by);
                if (next < 0) {
                    return false; // past the end: the panel goes on to its next widget
                }
                cursor = next;
                return true;
            }
            case HOME -> {
                cursor = firstStop();
                return cursor >= 0;
            }
            case END -> {
                cursor = lastStop();
                return cursor >= 0;
            }
            case ENTER, SPACE -> {
                return activate();
            }
            default -> {
                return false;
            }
        }
    }
}
