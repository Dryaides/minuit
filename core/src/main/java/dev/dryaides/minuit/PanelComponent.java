package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A screen's content without the screen: widgets, the drawing between them, mouse dispatch and
 * tooltips. The game hosts it in {@code client.MinuitScreen}; a bench draws it into an image. Every
 * position is in screen coordinates, the layout's relative boxes moved by {@link #at}.
 */
public abstract class PanelComponent {

    /** Set once by the constructor, except by a panel that lays itself out again for a new screen. */
    protected Theme theme;
    protected final Words words;
    protected int left;
    protected int top;
    protected final List<BaseComponent> widgets = new ArrayList<>();
    /**
     * Drawn over everything and given the clicks first, a dropdown's open list for instance. A
     * click outside it only closes it.
     */
    protected BaseComponent overlay;
    private BaseComponent focus;
    /** The stop the keyboard is on, ringed while the player moves with the keys. */
    private BaseComponent cursor;
    private boolean ring;
    private Runnable back;
    private final TipDelay tip = new TipDelay();

    protected PanelComponent(Theme theme, Words words, int left, int top) {
        this.theme = theme;
        this.words = words;
        this.left = left;
        this.top = top;
    }

    /** The back arrow's place in a header, panel coordinates. */
    public static final Box BACK_ARROW = new Box("back", 8, 8, 15, 15);

    private boolean hasBack;

    public abstract int width();

    public abstract int height();

    /** The panel's place on the screen. */
    public Box bounds() {
        return new Box("panel", this.left, this.top, width(), height());
    }

    public Theme theme() {
        return this.theme;
    }

    /** Where a header's title starts: after the back arrow when the page has one. */
    protected final int titleX() {
        return this.hasBack ? 28 : 14;
    }

    /**
     * The back arrow of a sub-page, top left of the header before the title: leaves for the page it
     * was opened from, while the header's cross closes everything. Null adds nothing. A page with
     * a parent calls it once and draws its title at {@link #titleX}.
     */
    protected final void addBack(Runnable back) {
        if (back == null) {
            return;
        }
        this.hasBack = true;
        this.back = back;
        add(new ButtonComponent(at(BACK_ARROW), "", back).icon(Icon.BACK).style(ButtonComponent.Style.GHOST)
                .tooltip(this.words.get("minuit.back")));
    }

    protected final Box at(Box relative) {
        return relative.offset(this.left, this.top);
    }

    protected final <W extends BaseComponent> W add(W widget) {
        this.widgets.add(widget);
        return widget;
    }

    public List<BaseComponent> widgets() {
        return List.copyOf(this.widgets);
    }

    /** The boxes a string may be drawn in, screen coordinates. A bench holds every drawn string to them. */
    public List<Box> textBoxes() {
        List<Box> boxes = new ArrayList<>();
        for (BaseComponent widget : this.widgets) {
            if (widget.visible) {
                boxes.add(widget.box());
            }
        }
        if (this.overlay != null) {
            boxes.add(this.overlay.box());
        }
        return boxes;
    }

    public final void draw(Draw d, int mx, int my, float delta) {
        drawBase(d, mx, my, delta);
        drawOverlay(d, mx, my, delta);
    }

    /**
     * Everything but the open overlay: background, widgets, foreground and the focus ring. A container
     * screen draws this, then its items, then {@link #drawOverlay}, so a dropdown lies over the items.
     */
    public final void drawBase(Draw d, int mx, int my, float delta) {
        drawBackground(d, mx, my);
        for (BaseComponent widget : this.widgets) {
            widget.draw(d, this.theme, mx, my, delta);
        }
        drawForeground(d, mx, my);
        if (this.ring && this.overlay == null && this.cursor != null && this.cursor.visible) {
            drawRing(d);
        }
    }

    /** The open overlay (a dropdown's list), if any. */
    public final void drawOverlay(Draw d, int mx, int my, float delta) {
        if (this.overlay != null) {
            this.overlay.draw(d, this.theme, mx, my, delta);
        }
    }

    private void drawRing(Draw d) {
        BaseComponent owner = ownerOf(this.cursor);
        if (owner == null) {
            return;
        }
        boolean clip = owner != this.cursor;
        if (clip) {
            d.scissor(owner.x, owner.y, owner.x + owner.w, owner.y + owner.h);
        }
        if (this.cursor.drawFocus(d, this.theme)) {
            if (clip) {
                d.endScissor();
            }
            return;
        }
        Box b = this.cursor.focusBox();
        Shapes.ring(d, b.x() - 2, b.y() - 2, b.w() + 4, b.h() + 4, this.cursor.focusCorner() + 2,
                this.theme.palette().text());
        if (clip) {
            d.endScissor();
        }
    }

