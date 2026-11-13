package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Redraw;
import dev.dryaides.minuit.internal.FitCache;
import dev.dryaides.minuit.internal.Lists;

import dev.dryaides.minuit.internal.Maths;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cards in rows of {@code columns}, each drawn by a callback: theme cards, thumbnails, player heads.
 * The cells share the width as {@link Box#columns} does; the grid is as tall as its rows, so a grid
 * taller than its place goes in a {@link ScrollComponent}. A renderer may draw up to {@code gapX / 2} and
 * {@code gapY / 2} pixels outside its cell, for a selection ring or a shadow.
 */
public class GridComponent<T> extends BaseComponent {

    /** Draws one card inside the cell it is given. */
    public interface CellRenderer<T> {
        void draw(Draw d, Theme theme, T item, Box cell, boolean hovered, boolean selected);

        /**
         * The draw the grid calls, with the card's hover glow: 0 to 1, eased on {@link BaseComponent#clock}
         * as a button's. A renderer made by {@link #glowing} fades its hover with it; a plain one sees the
         * card hovered while the mouse is on it.
         */
        default void draw(Draw d, Theme theme, T item, Box cell, boolean hovered, float glow, boolean selected) {
            draw(d, theme, item, cell, hovered, selected);
        }
    }

    /** Draws one card with its hover glow, 0 at rest and 1 under the mouse. */
    public interface GlowingCell<T> {
        void draw(Draw d, Theme theme, T item, Box cell, float glow, boolean selected);
    }

    /** A renderer that fades its hover look in and out with the glow. */
    public static <T> CellRenderer<T> glowing(GlowingCell<T> cell) {
        return new CellRenderer<>() {
            @Override
            public void draw(Draw d, Theme theme, T item, Box box, boolean hovered, boolean selected) {
                cell.draw(d, theme, item, box, hovered ? 1F : 0F, selected);
            }

            @Override
            public void draw(Draw d, Theme theme, T item, Box box, boolean hovered, float glow, boolean selected) {
                cell.draw(d, theme, item, box, glow, selected);
            }
        };
    }

    /**
     * A card with a 16:9 picture above its label, the picture a placeholder until its {@link AsyncImage}
     * is ready: a map catalogue, a gallery. Hover fades a ring in, the selected one wears the accent ring.
     */
    public static <T> CellRenderer<T> thumbnails(java.util.function.Function<T, AsyncImage> image,
                                                 java.util.function.Function<T, String> label) {
        FitCache labels = new FitCache();
        return glowing((d, theme, item, cell, glow, selected) -> {
            Palette p = theme.palette();
            FrameStyle f = theme.frame();
            f.section(d, p, cell.x(), cell.y(), cell.w(), cell.h());
            if (selected || glow > 0F) {
                Shapes.ring(d, cell.x() - 1, cell.y() - 1, cell.w() + 2, cell.h() + 2, f.corner() + 1,
                        selected ? p.accent() : Shapes.mix(Shapes.alpha(p.wellEdge(), 0), p.wellEdge(), glow));
            }
            int thumbW = Math.min(cell.w() - 8, (cell.h() - 18) * 16 / 9);
            int thumbH = thumbW * 9 / 16;
            AsyncImage.draw(d, p, image.apply(item), cell.x() + (cell.w() - thumbW) / 2, cell.y() + 4, thumbW, thumbH);
            d.centered(labels.fit(d, item, label.apply(item), cell.w() - 6).cut(), cell.x() + cell.w() / 2,
                    cell.y() + cell.h() - 12, selected ? p.text() : p.textSoft());
        });
    }

    private final int columns;
    private final int cellHeight;
    private final int gapX;
    private final int gapY;
    private final CellRenderer<T> renderer;
    private final Consumer<T> onClick;
    private List<T> items = List.of();
    private int cursor;
    private final HoverGlows<T> glows = new HoverGlows<>();
    public T selected;

    /** @param box its x, y and width; the height follows the items */
    public GridComponent(Box box, int columns, int cellHeight, int gapX, int gapY, List<T> items, CellRenderer<T> renderer,
                 Consumer<T> onClick) {
        super(box);
        this.columns = Math.max(1, columns);
        this.cellHeight = cellHeight;
        this.gapX = gapX;
        this.gapY = gapY;
        this.renderer = renderer;
        this.onClick = onClick;
        setItems(items);
    }

    public void setItems(List<T> items) {
        this.items = Lists.withoutNulls(items);
        int rows = (this.items.size() + this.columns - 1) / this.columns;
        this.h = Math.max(0, rows * (this.cellHeight + this.gapY) - this.gapY);
    }

    public List<T> items() {
        return this.items;
    }

    public GridComponent<T> selected(T item) {
        this.selected = item;
        return this;
    }

    /** The cell of the item at {@code index}, in screen coordinates. */
    public Box cell(int index) {
        Box row = new Box("cell", this.x, this.y + index / this.columns * (this.cellHeight + this.gapY), this.w,
                this.cellHeight);
        return row.columns(this.columns, this.gapX)[index % this.columns];
    }

    /** The item under the mouse, or null; the gaps hold none. */
    public T itemAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        int pitch = this.cellHeight + this.gapY;
        int from = 0;
        int to = this.items.size();
        if (this.gapY >= 0 && pitch > 0) {
            // only rows r-1..r+1 can hold the point, so the first match is the one the full scan finds
            int row = (int) Math.floor((my - this.y) / pitch);
            from = Math.max(0, (row - 1) * this.columns);
            to = (int) Math.min(to, (long) (row + 2) * this.columns);
        }
        for (int i = from; i < to; i++) {
            if (cell(i).contains(mx, my)) {
                return this.items.get(i);
            }
        }
        return null;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        T over = this.active && hovered ? itemAt(mx, my) : null;
        this.glows.frame(Redraw.quietClock());
        for (int i = 0; i < this.items.size(); i++) {
            T item = this.items.get(i);
            boolean on = item.equals(over);
            this.renderer.draw(d, theme, item, cell(i), on, this.glows.of(item, on), item.equals(this.selected));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        T item = this.active && button == 0 ? itemAt(mx, my) : null;
        if (item == null) {
            return false;
        }
        this.cursor = this.items.indexOf(item);
        this.selected = item;
        click();
        this.onClick.accept(item);
        return true;
    }

    @Override
    protected boolean navigable() {
        return !this.items.isEmpty();
    }

    /** The keyboard lands on the selected card, else on the first card forward and the last one back. */
    @Override
    protected void enter(boolean forward) {
        int chosen = this.selected == null ? -1 : this.items.indexOf(this.selected);
        this.cursor = chosen >= 0 ? chosen : forward ? 0 : this.items.size() - 1;
    }

    /** The arrows move between cards; past the first or the last one they leave the grid. */
    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        int at = Maths.clamp(this.cursor, 0, Math.max(0, this.items.size() - 1));
        int target = switch (key) {
            case LEFT -> at % this.columns == 0 ? -1 : at - 1;
            case RIGHT -> at % this.columns == this.columns - 1 ? -1 : at + 1;
            case UP -> at - this.columns;
            case DOWN -> at + this.columns;
            default -> -1;
        };
        if (target < 0 || target >= this.items.size()) {
            return false;
        }
        this.cursor = target;
        return true;
    }

    @Override
    protected boolean activate() {
        if (this.items.isEmpty()) {
            return false;
        }
        this.selected = this.items.get(Maths.clamp(this.cursor, 0, this.items.size() - 1));
        click();
        this.onClick.accept(this.selected);
        return true;
    }

    @Override
    protected Box focusBox() {
        return this.items.isEmpty() ? box() : cell(Maths.clamp(this.cursor, 0, this.items.size() - 1));
    }
}
