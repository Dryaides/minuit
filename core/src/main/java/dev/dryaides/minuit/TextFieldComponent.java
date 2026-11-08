package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * A single line of text the player types: a cursor, a selection by mouse or by Shift and the
 * arrows, a placeholder while it is empty, a length limit. It takes the keyboard when clicked and
 * gives it back on a click elsewhere or on Escape ({@link PanelComponent} routes the keys).
 */
public class TextFieldComponent extends BaseComponent {

    /** The system clipboard as the kit sees it; the host plugs in the game's. */
    public interface Clipboard {
        String get();

        void set(String text);
    }

    /** Set by the screen host; until then copy and paste stay inside the game session. */
    public static volatile Clipboard clipboard = new Clipboard() {
        private String text = "";

        @Override
        public String get() {
            return this.text;
        }

        @Override
        public void set(String text) {
            this.text = text;
        }
    };

    private static final int PAD = 4;

    private final Consumer<String> onChange;
    private String text;
    private String placeholder = "";
    /** Half a blink period of the cursor. */
    public static final long BLINK_MS = 500;

    private int maxLength = 256;
    private IntPredicate filter = c -> c >= 32 && c != 127;
    private Consumer<String> onSubmit = text -> {
    };
    private int cursor;
    /** The other end of the selection; equal to {@link #cursor} when nothing is selected. */
    private int anchor;
    /** Width in pixels of the text scrolled out on the left, so the cursor stays in view. */
    private int shift;
    private boolean selecting;
    /** Pixel width of each prefix of the text, measured at the last draw, to place the cursor under the mouse. */
    private int[] prefixWidths = {0};
    /** When the cursor last moved or the field took the keyboard: the blink starts lit from there. */
    private long blinkFrom;
    private int blinkCursor = -1;

    public TextFieldComponent(Box box, String initial, Consumer<String> onChange) {
        super(box);
        this.text = initial;
        this.onChange = onChange;
        this.cursor = initial.length();
        this.anchor = this.cursor;
    }

    public TextFieldComponent placeholder(String value) {
        this.placeholder = value;
        return this;
    }

    public TextFieldComponent maxLength(int value) {
        this.maxLength = value;
        if (this.text.length() > value) {
            set(this.text.substring(0, value));
        }
        return this;
    }

    /** Which characters may be typed or pasted, as code points. */
    public TextFieldComponent filter(IntPredicate value) {
        this.filter = value;
        return this;
    }

    /** Called with the text on Enter. */
    public TextFieldComponent onSubmit(Consumer<String> value) {
        this.onSubmit = value;
        return this;
    }

    public String text() {
        return this.text;
    }

    /** Replaces the text without calling the change listener, the cursor at its end. */
    public void setText(String value) {
        this.text = value.length() > this.maxLength ? value.substring(0, this.maxLength) : value;
        this.cursor = this.text.length();
        this.anchor = this.cursor;
    }

    /** The selection as [start, end), empty when start equals end. */
    public int[] selection() {
        return new int[]{Math.min(this.cursor, this.anchor), Math.max(this.cursor, this.anchor)};
    }

    public int cursor() {
        return this.cursor;
    }

    /** Moves the cursor; {@code select} keeps the other end of the selection where it was. */
    public void moveCursor(int to, boolean select) {
        this.cursor = Maths.clamp(to, 0, this.text.length());
        if (!select) {
            this.anchor = this.cursor;
        }
    }

