package dev.dryaides.minuit;

import java.util.function.Function;

/**
 * Ready-made renderers that show a Minecraft item ({@link ItemView}) and its name, as
 * {@code PlayerHeadComponent.row/.cell} do for heads. The item is drawn at 16 px with its count and durability,
 * shrunk by n/gs when the place is smaller, enlarged by a whole factor only where a cell or slide is large.
 * Items that cannot be drawn (no world loaded) show their first letter instead.
 *
 * <pre>
 * new ListComponent&lt;&gt;(box, stacks, ItemRenderers.row(s -&gt; s.view(), s -&gt; s.label()));
 * </pre>
 */
public final class ItemRenderers {
    private static final int GAP = 4;
    private static final int LABEL_H = 10;
    private static final int MAX_CELL_ZOOM = 3;
    private static final int MAX_SLIDE_ZOOM = 4;

    private ItemRenderers() {
    }

    /** List rows: the item at the left (rows of 18 hold it whole), then its name. A null name uses the item's own. */
    public static <T> ListComponent.RowRenderer<T> row(Function<T, ItemView> item, Function<T, String> name) {
        return (d, t, value, box, hovered, selected) -> drawRow(d, t, value, box, hovered, selected, item, name);
    }

    /** Grid cells: the item centred, whole-factor larger in a big cell, its name under it when the cell has room. */
    public static <T> GridComponent.CellRenderer<T> cell(Function<T, ItemView> item, Function<T, String> name) {
        return (d, t, value, box, hovered, selected) -> drawTile(d, t, value, box, hovered, selected, item, name, MAX_CELL_ZOOM);
    }

    /** Carousel slides: the item large (a whole factor, up to 4x) and its name under it. */
    public static <T> CarouselComponent.SlideRenderer<T> slide(Function<T, ItemView> item, Function<T, String> name) {
        return (d, t, value, box, hovered) -> drawTile(d, t, value, box, hovered, false, item, name, MAX_SLIDE_ZOOM);
    }

    /** Table cells: like a list row, for a column whose rows are 18 or taller. */
    public static <T> TableComponent.CellRenderer<T> tableCell(Function<T, ItemView> item, Function<T, String> name) {
        return (d, t, value, box, hovered, selected) -> drawRow(d, t, value, box, hovered, selected, item, name);
    }

    private static <T> String label(T value, ItemView view, Function<T, String> name) {
        String given = name == null ? null : name.apply(value);
        return given != null ? given : ItemArt.name(view);
    }

    private static <T> void drawRow(Draw d, Theme t, T value, Box box, boolean hovered, boolean selected,
            Function<T, ItemView> item, Function<T, String> name) {
        Palette p = t.palette();
        ItemView view = item.apply(value);
        int ink = RowLook.ink(p, RowLook.face(p, true, true, selected, hovered, 1F), selected, true);
        int room = Math.min(16, box.h() - 2);
        int left = box.x() + 2;
        if (view != null && room > 0) {
            int top = box.y() + (box.h() - room) / 2;
            int side = ItemArt.fit(d, p, view, left, top, room, ink, true);
            if (side == 16) {
                ItemArt.decorations(d, view, left, top);
            }
            left += room + GAP;
        }
        String text = label(value, view, name);
        int textW = box.right() - 2 - left;
        if (!text.isEmpty() && textW > 0) {
            d.textFit(text, left, RowLook.textY(box.y(), box.h()), textW, ink);
        }
    }

    private static <T> void drawTile(Draw d, Theme t, T value, Box box, boolean hovered, boolean selected,
            Function<T, ItemView> item, Function<T, String> name, int maxZoom) {
        Palette p = t.palette();
        ItemView view = item.apply(value);
        int ink = RowLook.ink(p, RowLook.face(p, true, true, selected, hovered, 1F), selected, true);
        String text = label(value, view, name);
        boolean labelled = !text.isEmpty() && box.h() >= 16 + LABEL_H + 4;
        int artRoom = Math.min(box.w() - 2, box.h() - 2 - (labelled ? LABEL_H : 0));
        if (view != null && artRoom > 0) {
            int zoom = Math.max(1, Math.min(maxZoom, artRoom / 16));
            if (artRoom >= 16) {
                int side = 16 * zoom;
                int left = box.x() + (box.w() - side) / 2;
                int top = box.y() + 1 + (artRoom - side) / 2;
                if (zoom == 1) {
                    view.draw(d, left, top, ink, 1F);
                    ItemArt.decorations(d, view, left, top);
                } else {
                    d.push(left, top, zoom);
                    view.draw(d, 0, 0, ink, 1F);
                    ItemArt.decorations(d, view, 0, 0);
                    d.pop();
                }
            } else {
                ItemArt.fit(d, p, view, box.x() + (box.w() - artRoom) / 2, box.y() + 1, artRoom, ink, true);
            }
        }
        if (labelled) {
            int y = box.bottom() - LABEL_H;
            String shown = d.ellipsize(text, box.w() - 4);
            d.text(shown, box.x() + (box.w() - d.width(shown)) / 2, y, ink);
        }
    }
}
