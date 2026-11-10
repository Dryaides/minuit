package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.FitCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link TableComponent} whose rows fold: one column shows the tree, the other columns are the table's own. The
 * table draws only the rows in view and keeps its keys, selection and bar; this only hands it the open rows.
 *
 * <pre>{@code
 * TreeTableComponent<Task> tasks = add(new TreeTableComponent<Task>(at(new Box("tasks", 8, 8, 280, 120)), 14,
 *         roots, Task::children)
 *         .treeColumn("Name", Task::name)
 *         .column("Owner", c -> c.text(Task::owner).fixed(60)));
 * }</pre>
 *
 * <p>A click on an arrow folds or opens the row. With focus: right opens a row or goes to its first child, left folds
 * it or goes to its parent, and the table's keys walk the open rows. Enter, Space or a double click calls
 * {@link #onActivate(Consumer)}, or folds or opens the row when there is none. A row hidden by a fold hands its
 * selection to the open row above it.
 *
 * <p>Sorting would tear children from their parents, so a sortable column is refused with an exception: sort the
 * lists your {@code children} function returns, then call {@link #refresh()}. Nodes are matched by identity, and
 * a node met twice shows once. Children are asked for only for the rows that are open or showing.
 */
public class TreeTableComponent<T> extends TableComponent<T> {
    private static final int INDENT = 8;
    private static final int ARROW = 9;
    private static final int TEXT_GAP = 1;
    private static final int ICON_GAP = 2;
    private static final String[] CLOSED = {"#..", "##.", "###", "##.", "#.."};
    private static final String[] OPEN = {"#####", ".###.", "..#.."};

    private final FitCache fits = new FitCache();
    private final Function<T, ? extends Collection<? extends T>> children;
    private final List<T> roots = new ArrayList<>();
    private final Map<T, Boolean> expanded = new IdentityHashMap<>();
    private final Map<T, Integer> depth = new IdentityHashMap<>();
    private final Map<T, T> parent = new IdentityHashMap<>();
    private final Map<T, List<T>> kids = new IdentityHashMap<>();
    private Function<T, String> label = String::valueOf;
    private Function<T, IconArt> icon;
    private BiConsumer<T, Boolean> onToggle;
    private Consumer<T> userActivate;
    private int treeLeft = Integer.MIN_VALUE;
    private int treeWidth;
    private boolean originKnown;
    private int rowsOrigin;

    public TreeTableComponent(Box box, int rowHeight, Collection<? extends T> roots, Function<T, ? extends Collection<? extends T>> children) {
        super(box, rowHeight);
        this.children = Objects.requireNonNull(children, "children");
        super.onActivate(item -> {
            if (userActivate != null) {
                userActivate.accept(item);
            } else {
                toggle(item);
            }
        });
        setRoots(roots);
    }

    /** The column with the arrows and the indent, wide by its share. Call this once. */
    public TreeTableComponent<T> treeColumn(String title, Function<T, String> label) {
        return treeColumn(title, label, 2F);
    }

    /** The column with the arrows and the indent, with its share of the width. */
    public TreeTableComponent<T> treeColumn(String title, Function<T, String> label, float weight) {
        return treeColumn(title, label, null, weight);
    }

    /**
     * The column with the arrows, then a picture before the label; null for a row shows none. An {@link ItemView}
     * gets its count, the game's tooltip and the recipe keys: build the table with rows of {@link #ITEM_ROW}.
     */
    public TreeTableComponent<T> treeColumn(String title, Function<T, String> label, Function<T, IconArt> icon) {
        return treeColumn(title, label, icon, 2F);
    }

    /** The same, with the column's share of the width. */
    public TreeTableComponent<T> treeColumn(String title, Function<T, String> label, Function<T, IconArt> icon, float weight) {
        this.label = Objects.requireNonNull(label, "label");
        this.icon = icon;
        super.column(title, c -> c.weight(weight).renderer(this::drawTreeCell).iconArt(icon == null ? null : icon::apply));
        return this;
    }

    /** A plain column of the table. A comparator on it is refused: see the class's note. */
    @Override
    public TreeTableComponent<T> column(String title, Consumer<Column<T>> setup) {
        super.column(title, setup);
        for (Column<T> column : columns()) {
            if (column.sortable()) {
                throw new IllegalArgumentException("a tree table cannot sort its rows: column " + column.title());
            }
        }
        return this;
    }

    @Override
    public TreeTableComponent<T> textColumn(String title, Function<? super T, String> getter) {
        super.textColumn(title, getter);
        return this;
    }

    @Override
    public TreeTableComponent<T> headerHeight(int height) {
        super.headerHeight(height);
        return this;
    }

    @Override
    public TreeTableComponent<T> emptyText(String text) {
        super.emptyText(text);
        return this;
    }

    @Override
    public TreeTableComponent<T> onSelect(Consumer<T> callback) {
        super.onSelect(callback);
        return this;
    }

    /** Called on Enter, Space or a double click, in place of folding the row. */
    @Override
    public TreeTableComponent<T> onActivate(Consumer<T> callback) {
        userActivate = callback;
        return this;
    }

    @Override
    public TreeTableComponent<T> active(boolean on) {
        super.active(on);
        return this;
    }

    /** Called with the row and whether it is now open, when the player folds or opens it. */
    public TreeTableComponent<T> onToggle(BiConsumer<T, Boolean> onToggle) {
        this.onToggle = onToggle;
        return this;
    }

    /** New top rows. What is open stays open. */
    public final void setRoots(Collection<? extends T> newRoots) {
        fits.clear();
        roots.clear();
        if (newRoots != null) {
            for (T root : newRoots) {
                if (root != null) {
                    roots.add(root);
                }
            }
        }
        refresh();
    }

    /** Asks for the children again, after they changed. */
    public void refresh() {
        T was = selected();
        Map<T, T> oldParent = new IdentityHashMap<>(parent);
        depth.clear();
        parent.clear();
        kids.clear();
        List<T> flat = new ArrayList<>();
        for (T root : roots) {
            place(root, null, 0, flat);
        }
        setItems(flat);
        if (was != null && !depth.containsKey(was)) {
            T up = oldParent.get(was);
            while (up != null && !depth.containsKey(up)) {
                up = oldParent.get(up);
            }
            if (up != null) {
                select(up);
            }
        }
    }

    private void place(T node, T above, int level, List<T> flat) {
        if (depth.containsKey(node)) {
            return;
        }
        depth.put(node, level);
        if (above != null) {
            parent.put(node, above);
        }
        flat.add(node);
        Collection<? extends T> asked = children.apply(node);
        List<T> list = new ArrayList<>();
        if (asked != null) {
            for (T child : asked) {
                if (child != null) {
                    list.add(child);
                }
            }
        }
        kids.put(node, list);
        if (expanded.containsKey(node)) {
            for (T child : list) {
                place(child, node, level + 1, flat);
            }
        }
    }

    public boolean isExpanded(T node) {
        return expanded.containsKey(node);
    }

    /** Opens a row, without calling back. */
    public void expand(T node) {
        if (node != null && expanded.put(node, Boolean.TRUE) == null) {
            refresh();
        }
    }

    /** Folds a row, without calling back. */
    public void collapse(T node) {
        if (expanded.remove(node) != null) {
            refresh();
        }
    }

    /** Opens every row, however deep. */
    public void expandAll() {
        List<T> open = new ArrayList<>(roots);
        Map<T, Boolean> seen = new IdentityHashMap<>();
        while (!open.isEmpty()) {
            T node = open.remove(open.size() - 1);
            if (seen.put(node, Boolean.TRUE) != null) {
                continue;
            }
            expanded.put(node, Boolean.TRUE);
            Collection<? extends T> asked = children.apply(node);
            if (asked != null) {
                for (T child : asked) {
                    if (child != null) {
                        open.add(child);
                    }
                }
            }
        }
        refresh();
    }

    public void collapseAll() {
        expanded.clear();
        refresh();
    }

    /** How deep a showing row is, from 0, or -1 when it is not showing. */
    public int depthOf(T node) {
        Integer level = depth.get(node);
        return level == null ? -1 : level;
    }

    public boolean hasChildren(T node) {
        List<T> list = kids.get(node);
        return list != null && !list.isEmpty();
    }

    /** Where a showing row's arrow is, for a script or a test. Empty before the first frame. */
    Box arrowBox(int index) {
        if (treeLeft == Integer.MIN_VALUE || index < 0 || index >= view().size()) {
            return new Box("arrow", 0, 0, 0, 0);
        }
        int level = depthOf(view().get(index));
        int top = rowsOrigin + index * rowHeight() - (int) scroll();
        return new Box("arrow", treeLeft + level * INDENT, top, ARROW, rowHeight());
    }

    private void toggleByPlayer(T node) {
        BaseComponent.click();
        toggle(node);
    }

    private void toggle(T node) {
        if (!hasChildren(node)) {
            return;
        }
        boolean open = !isExpanded(node);
        if (open) {
            expand(node);
        } else {
            collapse(node);
        }
        if (onToggle != null) {
            onToggle.accept(node, open);
        }
    }

    private void drawTreeCell(Draw d, Theme theme, T item, Box cell, boolean hovered, boolean selected) {
        treeLeft = cell.x();
        treeWidth = cell.w();
        if (!originKnown) {
            // Where row 0 would be: the table keeps its header's height to itself.
            originKnown = true;
            rowsOrigin = cell.y() - view().indexOf(item) * rowHeight() + (int) scroll();
        }
        int ink = cellInk(theme, selected, hovered);
        int level = depthOf(item);
        int left = cell.x() + level * INDENT;
        if (hasChildren(item)) {
            boolean open = isExpanded(item);
            String[] art = open ? OPEN : CLOSED;
            int artW = art[0].length();
            DataLook.art(d, art, left + (ARROW - artW) / 2, cell.y() + (cell.h() - art.length) / 2, ink);
        }
        int textX = left + ARROW + TEXT_GAP;
        IconArt art = icon == null ? null : icon.apply(item);
        if (art != null) {
            textX += drawArt(d, theme.palette(), art, textX, cell.y(), ink) + ICON_GAP;
        }
        d.text(fits.fit(d, item, textOf(item), Math.max(0, cell.right() - textX - 2)).cut(), textX,
                RowLook.textY(cell.y(), cell.h()), ink);
    }

    private String textOf(T item) {
        String text = label.apply(item);
        return text == null ? "" : text;
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        T item = visible && active ? rowAt(mx, my) : null;
        if (item != null && treeLeft != Integer.MIN_VALUE && mx >= treeLeft && mx < treeLeft + treeWidth) {
            IconArt art = icon == null ? null : icon.apply(item);
            if (art instanceof ItemView) {
                return super.tooltipAt(mx, my); // the item's own tooltip
            }
            int textX = treeLeft + depthOf(item) * INDENT + ARROW + TEXT_GAP + (art == null ? 0 : artWidth(art) + ICON_GAP);
            if (TextMeasure.width(textOf(item)) > treeLeft + treeWidth - textX - 2) {
                return List.of(textOf(item));
            }
        }
        return super.tooltipAt(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (active && visible && button == 0 && isOver(mx, my) && treeLeft != Integer.MIN_VALUE) {
            T item = rowAt(mx, my);
            if (item != null && hasChildren(item)) {
                int arrowX = treeLeft + depthOf(item) * INDENT;
                if (mx >= arrowX && mx < arrowX + ARROW) {
                    toggleByPlayer(item);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        T at = selected();
        if (active && at != null) {
            if (key == InputKey.RIGHT) {
                if (hasChildren(at)) {
                    if (!isExpanded(at)) {
                        toggleByPlayer(at);
                    } else {
                        selectByPlayer(kids.get(at).get(0));
                    }
                }
                return true;
            }
            if (key == InputKey.LEFT) {
                if (isExpanded(at) && hasChildren(at)) {
                    toggleByPlayer(at);
                } else if (parent.get(at) != null) {
                    selectByPlayer(parent.get(at));
                }
                return true;
            }
        }
        return super.keyPressed(key, shift);
    }
}