    @Override
    protected boolean takesFocus() {
        return true;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        theme.frame().field(d, p, this.x, this.y, this.w, this.h, this.focused);
        int ink = theme.frame().fieldText(p);
        int[] widths = new int[this.text.length() + 1];
        for (int i = 1; i <= this.text.length(); i++) {
            widths[i] = d.width(this.text.substring(0, i));
        }
        this.prefixWidths = widths;
        int left = this.x + PAD;
        int room = this.w - 2 * PAD;
        int ty = this.y + (this.h - 8) / 2;
        if (this.text.isEmpty() && !this.focused) {
            d.textFit(this.placeholder, left, ty, room, theme.frame().fieldHint(p));
            return;
        }
        int cursorX = widths[this.cursor];
        if (cursorX - this.shift > room - 1) {
            this.shift = cursorX - room + 1;
        } else if (cursorX < this.shift) {
            this.shift = cursorX;
        }
        this.shift = Maths.clamp(this.shift, 0, Math.max(0, widths[this.text.length()] - room + 1));
        int origin = left - this.shift;
        d.scissor(left, this.y + 1, left + room, this.y + this.h - 1);
        int[] sel = selection();
        if (this.focused && sel[0] < sel[1]) {
            d.fill(Math.max(left, origin + widths[sel[0]]), ty - 1, Math.min(left + room, origin + widths[sel[1]]), ty + 9,
                    p.primary());
            d.text(this.text.substring(0, sel[0]), origin, ty, ink);
            d.text(this.text.substring(sel[0], sel[1]), origin + widths[sel[0]], ty, p.primaryText());
            d.text(this.text.substring(sel[1]), origin + widths[sel[1]], ty, ink);
        } else {
            d.text(this.text, origin, ty, ink);
        }
        if (this.focused && cursorLit()) {
            d.fill(origin + cursorX, ty - 1, origin + cursorX + 1, ty + 9, ink);
        }
        if (!this.focused) {
            this.blinkCursor = -1;
        }
        d.endScissor();
    }

    /** The character boundary nearest to a mouse x. */
    private int indexAt(double mx) {
        double local = mx - (this.x + PAD) + this.shift;
        int[] widths = this.prefixWidths;
        int best = 0;
        for (int i = 1; i < widths.length && i <= this.text.length(); i++) {
            if (Math.abs(widths[i] - local) < Math.abs(widths[best] - local)) {
                best = i;
            }
        }
        return best;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || button != 0 || !isOver(mx, my)) {
            return false;
        }
        moveCursor(indexAt(mx), false);
        this.selecting = true;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!this.selecting) {
            return false;
        }
        moveCursor(indexAt(mx), true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.selecting = false;
        return false;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        int[] sel = selection();
        switch (key) {
            case LEFT -> moveCursor(this.cursor - 1, shift);
            case RIGHT -> moveCursor(this.cursor + 1, shift);
            case HOME -> moveCursor(0, shift);
            case END -> moveCursor(this.text.length(), shift);
            case SELECT_ALL -> {
                this.anchor = 0;
                this.cursor = this.text.length();
            }
            case BACKSPACE -> replace(sel[0] < sel[1] ? sel[0] : Math.max(0, this.cursor - 1), sel[1], "");
            case DELETE -> replace(sel[0], sel[0] < sel[1] ? sel[1] : Math.min(this.text.length(), this.cursor + 1), "");
            case COPY -> {
                if (sel[0] < sel[1]) {
                    clipboard.set(this.text.substring(sel[0], sel[1]));
                }
            }
            case CUT -> {
                if (sel[0] < sel[1]) {
                    clipboard.set(this.text.substring(sel[0], sel[1]));
                    replace(sel[0], sel[1], "");
                }
            }
            case PASTE -> insert(clipboard.get());
            case ENTER -> this.onSubmit.accept(this.text);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        if (!this.filter.test(codepoint)) {
            return false;
        }
        insert(new String(Character.toChars(codepoint)));
        return true;
    }

    /** Types {@code typed} over the selection, keeping only allowed characters, up to the length limit. */
    private void insert(String typed) {
        StringBuilder kept = new StringBuilder();
        typed.codePoints().filter(this.filter).forEach(kept::appendCodePoint);
        int[] sel = selection();
        int room = this.maxLength - (this.text.length() - (sel[1] - sel[0]));
        String added = kept.length() > room ? kept.substring(0, Math.max(0, room)) : kept.toString();
        replace(sel[0], sel[1], added);
    }

    private boolean cursorLit() {
        long now = BaseComponent.clock.getAsLong();
        if (this.blinkCursor != this.cursor) {
            this.blinkCursor = this.cursor;
            this.blinkFrom = now;
        }
        return BaseComponent.reducedMotion || (now - this.blinkFrom) / BLINK_MS % 2 == 0;
    }

    private void replace(int start, int end, String with) {
        if (start == end && with.isEmpty()) {
            return;
        }
        this.text = this.text.substring(0, start) + with + this.text.substring(end);
        this.cursor = start + with.length();
        this.anchor = this.cursor;
        this.onChange.accept(this.text);
    }

    private void set(String value) {
        this.text = value;
        this.cursor = Math.min(this.cursor, value.length());
        this.anchor = this.cursor;
        this.onChange.accept(value);
    }
}
