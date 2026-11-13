package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Minus, value, plus. A click moves one step, {@link #shiftFactor} steps with Shift held; held down, the button repeats
 * after {@link #REPEAT_DELAY_MS}, faster and faster, timed on {@link BaseComponent#clock}. Left and
 * Right step when the keyboard is on it, Home and End go to the ends. {@code onChange} fires on
 * every step, the commit once the button is let go, or at once for a key. A {@link #canStep} veto can
 * refuse a move inside the bounds; the button stays live and a refused press or key goes to
 * {@link #onVeto}, so the screen can say why.
 */
public class StepperComponent extends BaseComponent {

    /** Whether the value may go from {@code from} to {@code to}, both inside the bounds. */
    public interface StepVeto {
        boolean allows(int from, int to);
    }

    /** A move {@link #canStep} refused on a press or a key, not on a repeat of a held button. */
    public interface VetoListener {
        void vetoed(int from, int to);
    }

    /** The colour of the value text, picked from the palette's roles. */
    public interface LabelColor {
        int color(Palette palette, int value);
    }

    public static final int REPEAT_DELAY_MS = 400;
    public static final int REPEAT_FIRST_MS = 150;
    public static final int REPEAT_FASTEST_MS = 40;
    /** Each repeat waits this fraction of the one before, down to {@link #REPEAT_FASTEST_MS}. */
    public static final float REPEAT_SPEEDUP = 0.85F;
    /** The default {@link #shiftFactor}. */
    public static final int SHIFT_FACTOR = 10;

    private final int min;
    private final int max;
    private final IntConsumer onChange;
    private IntConsumer onCommit = v -> { };
    private IntFunction<String> label = Integer::toString;
    private int step = 1;
    private int shiftFactor = SHIFT_FACTOR;
    private StepVeto veto = (from, to) -> true;
    private VetoListener onVeto = (from, to) -> { };
    /** True while the held button repeats, when a refusal is not reported. */
    private boolean repeating;
    private LabelColor labelColor = (palette, value) -> palette.text();
    public int value;
    /** Delta mode: each step is sent here as +step or -step and the value is not kept here. */
    private IntConsumer onStep;
    /** The text shown in delta mode, set by the screen from the value the server holds. */
    public String caption = "";

    /** -1 minus held, +1 plus held, 0 nothing. */
    private int held;
    private long heldSince;
    private int repeatsDone;
    private int pressValue;

    public StepperComponent(Box box, int min, int max, int value, IntConsumer onChange) {
        super(box);
        this.min = min;
        this.max = Math.max(min, max);
        this.value = Maths.clamp(value, min, this.max);
        this.onChange = onChange;
    }

    /**
     * A stepper whose value lives elsewhere, a server's: each step (repeat and Shift included) goes
     * to {@code onStep} as a signed delta, and the screen writes what to show in {@link #caption}.
     * No bounds; both buttons stay live.
     */
    public static StepperComponent delta(Box box, IntConsumer onStep) {
        StepperComponent s = new StepperComponent(box, 0, 0, 0, v -> { });
        s.onStep = onStep;
        return s;
    }

    public StepperComponent caption(String text) {
        this.caption = text == null ? "" : text;
        return this;
    }

    public StepperComponent step(int step) {
        this.step = Math.max(1, step);
        return this;
    }

    /** How many steps one move takes with Shift held. */
    public StepperComponent shiftFactor(int factor) {
        this.shiftFactor = Math.max(1, factor);
        return this;
    }

    /**
     * A veto on each move of the value, the press, the repeats, the keys and Home or End alike: a
     * refused move leaves the value as it is. Delta mode keeps no value and asks no veto.
     */
    public StepperComponent canStep(StepVeto veto) {
        this.veto = veto;
        return this;
    }

    /** Told of each refused press or key, once per press. */
    public StepperComponent onVeto(VetoListener listener) {
        this.onVeto = listener;
        return this;
    }

    /** The value text's colour while the stepper is active, {@code text} by default. */
    public StepperComponent labelColor(LabelColor color) {
        this.labelColor = color;
        return this;
    }

    /** The text written for a value, in place of the bare number. */
    public StepperComponent label(IntFunction<String> format) {
        this.label = format;
        return this;
    }

    /** Fired once the value is settled: on release of a held button, or on a key. */
    public StepperComponent onCommit(IntConsumer commit) {
        this.onCommit = commit;
        return this;
    }

    /**
     * How many repeats are due {@code heldMs} after the press: none before the delay, then one per
     * interval, each interval {@link #REPEAT_SPEEDUP} of the last.
     */
    public static int repeatsDue(long heldMs) {
        int n = 0;
        double at = REPEAT_DELAY_MS;
        double gap = REPEAT_FIRST_MS;
        while (at <= heldMs) {
            n++;
            at += gap;
            gap = Math.max(REPEAT_FASTEST_MS, gap * REPEAT_SPEEDUP);
        }
        return n;
    }

    private int buttonSize() {
        return Math.min(this.h, this.w / 3);
    }

    private int partAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return 0;
        }
        int b = buttonSize();
        return mx < this.x + b ? -1 : mx >= this.x + this.w - b ? 1 : 0;
    }

    private int stride(boolean shift) {
        return this.step * (shift ? this.shiftFactor : 1);
    }

    private boolean move(int direction) {
        return moveBy(direction, heldKeys().shift());
    }

    private boolean moveBy(int direction, boolean shift) {
        if (this.onStep != null) {
            this.onStep.accept(direction * stride(shift));
            return true;
        }
        return set(target(direction, shift));
    }

    private int target(int direction, boolean shift) {
        return (int) Maths.clamp((long) this.value + (long) direction * stride(shift), this.min, this.max);
    }

    /** Whether one step that way stays inside the bounds; a veto keeps the button live. */
    private boolean canMove(int direction) {
        return target(direction, false) != this.value;
    }

    /** Runs the repeats due by now; called each frame, so a held button keeps stepping. */
    private void repeat() {
        if (this.held == 0) {
            return;
        }
        int due = repeatsDue(clock().getAsLong() - this.heldSince);
        this.repeating = true;
        try {
            while (this.repeatsDone < due) {
                this.repeatsDone++;
                if (!move(this.held)) {
                    break;
                }
            }
        } finally {
            this.repeating = false;
        }
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        repeat();
        Palette p = theme.palette();
        int b = buttonSize();
        int over = hovered ? partAt(mx, my) : 0;
        boolean free = this.onStep != null;
        drawButton(d, theme, this.x, b, -1, over, free || canMove(-1));
        drawButton(d, theme, this.x + this.w - b, b, 1, over, free || canMove(1));
        theme.frame().well(d, p, this.x + b + 1, this.y, this.w - 2 * b - 2, this.h, this.focused);
        String text = d.ellipsize(free ? this.caption : this.label.apply(this.value), this.w - 2 * b - 6);
        d.centered(text, this.x + this.w / 2, this.y + (this.h - 8) / 2 + 1, this.active ? this.labelColor.color(p, this.value) : p.disabledText());
    }

    private void drawButton(Draw d, Theme theme, int bx, int b, int side, int over, boolean can) {
        Palette p = theme.palette();
        boolean live = this.active && can;
        int text;
        if (!live) {
            Shapes.rounded(d, bx, this.y, b, this.h, 3, p.wellEdge());
            Shapes.rounded(d, bx + 1, this.y + 1, b - 2, this.h - 2, 2, p.disabled());
            text = p.disabledText();
        } else {
            float lit = over == side ? this.hoverAnim : 0F;
            theme.frame().raised(d, p, bx, this.y, b, this.h, Shapes.mix(p.raised(), p.raisedHover(), lit),
                    this.held == side ? Shapes.Relief.PRESSED : Shapes.Relief.RAISED);
            text = p.raisedText();
        }
        int sink = this.held == side ? 1 : 0;
        int cx = bx + b / 2 + sink;
        int cy = this.y + this.h / 2 + sink;
        d.fill(cx - 3, cy - 1, cx + 2, cy, text);
        if (side > 0) {
            d.fill(cx - 1, cy - 3, cx, cy + 2, text);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int part = partAt(mx, my);
        if (!this.active || button != 0 || part == 0) {
            return isOver(mx, my) && this.active;
        }
        click();
        this.pressValue = this.value;
        this.held = part;
        this.heldSince = clock().getAsLong();
        this.repeatsDone = 0;
        move(part);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (this.held == 0) {
            return false;
        }
        repeat();
        this.held = 0;
        if (this.value != this.pressValue) {
            this.onCommit.accept(this.value);
        }
        return true;
    }

    @Override
    protected boolean navigable() {
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (this.onStep != null) {
            switch (key) {
                case LEFT -> moveBy(-1, shift);
                case RIGHT -> moveBy(1, shift);
                default -> {
                    return false;
                }
            }
            return true;
        }
        int before = this.value;
        switch (key) {
            case LEFT -> moveBy(-1, shift);
            case RIGHT -> moveBy(1, shift);
            case HOME -> set(this.min);
            case END -> set(this.max);
            default -> {
                return false;
            }
        }
        if (this.value != before) {
            this.onCommit.accept(this.value);
        }
        return true;
    }

    private boolean set(int v) {
        if (v == this.value) {
            return false;
        }
        if (!this.veto.allows(this.value, v)) {
            if (!this.repeating) {
                this.onVeto.vetoed(this.value, v);
            }
            return false;
        }
        this.value = v;
        this.onChange.accept(v);
        return true;
    }
}
