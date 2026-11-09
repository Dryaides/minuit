package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * The list of what is wrong in a form, above its Save button: each line names a {@link FormFieldComponent} and goes
 * to it on a click. It draws nothing while nothing is wrong.
 *
 * <pre>{@code
 * FormSummaryComponent summary = add(new FormSummaryComponent(at(new Box("s", 10, 10, 160, 50)), this, List.of(name, port))
 *         .title(n -> words.plural("mymod.problems", n)));
 * add(new ButtonComponent(at(new Box("save", 10, 66, 80, 20)), "Save", () -> { if (summary.validateAll()) save(); }));
 * }</pre>
 *
 * <p>Up and down walk the lines and Enter goes to that field. Lines past the box's height sum up as "+N". It keeps up
 * on its own as the fields' errors change, once per tick.
 */
public class FormSummaryComponent extends BaseComponent {
    /** The height of the title and each line. */
    public static final int LINE = 11;

    private static final int PAD = 4;
    private static final int FACE_ALPHA = 0x30;
    private static final int HOVER_ALPHA = 0x28;
    private static final int ALERT_W = 7;
    private static final int ALERT_GAP = 3;
    private static final String[] ALERT = {
            "...#...",
            "..#.#..",
            "..#.#..",
            ".#...#.",
            ".#.#.#.",
            "#.....#",
            "###.###"};

    private final PanelComponent host;
    private final List<FormFieldComponent> fields = new ArrayList<>();
    private final List<FormFieldComponent> wrong = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();
    private static final String TITLE_KEY = "minuit.form.problems";
    /** The mod's own, or null for the lang entry (English when the lang file lacks it). */
    private IntFunction<String> title;
    private Words words;
    private String titleText = "";
    private int cursor;
    private int moreFor = -1;
    private String moreLabel = "";
    private boolean keyed;

    @Override
    void words(Words words) {
        this.words = words;
        refresh();
    }

    private String titleFor(int n) {
        if (title != null) {
            return title.apply(n);
        }
        if (words != null) {
            String found = words.plural(TITLE_KEY, n, n);
            if (!found.equals(TITLE_KEY)) {
                return found;
            }
        }
        return n == 1 ? "1 problem to fix" : n + " problems to fix";
    }

    public FormSummaryComponent(Box box, PanelComponent host, Collection<FormFieldComponent> fields) {
        super(box);
        this.host = Objects.requireNonNull(host, "host");
        if (fields != null) {
            this.fields.addAll(fields);
        }
    }

    /** The title for so many problems. From the lang file ({@code minuit.form.problems}) by default. */
    public FormSummaryComponent title(IntFunction<String> title) {
        if (title != null) {
            this.title = title;
            refresh();
        }
        return this;
    }

    public FormSummaryComponent add(FormFieldComponent field) {
        fields.add(field);
        refresh();
        return this;
    }

    /** Validates every field. True when all is well. Goes to no field by itself: call {@link #focusFirst()} for that. */
    public boolean validateAll() {
        boolean ok = true;
        for (FormFieldComponent field : fields) {
            ok &= field.validate();
        }
        refresh();
        return ok;
    }

    /** Gives the focus to the first field with an error. */
    public void focusFirst() {
        if (!wrong.isEmpty()) {
            wrong.get(0).focusControl();
        }
    }

    /** The lines shown, "Label: message", for a script or a test. */
    public List<String> errors() {
        return List.copyOf(rows);
    }

    /** The line the keys are on. */
    int cursor() {
        return cursor;
    }

    /** Where a line is, for a script or a test. */
    Box rowBox(int row) {
        return new Box("row" + row, x + PAD, rowY(row), Math.max(0, w - 2 * PAD), LINE);
    }

    private int rowY(int row) {
        return y + PAD + LINE + row * LINE;
    }

    private int rowsFit() {
        return Math.max(0, (h - 2 * PAD - LINE) / LINE);
    }

