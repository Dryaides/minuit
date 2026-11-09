package dev.dryaides.minuit;

import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * A label, a required mark, a line of help and an error message around any other widget.
 *
 * <pre>{@code
 * TextFieldComponent name = add(new TextFieldComponent(at(new Box("name", 0, 0, 0, 16)), "", v -> { }));
 * FormFieldComponent field = add(new FormFieldComponent(at(new Box("f", 10, 10, 160, 0)), this, "World name", name)
 *         .required(true)
 *         .description(words.get("mymod.name_help"))
 *         .validator(() -> name.text().isBlank() ? words.get("mymod.name_needed") : null));
 * // Later, on Save:
 * if (field.validate()) { save(); }
 * }</pre>
 *
 * <p>You add the widget to the panel yourself, so that it keeps its own place in the keyboard's order; this one only
 * moves it: it takes the field's x and width and sits under the label, keeping its own height. A click on the label
 * gives the widget the focus. Disabling or hiding the field, through {@link #active(boolean)} and {@link #show(boolean)},
 * does the same to the widget.
 *
 * <p>A height of 0 in the box means "as high as the content": {@link #height()} tells it, and the height changes when
 * an error comes and goes, unless {@link #reserveError(boolean)} keeps a line for it. {@link #onResize(IntConsumer)}
 * tells you when to move what is beneath. After the first {@link #validate()}, the validator runs again on each
 * tick, so the error goes as soon as the player has fixed it: as {@link FormSummaryComponent} expects.
 */
public class FormFieldComponent extends BaseComponent {
    private static final int LABEL_H = 11;
    private static final int LINE_H = 10;
    private static final int MESSAGE_GAP = 2;
    private static final String MARK = " *";
    private static final String[] ALERT = {
            "...#...",
            "..#.#..",
            "..#.#..",
            ".#...#.",
            ".#.#.#.",
            "#.....#",
            "###.###"};
    private static final int ALERT_W = 7;
    private static final int ALERT_GAP = 3;

    private final PanelComponent host;
    private final BaseComponent control;
    private final int givenH;
    private String label;
    private boolean required;
    private String description = "";
    private String error = "";
    private boolean reserve;
    private Supplier<String> validator;
    private boolean checked;
    private IntConsumer onResize;

    private List<String> descriptionLines = List.of();
    private List<String> errorLines = List.of();
    private int wrappedW = -1;
    private boolean labelCut;

    /**
     * @param box where the field goes; a height of 0 fits the content
     * @param host the panel it is added to
     * @param label what the widget asks for; may be empty, and then takes no room
     * @param control the widget, added to the panel by you; may be null, for a block of text
     */
    public FormFieldComponent(Box box, PanelComponent host, String label, BaseComponent control) {
        super(box);
        this.host = Objects.requireNonNull(host, "host");
        this.control = control;
        this.label = label == null ? "" : label;
        this.givenH = box.h();
        layout();
    }

    // ---- setup ----

    public FormFieldComponent label(String label) {
        this.label = label == null ? "" : label;
        return this;
    }

    /** Shows the mark after the label. It only marks: your validator says what counts as filled. */
    public FormFieldComponent required(boolean required) {
        this.required = required;
        return this;
    }

    public boolean isRequired() {
        return required;
    }

    /** A quiet line under the widget, which wraps. Null or empty for none. */
    public FormFieldComponent description(String description) {
        this.description = description == null ? "" : description;
        wrappedW = -1;
        layout();
        return this;
    }

    /** Says what is wrong now: null means nothing. It runs on {@link #validate()}, then on each tick. */
    public FormFieldComponent validator(Supplier<String> validator) {
        this.validator = validator;
        return this;
    }

    /** Keeps a line for the error, so that nothing moves when it shows. Off by default. */
    public FormFieldComponent reserveError(boolean reserve) {
        this.reserve = reserve;
        layout();
        return this;
    }

    /** Called with the new height when an automatic height changes. */
    public FormFieldComponent onResize(IntConsumer onResize) {
        this.onResize = onResize;
        return this;
    }

    /** Disables the field and its widget. */
    public FormFieldComponent active(boolean active) {
        this.active = active;
        if (control != null) {
            control.active = active;
        }
        return this;
    }

    /** Hides or shows the field and its widget. */
    public FormFieldComponent show(boolean show) {
        visible = show;
        if (control != null) {
            control.visible = show;
        }
        return this;
    }

    @Override
    public FormFieldComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- content ----

    public String label() {
        return label;
    }

    /** The widget this field is around. */
    public BaseComponent control() {
        return control;
    }

    /** The message showing, or an empty text. */
    public String error() {
        return error;
    }

    public boolean hasError() {
        return !error.isEmpty();
    }

    /** Shows a message of your own, from a server's answer say. Null or empty takes it away. */
    public void setError(String message) {
        String clean = message == null ? "" : message;
        if (!clean.equals(error)) {
            error = clean;
            wrappedW = -1;
            layout();
        }
    }

    /**
     * Runs the validator and shows what it says. True when all is well, also for a field with no validator. A disabled
     * or hidden field is not checked: it drops its error and passes, as the player cannot fix it.
     */
    public boolean validate() {
        checked = true;
        if (!active || !visible) {
            setError(null);
        } else if (validator != null) {
            setError(validator.get());
        }
        return error.isEmpty();
    }

    /** Takes back an error and stops checking until the next {@link #validate()}. */
    public void reset() {
        checked = false;
        setError(null);
    }

    /** Puts the keyboard on the widget, as Tab would. */
    public void focusControl() {
        if (control != null && control.visible && control.active) {
            host.cursorTo(control);
        }
    }

    /** The height of the whole field. */
    public int height() {
        return contentH();
    }

    // ---- layout ----

    private int labelH() {
        return label.isEmpty() ? 0 : LABEL_H;
    }

    private int controlH() {
        return control == null || !control.visible ? 0 : control.h;
    }

    private int messageW() {
        return Math.max(1, w);
    }

    private void wrap() {
        if (wrappedW == w) {
            return;
        }
        wrappedW = w;
        descriptionLines = description.isEmpty() ? List.of() : TextMeasure.wrapAll(description, messageW());
        errorLines = error.isEmpty() ? List.of() : TextMeasure.wrapAll(error, Math.max(1, messageW() - ALERT_W - ALERT_GAP));
    }

    private int contentH() {
        wrap();
        int errorH = errorLines.isEmpty() ? (reserve ? LINE_H : 0) : errorLines.size() * LINE_H;
        int under = descriptionLines.size() * LINE_H + errorH;
        return labelH() + controlH() + (under > 0 ? MESSAGE_GAP + under : 0);
    }

    private void layout() {
        if (control != null) {
            control.x = x;
            control.y = y + labelH();
            control.w = w;
        }
        if (givenH <= 0) {
            int height = contentH();
            if (height != h) {
                h = height;
                if (onResize != null) {
                    onResize.accept(h);
                }
            }
        }
    }

    // ---- drawing ----

    /** Only the label's line takes the pointer: the rest is the widget's or only words. */
    @Override
    public boolean isOver(double mx, double my) {
        return visible && settled() && !label.isEmpty() && mx >= x && mx < x + w && my >= y && my < y + LABEL_H;
    }

    @Override
    public void tick() {
        if (checked && validator != null && active && visible) {
            setError(validator.get());
        }
        layout();
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        layout();
        Palette p = t.palette();
        int top = y;
        if (!label.isEmpty()) {
            drawLabel(d, p, top);
            top += LABEL_H;
        }
        if (control != null) {
            if (control.visible) {
                if (!error.isEmpty()) {
                    t.frame().ringAround(d, p, control.x, control.y, control.w, control.h, 1, FormLook.alertEdge(p));
                }
                top += control.h;
            }
        }
        int soft = active ? Ink.soft(p, FormLook.surface(p)) : p.disabledText();
        top += MESSAGE_GAP;
        for (int i = 0; i < descriptionLines.size(); i++) {
            d.textFit(descriptionLines.get(i), x, top + 1, w, soft);
            top += LINE_H;
        }
        if (!errorLines.isEmpty()) {
            int alert = active ? FormLook.alert(p) : p.disabledText();
            FormLook.pixels(d, ALERT, x, top + 1, alert);
            for (int i = 0; i < errorLines.size(); i++) {
                d.textFit(errorLines.get(i), x + ALERT_W + ALERT_GAP, top + 1, w - ALERT_W - ALERT_GAP, alert);
                top += LINE_H;
            }
        }
    }

    private void drawLabel(Draw d, Palette p, int top) {
        int mark = required ? TextMeasure.width(MARK) : 0;
        int room = Math.max(0, w - mark);
        labelCut = TextMeasure.width(label) > room;
        int ink = active ? p.text() : p.disabledText();
        d.textFit(label, x, top + 1, room, ink);
        if (required) {
            int after = labelCut ? x + room : x + TextMeasure.width(label);
            d.text(MARK, after, top + 1, active ? FormLook.alert(p) : p.disabledText());
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        if (tooltip != null) {
            return tooltip;
        }
        return labelCut ? List.of(label) : null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !isOver(mx, my) || !active) {
            return false;
        }
        focusControl();
        return true;
    }
}
