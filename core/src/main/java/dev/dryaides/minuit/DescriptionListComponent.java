package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * Terms and their values, one pair per line: a mob's stats, an item's details, a server's address and version. It
 * shows, it does not take input.
 *
 * <pre>{@code
 * add(new DescriptionListComponent(at(new Box("stats", 10, 10, 140, 0)))
 *         .row("Health", "20")
 *         .row("Armor", "4")
 *         .row("Owner", playerName));
 * }</pre>
 *
 * <p>The terms take one column, as wide as the longest term up to {@link #termShare(float)} of the box; a cut term
 * or value ends in dots and shows whole as a tooltip. A box of height 0 takes the rows' height. {@link #lines(boolean)}
 * draws a rule between rows.
 */
public class DescriptionListComponent extends BaseComponent {

    public static final int ROW = 11;
    /** A row with a rule under it: the rule keeps clear of the letters' descenders. */
    public static final int RULED_ROW = 13;

    private static final int GAP = 6;

    private record Row(String term, String value) { }

    private final int givenH;
    private final List<Row> rows = new ArrayList<>();
    private float termShare = 0.5F;
    private boolean lines;

    public DescriptionListComponent(Box box) {
        super(box);
        this.givenH = box.h();
        fit();
    }

    /** Adds a line; a null value reads as an empty one. */
    public DescriptionListComponent row(String term, String value) {
        this.rows.add(new Row(term == null ? "" : term, value == null ? "" : value));
        fit();
        return this;
    }

    /** Changes the value of the first line with {@code term}; no line is added when none has it. */
    public DescriptionListComponent value(String term, String value) {
        for (int i = 0; i < this.rows.size(); i++) {
            if (this.rows.get(i).term().equals(term)) {
                this.rows.set(i, new Row(term, value == null ? "" : value));
                return this;
            }
        }
        return this;
    }

    public DescriptionListComponent clear() {
        this.rows.clear();
        fit();
        return this;
    }

    /** The most of the box's width the terms may take, from 0.2 to 0.8; a half by default. */
    public DescriptionListComponent termShare(float share) {
        this.termShare = Math.max(0.2F, Math.min(0.8F, share));
        return this;
    }

    public DescriptionListComponent lines(boolean lines) {
        this.lines = lines;
        fit();
        return this;
    }

    public DescriptionListComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public DescriptionListComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    public int naturalHeight() {
        return this.rows.size() * rowH();
    }

    private int rowH() {
        return this.lines ? RULED_ROW : ROW;
    }

    private void fit() {
        if (this.givenH <= 0) {
            this.h = naturalHeight();
        }
    }

    private int termWidth() {
        int widest = 0;
        for (Row row : this.rows) {
            widest = Math.max(widest, TextMeasure.width(row.term()) - 1);
        }
        return Math.min(widest, Math.round(this.w * this.termShare));
    }

    private int valueLeft() {
        return this.x + termWidth() + GAP;
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        fit();
        Palette p = t.palette();
        int termInk = this.active ? FormLook.soft(p) : p.disabledText();
        int valueInk = this.active ? p.text() : p.disabledText();
        int rule = Shapes.alpha(FormLook.edge(p), 0x80);
        int termW = termWidth();
        int valueX = valueLeft();
        int valueW = this.x + this.w - valueX;
        int rowH = rowH();
        int shown = Math.min(this.rows.size(), this.h / rowH);
        for (int i = 0; i < shown; i++) {
            Row row = this.rows.get(i);
            int ry = this.y + i * rowH;
            int textY = RowLook.textY(this.lines ? ry + 1 : ry, ROW) + ROW % 2;
            d.textFit(row.term(), this.x, textY, termW + 1, termInk);
            d.textFit(row.value(), valueX, textY, valueW + 1, valueInk);
            if (this.lines && i < shown - 1) {
                d.fill(this.x, ry + rowH - 1, this.x + this.w, ry + rowH, rule);
            }
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        if (this.tooltip != null) {
            return this.tooltip;
        }
        int i = (int) Math.floor((my - this.y) / rowH());
        if (i < 0 || i >= this.rows.size()) {
            return null;
        }
        Row row = this.rows.get(i);
        boolean onTerm = mx < valueLeft();
        String text = onTerm ? row.term() : row.value();
        int room = onTerm ? termWidth() : this.x + this.w - valueLeft();
        return TextMeasure.width(text) - 1 > room ? List.of(text) : null;
    }
}
