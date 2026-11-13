package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Accent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * A row of joined toggle buttons that share their edges, like shadcn's Toggle Group: one plate cut
 * into equal items, rounded only at the two ends. An item that is on holds its hover face. In
 * {@link Mode#SINGLE} at most one item is on, in {@link Mode#MULTIPLE} any number. The arrows move
 * between items, Space or Enter toggles the one under the keyboard, and disabled items are skipped.
 */
public class ToggleGroupComponent extends BaseComponent {

    public enum Mode {
        SINGLE,
        MULTIPLE
    }

    private final Mode mode;
    private final List<Item> items = new ArrayList<>();
    private final Consumer<Set<Integer>> onChange;
    /** In {@link Mode#SINGLE}: a click on the item that is on keeps it on instead of clearing the group. */
    public boolean required;
    /** How the pushed segment shows; {@link PressStyle#OVERLAP} by default. */
    private PressStyle press = PressStyle.OVERLAP;
    /** Per item, how far its label moves down this frame, set by the row drawn under the labels. */
    private int[] shift = new int[0];

    /** @param onChange the indexes that are on, after each change made by the player */
    public ToggleGroupComponent(Box box, List<String> labels, Mode mode, Set<Integer> on, Consumer<Set<Integer>> onChange) {
        super(box);
        this.mode = mode;
        this.onChange = onChange;
        Box[] cells = box.columns(labels.size(), 0);
        for (int i = 0; i < labels.size(); i++) {
            this.items.add(new Item(cells[i], labels.get(i), i));
        }
        set(on);
    }

    public ToggleGroupComponent required(boolean value) {
        this.required = value;
        return this;
    }

    /** Sets how the pushed segment shows; {@link PressStyle#RAISED_SELECTED} is an option only, never a default. */
    public ToggleGroupComponent style(PressStyle press) {
        this.press = press;
        return this;
    }

    public PressStyle style() {
        return this.press;
    }

    public int count() {
        return this.items.size();
    }

    public boolean isOn(int index) {
        return this.items.get(index).on;
    }

    /** The indexes that are on, in order. */
    public Set<Integer> on() {
        Set<Integer> on = new TreeSet<>();
        for (Item item : this.items) {
            if (item.on) {
                on.add(item.index);
            }
        }
        return on;
    }

    /** Sets which items are on without telling the listener; in {@link Mode#SINGLE} only the lowest index is kept. */
    public void set(Set<Integer> on) {
        boolean taken = false;
        for (Item item : this.items) {
            item.on = on.contains(item.index) && !(this.mode == Mode.SINGLE && taken);
            taken |= item.on;
        }
    }

    /** Greys out item {@code index}: drawn disabled, skipped by clicks and by the keyboard; {@code tip} says why. */
    public ToggleGroupComponent disabled(int index, boolean disabled, String... tip) {
        Item item = this.items.get(index);
        item.active = !disabled;
        item.tooltip(tip);
        return this;
    }

    /** What a click or Space on item {@code index} does, under the selection rules of the mode. */
    public void toggle(int index) {
        Item hit = this.items.get(index);
        if (!this.active || !hit.active) {
            return;
        }
        if (this.mode == Mode.MULTIPLE) {
            hit.on = !hit.on;
        } else if (hit.on) {
            if (this.required) {
                return;
            }
            hit.on = false;
        } else {
            this.items.forEach(item -> item.on = item == hit);
        }
        this.onChange.accept(on());
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        for (Item item : this.items) {
            if (item.tooltip != null && item.isOver(mx, my)) {
                return item.tooltip;
            }
        }
        return super.tooltipAt(mx, my);
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        if (this.press != PressStyle.SEPARATE) {
            joined(d, theme);
            for (Item item : this.items) {
                item.draw(d, theme, mx, my, this.delta);
            }
            return;
        }
        for (Item item : this.items) {
            item.draw(d, theme, mx, my, this.delta);
        }
        Palette p = theme.palette();
        if (theme.frame().gameWidgets()) {
            return;
        }
        for (int i = 1; i < this.items.size(); i++) {
            int x = this.items.get(i).x;
            d.fill(x, this.y + 1, x + 1, this.y + this.h - 1, p.wellEdge());
        }
    }

    /** The plates of a joined row in {@link #press}; the items then draw only their labels on top. */
    private void joined(Draw d, Theme theme) {
        Palette p = theme.palette();
        int n = this.items.size();
        int[] xs = new int[n + 1];
        int[] faces = new int[n];
        boolean[] on = new boolean[n];
        for (int i = 0; i < n; i++) {
            Item item = this.items.get(i);
            boolean usable = item.active && this.active;
            xs[i] = item.x;
            on[i] = usable && item.on;
            faces[i] = !usable ? p.disabled() : on[i] ? Shapes.mix(p.primary(), p.primaryHover(), item.hoverAnim)
                    : Shapes.mix(p.raised(), p.raisedHover(), item.hoverAnim);
        }
        xs[n] = this.x + this.w;
        this.shift = this.press.row(d, p, theme.frame(), xs, this.y, this.h, faces, on);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || button != 0) {
            return false;
        }
        for (Item item : this.items) {
            if (item.active && item.isOver(mx, my)) {
                item.click();
                toggle(item.index);
                return true;
            }
        }
        return false;
    }

    @Override
    public List<BaseComponent> stops() {
        return this.active ? this.items.stream().filter(item -> item.active).map(item -> (BaseComponent) item).toList()
                : List.of();
    }

    /**
     * One segment of a joined row or column: the plate runs past the inner edges and is cut there, so only the
     * ends get the frame's corners and a sunk segment shows its neighbours' edges beside it. Shared by the side rail.
     */
    static void segment(Draw d, Theme theme, int x, int y, int w, int h, boolean first, boolean last, boolean vertical,
                        int face, Shapes.Relief relief) {
        int reach = 8;
        int x0 = x;
        int y0 = y;
        int x1 = x + w;
        int y1 = y + h;
        if (vertical) {
            y0 = first ? y : y - reach;
            y1 = last ? y + h : y + h + reach;
        } else {
            x0 = first ? x : x - reach;
            x1 = last ? x + w : x + w + reach;
        }
        d.scissor(x, y, x + w, y + h);
        theme.frame().button(d, theme.palette(), x0, y0, x1 - x0, y1 - y0, null, face, relief);
        d.endScissor();
    }

    private final class Item extends BaseComponent {
        final String label;
        final int index;
        boolean on;

        Item(Box box, String label, int index) {
            super(box);
            this.label = label;
            this.index = index;
        }

        boolean first() {
            return this.index == 0;
        }

        boolean last() {
            return this.index == ToggleGroupComponent.this.items.size() - 1;
        }

        @Override
        protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
            Palette p = theme.palette();
            boolean usable = this.active && ToggleGroupComponent.this.active;
            FrameStyle style = theme.frame();
            ToggleGroupComponent group = ToggleGroupComponent.this;
            if (group.press != PressStyle.SEPARATE) {
                boolean on = usable && this.on;
                int ink = !usable ? p.disabledText() : on ? p.primaryText() : p.raisedText();
                int room = this.w - 6;
                int fit = Math.min(room, d.width(this.label));
                int sink = this.index < group.shift.length ? group.shift[this.index] : 0;
                d.textShrink(this.label, this.x + (this.w - fit) / 2, this.y + sink, room,
                        PressStyle.faceHeight(style, this.h), 0.75F, ink);
                return;
            }
            if (style.gameWidgets()) {
                boolean shown = usable && this.on;
                style.tab(d, p, this.x, this.y, this.w, this.h, shown, usable && (hovered || this.focused));
                int top = this.y + style.tabInset(this.h, shown) + (shown ? 2 : 1);
                int bottom = this.y + this.h - (shown ? 2 : 3);
                int fit = Math.min(d.width(this.label), this.w - 6);
                d.textShrink(this.label, this.x + (this.w - fit) / 2, top, this.w - 6, bottom - top, 0.75F,
                        style.tabText(p, shown, usable));
                return;
            }
            int face;
            int text;
            if (!usable) {
                // never heavier than the idle segments: on a light chroma it stays as light as they are
                face = Shapes.luminance(p.disabled()) < Shapes.luminance(p.raised()) && !Accent.dark(p)
                        ? p.raised() : p.disabled();
                text = p.disabledText();
            } else if (this.on) {
                face = theme.frame().selectedFace(p, this.hoverAnim);
                text = Shapes.textOn(p, face);
            } else {
                face = Shapes.mix(p.raised(), p.raisedHover(), this.hoverAnim);
                text = p.raisedText();
            }
            segment(d, theme, this.x, this.y, this.w, this.h, first(), last(), false, face,
                    usable && this.on ? theme.frame().selectedRelief() : Shapes.Relief.RAISED);
            int room = this.w - 6;
            int fit = Math.min(room, d.width(this.label));
            int sink = usable && this.on ? style.selectedTravel() : 0;
            d.textShrink(this.label, this.x + (this.w - fit) / 2, this.y + sink, room,
                    this.h - style.buttonLip(this.h), 0.75F, text);
        }

        @Override
        protected boolean navigable() {
            return true;
        }

        @Override
        protected boolean activate() {
            if (!this.active) {
                return false;
            }
            click();
            toggle(this.index);
            return true;
        }

        @Override
        protected int focusCorner() {
            return first() || last() ? 3 : 0;
        }
    }
}
