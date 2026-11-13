package dev.dryaides.minuit;

import java.util.function.LongFunction;

/**
 * A number under its label: coins, score, kills, blocks mined. A new value counts to itself from the one shown,
 * over {@link #ROLL_MS}, at once with {@link MotionStyle#NONE}. It shows, it does not take input.
 *
 * <pre>{@code
 * StatComponent coins = add(new StatComponent(at(new Box("coins", 10, 10, 80, StatComponent.HEIGHT)), "Coins", 120));
 * coins.value(345); // counts from 120 to 345
 * add(new StatComponent(at(new Box("time", 100, 10, 80, StatComponent.HEIGHT)), "Best lap", 8420)
 *         .format(ms -> ms / 1000 + "." + ms / 100 % 10 + " s"));
 * }</pre>
 *
 * <p>The number draws at twice the text size, on whole pixels; when that does not fit the box, or with
 * {@link #large(boolean)} false, it draws at the text size and the box wants {@link #SMALL_HEIGHT}.
 */
public class StatComponent extends BaseComponent {

    /** The label, a gap, and the number at twice the text size. */
    public static final int HEIGHT = 27;
    /** The label and the number at the text size. */
    public static final int SMALL_HEIGHT = 19;
    public static final int ROLL_MS = 600;

    private static final int GAP = 2;
    private static final int LARGE = 2;

    private String label;
    private long value;
    private long rollFrom;
    private long rolledAt = -1;
    private boolean large = true;
    private boolean centred;
    private LongFunction<String> format = Long::toString;

    public StatComponent(Box box, String label, long value) {
        super(box);
        this.label = label == null ? "" : label;
        this.value = value;
    }

    /** Counts to {@code value} from the number shown now. */
    public StatComponent value(long value) {
        if (value != this.value) {
            this.rollFrom = shown();
            this.rolledAt = clock().getAsLong();
            this.value = value;
        }
        return this;
    }

    /** Shows {@code value} at once, with no count. */
    public StatComponent set(long value) {
        this.value = value;
        this.rolledAt = -1;
        return this;
    }

    public long value() {
        return this.value;
    }

    public StatComponent label(String label) {
        this.label = label == null ? "" : label;
        return this;
    }

    /** How a number reads: a unit, a time, digit grouping. Plain digits by default. */
    public StatComponent format(LongFunction<String> format) {
        this.format = format == null ? Long::toString : format;
        return this;
    }

    public StatComponent large(boolean large) {
        this.large = large;
        return this;
    }

    /** Centres the label and the number in the box, for a row of stats. */
    public StatComponent centred(boolean centred) {
        this.centred = centred;
        return this;
    }

    public StatComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public StatComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** The number drawn now: on its way from the last value to the new one, eased out. */
    public long shown() {
        if (this.rolledAt < 0) {
            return this.value;
        }
        long since = clock().getAsLong() - this.rolledAt;
        if (motion() == MotionStyle.NONE || since >= ROLL_MS) {
            this.rolledAt = -1;
            return this.value;
        }
        double t = Math.max(0D, since) / ROLL_MS;
        double eased = 1D - Math.pow(1D - t, 3D);
        return this.rollFrom + Math.round((this.value - this.rollFrom) * eased);
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        Palette p = t.palette();
        int labelInk = this.active ? FormLook.soft(p) : p.disabledText();
        int valueInk = this.active ? p.text() : p.disabledText();
        String number = this.format.apply(shown());
        int labelW = Math.min(this.w, TextMeasure.width(this.label) - 1);
        d.textFit(this.label, this.centred ? this.x + (this.w - labelW) / 2 : this.x, this.y, this.w + 1, labelInk);
        int numberW = TextMeasure.width(number) - 1;
        int top = this.y + RowLook.FONT_H + 1 + GAP;
        boolean big = this.large && numberW * LARGE <= this.w && this.h >= HEIGHT;
        if (big) {
            int left = this.centred ? this.x + (this.w - numberW * LARGE) / 2 : this.x;
            d.push(left, top, LARGE);
            d.text(number, 0, 0, valueInk);
            d.pop();
        } else {
            int shownW = Math.min(this.w, numberW);
            d.textFit(number, this.centred ? this.x + (this.w - shownW) / 2 : this.x, top, this.w + 1, valueInk);
        }
    }
}