    /**
     * Every place the keyboard stops on, in reading order: top to bottom, then left to right, a
     * widget that holds others giving its own stops at its place. Hidden and disabled ones are left out.
     */
    public List<BaseComponent> stops() {
        List<BaseComponent> stops = new ArrayList<>();
        for (BaseComponent widget : readingOrder(this.widgets)) {
            if (widget.visible && widget.active && widget.settled()) {
                for (BaseComponent stop : widget.stops()) {
                    if (stop.visible && stop.active && stop.settled()) {
                        stops.add(stop);
                    }
                }
            }
        }
        return stops;
    }

    /** {@code widgets} sorted top to bottom, then left to right. */
    static List<BaseComponent> readingOrder(List<BaseComponent> widgets) {
        List<BaseComponent> sorted = new ArrayList<>(widgets);
        sorted.sort(Comparator.comparingInt((BaseComponent w) -> w.y).thenComparingInt(w -> w.x));
        return sorted;
    }

    /** The stop the keyboard is on, or null. */
    public BaseComponent cursor() {
        return this.cursor;
    }

    private BaseComponent ownerOf(BaseComponent stop) {
        for (BaseComponent widget : this.widgets) {
            if (widget == stop || widget.stops().contains(stop)) {
                return widget;
            }
        }
        return null;
    }

    /** Puts the keyboard on {@code stop}: a field takes the keys, a scroll box brings it into view. */
    private void moveTo(BaseComponent stop, boolean forward) {
        this.cursor = stop;
        this.ring = true;
        stop.enter(forward);
        BaseComponent owner = ownerOf(stop);
        if (stop.takesFocus()) {
            focus(owner);
            stop.focused = true;
        } else {
            focus(null);
        }
        reveal(owner);
    }

    private void reveal(BaseComponent owner) {
        if (owner instanceof ScrollComponent scroll && owner != this.cursor) {
            Box b = this.cursor.focusBox();
            scroll.reveal(new Box("stop", b.x(), b.y() + scroll.scroll() - 2, b.w(), b.h() + 4));
        } else if (owner instanceof ListComponent<?> list && owner != this.cursor) {
            list.revealStop(this.cursor);
        }
    }

    private boolean step(int dir) {
        List<BaseComponent> stops = stops();
        if (stops.isEmpty()) {
            return false;
        }
        int at = stops.indexOf(this.cursor);
        int next = at < 0 ? (dir > 0 ? 0 : stops.size() - 1) : Math.floorMod(at + dir, stops.size());
        moveTo(stops.get(next), dir > 0);
        return true;
    }

    public BaseComponent overlay() {
        return this.overlay;
    }

    /** @return true when an overlay was open: Escape then closes it, not the screen */
    public boolean closeOverlay() {
        boolean open = this.overlay != null;
        this.overlay = null;
        return open;
    }

    protected abstract void drawBackground(Draw d, int mx, int my);

    protected void drawForeground(Draw d, int mx, int my) {
    }

    /** Called twenty times a second by the host. */
    public void tick() {
    }

    /** The host's tick: {@link #tick}, then every widget's, windows and sections passing it inside. */
    public final void tickAll() {
        tick();
        for (BaseComponent widget : List.copyOf(this.widgets)) {
            widget.tick();
        }
    }

