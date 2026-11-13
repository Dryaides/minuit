package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * A box that scrolls widgets taller than itself, with a scrollbar on its right. Widgets are added
 * at the place they would have unscrolled, in screen coordinates, and must leave {@link #BAR} pixels
 * free on the right; whatever leaves the box is cut. The wheel goes to a child that uses it first,
 * a slider for instance, then scrolls by {@link #step} pixels a notch; the thumb can be dragged.
 * <p>The declared height is the most the box takes ({@link #max}); the layout reserves it and what sits below stays
 * put. Content up to that height shows whole, the box hugging it, with no bar. Past it the box drops to
 * {@link #NORMAL} of the declared height and scrolls, so a bar always hides a fifth or more of the box.
 * {@link #fit} off keeps the box at its declared height, scrolling on any overflow. A box that fills a fixed
 * region (a screen or page body, a showcase body, a dialog or drawer view) sets {@code fit} off: the drop to
 * {@link #NORMAL} is for boxes whose height is a cap in a flowing layout (lists, popups, panels in a column).
 */
public class ScrollComponent extends BaseComponent {

    /** Room kept on the right for the scrollbar. */
    public static final int BAR = 6;

    /** The share of the declared height a scrolling box keeps. */
    public static final double NORMAL = 0.8;

    private final List<BaseComponent> children = new ArrayList<>();
    /** False keeps the box at its declared height and scrolls on any overflow. */
    public boolean fit = true;
    /** The declared height: the most the box takes. */
    public int max;
    /** Pixels a wheel notch moves. */
    public int step = 10;
    /** Room kept under the lowest child, for what it draws below its box (a shadow, a ring). */
    public int bottomRoom = 0;
    private final ScrollBar bar = new ScrollBar();
    private Words words;

    public ScrollComponent(Box box) {
        super(box);
        this.max = this.h;
    }

    /** Adds a widget at its unscrolled place; the content grows to hold it. */
    public <W extends BaseComponent> W add(W widget) {
        widget.y -= this.bar.offset();
        if (this.words != null) {
            widget.words(this.words);
        }
        this.children.add(widget);
        return widget;
    }

    @Override
    void words(Words words) {
        this.words = words;
        this.children.forEach(child -> child.words(words));
    }

    /** Takes {@code widget} out, back at its unscrolled place; the scroll stays within what is left. */
    public boolean remove(BaseComponent widget) {
        if (!this.children.remove(widget)) {
            return false;
        }
        widget.y += this.bar.offset();
        widget.focused = false;
        setScroll(this.bar.offset());
        return true;
    }

    /** Takes every child out; the scroll goes back to the top. */
    public void clear() {
        for (BaseComponent child : List.copyOf(this.children)) {
            remove(child);
        }
        setScroll(0);
    }

    /**
     * Puts {@code widgets} in place of the children, each at its unscrolled place, keeping the scroll
     * where it was as far as the new content allows: a list refreshed from the server.
     */
    public void replaceChildren(List<? extends BaseComponent> widgets) {
        int kept = this.bar.offset();
        for (BaseComponent child : List.copyOf(this.children)) {
            remove(child);
        }
        this.bar.offset(0);
        widgets.forEach(this::add);
        setScroll(kept);
    }

    /** The current theme's bleed under a child, so the lowest one's band scrolls into view. */
    private int bleed;

    public List<BaseComponent> children() {
        return List.copyOf(this.children);
    }

    /** The height of what scrolls: from the top of the box to the lowest child. */
    public int contentHeight() {
        int bottom = 0;
        for (BaseComponent child : this.children) {
            if (child.visible) {
                bottom = Math.max(bottom, child.y + this.bar.offset() + child.h - this.y);
            }
        }
        return bottom == 0 ? 0 : bottom + this.bottomRoom + this.bleed;
    }

    public int scroll() {
        return this.bar.offset();
    }

    public void setScroll(int value) {
        int before = this.bar.offset();
        this.bar.content(contentHeight(), this.h);
        this.bar.offset(value);
        follow(before);
    }

    /** Moves the children by what the bar moved since {@code before}. */
    private void follow(int before) {
        int moved = this.bar.offset() - before;
        for (BaseComponent child : this.children) {
            child.y -= moved;
        }
    }

    /** Scrolls just enough for {@code box}, in unscrolled screen coordinates, to be inside. */
    public void reveal(Box box) {
        int top = box.y() - this.y;
        if (top < this.bar.offset()) {
            setScroll(top);
        } else if (top + box.h() > this.bar.offset() + this.h) {
            setScroll(top + box.h() - this.h);
        }
    }

    /** Sizes the box to its content within {@link #max}, or to {@link #NORMAL} of it once the content overflows. */
    private void fitContent() {
        if (!this.fit) {
            this.h = this.max;
            return;
        }
        this.h = fitted(contentHeight(), this.max, true);
    }

    /**
     * The scroll rule every scrolling box follows: {@code content} up to {@code max} shows whole, the box hugging
     * it; past it the box keeps {@link #NORMAL} of {@code max}. With {@code fit} off the box stays at {@code max}.
     */
    static int fitted(int content, int max, boolean fit) {
        if (!fit) {
            return max;
        }
        return content <= max ? content : (int) Math.round(max * NORMAL);
    }

    private int maxScroll() {
        this.bar.content(contentHeight(), this.h);
        return this.bar.max();
    }

    private boolean inside(double mx, double my) {
        return isOver(mx, my) && mx < this.x + this.w - BAR;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        setScroll(this.bar.offset());
        if (!this.focused) {
            this.children.forEach(child -> child.focused = false);
        }
        boolean in = inside(mx, my);
        int cx = in ? mx : Integer.MIN_VALUE;
        int cy = in ? my : Integer.MIN_VALUE;
        this.bleed = FrameStyle.bleedOf(theme.frame()).bottom();
        fitContent();
        setScroll(this.bar.offset());
        d.scissor(this.x, this.y, this.x + this.w, this.y + this.h);
        for (BaseComponent child : this.children) {
            if (child.y < this.y + this.h && child.y + child.h > this.y) {
                child.draw(d, theme, cx, cy, this.delta);
            }
        }
        d.endScissor();
        int barX = this.x + this.w - BAR;
        boolean lit = hovered && this.bar.overThumb(mx, my, barX, this.y + 2, this.h - 4);
        this.bar.draw(d, theme, barX, this.y + 2, this.h - 4, lit);
        edgeCue(d, theme.palette());
    }

    /** A 1 px light line across the whole viewport, scroll bar included, on each edge that clips content. */
    private void edgeCue(Draw d, Palette p) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int line = p.surfaceEdge();
        int offset = this.bar.offset();
        if (offset > 0) {
            d.fill(this.x, this.y, this.x + this.w, this.y + 1, line);
        }
        if (offset < max) {
            d.fill(this.x, this.y + this.h - 1, this.x + this.w, this.y + this.h, line);
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!inside(mx, my)) {
            return null;
        }
        for (int i = this.children.size() - 1; i >= 0; i--) {
            BaseComponent child = this.children.get(i);
            List<String> lines = child.visible ? child.tooltipAt(mx, my) : null;
            if (lines != null) {
                return lines;
            }
        }
        return this.tooltip;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.visible || !isOver(mx, my)) {
            return false;
        }
        if (!inside(mx, my)) {
            if (maxScroll() > 0 && button == 0) {
                int before = this.bar.offset();
                this.bar.press(mx, my, this.x + this.w - BAR, this.y + 2, this.h - 4);
                follow(before);
            }
            return true;
        }
        focusChild(mx, my);
        for (BaseComponent child : List.copyOf(this.children)) {
            if (child.visible && child.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    /** The child under a click takes the keyboard while this scroll holds it for the panel. */
    private void focusChild(double mx, double my) {
        for (BaseComponent child : this.children) {
            child.focused = this.focused && child.visible && child.active && child.takesFocus() && child.isOver(mx, my);
        }
    }

    @Override
    public List<BaseComponent> stops() {
        List<BaseComponent> stops = new ArrayList<>();
        for (BaseComponent child : PanelComponent.readingOrder(this.children)) {
            if (child.visible && child.active) {
                stops.addAll(child.stops());
            }
        }
        return stops;
    }

    @Override
    protected boolean takesFocus() {
        return this.children.stream().anyMatch(BaseComponent::takesFocus);
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        for (BaseComponent child : this.children) {
            if (child.focused && child.keyPressed(key, shift)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(int codepoint) {
        for (BaseComponent child : this.children) {
            if (child.focused && child.charTyped(codepoint)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.bar.release();
        boolean any = false;
        for (BaseComponent child : this.children) {
            any |= child.mouseReleased(mx, my, button);
        }
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.bar.dragging()) {
            int before = this.bar.offset();
            this.bar.content(contentHeight(), this.h);
            this.bar.drag(my);
            follow(before);
            return true;
        }
        for (BaseComponent child : this.children) {
            if (child.visible && child.mouseDragged(mx, my, button, dx, dy)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!isOver(mx, my)) {
            return false;
        }
        if (inside(mx, my)) {
            for (BaseComponent child : this.children) {
                if (child.visible && child.mouseScrolled(mx, my, amount)) {
                    return true;
                }
            }
        }
        int before = this.bar.offset();
        setScroll(before - (int) Math.round(amount * this.step));
        return this.bar.offset() != before || maxScroll() > 0;
    }

    @Override
    public void tick() {
        for (BaseComponent child : List.copyOf(this.children)) {
            child.tick();
        }
    }
}
