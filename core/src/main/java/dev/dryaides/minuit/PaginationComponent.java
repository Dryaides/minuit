package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Page buttons with an ellipsis for the pages between, and an optional page size. The buttons are real
 * {@link ButtonComponent}s, so they look and sound like every other button of the theme.
 *
 * <pre>{@code
 * PaginationComponent pages = add(new PaginationComponent(at(new Box("pages", 10, 150, 280, 16)), this, 240, 25, this::showPage)
 *         .pageSizes(10, 25, 50)
 *         .onPageSize(this::resize));
 * // ... the other widgets ...
 * add(pages.menu()); // last, for the list of sizes
 * }</pre>
 *
 * <p>Pages count from 0 in the API and from 1 on screen. The number of buttons does not change from page to
 * page, so nothing moves under the pointer while paging. When the box is too narrow it gives way in this order:
 * the neighbours of the current page, the page size, the page numbers (for {@code 3 / 12}), then that text.
 *
 * <h2>Input</h2>
 * Left and right change the page, with shift by the jump, Home and End go to either end, up and down change the
 * page size. The wheel over the widget changes the page. A click on an ellipsis jumps by 5 pages
 * ({@link #jump(int)}). The sizes open a {@link ContextMenuComponent} when {@link #menu()} is added, with its rule:
 * after every other widget of the panel. Without it, a click goes to the next size.
 */
public class PaginationComponent extends BaseComponent {
    private static final int GAP = 3;
    private static final int SIZE_GAP = 8;
    private static final int MIN_CELL = 14;
    private static final int CELL_PAD = 4;
    private static final int LABEL_PAD = 6;
    private static final int SIZE_PAD = 5;
    /** The neighbours of the current page, the first and last page, and the two ellipses. */
    private static final int FIXED_SLOTS = 5;

    private enum Kind { PREV, NEXT, PAGE, GAP_BACK, GAP_FORWARD, TEXT }

    private final PanelComponent host;
    private final IntConsumer onPage;
    private final RowLook.HoverFade gapHover = new RowLook.HoverFade();
    private final List<ButtonComponent> pool = new ArrayList<>();
    private final ButtonComponent prev;
    private final ButtonComponent next;
    private final ButtonComponent sizeButton;

    private int total;
    private int pageSize;
    private int page;
    private int siblings = 1;
    private int jump = 5;
    private boolean centered;
    private int[] sizes = new int[0];
    private static final String SIZE_KEY = "minuit.page.size";
    /** The mod's own, or null for the lang entry (English when the lang file lacks it). */
    private IntFunction<String> sizeLabel;
    private Words words;
    private IntConsumer onPageSize;
    private ContextMenuComponent menu;

    // The layout, worked out when something changes, not on every frame.
    private boolean stale = true;
    private int laidW = -1;
    private int laidH = -1;
    private int laidX;
    private int laidY;
    private int count;
    private Kind[] kinds = new Kind[0];
    private int[] pages = new int[0];
    private int[] slotX = new int[0];
    private int[] slotW = new int[0];
    private String[] slotText = new String[0];
    private boolean showsSize;
    private int sizeX;
    private int sizeW;
    private int currentSlot = -1;
    private int shownSiblings;
    private boolean compact;
    private boolean arrowsOnly;
    private int[] poolPage = new int[8];
    private int heldGap = -1;
    private ButtonComponent heldButton;

    /**
     * @param box the widget's place
     * @param host the panel this pagination is added to
     * @param total how many items are paged
     * @param pageSize how many items a page holds
     * @param onPage called with the new page, from 0, when the player changes it; may be null
     */
    public PaginationComponent(Box box, PanelComponent host, int total, int pageSize, IntConsumer onPage) {
        super(box);
        this.host = Objects.requireNonNull(host, "host");
        this.onPage = onPage;
        this.total = Math.max(0, total);
        this.pageSize = Math.max(1, pageSize);
        prev = new ButtonComponent(new Box("prev", 0, 0, 1, 1), "", () -> go(page - 1)).icon(Icon.PREV);
        next = new ButtonComponent(new Box("next", 0, 0, 1, 1), "", () -> go(page + 1)).icon(Icon.NEXT);
        sizeButton = new ButtonComponent(new Box("size", 0, 0, 1, 1), "", this::pressSize);
    }

    // ---- setup ----

    /** The sizes offered; none hides the choice. The size in use need not be one of them. */
    public PaginationComponent pageSizes(int... sizes) {
        this.sizes = sizes == null ? new int[0] : sizes.clone();
        stale = true;
        return this;
    }

    /** What the size button says for a size. From the lang file ({@code minuit.page.size}, "25 / page") by default. */
    @Override
    void words(Words words) {
        this.words = words;
        stale = true;
    }

    private String sizeLabelFor(int n) {
        if (sizeLabel != null) {
            return sizeLabel.apply(n);
        }
        if (words != null) {
            String found = words.get(SIZE_KEY, n);
            if (!found.equals(SIZE_KEY)) {
                return found;
            }
        }
        return n + " / page";
    }

    public PaginationComponent sizeLabel(IntFunction<String> sizeLabel) {
        this.sizeLabel = Objects.requireNonNull(sizeLabel, "sizeLabel");
        stale = true;
        return this;
    }

    /** Called when the player changes the page size, before the callback of the page that follows from it. */
    public PaginationComponent onPageSize(IntConsumer onPageSize) {
        this.onPageSize = onPageSize;
        return this;
    }

    /** How many pages on each side of the current one have a button. 1 by default. */
    public PaginationComponent siblings(int siblings) {
        this.siblings = Math.max(0, siblings);
        stale = true;
        return this;
    }

    /** How far a click on an ellipsis or shift and an arrow goes. 5 by default; 0 makes the ellipses plain text. */
    public PaginationComponent jump(int jump) {
        this.jump = Math.max(0, jump);
        return this;
    }

    /** True to centre the page buttons in the box, or in what the page size leaves of it. Left aligned by default. */
    public PaginationComponent centered(boolean centered) {
        this.centered = centered;
        stale = true;
        return this;
    }

    /** The list of sizes, in the panel's bounds: add it after every other widget. */
    public ContextMenuComponent menu() {
        return menu(host.bounds());
    }

    /** The list of sizes, staying in this box: add it after every other widget. */
    public ContextMenuComponent menu(Box area) {
        if (menu == null) {
            menu = new ContextMenuComponent(area, host);
        }
        return menu;
    }

    @Override
    public PaginationComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- value ----

    public int page() {
        return page;
    }

    public int pageSize() {
        return pageSize;
    }

    public int total() {
        return total;
    }

    /** At least 1: with no items there is still an empty first page. */
    public int pageCount() {
        return Math.max(1, (int) ((total + (long) pageSize - 1) / pageSize));
    }

    /** The index of the first item of the current page. */
    public int firstItem() {
        return (int) Math.min((long) page * pageSize, Math.max(0, total - 1));
    }

    /** Goes to a page without calling back. It is kept inside the range. */
    public void setPage(int page) {
        this.page = clampPage(page);
        stale = true;
    }

    /** Changes the number of items without calling back, even if that moves the current page back. */
    public void setTotal(int total) {
        this.total = Math.max(0, total);
        page = clampPage(page);
        stale = true;
    }

    /** Changes the page size without calling back, keeping the first item of the page in view. */
    public void setPageSize(int size) {
        int first = firstItem();
        pageSize = Math.max(1, size);
        page = clampPage(first / pageSize);
        stale = true;
    }

    private int clampPage(int wanted) {
        return Math.max(0, Math.min(pageCount() - 1, wanted));
    }

    private boolean canPick(int target) {
        return active && clampPage(target) != page;
    }

    private void go(int target) {
        int clamped = clampPage(target);
        if (clamped != page) {
            page = clamped;
            stale = true;
            layout();
            if (onPage != null) {
                onPage.accept(page);
            }
        }
    }

    private void changeSize(int size) {
        if (size == pageSize) {
            return;
        }
        int before = page;
        setPageSize(size);
        layout();
        if (onPageSize != null) {
            onPageSize.accept(size);
        }
        if (page != before && onPage != null) {
            onPage.accept(page);
        }
    }

    private int sizeIndex() {
        for (int i = 0; i < sizes.length; i++) {
            if (sizes[i] == pageSize) {
                return i;
            }
        }
        return -1;
    }

    private void cycleSize(int by) {
        if (sizes.length == 0) {
            return;
        }
        int at = sizeIndex();
        changeSize(sizes[at < 0 ? 0 : Math.floorMod(at + by, sizes.length)]);
    }

    private void pressSize() {
        if (menu == null) {
            cycleSize(1);
            return;
        }
        List<ContextMenuComponent.Item> items = new ArrayList<>();
        for (int size : sizes) {
            items.add(ContextMenuComponent.Item.check(sizeLabelFor(size), size == pageSize, on -> changeSize(size)));
        }
        menu.items(items);
        menu.openBelow(new Box("size", sizeButton.x, sizeButton.y, sizeButton.w, sizeButton.h));
        // The first row is lit on opening: go on to the size in use, for the keys to start from there.
        for (int i = sizeIndex(); i > 0; i--) {
            menu.keyPressed(InputKey.DOWN, false);
        }
    }

    // ---- reading, for a script or a test ----

    /** What the page slots say, in order, such as {@code 1 ... 4 5 6 ... 20}; arrows left out. */
    public String slotsText() {
        layout();
        StringBuilder out = new StringBuilder();
        for (int s = 0; s < count; s++) {
            if (kinds[s] != Kind.PREV && kinds[s] != Kind.NEXT) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(slotText[s]);
            }
        }
        return out.toString();
    }

    Box slotBox(int slot) {
        layout();
        return new Box("slot" + slot, x + slotX[slot], y, slotW[slot], h);
    }

    public int slotCount() {
        layout();
        return count;
    }

    /** The slot of a page, or -1 if it has no button now. */
    public int slotOfPage(int target) {
        layout();
        for (int s = 0; s < count; s++) {
            if (kinds[s] == Kind.PAGE && pages[s] == target) {
                return s;
            }
        }
        return -1;
    }

    /** The first slot holding an ellipsis, or -1. */
    public int ellipsisSlot() {
        layout();
        for (int s = 0; s < count; s++) {
            if (kinds[s] == Kind.GAP_BACK || kinds[s] == Kind.GAP_FORWARD) {
                return s;
            }
        }
        return -1;
    }

    /** The size button's place, or null while it has no room. */
    Box sizeBox() {
        layout();
        return showsSize ? new Box("size", x + sizeX, y, sizeW, h) : null;
    }

    public boolean isCompact() {
        layout();
        return compact;
    }

    // ---- layout ----

    private void ensure(int size) {
        if (kinds.length < size) {
            kinds = new Kind[size];
            pages = new int[size];
            slotX = new int[size];
            slotW = new int[size];
            slotText = new String[size];
        }
    }

    private int widestSize() {
        int widest = 0;
        for (int size : sizes) {
            widest = Math.max(widest, TextMeasure.width(sizeLabelFor(size)));
        }
        return widest + 2 * SIZE_PAD;
    }

    private static int slotsFor(int pagesTotal, int sib) {
        return Math.min(pagesTotal, 2 * sib + FIXED_SLOTS);
    }

    private int rowWidth(int slots, int cell, int arrow) {
        return 2 * arrow + slots * cell + (slots + 1) * GAP;
    }

    private void layout() {
        if (!stale && w == laidW && h == laidH && x == laidX && y == laidY) {
            return;
        }
        stale = false;
        laidW = w;
        laidH = h;
        laidX = x; // a parent moved the widget: the buttons follow
        laidY = y;
        int n = pageCount();
        int cell = Math.max(MIN_CELL, TextMeasure.width(Integer.toString(n)) + 2 * CELL_PAD);
        int arrow = Math.max(MIN_CELL, Math.min(h, 20));
        int sizeRoom = sizes.length > 0 ? widestSize() + SIZE_GAP : 0;
        String text = (page + 1) + " / " + n;
        int textW = TextMeasure.width(text) + 2 * LABEL_PAD;
        ensure(2 * Math.max(siblings, 0) + FIXED_SLOTS + 2);
        showsSize = false;
        compact = false;
        arrowsOnly = false;
        shownSiblings = siblings;
        boolean placed = false;
        // Numbers matter more than the size: the neighbours go first, then the size, then the numbers.
        for (int withSize = sizes.length > 0 ? 1 : 0; withSize >= 0 && !placed; withSize--) {
            int room = w - withSize * sizeRoom;
            for (int sib = siblings; sib >= 0 && !placed; sib = sib == 0 ? -1 : 0) {
                if (rowWidth(slotsFor(n, sib), cell, arrow) <= room) {
                    showsSize = withSize == 1;
                    shownSiblings = sib;
                    placed = true;
                }
            }
        }
        if (!placed) {
            compact = 2 * arrow + textW + 2 * GAP <= w;
            arrowsOnly = !compact;
        }
        count = 0;
        currentSlot = -1;
        if (arrowsOnly && 2 * arrow + GAP > w) {
            return; // not even the arrows: nothing to press, nothing drawn
        }
        pushSlot(Kind.PREV, 0, arrow, "");
        if (compact) {
            currentSlot = count;
            pushSlot(Kind.TEXT, page, textW, text);
        } else if (!arrowsOnly) {
            pushPages(n, cell);
        }
        pushSlot(Kind.NEXT, 0, arrow, "");
        int room = showsSize ? w - sizeRoom : w;
        int used = -GAP;
        for (int s = 0; s < count; s++) {
            used += slotW[s] + GAP;
        }
        int at = centered ? Math.max(0, (room - used) / 2) : 0;
        for (int s = 0; s < count; s++) {
            slotX[s] = at;
            at += slotW[s] + GAP;
        }
        if (showsSize) {
            sizeW = widestSize();
            sizeX = w - sizeW;
        }
        syncButtons();
    }

    private void pushSlot(Kind kind, int target, int width, String text) {
        kinds[count] = kind;
        pages[count] = target;
        slotW[count] = width;
        slotText[count] = text;
        count++;
    }

    private void pushPage(int target, int cell) {
        if (target == page) {
            currentSlot = count;
        }
        pushSlot(Kind.PAGE, target, cell, Integer.toString(target + 1));
    }

    /** 1 ... 4 5 6 ... 20, with as many slots on every page, so that nothing moves as the pages go by. */
    private void pushPages(int n, int cell) {
        int sib = shownSiblings;
        int slots = 2 * sib + FIXED_SLOTS;
        if (n <= slots) {
            for (int i = 0; i < n; i++) {
                pushPage(i, cell);
            }
            return;
        }
        int edgeRun = 2 * sib + 3; // the run of numbers next to an end: the slots but one ellipsis and the far page
        if (page < sib + 3) {
            for (int i = 0; i < edgeRun; i++) {
                pushPage(i, cell);
            }
            pushSlot(Kind.GAP_FORWARD, 0, cell, Draw.ELLIPSIS);
            pushPage(n - 1, cell);
        } else if (page >= n - sib - 3) {
            pushPage(0, cell);
            pushSlot(Kind.GAP_BACK, 0, cell, Draw.ELLIPSIS);
            for (int i = n - edgeRun; i < n; i++) {
                pushPage(i, cell);
            }
        } else {
            pushPage(0, cell);
            pushSlot(Kind.GAP_BACK, 0, cell, Draw.ELLIPSIS);
            for (int i = page - sib; i <= page + sib; i++) {
                pushPage(i, cell);
            }
            pushSlot(Kind.GAP_FORWARD, 0, cell, Draw.ELLIPSIS);
            pushPage(n - 1, cell);
        }
    }

    /** Places the pool's buttons: a frame only draws them. */
    private void syncButtons() {
        int used = 0;
        for (int s = 0; s < count; s++) {
            ButtonComponent b;
            switch (kinds[s]) {
                case PREV -> b = prev;
                case NEXT -> b = next;
                case PAGE -> {
                    if (used == pool.size()) {
                        int index = used;
                        pool.add(new ButtonComponent(new Box("page", 0, 0, 1, 1), "", () -> pressPool(index)));
                    }
                    b = pool.get(used++);
                    b.label = slotText[s];
                    // The primary face, not "selected": that look nearly vanishes in glass, slate and vanilla.
                    b.role = pages[s] == page ? ButtonComponent.Role.PRIMARY : prev.role;
                    poolPages(used - 1, pages[s]);
                }
                default -> {
                    continue;
                }
            }
            b.x = x + slotX[s];
            b.y = y;
            b.w = slotW[s];
            b.h = h;
        }
        if (showsSize) {
            sizeButton.label = sizeLabelFor(pageSize);
            sizeButton.x = x + sizeX;
            sizeButton.y = y;
            sizeButton.w = sizeW;
            sizeButton.h = h;
        }
    }

    private void poolPages(int index, int target) {
        if (index >= poolPage.length) {
            int[] grown = new int[poolPage.length * 2];
            System.arraycopy(poolPage, 0, grown, 0, poolPage.length);
            poolPage = grown;
        }
        poolPage[index] = target;
    }

    private void pressPool(int index) {
        go(poolPage[index]);
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        layout();
        Palette p = t.palette();
        int under = NavLook.solid(p.surface());
        int gapOver = -1;
        if (active && hovered && jump > 0) {
            int at = slotAt(mx, my);
            if (at >= 0 && (kinds[at] == Kind.GAP_BACK || kinds[at] == Kind.GAP_FORWARD)) {
                gapOver = at;
            }
        }
        gapHover.step(gapOver);
        int used = 0;
        for (int s = 0; s < count; s++) {
            switch (kinds[s]) {
                case PREV -> drawButton(d, t, prev, mx, my, page > 0);
                case NEXT -> drawButton(d, t, next, mx, my, page < pageCount() - 1);
                case PAGE -> drawButton(d, t, pool.get(used++), mx, my, true);
                case TEXT -> {
                    int ink = active ? Ink.on(p, under) : NavLook.disabled(p, under);
                    d.centered(slotText[s], x + slotX[s] + slotW[s] / 2, RowLook.textY(y, h), ink);
                }
                default -> drawGap(d, t.frame(), p, s, under, s == gapOver);
            }
        }
        if (showsSize) {
            drawButton(d, t, sizeButton, mx, my, true);
        }
    }

    private void drawButton(Draw d, Theme t, ButtonComponent b, int mx, int my, boolean enabled) {
        b.active = active && enabled;
        b.draw(d, t, mx, my, delta);
    }

    private void drawGap(Draw d, FrameStyle frame, Palette p, int s, int under, boolean over) {
        int sx = x + slotX[s];
        int sw = slotW[s];
        int base = Ink.on(p, under);
        if (over) {
            int alpha = heldGap == s ? NavLook.WASH_HELD : Math.round(NavLook.WASH_HOVER * gapHover.fade());
            frame.row(d, p, sx, y, sw, h, alpha > 0 ? Shapes.alpha(base, alpha) : 0);
        }
        int ink = !active ? NavLook.disabled(p, under) : over ? base : Ink.soft(p, under);
        int textW = d.width(slotText[s]) - 1;
        d.text(slotText[s], sx + (sw - textW) / 2, RowLook.textY(y, h), ink);
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

    /** The ring goes round the current page's button, where the arrows act. */
    @Override
    protected Box focusBox() {
        layout();
        if (currentSlot >= 0 && currentSlot < count) {
            return new Box("page", x + slotX[currentSlot], y, slotW[currentSlot], h);
        }
        return super.focusBox();
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        return tooltip;
    }

    // ---- input ----

    private ButtonComponent buttonAt(double mx, double my) {
        layout();
        if (showsSize && sizeButton.isOver(mx, my)) {
            return sizeButton;
        }
        int s = slotAt(mx, my);
        if (s < 0) {
            return null;
        }
        int used = 0;
        for (int i = 0; i < s; i++) {
            if (kinds[i] == Kind.PAGE) {
                used++;
            }
        }
        return switch (kinds[s]) {
            case PREV -> prev;
            case NEXT -> next;
            case PAGE -> pool.get(used);
            default -> null;
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        layout();
        if (!active || !visible || button != 0) {
            return false;
        }
        ButtonComponent b = buttonAt(mx, my);
        if (b != null) {
            if (!b.active) {
                return true;
            }
            heldButton = b;
            b.mouseClicked(mx, my, button);
            return true;
        }
        int s = slotAt(mx, my);
        if (s >= 0 && jump > 0 && (kinds[s] == Kind.GAP_BACK || kinds[s] == Kind.GAP_FORWARD)) {
            heldGap = s;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (heldButton != null) {
            ButtonComponent b = heldButton;
            heldButton = null;
            b.mouseReleased(mx, my, button);
            return true;
        }
        if (heldGap >= 0) {
            int s = heldGap;
            heldGap = -1;
            if (s < count && slotAt(mx, my) == s) {
                BaseComponent.click();
                go(page + (kinds[s] == Kind.GAP_BACK ? -jump : jump));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!active || !visible || amount == 0 || !isOver(mx, my)) {
            return false;
        }
        int target = page + (amount > 0 ? -1 : 1);
        if (canPick(target)) {
            BaseComponent.sound.tap();
            go(target);
        }
        return true;
    }

    @Override
    protected boolean takesFocus() {
        return visible && active;
    }

    @Override
    protected boolean navigable() {
        layout();
        return visible && active && settled() && count > 0 && (pageCount() > 1 || (showsSize && sizes.length > 1));
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        layout();
        if (!active || !visible || count == 0) {
            return false;
        }
        int step = shift && jump > 0 ? jump : 1;
        switch (key) {
            case LEFT -> keyGo(page - step);
            case RIGHT -> keyGo(page + step);
            case HOME -> keyGo(0);
            case END -> keyGo(pageCount() - 1);
            case UP, DOWN -> {
                if (!showsSize) {
                    return false;
                }
                BaseComponent.click();
                cycleSize(key == InputKey.DOWN ? 1 : -1);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void keyGo(int target) {
        if (canPick(target)) {
            BaseComponent.sound.tap();
            go(target);
        }
    }
}
