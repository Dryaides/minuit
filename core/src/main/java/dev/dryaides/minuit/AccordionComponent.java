package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A stack of titled blocks that fold, with one open at a time or several. Each holds wrapped text, widgets, or
 * both. {@link SectionComponent} is the right choice for one block that folds at once; this adds what it does
 * not have: blocks that know each other, a height that eases, and the arrows moving between the titles.
 *
 * <pre>{@code
 * AccordionComponent faq = add(new AccordionComponent(at(new Box("faq", 10, 10, 200, 120)), AccordionComponent.Mode.SINGLE));
 * faq.item("Where is my world?", "In the saves folder.");
 * AccordionComponent.Item video = faq.item("Video", "");
 * video.add(new SliderComponent(new Box("fov", 0, 0, 170, 12), 30, 110, 70, v -> { }), 6, 2);
 * faq.setOpen(0, true);
 * }</pre>
 *
 * <p>With the focus on it: up, down, home and end move between titles; Enter, Space or the right arrow open,
 * and the left arrow closes. Tab moves on to the widgets of what is open, then out. In {@code SINGLE} mode, opening
 * one closes the other; {@link #collapsible(boolean) collapsible(false)} keeps one always open. When the stack is
 * taller than the box, it scrolls by the wheel, with a bar. {@link #fit()} sets the box's height to what is open
 * right now, for a stack with room to grow. {@code active = false} shows the state and takes no input, except the
 * wheel. A child's position is relative to the top left of its block's content; it takes input once its block is
 * fully open and only inside the box. The stack sets each child's {@code x}, {@code y} and {@code visible}.
 */
public class AccordionComponent extends BaseComponent {
    /** How many blocks can be open together. */
    public enum Mode { SINGLE, MULTIPLE }

    /** One block. */
    public final class Item {
        private String title;
        private String text;
        private boolean enabled = true;
        private List<String> tooltip;
        private final List<Child> children = new ArrayList<>();
        private final SurfaceLook.Tween openness = new SurfaceLook.Tween(OPEN_MS, 0F);
        private boolean open;
        private List<String> lines = List.of();
        private int contentH;

        private Item(String title, String text) {
            this.title = title == null ? "" : title;
            this.text = text == null ? "" : text;
        }

        /** Adds a widget at a place relative to the top left of this block's content. The block grows to hold it. */
        public <W extends BaseComponent> W add(W widget, int rx, int ry) {
            children.add(new Child(widget, rx, ry));
            dirty = true;
            return widget;
        }

        /** Shown over this title, a disabled one included, where it can say why. */
        public Item tooltip(String... lines) {
            this.tooltip = lines == null || lines.length == 0 ? null : List.of(lines);
            return this;
        }

        public Item text(String text) {
            this.text = text == null ? "" : text;
            dirty = true;
            return this;
        }

        public Item title(String title) {
            this.title = title == null ? "" : title;
            return this;
        }

        public boolean isOpen() {
            return open;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public String title() {
            return title;
        }
    }

    private static final class Child {
        final BaseComponent widget;
        final int rx;
        final int ry;
        Child(BaseComponent widget, int rx, int ry) {
            this.widget = widget;
            this.rx = rx;
            this.ry = ry;
        }
    }

    /** As tall as a {@link SectionComponent}'s title. */
    public static final int HEADER = SectionComponent.HEADER;
    private static final int OPEN_MS = 150;
    private static final int LINE = SurfaceLook.LINE;
    private static final int FONT_H = SurfaceLook.FONT_H;
    private static final int TEXT_LEFT = 14;
    private static final int RIGHT = 4;
    private static final int CONTENT_PAD = 3;
    private static final int BAR = ScrollComponent.BAR;
    /** Narrower than this, a bar would leave no room for the words: the wheel still scrolls. */
    private static final int MIN_BAR_W = 40;
    private static final int HELD_ALPHA = 0x50;

    private final Mode mode;
    private final List<Item> items = new ArrayList<>();
    private final RowLook.HoverFade rowHover = new RowLook.HoverFade();
    private boolean collapsible = true;
    private String emptyText;
    private Words words;
    private BiConsumer<Integer, Boolean> onChange;

    private boolean dirty = true;
    private int laidW = -1;
    private int laidH = -1;
    private boolean overflows;
    private final ScrollBar bar = new ScrollBar();
    private FrameStyle frame = FrameStyles.VANILLA;
    private int cursor;
    private int heldRow = -1;

    @Override
    void words(Words words) {
        this.words = words;
    }

    public AccordionComponent(Box box, Mode mode) {
        super(box);
        this.mode = mode == null ? Mode.SINGLE : mode;
    }

    /** Adds a block, closed. */
    public Item item(String title, String text) {
        Item item = new Item(title, text);
        items.add(item);
        dirty = true;
        return item;
    }

    /** False so that a block cannot be closed by the player except by opening another: for {@code SINGLE}. On by default. */
    public AccordionComponent collapsible(boolean collapsible) {
        this.collapsible = collapsible;
        return this;
    }

    /** What an empty stack says instead of the lang entry {@code minuit.empty}. */
    public AccordionComponent emptyText(String emptyText) {
        this.emptyText = emptyText == null ? "" : emptyText;
        return this;
    }

    /** Called with the block's index and its new state when the player opens or closes one; both, for a swap in {@code SINGLE}. */
    public AccordionComponent onChange(BiConsumer<Integer, Boolean> onChange) {
        this.onChange = onChange;
        return this;
    }

    public AccordionComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public AccordionComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** Sets the box's height to what is open right now, and returns this. */
    public AccordionComponent fit() {
        overflows = false;
        measure(textRoom());
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            total += HEADER + (item.open ? item.contentH : 0);
        }
        h = total;
        dirty = true;
        return this;
    }

    public int count() {
        return items.size();
    }

    public Item itemAt(int index) {
        return items.get(index);
    }

    /**
     * Greys out block {@code index}: drawn disabled, it cannot be opened or closed by the player. The {@code tip}
     * lines show over its title, to say why; none shows nothing.
     */
    public AccordionComponent disabled(int index, boolean disabled, String... tip) {
        Item item = items.get(index);
        item.enabled = !disabled;
        item.tooltip(tip);
        return this;
    }

    public boolean isOpen(int index) {
        return items.get(index).open;
    }

    /** Opens or closes a block at once, without calling back. In {@code SINGLE} mode, opening closes the others. */
    public void setOpen(int index, boolean open) {
        change(index, open, false);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            item.openness.snap(item.open ? 1F : 0F);
        }
        clampScroll();
    }

    /** Opens or closes a block with its easing, without calling back. */
    public void slideOpen(int index, boolean open) {
        change(index, open, false);
    }

    /** The title the keyboard is on. */
    int cursor() {
        return cursor;
    }

    int scroll() {
        return bar.offset();
    }

    void setScroll(int pixels) {
        clampScroll();
        bar.offset(pixels);
    }

    /** True when the stack is taller than the box. */
    boolean overflows() {
        layout();
        return overflows;
    }

    /** True while a block's height is easing. */
    boolean animating() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).openness.moving()) {
                return true;
            }
        }
        return false;
    }

    /** The stack's height at rest, what is open included. */
    int targetHeight() {
        layout();
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            total += HEADER + (item.open ? item.contentH : 0);
        }
        return total;
    }

    /** The stack's height this frame. */
    int heightNow() {
        layout();
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            total += HEADER + shownH(items.get(i));
        }
        return total;
    }

    /** A title's place now, before the view's cut. */
    Box headerBox(int index) {
        layout();
        return new Box("header" + index, x, rowTop(index), viewW(), HEADER);
    }

    /** A block's content height when open. */
    int contentHeight(int index) {
        layout();
        return items.get(index).contentH;
    }

    private void change(int index, boolean open, boolean tell) {
        Item item = items.get(index);
        if (item.open == open) {
            return;
        }
        if (open && mode == Mode.SINGLE) {
            for (int i = 0; i < items.size(); i++) {
                Item other = items.get(i);
                if (i != index && other.open) {
                    other.open = false;
                    other.openness.go(0F);
                    if (tell && onChange != null) {
                        onChange.accept(i, false);
                    }
                }
            }
        }
        item.open = open;
        item.openness.go(open ? 1F : 0F);
        dirty = true;
        if (tell && onChange != null) {
            onChange.accept(index, open);
        }
    }

    private void playerToggle(int index) {
        Item item = items.get(index);
        if (!item.enabled) {
            return;
        }
        if (item.open && !collapsible) {
            return;
        }
        BaseComponent.click();
        change(index, !item.open, true);
    }

    private void layout() {
        if (!dirty && laidW == w && laidH == h) {
            return;
        }
        dirty = false;
        laidW = w;
        laidH = h;
        overflows = false;
        measure(textRoom());
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            total += HEADER + (item.open ? item.contentH : 0);
        }
        if (total > h && w >= MIN_BAR_W && h >= HEADER) {
            overflows = true;
            measure(textRoom());
        }
    }

    private void measure(int room) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            item.lines = item.text.isEmpty() || room <= 0 ? List.of() : TextMeasure.wrapAll(item.text, room);
            int bottom = item.lines.size() * LINE;
            for (int k = 0; k < item.children.size(); k++) {
                Child c = item.children.get(k);
                bottom = Math.max(bottom, c.ry + c.widget.h + 1);
            }
            item.contentH = bottom == 0 ? 0 : CONTENT_PAD + bottom + CONTENT_PAD;
        }
    }

    private int barX() {
        return ScrollBar.x(frame, x + w);
    }

    private int viewW() {
        return Math.max(0, w - (overflows ? ScrollBar.room(frame) : 0));
    }

    private int textRoom() {
        return Math.max(0, viewW() - TEXT_LEFT - RIGHT);
    }

    private int shownH(Item item) {
        return Math.round(item.contentH * item.openness.value());
    }

    private int rowTop(int index) {
        int top = y - bar.offset();
        for (int i = 0; i < index; i++) {
            top += HEADER + shownH(items.get(i));
        }
        return top;
    }

    private int maxScroll() {
        return Math.max(0, heightNow() - h);
    }

    private void clampScroll() {
        bar.content(heightNow(), h);
    }

    private int rowAt(double mx, double my) {
        if (mx < x || mx >= x + viewW() || my < y || my >= y + h) {
            return -1;
        }
        int top = y - bar.offset();
        for (int i = 0; i < items.size(); i++) {
            if (my >= top && my < top + HEADER) {
                return i;
            }
            top += HEADER + shownH(items.get(i));
        }
        return -1;
    }

    private void placeChildren() {
        int top = y - bar.offset();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int contentTop = top + HEADER + CONTENT_PAD;
            boolean full = item.open && !item.openness.moving();
            for (int k = 0; k < item.children.size(); k++) {
                Child c = item.children.get(k);
                c.widget.x = x + c.rx;
                c.widget.y = contentTop + c.ry;
                c.widget.visible = full && c.widget.y + c.widget.h > y && c.widget.y < y + h;
            }
            top += HEADER + shownH(item);
        }
    }

    private void revealCursor() {
        clampScroll();
        int top = rowTop(cursor) + bar.offset() - y;
        Item item = items.get(cursor);
        if (top < bar.offset()) {
            bar.offset(top);
        } else {
            int bottom = top + HEADER + (item.open ? Math.min(item.contentH, Math.max(0, h - HEADER)) : 0);
            if (bottom > bar.offset() + h) {
                bar.offset(bottom - h);
            }
        }
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        frame = t.frame();
        layout();
        if (!animating()) {
            clampScroll();
        }
        placeChildren();
        Palette p = t.palette();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (items.isEmpty()) {
            RowLook.drawEmpty(d, p, text(emptyText, words, "minuit.empty", "Nothing here"), active, x, y, w, h);
            return;
        }
        int under = p.surface() | 0xFF000000;
        int ink = active ? Ink.on(p, under) : p.disabledText();
        int soft = active ? Ink.soft(p, under) : p.disabledText();
        int hoverRow = active && !bar.dragging() && hovered ? rowAt(mx, my) : -1;
        rowHover.step(hoverRow);
        int vw = viewW();
        d.scissor(x, y, x + vw, y + h);
        int top = y - bar.offset();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            int shown = shownH(item);
            if (top < y + h && top + HEADER + shown > y) {
                drawHeader(d, t, p, item, i, top, vw, hoverRow, ink, soft);
                if (shown > 0) {
                    drawContent(d, t, item, top + HEADER, shown, vw, soft, mx, my);
                }
            }
            top += HEADER + shown;
        }
        d.endScissor();
        if (overflows) {
            int barX = barX();
            bar.draw(d, t, barX, y, h, active && hovered && mx >= barX);
            if (!active) {
                RowLook.dimBar(d, p, barX, y, BAR, h);
            }
        }
    }

    private void drawHeader(Draw d, Theme t, Palette p, Item item, int index, int top, int vw, int hoverRow, int ink, int soft) {
        boolean on = active && item.enabled;
        if (on && hoverRow == index) {
            int wash = heldRow == index ? Shapes.alpha(p.text(), HELD_ALPHA) : RowLook.tint(p, rowHover.fade());
            t.frame().row(d, p, x, top, vw, HEADER - 1, wash);
        }
        int color = on ? soft : p.disabledText();
        arrow(d, x + 4, top + (HEADER - 1) / 2, item.open, color);
        int room = vw - TEXT_LEFT - RIGHT;
        if (room >= 8 && h >= FONT_H && RowLook.textY(top, HEADER - 1) >= y && RowLook.textY(top, HEADER - 1) + FONT_H <= y + h) {
            d.textFit(item.title, x + TEXT_LEFT, RowLook.textY(top, HEADER - 1), room, on ? ink : p.disabledText());
        }
        d.fill(x, top + HEADER - 1, x + vw, top + HEADER, p.surfaceEdge());
    }

    /** A small triangle, on its side when closed, point down when open. Drawn in rows, as a section's. */
    private static void arrow(Draw d, int ax, int centre, boolean open, int color) {
        if (open) {
            Shapes.arrow(d, ax + 2, centre + 1, 4, Shapes.Point.DOWN, color);
        } else {
            Shapes.arrow(d, ax + 4, centre, 4, Shapes.Point.RIGHT, color);
        }
    }

    private void drawContent(Draw d, Theme t, Item item, int top, int shown, int vw, int soft, int mx, int my) {
        int clipTop = Math.max(y, top);
        int clipBottom = Math.min(y + h, top + shown);
        if (clipBottom <= clipTop) {
            return;
        }
        boolean moving = item.openness.moving();
        d.scissor(x, clipTop, x + vw, clipBottom);
        int line = top + CONTENT_PAD;
        for (int i = 0; i < item.lines.size(); i++) {
            // While a block eases, the cut moves past its words; at rest, a line is whole or not drawn.
            if (moving || (line >= y && line + FONT_H <= y + h)) {
                if (line < clipBottom && line + FONT_H > clipTop) {
                    d.text(item.lines.get(i), x + TEXT_LEFT, line, soft);
                }
            }
            line += LINE;
        }
        for (int k = 0; k < item.children.size(); k++) {
            BaseComponent child = item.children.get(k).widget;
            if (child.visible) {
                child.draw(d, t, inViewPointer(mx, my) ? mx : -100_000, my, delta);
            }
        }
        if (top + shown <= y + h) {
            d.fill(x, top + shown - 1, x + vw, top + shown, t.palette().surfaceEdge());
        }
        d.endScissor();
    }

    private boolean inViewPointer(double mx, double my) {
        return mx >= x && mx < x + viewW() && my >= y && my < y + h;
    }


    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!visible || !isOver(mx, my)) {
            return null;
        }
        placeChildren();
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            for (int k = 0; k < item.children.size(); k++) {
                BaseComponent child = item.children.get(k).widget;
                if (child.visible && inViewPointer(mx, my) && child.isOver(mx, my)) {
                    return child.tooltipAt(mx, my);
                }
            }
        }
        int row = rowAt(mx, my);
        if (row >= 0 && items.get(row).tooltip != null) {
            return items.get(row).tooltip;
        }
        if (tooltip != null) {
            return tooltip;
        }
        if (row >= 0 && TextMeasure.width(items.get(row).title) > viewW() - TEXT_LEFT - RIGHT) {
            return List.of(items.get(row).title);
        }
        return null;
    }

    private boolean accepts() {
        return visible && active && !items.isEmpty();
    }

    private boolean anyEnabled() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).enabled) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean takesFocus() {
        return accepts() && anyEnabled();
    }

    @Override
    protected boolean navigable() {
        return accepts() && anyEnabled();
    }

    @Override
    public List<BaseComponent> stops() {
        if (!navigable()) {
            return List.of();
        }
        boolean any = false;
        for (int i = 0; i < items.size() && !any; i++) {
            any = !items.get(i).children.isEmpty();
        }
        if (!any) {
            return List.of(this);
        }
        placeChildren();
        List<BaseComponent> out = new ArrayList<>();
        out.add(this);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            for (int k = 0; k < item.children.size(); k++) {
                BaseComponent child = item.children.get(k).widget;
                if (child.visible) {
                    out.addAll(child.stops());
                }
            }
        }
        return out;
    }

    @Override
    public void tick() {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            for (int k = 0; k < item.children.size(); k++) {
                item.children.get(k).widget.tick();
            }
        }
    }

    private boolean cursorShown() {
        int top = rowTop(cursor);
        return top >= y && top + HEADER <= y + h && h >= HEADER && !items.isEmpty();
    }

    @Override
    protected Box focusBox() {
        if (!items.isEmpty() && cursorShown()) {
            // The ring grows by 2 on each side: this lays it on the row's edges.
            return new Box("row", x + 2, rowTop(cursor) + 2, Math.max(0, viewW() - 4), HEADER - 4);
        }
        return super.focusBox();
    }

    @Override
    protected int focusCorner() {
        return 0;
    }

    private boolean overBar(double mx, double my) {
        return overflows && mx >= barX() && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!accepts() || !isOver(mx, my)) {
            return false;
        }
        layout();
        placeChildren();
        if (button == 0 && overBar(mx, my)) {
            clampScroll();
            bar.press(mx, my, barX(), y, h);
            return true;
        }
        int row = rowAt(mx, my);
        if (row >= 0) {
            if (button == 0 && items.get(row).enabled) {
                cursor = row;
                heldRow = row;
            }
            return true;
        }
        if (inViewPointer(mx, my)) {
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                for (int k = 0; k < item.children.size(); k++) {
                    BaseComponent child = item.children.get(k).widget;
                    if (child.visible && child.active && child.isOver(mx, my) && child.mouseClicked(mx, my, button)) {
                        return true;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (bar.drag(my)) {
            return true;
        }
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            for (int k = 0; k < item.children.size(); k++) {
                BaseComponent child = item.children.get(k).widget;
                if (child.visible && child.mouseDragged(mx, my, button, dx, dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (bar.dragging()) {
            bar.release();
            return true;
        }
        int was = heldRow;
        heldRow = -1;
        boolean used = false;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            for (int k = 0; k < item.children.size(); k++) {
                used |= item.children.get(k).widget.mouseReleased(mx, my, button);
            }
        }
        if (was >= 0 && accepts() && rowAt(mx, my) == was) {
            playerToggle(was);
            return true;
        }
        return used || was >= 0;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || !isOver(mx, my)) {
            return false;
        }
        layout();
        placeChildren();
        if (active && inViewPointer(mx, my)) {
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                for (int k = 0; k < item.children.size(); k++) {
                    BaseComponent child = item.children.get(k).widget;
                    if (child.visible && child.isOver(mx, my) && child.mouseScrolled(mx, my, amount)) {
                        return true;
                    }
                }
            }
        }
        if (maxScroll() <= 0) {
            return false;
        }
        clampScroll();
        bar.wheel(amount, ScrollBar.WHEEL_ROWS * LINE);
        return true;
    }

    private void moveCursor(int by) {
        for (int at = cursor + by; at >= 0 && at < items.size(); at += by) {
            if (items.get(at).enabled) {
                cursor = at;
                revealCursor();
                return;
            }
        }
    }

    private void cursorTo(int from, int by) {
        for (int at = from; at >= 0 && at < items.size(); at += by) {
            if (items.get(at).enabled) {
                cursor = at;
                revealCursor();
                return;
            }
        }
    }

    @Override
    protected void enter(boolean backwards) {
        if (!items.isEmpty() && !items.get(Math.min(cursor, items.size() - 1)).enabled) {
            cursorTo(0, 1);
        }
    }

    @Override
    protected boolean activate() {
        if (!navigable()) {
            return false;
        }
        playerToggle(cursor);
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!navigable()) {
            return false;
        }
        layout();
        switch (key) {
            case UP -> moveCursor(-1);
            case DOWN -> moveCursor(1);
            case HOME -> cursorTo(0, 1);
            case END -> cursorTo(items.size() - 1, -1);
            case RIGHT -> {
                if (!items.get(cursor).open && items.get(cursor).enabled) {
                    playerToggle(cursor);
                }
            }
            case LEFT -> {
                if (items.get(cursor).open && items.get(cursor).enabled) {
                    playerToggle(cursor);
                }
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
