package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scrolling list of rows drawn by a callback, with a thin scrollbar. A row may hold widgets
 * ({@link #rowWidgets}): they take the clicks, the keyboard and Tab before the row does. Hovered rows
 * glow in and out as a button does.
 */
public class ListComponent<T> extends BaseComponent {

    /** Draws one row inside the box it is given. */
    public interface RowRenderer<T> {
        void draw(Draw d, Theme theme, T item, Box row, boolean hovered, boolean selected);
    }

    /** A click on a row outside its widgets: the item, its index, the mouse inside the row box. */
    public interface RowClick<T> {
        void clicked(T item, int index, int mouseX, int mouseY);
    }

    /** The widgets one row holds, placed inside {@code row}, the row's box with the list scrolled to the top. */
    public interface RowWidgets<T> {
        List<BaseComponent> build(T item, int index, Box row);
    }

    /** Room kept on the right for the scrollbar. */
    public static final int BAR = 6;

    private final int rowHeight;
    private final RowRenderer<T> renderer;
    private final RowClick<T> onClick;
    private List<T> items = new ArrayList<>();
    private RowWidgets<T> rowWidgets;
    /** Each row's widgets, moved up by the scroll as {@link ScrollComponent} moves its children. */
    private final List<List<BaseComponent>> widgets = new ArrayList<>();
    private final HoverGlows<T> glows = new HoverGlows<>();
    private double scroll;
    public T selected;

    public ListComponent(Box box, int rowHeight, RowRenderer<T> renderer, Consumer<T> onClick) {
        this(box, rowHeight, renderer, (item, index, mouseX, mouseY) -> onClick.accept(item));
    }

    public ListComponent(Box box, int rowHeight, RowRenderer<T> renderer, RowClick<T> onClick) {
        super(box);
        this.rowHeight = rowHeight;
        this.renderer = renderer;
        this.onClick = onClick;
    }

    /** Gives every row the widgets {@code build} makes, built again on each {@link #setItems}. */
    public ListComponent<T> rowWidgets(RowWidgets<T> build) {
        this.rowWidgets = build;
        buildWidgets();
        return this;
    }

    public void setItems(List<T> items) {
        this.items = new ArrayList<>(items);
        this.scroll = Math.min(this.scroll, maxScroll());
        buildWidgets();
    }

    private void buildWidgets() {
        this.widgets.clear();
        if (this.rowWidgets == null) {
            return;
        }
        for (int i = 0; i < this.items.size(); i++) {
            List<BaseComponent> row = new ArrayList<>(this.rowWidgets.build(this.items.get(i), i, rowBox(i, 0)));
            row.forEach(w -> w.y -= (int) this.scroll);
            this.widgets.add(row);
        }
    }

    /** The widgets of the row at {@code index}, in screen coordinates as drawn now. */
    public List<BaseComponent> widgetsOf(int index) {
        return index >= 0 && index < this.widgets.size() ? List.copyOf(this.widgets.get(index)) : List.of();
    }

    public List<T> items() {
        return List.copyOf(this.items);
    }

    public double scroll() {
        return this.scroll;
    }

    public void setScroll(double scroll) {
        double next = Maths.clamp(scroll, 0, maxScroll());
        int shift = (int) next - (int) this.scroll;
        this.widgets.forEach(row -> row.forEach(w -> w.y -= shift));
        this.scroll = next;
    }

    /** Scrolls just enough for this row to be fully inside the list. */
    public void reveal(T item) {
        int index = this.items.indexOf(item);
        if (index < 0) {
            return;
        }
        int rowTop = index * this.rowHeight;
        if (rowTop < this.scroll) {
            setScroll(rowTop);
        } else if (rowTop + this.rowHeight > this.scroll + this.h) {
            setScroll(rowTop + this.rowHeight - this.h);
        }
    }

    /** Scrolls the row holding {@code stop} into view; the panel calls it when the keyboard lands there. */
    void revealStop(BaseComponent stop) {
        for (int i = 0; i < this.widgets.size(); i++) {
            for (BaseComponent w : this.widgets.get(i)) {
                if (w == stop || w.stops().contains(stop)) {
                    reveal(this.items.get(i));
                    return;
                }
            }
        }
    }

    private double maxScroll() {
        return Math.max(0, this.items.size() * this.rowHeight - this.h);
    }

    private Box rowBox(int index, int scrolled) {
        return new Box("row", this.x + 4, this.y + index * this.rowHeight - scrolled, this.w - BAR - 8, this.rowHeight);
    }

    private List<BaseComponent> allWidgets() {
        List<BaseComponent> all = new ArrayList<>();
        this.widgets.forEach(all::addAll);
        return all;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        theme.frame().well(d, p, this.x, this.y, this.w, this.h, false);
        if (!this.focused) {
            allWidgets().forEach(w -> w.focused = false);
        }
        boolean in = hovered && mx < this.x + this.w - BAR;
        int cx = in ? mx : Integer.MIN_VALUE;
        int cy = in ? my : Integer.MIN_VALUE;
        this.glows.frame(clock.getAsLong());
        d.scissor(this.x + 1, this.y + 1, this.x + this.w - 1, this.y + this.h - 1);
        int first = (int) (this.scroll / this.rowHeight);
        for (int i = first; i < this.items.size(); i++) {
            int ry = this.y + i * this.rowHeight - (int) this.scroll;
            if (ry > this.y + this.h) {
                break;
            }
            T item = this.items.get(i);
            boolean rowHover = in && my >= ry && my < ry + this.rowHeight;
            boolean isSelected = item.equals(this.selected);
            float glow = this.glows.of(item, rowHover);
            if (isSelected || glow > 0F) {
                Shapes.rounded(d, this.x + 2, ry + 1, this.w - BAR - 4, this.rowHeight - 2, Math.max(2, theme.frame().corner()),
                        isSelected ? p.raisedHover() : Shapes.mix(Shapes.alpha(p.raised(), 0), p.raised(), glow));
            }
            this.renderer.draw(d, theme, item, rowBox(i, (int) this.scroll), rowHover, isSelected);
            for (BaseComponent w : widgetsOf(i)) {
                w.draw(d, theme, cx, cy, this.delta);
            }
        }
        d.endScissor();
        double max = maxScroll();
        if (max > 0) {
            int track = this.h - 4;
            int thumb = Math.max(10, (int) (track * (this.h / (double) (this.items.size() * this.rowHeight))));
            int ty = this.y + 2 + (int) ((track - thumb) * (this.scroll / max));
            Shapes.rounded(d, this.x + this.w - 5, this.y + 2, 3, track, 1, p.track());
            Shapes.rounded(d, this.x + this.w - 5, ty, 3, thumb, 1, p.accent());
        }
    }

    /** The row under the mouse, or null. Used for the row tooltips. */
    public T rowAt(double mx, double my) {
        int index = indexAt(mx, my);
        return index < 0 ? null : this.items.get(index);
    }

    /** The index of the row under the mouse, or -1. */
    public int indexAt(double mx, double my) {
        if (!isOver(mx, my) || mx >= this.x + this.w - BAR) {
            return -1;
        }
        int index = (int) ((my - this.y + this.scroll) / this.rowHeight);
        return index >= 0 && index < this.items.size() ? index : -1;
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        for (BaseComponent w : widgetsOf(indexAt(mx, my))) {
            List<String> lines = w.visible ? w.tooltipAt(mx, my) : null;
            if (lines != null) {
                return lines;
            }
        }
        return super.tooltipAt(mx, my);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!isOver(mx, my)) {
            return false;
        }
        setScroll(this.scroll - amount * this.rowHeight);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int index = this.active ? indexAt(mx, my) : -1;
        if (index < 0) {
            return false;
        }
        for (BaseComponent w : allWidgets()) {
            w.focused = this.focused && w.visible && w.active && w.takesFocus() && w.isOver(mx, my);
        }
        for (BaseComponent w : widgetsOf(index)) {
            if (w.visible && w.mouseClicked(mx, my, button)) {
                return true;
            }
        }
        T row = this.items.get(index);
        Box box = rowBox(index, (int) this.scroll);
        this.selected = row;
        click();
        this.onClick.clicked(row, index, (int) mx - box.x(), (int) my - box.y());
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = false;
        for (BaseComponent w : allWidgets()) {
            any |= w.mouseReleased(mx, my, button);
        }
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        for (BaseComponent w : allWidgets()) {
            if (w.visible && w.mouseDragged(mx, my, button, dx, dy)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<BaseComponent> stops() {
        List<BaseComponent> stops = new ArrayList<>();
        for (BaseComponent w : allWidgets()) {
            if (w.visible && w.active) {
                stops.addAll(w.stops());
            }
        }
        return stops;
    }

    @Override
    protected boolean takesFocus() {
        return allWidgets().stream().anyMatch(BaseComponent::takesFocus);
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        for (BaseComponent w : allWidgets()) {
            if (w.focused && w.keyPressed(key, shift)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(int codepoint) {
        for (BaseComponent w : allWidgets()) {
            if (w.focused && w.charTyped(codepoint)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        allWidgets().forEach(BaseComponent::tick);
    }
}