    /**
     * The tooltip of whatever is under the mouse, or null: the topmost widget's
     * {@link BaseComponent#tooltipAt}. A panel adds tips for its own drawing by overriding this and asking
     * {@code super} first.
     */
    public List<String> tooltipAt(double mx, double my) {
        if (this.overlay != null) {
            return this.overlay.tooltipAt(mx, my);
        }
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            BaseComponent widget = this.widgets.get(i);
            List<String> lines = widget.visible ? widget.tooltipAt(mx, my) : null;
            if (lines != null) {
                return lines;
            }
        }
        return null;
    }

    /**
     * Draws the tooltip under the mouse over everything, once the same tip has stayed under it for
     * {@link TipDelay#MS}; the host calls it after {@link #draw}.
     */
    public final void drawTooltip(Draw d, int mx, int my) {
        this.tip.draw(d, tooltipAt(mx, my), mx, my);
    }

    /** The widget holding the keyboard, or null. */
    public BaseComponent focus() {
        return this.focus;
    }

    /** Gives the keyboard to {@code widget}, or takes it back for null. */
    public void focus(BaseComponent widget) {
        if (this.focus != null) {
            this.focus.focused = false;
        }
        this.focus = widget;
        if (widget != null) {
            widget.focused = true;
        }
    }

    /**
     * A key from the host. An open overlay takes every key, Escape closing it. Then the field that
     * holds the keyboard, or the stop the keyboard is on, uses the key if it can; an Escape the field
     * leaves takes the keyboard back from it, so in a window the inner field lets go first, then the
     * window. A key left over moves between the {@link #stops}: Tab and Shift+Tab,
     * the arrows; Enter and Space press the stop; Escape goes back when the page has a back arrow.
     *
     * @return false when nothing used the key, so the host may close the screen on Escape
     */
    public boolean keyPressed(InputKey key, boolean shift) {
        if (this.overlay != null) {
            if (key == InputKey.ESCAPE) {
                this.overlay = null;
            } else if (!this.overlay.keyPressed(key, shift) && (key == InputKey.ENTER || key == InputKey.SPACE)) {
                this.overlay.activate();
            }
            return true;
        }
        BaseComponent held = this.focus;
        BaseComponent on = this.cursor;
        if (held != null && held.visible) {
            if (held.keyPressed(key, shift)) {
                return true;
            }
            if (key == InputKey.ESCAPE) {
                focus(null);
                return true;
            }
        } else if (on != null && !on.takesFocus() && on.visible && on.active && stops().contains(on)
                && on.keyPressed(key, shift)) {
            this.ring = true;
            reveal(ownerOf(on));
            return true;
        }
        return switch (key) {
            case TAB -> step(shift ? -1 : 1);
            case UP, LEFT -> step(-1);
            case DOWN, RIGHT -> step(1);
            case ENTER, SPACE -> {
                if (on == null || !on.visible || !on.active || !stops().contains(on)) {
                    yield false;
                }
                if (on.takesFocus() && held == null) {
                    moveTo(on, true);
                    yield true;
                }
                yield on.activate();
            }
            case ESCAPE -> {
                if (this.back == null) {
                    yield false;
                }
                this.back.run();
                yield true;
            }
            default -> false;
        };
    }

    /** True while the focused widget waits for a raw key; the host then sends keys through {@link #rawKey}. */
    public boolean capturing() {
        BaseComponent held = this.focus;
        return held != null && held.visible && held.capturesKeys();
    }

    /** A raw key or mouse button for the capturing widget; true when it used it. */
    public boolean rawKey(KeyPress key) {
        return capturing() && this.focus.keyCaptured(key);
    }

    public boolean charTyped(int codepoint) {
        BaseComponent held = this.focus;
        return held != null && held.visible && held.charTyped(codepoint);
    }

    public boolean mouseClicked(double mx, double my, int button) {
        BaseComponent open = this.overlay;
        if (open != null) {
            if (open.isOver(mx, my)) {
                open.mouseClicked(mx, my, button);
            } else {
                this.overlay = null;
            }
            return true;
        }
        this.ring = false;
        this.cursor = null;
        for (BaseComponent stop : stops()) {
            if (stop.isOver(mx, my)) {
                this.cursor = stop;
            }
        }
        BaseComponent clicked = null;
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            BaseComponent widget = this.widgets.get(i);
            if (widget.visible && widget.isOver(mx, my)) {
                // the topmost widget under the mouse decides, so a window over a field hides it
                clicked = widget.active && widget.takesFocus() ? widget : null;
                break;
            }
        }
        if (clicked != this.focus) {
            focus(clicked);
        }
        if (capturing() && rawKey(KeyPress.mouse(button))) {
            return true;
        }
        // Topmost first, as drawn; a clicked window comes to the front and keeps the keyboard.
        List<BaseComponent> order = List.copyOf(this.widgets);
        for (int i = order.size() - 1; i >= 0; i--) {
            BaseComponent widget = order.get(i);
            if (widget.visible && widget.mouseClicked(mx, my, button)) {
                if (widget instanceof WindowComponent) {
                    this.widgets.remove(widget);
                    this.widgets.add(widget);
                }
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = false;
        for (BaseComponent widget : this.widgets) {
            any |= widget.mouseReleased(mx, my, button);
        }
        return any;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        for (BaseComponent widget : this.widgets) {
            if (widget.mouseDragged(mx, my, button, dx, dy)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (this.overlay != null) {
            return true;
        }
        for (int i = this.widgets.size() - 1; i >= 0; i--) {
            BaseComponent widget = this.widgets.get(i);
            if (widget.visible && widget.mouseScrolled(mx, my, amount)) {
                return true;
            }
        }
        return false;
    }

    /** Called by the host when the screen closes. */
    public void closing() {
    }
}
