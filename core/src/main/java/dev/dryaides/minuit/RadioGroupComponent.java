package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * One choice out of a few, all in view. {@link ToggleGroupComponent} is the same choice as a row of buttons;
 * this one reads better with long labels or in a column.
 *
 * <pre>{@code
 * add(new RadioGroupComponent(at(new Box("mode", 10, 10, 160, 42)), List.of("Peaceful", "Normal", "Hard"), 1, this::setMode)
 *         .disabled(2, true, "Unlock it first"));
 * }</pre>
 *
 * <p>The group is one keyboard stop, as in any radio group: Tab comes in on the chosen row, the arrows move
 * the choice and wrap, Home and End jump, Space or Enter chooses the row the keys are on when nothing is
 * chosen yet. Rows that do not fit whole are not drawn, so {@link #contentHeight()} tells the box you need.
 */
public class RadioGroupComponent extends BaseComponent {
    /** Height of a row by default. {@code 14 * rows} fits the rows exactly. */
    public static final int ROW = 14;

    private static final int SIDE = 9;
    private static final int LABEL_GAP = 4;
    /** Between two options on a line of a horizontal group. */
    private static final int COLUMN_GAP = 10;

    private final IntConsumer onChange;
    private List<String> options = List.of();
    private boolean[] off = new boolean[0];
    private List<List<String>> offTips = new ArrayList<>();
    private boolean horizontal;
    private int rowH = ROW;
    private int selected;
    private int cursor;

    private boolean dirty = true;
    private int laidW = -1;
    private int laidH = -1;
    private int[] left = new int[0];
    private int[] top = new int[0];
    private int[] width = new int[0];
    private boolean[] shows = new boolean[0];
    private int lines;

    /**
     * @param box the group
     * @param options what to choose from; may be empty
     * @param selected the row chosen at the start, or -1 for none
     * @param onChange called with the row when the player changes the choice; may be null
     */
    public RadioGroupComponent(Box box, Collection<String> options, int selected, IntConsumer onChange) {
        super(box);
        this.onChange = onChange;
        setOptions(options);
        select(selected);
        cursor = Math.max(0, this.selected);
    }

    // ---- setup ----

    /** New options. The choice is kept by row, or dropped when the row is gone. */
    public final void setOptions(Collection<String> options) {
        List<String> copy = new ArrayList<>();
        if (options != null) {
            for (String option : options) {
                copy.add(option == null ? "" : option);
            }
        }
        this.options = copy;
        off = new boolean[copy.size()];
        offTips = new ArrayList<>();
        for (int i = 0; i < copy.size(); i++) {
            offTips.add(null);
        }
        left = new int[copy.size()];
        top = new int[copy.size()];
        width = new int[copy.size()];
        shows = new boolean[copy.size()];
        if (selected >= copy.size()) {
            selected = -1;
        }
        cursor = Math.max(0, Math.min(cursor, copy.size() - 1));
        dirty = true;
    }

    /** Options side by side, wrapping onto new lines, in place of a column. */
    public RadioGroupComponent horizontal(boolean horizontal) {
        this.horizontal = horizontal;
        dirty = true;
        return this;
    }

    /** Height of a row. 14 by default; never less than the radio itself. */
    public RadioGroupComponent rowHeight(int rowHeight) {
        rowH = Math.max(SIDE, rowHeight);
        dirty = true;
        return this;
    }

    /** Takes one row out of the choice, with lines that say why. A disabled row may stay the choice. */
    public RadioGroupComponent disabled(int row, boolean disabled, String... why) {
        if (row >= 0 && row < off.length) {
            off[row] = disabled;
            offTips.set(row, disabled && why != null && why.length > 0 ? List.of(why) : null);
        }
        return this;
    }

    public RadioGroupComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public RadioGroupComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- content ----

    public int selected() {
        return selected;
    }

    /** Sets the choice, without calling back. -1 or a row that does not exist means none. */
    public final void select(int row) {
        selected = row >= 0 && row < options.size() ? row : -1;
        if (selected >= 0) {
            cursor = selected;
        }
    }

    public int count() {
        return options.size();
    }

    public boolean isDisabled(int row) {
        return off[row];
    }

    /** The row the keys are on. */
    int cursor() {
        return cursor;
    }

    /** The height that shows every row at this width. */
    public int contentHeight() {
        layout();
        return lines * rowH;
    }

    /** Where a row is, for a script or a test. */
    Box rowBox(int row) {
        layout();
        return new Box("row" + row, left[row], top[row], width[row], rowH);
    }

    // ---- layout ----

    private void layout() {
        if (!dirty && laidW == w && laidH == h) {
            return;
        }
        dirty = false;
        laidW = w;
        laidH = h;
        Arrays.fill(shows, false);
        int line = 0;
        int at = 0;
        int fits = rowH <= 0 ? 0 : h / rowH;
        for (int i = 0; i < options.size(); i++) {
            int natural = SIDE + LABEL_GAP + TextMeasure.width(options.get(i));
            if (horizontal && at > 0 && at + natural > w) {
                line++;
                at = 0;
            }
            int room = horizontal ? Math.min(natural, w - at) : w;
            left[i] = x + at;
            top[i] = y + line * rowH;
            width[i] = Math.max(0, room);
            shows[i] = line < fits && room >= SIDE;
            if (horizontal) {
                at += natural + COLUMN_GAP;
            } else {
                line++;
            }
        }
        lines = options.isEmpty() ? 0 : horizontal ? line + 1 : line;
    }

    /** A box's place can change between frames. */
    private void relayoutIfMoved() {
        if (!options.isEmpty() && (left[0] != x || top[0] != y)) {
            dirty = true;
        }
        layout();
    }

    private int rowAt(double mx, double my) {
        relayoutIfMoved();
        for (int i = 0; i < options.size(); i++) {
            if (shows[i] && mx >= left[i] && mx < left[i] + width[i] && my >= top[i] && my < top[i] + rowH) {
                return i;
            }
        }
        return -1;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        relayoutIfMoved();
        Palette p = t.palette();
        int hover = hovered && active ? rowAt(mx, my) : -1;
        for (int i = 0; i < options.size(); i++) {
            if (shows[i]) {
                drawRow(d, t.frame(), p, i, i == hover);
            }
        }
    }

    private void drawRow(Draw d, FrameStyle frame, Palette p, int row, boolean hover) {
        boolean live = active && !off[row];
        boolean chosen = row == selected;
        int cx = left[row];
        int cy = top[row] + (rowH - SIDE) / 2;
        frame.radio(d, p, cx, cy, SIDE, chosen, hover, live);
        int textLeft = cx + SIDE + LABEL_GAP;
        int room = cx + width[row] - textLeft;
        if (room > 0) {
            d.textFit(options.get(row), textLeft, RowLook.textY(top[row], rowH), room, live ? p.text() : p.disabledText());
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        int row = rowAt(mx, my);
        if (row >= 0) {
            if (off[row] && offTips.get(row) != null) {
                return offTips.get(row);
            }
            if (tooltip != null) {
                return tooltip;
            }
            int room = width[row] - SIDE - LABEL_GAP;
            return TextMeasure.width(options.get(row)) > room ? List.of(options.get(row)) : null;
        }
        return tooltip;
    }

    // ---- input ----

    private boolean canChoose(int row) {
        return row >= 0 && row < options.size() && !off[row] && shows[row];
    }

    private boolean anyLive() {
        layout();
        for (int i = 0; i < options.size(); i++) {
            if (canChoose(i)) {
                return true;
            }
        }
        return false;
    }

    private void choose(int row) {
        cursor = row;
        if (row != selected) {
            BaseComponent.click();
            selected = row;
            if (onChange != null) {
                onChange.accept(row);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !active || button != 0 || !isOver(mx, my)) {
            return false;
        }
        int row = rowAt(mx, my);
        if (row < 0) {
            return false;
        }
        if (canChoose(row)) {
            choose(row);
        }
        return true;
    }

    @Override
    protected boolean navigable() {
        return visible && active && settled() && anyLive();
    }

    @Override
    protected void enter(boolean forward) {
        layout();
        if (canChoose(selected)) {
            cursor = selected;
        } else if (!canChoose(cursor)) {
            cursor = next(forward ? -1 : options.size(), forward ? 1 : -1);
        }
    }

    /** The next row that can be chosen after {@code from}, going by {@code by} and wrapping. */
    private int next(int from, int by) {
        int count = options.size();
        for (int i = 1; i <= count; i++) {
            int row = Math.floorMod(from + by * i, count);
            if (canChoose(row)) {
                return row;
            }
        }
        return -1;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!visible || !active || options.isEmpty()) {
            return false;
        }
        layout();
        int target;
        switch (key) {
            case DOWN, RIGHT -> target = next(cursor, 1);
            case UP, LEFT -> target = next(cursor, -1);
            case HOME -> target = next(-1, 1);
            case END -> target = next(options.size(), -1);
            default -> {
                return false;
            }
        }
        if (target >= 0) {
            choose(target);
        }
        return true;
    }

    @Override
    protected boolean activate() {
        layout();
        if (!canChoose(cursor)) {
            return false;
        }
        choose(cursor);
        return true;
    }

    @Override
    protected Box focusBox() {
        layout();
        if (cursor >= 0 && cursor < options.size() && shows[cursor]) {
            // The ring grows by 2 on each side: it stays on the row's own height, off the neighbours' letters.
            return new Box("row", left[cursor], top[cursor] + 1, width[cursor], Math.max(0, rowH - 2));
        }
        return super.focusBox();
    }

    @Override
    protected int focusCorner() {
        return 0;
    }
}
