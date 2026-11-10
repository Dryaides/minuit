package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.FitCache;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An expandable tree: roots plus a function that gives a node's children. Only the rows in view are
 * drawn, and {@code children} is only asked about nodes that are on screen or being expanded, so a
 * large tree costs what its open rows cost.
 *
 * <pre>{@code
 * TreeComponent<File> files = add(new TreeComponent<>(at(new Box("files", 8, 8, 180, 120)),
 *         List.of(root), File::kids, File::name)
 *         .icon(f -> f.isDirectory() ? Icon.FOLDER : null)
 *         .onSelect(this::preview)
 *         .onActivate(this::open));
 * files.expand(root);
 * files.setRoots(List.of(newRoot)); // nodes that are equal stay open
 * }</pre>
 *
 * <p>Click an arrow to fold, a row to select, twice to activate. With focus: up and down move the
 * selection, right expands and then enters, left folds and then goes to the parent, home and end
 * jump, Enter or Space activates. Expanded nodes and the selection are kept by {@code equals} and
 * {@code hashCode}, so a node reachable from two parents opens in both, and its row shows selected in
 * both. A node that contains itself shows the repeat as a leaf rather than recursing.
 */
public class TreeComponent<T> extends BaseComponent implements ItemSource {
    /** Draws what sits right of the arrow; the tree has drawn the row's background, guides and arrow. */
    @FunctionalInterface
    public interface RowRenderer<T> {
        void draw(Draw d, Theme theme, T node, Box row, int depth, boolean expanded, boolean hovered, boolean selected);
    }

    /** Width of the scroll bar's strip. */
    static final int BAR = ScrollBar.WIDTH;
    private static final int ARROW = 11;
    private static final int ICON_GAP = 2;
    private static final long DOUBLE_CLICK_MS = 350L;
    private static final int FOLD_MS = 120;

    private static final class Row<T> {
        final T node;
        final int depth;
        final int parent;
        final boolean expandable;
        final boolean expanded;

        Row(T node, int depth, int parent, boolean expandable, boolean expanded) {
            this.node = node;
            this.depth = depth;
            this.parent = parent;
            this.expandable = expandable;
            this.expanded = expanded;
        }
    }

    /** One open level while flattening: replaces recursion so nesting depth cannot overflow the stack. */
    private static final class Level<T> {
        final T node;
        final int rowIndex;
        final Iterator<T> kids;

        Level(T node, int rowIndex, Iterator<T> kids) {
            this.node = node;
            this.rowIndex = rowIndex;
            this.kids = kids;
        }
    }

    private final FitCache fits = new FitCache();
    private final Function<T, List<T>> children;
    private final Function<T, String> label;
    private final Set<T> expanded = new HashSet<>();
    private List<T> roots = List.of();
    private List<Row<T>> rows = new ArrayList<>();

    private Function<T, Icon> icon;
    private Function<T, IconArt> iconArt;
    private RowRenderer<T> renderer;
    private Function<T, List<String>> rowTooltip;
    private Consumer<T> onSelect;
    private Consumer<T> onActivate;
    private BiConsumer<T, Boolean> onToggle;
    private String emptyText;
    private int rowHeight = 12;
    private boolean rowHeightSet;
    private boolean itemTooltips = true;
    private boolean itemTipShown;
    private int indent = 9;
    private boolean guides = true;

    private T selected;
    private int cursor = -1;
    private final ScrollBar bar = new ScrollBar();
    private FrameStyle frame = FrameStyles.VANILLA;
    private Box[] rowBoxes = new Box[0];
    private T lastClicked;
    private long lastClickAt;
    private Theme lastTheme;
    private final RowLook.HoverFade hover = new RowLook.HoverFade();

    // A fold in motion. Rows at or after foldStart slide; foldGhost holds the rows just removed.
    private int foldStart = -1;
    private int foldCount;
    private boolean foldOpening;
    private List<Row<T>> foldGhost = List.of();
    private long foldAt;

    public TreeComponent(Box box, Collection<? extends T> roots, Function<T, List<T>> children, Function<T, String> label) {
        super(box);
        this.children = Objects.requireNonNull(children, "children");
        this.label = Objects.requireNonNull(label, "label");
        setRoots(roots);
    }

