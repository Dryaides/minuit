package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * An integer slider snapped to a step. The value is written at the right of the track, through a
 * label format when one is set ("Rounds: 5"). {@code onChange} fires as the knob moves; the commit
 * fires once, on release, or at once for a key.
 */
public class SliderComponent extends BaseComponent {

    private static final int VALUE_ROOM = 24;
    /** The shortest track a long value label may leave, as {@link RangeSliderComponent} keeps. */
    private static final int TRACK_MIN_W = 24;
    private static final int LABEL_FLOOR_W = 12;
    private final int min;
    private final int max;
    private final IntConsumer onChange;
    private IntConsumer onCommit = v -> { };
    private IntFunction<String> label = Integer::toString;
    private int step = 1;
    /** The width the value text took at the last draw, which the track leaves free. */
    private int valueRoom = VALUE_ROOM;
    public int value;
    /** Draws a stop at the end of the track: the value goes no further for now. */
    public boolean capped;
    private boolean dragging;
    private int pressValue;
    /** The family drew the game's slider at the last draw: the handle then runs over the whole box. */
    private boolean gameLook;
    private boolean keyLit;

    public SliderComponent(Box box, int min, int max, int value, IntConsumer onChange) {
        super(box);
        this.min = min;
        this.max = Math.max(min, max);
        this.value = Maths.clamp(value, min, this.max);
        this.onChange = onChange;
    }

    /** Only multiples of {@code step} above {@code min} are taken, plus {@code max} itself. */
    public SliderComponent step(int step) {
        this.step = Math.max(1, step);
        this.value = snap(this.value, this.min, this.max, this.step);
        return this;
    }

    /** The text written for a value, in place of the bare number. */
    public SliderComponent label(IntFunction<String> format) {
        this.label = format;
        return this;
    }

    /** Fired once the value is settled: on release after a drag or a click, or on a key. */
    public SliderComponent onCommit(IntConsumer commit) {
        this.onCommit = commit;
        return this;
    }

    /** True from the press on the track until the release, so a screen can hold back a server update. */
    public boolean isDragging() {
        return this.dragging;
    }

    /** {@code raw} on the nearest step from {@code min}, kept in range; {@code max} is a stop even off the grid. */
    public static int snap(int raw, int min, int max, int step) {
        int v = Maths.clamp(raw, min, max);
        if (step <= 1) {
            return v;
        }
        int down = min + (v - min) / step * step;
        int up = (int) Math.min((long) down + step, max);
        return v - down < up - v ? down : up;
    }

    /** Number of ticks after the first: {@code max} is the last one even off the grid. */
    private int ticks() {
        return (this.max - this.min + this.step - 1) / this.step;
    }

    /** Where {@code v} sits on the track, 0 to 1, by tick index so it matches the ticks the groove draws. */
    private float fraction(int v) {
        int n = ticks();
        if (n == 0) {
            return 0;
        }
        int index = v >= this.max ? n : (v - this.min) / this.step;
        return (float) index / n;
    }

    /** Pixels from the track start to the handle centre, always on a tick for a stepped slider. */
    int handleOffset() {
        return Math.round(fraction(this.value) * trackWidth());
    }

    private int trackWidth() {
        return Math.max(0, this.gameLook ? this.w - 8 : this.w - this.valueRoom - 8);
    }

    /**
     * The room a slider keeps for its value label beside the track, shared with {@link RangeSliderComponent}:
     * the label's width (at least {@code least}) while the track keeps its minimum, else what is left
     * when that still holds {@code floor} pixels of text, else none.
     */
    static int labelRoom(int wanted, int least, int spare, int floor) {
        if (wanted <= 0) {
            return 0;
        }
        int room = Math.max(least, wanted);
        if (room <= spare) {
            return room;
        }
        return spare >= floor ? spare : 0;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        String number = this.label.apply(this.value);
        FrameStyle style = theme.frame();
        this.gameLook = style.gameWidgets();
        if (this.gameLook) {
            boolean lit = this.active && (this.dragging || this.keyLit || this.hoverAnim >= 0.5F);
            style.sliderBar(d, p, this.x, this.y, this.w, this.h, lit, this.active);
            style.sliderHandle(d, p, this.x + handleOffset(), this.y, this.h, lit);
            int fit = Math.min(d.width(number), this.w - 4);
            d.textFit(number, this.x + (this.w - fit) / 2, this.y + (this.h - 8) / 2 + 1, this.w - 4,
                    this.active ? p.raisedText() : p.disabledText());
            return;
        }
        int wanted = Math.max(d.width(this.label.apply(this.min)), d.width(this.label.apply(this.max))) + 4;
        this.valueRoom = labelRoom(wanted, VALUE_ROOM, this.w - 8 - TRACK_MIN_W, LABEL_FLOOR_W);
        int tx = this.x + 4;
        int tw = trackWidth();
        int ty = this.y + this.h / 2 - 2;
        int filled = handleOffset();
        int steps = ticks();
        theme.frame().groove(d, p, tx - 2, ty, tw + 4, 4, filled + 2, steps, this.active);
        if (this.capped) {
            d.fill(tx + tw + 2, ty - 2, tx + tw + 4, ty + 6, p.frame());
        }
        if (this.active) {
            theme.frame().knob(d, p, tx + filled - 3, this.y + this.h / 2 - 4, this.dragging || this.hoverAnim >= 0.5F);
        }
        if (this.valueRoom > 0) {
            String shown = d.ellipsize(number, this.valueRoom - 4);
            d.text(shown, this.x + this.w - 2 - d.width(shown), this.y + (this.h - 8) / 2 + 1,
                    this.active ? p.text() : p.disabledText());
        }
    }

    private void set(int v) {
        if (v != this.value) {
            this.value = v;
            this.onChange.accept(v);
        }
    }

    private void update(double mx) {
        float t = (float) Maths.clamp((mx - this.x - 4) / Math.max(1, trackWidth()), 0, 1);
        int n = ticks();
        int index = Math.round(t * n);
        set(index >= n ? this.max : this.min + index * this.step);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !isOver(mx, my) || button != 0) {
            return false;
        }
        this.dragging = true;
        this.pressValue = this.value;
        update(mx);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (this.dragging) {
            update(mx);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = this.dragging;
        this.dragging = false;
        if (was && this.value != this.pressValue) {
            this.onCommit.accept(this.value);
        }
        return was;
    }

    /** The game's slider turns its outline white under the keyboard, as under the mouse. */
    @Override
    protected boolean drawFocus(Draw d, Theme theme) {
        if (!theme.frame().gameWidgets()) {
            return false;
        }
        this.keyLit = true;
        render(d, theme, Integer.MIN_VALUE, Integer.MIN_VALUE, false);
        this.keyLit = false;
        return true;
    }

    @Override
    protected boolean navigable() {
        return true;
    }

    /** Left and Right move one step, a tenth of the range with Shift; Home and End go to the ends. */
    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        int by = shift ? Math.max(this.step, (this.max - this.min) / 10 / this.step * this.step) : this.step;
        int target = switch (key) {
            case LEFT -> this.value - by;
            case RIGHT -> this.value + by;
            case HOME -> this.min;
            case END -> this.max;
            default -> Integer.MIN_VALUE;
        };
        if (target == Integer.MIN_VALUE) {
            return false;
        }
        int before = this.value;
        set(snap(target, this.min, this.max, this.step));
        if (this.value != before) {
            this.onCommit.accept(this.value);
        }
        return true;
    }
}
