package dev.dryaides.minuit;

/**
 * A mini panel on the HUD: an optional title over a rule, then up to {@code maxLines} lines of text, in the theme's
 * panel. It sizes itself to its text between a minimum and a maximum width; a line too long is cut with "...".
 * Lines are measured only when one changes.
 *
 * <pre>{@code
 * HudPanel quest = HudLayer.main().add(new HudPanel("mymod", "quest", HudAnchor.MIDDLE_LEFT, 4).title("Quest"));
 * quest.line(0, "Gather 12 oak logs").line(1, "7 / 12");
 * }</pre>
 */
@Incubating
public class HudPanel extends HudPart {

    private static final int PAD = 4;
    private static final int LINE = 10;

    private String title;
    private final String[] lines;
    private final String[] shownLines;
    private String shownTitle;
    private int minWidth = 60;
    private int maxWidth = 160;
    private boolean measure = true;

    public HudPanel(String modId, String id, HudAnchor anchor, int maxLines) {
        super(modId, id, anchor, 60, 2 * PAD + 8);
        this.lines = new String[Math.max(1, maxLines)];
        this.shownLines = new String[this.lines.length];
    }

    public HudPanel title(String words) {
        if (words == null ? this.title != null : !words.equals(this.title)) {
            this.title = words;
            this.measure = true;
        }
        return this;
    }

    /** Sets line {@code index}; null hides it. Lines past {@code maxLines} are ignored. */
    public HudPanel line(int index, String words) {
        if (index < 0 || index >= this.lines.length) {
            return this;
        }
        String was = this.lines[index];
        if (words == null ? was != null : !words.equals(was)) {
            this.lines[index] = words;
            this.measure = true;
        }
        return this;
    }

    /** Width bounds in GUI pixels, panel included. */
    public HudPanel widths(int min, int max) {
        this.minWidth = min;
        this.maxWidth = Math.max(min, max);
        this.measure = true;
        return this;
    }

    @Override
    protected void update(Draw d, float partial) {
        if (fontChanged()) {
            this.measure = true;
        }
        if (!this.measure) {
            return;
        }
        this.measure = false;
        int room = this.maxWidth - 2 * PAD;
        int widest = 0;
        this.shownTitle = this.title == null ? null : d.ellipsize(this.title, room);
        if (this.shownTitle != null) {
            widest = d.width(this.shownTitle);
        }
        int count = 0;
        for (int i = 0; i < this.lines.length; i++) {
            this.shownLines[i] = this.lines[i] == null ? null : d.ellipsize(this.lines[i], room);
            if (this.shownLines[i] != null) {
                widest = Math.max(widest, d.width(this.shownLines[i]));
                count++;
            }
        }
        int height = PAD + (this.shownTitle != null ? LINE + 3 : 0) + Math.max(1, count) * LINE - 2 + PAD;
        size(Math.max(this.minWidth, Math.min(this.maxWidth, widest + 2 * PAD)), height);
    }

    @Override
    protected void draw(Draw d, Theme theme, int x, int y, float partial) {
        Palette p = theme.palette();
        theme.frame().panel(d, p, x, y, this.w, this.h);
        int left = x + PAD;
        int top = y + PAD;
        if (this.shownTitle != null) {
            d.text(this.shownTitle, left, top, p.text());
            top += LINE;
            d.fill(left, top - 1, x + this.w - PAD, top, p.surfaceEdge());
            top += 3;
        }
        for (String line : this.shownLines) {
            if (line != null) {
                d.text(line, left, top, p.textSoft());
                top += LINE;
            }
        }
    }
}