    private void refresh() {
        boolean same = true;
        int at = 0;
        for (FormFieldComponent field : fields) {
            if (field.hasError()) {
                same = same && at < wrong.size() && wrong.get(at) == field && rows.get(at).equals(textOf(field));
                at++;
            }
        }
        if (same && at == wrong.size()) {
            return;
        }
        wrong.clear();
        rows.clear();
        for (FormFieldComponent field : fields) {
            if (field.hasError()) {
                wrong.add(field);
                rows.add(textOf(field));
            }
        }
        titleText = titleFor(wrong.size());
        moreFor = -1;
        cursor = Math.max(0, Math.min(cursor, wrong.size() - 1));
    }

    private static String textOf(FormFieldComponent field) {
        return field.label().isEmpty() ? field.error() : field.label() + ": " + field.error();
    }

    @Override
    public void tick() {
        refresh();
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        if (wrong.isEmpty() || h < 2 * PAD + LINE) {
            return;
        }
        Palette p = t.palette();
        int alert = FormLook.alert(p);
        t.frame().plate(d, p, x, y, w, h, Shapes.alpha(p.bad(), FACE_ALPHA), FormLook.alertEdge(p));
        FormLook.pixels(d, ALERT, x + PAD, y + PAD + 1, alert);
        int left = x + PAD + ALERT_W + ALERT_GAP;
        d.textFit(titleText, left, y + PAD, x + w - PAD - left, alert);
        int fit = rowsFit();
        int shown = wrong.size() > fit ? Math.max(0, fit - 1) : wrong.size();
        int hover = hovered && active ? rowAt(mx, my) : -1;
        int ink = active ? p.text() : p.disabledText();
        for (int i = 0; i < shown; i++) {
            int ry = rowY(i);
            if (i == hover) {
                d.fill(x + PAD - 2, ry, x + w - PAD + 2, ry + LINE, Shapes.alpha(p.text(), HOVER_ALPHA));
            }
            d.textFit(rows.get(i), left, RowLook.textY(ry, LINE), x + w - PAD - left, ink);
        }
        if (shown < wrong.size() && fit > 0) {
            if (shown != moreFor) {
                moreFor = shown;
                moreLabel = "+" + (wrong.size() - shown);
            }
            d.textFit(moreLabel, left, RowLook.textY(rowY(shown), LINE), x + w - PAD - left, Ink.soft(p, FormLook.surface(p)));
        }
    }

    private int rowAt(double mx, double my) {
        if (mx < x + PAD - 2 || mx >= x + w - PAD + 2) {
            return -1;
        }
        int row = (int) Math.floor((my - rowY(0)) / LINE);
        int fit = rowsFit();
        int shown = wrong.size() > fit ? Math.max(0, fit - 1) : wrong.size();
        return my >= rowY(0) && row < shown ? row : -1;
    }

    @Override
    public boolean isOver(double mx, double my) {
        return !wrong.isEmpty() && super.isOver(mx, my);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        int row = rowAt(mx, my);
        if (row >= 0 && TextMeasure.width(rows.get(row)) > w - 2 * PAD - ALERT_W - ALERT_GAP) {
            return List.of(rows.get(row));
        }
        return tooltip;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !active || button != 0 || !isOver(mx, my)) {
            return false;
        }
        int row = rowAt(mx, my);
        if (row >= 0) {
            BaseComponent.click();
            cursor = row;
            wrong.get(row).focusControl();
        }
        return true;
    }

    @Override
    protected boolean navigable() {
        return visible && active && settled() && !wrong.isEmpty();
    }

    @Override
    protected void enter(boolean forward) {
        keyed = true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!navigable()) {
            return false;
        }
        switch (key) {
            case UP -> cursor = Math.max(0, cursor - 1);
            case DOWN -> cursor = Math.min(wrong.size() - 1, cursor + 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean activate() {
        if (!navigable()) {
            return false;
        }
        BaseComponent.click();
        wrong.get(Math.min(cursor, wrong.size() - 1)).focusControl();
        return true;
    }

    @Override
    protected Box focusBox() {
        return keyed && !wrong.isEmpty() && cursor < rowsFit() ? rowBox(cursor) : super.focusBox();
    }

    @Override
    protected int focusCorner() {
        return 0;
    }
}
