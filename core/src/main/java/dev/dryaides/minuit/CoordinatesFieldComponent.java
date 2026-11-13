package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Fmt;
import dev.dryaides.minuit.internal.Maths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Three number fields, X Y Z, side by side. Each takes digits and a leading minus (and a point when
 * {@link #decimals} asks for them). Tab moves from one to the next, Up and Down nudge the field by 1 (Shift: 10),
 * and a paste of "x y z" or "x, y, z" fills all three. Two optional buttons follow: "~" switches to coordinates
 * relative to the player, as in commands, and "Here" fills the fields from a supplier the mod gives, so core
 * needs no Minecraft types.
 *
 * <pre>{@code
 * add(new CoordinatesFieldComponent(at(new Box("pos", 10, 10, 220, 16)), v -> target = v)
 *         .yRange(-64, 320)
 *         .here(() -> new CoordinatesFieldComponent.Value(player.getX(), player.getY(), player.getZ(), false)));
 * }</pre>
 *
 * <p>Every value handed out has Y inside the {@link #yRange} and X and Z inside {@link #LIMIT}. While a field is
 * being typed in, its text is left alone; it is corrected when the keyboard leaves it.
 */
public class CoordinatesFieldComponent extends BaseComponent {

    /** The world's horizontal limit; X and Z stay inside it. */
    public static final int LIMIT = 30_000_000;

    /**
     * The three numbers and whether they are relative to the player ("~").
     *
     * @param relative true when each number is an offset, written "~" or "~5" in a command
     */
    public record Value(double x, double y, double z, boolean relative) {

        /** The numbers as a command writes them: "10 64 -20", or "~ ~5 ~-2" when relative. */
        public String command() {
            return part(this.x) + " " + part(this.y) + " " + part(this.z);
        }

        private String part(double v) {
            String number = v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
            if (!this.relative) {
                return number;
            }
            return v == 0D ? "~" : "~" + number;
        }
    }

    private static final int GAP = 4;
    private static final int LABEL_GAP = 3;
    private static final int PART_GAP = 2;
    private static final String[] AXES = {"X", "Y", "Z"};

    private final Field[] fields = new Field[3];
    private final ButtonComponent relativeButton;
    private final ButtonComponent hereButton;
    private final Consumer<Value> onChange;
    private Supplier<Value> here;
    private boolean relativeToggle;
    private boolean relative;
    private int decimals;
    private double yMin = Double.NEGATIVE_INFINITY;
    private double yMax = Double.POSITIVE_INFINITY;
    private String hereLabel;
    private Words words;

    public CoordinatesFieldComponent(Box box, Consumer<Value> onChange) {
        super(box);
        this.onChange = onChange == null ? v -> { } : onChange;
        for (int i = 0; i < 3; i++) {
            int axis = i;
            this.fields[i] = new Field(AXES[i], text -> typed(axis, text));
            this.fields[i].setText("0");
        }
        applyFilter();
        this.relativeButton = new ButtonComponent(new Box("relative", 0, 0, 0, 0), "~", () -> {
            this.relative = !this.relative;
            emit();
        });
        this.hereButton = new ButtonComponent(new Box("here", 0, 0, 0, 0), "", this::fillHere);
    }

    /** Digits after the point, 0 to 3; 0 (the default) means whole numbers, as block coordinates are. */
    public CoordinatesFieldComponent decimals(int decimals) {
        this.decimals = Maths.clamp(decimals, 0, 3);
        applyFilter();
        value(value());
        return this;
    }

    /** Y may only be inside [min, max], the world's lowest and highest block given by the mod. */
    public CoordinatesFieldComponent yRange(double min, double max) {
        this.yMin = Math.min(min, max);
        this.yMax = Math.max(min, max);
        value(value());
        return this;
    }

    /** Adds the "~" button that switches between absolute and relative coordinates. */
    public CoordinatesFieldComponent relativeToggle(boolean shown) {
        this.relativeToggle = shown;
        return this;
    }

    /** Adds the "Here" button; the supplier returns the player's position, and its {@code relative} is ignored. */
    public CoordinatesFieldComponent here(Supplier<Value> position) {
        this.here = position;
        return this;
    }

    /** The "Here" button's word, over the lang file's. */
    public CoordinatesFieldComponent hereLabel(String label) {
        this.hereLabel = label;
        return this;
    }

    public CoordinatesFieldComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public CoordinatesFieldComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** The numbers now in the fields, Y and X, Z inside their limits. */
    public Value value() {
        return new Value(limit(0, read(0)), limit(1, read(1)), limit(2, read(2)), this.relative);
    }

    /** Fills the fields without calling the listener; numbers are cut down to the block, as {@link #parse} does. */
    public CoordinatesFieldComponent value(Value value) {
        this.relative = value.relative();
        set(0, value.x());
        set(1, value.y());
        set(2, value.z());
        return this;
    }

    /** {@code y} held inside [min, max]. */
    public static double clampY(double y, double min, double max) {
        return Math.max(Math.min(min, max), Math.min(Math.max(min, max), y));
    }

    /**
     * Reads "x y z" or "x, y, z" (also in brackets), each part a number or, as in a command, "~" or "~5" for
     * all three. Returns null for anything else: other counts, words, a mix of absolute and relative parts.
     * Numbers are cut down to {@code decimals} digits (12.7 is 12 with none, -3.2 is -4, the block it is in) and
     * held inside {@link #LIMIT}.
     */
    public static Value parse(String text, int decimals) {
        if (text == null) {
            return null;
        }
        String[] parts = text.replace(',', ' ').replace('(', ' ').replace(')', ' ').replace('[', ' ').replace(']', ' ')
                .trim().split("\\s+");
        if (parts.length != 3) {
            return null;
        }
        double[] out = new double[3];
        int tildes = 0;
        for (int i = 0; i < 3; i++) {
            String part = parts[i];
            boolean tilde = part.startsWith("~");
            if (tilde) {
                tildes++;
                part = part.substring(1);
            }
            if (part.isEmpty() && tilde) {
                out[i] = 0D;
            } else if (part.matches("-?\\d+(\\.\\d+)?")) {
                double scale = Math.pow(10, Maths.clamp(decimals, 0, 3));
                out[i] = Maths.clamp(Math.floor(Double.parseDouble(part) * scale) / scale, -LIMIT, LIMIT);
            } else {
                return null;
            }
        }
        if (tildes != 0 && tildes != 3) {
            return null;
        }
        return new Value(out[0], out[1], out[2], tildes == 3);
    }

    /** {@code text} cut to what a field holds: a leading minus, digits, and one point with up to {@code decimals} digits after it. */
    static String clean(String text, int decimals) {
        StringBuilder out = new StringBuilder();
        int point = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '-' && i == 0) {
                out.append(c);
            } else if (c >= '0' && c <= '9') {
                if (point < 0 || out.length() - point <= decimals) {
                    out.append(c);
                }
            } else if (c == '.' && decimals > 0 && point < 0) {
                point = out.length();
                out.append(c);
            }
        }
        return out.toString();
    }

    private void applyFilter() {
        boolean dots = this.decimals > 0;
        for (Field f : this.fields) {
            f.filter(c -> c >= '0' && c <= '9' || c == '-' || dots && c == '.');
            f.maxLength(this.decimals > 0 ? 12 + this.decimals : 11);
        }
    }

    private static double number(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0D;
        }
    }

    private double read(int axis) {
        return number(this.fields[axis].text());
    }

    private double limit(int axis, double v) {
        double inside = Maths.clamp(v, -LIMIT, LIMIT);
        return axis == 1 ? clampY(inside, this.yMin, this.yMax) : inside;
    }

    private String format(double v) {
        String s = Fmt.fixed(v + 0D, this.decimals);
        return s.equals("-0") ? "0" : s;
    }

    private void set(int axis, double v) {
        double scale = Math.pow(10, this.decimals);
        this.fields[axis].setText(format(limit(axis, Math.floor(v * scale + 1e-9) / scale)));
    }

    private void typed(int axis, String text) {
        String clean = clean(text, this.decimals);
        if (!clean.equals(text)) {
            this.fields[axis].setText(clean);
        }
        emit();
    }

    private void emit() {
        this.onChange.accept(value());
    }

    private void fillHere() {
        if (this.here == null) {
            return;
        }
        Value at = this.here.get();
        if (at == null) {
            return;
        }
        value(new Value(at.x(), at.y(), at.z(), false));
        emit();
    }

    private void nudge(int axis, double by) {
        double step = Math.pow(10, -this.decimals);
        double next = Math.round(read(axis) / step) * step + by;
        set(axis, next);
        emit();
    }

    private int focusedAxis() {
        for (int i = 0; i < 3; i++) {
            if (this.fields[i].focused) {
                return i;
            }
        }
        return -1;
    }

    private void only(Field field) {
        for (Field f : this.fields) {
            f.focused = f == field;
        }
    }

    @Override
    void words(Words words) {
        this.words = words;
        this.relativeButton.tooltip(text(null, words, "minuit.coords.relative", "Relative to you (~)"));
        this.hereButton.tooltip(text(null, words, "minuit.coords.here.tip", "Fill in your position"));
    }

    /** Places the fields, labels and buttons for the current box. */
    private void layout() {
        int labelW = 0;
        for (String axis : AXES) {
            labelW = Math.max(labelW, TextMeasure.width(axis) - 1);
        }
        labelW += LABEL_GAP;
        int right = this.x + this.w;
        if (this.here != null) {
            String label = text(this.hereLabel, this.words, "minuit.coords.here", "Here");
            int bw = Math.min(this.w / 3, TextMeasure.width(label) - 1 + 12);
            right -= bw;
            place(this.hereButton, right, bw);
            this.hereButton.label = label;
            right -= PART_GAP;
        }
        if (this.relativeToggle) {
            int bw = Math.max(this.h, 14);
            right -= bw;
            place(this.relativeButton, right, bw);
            this.relativeButton.selected = this.relative;
            right -= PART_GAP;
        }
        int fieldsW = right - this.x + PART_GAP - 3 * labelW - 2 * GAP;
        if (this.here == null && !this.relativeToggle) {
            fieldsW = this.w - 3 * labelW - 2 * GAP;
        }
        int each = Math.max(0, fieldsW / 3);
        int at = this.x;
        for (int i = 0; i < 3; i++) {
            int fw = i == 2 ? Math.max(0, fieldsW - 2 * each) : each;
            Field f = this.fields[i];
            f.labelX = at;
            f.x = at + labelW;
            f.y = this.y;
            f.w = fw;
            f.h = this.h;
            f.active = this.active;
            at += labelW + fw + GAP;
        }
    }

    private void place(ButtonComponent button, int bx, int bw) {
        button.x = bx;
        button.y = this.y;
        button.w = bw;
        button.h = this.h;
        button.active = this.active;
    }

    private List<BaseComponent> parts() {
        List<BaseComponent> parts = new ArrayList<>(List.of(this.fields));
        if (this.relativeToggle) {
            parts.add(this.relativeButton);
        }
        if (this.here != null) {
            parts.add(this.hereButton);
        }
        return parts;
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        layout();
        if (!this.focused) {
            only(null);
        }
        Palette p = t.palette();
        int textY = RowLook.textY(this.y, this.h) + this.h % 2;
        int ink = this.active ? FormLook.soft(p) : p.disabledText();
        for (int i = 0; i < 3; i++) {
            Field f = this.fields[i];
            if (!f.focused) {
                String clean = format(limit(i, read(i)));
                if (!clean.equals(f.text())) {
                    f.setText(clean);
                }
            }
            d.text(AXES[i], f.labelX, textY, ink);
            f.draw(d, t, mx, my, this.delta);
        }
        if (this.relativeToggle) {
            this.relativeButton.draw(d, t, mx, my, this.delta);
        }
        if (this.here != null) {
            this.hereButton.draw(d, t, mx, my, this.delta);
        }
    }

    @Override
    public List<BaseComponent> stops() {
        layout();
        List<BaseComponent> out = new ArrayList<>();
        if (!this.active || !this.visible) {
            return out;
        }
        for (BaseComponent part : parts()) {
            out.addAll(part.stops());
        }
        return out;
    }

    @Override
    protected boolean takesFocus() {
        return true;
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        for (BaseComponent part : parts()) {
            List<String> lines = part.tooltipAt(mx, my);
            if (lines != null) {
                return lines;
            }
        }
        return super.tooltipAt(mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !isOver(mx, my)) {
            return false;
        }
        layout();
        only(null);
        for (BaseComponent part : parts()) {
            if (part.mouseClicked(mx, my, button)) {
                if (part instanceof Field f) {
                    only(f);
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean any = false;
        for (BaseComponent part : parts()) {
            any |= part.mouseReleased(mx, my, button);
        }
        return any;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        int axis = focusedAxis();
        return axis >= 0 && this.fields[axis].mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        int axis = focusedAxis();
        if (axis < 0) {
            return false;
        }
        switch (key) {
            case UP -> nudge(axis, shift ? 10 : 1);
            case DOWN -> nudge(axis, shift ? -10 : -1);
            case PASTE -> {
                Value pasted = parse(TextFieldComponent.clipboard.get(), this.decimals);
                if (pasted == null) {
                    return this.fields[axis].keyPressed(key, shift);
                }
                value(pasted);
                emit();
            }
            default -> {
                return this.fields[axis].keyPressed(key, shift);
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        int axis = focusedAxis();
        return axis >= 0 && this.fields[axis].charTyped(codepoint);
    }

    /** One axis: a text field that tells its owner when the keyboard lands on it, so only one holds it. */
    private final class Field extends TextFieldComponent {
        int labelX;

        Field(String name, Consumer<String> onChange) {
            super(new Box(name, 0, 0, 0, 0), "", onChange);
        }

        @Override
        protected void enter(boolean forward) {
            only(this);
        }
    }
}