    /** A theme icon before the label; null for a node shows none. */
    public TreeComponent<T> icon(Function<T, Icon> icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Your own picture before the label, instead of a theme icon; null for a node shows none. An
     * {@link ItemView} is drawn with its count and durability, and its row answers {@link #itemAt} for the
     * recipe keys. Rows grow to 18 px for a 16 px item, unless {@link #rowHeight} was set.
     */
    public TreeComponent<T> iconArt(Function<T, IconArt> iconArt) {
        this.iconArt = iconArt;
        if (!rowHeightSet) {
            rowHeight = iconArt != null ? ItemArt.ITEM_ROW : 12;
            clampScroll();
        }
        return this;
    }

    /** Whether an item row shows the game's own tooltip (its name without a world). On by default; a {@link #rowTooltip} wins. */
    public TreeComponent<T> itemTooltips(boolean itemTooltips) {
        this.itemTooltips = itemTooltips;
        return this;
    }

    /** Replaces the icon and label. The box it gets starts right of the arrow. */
    public TreeComponent<T> renderer(RowRenderer<T> renderer) {
        this.renderer = renderer;
        return this;
    }

    /** Lines for the row under the pointer. Null or empty falls back to {@link #tooltip}. */
    public TreeComponent<T> rowTooltip(Function<T, List<String>> rowTooltip) {
        this.rowTooltip = rowTooltip;
        return this;
    }

    /** Called when the selection changes through a click or a key, not through {@link #select}. */
    public TreeComponent<T> onSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /** Called on a double click, Enter or Space. */
    public TreeComponent<T> onActivate(Consumer<T> onActivate) {
        this.onActivate = onActivate;
        return this;
    }

    /** Called when the player folds or unfolds a node, with its new state. */
    public TreeComponent<T> onToggle(BiConsumer<T, Boolean> onToggle) {
        this.onToggle = onToggle;
        return this;
    }

    /** Text shown when there are no roots. */
    public TreeComponent<T> emptyText(String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    public TreeComponent<T> rowHeight(int rowHeight) {
        this.rowHeight = Math.max(1, rowHeight);
        rowHeightSet = true;
        clampScroll();
        return this;
    }

    /** Pixels per level. */
    public TreeComponent<T> indent(int indent) {
        this.indent = Math.max(0, indent);
        return this;
    }

    /** Whether to draw the vertical lines that mark each level. On by default. */
    public TreeComponent<T> guides(boolean guides) {
        this.guides = guides;
        return this;
    }

    public TreeComponent<T> active(boolean active) {
        this.active = active;
        if (!active) {
            bar.release();
        }
        return this;
    }

    @Override
    public TreeComponent<T> tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** New roots. Expanded nodes, selection and scroll stay, matched by equality. Null means none. */
    public final void setRoots(Collection<? extends T> roots) {
        fits.clear();
        List<T> copy = new ArrayList<>();
        if (roots != null) {
            for (T root : roots) {
                if (root != null) {
                    copy.add(root);
                }
            }
        }
        this.roots = copy;
        rebuild();
    }

    /** Asks {@code children} again, after the data changed under the same roots. */
    public void refresh() {
        rebuild();
    }

    public List<T> roots() {
        return Collections.unmodifiableList(roots);
    }

    public boolean isExpanded(T node) {
        return expanded.contains(node);
    }

    public void expand(T node) {
        if (node != null && expanded.add(node)) {
            rebuild();
        }
    }

    public void collapse(T node) {
        if (expanded.remove(node)) {
            rebuild();
        }
    }

    public void toggle(T node) {
        if (expanded.contains(node)) {
            collapse(node);
        } else {
            expand(node);
        }
    }

    /** Opens every node. This walks the whole tree, so a tree of unlimited depth must not call it. */
    public void expandAll() {
        Set<T> seen = new HashSet<>(roots);
        ArrayDeque<T> todo = new ArrayDeque<>(roots);
        while (!todo.isEmpty()) {
            T node = todo.pop();
            List<T> kids = kidsOf(node);
            if (!kids.isEmpty()) {
                expanded.add(node);
            }
            for (T kid : kids) {
                if (seen.add(kid)) {
                    todo.push(kid);
                }
            }
        }
        rebuild();
    }

    public void collapseAll() {
        expanded.clear();
        rebuild();
    }

    /**
     * Opens the node's ancestors, moves the keyboard cursor to it and scrolls it into view, without
     * changing the selection. Searches from the roots, so this costs up to one walk of the tree.
     *
     * @return false when the node is not in the tree
     */
    public boolean reveal(T node) {
        if (node == null) {
            return false;
        }
        Map<T, T> parents = new HashMap<>();
        for (T root : roots) {
            parents.putIfAbsent(root, null);
        }
        ArrayDeque<T> todo = new ArrayDeque<>(roots);
        boolean found = parents.containsKey(node);
        while (!found && !todo.isEmpty()) {
            T at = todo.pop();
            for (T kid : kidsOf(at)) {
                if (!parents.containsKey(kid)) {
                    parents.put(kid, at);
                    todo.push(kid);
                }
                if (kid.equals(node)) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            return false;
        }
        for (T up = parents.get(node); up != null; up = parents.get(up)) {
            expanded.add(up);
        }
        rebuild();
        // First the row that came from the path just walked: the node may also appear elsewhere.
        for (int i = 0; i < rows.size(); i++) {
            Row<T> row = rows.get(i);
            if (row.node.equals(node) && Objects.equals(row.parent < 0 ? null : rows.get(row.parent).node, parents.get(node))) {
                setCursor(i);
                return true;
            }
        }
        return false;
    }

    public T selected() {
        return selected;
    }

    /** Sets the selection without calling {@link #onSelect}; the cursor follows if the row is showing. */
    public void select(T node) {
        selected = node;
        if (node == null) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).node.equals(node)) {
                setCursor(i);
                return;
            }
        }
    }

    /** The node the keyboard is on, or null when the tree is empty. */
    public T cursor() {
        return cursor < 0 ? null : rows.get(cursor).node;
    }

    /** How many rows are open, in view or not. */
    int rowCount() {
        return rows.size();
    }

    double scroll() {
        return bar.offset();
    }

    public void setScroll(double value) {
        clampScroll();
        bar.offset((int) Math.round(value));
    }

    /**
     * The colour the tree's own text has in a row, for a {@link RowRenderer} that draws over the row's
     * face: read it inside the renderer, since it follows the focus and {@code active}.
     */
    public int cellInk(Theme theme, boolean isSelected, boolean isHovered) {
        Palette p = theme.palette();
        return RowLook.ink(p, RowLook.face(p, active, focused, isSelected, isHovered, hover.fade()), isSelected, active);
    }

    /** The node of the row at a point, or null. */
    public T rowAt(double mx, double my) {
        int i = indexAt(mx, my);
        return i < 0 ? null : rows.get(i).node;
    }

    private List<T> kidsOf(T node) {
        List<T> kids = children.apply(node);
        if (kids == null || kids.isEmpty()) {
            return List.of();
        }
        for (T kid : kids) {
            if (kid == null) {
                List<T> clean = new ArrayList<>(kids);
                clean.removeIf(Objects::isNull);
                return clean;
            }
        }
        return kids;
    }

    private void rebuild() {
        List<T> cursorPath = new ArrayList<>();
        int oldCursor = cursor;
        for (int i = cursor; i >= 0; i = rows.get(i).parent) {
            cursorPath.add(rows.get(i).node);
        }
        rows = flatten();
        foldStart = -1;
        resolveCursor(oldCursor, cursorPath);
        clampScroll();
    }

    private List<Row<T>> flatten() {
        List<Row<T>> out = new ArrayList<>();
        Set<T> path = new HashSet<>();
        ArrayDeque<Level<T>> stack = new ArrayDeque<>();
        stack.push(new Level<>(null, -1, roots.iterator()));
        while (!stack.isEmpty()) {
            Level<T> level = stack.peek();
            if (!level.kids.hasNext()) {
                stack.pop();
                if (level.node != null) {
                    path.remove(level.node);
                }
                continue;
            }
            T node = level.kids.next();
            boolean repeat = path.contains(node);
            List<T> kids = repeat ? List.of() : kidsOf(node);
            boolean open = !kids.isEmpty() && expanded.contains(node);
            int index = out.size();
            out.add(new Row<>(node, stack.size() - 1, level.rowIndex, !kids.isEmpty(), open));
            if (open) {
                path.add(node);
                stack.push(new Level<>(node, index, kids.iterator()));
            }
        }
        return out;
    }

    /** Keeps the cursor on its node, else on its nearest ancestor still showing, else by position. */
    private void resolveCursor(int oldIndex, List<T> path) {
        if (rows.isEmpty()) {
            cursor = -1;
            return;
        }
        if (path.isEmpty()) {
            cursor = 0;
            return;
        }
        if (oldIndex < rows.size() && rows.get(oldIndex).node.equals(path.get(0))) {
            cursor = oldIndex;
            return;
        }
        Map<T, Integer> firstIndex = new HashMap<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            firstIndex.put(rows.get(i).node, i);
        }
        for (T node : path) {
            Integer found = firstIndex.get(node);
            if (found != null) {
                cursor = found;
                return;
            }
        }
        cursor = Math.min(oldIndex, rows.size() - 1);
    }

    private int viewX() { return x + 1; }
    private int viewY() { return y + 1; }
    private int viewH() { return Math.max(0, h - 2); }
    private int contentH() { return (int) Math.min(Integer.MAX_VALUE, (long) rows.size() * rowHeight); }
    private boolean overflows() { return contentH() > viewH(); }
    private int viewW() { return Math.max(0, w - 2 - (overflows() ? ScrollBar.wellRoom(frame) : 0)); }
    private int barX() { return ScrollBar.wellX(frame, x, w); }
    private int trackY() { return viewY() + 1; }
    private int trackH() { return Math.max(1, viewH() - 2); }

    private void clampScroll() {
        bar.content(contentH(), viewH());
    }

    private int rowY(int index) {
        return viewY() + index * rowHeight - bar.offset();
    }

    /** Past half the row, indentation stops growing, so a very deep node keeps room for its label. */
    private int indentOf(int depth) {
        return (int) Math.min((long) depth * indent, Math.max(0, viewW() / 2));
    }

    private int arrowX(int depth) {
        return viewX() + 1 + indentOf(depth);
    }

    private int indexAt(double mx, double my) {
        if (mx < viewX() || mx >= viewX() + viewW() || my < viewY() || my >= viewY() + viewH()) {
            return -1;
        }
        int i = (int) Math.floor((my - viewY() + bar.offset()) / rowHeight);
        return i >= 0 && i < rows.size() ? i : -1;
    }

    private boolean overArrow(Row<T> row, double mx) {
        int ax = arrowX(row.depth);
        return row.expandable && mx >= ax && mx < ax + ARROW;
    }

    private boolean overBar(double mx, double my) {
        return overflows() && mx >= barX() && mx < x + w && my >= y && my < y + h;
    }

    private void setCursor(int index) {
        cursor = index;
        int top = index * rowHeight;
        clampScroll();
        if (top < bar.offset()) {
            bar.offset(top);
        } else if (top + rowHeight > bar.offset() + viewH()) {
            bar.offset(top + rowHeight - viewH());
        }
    }

    private void fold(int index, boolean open) {
        Row<T> row = rows.get(index);
        if (open == row.expanded || !row.expandable) {
            return;
        }
        List<Row<T>> before = rows;
        if (open) {
            expanded.add(row.node);
        } else {
            expanded.remove(row.node);
        }
        rebuild();
        startFold(index, before, open);
        BaseComponent.click();
        if (onToggle != null) {
            onToggle.accept(row.node, open);
        }
    }

    private void startFold(int index, List<Row<T>> before, boolean opening) {
        int count = Math.abs(rows.size() - before.size());
        if (BaseComponent.reducedMotion || count == 0 || (long) count * rowHeight > 2L * viewH()) {
            return;
        }
        foldStart = index + 1;
        foldCount = count;
        foldOpening = opening;
        foldGhost = opening ? List.of() : new ArrayList<>(before.subList(index + 1, index + 1 + count));
        foldAt = BaseComponent.clock.getAsLong();
    }

    /** Pixels of the moving rows still showing; -1 when nothing is moving. */
    private int foldExtent() {
        if (foldStart < 0) {
            return -1;
        }
        long age = BaseComponent.clock.getAsLong() - foldAt;
        if (BaseComponent.reducedMotion || age >= FOLD_MS || foldStart > rows.size()) {
            foldStart = -1;
            foldGhost = List.of();
            return -1;
        }
        float t = 1F - Math.max(0F, (float) age) / FOLD_MS;
        float eased = 1F - t * t * t;
        float shown = foldOpening ? eased : 1F - eased;
        return Math.round(foldCount * rowHeight * shown);
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        lastTheme = theme;
        frame = theme.frame();
        itemTipShown = false;
        Palette p = theme.palette();
        theme.frame().well(d, p, x, y, w, h, focused && active);
        if (rows.isEmpty()) {
            hover.step(-1);
            RowLook.drawEmpty(d, p, emptyText, active, viewX(), viewY(), viewW(), viewH());
            return;
        }
        int vx = viewX();
        int vy = viewY();
        int vw = viewW();
        int vh = viewH();
        int hoverIndex = -1;
        boolean overRowArrow = false;
        int extent = foldExtent();
        if (active && hovered && !bar.dragging() && extent < 0) {
            hoverIndex = indexAt(mx, my);
            overRowArrow = hoverIndex >= 0 && overArrow(rows.get(hoverIndex), mx);
        }
        hover.step(hoverIndex);
        d.scissor(vx, vy, vx + vw, vy + vh);
        if (extent < 0) {
            drawRange(d, theme, 0, rows.size(), 0, hoverIndex, overRowArrow);
        } else {
            drawFolding(d, theme, extent);
        }
        d.endScissor();
        itemTipShown = hoverIndex >= 0 && !overRowArrow && showItemTip(d, rows.get(hoverIndex).node, mx, my);
        if (overflows() && RowLook.barFits(w, h, BAR)) {
            bar.draw(d, theme, barX(), trackY(), trackH(), active && hovered && overBar(mx, my));
            if (!active) {
                RowLook.dimBar(d, p, barX(), trackY(), BAR, trackH());
            }
        }
    }

    private void drawFolding(Draw d, Theme theme, int extent) {
        int vx = viewX();
        int vw = viewW();
        int foldTop = rowY(foldStart);
        drawRange(d, theme, 0, foldStart, 0, -1, false);
        // The moving block is cut at its parent's lower edge, so it slides out from under the parent.
        int clipTop = Math.max(viewY(), foldTop);
        int clipBottom = viewY() + viewH();
        if (clipTop >= clipBottom) {
            return;
        }
        d.scissor(vx, clipTop, vx + vw, clipBottom);
        int full = foldCount * rowHeight;
        if (foldOpening) {
            drawRange(d, theme, foldStart, rows.size(), extent - full, -1, false);
        } else {
            for (int i = 0; i < foldGhost.size(); i++) {
                int ry = foldTop + i * rowHeight + extent - full;
                if (ry + rowHeight > clipTop && ry < clipBottom) {
                    drawRow(d, theme, foldGhost.get(i), foldStart + i, ry, false, false);
                }
            }
            drawRange(d, theme, foldStart, rows.size(), extent, -1, false);
        }
        d.endScissor();
    }

    /** Draws rows from..to that land in view, each moved down by shift pixels. */
    private void drawRange(Draw d, Theme theme, int from, int to, int shift, int hoverIndex, boolean overRowArrow) {
        int top = (viewY() - shift - rowY(0)) / rowHeight;
        int first = Math.max(from, top - 1);
        int last = Math.min(to, top + viewH() / rowHeight + 2);
        for (int i = first; i < last; i++) {
            int ry = rowY(i) + shift;
            if (ry + rowHeight > viewY() && ry < viewY() + viewH()) {
                drawRow(d, theme, rows.get(i), i, ry, i == hoverIndex, i == hoverIndex && overRowArrow);
            }
        }
    }

    private void drawRow(Draw d, Theme theme, Row<T> row, int index, int ry, boolean hovered, boolean arrowHovered) {
        Palette p = theme.palette();
        int vx = viewX();
        int vw = viewW();
        boolean isSelected = selected != null && selected.equals(row.node);
        int face = RowLook.face(p, active, focused, isSelected, hovered, hover.fade());
        theme.frame().row(d, p, vx, ry, vw, rowHeight, face >>> 24 != 0 ? face : 0);
        int ink = RowLook.ink(p, face, isSelected, active);
        int faint = !active || isSelected || hovered ? ink : RowLook.softInk(p, ink);

        int ax = arrowX(row.depth);
        if (guides && indent > 0) {
            int color = Shapes.alpha(ink, 0x40);
            int limit = ax - vx - 1;
            for (int level = 0; level < row.depth; level++) {
                int offset = level * indent;
                if (offset >= limit) {
                    break;
                }
                int gx = vx + 1 + offset + ARROW / 2;
                d.fill(gx, ry, gx + 1, ry + rowHeight, color);
            }
        }
        // A row sliding in a fold is cut by its parent, not by the view: it stays whole.
        if (foldStart < 0 && RowLook.textCut(ry, rowHeight, viewY(), viewH())) {
            return;
        }
        if (row.expandable) {
            IconArt arrow = theme.icon(row.expanded ? Icon.CARET : Icon.NEXT);
            int color = arrowHovered ? ink : faint;
            arrow.draw(d, ax + (ARROW - arrow.width()) / 2, ry + (rowHeight - arrow.height()) / 2, color, 1F);
        }
        int cx = ax + ARROW;
        int right = vx + vw;
        if (renderer != null) {
            renderer.draw(d, theme, row.node, rowBox(index, cx, ry, Math.max(0, right - cx)),
                    row.depth, row.expanded, hovered, isSelected);
            return;
        }
        IconArt art = artOf(theme, row.node);
        if (art != null) {
            cx += drawArt(d, theme.palette(), art, cx, ry, faint);
        }
        cx += ICON_GAP;
        d.text(fits.fit(d, row.node, textOf(row.node), Math.max(0, right - cx - 2)).cut(), cx, RowLook.textY(ry, rowHeight), ink);
    }

    /** The box handed to the renderer, kept per row so a still tree allocates none; a clash only costs a new box. */
    private Box rowBox(int index, int bx, int by, int bw) {
        int room = viewH() / Math.max(1, rowHeight) + 8;
        if (rowBoxes.length < room) {
            rowBoxes = new Box[room];
        }
        int slot = index % rowBoxes.length;
        Box box = rowBoxes[slot];
        if (box == null || box.x() != bx || box.y() != by || box.w() != bw || box.h() != rowHeight) {
            box = new Box("row", bx, by, bw, rowHeight);
            rowBoxes[slot] = box;
        }
        return box;
    }

    /** Draws the art at the row's middle; an item fitted crisply, with its decorations at full size. Returns its width. */
    private int drawArt(Draw d, Palette p, IconArt art, int ax, int ry, int color) {
        if (!ItemArt.isItem(art)) {
            art.draw(d, ax, ry + (rowHeight - art.height()) / 2, color, 1F);
            return art.width();
        }
        int room = artWidth(art);
        int top = ry + (rowHeight - room) / 2;
        ItemArt.fit(d, p, art, ax, top, room, color, active);
        if (room >= art.height()) {
            ItemArt.decorations(d, (ItemView) art, ax, top);
        }
        return room;
    }

    private int artWidth(IconArt art) {
        return ItemArt.isItem(art) ? Math.min(art.height(), rowHeight) : art.width();
    }

    /** The game's tooltip for an item row, when no tooltip of yours is set. */
    private boolean showItemTip(Draw d, T node, int mx, int my) {
        if (!itemTooltips || renderer != null || iconArt == null || hasOwnTip(node)) {
            return false;
        }
        return ItemArt.hoverTooltip(d, iconArt.apply(node), mx, my);
    }

    private boolean hasOwnTip(T node) {
        if (rowTooltip != null) {
            List<String> lines = rowTooltip.apply(node);
            if (lines != null && !lines.isEmpty()) {
                return true;
            }
        }
        return tooltip != null && !tooltip.isEmpty();
    }

    /** The item of the row under the pointer, for the recipe keys of JEI, REI or EMI. */
    @Override
    public ItemView itemAt(double mx, double my) {
        if (!visible || iconArt == null) {
            return null;
        }
        int i = indexAt(mx, my);
        return i >= 0 && iconArt.apply(rows.get(i).node) instanceof ItemView item ? item : null;
    }

    private IconArt artOf(Theme theme, T node) {
        if (iconArt != null) {
            IconArt art = iconArt.apply(node);
            if (art != null) {
                return art;
            }
        }
        if (icon != null) {
            Icon named = icon.apply(node);
            if (named != null) {
                return theme.icon(named);
            }
        }
        return null;
    }

    private String textOf(T node) {
        String text = label.apply(node);
        return text == null ? "" : text;
    }

    @Override
    protected Box focusBox() {
        if (cursorInView()) {
            // The ring grows by 2 on each side: this lays it on the row's edges, 1 px inside the well's border.
            return new Box("row", viewX() + 3, rowY(cursor) + 2, Math.max(0, viewW() - 6), Math.max(0, rowHeight - 4));
        }
        return super.focusBox();
    }

    /** Square on a row, which is square, as in the {@link ReorderListComponent}: the default corner makes a capsule of a row this low. */
    @Override
    protected int focusCorner() {
        return cursorInView() ? 0 : super.focusCorner();
    }

    private boolean cursorInView() {
        if (cursor < 0) {
            return false;
        }
        int ry = rowY(cursor);
        return ry >= viewY() && ry + rowHeight <= viewY() + viewH();
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (visible && active) {
            int i = indexAt(mx, my);
            if (i >= 0) {
                T node = rows.get(i).node;
                if (rowTooltip != null) {
                    List<String> lines = rowTooltip.apply(node);
                    if (lines != null && !lines.isEmpty()) {
                        return lines;
                    }
                }
                List<String> base = super.tooltipAt(mx, my);
                if (base != null && !base.isEmpty()) {
                    return base;
                }
                if (itemTooltips && renderer == null && iconArt != null && iconArt.apply(node) instanceof ItemView item) {
                    return itemTipShown ? List.of() : ItemArt.nameTip(item);
                }
                if (renderer == null && isCut(rows.get(i))) {
                    return List.of(textOf(node));
                }
                return base;
            }
        }
        return super.tooltipAt(mx, my);
    }

    private boolean isCut(Row<T> row) {
        int cx = arrowX(row.depth) + ARROW;
        if (lastTheme != null) {
            IconArt art = artOf(lastTheme, row.node);
            if (art != null) {
                cx += artWidth(art);
            }
        }
        cx += ICON_GAP;
        return TextMeasure.width(textOf(row.node)) > viewX() + viewW() - cx - 2;
    }

    private boolean accepts() {
        return visible && active && settled();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!accepts() || button != 0 || !isOver(mx, my)) {
            return false;
        }
        if (overBar(mx, my)) {
            bar.press(mx, my, barX(), trackY(), trackH());
            return true;
        }
        int i = indexAt(mx, my);
        if (i < 0) {
            return true;
        }
        Row<T> row = rows.get(i);
        if (overArrow(row, mx)) {
            lastClicked = null;
            fold(i, !row.expanded);
            return true;
        }
        long now = BaseComponent.clock.getAsLong();
        boolean twice = lastClicked != null && lastClicked.equals(row.node) && now - lastClickAt <= DOUBLE_CLICK_MS;
        BaseComponent.click();
        setCursor(i);
        choose(row.node);
        if (twice) {
            lastClicked = null;
            fire();
        } else {
            lastClicked = row.node;
            lastClickAt = now;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return bar.drag(my);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = bar.dragging();
        bar.release();
        return was;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || !settled() || !isOver(mx, my) || !overflows()) {
            return false;
        }
        clampScroll();
        bar.wheel(amount, ScrollBar.WHEEL_ROWS * rowHeight);
        return true;
    }

