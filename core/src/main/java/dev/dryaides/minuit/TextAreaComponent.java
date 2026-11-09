package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Texts;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * Several lines of text to edit, wrapped to the box, with a caret, a selection, the clipboard and a
 * vertical scroll bar. It looks like {@link TextFieldComponent} and shares its names.
 *
 * <pre>{@code
 * add(new TextAreaComponent(at(new Box("notes", 12, 28, 276, 80)), config.notes, v -> config.notes = v)
 *         .placeholder("Write something...")
 *         .maxLength(2000));
 * }</pre>
 *
 * <p>Enter starts a new line and Tab leaves the box. Ctrl with the arrows jumps by word, and with Home
 * or End goes to the start or end of the text. A double click takes a word and a triple click a whole
 * line. The clipboard is {@link TextFieldComponent#clipboard}. Ctrl+Z undoes; Ctrl+Y and Ctrl+Shift+Z redo.
 *
 * <p><b>Not verified on a real keyboard:</b> undo and redo match the raw GLFW codes 90 (Z) and 89 (Y) and
 * have only been driven by scripted codes on the bench, never in game. GLFW names its codes after US key
 * positions, so on AZERTY or QWERTZ these codes may belong to a key with another letter on it, which would
 * put undo on the wrong key. The library's own Ctrl+A, C, X and V also compare fixed codes, so seeing which
 * key selects all in a text field on that keyboard should show how the codes behave. AltGr, which Windows
 * may report as Ctrl, has not been tried either. Inside a {@link ScrollComponent} the raw keys do not reach
 * the area, so there only {@link #undo()} and {@link #redo()} work.
 *
 * <p>Text comes in clean: line ends become {@code \n}, a tab becomes four spaces, and control
 * characters and the game's section sign are dropped. Every position is an index into {@link #text()}.
 * Offsets and lines are measured with {@link TextMeasure}, so all the editing methods work without a
 * {@link Draw}.
 */
public class TextAreaComponent extends BaseComponent {
    /** Pixels from one line of text to the next. */
    public static final int LINE_HEIGHT = 10;
    /** The most text a new area takes. Change it with {@link #maxLength(int)}. */
    public static final int DEFAULT_MAX_LENGTH = 32767;

    private static final int PAD_X = 4;
    private static final int PAD_Y = 4;
    /** Kept free on the right even with no bar, so text does not rewrap when the bar appears. */
    private static final int GUTTER = ScrollComponent.BAR;
    private static final int DOUBLE_CLICK_MS = 400;
    private static final int CLICK_SLOP = 2;
    private static final int UNDO_LIMIT = 64;
    private static final int NEWLINE_MARK = 3;
    private static final int SECTION_SIGN = 0xA7;
    // Undo and redo have no InputKey: while focused, the area reads the raw key codes (GLFW's). See the class comment.
    private static final int RAW_Z = 90;
    private static final int RAW_Y = 89;

    private enum EditKind { NONE, TYPING, ERASING, OTHER }
    private enum Drag { NONE, CHARS, WORDS, LINES, BAR }
    private record Snapshot(String text, int cursor, int anchor) { }

    private String text = "";
    private final Consumer<String> onChange;
    private String placeholder = "";
    private int maxLength = DEFAULT_MAX_LENGTH;
    private IntPredicate filter = c -> true;

    private int cursor;
    private int anchor;
    /** The caret sits at the end of a wrapped line, not at the start of the next, which has the same index. */
    private boolean atLineEnd;
    /** Where up and down aim, in pixels from the line's start; -1 when not set. */
    private int goalX = -1;
    private final ScrollBar bar = new ScrollBar();
    private FrameStyle frame = FrameStyles.VANILLA;
    private long caretSince;

    private int[] starts = new int[8];
    private int[] ends = new int[8];
    private int lineCount;
    private boolean dirty = true;
    private int layoutWidth = -1;
    private Object layoutFont;

    private final ArrayDeque<Snapshot> undo = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redo = new ArrayDeque<>();
    private EditKind lastEdit = EditKind.NONE;

    private Drag drag = Drag.NONE;
    private int dragLastY;
    private int dragLastX;
    private int wordStart;
    private int wordEnd;
    private long lastClickAt = Long.MIN_VALUE / 2;
    private int lastClickX;
    private int lastClickY;
    private int clickCount;

    public TextAreaComponent(Box box, String value, Consumer<String> onChange) {
        super(box);
        this.onChange = onChange;
        this.text = clip(clean(value == null ? "" : value), maxLength);
        cursor = anchor = text.length();
    }

    /** Shown in the field's hint colour while the text is empty. */
    public TextAreaComponent placeholder(String value) {
        placeholder = value == null ? "" : value;
        return this;
    }

    /** The most UTF-16 chars the text may hold, as {@code String.length()} counts. Text past it is cut. */
    public TextAreaComponent maxLength(int value) {
        maxLength = Math.max(0, value);
        if (text.length() > maxLength) {
            text = clip(text, maxLength);
            dirty = true;
            cursor = Math.min(cursor, text.length());
            anchor = Math.min(anchor, text.length());
            forgetHistory();
        }
        return this;
    }

    /** Which code points may be entered, on top of the cleaning above. New lines always pass. */
    public TextAreaComponent filter(IntPredicate value) {
        filter = value == null ? c -> true : value;
        return this;
    }

    public String text() { return text; }

    public int cursor() { return cursor; }

    /** The selection as {start, end}; start equals end when nothing is selected. */
    public int[] selection() { return new int[]{Math.min(cursor, anchor), Math.max(cursor, anchor)}; }

    public boolean hasSelection() { return cursor != anchor; }

    public String selectedText() {
        int[] s = selection();
        return text.substring(s[0], s[1]);
    }

    /** How many lines the text takes on screen, counting wrapped ones. */
    int lineCount() {
        layout();
        return lineCount;
    }

    /** The text of one line on screen, without its line end. A wrapped line keeps the space it broke at. */
    String line(int index) {
        layout();
        return text.substring(starts[index], ends[index]);
    }

    /** The on-screen line the caret is on. */
    int cursorLine() {
        layout();
        return lineOf(cursor);
    }

    /** Pixels the text has moved up. */
    int scroll() { return bar.offset(); }

    int maxScroll() {
        layout();
        bar.content(lineCount * LINE_HEIGHT, viewHeight());
        return bar.max();
    }

    void setScroll(int value) {
        maxScroll();
        bar.offset(value);
    }

    /** Replaces the text with the caret at its end. Does not call back, and clears the undo history. */
    public void setText(String value) {
        text = clip(clean(value == null ? "" : value), maxLength);
        cursor = anchor = text.length();
        atLineEnd = false;
        goalX = -1;
        dirty = true;
        forgetHistory();
        if (focused) {
            reveal();
        } else {
            setScroll(0);
        }
    }

    /** Moves the caret to an index; extend keeps the other end of the selection where it is. */
    public void moveCursor(int index, boolean extend) {
        goalX = -1;
        place(index, extend, false);
    }

    /** Moves the caret by lines on screen, keeping its column as well as the lines allow. */
    public void moveLine(int delta, boolean extend) {
        layout();
        int line = lineOf(cursor);
        if (goalX < 0) {
            goalX = offsetOn(line, cursor);
        }
        int target = Math.max(0, Math.min(lineCount - 1, line + delta));
        if (target == line) {
            place(cursor, extend, atLineEnd);
            return;
        }
        int index = indexOn(target, goalX);
        place(index, extend, endsWrapped(target, index));
    }

    /** Moves the caret to the start of its line on screen. */
    public void moveHome(boolean extend) {
        layout();
        goalX = -1;
        place(starts[lineOf(cursor)], extend, false);
    }

    /** Moves the caret to the end of its line on screen. */
    public void moveEnd(boolean extend) {
        layout();
        int line = lineOf(cursor);
        goalX = -1;
        place(ends[line], extend, endsWrapped(line, ends[line]));
    }

    public void selectAll() {
        anchor = 0;
        cursor = text.length();
        atLineEnd = false;
        goalX = -1;
        lastEdit = EditKind.NONE;
        touch();
        reveal();
    }

    /** Selects the word, the run of spaces or the punctuation at an index. */
    public void selectWord(int index) {
        int[] word = wordAt(snap(index));
        anchor = word[0];
        cursor = word[1];
        atLineEnd = false;
        goalX = -1;
        lastEdit = EditKind.NONE;
        touch();
        reveal();
    }

    /** Selects the whole line, up to its line end, around an index. */
    public void selectLine(int index) {
        int at = snap(index);
        int start = text.lastIndexOf('\n', at - 1) + 1;
        int end = text.indexOf('\n', at);
        anchor = start;
        cursor = end < 0 ? text.length() : end;
        atLineEnd = false;
        goalX = -1;
        lastEdit = EditKind.NONE;
        touch();
        reveal();
    }

    /** Puts text in place of the selection, cleaned and cut to what maxLength leaves room for. */
    public void insert(String value) {
        int[] s = selection();
        replace(s[0], s[1], value, EditKind.OTHER);
    }

    /** Removes the selection, or the character before the caret. */
    public void deleteBackward() {
        int[] s = selection();
        if (s[0] != s[1]) {
            replace(s[0], s[1], "", EditKind.OTHER);
        } else if (cursor > 0) {
            replace(before(cursor), cursor, "", EditKind.ERASING);
        }
    }

    /** Removes the selection, or the character after the caret. */
    public void deleteForward() {
        int[] s = selection();
        if (s[0] != s[1]) {
            replace(s[0], s[1], "", EditKind.OTHER);
        } else if (cursor < text.length()) {
            replace(cursor, after(cursor), "", EditKind.ERASING);
        }
    }

    /** Copies the selection, if any. */
    public void copy() {
        TextFieldComponent.Clipboard clip = TextFieldComponent.clipboard;
        if (clip != null && hasSelection()) {
            clip.set(selectedText());
        }
    }

    /** Copies the selection, if any, and removes it. */
    public void cut() {
        if (hasSelection()) {
            copy();
            deleteBackward();
        }
    }

    /** Puts the clipboard's text in place of the selection. */
    public void paste() {
        TextFieldComponent.Clipboard clip = TextFieldComponent.clipboard;
        String value = clip == null ? null : clip.get();
        if (value != null && !value.isEmpty()) {
            insert(value);
        }
    }

    /** Takes back the last edit. Runs of typing or erasing count as one. Returns false with nothing to undo. */
    public boolean undo() {
        return restore(undo, redo);
    }

    /** Puts back what {@link #undo()} took. Returns false with nothing to redo. */
    public boolean redo() {
        return restore(redo, undo);
    }

    private boolean restore(ArrayDeque<Snapshot> from, ArrayDeque<Snapshot> to) {
        Snapshot state = from.pollLast();
        if (state == null) {
            return false;
        }
        to.addLast(new Snapshot(text, cursor, anchor));
        text = state.text();
        cursor = state.cursor();
        anchor = state.anchor();
        atLineEnd = false;
        goalX = -1;
        dirty = true;
        lastEdit = EditKind.NONE;
        touch();
        reveal();
        fire();
        return true;
    }

    private void forgetHistory() {
        undo.clear();
        redo.clear();
        lastEdit = EditKind.NONE;
    }

    private void replace(int from, int to, String value, EditKind kind) {
        String added = clean(value);
        int room = maxLength - (text.length() - (to - from));
        added = clip(added, room);
        if (from == to && added.isEmpty()) {
            return;
        }
        String next = text.substring(0, from) + added + text.substring(to);
        if (next.equals(text)) {
            return;
        }
        if (kind == EditKind.OTHER || kind != lastEdit) {
            undo.addLast(new Snapshot(text, cursor, anchor));
            if (undo.size() > UNDO_LIMIT) {
                undo.removeFirst();
            }
        }
        redo.clear();
        lastEdit = kind;
        text = next;
        cursor = anchor = from + added.length();
        atLineEnd = false;
        goalX = -1;
        dirty = true;
        touch();
        reveal();
        fire();
    }

    private void fire() {
        if (onChange != null) {
            onChange.accept(text);
        }
    }

    private void place(int index, boolean extend, boolean lineEnd) {
        cursor = snap(index);
        if (!extend) {
            anchor = cursor;
        }
        atLineEnd = lineEnd;
        lastEdit = EditKind.NONE;
        touch();
        reveal();
    }

    private void touch() {
        caretSince = clock.getAsLong();
    }

    /** Scrolls just enough to show the caret's line. */
    private void reveal() {
        layout();
        int top = lineOf(cursor) * LINE_HEIGHT;
        int view = viewHeight();
        if (top < scroll()) {
            setScroll(top);
        } else if (top + LINE_HEIGHT > scroll() + view) {
            setScroll(top + LINE_HEIGHT - view);
        }
    }

    private String clean(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        while (i < raw.length()) {
            int c = raw.codePointAt(i);
            i += Character.charCount(c);
            if (c == '\r') {
                if (i < raw.length() && raw.charAt(i) == '\n') {
                    i++;
                }
                out.append('\n');
            } else if (c == '\n') {
                out.append('\n');
            } else if (c == '\t') {
                if (filter.test(' ')) {
                    out.append("    ");
                }
            } else if (allowed(c)) {
                out.appendCodePoint(c);
            }
        }
        return out.toString();
    }

    private boolean allowed(int c) {
        return c >= 32 && c != 127 && c != SECTION_SIGN && filter.test(c);
    }

    /** At most max chars, never ending inside a surrogate pair. */
    private static String clip(String value, int max) {
        return Texts.clip(value, max);
    }

    private int snap(int index) {
        int at = Math.max(0, Math.min(index, text.length()));
        if (at > 0 && at < text.length()
                && Character.isLowSurrogate(text.charAt(at)) && Character.isHighSurrogate(text.charAt(at - 1))) {
            at--;
        }
        return at;
    }

    private int before(int index) {
        return index <= 0 ? 0 : text.offsetByCodePoints(index, -1);
    }

    private int after(int index) {
        return index >= text.length() ? text.length() : text.offsetByCodePoints(index, 1);
    }

    /** 0 line end, 1 space, 2 word, 3 anything else. */
    private static int kind(int c) {
        if (c == '\n') {
            return 0;
        }
        if (Character.isWhitespace(c)) {
            return 1;
        }
        return Character.isLetterOrDigit(c) || c == '_' ? 2 : 3;
    }

    private int[] wordAt(int index) {
        if (text.isEmpty()) {
            return new int[]{0, 0};
        }
        int at = index;
        if (at == text.length() || text.charAt(at) == '\n') {
            if (at == 0 || text.charAt(at - 1) == '\n') {
                return new int[]{at, at};
            }
            at = before(at);
        } else if (at > 0 && kind(text.codePointAt(at)) != 2 && kind(text.codePointBefore(at)) == 2) {
            at = before(at); // right after a word, that word is what a double click means
        }
        int k = kind(text.codePointAt(at));
        int start = at;
        while (start > 0 && kind(text.codePointBefore(start)) == k) {
            start = before(start);
        }
        int end = at;
        while (end < text.length() && kind(text.codePointAt(end)) == k) {
            end = after(end);
        }
        return new int[]{start, end};
    }

    /** Where ctrl and left lands: the start of the word before the index. */
    private int wordLeft(int index) {
        int at = index;
        while (at > 0 && kind(text.codePointBefore(at)) <= 1) {
            at = before(at);
        }
        if (at == 0) {
            return 0;
        }
        int k = kind(text.codePointBefore(at));
        while (at > 0 && kind(text.codePointBefore(at)) == k) {
            at = before(at);
        }
        return at;
    }

    /** Where ctrl and right lands: the end of the word after the index. */
    private int wordRight(int index) {
        int at = index;
        while (at < text.length() && kind(text.codePointAt(at)) <= 1) {
            at = after(at);
        }
        if (at == text.length()) {
            return at;
        }
        int k = kind(text.codePointAt(at));
        while (at < text.length() && kind(text.codePointAt(at)) == k) {
            at = after(at);
        }
        return at;
    }

    private int textWidth() {
        return Math.max(1, w - 2 * PAD_X - GUTTER);
    }

    private int viewHeight() {
        return Math.max(LINE_HEIGHT, h - 2 * PAD_Y);
    }

    private void layout() {
        int width = textWidth();
        Object font = TextMeasure.font;
        if (!dirty && width == layoutWidth && font == layoutFont) {
            return;
        }
        dirty = false;
        layoutWidth = width;
        layoutFont = font;
        lineCount = 0;
        int from = 0;
        while (true) {
            int newline = text.indexOf('\n', from);
            int end = newline < 0 ? text.length() : newline;
            wrapLine(from, end, width);
            if (newline < 0) {
                break;
            }
            from = newline + 1;
        }
    }

    private void wrapLine(int from, int end, int width) {
        int start = from;
        while (true) {
            int fit = fit(start, end, width);
            if (fit >= end) {
                addLine(start, end);
                return;
            }
            int cut = breakAt(start, fit);
            if (cut >= end) {
                addLine(start, end);
                return;
            }
            addLine(start, cut);
            start = cut;
        }
    }

    private void addLine(int start, int end) {
        if (lineCount == starts.length) {
            starts = Arrays.copyOf(starts, lineCount * 2);
            ends = Arrays.copyOf(ends, lineCount * 2);
        }
        starts[lineCount] = start;
        ends[lineCount] = end;
        lineCount++;
    }

    /** The largest index from which the text since start fits the width. Assumes a char takes at least a pixel. */
    private int fit(int start, int end, int width) {
        int lo = start;
        int hi = (int) Math.min(end, (long) start + width);
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (TextMeasure.width(text.substring(start, mid)) <= width) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return snap(lo);
    }

    /** Where a line that fits up to index fit should break: after a space if it can, else inside the word. */
    private int breakAt(int start, int fit) {
        if (text.charAt(fit) == ' ') {
            return fit + 1;
        }
        for (int i = fit - 1; i > start; i--) {
            if (text.charAt(i) == ' ') {
                return i + 1;
            }
        }
        return fit > start ? fit : after(start);
    }

    private int lineOf(int index) {
        int lo = 0;
        int hi = lineCount - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (starts[mid] <= index) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return atLineEnd && index == cursor && lo > 0 && starts[lo] == index && ends[lo - 1] == index ? lo - 1 : lo;
    }

    /** True at the end of a line that wrapped, where the same index also starts the next line. */
    private boolean endsWrapped(int line, int index) {
        return index == ends[line] && line + 1 < lineCount && starts[line + 1] == index;
    }

    private int offsetOn(int line, int index) {
        int to = Math.max(starts[line], Math.min(index, ends[line]));
        return TextMeasure.width(text.substring(starts[line], to));
    }

    /** The index on a line nearest to a pixel offset from the line's start. */
    private int indexOn(int line, int offset) {
        int at = starts[line];
        int left = 0;
        while (at < ends[line]) {
            int next = after(at);
            int right = TextMeasure.width(text.substring(starts[line], next));
            if (offset < (left + right + 1) / 2) {
                return at;
            }
            at = next;
            left = right;
        }
        return ends[line];
    }

    private int lineAtY(double my) {
        int line = (int) Math.floor((my - (y + PAD_Y) + scroll()) / LINE_HEIGHT);
        return Math.max(0, Math.min(lineCount - 1, line));
    }

    private void placeAt(double mx, double my, boolean extend) {
        layout();
        int line = lineAtY(my);
        int index = indexOn(line, (int) Math.round(mx) - (x + PAD_X));
        goalX = -1;
        place(index, extend, endsWrapped(line, index));
    }

    private int barX() { return ScrollBar.wellX(frame, x, w); }
    private int trackY() { return y + 2; }
    private int trackH() { return Math.max(1, h - 4); }

    private boolean overBar(double mx) {
        return maxScroll() > 0 && mx >= barX();
    }

    private void dragBar(double my) {
        bar.drag(my);
        if (bar.offset() < bar.max()) {
            // whole lines, so the top line is not cut through while the bar moves
            bar.offset((bar.offset() + LINE_HEIGHT / 2) / LINE_HEIGHT * LINE_HEIGHT);
        }
    }

    @Override
    protected boolean takesFocus() { return visible && active; }

    @Override
    protected boolean navigable() { return visible && active; }

    @Override
    protected boolean capturesKeys() { return active; }

    /** Takes only the undo and redo keys, which have no InputKey. Every other key goes on to {@link #keyPressed}. */
    @Override
    public boolean keyCaptured(KeyPress press) {
        if (!focused || !active || press.kind() != KeyPress.Kind.KEY || !keys.ctrl()) {
            return false;
        }
        boolean shift = keys.shift();
        if (press.code() == RAW_Z) {
            if (shift) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (press.code() == RAW_Y && !shift) {
            redo();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !active || button != 0 || !isOver(mx, my)) {
            return false;
        }
        layout();
        if (overBar(mx)) {
            bar.press(mx, my, barX(), trackY(), trackH());
            drag = Drag.BAR;
            return true;
        }
        long now = clock.getAsLong();
        boolean again = now - lastClickAt <= DOUBLE_CLICK_MS
                && Math.abs(mx - lastClickX) <= CLICK_SLOP && Math.abs(my - lastClickY) <= CLICK_SLOP;
        clickCount = again ? clickCount % 3 + 1 : 1;
        lastClickAt = now;
        lastClickX = (int) Math.round(mx);
        lastClickY = (int) Math.round(my);
        dragLastX = lastClickX;
        dragLastY = lastClickY;
        if (clickCount == 1) {
            placeAt(mx, my, keys.shift());
            drag = Drag.CHARS;
        } else {
            placeAt(mx, my, false);
            if (clickCount == 2) {
                selectWord(cursor);
                wordStart = anchor;
                wordEnd = cursor;
                drag = Drag.WORDS;
            } else {
                selectLine(cursor);
                drag = Drag.LINES;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag == Drag.NONE || button != 0) {
            return false;
        }
        if (drag == Drag.BAR) {
            dragBar(my);
            return true;
        }
        dragLastX = (int) Math.round(mx);
        dragLastY = (int) Math.round(my);
        extendDrag();
        return true;
    }

    /** Moves the selection's free end to the pointer, at most a line past the box so scrolling stays even. */
    private void extendDrag() {
        int top = y + PAD_Y;
        int clamped = Math.max(top - LINE_HEIGHT, Math.min(y + h - PAD_Y + LINE_HEIGHT - 1, dragLastY));
        if (drag == Drag.CHARS) {
            placeAt(dragLastX, clamped, true);
        } else if (drag == Drag.WORDS) {
            placeAt(dragLastX, clamped, false);
            int[] word = wordAt(cursor);
            if (word[1] <= wordStart || cursor < wordStart) {
                anchor = wordEnd;
                cursor = Math.min(word[0], wordStart);
            } else {
                anchor = wordStart;
                cursor = Math.max(word[1], wordEnd);
            }
            atLineEnd = false;
            reveal();
        }
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drag == Drag.NONE || button != 0) {
            return false;
        }
        drag = Drag.NONE;
        bar.release();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        boolean selecting = drag == Drag.CHARS || drag == Drag.WORDS;
        if (selecting && (dragLastY < y + PAD_Y || dragLastY >= y + h - PAD_Y)) {
            extendDrag();
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || !isOver(mx, my)) {
            return false;
        }
        maxScroll();
        return bar.wheel(amount, ScrollBar.WHEEL_ROWS * LINE_HEIGHT);
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!focused || !active) {
            return false;
        }
        boolean ctrl = keys.ctrl();
        switch (key) {
            case LEFT -> moveSideways(false, shift, ctrl);
            case RIGHT -> moveSideways(true, shift, ctrl);
            case UP -> moveLine(-1, shift);
            case DOWN -> moveLine(1, shift);
            case HOME -> {
                if (ctrl) {
                    moveCursor(0, shift);
                } else {
                    moveHome(shift);
                }
            }
            case END -> {
                if (ctrl) {
                    moveCursor(text.length(), shift);
                } else {
                    moveEnd(shift);
                }
            }
            case BACKSPACE -> {
                if (ctrl && !hasSelection()) {
                    replace(wordLeft(cursor), cursor, "", EditKind.OTHER);
                } else {
                    deleteBackward();
                }
            }
            case DELETE -> {
                if (ctrl && !hasSelection()) {
                    replace(cursor, wordRight(cursor), "", EditKind.OTHER);
                } else {
                    deleteForward();
                }
            }
            case ENTER -> {
                insert("\n");
            }
            case SELECT_ALL -> selectAll();
            case COPY -> copy();
            case CUT -> cut();
            case PASTE -> paste();
            case SPACE -> { } // the space itself arrives through charTyped; keep the panel from pressing anything
            default -> {
                return false;
            }
        }
        return true;
    }

    private void moveSideways(boolean forward, boolean shift, boolean ctrl) {
        goalX = -1;
        if (hasSelection() && !shift && !ctrl) {
            int[] s = selection();
            place(forward ? s[1] : s[0], false, false);
        } else if (ctrl) {
            place(forward ? wordRight(cursor) : wordLeft(cursor), shift, false);
        } else if (forward && atLineEnd) {
            place(cursor, shift, false);
        } else {
            place(forward ? after(cursor) : before(cursor), shift, false);
        }
    }

    @Override
    public boolean charTyped(int codePoint) {
        if (!focused || !active) {
            return false;
        }
        if (codePoint == '\n' || codePoint == '\r') {
            insert("\n");
            return true;
        }
        if (allowed(codePoint)) {
            int[] s = selection();
            replace(s[0], s[1], new String(Character.toChars(codePoint)),
                    s[0] == s[1] ? EditKind.TYPING : EditKind.OTHER);
        }
        return true;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        frame = theme.frame();
        boolean live = focused && active;
        frame.field(d, p, x, y, w, h, live);
        maxScroll();
        int scroll = scroll();

        int left = x + PAD_X;
        int top = y + PAD_Y;
        int color = active ? frame.fieldText(p) : p.disabledText();
        // tight to the lines: a line scrolled away leaves nothing in the padding
        d.scissor(x + 1, top - 1, barX(), y + h - PAD_Y);
        if (text.isEmpty()) {
            drawPlaceholder(d, frame.fieldHint(p), left, top);
        } else {
            int first = Math.max(0, scroll / LINE_HEIGHT);
            int last = Math.min(lineCount - 1, (scroll + viewHeight()) / LINE_HEIGHT);
            for (int line = first; line <= last; line++) {
                int ty = top + line * LINE_HEIGHT - scroll;
                if (!lineCut(ty, top)) {
                    drawLine(d, p, line, left, ty, color, live);
                }
            }
        }
        if (live && caretShown()) {
            int line = lineOf(cursor);
            int cx = Math.min(left + offsetOn(line, cursor), left + textWidth());
            int cy = top + line * LINE_HEIGHT - scroll;
            if (!lineCut(cy, top)) {
                // In the space after the previous letter, not on the first column of the next one.
                d.fill(cx - 1, cy - 1, cx, cy + 9, frame.fieldText(p));
            }
        }
        d.endScissor();

        if (maxScroll() > 0 && RowLook.barFits(w, h, GUTTER)) {
            bar.draw(d, theme, barX(), trackY(), trackH(), active && hovered && mx >= barX());
            if (!active) {
                RowLook.dimBar(d, p, barX(), trackY(), GUTTER, trackH());
            }
        }
    }

    /** True when the box would cut this line: a box whose height is not a whole number of lines shows no slivers of letters. */
    private boolean lineCut(int lineY, int top) {
        return RowLook.textCut(lineY, RowLook.FONT_H, top, h - 2 * PAD_Y);
    }

    private void drawPlaceholder(Draw d, int color, int left, int top) {
        List<String> lines = TextMeasure.wrapAll(placeholder, textWidth());
        int fits = viewHeight() / LINE_HEIGHT;
        for (int i = 0; i < lines.size() && i < fits; i++) {
            d.text(lines.get(i), left, top + i * LINE_HEIGHT, color);
        }
    }

    private void drawLine(Draw d, Palette p, int line, int left, int ty, int color, boolean live) {
        int start = starts[line];
        int end = ends[line];
        int[] s = selection();
        int from = Math.max(s[0], start);
        int to = Math.min(s[1], end);
        boolean takesLineEnd = s[0] <= end && s[1] > end && end < text.length() && text.charAt(end) == '\n';
        if (!live || s[0] == s[1] || (from >= to && !takesLineEnd)) {
            d.text(text.substring(start, end), left, ty, color);
            return;
        }
        int a = left + TextMeasure.width(text.substring(start, from));
        int b = a + TextMeasure.width(text.substring(from, to));
        d.fill(a, ty - 1, b + (takesLineEnd ? NEWLINE_MARK : 0), ty + 9, p.primary());
        if (from > start) {
            d.text(text.substring(start, from), left, ty, color);
        }
        if (to > from) {
            d.text(text.substring(from, to), a, ty, p.primaryText());
        }
        if (end > to) {
            d.text(text.substring(to, end), b, ty, color);
        }
    }

    private boolean caretShown() {
        if (reducedMotion) {
            return true;
        }
        long since = clock.getAsLong() - caretSince;
        return since < 0 || since % (2 * TextFieldComponent.BLINK_MS) < TextFieldComponent.BLINK_MS;
    }
}
