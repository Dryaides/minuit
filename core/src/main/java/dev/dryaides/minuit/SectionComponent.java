package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * A titled section the player folds and unfolds with a click on its header. The widgets inside are
 * added at the place they have while it is open, in screen coordinates; folded, the section is only
 * its {@link #HEADER} tall and draws and answers for none of them. Its height follows, so the panel
 * moves what lies below from {@link #onToggle}, with {@link #moveTo} on the widgets that follow.
 */
public class SectionComponent extends BaseComponent {

    /** The height of the header row. */
    public static final int HEADER = 14;

    public String title;
    public boolean open;
    private final List<BaseComponent> children = new ArrayList<>();
    private Runnable onToggle = () -> {
    };

    public SectionComponent(Box box, String title, boolean open) {
        super(box.x(), box.y(), box.w(), HEADER);
        this.title = title;
        this.open = open;
        fit();
    }

    /** Called after the section folds or unfolds, once its height has changed. */
    public SectionComponent onToggle(Runnable onToggle) {
        this.onToggle = onToggle;
        return this;
    }

    /** Adds a widget at its place while the section is open, below the header; the body grows to hold it. */
    public <W extends BaseComponent> W add(W widget) {
        this.children.add(widget);
        fit();
        return widget;
    }

    public List<BaseComponent> children() {
        return List.copyOf(this.children);
    }

    public void setOpen(boolean value) {
        if (this.open != value) {
            this.open = value;
            fit();
            this.onToggle.run();
        }
    }

    /** Moves the section and its widgets so that its top is at {@code top}. */
    public void moveTo(int top) {
        int shift = top - this.y;
        this.y = top;
        for (BaseComponent child : this.children) {
            child.y += shift;
        }
    }

    private void fit() {
        int bottom = this.y + HEADER;
        if (this.open) {
            for (BaseComponent child : this.children) {
                bottom = Math.max(bottom, child.y + child.h + 2);
            }
        }
        this.h = bottom - this.y;
    }

    private boolean onHeader(double mx, double my) {
        return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + HEADER;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        boolean lit = hovered && onHeader(mx, my);
        if (lit) {
            Shapes.rounded(d, this.x, this.y, this.w, HEADER, 3, Shapes.alpha(p.raised(), 160));
        }
        int ink = this.active ? (lit ? p.text() : p.textSoft()) : p.disabledText();
        int cy = this.y + HEADER / 2;
        if (this.open) {
            d.fill(this.x + 4, cy - 1, this.x + 9, cy, ink);
            d.fill(this.x + 5, cy, this.x + 8, cy + 1, ink);
            d.fill(this.x + 6, cy + 1, this.x + 7, cy + 2, ink);
        } else {
            d.fill(this.x + 5, cy - 2, this.x + 6, cy + 3, ink);
            d.fill(this.x + 6, cy - 1, this.x + 7, cy + 2, ink);
            d.fill(this.x + 7, cy, this.x + 8, cy + 1, ink);
        }
        d.textFit(this.title, this.x + 13, this.y + (HEADER - 8) / 2, this.w - 15, ink);
        if (this.open) {
            d.fill(this.x + 2, this.y + HEADER, this.x + this.w - 2, this.y + HEADER + 1, p.surfaceEdge());
            if (!this.focused) {
                this.children.forEach(child -> child.focused = false);
            }
            for (BaseComponent child : this.children) {
                child.draw(d, theme, mx, my, this.delta);
            }
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        if (this.open) {
            for (int i = this.children.size() - 1; i >= 0; i--) {
                BaseComponent child = this.children.get(i);
                List<String> lines = child.visible ? child.tooltipAt(mx, my) : null;
                if (lines != null) {
                    return lines;
                }
            }
        }
        return this.tooltip;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.visible || !this.active || !isOver(mx, my)) {
            return false;
        }
        if (onHeader(mx, my)) {
            if (button != 0) {
                return false;
            }
            click();
            setOpen(!this.open);
            return true;
        }
        if (!this.open) {
            return false;
        }
        for (BaseComponent child : this.children) {
            child.focused = this.focused && child.visible && child.active && child.takesFocus() && child.isOver(mx, my);
        }
        for (BaseComponent child : List.copyOf(this.children)) {
            if (child.visible && child.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = false;
        for (BaseComponent child : this.children) {
            any |= child.mouseReleased(mx, my, button);
        }
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.open) {
            for (BaseComponent child : this.children) {
                if (child.visible && child.mouseDragged(mx, my, button, dx, dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (this.open && isOver(mx, my)) {
            for (BaseComponent child : this.children) {
                if (child.visible && child.mouseScrolled(mx, my, amount)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean takesFocus() {
        return this.open && this.children.stream().anyMatch(BaseComponent::takesFocus);
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
    public void tick() {
        for (BaseComponent child : List.copyOf(this.children)) {
            child.tick();
        }
    }
}
