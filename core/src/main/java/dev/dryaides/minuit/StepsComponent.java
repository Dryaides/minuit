package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Where a wizard has got to: a row or a column of numbered markers joined by a line.
 *
 * <pre>{@code
 * StepsComponent steps = add(new StepsComponent(at(new Box("steps", 10, 10, 280, 30)),
 *         List.of("Pick a world", "Choose mods", "Confirm"))
 *         .current(1)
 *         .onStep(this::showPage));
 * steps.error(1, words.get("mymod.conflict"));
 * }</pre>
 *
 * <p>Steps before {@link #current()} are done, and {@link #current(int)} with the step count marks all done. A step
 * with an {@link #error(int, String)} shows it whatever its place, and the message is its tooltip.
 *
 * <p>Steps are clickable once {@link #onStep(IntConsumer)} is set, up to the furthest one reached;
 * {@link #allow(IntPredicate)} sets your own rule, and {@link #current(int)} extends what was reached. The click moves
 * the current step and then calls back; the setter does not call back, and a click on the current step does nothing.
 * With focus: the arrows walk the clickable steps and Enter or Space goes to one.
 *
 * <p>A row too narrow for its labels shows only the current one, a narrower row has small dots, and a row with no room
 * for dots says {@code 2/12} and the label. A column too short scrolls by whole steps with the wheel, and the line
 * running off the end says there is more. A frame allocates nothing: words are cut when the size or the labels change.
 */
public class StepsComponent extends BaseComponent {
    /** What a step shows. */
    public enum State { DONE, CURRENT, ERROR, UPCOMING }

    /** Width and height of a marker. */
    public static final int MARKER = 13;

    private static final int DOT = 5;
    private static final int RING_ROOM = 2;
    private static final int LINE_GAP = 2;
    /** One pixel: an even line cannot sit in the middle of an odd marker. */
    private static final int LINE_W = 1;
    private static final int LINE_REST = 0x50;
    private static final int LABEL_GAP = 3;
    private static final int LINE_H = 10;
    private static final int MIN_LABEL_CELL = 26;
    private static final int MAX_CORNER = 6;
    private static final int REST_EDGE = 0x70;
    private static final int TEXT_SIDE = 5;
    private static final int ROW_PLAIN = 21;
    private static final int ROW_DESC = 28;
    private static final int BAR = 6;
    private static final String[] CHECK = {
            "......#",
            ".....##",
            "#...##.",
            "##.##..",
            ".###...",
            "..#...."};
    private static final String[] BANG = {
            "###",
            "###",
            "###",
            ".#.",
            "...",
            "###",
            "###"};

    private final List<String> labels = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> descriptions = new ArrayList<>();
    private final RowLook.HoverFade hover = new RowLook.HoverFade();

    private int current;
    private int reached;
    private boolean vertical;
    private int rowHeight;
    private IntPredicate allow;
    private IntConsumer onStep;

    private boolean dirty = true;
    private int laidW = -1;
    private int laidH = -1;
    private String[] numbers = new String[0];
    private String[] fitLabels = new String[0];
    private String[] fitDescs = new String[0];
    private boolean showLabels;
    private boolean dots;
    private boolean textOnly;
    private String summary = "";
    private String currentFit = "";
    private int rowsFit;
    private final ScrollBar scroll = new ScrollBar();
    private FrameStyle frame = FrameStyles.VANILLA;
    private boolean bar;

    private int cursor = -1;
    private int held = -1;
    private int tipFor = -2;
    private List<String> tip;

    public StepsComponent(Box box, List<String> labels) {
        super(box);
        setLabels(labels);
    }

    // ---- setup ----

    /** True for a column of steps. A row by default. */
    public StepsComponent vertical(boolean vertical) {
        this.vertical = vertical;
        dirty = true;
        return this;
    }

    /** The height of a step in a column. By default enough for its words. */
    public StepsComponent rowHeight(int rowHeight) {
        this.rowHeight = Math.max(0, rowHeight);
        dirty = true;
        return this;
    }

    /** A line of smaller words under each label, in a column. Null or a short list leaves steps without one. */
    public StepsComponent descriptions(List<String> texts) {
        descriptions.clear();
        if (texts != null) {
            for (String text : texts) {
                descriptions.add(text == null ? "" : text);
            }
        }
        dirty = true;
        return this;
    }

    /** Which steps a click or the keys may go to. By default those reached so far, once {@link #onStep} is set. */
    public StepsComponent allow(IntPredicate allow) {
        this.allow = allow;
        return this;
    }

    /** Called with the step the player went to. Not through {@link #current(int)}. */
    public StepsComponent onStep(IntConsumer onStep) {
        this.onStep = onStep;
        return this;
    }

    public StepsComponent active(boolean active) {
        this.active = active;
        if (!active) {
            held = -1;
        }
        return this;
    }

    @Override
    public StepsComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- content ----

    /** New labels. Errors are forgotten and the current step is kept when it still exists. */
    public final void setLabels(List<String> names) {
        labels.clear();
        errors.clear();
        if (names != null) {
            for (String name : names) {
                labels.add(name == null ? "" : name);
                errors.add(null);
            }
        }
        current = Math.max(0, Math.min(labels.size(), current));
        reached = Math.max(0, Math.min(labels.size(), reached));
        cursor = -1;
        dirty = true;
        tipFor = -2;
    }

    /** Sets the current step, from 0; the step count means all are done. Does not call back. */
    public StepsComponent current(int step) {
        current = Math.max(0, Math.min(labels.size(), step));
        reached = Math.max(reached, current);
        cursor = -1;
        reveal(current);
        dirty = true;
        return this;
    }

    public int current() {
        return current;
    }

    /** The furthest step reached so far. */
    public int reached() {
        return reached;
    }

    /** Marks a step as failed, with what is wrong as its tooltip; null clears it. An empty message has no tooltip. */
    public StepsComponent error(int step, String message) {
        if (step >= 0 && step < errors.size()) {
            errors.set(step, message);
            tipFor = -2;
        }
        return this;
    }

    public int stepCount() {
        return labels.size();
    }

    public State state(int step) {
        if (errors.get(step) != null) {
            return State.ERROR;
        }
        return step < current ? State.DONE : step == current ? State.CURRENT : State.UPCOMING;
    }

    /** True when a click or the keys may go to this step. */
    public boolean canGo(int step) {
        if (step < 0 || step >= labels.size() || step == current) {
            return false;
        }
        return allow != null ? allow.test(step) : onStep != null && step <= reached;
    }

    /** The first step in view in a column. */
    public int firstShown() {
        refresh();
        return scroll.offset();
    }

    /** How many steps a column has room for. */
    int rowsShown() {
        refresh();
        return rowsFit;
    }

    /** The step the keys are on, or -1. */
    int cursor() {
        return cursor;
    }

    /** True when every label has its place. */
    public boolean labelsShow() {
        refresh();
        return showLabels;
    }

    /** True when the row has only small dots or less. */
    public boolean compact() {
        refresh();
        return dots || textOnly;
    }

    /** True when the row says "2/12" in place of markers. */
    public boolean textOnly() {
        refresh();
        return textOnly;
    }

    // ---- geometry ----

    private int count() {
        return labels.size();
    }

    private int cellLeft(int step) {
        return x + step * w / Math.max(1, count());
    }

    private int cellRight(int step) {
        return x + (step + 1) * w / Math.max(1, count());
    }

    private int markerSize() {
        return dots ? DOT : MARKER;
    }

    private int rowTop() {
        return y + RING_ROOM;
    }

    /** The left of a marker. */
    public int markerX(int step) {
        refresh();
        if (vertical) {
            return x + RING_ROOM;
        }
        return (cellLeft(step) + cellRight(step) - markerSize()) / 2;
    }

    /** The top of a marker. */
    public int markerY(int step) {
        refresh();
        if (vertical) {
            return y + RING_ROOM + (step - scroll.offset()) * stepH();
        }
        if (!dots) {
            return rowTop();
        }
        return tallRow() ? rowTop() + (MARKER - DOT) / 2 : y + Math.max(0, (h - DOT) / 2);
    }

    private boolean tallRow() {
        return h >= MARKER + RING_ROOM + LABEL_GAP + LINE_H - 1;
    }

    private int stepH() {
        if (rowHeight > 0) {
            return rowHeight;
        }
        return descriptions.isEmpty() ? ROW_PLAIN : ROW_DESC;
    }

    private int barX() {
        return ScrollBar.x(frame, x + w);
    }

    private int columnTextW() {
        return Math.max(0, w - RING_ROOM - MARKER - TEXT_SIDE - (bar ? BAR + 2 : 0));
    }

    private void refresh() {
        if (!dirty && laidW == w && laidH == h) {
            return;
        }
        dirty = false;
        laidW = w;
        laidH = h;
        int n = count();
        if (numbers.length != n) {
            numbers = new String[n];
            fitLabels = new String[n];
            fitDescs = new String[n];
            for (int i = 0; i < n; i++) {
                numbers[i] = Integer.toString(i + 1);
            }
        }
        Draw measure = TextMeasure.draw();
        showLabels = false;
        dots = false;
        textOnly = false;
        bar = false;
        if (vertical) {
            rowsFit = Math.max(0, (h - 2 * RING_ROOM + stepH() - MARKER) / stepH());
            bar = rowsFit < n && w >= MARKER + BAR + 20;
            scroll.content(n, rowsFit);
            showLabels = columnTextW() >= 10;
            for (int i = 0; i < n; i++) {
                fitLabels[i] = measure.ellipsize(labels.get(i), columnTextW());
                fitDescs[i] = i < descriptions.size() ? measure.ellipsize(descriptions.get(i), columnTextW()) : "";
            }
            return;
        }
        rowsFit = n;
        scroll.content(n, n);
        int cell = n == 0 ? w : w / n;
        boolean tall = tallRow();
        if (cell >= MARKER + LINE_GAP + LINE_GAP && h >= MARKER + 2) {
            showLabels = tall && cell >= MIN_LABEL_CELL;
        } else if (cell >= DOT + 2 && h >= DOT) {
            dots = true;
        } else {
            textOnly = true;
        }
        for (int i = 0; i < n; i++) {
            fitLabels[i] = measure.ellipsize(labels.get(i), Math.max(0, cellRight(i) - cellLeft(i) - 2));
            fitDescs[i] = "";
        }
        if (textOnly && n > 0) {
            int shown = Math.min(n, current + 1);
            String name = current < n ? labels.get(current) : "";
            // Two dots on their own would say nothing: below a count's width the box stays empty.
            summary = w < TextMeasure.width(shown + "/" + n) ? "" : measure.ellipsize(shown + "/" + n + (name.isEmpty() ? "" : " " + name), w);
        }
        currentFit = current < n ? measure.ellipsize(labels.get(current), w) : "";
    }

    private void reveal(int step) {
        refresh();
        if (!vertical || rowsFit <= 0) {
            return;
        }
        int target = Math.min(step, count() - 1);
        if (target < scroll.offset()) {
            scroll.offset(target);
        } else if (target >= scroll.offset() + rowsFit) {
            scroll.offset(target - rowsFit + 1);
        }
    }

    /** The step under a point, or -1. */
    int stepAt(double mx, double my) {
        refresh();
        if (!isOver(mx, my) || count() == 0 || textOnly) {
            return -1;
        }
        if (vertical) {
            if (bar && mx >= barX()) {
                return -1;
            }
            int first = scroll.offset();
            int row = first + (int) Math.floor((my - y - RING_ROOM) / stepH());
            return row >= first && row < first + rowsFit && row < count() ? row : -1;
        }
        int column = (int) Math.floor((mx - x) * count() / w);
        return column >= 0 && column < count() ? column : -1;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        frame = t.frame();
        refresh();
        Palette p = t.palette();
        int over = active && hovered && settled() ? stepAt(mx, my) : -1;
        if (!canGo(over)) {
            over = -1;
        }
        hover.step(over);
        if (count() == 0 || w <= 0 || h <= 0) {
            return;
        }
        if (textOnly) {
            d.text(summary, x, y + Math.max(0, (h - RowLook.FONT_H) / 2), (active ? Ink.on(p, p.surface()) : p.disabledText()));
            return;
        }
        if (vertical) {
            renderColumn(d, t, over, hovered && mx >= barX());
        } else {
            renderRow(d, t, over);
        }
    }

    private void renderRow(Draw d, Theme t, int over) {
        Palette p = t.palette();
        int size = markerSize();
        int lineY = markerY(0) + (size - LINE_W) / 2;
        for (int i = 0; i + 1 < count(); i++) {
            int from = markerX(i) + size + LINE_GAP;
            int to = markerX(i + 1) - LINE_GAP;
            if (to > from) {
                d.fill(from, lineY, to, lineY + LINE_W, lineInk(p, i));
            }
        }
        for (int i = 0; i < count(); i++) {
            drawMarker(d, t, i, markerX(i), markerY(i), i == over);
        }
        if (!tallRow()) {
            return;
        }
        int labelY = rowTop() + MARKER + LABEL_GAP;
        if (showLabels) {
            for (int i = 0; i < count(); i++) {
                int width = d.width(fitLabels[i]);
                d.text(fitLabels[i], (cellLeft(i) + cellRight(i) - width) / 2, labelY, labelInk(p, i, i == over));
            }
        } else if (current < count()) {
            int width = d.width(currentFit);
            int centre = (cellLeft(current) + cellRight(current)) / 2;
            int left = Math.max(x, Math.min(x + w - width, centre - width / 2));
            d.text(currentFit, left, labelY, labelInk(p, current, false));
        }
    }

    private void renderColumn(Draw d, Theme t, int over, boolean overBar) {
        Palette p = t.palette();
        int stepH = stepH();
        int lineX = x + RING_ROOM + (MARKER - LINE_W) / 2;
        int textX = x + RING_ROOM + MARKER + TEXT_SIDE;
        int first = scroll.offset();
        int end = Math.min(count(), first + rowsFit);
        for (int i = first; i < end; i++) {
            int top = markerY(i);
            if (i + 1 < count()) {
                int from = top + MARKER + LINE_GAP;
                int to = i + 1 < end ? top + stepH - LINE_GAP : Math.min(y + h, top + stepH - LINE_GAP);
                if (to > from) {
                    d.fill(lineX, from, lineX + LINE_W, to, lineInk(p, i));
                }
            }
            if (i == over) {
                d.fill(textX - 2, top, x + w - (bar ? ScrollBar.room(frame) : 0), top + MARKER, RowLook.tint(p, hover.fade()));
            }
            drawMarker(d, t, i, x + RING_ROOM, top, i == over);
            if (showLabels) {
                d.text(fitLabels[i], textX, top + (MARKER - RowLook.FONT_H) / 2 + 1, labelInk(p, i, i == over));
                if (!fitDescs[i].isEmpty() && stepH >= MARKER + LINE_H && top + MARKER + LINE_H <= y + h) {
                    d.text(fitDescs[i], textX, top + MARKER + 1, (active ? Ink.soft(p, p.surface()) : p.disabledText()));
                }
            }
        }
        if (bar && rowsFit > 0) {
            int barX = barX();
            int trackH = Math.max(1, h - 2);
            scroll.draw(d, t, barX, y + 1, trackH, active && overBar);
            if (!active) {
                RowLook.dimBar(d, p, barX, y + 1, BAR, trackH);
            }
        }
    }

    private int lineInk(Palette p, int step) {
        if (!active) {
            return Shapes.alpha(DataLook.opaque(p.disabledText()), REST_EDGE);
        }
        if (step < current && errors.get(step) == null) {
            return Ink.readable(p, p.markInk(), p.surface(), Ink.MARK);
        }
        return Shapes.alpha(DataLook.opaque(Ink.on(p, p.surface())), LINE_REST);
    }

    private int labelInk(Palette p, int step, boolean over) {
        if (!active) {
            return p.disabledText();
        }
        State state = state(step);
        if (state == State.ERROR) {
            return Ink.readable(p, p.bad(), p.surface(), Ink.MARK);
        }
        return state == State.CURRENT || over ? Ink.on(p, p.surface()) : Ink.soft(p, p.surface());
    }

    private void drawMarker(Draw d, Theme t, int step, int mx, int my, boolean over) {
        Palette p = t.palette();
        int size = markerSize();
        FrameStyle frame = t.frame();
        State state = state(step);
        boolean pressed = held == step && over;
        if (!active) {
            int edge = p.disabledText();
            if (state == State.DONE || state == State.ERROR) {
                frame.plate(d, p, mx, my, size, size, Shapes.alpha(DataLook.opaque(edge), 0x50), 0);
            }
            frame.plate(d, p, mx, my, size, size, 0, edge);
            drawGlyph(d, step, state, mx, my, size, edge);
            return;
        }
        switch (state) {
            case DONE -> {
                int face = over ? p.primaryHover() : p.primary();
                frame.plate(d, p, mx, my, size, size, pressed ? Shapes.mix(DataLook.opaque(face), DataLook.back(p), 0.25F) : face, 0);
                drawGlyph(d, step, state, mx, my, size, p.primaryText());
            }
            case ERROR -> {
                int face = over ? p.destructiveHover() : p.destructive();
                frame.plate(d, p, mx, my, size, size, pressed ? Shapes.mix(DataLook.opaque(face), DataLook.back(p), 0.25F) : face, 0);
                drawGlyph(d, step, state, mx, my, size, p.destructiveText());
            }
            case CURRENT -> {
                int ink = Ink.readable(p, p.markInk(), p.surface(), Ink.MARK);
                frame.plate(d, p, mx, my, size, size, Shapes.alpha(ink, 0x30), 0);
                frame.plate(d, p, mx, my, size, size, 0, ink);
                drawGlyph(d, step, state, mx, my, size, Ink.on(p, p.surface()));
            }
            default -> {
                int ink = Ink.soft(p, p.surface());
                if (over) {
                    frame.plate(d, p, mx, my, size, size, RowLook.tint(p, pressed ? 1F : hover.fade()), 0);
                }
                frame.plate(d, p, mx, my, size, size, 0, over ? ink : Shapes.alpha(DataLook.opaque(ink), REST_EDGE));
                drawGlyph(d, step, state, mx, my, size, ink);
            }
        }
    }

    private void drawGlyph(Draw d, int step, State state, int mx, int my, int size, int ink) {
        if (size < MARKER) {
            return;
        }
        if (state == State.DONE) {
            DataLook.art(d, CHECK, mx + 3, my + 4, ink);
        } else if (state == State.ERROR) {
            DataLook.art(d, BANG, mx + 5, my + 3, ink);
        } else {
            String number = numbers[step];
            int width = d.width(number);
            if (width <= size - 2) {
                d.text(number, mx + (size - width + 1) / 2, my + (size - RowLook.FONT_H) / 2 + 1, ink);
            }
        }
    }

    // ---- tooltips ----

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!visible) {
            return null;
        }
        int at = stepAt(mx, my);
        if (at < 0) {
            return super.tooltipAt(mx, my);
        }
        String error = errors.get(at);
        boolean cut = !showLabels || !fitLabels[at].equals(labels.get(at));
        if ((error == null || error.isEmpty()) && !cut) {
            return super.tooltipAt(mx, my);
        }
        if (tipFor != at || tip == null) {
            tipFor = at;
            List<String> lines = new ArrayList<>(2);
            if (cut) {
                lines.add(labels.get(at));
            }
            if (error != null && !error.isEmpty()) {
                lines.add(error);
            }
            tip = List.copyOf(lines);
        }
        return tip;
    }

    // ---- input ----

    private boolean accepts() {
        return visible && active && settled();
    }

    private boolean anyToGo() {
        for (int i = 0; i < count(); i++) {
            if (canGo(i)) {
                return true;
            }
        }
        return false;
    }

    private void go(int step) {
        BaseComponent.click();
        current = step;
        reached = Math.max(reached, current);
        dirty = true;
        reveal(step);
        if (onStep != null) {
            onStep.accept(step);
        }
    }

    @Override
    protected boolean takesFocus() {
        return visible && active && anyToGo();
    }

    @Override
    protected boolean navigable() {
        return accepts() && anyToGo();
    }

    @Override
    protected Box focusBox() {
        refresh();
        int on = cursor >= 0 ? cursor : Math.min(current, count() - 1);
        if (on >= 0 && !textOnly && (!vertical || (on >= scroll.offset() && on < scroll.offset() + rowsFit))) {
            return new Box("marker", markerX(on), markerY(on), markerSize(), markerSize());
        }
        return super.focusBox();
    }

    @Override
    protected int focusCorner() {
        return Math.min(super.focusCorner(), MAX_CORNER);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!accepts() || button != 0 || !isOver(mx, my)) {
            return false;
        }
        if (bar && scroll.press(mx, my, barX(), y + 1, Math.max(1, h - 2))) {
            return true;
        }
        int at = stepAt(mx, my);
        if (canGo(at)) {
            held = at;
            cursor = at;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && scroll.dragging()) {
            scroll.release();
            return true;
        }
        if (button != 0 || held < 0) {
            return false;
        }
        int was = held;
        held = -1;
        if (accepts() && stepAt(mx, my) == was && canGo(was)) {
            go(was);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return scroll.drag(my) || held >= 0;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || !settled() || !isOver(mx, my) || !vertical) {
            return false;
        }
        refresh();
        if (rowsFit >= count()) {
            return false;
        }
        scroll.wheel(amount > 0D ? 1 : -1, ScrollBar.WHEEL_ROWS);
        return true;
    }

    private void walk(int by) {
        int from = cursor >= 0 ? cursor : current;
        for (int i = from + by; i >= 0 && i < count(); i += by) {
            if (canGo(i)) {
                cursor = i;
                reveal(i);
                return;
            }
        }
    }

    @Override
    protected boolean activate() {
        if (!accepts() || !canGo(cursor)) {
            return false;
        }
        go(cursor);
        cursor = -1;
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!accepts() || count() == 0) {
            return false;
        }
        switch (key) {
            case LEFT, UP -> walk(-1);
            case RIGHT, DOWN -> walk(1);
            case ENTER, SPACE -> {
                return activate();
            }
            case ESCAPE -> {
                if (cursor < 0) {
                    return false;
                }
                cursor = -1;
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