    private void choose(T node) {
        boolean changed = !node.equals(selected);
        selected = node;
        if (changed && onSelect != null) {
            onSelect.accept(node);
        }
    }

    private void fire() {
        if (onActivate != null && cursor >= 0) {
            onActivate.accept(rows.get(cursor).node);
        }
    }

    private void moveTo(int index) {
        int target = Math.max(0, Math.min(rows.size() - 1, index));
        setCursor(target);
        choose(rows.get(target).node);
    }

    @Override
    protected boolean takesFocus() {
        return active;
    }

    @Override
    protected boolean navigable() {
        return visible && active && settled() && !rows.isEmpty();
    }

    @Override
    protected boolean activate() {
        if (!accepts() || cursor < 0) {
            return false;
        }
        BaseComponent.click();
        choose(rows.get(cursor).node);
        fire();
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!accepts() || rows.isEmpty()) {
            return false;
        }
        int at = Math.max(0, cursor);
        Row<T> row = rows.get(at);
        switch (key) {
            case UP -> moveTo(at - 1);
            case DOWN -> moveTo(at + 1);
            case HOME -> moveTo(0);
            case END -> moveTo(rows.size() - 1);
            case RIGHT -> {
                if (row.expanded) {
                    moveTo(at + 1);
                } else if (row.expandable) {
                    fold(at, true);
                }
            }
            case LEFT -> {
                if (row.expanded) {
                    fold(at, false);
                } else if (row.parent >= 0) {
                    moveTo(row.parent);
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
