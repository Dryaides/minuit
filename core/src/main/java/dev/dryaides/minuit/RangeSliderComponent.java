package dev.dryaides.minuit;

import java.util.function.IntFunction;

/**
 * Picks a whole-number range: a slider with a low and a high handle, and the fill between them only.
 *
 * <pre>{@code
 * add(new RangeSliderComponent(at(new Box("levels", 10, 10, 160, 16)), 0, 100, 20, 80,
 *         (low, high) -> preview(low, high))
 *         .step(5)
 *         .minGap(10)
 *         .label(v -> v + " %")
 *         .onCommit((low, high) -> save(low, high)));
 * }</pre>
 *
 * <p>Mouse: a press grabs the nearest handle, and a press on the track also moves it there. Where both
 * handles are under the pointer, the first movement decides: right takes the high one, left the low one.
 * Handles stop at each other, {@code minGap} apart.
 *
 * <p>Keys: on arrival no handle is chosen. Tab (shift: backwards), left or right chooses one; after that
 * left and right move it, with shift for the larger step, and Home and End send it to its limit. Enter
 * goes back to choosing. Tab on the last handle leaves the widget.
 *
 * <p>Every value lands on a stop: {@code min + k * step}, and {@code max} even if the step does not
 * divide the range. If {@code min > max} they are swapped. {@code low} and {@code high} may be written
 * directly, as {@link SliderComponent#value} may; they are put back in bounds on the next frame or input,
 * without calling back.
 */
public class RangeSliderComponent extends BaseComponent {
    /** Told the new low and high. */
    @FunctionalInterface
    public interface RangeListener {
        void accept(int low, int high);
    }

    /** Writes the label from both values. */
    @FunctionalInterface
    public interface RangeLabel {
        String of(int low, int high);
    }

    /** One of the two handles. */
    public enum Handle { LOW, HIGH }

    private static final int GAME_HANDLE_W = 8;
    private static final int KNOB_W = 6;
    private static final int KNOB_H = 8;
    private static final int LABEL_MIN_W = 20;
    private static final int LABEL_GAP = 8;
    private static final int GROOVE_H = 4;
    private static final int GROOVE_MIN_W = 24;
    private static final int LABEL_FLOOR_W = 12;
    private static final int LARGE_STEP_DIVISOR = 10;

    private enum Grab { NONE, LOW, HIGH, UNDECIDED }

    public int low;
    public int high;

    private final int min;
    private final int max;
    private final RangeListener onChange;
    private RangeListener onCommit;
    private RangeLabel label = (l, h) -> l + " - " + h;
    private int step = 1;
    private int minGap;

    /** Set on every frame: the look decides where the track lies, and the mouse must agree with it. */
    private boolean gameWidgets;
    private Grab grab = Grab.NONE;
    private int grabOffset;
    private int pressX;
    private int startLow;
    private int startHigh;
    private Handle chosen;
    private boolean wasFocused;

    /** @param onChange told while dragging and on every key; may be null */
    public RangeSliderComponent(Box box, int min, int max, int low, int high, RangeListener onChange) {
        super(box);
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        this.low = low;
        this.high = high;
        this.onChange = onChange;
        normalize();
    }

    /** Whole steps between stops, from {@code min}. Below 1 means 1. */
    public RangeSliderComponent step(int step) {
        this.step = Math.max(1, step);
        normalize();
        return this;
    }

    /** The smallest distance between the handles. Clamped between 0 and the whole range. */
    public RangeSliderComponent minGap(int minGap) {
        this.minGap = Math.max(0, minGap);
        normalize();
        return this;
    }

    /** Formats each value; the label reads {@code "low - high"}. */
    public RangeSliderComponent label(IntFunction<String> label) {
        this.label = (l, h) -> label.apply(l) + " - " + label.apply(h);
        return this;
    }

    /** Writes the whole label. A null or empty one hides it and gives the room to the track. */
    public RangeSliderComponent label(RangeLabel label) {
        this.label = label;
        return this;
    }

