package dev.dryaides.minuit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * A number to type, with a minus and a plus beside it. {@link StepperComponent} moves a whole number by steps
 * and shows it; this one takes a typed value, decimals and a precise value out of a large range.
 *
 * <pre>{@code
 * add(new NumberFieldComponent(at(new Box("speed", 10, 10, 90, 16)), 0, 100, 1.5, v -> config.speed = v)
 *         .step(0.5).decimals(1));
 * }</pre>
 *
 * <p>What is typed counts when it is settled: by Enter, or by leaving the field. It is then rounded to
 * {@link #decimals(int)} and brought inside the limits. Text that is no number goes back to the last value, as Escape
 * does; while the text would be refused or moved, the field wears a red ring. A comma counts as the decimal point.
 *
 * <p>Up and down step, with Shift by ten steps. The buttons repeat while held. The wheel steps only while the field
 * has the focus, so a scrolling page cannot change it by accident: {@link #wheelOnHover(boolean)} changes that.
 */
public class NumberFieldComponent extends BaseComponent {
    private static final int TEXT_PAD = 4;
    private static final int BUTTON_W = 12;
    private static final int BUTTON_MARGIN = 1;
    private static final int BORDER = 1;
    private static final int WASH_INSET = 3;
    private static final int MIN_BUTTON_H = 9;
    private static final int MIN_TEXT = 18;
    private static final int STITCH_BLEED = 6;
    private static final int HOVER_WASH = 0x28;
    private static final int HELD_WASH = 0x50;
    private static final int SHIFT_FACTOR = 10;
    private static final int MINUS = 0;
    private static final int PLUS = 1;
    private static final int NO_BUTTON = -1;
    private static final int REPEAT_FIRST = 80;
    private static final String[] ART_MINUS = {".....", ".....", "#####", ".....", "....."};
    private static final String[] ART_PLUS = {"..#..", "..#..", "#####", "..#..", "..#.."};
    private static final int ART = 5;

    private final DoubleConsumer onChange;
    private final TextFieldComponent field;
    private final RowLook.HoverFade hover = new RowLook.HoverFade();

    private double min;
    private double max;
    private double step = 1D;
    private int decimals;
    private double value;
    private double asked;
    private boolean wheelOnHover;
    private String shown = "";
    private boolean editing;
    private int held = NO_BUTTON;
    private long nextRepeat;
    private boolean hadFocus;
    private Theme lastTheme;

    /**
     * @param box the field and its buttons
     * @param min the least value; pass {@code -Double.MAX_VALUE} for no limit
     * @param max the most
     * @param value where it starts
     * @param onChange called when the player's change settles or a step lands; may be null
     */
    public NumberFieldComponent(Box box, double min, double max, double value, DoubleConsumer onChange) {
        super(box);
        bound(min, max);
        this.onChange = onChange;
        this.field = new TextFieldComponent(box, "", this::edited)
                .filter(this::mayType);
        setValue(value);
    }

    // ---- setup ----

    /** How far a button, a key or a turn of the wheel moves the value. 1 by default. */
    public NumberFieldComponent step(double step) {
        this.step = step > 0D && Double.isFinite(step) ? step : 1D;
        return this;
    }

    /** How many decimals the value keeps. 0, whole numbers, by default. */
    public NumberFieldComponent decimals(int decimals) {
        this.decimals = Math.max(0, Math.min(9, decimals));
        setValue(asked);
        return this;
    }

    public NumberFieldComponent limits(double min, double max) {
        bound(min, max);
        setValue(asked);
        return this;
    }

    /** Orders the limits; an infinite or NaN one means no limit on that side. */
    private void bound(double a, double b) {
        double lo = Double.isFinite(a) ? a : -Double.MAX_VALUE;
        double hi = Double.isFinite(b) ? b : Double.MAX_VALUE;
        this.min = Math.min(lo, hi);
        this.max = Math.max(lo, hi);
    }

    /** True to step with the wheel without the focus too. Off by default. */
    public NumberFieldComponent wheelOnHover(boolean wheelOnHover) {
        this.wheelOnHover = wheelOnHover;
        return this;
    }

    public NumberFieldComponent active(boolean active) {
        this.active = active;
        if (!active) {
            held = NO_BUTTON;
        }
        sync();
        return this;
    }

    @Override
    public NumberFieldComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- content ----

    public double value() {
        return value;
    }

    public int intValue() {
        return (int) Math.round(value);
    }

    /** Sets the value, rounded and inside the limits, without calling back. What was being typed goes. */
    public final void setValue(double value) {
        this.value = clean(value);
        // Kept as given: decimals() usually comes after the constructor, and 0.5 must not have become 1 by then.
        asked = Double.isFinite(value) ? value : this.value;
        show();
    }

    /** What the field reads. */
    public String text() {
        return field.text();
    }

    /** Sets the text as if typed, without calling back or settling. */
    public void setText(String text) {
        field.setText(text == null ? "" : text);
        editing = true;
    }

    /** True while the typed text would be refused or changed when settled. */
    public boolean refused() {
        if (!editing) {
            return false;
        }
        Double typed = parse(field.text());
        return typed == null || typed < min || typed > max;
    }

    public boolean atMin() {
        return value <= min;
    }

    public boolean atMax() {
        return value >= max;
    }

    Box minusBox() {
        return new Box("minus", buttonX(MINUS), y, BUTTON_W, h);
    }

    Box plusBox() {
        return new Box("plus", buttonX(PLUS), y, BUTTON_W, h);
    }

    // ---- values ----

    private double clean(double raw) {
        if (!Double.isFinite(raw)) {
            return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
        }
        double rounded = BigDecimal.valueOf(raw).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
        return Math.max(min, Math.min(max, rounded));
    }

    private String format(double number) {
        if (decimals == 0) {
            return Long.toString(Math.round(number));
        }
        return BigDecimal.valueOf(number).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
    }

    private void show() {
        shown = format(value);
        field.setText(shown);
        editing = false;
    }

    private static Double parse(String text) {
        String clean = text.strip().replace(',', '.');
        if (clean.isEmpty() || clean.equals("-") || clean.equals(".") || clean.equals("-.")) {
            return null;
        }
        try {
            double number = Double.parseDouble(clean);
            return Double.isInfinite(number) || Double.isNaN(number) ? null : number;
        } catch (NumberFormatException wrong) {
            return null;
        }
    }

    private boolean mayType(int c) {
        if (c >= '0' && c <= '9') {
            return true;
        }
        if (c == '-') {
            return min < 0D;
        }
        return (c == '.' || c == ',') && decimals > 0;
    }

    private void edited(String text) {
        editing = !text.equals(shown);
    }

    /** Takes what was typed. */
    private void settle() {
        if (!editing) {
            return;
        }
        Double typed = parse(field.text());
        double before = value;
        if (typed != null) {
            value = clean(typed);
        }
        show();
        if (value != before && onChange != null) {
            onChange.accept(value);
        }
    }

    private void stepBy(int steps) {
        Double typed = editing ? parse(field.text()) : null;
        double from = typed != null ? typed : value;
        double before = value;
        value = clean(from + steps * step);
        show();
        field.moveCursor(field.text().length(), false);
        if (value != before && onChange != null) {
            onChange.accept(value);
        }
    }

    // ---- geometry ----

    private boolean inField(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean showsButtons() {
        return h >= MIN_BUTTON_H && w - zoneW() - TEXT_PAD >= MIN_TEXT;
    }

    private static int zoneW() {
        return 2 * BUTTON_W + 2 * BUTTON_MARGIN + BORDER;
    }

    private int zone() {
        return showsButtons() ? zoneW() : 0;
    }

    private int buttonX(int button) {
        int right = x + w - BORDER - BUTTON_MARGIN - BUTTON_W;
        return button == PLUS ? right : right - BUTTON_W;
    }

    private int buttonAt(double mx, double my) {
        if (!inField(mx, my) || !showsButtons()) {
            return NO_BUTTON;
        }
        if (mx >= buttonX(PLUS)) {
            return PLUS;
        }
        return mx >= buttonX(MINUS) ? MINUS : NO_BUTTON;
    }

    private boolean can(int button) {
        return active && (button == PLUS ? value < max : value > min);
    }

    private void placeField() {
        int zone = zone();
        field.x = x;
        field.y = y;
        field.h = h;
        field.w = zone > 0 ? w - zone + TEXT_PAD : w;
        field.active = active;
        field.focused = focused && active;
    }

    // ---- drawing ----

    /** The focus going elsewhere settles what was typed. */
    private void sync() {
        boolean live = focused && active && visible;
        if (hadFocus && !live) {
            hadFocus = false;
            held = NO_BUTTON;
            settle();
        } else if (live) {
            hadFocus = true;
        }
    }

    @Override
    public void tick() {
        sync();
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        lastTheme = t;
        sync();
        placeField();
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        if (field.focused && h >= FormLook.FONT_H) {
            drawLive(d, t, mx, my);
        } else {
            drawResting(d, p, frame);
        }
        if (refused()) {
            frame.plate(d, p, x, y, w, h, 0, FormLook.alertEdge(p));
        }
        int over = active && inField(mx, my) ? buttonAt(mx, my) : NO_BUTTON;
        hover.step(over);
        if (showsButtons()) {
            drawButton(d, p, frame, MINUS, ART_MINUS, over);
            drawButton(d, p, frame, PLUS, ART_PLUS, over);
        }
        repeat(over);
    }

    /** The library's field does the text, but draws its frame only as wide as its text: as in a combo box, two halves are stitched. */
    private void drawLive(Draw d, Theme t, int mx, int my) {
        int zone = zone();
        if (zone == 0) {
            field.draw(d, t, mx, my, delta);
            return;
        }
        int seam = x + w - zone;
        d.scissor(x - STITCH_BLEED, y - STITCH_BLEED, seam, y + h + STITCH_BLEED);
        field.draw(d, t, mx, my, delta);
        d.endScissor();
        d.scissor(seam, y - STITCH_BLEED, x + w + STITCH_BLEED, y + h + STITCH_BLEED);
        t.frame().field(d, t.palette(), x, y, w, h, true);
        d.endScissor();
    }

    private void drawResting(Draw d, Palette p, FrameStyle frame) {
        frame.field(d, p, x, y, w, h, false);
        int room = w - zone() - TEXT_PAD - (zone() == 0 ? TEXT_PAD : 0);
        if (room <= 0 || h < FormLook.FONT_H) {
            return;
        }
        int color = active ? frame.fieldText(p) : FormLook.fieldDisabled(p, frame);
        d.scissor(x, y, x + w, y + h);
        d.textFit(field.text(), x + TEXT_PAD, y + (h - FormLook.FONT_H) / 2, room, color);
        d.endScissor();
    }

    private void drawButton(Draw d, Palette p, FrameStyle frame, int button, String[] art, int over) {
        int bx = buttonX(button);
        boolean can = can(button);
        boolean isOver = button == over && can;
        boolean isHeld = isOver && button == held;
        int ink = frame.fieldText(p);
        if (isOver && h > 2 * WASH_INSET) {
            int alpha = isHeld ? HELD_WASH : Math.round(HOVER_WASH * hover.fade());
            d.fill(bx, y + WASH_INSET, bx + BUTTON_W, y + h - WASH_INSET, Shapes.alpha(ink, alpha));
        }
        int color = !active ? FormLook.fieldDisabled(p, frame) : !can ? Shapes.alpha(frame.fieldHint(p), 0x60) : isOver ? ink : frame.fieldHint(p);
        FormLook.pixels(d, art, bx + (BUTTON_W - ART) / 2, y + (h - ART) / 2, color);
    }

    private void repeat(int over) {
        if (held == NO_BUTTON || over != held || !can(held)) {
            return;
        }
        long now = BaseComponent.clock().getAsLong();
        while (now >= nextRepeat && can(held)) {
            stepBy(held == PLUS ? 1 : -1);
            nextRepeat += REPEAT_FIRST;
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!inField(mx, my)) {
            return null;
        }
        if (tooltip != null) {
            return tooltip;
        }
        int room = w - zone() - TEXT_PAD;
        return !focused && TextMeasure.width(field.text()) > room ? List.of(field.text()) : null;
    }

    // ---- input ----

    @Override
    protected boolean takesFocus() {
        return visible && active;
    }

    @Override
    protected boolean navigable() {
        return visible && active && settled();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        sync();
        if (!visible || !active || button != 0 || !inField(mx, my)) {
            return false;
        }
        hadFocus = true;
        placeField();
        int hit = buttonAt(mx, my);
        if (hit != NO_BUTTON) {
            if (can(hit)) {
                BaseComponent.click();
                held = hit;
                nextRepeat = BaseComponent.clock().getAsLong() + StepperComponent.REPEAT_DELAY_MS;
                stepBy(hit == PLUS ? 1 : -1);
            }
            return true;
        }
        // The field needs one frame's measures before it can place the caret, and a resting field draws none.
        if (lastTheme != null) {
            field.draw(TextMeasure.draw(), lastTheme, (int) mx, (int) my, delta);
        }
        field.mouseClicked(mx, my, 0);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return held == NO_BUTTON && field.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        held = NO_BUTTON;
        field.mouseReleased(mx, my, button);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || !active || !inField(mx, my) || amount == 0D || !(focused || wheelOnHover)) {
            return false;
        }
        stepBy(amount > 0D ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        sync();
        if (!visible || !active) {
            return false;
        }
        placeField();
        switch (key) {
            case UP -> stepBy(shift ? SHIFT_FACTOR : 1);
            case DOWN -> stepBy(shift ? -SHIFT_FACTOR : -1);
            case ENTER -> settle();
            case ESCAPE -> {
                if (!editing) {
                    return false;
                }
                BaseComponent.click();
                show();
            }
            case SPACE -> {
                return true;
            }
            case TAB -> {
                return false;
            }
            default -> {
                return field.keyPressed(key, shift);
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(int codePoint) {
        sync();
        if (!visible || !active) {
            return false;
        }
        placeField();
        return field.charTyped(codePoint);
    }
}
