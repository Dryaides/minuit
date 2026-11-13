package dev.dryaides.minuit;

import java.util.List;
import java.util.function.Consumer;

/**
 * A line of text the player copies: a world seed, coordinates, an invite code, a command. A click, or Enter on
 * the keyboard, puts it on the clipboard; the button then reads "Copied" for {@link #DONE_MS}. The text cannot be
 * edited.
 *
 * <pre>{@code
 * add(new CopyFieldComponent(at(new Box("seed", 10, 10, 160, 16)), Long.toString(seed)));
 * }</pre>
 *
 * <p>Text too long for the field ends in dots and shows whole as a tooltip. The clipboard is
 * {@link TextFieldComponent#clipboard}, which the host sets.
 */
public class CopyFieldComponent extends BaseComponent {

    public static final int DONE_MS = 1500;

    private static final int PAD = 4;
    private static final int BUTTON_PAD = 6;

    private String text;
    private String copyLabel;
    private String doneLabel;
    private Words words;
    private Consumer<String> onCopy = s -> { };
    private long copiedAt = -1;
    private boolean cut;
    private final ButtonComponent button = new ButtonComponent(new Box("copy", 0, 0, 0, 0), "", this::copy);

    public CopyFieldComponent(Box box, String text) {
        super(box);
        this.text = text == null ? "" : text;
    }

    public CopyFieldComponent text(String text) {
        this.text = text == null ? "" : text;
        this.copiedAt = -1;
        return this;
    }

    public String text() {
        return this.text;
    }

    /** The button's words before and after a copy, over the lang file's "Copy" and "Copied". */
    public CopyFieldComponent labels(String copy, String done) {
        this.copyLabel = copy;
        this.doneLabel = done;
        return this;
    }

    /** Runs after each copy, with the text copied. */
    public CopyFieldComponent onCopy(Consumer<String> onCopy) {
        this.onCopy = onCopy == null ? s -> { } : onCopy;
        return this;
    }

    public CopyFieldComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public CopyFieldComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    @Override
    void words(Words words) {
        this.words = words;
    }

    /** True for {@link #DONE_MS} after a copy. */
    public boolean justCopied() {
        return this.copiedAt >= 0 && clock().getAsLong() - this.copiedAt < DONE_MS;
    }

    private String label() {
        return justCopied()
                ? text(this.doneLabel, this.words, "minuit.copy.done", "Copied")
                : text(this.copyLabel, this.words, "minuit.copy.action", "Copy");
    }

    /** The button is as wide as the longer of its two words, so it does not jump on a copy. */
    private int buttonWidth() {
        String copy = text(this.copyLabel, this.words, "minuit.copy.action", "Copy");
        String done = text(this.doneLabel, this.words, "minuit.copy.done", "Copied");
        int widest = Math.max(TextMeasure.width(copy), TextMeasure.width(done)) - 1;
        return Math.min(this.w / 2, widest + 2 * BUTTON_PAD);
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        int bw = buttonWidth();
        int fieldW = this.w - bw - 1;
        frame.field(d, p, this.x, this.y, fieldW, this.h, false);
        int room = fieldW - 2 * PAD;
        this.cut = TextMeasure.width(this.text) - 1 > room;
        int textY = RowLook.textY(this.y, this.h) + this.h % 2;
        d.textFit(this.text, this.x + PAD, textY, room + 1, this.active ? frame.fieldText(p) : frame.fieldHint(p));
        this.button.x = this.x + this.w - bw;
        this.button.y = this.y;
        this.button.w = bw;
        this.button.h = this.h;
        this.button.active = this.active;
        this.button.label = label();
        this.button.draw(d, t, mx, my, this.delta);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        if (this.tooltip != null) {
            return this.tooltip;
        }
        return this.cut ? List.of(this.text) : null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !isOver(mx, my) || button != 0) {
            return false;
        }
        if (!this.button.mouseClicked(mx, my, button)) {
            activate();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return this.button.mouseReleased(mx, my, button);
    }

    @Override
    protected boolean navigable() {
        return this.active;
    }

    @Override
    protected boolean activate() {
        if (!this.active) {
            return false;
        }
        click();
        copy();
        return true;
    }

    /** The button's own click has already sounded. */
    private void copy() {
        TextFieldComponent.clipboard.set(this.text);
        this.copiedAt = clock().getAsLong();
        this.onCopy.accept(this.text);
    }
}