    /** Told on release, and after every key that moved a handle, when the range did change. */
    public RangeSliderComponent onCommit(RangeListener onCommit) {
        this.onCommit = onCommit;
        return this;
    }

    /** Sets both values without calling back. */
    public RangeSliderComponent set(int low, int high) {
        this.low = low;
        this.high = high;
        normalize();
        return this;
    }

    @Override
    public RangeSliderComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    public int min() { return min; }
    public int max() { return max; }
    public boolean isDragging() { return grab != Grab.NONE; }

    /** The handle the keys move, or null while choosing. */
    public Handle chosen() { return chosen; }

    /** The x where a value's handle is centred. Right for the theme drawn last. */
    public int xOf(int value) {
        return spanX() + pixelOf(clamp(value, min, max));
    }

    // ---- the stops ----

    private long range() {
        return (long) max - min;
    }

    /** minGap, as far as the range allows it. */
    private long gap() {
        return Math.min(minGap, range());
    }

    private static long clamp(long v, long lo, long hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** The largest stop at or under v, which is in the range. */
    private long stopBelow(long v) {
        if (v >= max) return max;
        return min + Math.floorDiv(Math.max(v, min) - min, (long) step) * step;
    }

    /** The smallest stop at or over v, which is in the range. */
    private long stopAbove(long v) {
        if (v <= min) return min;
        return Math.min(max, min + (Math.floorDiv(v - min - 1, (long) step) + 1) * step);
    }

    /** The nearest stop; halfway goes up. */
    private long snap(long v) {
        long a = stopBelow(v);
        long b = stopAbove(v);
        return v - a >= b - v ? b : a;
    }

    /** The stop for a handle that wants to be at v, given where the other handle is. */
    private long allowed(Handle h, long v) {
        long s = snap(clamp(v, min, max));
        return h == Handle.LOW
                ? Math.min(s, stopBelow(high - gap()))
                : Math.max(s, stopAbove(low + gap()));
    }

    /** Brings values written from outside back in bounds: on stops, in order, minGap apart. */
    private void normalize() {
        long a = snap(clamp(Math.min(low, high), min, max));
        long b = snap(clamp(Math.max(low, high), min, max));
        long g = gap();
        if (b - a < g) {
            if (a + g <= max) {
                b = stopAbove(a + g);
            } else {
                b = max;
            }
            if (b - a < g) {
                a = stopBelow(b - g);
            }
        }
        low = (int) a;
        high = (int) b;
    }

    private int value(Handle h) {
        return h == Handle.LOW ? low : high;
    }

    // ---- the geometry ----

    private String labelText() {
        String s = label == null ? null : label.of(low, high);
        return s == null ? "" : s;
    }

    private int widest() {
        if (label == null) return 0;
        int most = TextMeasure.width(labelText());
        String atMin = label.of(min, min);
        String atMax = label.of(max, max);
        if (atMin != null) most = Math.max(most, TextMeasure.width(atMin));
        if (atMax != null) most = Math.max(most, TextMeasure.width(atMax));
        return most;
    }

    /** The room kept right of the groove: the widest label, or what is left, or none when too tight. */
    private int labelRoom() {
        return SliderComponent.labelRoom(widest(), LABEL_MIN_W, w - LABEL_GAP - GROOVE_MIN_W, LABEL_FLOOR_W);
    }

    private int grooveW() {
        int room = labelRoom();
        return room == 0 ? w - 4 : w - LABEL_GAP - room;
    }

    /** The x of the smallest value's centre. */
    private int spanX() {
        return gameWidgets ? x + GAME_HANDLE_W / 2 : x + 4;
    }

    /** Pixels between the centres of the smallest and the largest value. */
    private int span() {
        return Math.max(0, gameWidgets ? w - GAME_HANDLE_W : grooveW() - 4);
    }

    private int pixelOf(long v) {
        long r = range();
        if (r == 0) return 0;
        long s = span();
        return (int) ((s * (v - min) * 2 + r) / (2 * r));
    }

    private long valueAt(int centerX) {
        long s = span();
        if (s == 0) return min;
        long px = clamp((long) centerX - spanX(), 0, s);
        return min + (range() * px * 2 + s) / (2 * s);
    }

    private int halfHandle() {
        return (gameWidgets ? GAME_HANDLE_W : KNOB_W) / 2;
    }

    // ---- the mouse ----

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!active || !visible || button != 0 || !isOver(mx, my)) {
            return false;
        }
        normalize();
        startLow = low;
        startHigh = high;
        int px = (int) Math.floor(mx);
        pressX = px;
        if (span() == 0 || gap() == range()) {
            return true; // nothing can move, min == max included
        }
        int lowX = xOf(low);
        int highX = xOf(high);
        int half = halfHandle();
        boolean onLow = Math.abs(px - lowX) <= half;
        boolean onHigh = Math.abs(px - highX) <= half;
        if (onLow && onHigh) {
            grab = Grab.UNDECIDED;
            return true;
        }
        Handle h;
        if (onLow) {
            h = Handle.LOW;
        } else if (onHigh) {
            h = Handle.HIGH;
        } else if (lowX == highX) {
            h = px < lowX ? Handle.LOW : Handle.HIGH;
        } else {
            h = Math.abs(px - lowX) <= Math.abs(px - highX) ? Handle.LOW : Handle.HIGH;
        }
        grab = h == Handle.LOW ? Grab.LOW : Grab.HIGH;
        chosen = h;
        grabOffset = onLow || onHigh ? px - xOf(value(h)) : 0;
        moveTo(h, valueAt(px - grabOffset));
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (grab == Grab.NONE) {
            return false;
        }
        if (!active) {
            return true;
        }
        int px = (int) Math.floor(mx);
        if (grab == Grab.UNDECIDED) {
            decide(px);
            return true;
        }
        moveTo(grab == Grab.LOW ? Handle.LOW : Handle.HIGH, valueAt(px - grabOffset));
        return true;
    }

    /** Both handles were under the press. A handle with no room to go that way does not take the drag. */
    private void decide(int px) {
        if (px == pressX) {
            return;
        }
        Handle h = px > pressX ? Handle.HIGH : Handle.LOW;
        int offset = pressX - xOf(value(h));
        if (allowed(h, valueAt(px - offset)) == value(h)) {
            return;
        }
        grab = h == Handle.LOW ? Grab.LOW : Grab.HIGH;
        grabOffset = offset;
        chosen = h;
        moveTo(h, valueAt(px - offset));
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (grab == Grab.NONE || button != 0) {
            return false;
        }
        grab = Grab.NONE;
        if ((low != startLow || high != startHigh) && onCommit != null) {
            onCommit.accept(low, high);
        }
        return true;
    }

    /** Moves a handle as far as it may go. True if it did move. */
    private boolean moveTo(Handle h, long wanted) {
        int v = (int) allowed(h, wanted);
        if (v == value(h)) {
            return false;
        }
        if (h == Handle.LOW) {
            low = v;
        } else {
            high = v;
        }
        if (onChange != null) {
            onChange.accept(low, high);
        }
        return true;
    }

    // ---- the keys ----

    @Override
    protected boolean navigable() {
        return active && visible;
    }

    @Override
    protected void enter(boolean forward) {
        chosen = null;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!active || !visible) {
            return false;
        }
        normalize();
        if (gap() == range()) {
            return false; // nothing can move, min == max included: the panel's navigation goes on
        }
        switch (key) {
            case TAB:
                return tab(shift);
            case LEFT:
            case RIGHT:
                int dir = key == InputKey.RIGHT ? 1 : -1;
                if (chosen == null) {
                    chosen = dir > 0 ? Handle.HIGH : Handle.LOW;
                } else {
                    step(chosen, dir, shift);
                }
                return true;
            case HOME:
                if (chosen == null) chosen = Handle.LOW;
                commitIfMoved(moveTo(chosen, min));
                return true;
            case END:
                if (chosen == null) chosen = Handle.HIGH;
                commitIfMoved(moveTo(chosen, max));
                return true;
            default:
                return false;
        }
    }

    /** Low, then high, then out; backwards with shift. False lets the panel go to the next widget. */
    private boolean tab(boolean backwards) {
        Handle first = backwards ? Handle.HIGH : Handle.LOW;
        Handle last = backwards ? Handle.LOW : Handle.HIGH;
        if (chosen == null) {
            chosen = first;
            return true;
        }
        if (chosen == first) {
            chosen = last;
            return true;
        }
        chosen = null;
        return false;
    }

    private void step(Handle h, int dir, boolean large) {
        long from = value(h);
        long to;
        if (large) {
            long by = Math.max(step, range() / LARGE_STEP_DIVISOR);
            to = snap(clamp(from + dir * by, min, max));
            if (to == from) to = neighbour(from, dir);
        } else {
            to = neighbour(from, dir);
        }
        commitIfMoved(moveTo(h, to));
    }

    /** The next stop that way. */
    private long neighbour(long v, int dir) {
        return dir > 0 ? stopAbove(v + 1) : stopBelow(v - 1);
    }

    private void commitIfMoved(boolean moved) {
        if (moved && onCommit != null) {
            onCommit.accept(low, high);
        }
    }

    /** Enter: choose again. */
    @Override
    protected boolean activate() {
        if (chosen == null) {
            return false;
        }
        chosen = null;
        return true;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        gameWidgets = t.frame().gameWidgets();
        normalize();
        if (wasFocused && !focused) {
            chosen = null;
        }
        wasFocused = focused;
        boolean lowHot = active && (grab == Grab.LOW || grab == Grab.UNDECIDED || focused && chosen == Handle.LOW);
        boolean highHot = active && (grab == Grab.HIGH || grab == Grab.UNDECIDED || focused && chosen == Handle.HIGH);
        if (active && hovered && grab == Grab.NONE) {
            lowHot |= underPointer(mx, Handle.LOW);
            highHot |= underPointer(mx, Handle.HIGH);
        }
        boolean barHot = active && (hovered || grab != Grab.NONE);
        if (gameWidgets) {
            drawGame(d, t, barHot, lowHot, highHot);
        } else {
            drawModern(d, t, lowHot, highHot);
        }
    }

    /** What a press here would take: both handles when it would wait for the drag's direction. */
    private boolean underPointer(int mx, Handle h) {
        int lowX = xOf(low);
        int highX = xOf(high);
        int half = halfHandle();
        boolean onLow = Math.abs(mx - lowX) <= half;
        boolean onHigh = Math.abs(mx - highX) <= half;
        if (onLow || onHigh) {
            return h == Handle.LOW ? onLow : onHigh;
        }
        if (lowX == highX) {
            return (mx < lowX) == (h == Handle.LOW);
        }
        boolean lowNearer = Math.abs(mx - lowX) <= Math.abs(mx - highX);
        return lowNearer == (h == Handle.LOW);
    }

    /**
     * The game's look, with the parts and colours of the library's slider: its bar, its two handles, its label. The
     * label takes the stretch between the handles, else the wider one outside them, and never sits under a handle.
     */
    private void drawGame(Draw d, Theme t, boolean barHot, boolean lowHot, boolean highHot) {
        Palette p = t.palette();
        if (w < 2) {
            return;
        }
        t.frame().sliderBar(d, p, x, y, w, h, barHot, active);
        boolean handles = w >= GAME_HANDLE_W;
        if (handles) {
            drawGameHandles(d, t, lowHot, highHot);
        }
        String text = labelText();
        if (text.isEmpty()) {
            return;
        }
        int half = GAME_HANDLE_W / 2;
        int[] spans = handles
                ? new int[] {xOf(low) + half, xOf(high) - half, x + 2, xOf(low) - half, xOf(high) + half, x + w - 2}
                : new int[] {x + 2, x + w - 2};
        int textW = d.width(text);
        int pick = -1;
        for (int i = 0; i < spans.length; i += 2) {
            int room = spans[i + 1] - spans[i];
            if (room >= textW + 2 && (pick < 0 || pick > 0 && room > spans[pick + 1] - spans[pick])) {
                pick = i;
            }
        }
        if (pick >= 0) {
            int from = spans[pick];
            d.text(text, from + (spans[pick + 1] - from - textW + 1) / 2, labelY(), active ? p.raisedText() : p.disabledText());
        }
    }

    private void drawGameHandles(Draw d, Theme t, boolean lowHot, boolean highHot) {
        Palette p = t.palette();
        int half = GAME_HANDLE_W / 2;
        if (grab == Grab.HIGH || chosen == Handle.HIGH) {
            t.frame().sliderHandle(d, p, xOf(low) - half, y, h, lowHot);
            t.frame().sliderHandle(d, p, xOf(high) - half, y, h, highHot);
        } else {
            t.frame().sliderHandle(d, p, xOf(high) - half, y, h, highHot);
            t.frame().sliderHandle(d, p, xOf(low) - half, y, h, lowHot);
        }
    }

    /** The themed look: a groove, knobs, the label on the right, as the library's slider. */
    private void drawModern(Draw d, Theme t, boolean lowHot, boolean highHot) {
        Palette p = t.palette();
        FrameStyle f = t.frame();
        int grooveW = grooveW();
        if (grooveW >= 2) {
            int grooveX = x + 2;
            int grooveY = y + h / 2 - GROOVE_H / 2;
            f.groove(d, p, grooveX, grooveY, grooveW, GROOVE_H, 0, (int) Math.min(range(), Integer.MAX_VALUE), active);
            int from = xOf(low);
            int fill = xOf(high) - from;
            if (fill >= 2) {
                // the fill is one more groove, cut in as many ticks as it spans, so a family's notches line up
                f.groove(d, p, from, grooveY, fill, GROOVE_H, fill, (int) Math.min((long) high - low, Integer.MAX_VALUE), active);
            }
        }
        if (active && w >= KNOB_W + 2) {
            drawKnobs(d, t, lowHot, highHot);
        }
        int room = labelRoom();
        String text = labelText();
        if (room > 0 && !text.isEmpty()) {
            String shown = d.ellipsize(text, room);
            d.text(shown, x + w - 2 - d.width(shown), labelY(), active ? p.text() : p.disabledText());
        }
    }

    private void drawKnobs(Draw d, Theme t, boolean lowHot, boolean highHot) {
        if (grab == Grab.HIGH || chosen == Handle.HIGH) {
            drawKnob(d, t, Handle.LOW, lowHot);
            drawKnob(d, t, Handle.HIGH, highHot);
        } else {
            drawKnob(d, t, Handle.HIGH, highHot);
            drawKnob(d, t, Handle.LOW, lowHot);
        }
    }

    private void drawKnob(Draw d, Theme t, Handle handle, boolean hot) {
        t.frame().knob(d, t.palette(), xOf(value(handle)) - KNOB_W / 2, y + h / 2 - KNOB_H / 2, hot);
    }

    private int labelY() {
        return y + (h - 8) / 2 + 1;
    }

    /** While choosing, the ring is on the whole widget; then it marks the chosen handle. */
    @Override
    protected Box focusBox() {
        if (chosen == null) {
            return super.focusBox();
        }
        // Every family's knob covers one column more than KNOB_W, centred on xOf: the ring must go round all of it.
        return new Box(box().name(), xOf(value(chosen)) - KNOB_W / 2, y + h / 2 - KNOB_H / 2, KNOB_W + 1, KNOB_H);
    }

    @Override
    protected int focusCorner() {
        return chosen == null ? super.focusCorner() : 1;
    }

    @Override
    protected boolean drawFocus(Draw d, Theme t) {
        if (!t.frame().gameWidgets()) {
            if (chosen != null && w >= KNOB_W + 2) {
                drawKnob(d, t, chosen, true);
            }
            return false;
        }
        drawGame(d, t, chosen == null, chosen != Handle.HIGH, chosen != Handle.LOW);
        return true;
    }
}
