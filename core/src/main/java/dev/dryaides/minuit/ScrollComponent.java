package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;
import java.util.ArrayList;
import java.util.List;

/**
 * A box that scrolls widgets taller than itself, with a scrollbar on its right. Widgets are added
 * at the place they would have unscrolled, in screen coordinates, and must leave {@link #BAR} pixels
 * free on the right; whatever leaves the box is cut. The wheel goes to a child that uses it first,
 * a slider for instance, then scrolls by {@link #step} pixels a notch; the thumb can be dragged.
 */
public class ScrollComponent extends BaseComponent {

    /** Room kept on the right for the scrollbar. */
    public static final int BAR = 6;

    private final List<BaseComponent> children = new ArrayList<>();
    /** Pixels a wheel notch moves. */
    public int step = 10;
    /** Room kept under the lowest child, for what it draws below its box (a shadow, a ring). */
    public int bottomRoom = 0;
    private int scroll;
    /** The mouse y on the thumb when a drag started, or -1. */
    private double grab = -1;
    private int grabScroll;

    public ScrollComponent(Box box) {
        super(box);
    }

    /** Adds a widget at its unscrolled place; the content grows to hold it. */
    public <W extends BaseComponent> W add(W widget) {
        widget.y -= this.scroll;
        this.children.add(widget);
        return widget;
    }

    /** Takes {@code widget} out, back at its unscrolled place; the scroll stays within what is left. */
    public boolean remove(BaseComponent widget) {
        if (!this.children.remove(widget)) {
            return false;
        }
        widget.y += this.scroll;
        widget.focused = false;
        setScroll(this.scroll);
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
        int kept = this.scroll;
        for (BaseComponent child : List.copyOf(this.children)) {
            remove(child);
        }
        this.scroll = 0;
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
                bottom = Math.max(bottom, child.y + this.scroll + child.h - this.y);
            }
        }
        return bottom == 0 ? 0 : bottom + this.bottomRoom + this.bleed;
    }

    public int scroll() {
        return this.scroll;
    }

    public void setScroll(int value) {
        int next = Maths.clamp(value, 0, maxScroll());
        for (BaseComponent child : this.children) {
            child.y -= next - this.scroll;
        }
        this.scroll = next;
    }

    /** Scrolls just enough for {@code box}, in unscrolled screen coordinates, to be inside. */
    public void reveal(Box box) {
        int top = box.y() - this.y;
        if (top < this.scroll) {
            setScroll(top);
        } else if (top + box.h() > this.scroll + this.h) {
            setScroll(top + box.h() - this.h);
        }
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - this.h);
    }

    private boolean inside(double mx, double my) {
        return isOver(mx, my) && mx < this.x + this.w - BAR;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        if (this.scroll > maxScroll()) {
            setScroll(maxScroll());
        }
        if (!this.focused) {
            this.children.forEach(child -> child.focused = false);
        }
        boolean in = inside(mx, my);
        int cx = in ? mx : Integer.MIN_VALUE;
        int cy = in ? my : Integer.MIN_VALUE;
        this.bleed = FrameStyle.bleedOf(theme.frame()).bottom();
        d.scissor(this.x, this.y, this.x + this.w, this.y + this.h);
        for (BaseComponent child : this.children) {
            if (child.y < this.y + this.h && child.y + child.h > this.y) {
                child.draw(d, theme, cx, cy, this.delta);
            }
        }
        d.endScissor();
        int max = maxScroll();
        if (max > 0) {
            Palette p = theme.palette();
            int[] thumb = thumb();
            boolean lit = this.grab >= 0 || (hovered && mx >= this.x + this.w - BAR && my >= thumb[0] && my < thumb[0] + thumb[1]);
            theme.frame().scroller(d, p, this.x + this.w - BAR, this.y + 2, this.h - 4, thumb[0], thumb[1], lit);
        }
    }

    /** The thumb's top and height. */
    private int[] thumb() {
        int track = this.h - 4;
        int length = Math.max(10, track * this.h / Math.max(1, contentHeight()));
        int max = maxScroll();
        int top = this.y + 2 + (max == 0 ? 0 : (track - length) * this.scroll / max);
        return new int[]{top, length};
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
                int[] thumb = thumb();
                if (my < thumb[0] || my >= thumb[0] + thumb[1]) {
                    setScroll(this.scroll + (my < thumb[0] ? -this.h : this.h));
                }
                this.grab = my;
                this.grabScroll = this.scroll;
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
        this.grab = -1;
        boolean any = false;
        for (BaseComponent child : this.children) {
            any |= child.mouseReleased(mx, my, button);
        }
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.grab >= 0) {
            int[] thumb = thumb();
            int travel = this.h - 4 - thumb[1];
            if (travel > 0) {
                setScroll(this.grabScroll + (int) Math.round((my - this.grab) * maxScroll() / travel));
            }
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
        int before = this.scroll;
        setScroll(this.scroll - (int) Math.round(amount * this.step));
        return this.scroll != before || maxScroll() > 0;
    }

    @Override
    public void tick() {
        for (BaseComponent child : List.copyOf(this.children)) {
            child.tick();
        }
    }
}
