package dev.dryaides.minuit;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * A short code typed into one box per character: a pairing code, a server's invite.
 *
 * <pre>{@code
 * add(new OtpInputComponent(at(new Box("code", 10, 10, 120, 18)), 6, code -> check(code)).groupEvery(3));
 * }</pre>
 *
 * <p>The value is a plain string with no holes: a box can only be filled once those before it are. Typing
 * fills the box the caret is on and moves on. Backspace clears the box the caret is on; on an empty box it goes back
 * one and clears that one, so holding it erases everything. Delete takes the character out and closes the gap. The
 * arrows, Home and End move. Ctrl+A marks all, for the next key to replace. Paste fills from the caret, leaving out
 * what the filter refuses, so "123 456" fills six boxes. Copy copies the code.
 *
 * <p>Digits only by default. {@link #onComplete(Consumer)} runs when the last box fills, once per code.
 */
public class OtpInputComponent extends BaseComponent {
    private static final int GAP = 2;
    private static final int GROUP_GAP = 9;
    private static final int DASH_W = 3;
    private static final int MIN_BOX = 7;
    private static final int MASK_DOT = 3;
    private static final int MAX_LENGTH = 32;
    private static final int MARK_ALPHA = 0x80;

    private final int length;
    private final Consumer<String> onChange;
    private final String[] cells;
    private int count;
    private int caret;
    private boolean allMarked;
    private long caretSince;
    private IntPredicate filter = c -> c >= '0' && c <= '9';
    private boolean mask;
    private boolean upper;
    private boolean invalid;
    private int groupEvery;
    private Consumer<String> onComplete;
    private String lastComplete;
    private String value = "";

    /**
     * @param box the row of boxes; a box is never wider than the row is high
     * @param length how many characters, up to 32
     * @param onChange called with the whole text when the player changes it; may be null
     */
    public OtpInputComponent(Box box, int length, Consumer<String> onChange) {
        super(box);
        this.length = Math.max(1, Math.min(MAX_LENGTH, length));
        this.cells = new String[this.length];
        this.onChange = onChange;
    }

    // ---- setup ----

    /** Which characters a box takes. Digits by default. */
    public OtpInputComponent filter(IntPredicate filter) {
        this.filter = filter == null ? c -> true : filter;
        return this;
    }

    /** Letters and digits in place of digits only, shown upper case. */
    public OtpInputComponent alphanumeric() {
        upper = true;
        return filter(c -> Character.isLetterOrDigit(c) && c < 0x80);
    }

    /** True to turn letters to upper case as they come in. Off by default. */
    public OtpInputComponent uppercase(boolean upper) {
        this.upper = upper;
        return this;
    }

    /** Shows a dot in place of each character. Off by default. */
    public OtpInputComponent mask(boolean mask) {
        this.mask = mask;
        return this;
    }

    /** A dash and a wider gap after every so many boxes: {@code 3} for 123-456. 0, the default, for none. */
    public OtpInputComponent groupEvery(int groupEvery) {
        this.groupEvery = Math.max(0, groupEvery);
        return this;
    }

    public OtpInputComponent onComplete(Consumer<String> onComplete) {
        this.onComplete = onComplete;
        return this;
    }

    /** Marks the boxes as wrong, in red, until you say otherwise: for a code that was refused. */
    public OtpInputComponent invalid(boolean invalid) {
        this.invalid = invalid;
        return this;
    }

    public boolean invalid() {
        return invalid;
    }

    public OtpInputComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public OtpInputComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- content ----

    /** The code so far. */
    public String value() {
        return value;
    }

    /** Sets the code, without calling back. What the filter refuses is left out, what does not fit is cut. */
    public void setValue(String text) {
        count = 0;
        caret = 0;
        allMarked = false;
        if (text != null) {
            fill(text);
        }
        rebuildValue();
        lastComplete = complete() ? value : null;
        caret = caretEnd();
    }

    /** Empties the boxes, without calling back. */
    public void clear() {
        setValue("");
    }

    public int length() {
        return length;
    }

    public boolean complete() {
        return count == length;
    }

    /** The box the caret is on. */
    int caret() {
        return caret;
    }

    public boolean allMarked() {
        return allMarked;
    }

    /** Where a box is, for a script or a test. */
    Box cellBox(int index) {
        return new Box("cell" + index, cellX(index), y, boxW(), h);
    }

    // ---- editing ----

    private int caretEnd() {
        return Math.min(count, length - 1);
    }

    private void rebuildValue() {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            out.append(cells[i]);
        }
        value = out.toString();
    }

    private String cellOf(int codePoint) {
        String cell = new String(Character.toChars(codePoint));
        return upper ? cell.toUpperCase(Locale.ROOT) : cell;
    }

    /** Writes what the filter takes from the caret on. Returns how many went in. */
    private int fill(String text) {
        int[] points = text.codePoints().toArray();
        int at = caret;
        int taken = 0;
        for (int point : points) {
            if (at >= length) {
                break;
            }
            if (filter.test(point)) {
                cells[at] = cellOf(point);
                at++;
                taken++;
            }
        }
        count = Math.max(count, at);
        caret = Math.min(at, length - 1);
        return taken;
    }

    private void changed() {
        rebuildValue();
        caretSince = BaseComponent.clock().getAsLong();
        if (onChange != null) {
            onChange.accept(value);
        }
        if (complete()) {
            if (!value.equals(lastComplete)) {
                lastComplete = value;
                if (onComplete != null) {
                    onComplete.accept(value);
                }
            }
        } else {
            lastComplete = null;
        }
    }

    private void removeAt(int index) {
        for (int i = index; i < count - 1; i++) {
            cells[i] = cells[i + 1];
        }
        count--;
        cells[count] = null;
    }

    private void moveCaret(int to) {
        allMarked = false;
        caret = Math.max(0, Math.min(caretEnd(), to));
        caretSince = BaseComponent.clock().getAsLong();
    }

    private void clearAll() {
        count = 0;
        caret = 0;
        allMarked = false;
    }

    private void backspace() {
        if (allMarked) {
            clearAll();
            changed();
        } else if (caret < count) {
            removeAt(caret);
            caret = Math.min(caret, caretEnd());
            changed();
        } else if (caret > 0) {
            caret--;
            removeAt(caret);
            changed();
        }
    }

    private void delete() {
        if (allMarked) {
            clearAll();
            changed();
        } else if (caret < count) {
            removeAt(caret);
            caret = Math.min(caret, caretEnd());
            changed();
        }
    }

    private void paste() {
        TextFieldComponent.Clipboard clip = TextFieldComponent.clipboard;
        String text = clip == null ? null : clip.get();
        if (text == null || text.isEmpty()) {
            return;
        }
        if (allMarked) {
            String[] saved = cells.clone();
            int savedCount = count;
            clearAll();
            if (fill(text) == 0) {
                System.arraycopy(saved, 0, cells, 0, saved.length);
                count = savedCount;
                caret = caretEnd();
                return;
            }
        } else if (fill(text) == 0) {
            return;
        }
        BaseComponent.click();
        changed();
    }

    // ---- geometry ----

    private int groups() {
        return groupEvery <= 0 || groupEvery >= length ? 0 : (length - 1) / groupEvery;
    }

    private int boxW() {
        int room = w - (length - 1 - groups()) * GAP - groups() * GROUP_GAP;
        return Math.max(0, Math.min(h, room / length));
    }

    private int cellX(int index) {
        int boxW = boxW();
        int groupsBefore = groupEvery <= 0 || groupEvery >= length ? 0 : index / groupEvery;
        return x + index * boxW + (index - groupsBefore) * GAP + groupsBefore * GROUP_GAP;
    }

    private int cellAt(double mx) {
        int boxW = boxW();
        for (int i = 0; i < length; i++) {
            // The gap after a box belongs to it, so that no click in the row falls through.
            int next = i == length - 1 ? cellX(i) + boxW : cellX(i + 1);
            if (mx < next) {
                return i;
            }
        }
        return length - 1;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        int boxW = boxW();
        boolean live = focused && active;
        if (boxW < MIN_BOX) {
            // Too narrow for its boxes: a plain field with the code as text, no slivers.
            frame.field(d, p, x, y, w, h, live);
            if (h >= FormLook.FONT_H && w > 4) {
                d.textFit(mask ? "" : value, x + 2, RowLook.textY(y, h), w - 4, active ? frame.fieldText(p) : FormLook.fieldDisabled(p, frame));
            }
            return;
        }
        int ink = !active ? FormLook.fieldDisabled(p, frame) : frame.fieldText(p);
        int alert = FormLook.alertEdge(p);
        for (int i = 0; i < length; i++) {
            int cx = cellX(i);
            boolean here = live && (i == caret || allMarked);
            frame.field(d, p, cx, y, boxW, h, here);
            if (allMarked && live && i < count) {
                d.fill(cx + 2, y + 2, cx + boxW - 2, y + h - 2, Shapes.alpha(p.primary(), MARK_ALPHA));
            }
            if (i < count) {
                drawCell(d, i, cx, boxW, ink);
            } else if (live && i == caret && !allMarked && FormLook.caretShown(caretSince) && h >= FormLook.FONT_H) {
                int mid = cx + boxW / 2;
                d.fill(mid, RowLook.textY(y, h) - 1, mid + 1, RowLook.textY(y, h) + FormLook.FONT_H, ink);
            }
            if (invalid) {
                frame.plate(d, p, cx, y, boxW, h, 0, alert);
            }
            if (groupEvery > 0 && groupEvery < length && i < length - 1 && (i + 1) % groupEvery == 0) {
                int dashX = cx + boxW + (GROUP_GAP - DASH_W) / 2;
                d.fill(dashX, y + h / 2, dashX + DASH_W, y + h / 2 + 1, active ? Ink.soft(p, FormLook.surface(p)) : p.disabledText());
            }
        }
    }

    private void drawCell(Draw d, int index, int cx, int boxW, int ink) {
        if (mask) {
            int side = MASK_DOT;
            d.fill(cx + (boxW - side) / 2, y + (h - side) / 2, cx + (boxW - side) / 2 + side, y + (h - side) / 2 + side, ink);
            return;
        }
        if (h < FormLook.FONT_H) {
            return;
        }
        String cell = cells[index];
        int width = d.width(cell) - 1;
        d.text(cell, cx + (boxW - width + 1) / 2, RowLook.textY(y, h), ink);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        return isOver(mx, my) ? tooltip : null;
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

    /** The ring goes round the boxes, not the room left beside them. */
    @Override
    protected Box focusBox() {
        return boxW() < MIN_BOX ? super.focusBox() : new Box(box().name(), x, y, cellX(length - 1) + boxW() - x, h);
    }

    @Override
    protected void enter(boolean forward) {
        moveCaret(caretEnd());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !active || button != 0 || !isOver(mx, my)) {
            return false;
        }
        moveCaret(cellAt(mx));
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!visible || !active) {
            return false;
        }
        switch (key) {
            case LEFT -> moveCaret(allMarked ? 0 : caret - 1);
            case RIGHT -> moveCaret(allMarked ? caretEnd() : caret + 1);
            case HOME -> moveCaret(0);
            case END -> moveCaret(caretEnd());
            case BACKSPACE -> backspace();
            case DELETE -> delete();
            case SELECT_ALL -> allMarked = count > 0;
            case PASTE -> paste();
            case COPY -> copy(false);
            case CUT -> copy(true);
            case SPACE -> {
                return true; // the character comes next, through charTyped
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void copy(boolean cut) {
        TextFieldComponent.Clipboard clip = TextFieldComponent.clipboard;
        if (clip != null && count > 0) {
            clip.set(value);
        }
        if (cut && count > 0) {
            clearAll();
            changed();
        }
    }

    @Override
    public boolean charTyped(int codePoint) {
        if (!visible || !active) {
            return false;
        }
        if (!filter.test(codePoint)) {
            return true;
        }
        if (allMarked) {
            clearAll();
        }
        cells[caret] = cellOf(codePoint);
        count = Math.max(count, caret + 1);
        caret = Math.min(caret + 1, length - 1);
        changed();
        return true;
    }
}
