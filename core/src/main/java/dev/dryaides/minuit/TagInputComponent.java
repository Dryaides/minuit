package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Redraw;
import dev.dryaides.minuit.internal.DarkTwin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * A field that holds a list of short texts as chips, with room to type the next one after the last chip.
 * Chips wrap across lines; the box scrolls by whole lines, or grows up to {@link #growTo(int)} first.
 *
 * <pre>{@code
 * public class FiltersPanel extends PanelComponent {
 *     public FiltersPanel(Theme theme, Words words, int left, int top) {
 *         super(theme, words, left, top);
 *         TagInputComponent tags = add(new TagInputComponent(at(new Box("tags", 10, 10, 180, 44)), this)
 *                 .placeholder(words.get("mymod.add_tag"))
 *                 .maxTags(8)
 *                 .suggestions(known)
 *                 .validator(t -> t.contains(":") ? null : words.get("mymod.needs_namespace"))
 *                 .onChange(this::save));
 *         add(new ButtonComponent(at(new Box("ok", 10, 60, 80, 20)), words.get("gui.done"), this::close));
 *         // Last, so that the suggestions are drawn over the button and offered every input first.
 *         add(tags.popup());
 *     }
 * }
 * }</pre>
 *
 * <h2>Adding</h2>
 * Enter or a comma adds what was typed ({@link #separators(String)} sets other separators), and so does
 * leaving the field unless {@link #commitOnLeave(boolean)} is off. Pasting text with a separator or a line
 * end in it adds every piece; without one it is plain typing. Texts are cleaned on the way in: control
 * characters and the game's section sign go, runs of spaces become one, the ends are trimmed.
 *
 * <p>A refused text stays in the field so that it can be corrected. A duplicate (ignoring case, unless
 * {@link #caseSensitive(boolean)}) shakes and reddens the chip it repeats, a full box shakes its content
 * and reddens its count, and a text rejected through {@link #rejectInvalid(boolean)} shakes in red. Under
 * {@link BaseComponent#reducedMotion} nothing moves: the red stays a little longer instead.
 *
 * <h2>Keys</h2>
 * <ul>
 * <li>Backspace with an empty field removes the last chip, and removes the selected chip when there is
 * one. {@link #confirmBackspace(boolean)} makes the first press only select. Holding Backspace to erase
 * the text stops at the empty field: the chips are only touched after a short pause.</li>
 * <li>Left at the start of the text selects the last chip, then the arrows walk the chips, up and down
 * across their lines. Right past the last chip goes back to the text. Home and End jump.</li>
 * <li>Delete removes the selected chip. Typing, Enter or Escape goes back to the text.</li>
 * <li>Ctrl+A, C, X and V act on the text. Copy with a chip selected copies it, and with an empty field and
 * no selection copies the whole list, ready to paste as a list.</li>
 * <li>With suggestions: down opens the list, up and down walk it, Enter or Tab takes the lit row, Escape
 * closes it. Enter with no lit row adds the text as typed.</li>
 * </ul>
 *
 * <h2>Putting it in a panel</h2>
 * With suggestions, add {@link #popup()} <b>after every other widget</b> of the panel, as for a
 * {@link ComboBoxComponent}. {@link #area(Box)} is the box the list stays in: the panel by default, or pass
 * the screen's box for a list that may leave the panel. {@link #growTo(int)} moves nothing else in the
 * panel: {@link #onResize(IntConsumer)} tells you when to do it.
 *
 * <p>{@code active = false} still scrolls with the wheel, and keeps a selected chip showing. Only a direct
 * child of a panel has been tried: not one inside a {@link ScrollComponent}, a {@link WindowComponent} or a
 * dialog. There is no undo, and no word jump with Ctrl and the arrows.
 */
public class TagInputComponent extends BaseComponent {
    /** Why a text did not become a chip. */
    public enum Refusal { DUPLICATE, FULL, INVALID }

    /** Height of a chip. */
    static final int CHIP_H = 12;
    /** Height of a line of chips, with its gap. {@code 16}, {@code 30}, {@code 44}... fit whole lines exactly. */
    static final int LINE = CHIP_H + 2;
    private static final int BAR = ScrollBar.WIDTH;
    /** Height of a row of the suggestions. */
    static final int ROW = DropdownComponent.LINE;

    private static final int LINE_GAP = 2;
    private static final int CHIP_GAP = 2;
    /** Left and right of the chips. The shake moves them by less, so they never touch the border. */
    private static final int SIDE = 3;
    private static final int BORDER = 1;
    private static final int CHIP_PAD = 4;
    /** Where the small x sits at the chip's right end. */
    private static final int CLOSE_ZONE = 11;
    private static final int CLOSE_SIZE = 5;
    /** Sits on the text's baseline, the height of its small letters. */
    private static final int CLOSE_TOP = 4;
    /** Inside the zone, so the x lies as far from the chip's edge as the text does on the other side. */
    private static final int CLOSE_LEFT = 2;
    private static final int CLOSE_REST = 0xC0;
    /** Below this chip width the x goes: the text comes first, and the keys still remove the chip. */
    private static final int MIN_CHIP_FOR_CLOSE = 18;
    private static final int MARK_LEFT = 3;
    private static final int MARK_GAP = 2;
    /**
     * The invalid mark, drawn by hand on the text's rows: the theme's warning icon has 9 rows, and a chip has 10
     * inside its edge, so its point would touch the edge.
     */
    private static final String[] MARK = {
            "...#...",
            "..#.#..",
            "..#.#..",
            ".##.##.",
            ".#####.",
            "###.###",
            "#######"};
    private static final int MARK_TOP = 2;
    private static final int MIN_INPUT = 24;
    /** The caret takes the column before the text, so it never bars the first letter. */
    private static final int TEXT_IN = 1;
    private static final int COUNT_GAP = 4;
    private static final int DEFAULT_MAX_LENGTH = 64;

    private static final int REST_FACE = 0x2C;
    private static final int HOVER_FACE = 0x48;
    private static final int CLOSE_HOVER = 0x40;
    private static final int CLOSE_HELD = 0x70;
    private static final int INVALID_FACE = 0x48;
    private static final int REFUSED_FACE = 0x90;
    private static final int DISABLED_FACE = 0x18;
    private static final int DISABLED_BAR = 0x90;

    private static final int SHAKE_MS = 360;
    private static final int STILL_MS = 700;
    private static final int SHAKE_AMPLITUDE = 2;
    private static final int SHAKE_TURNS = 3;
    /** Longer than a key's repeat, shorter than a person's pause between two presses. */
    private static final long BACKSPACE_PAUSE_MS = 250L;

    // The suggestions, as the combo box has them.
    private static final int ROW_LEFT = 3;
    private static final int ROW_RIGHT = 3;
    private static final int MIN_POPUP_W = 60;
    private static final int OPEN_MS = 110;

    private static final class Chip {
        final String text;
        final String key;
        String reason;

        Chip(String text, String key, String reason) {
            this.text = text;
            this.key = key;
            this.reason = reason;
        }
    }

    private final PanelComponent host;
    private final List<Chip> chips = new ArrayList<>();
    private final Layer layer = new Layer();
    private final PopupList list = new PopupList(ROW);
    private final ScrollBar lineBar = new ScrollBar();
    private final RowLook.HoverFade chipHover = new RowLook.HoverFade();
    private final Glide glide = new Glide(Slide.DOWN, OPEN_MS);

    private String placeholder = "";
    private String separators = ",";
    private boolean caseSensitive;
    private boolean commitOnLeave = true;
    private boolean confirmBackspace;
    private boolean rejectInvalid;
    private boolean showCount = true;
    private boolean layered;
    private int maxTags;
    private int maxLength = DEFAULT_MAX_LENGTH;
    private int growTo;
    private int baseH;
    private int maxRows = 6;
    private Box area;
    private Function<String, String> validator;
    private Function<String, List<String>> chipTooltip;
    private List<String> closeTooltip;
    private Consumer<List<String>> onChange;
    private Consumer<String> onAdd;
    private Consumer<String> onRemove;
    private BiConsumer<String, Refusal> onRefuse;
    private IntConsumer onResize;

    private String text = "";
    private int caret;
    private int anchor;
    private long caretSince;
    private long lastErase = Long.MIN_VALUE / 2;
    private int inputScroll;
    private boolean selectingText;

    private int selected = -1;
    private int heldClose = -1;
    private boolean hadFocus;

    private int refusedChip = -1;
    private boolean refusedAll;
    private boolean refusedInput;
    private long refusedAt = Long.MIN_VALUE / 2;

    // Layout: chip places as offsets from the left of the lines, which lines they fall on, and the input's place.
    private boolean dirty = true;
    private int laidW = -1;
    private int laidH = -1;
    private int[] chipLeft = new int[0];
    private int[] chipLine = new int[0];
    private int[] chipW = new int[0];
    private int inputLine;
    private int inputLeft;
    private int inputW;
    private int lines = 1;
    private boolean bar;

    // The suggestions.
    private List<String> options = List.of();
    private List<TextMatch.Folded> optionFolds = List.of();
    private List<String> shown = List.of();
    private boolean open;
    private int drawnRows;

    /**
     * @param box the field; its height at the start is the least it will have
     * @param host the panel this field is added to
     */
    public TagInputComponent(Box box, PanelComponent host) {
        super(box);
        this.host = Objects.requireNonNull(host, "host");
        this.baseH = h;
    }

    // ---- setup ----

    /** What the field shows with no chip and no text. */
    public TagInputComponent placeholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    /** The characters that end a chip while typing or in a paste. A comma by default; add a space for words. */
    public TagInputComponent separators(String separators) {
        this.separators = separators == null ? "" : separators;
        return this;
    }

    /** True to let "Stone" and "stone" both exist. Off by default. */
    public TagInputComponent caseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
        return this;
    }

    /** False to leave a text uncommitted when the field loses the focus. On by default. */
    public TagInputComponent commitOnLeave(boolean commitOnLeave) {
        this.commitOnLeave = commitOnLeave;
        return this;
    }

    /** True for a first Backspace that only selects the last chip, so a stray press loses nothing. Off by default. */
    public TagInputComponent confirmBackspace(boolean confirmBackspace) {
        this.confirmBackspace = confirmBackspace;
        return this;
    }

    /** The most chips the box takes; 0 for no limit. Chips past a new limit stay until removed. */
    public TagInputComponent maxTags(int maxTags) {
        this.maxTags = Math.max(0, maxTags);
        dirty = true;
        return this;
    }

    /** The longest chip, in chars. 64 by default. Typing stops there and a pasted piece is cut. */
    public TagInputComponent maxLength(int maxLength) {
        this.maxLength = Math.max(1, maxLength);
        return this;
    }

    /** False to hide the count that a limit shows at the end of the last line. On by default. */
    public TagInputComponent showCount(boolean showCount) {
        this.showCount = showCount;
        dirty = true;
        return this;
    }

    /**
     * Says what is wrong with a text: null means it is fine, anything else marks the chip invalid, and a
     * text that is not empty is its tooltip. Invalid chips stay, for the player to see and replace, unless
     * {@link #rejectInvalid(boolean)}. Existing chips are judged again.
     */
    public TagInputComponent validator(Function<String, String> validator) {
        this.validator = validator;
        for (Chip chip : chips) {
            chip.reason = reasonFor(chip.text);
        }
        dirty = true;
        return this;
    }

    /** True to refuse an invalid text instead of adding it with the invalid look. Off by default. */
    public TagInputComponent rejectInvalid(boolean rejectInvalid) {
        this.rejectInvalid = rejectInvalid;
        return this;
    }

    /** What the list offers. Those already in the box are left out. Null or empty means no list. */
    public TagInputComponent suggestions(Collection<String> suggestions) {
        List<String> copy = new ArrayList<>();
        List<TextMatch.Folded> folds = new ArrayList<>();
        if (suggestions != null) {
            for (String option : suggestions) {
                if (option != null && !option.isEmpty()) {
                    copy.add(option);
                    folds.add(TextMatch.fold(option));
                }
            }
        }
        options = copy;
        optionFolds = folds;
        refilter();
        return this;
    }

    /** How many rows the list shows before it scrolls. 6 by default; fewer when the area is short. */
    public TagInputComponent maxRows(int maxRows) {
        this.maxRows = Math.max(1, maxRows);
        return this;
    }

    /** The box the list stays in, in the panel's coordinates. The panel's bounds by default. */
    public TagInputComponent area(Box area) {
        this.area = area;
        return this;
    }

    /** Lets the box grow with its lines up to this height, then scroll. 0, the default, keeps its height. */
    public TagInputComponent growTo(int height) {
        this.growTo = Math.max(0, height);
        dirty = true;
        return this;
    }

    /** Called with the new height when {@link #growTo(int)} changes it, for you to move what is beneath. */
    public TagInputComponent onResize(IntConsumer onResize) {
        this.onResize = onResize;
        return this;
    }

    /** Lines for the chip under the pointer. A chip's invalid reason or cut text shows without this. */
    public TagInputComponent chipTooltip(Function<String, List<String>> chipTooltip) {
        this.chipTooltip = chipTooltip;
        return this;
    }

    /** A tooltip for every chip's small x. */
    public TagInputComponent closeTooltip(String... lines) {
        this.closeTooltip = lines == null || lines.length == 0 ? null : List.of(lines);
        return this;
    }

    /** Called with the whole list when the player adds or removes a chip. Not through the setters. */
    public TagInputComponent onChange(Consumer<List<String>> onChange) {
        this.onChange = onChange;
        return this;
    }

    public TagInputComponent onAdd(Consumer<String> onAdd) {
        this.onAdd = onAdd;
        return this;
    }

    public TagInputComponent onRemove(Consumer<String> onRemove) {
        this.onRemove = onRemove;
        return this;
    }

    /** Called with the text and the reason when the player's text is refused. */
    public TagInputComponent onRefuse(BiConsumer<String, Refusal> onRefuse) {
        this.onRefuse = onRefuse;
        return this;
    }

    public TagInputComponent active(boolean active) {
        this.active = active;
        if (!active) {
            lineBar.release();
            selectingText = false;
            heldClose = -1;
        }
        sync();
        return this;
    }

    @Override
    public TagInputComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /**
     * The list as a widget of its own: add it after every other widget of the panel, so that it is drawn
     * on top and offered every input first.
     */
    public BaseComponent popup() {
        layered = true;
        return layer;
    }

    // ---- content ----

    /** The chips' texts, in order. A copy. */
    public List<String> tags() {
        List<String> out = new ArrayList<>(chips.size());
        for (Chip chip : chips) {
            out.add(chip.text);
        }
        return out;
    }

    /** Replaces every chip, without calling back. Texts are cleaned, duplicates dropped; null means none. */
    public void setTags(Collection<String> tags) {
        chips.clear();
        if (tags != null) {
            for (String tag : tags) {
                String clean = tag == null ? "" : clean(tag);
                if (!clean.isEmpty() && indexOf(clean) < 0) {
                    chips.add(chipOf(clean));
                }
            }
        }
        selected = -1;
        heldClose = -1;
        refusedChip = -1;
        changed();
    }

    /** Adds a chip without calling back and without a shake. Returns false for a duplicate, a full box or nothing to add. */
    public boolean addTag(String tag) {
        String clean = tag == null ? "" : clean(tag);
        if (clean.isEmpty() || indexOf(clean) >= 0 || isFull() || (rejectInvalid && reasonFor(clean) != null)) {
            return false;
        }
        chips.add(chipOf(clean));
        changed();
        return true;
    }

    /** Removes a chip without calling back. Returns false when it is not there. */
    public boolean removeTag(String tag) {
        int at = tag == null ? -1 : indexOf(clean(tag));
        if (at < 0) {
            return false;
        }
        dropChip(at);
        return true;
    }

    public boolean isFull() {
        return maxTags > 0 && chips.size() >= maxTags;
    }

    /** The chips with the invalid look. */
    public List<String> invalidTags() {
        List<String> out = new ArrayList<>();
        for (Chip chip : chips) {
            if (chip.reason != null) {
                out.add(chip.text);
            }
        }
        return out;
    }

    /** What is being typed. */
    public String text() {
        return text;
    }

    /** Sets what is being typed, with the caret at its end, without calling back. */
    public void setText(String value) {
        text = value == null ? "" : cleanTyped(value);
        caret = text.length();
        anchor = caret;
        edited();
    }

    /** The caret, an index into {@link #text()}. */
    int caret() {
        return caret;
    }

    /** The selected part of {@link #text()}. */
    String selectedText() {
        return text.substring(Math.min(caret, anchor), Math.max(caret, anchor));
    }

    /** The selected chip, or -1 when the keyboard is on the text. */
    int selectedIndex() {
        return selected;
    }

    /** Selects a chip and scrolls it into view, without calling back. -1 goes back to the text. */
    void select(int index) {
        selected = index >= 0 && index < chips.size() ? index : -1;
        revealSelected();
    }

    /** True while a refusal shows. */
    boolean refusing() {
        return age() < refuseSpan();
    }

    /** The chip a refusal points at, or -1. */
    int refusedChip() {
        return refusing() ? refusedChip : -1;
    }

    /** How far the shake has moved what it moves, in pixels, right now. Always 0 under reduced motion. */
    int shakeOffset() {
        long age = age();
        if (reducedMotion() || age >= SHAKE_MS) {
            return 0;
        }
        float at = age / (float) SHAKE_MS;
        return Math.round(SHAKE_AMPLITUDE * (float) Math.sin(at * 2D * Math.PI * SHAKE_TURNS) * (1F - at));
    }

    /** How many lines the chips and the text take, in view or not. */
    int lineCount() {
        layout();
        return lines;
    }

    /** The first line in view. */
    int scroll() {
        layout();
        return lineBar.offset();
    }

    /** Scrolls to a line. */
    void setScroll(int line) {
        layout();
        lineBar.offset(line);
    }

    int maxScroll() {
        layout();
        return lineBar.max();
    }

    /** True when the scroll bar shows. */
    boolean overflows() {
        layout();
        return bar;
    }

    /** Where a chip is, before the view's cut, for a script or a test. */
    Box chipBox(int index) {
        layout();
        return new Box("chip" + index, lineLeft() + chipLeft[index], lineY(chipLine[index]), chipW[index], CHIP_H);
    }

    /** Where a chip's small x is. Empty when the chip is too narrow to have one. */
    Box closeBox(int index) {
        layout();
        if (!hasClose(index)) {
            return new Box("close" + index, 0, 0, 0, 0);
        }
        int right = lineLeft() + chipLeft[index] + chipW[index];
        return new Box("close" + index, right - CLOSE_ZONE, lineY(chipLine[index]), CLOSE_ZONE, CHIP_H);
    }

    /** Where the typed text starts. */
    Box inputBox() {
        layout();
        return new Box("input", lineLeft() + inputLeft, lineY(inputLine), inputW, CHIP_H);
    }

    /** The chip at a point, or -1. */
    int chipAt(double mx, double my) {
        layout();
        if (!inField(mx, my) || mx >= viewRight() || my < y + BORDER || my >= y + h - BORDER) {
            return -1;
        }
        for (int i = 0; i < chips.size(); i++) {
            if (lineShows(chipLine[i]) && chipBox(i).contains(mx, my)) {
                return i;
            }
        }
        return -1;
    }

    // ---- the list ----

    /** Opens the list, if it has something to show, and takes the panel's focus. */
    boolean openList() {
        if (!visible || !active) {
            return false;
        }
        if (host.focus() != this) {
            host.focus(this);
            hadFocus = true;
        }
        return openListNow();
    }

    /** Closes the list and keeps the text. */
    void closeList() {
        open = false;
        list.lit(-1);
        layer.visible = false;
        list.release();
    }

    boolean isOpen() {
        return open;
    }

    /** True when the list opened above the field, or last did. */
    boolean isAbove() {
        return list.above();
    }

    /** The list's place, once it is open. */
    Box popupBox() {
        place();
        return layer.box();
    }

    /** How many suggestions match the text. */
    int shownCount() {
        return shown.size();
    }

    String shownAt(int row) {
        return shown.get(row);
    }

    /** The row the keyboard is on, or -1. */
    int lit() {
        return list.lit();
    }

    /** How many rows the last frame drew, for a test of the list. */
    int drawnRows() {
        return drawnRows;
    }

    /** Where a row is, before the view's cut, for a script or a test. */
    Box rowBox(int row) {
        place();
        return new Box("row" + row, list.rowX(), list.rowY(row), list.rowW(), ROW);
    }

    private boolean openListNow() {
        if (open) {
            return true;
        }
        refilter();
        if (shown.isEmpty()) {
            return false;
        }
        open = true;
        layer.visible = true;
        list.reset();
        place();
        if (reducedMotion()) {
            glide.snap(true);
        } else {
            glide.replay();
        }
        return true;
    }

    private void moveLit(int by) {
        place();
        list.move(by);
    }

    /** Runs when the text or the chips change: the suggestions holding the text, best match first. */
    private void refilter() {
        String wanted = TextMatch.foldQuery(text);
        Set<String> taken = new HashSet<>();
        for (Chip chip : chips) {
            taken.add(chip.key);
        }
        long[] ranks = new long[options.size()];
        int count = 0;
        for (int i = 0; i < options.size(); i++) {
            TextMatch.Folded folded = optionFolds.get(i);
            if (folded.text.contains(wanted) && !taken.contains(keyOf(options.get(i)))) {
                ranks[count++] = TextMatch.rank(folded, wanted) | i;
            }
        }
        Arrays.sort(ranks, 0, count);
        List<String> found = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            found.add(options.get((int) (ranks[i] & TextMatch.INDEX_MASK)));
        }
        shown = found;
        list.count(count);
        list.offset(0);
        list.lit(-1);
        if (open && shown.isEmpty()) {
            closeList();
        }
    }

    // ---- chips and text ----

    private String keyOf(String clean) {
        return caseSensitive ? clean : clean.toLowerCase(Locale.ROOT);
    }

    private int indexOf(String clean) {
        String key = keyOf(clean);
        for (int i = 0; i < chips.size(); i++) {
            if (chips.get(i).key.equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private String reasonFor(String clean) {
        return validator == null ? null : validator.apply(clean);
    }

    private Chip chipOf(String clean) {
        return new Chip(clean, keyOf(clean), reasonFor(clean));
    }

    /** What a chip may hold: no control characters, no section sign, single spaces, trimmed, not too long. */
    private String clean(String raw) {
        String typed = cleanTyped(raw).strip();
        StringBuilder out = new StringBuilder(typed.length());
        boolean space = false;
        for (int i = 0; i < typed.length(); i++) {
            char c = typed.charAt(i);
            if (c != ' ' || !space) {
                out.append(c);
            }
            space = c == ' ';
        }
        return cut(out.toString(), maxLength);
    }

    private static String cut(String value, int length) {
        if (value.length() <= length) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(length - 1)) ? length - 1 : length;
        return value.substring(0, end).strip();
    }

    private static boolean storable(int codePoint) {
        return codePoint >= 0x20 && codePoint != 0x7F && codePoint != 0xA7;
    }

    private static String cleanTyped(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        raw.codePoints().forEach(c -> {
            if (storable(c)) {
                out.appendCodePoint(c);
            } else if (c == '\t') {
                out.append(' ');
            }
        });
        return out.toString();
    }

    private boolean isSeparator(int codePoint) {
        return separators.indexOf(codePoint) >= 0;
    }

    private void changed() {
        dirty = true;
        refilter();
        layout();
    }

    private void edited() {
        caretSince = BaseComponent.clock().getAsLong();
        dirty = true;
        boolean wasOpen = open;
        refilter();
        if (focused && active && !text.isEmpty() && !open && !options.isEmpty()) {
            openListNow();
        } else if (wasOpen && text.isEmpty()) {
            closeList();
        }
        revealInput();
    }

    private void notifyAdded(String tag) {
        if (onAdd != null) {
            onAdd.accept(tag);
        }
        if (onChange != null) {
            onChange.accept(tags());
        }
    }

    private void refuse(String tag, Refusal why, int chip) {
        refusedAt = BaseComponent.clock().getAsLong();
        refusedChip = chip;
        refusedAll = why == Refusal.FULL;
        refusedInput = why == Refusal.INVALID;
        if (chip >= 0) {
            layout();
            revealLine(chipLine[chip]);
        }
        if (onRefuse != null) {
            onRefuse.accept(tag, why);
        }
    }

    /** Takes a clean text from the player. Returns why it was refused, or null when it is now a chip. */
    private Refusal take(String clean) {
        int same = indexOf(clean);
        if (same >= 0) {
            refuse(clean, Refusal.DUPLICATE, same);
            return Refusal.DUPLICATE;
        }
        if (isFull()) {
            refuse(clean, Refusal.FULL, -1);
            return Refusal.FULL;
        }
        if (rejectInvalid && reasonFor(clean) != null) {
            refuse(clean, Refusal.INVALID, -1);
            return Refusal.INVALID;
        }
        chips.add(chipOf(clean));
        dirty = true;
        return null;
    }

    /** Commits what was typed. A refused text stays in the field. */
    private void commitTyped() {
        String clean = clean(text);
        if (clean.isEmpty()) {
            clearText();
            return;
        }
        if (take(clean) == null) {
            BaseComponent.click();
            closeList();
            clearText();
            changed();
            revealInput();
            notifyAdded(clean);
        }
    }

    private void clearText() {
        text = "";
        caret = 0;
        anchor = 0;
        inputScroll = 0;
        dirty = true;
        refilter();
    }

    private void takeSuggestion(String option) {
        String clean = clean(option);
        if (take(clean) == null) {
            BaseComponent.click();
            closeList();
            clearText();
            changed();
            revealInput();
            notifyAdded(clean);
        } else {
            closeList();
        }
    }

    /** Removes the chip without a callback and keeps the selection on the same chip. */
    private void dropChip(int at) {
        chips.remove(at);
        if (selected == at) {
            selected = -1;
        } else if (selected > at) {
            selected--;
        }
        refusedChip = -1;
        heldClose = -1;
        changed();
    }

    /** The player removes a chip. */
    private void removeByPlayer(int at, int selectAfter) {
        String gone = chips.get(at).text;
        BaseComponent.click();
        dropChip(at);
        select(selectAfter >= 0 && selectAfter < chips.size() ? selectAfter : -1);
        if (selected < 0) {
            revealInput();
        }
        if (onRemove != null) {
            onRemove.accept(gone);
        }
        if (onChange != null) {
            onChange.accept(tags());
        }
    }

    private boolean hasSelection() {
        return caret != anchor;
    }

    private void eraseSelection() {
        int from = Math.min(caret, anchor);
        int to = Math.max(caret, anchor);
        text = text.substring(0, from) + text.substring(to);
        caret = from;
        anchor = from;
    }

    private void insert(String value) {
        if (hasSelection()) {
            eraseSelection();
        }
        int room = maxLength - text.length();
        String piece = cut(value, Math.max(1, room));
        if (room <= 0 || piece.isEmpty()) {
            return;
        }
        text = text.substring(0, caret) + piece + text.substring(caret);
        caret += piece.length();
        anchor = caret;
        edited();
    }

    private int before(int index) {
        int at = Math.max(0, index - 1);
        return at > 0 && Character.isLowSurrogate(text.charAt(at)) ? at - 1 : at;
    }

    private int after(int index) {
        int at = Math.min(text.length(), index + 1);
        return at < text.length() && Character.isLowSurrogate(text.charAt(at)) ? at + 1 : at;
    }

    private void moveCaret(int index, boolean extend) {
        selected = -1;
        caret = Math.max(0, Math.min(text.length(), index));
        if (!extend) {
            anchor = caret;
        }
        caretSince = BaseComponent.clock().getAsLong();
        dirty = true;
        revealInput();
    }

    private void paste() {
        TextFieldComponent.Clipboard clip = TextFieldComponent.clipboard;
        String value = clip == null ? null : clip.get();
        if (value == null || value.isEmpty()) {
            return;
        }
        boolean list = false;
        for (int i = 0; i < value.length() && !list; i++) {
            char c = value.charAt(i);
            list = c == '\n' || c == '\r' || isSeparator(c);
        }
        selected = -1;
        if (!list) {
            if (isFull()) {
                refuse(clean(value), Refusal.FULL, -1);
            } else {
                insert(cleanTyped(value));
            }
            return;
        }
        if (hasSelection()) {
            eraseSelection();
        }
        pasteList(text.substring(0, caret) + value + text.substring(caret));
    }

    /** Refused pieces are dropped, not left in the field, or one bad piece would hold up the rest of a long list. */
    private void pasteList(String value) {
        List<String> added = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (int i = 0; i <= value.length(); i++) {
            char c = i < value.length() ? value.charAt(i) : ',';
            if (i == value.length() || c == '\n' || c == '\r' || isSeparator(c)) {
                String clean = clean(piece.toString());
                piece.setLength(0);
                if (!clean.isEmpty() && take(clean) == null) {
                    added.add(clean);
                }
            } else {
                piece.append(c);
            }
        }
        clearText();
        changed();
        revealInput();
        if (!added.isEmpty()) {
            BaseComponent.click();
            for (String tag : added) {
                if (onAdd != null) {
                    onAdd.accept(tag);
                }
            }
            if (onChange != null) {
                onChange.accept(tags());
            }
        }
    }

    private void copy(boolean cut) {
        TextFieldComponent.Clipboard clip = TextFieldComponent.clipboard;
        if (selected >= 0) {
            if (clip != null) {
                clip.set(chips.get(selected).text);
            }
            if (cut) {
                removeByPlayer(selected, selected);
            }
        } else if (hasSelection()) {
            if (clip != null) {
                clip.set(selectedText());
            }
            if (cut) {
                eraseSelection();
                edited();
            }
        } else if (text.isEmpty() && !chips.isEmpty() && clip != null) {
            clip.set(String.join(", ", tags()));
        }
    }

    // ---- geometry ----

    private boolean inField(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Lines that fit whole: no chip is ever cut by the top or bottom of the box. */
    private int visibleLines() {
        return Math.max(0, (h - 2) / LINE);
    }

    private int barX() {
        return x + w - BAR - BORDER;
    }

    private int viewRight() {
        return bar ? barX() : x + w - BORDER;
    }

    private int lineLeft() {
        return x + SIDE;
    }

    private int lineRoom(boolean withBar) {
        return Math.max(1, w - 2 * SIDE - (withBar ? BAR : 0));
    }

    /** The lines in view sit in the middle of the box: what is left over is less than one line. */
    private int linesTop() {
        int shows = Math.max(1, visibleLines());
        return y + (h - shows * LINE + LINE_GAP) / 2;
    }

    private int lineY(int line) {
        return linesTop() + (line - lineBar.offset()) * LINE;
    }

    private boolean lineShows(int line) {
        return line >= lineBar.offset() && line < lineBar.offset() + visibleLines();
    }

    private int naturalWidth(Chip chip) {
        return CHIP_PAD + markRoom(chip) + TextMeasure.width(chip.text) + CLOSE_ZONE;
    }

    private int markRoom(Chip chip) {
        return chip.reason == null ? 0 : MARK_LEFT + MARK[0].length() + MARK_GAP - CHIP_PAD;
    }

    private boolean hasClose(int index) {
        return chipW[index] >= MIN_CHIP_FOR_CLOSE;
    }

    private String countText() {
        return chips.size() + "/" + maxTags;
    }

    private boolean countShows(int room) {
        return maxTags > 0 && showCount && TextMeasure.width(countText()) + COUNT_GAP + MIN_INPUT <= room;
    }

    private void layout() {
        if (!dirty && laidW == w && laidH == h) {
            return;
        }
        dirty = false;
        if (chipLeft.length != chips.size()) {
            chipLeft = new int[chips.size()];
            chipLine = new int[chips.size()];
            chipW = new int[chips.size()];
        }
        flow(lineRoom(false));
        int before = h;
        if (growTo > 0) {
            int wanted = lines * LINE - LINE_GAP + 4;
            h = Math.max(baseH, Math.min(Math.max(baseH, growTo), wanted));
        }
        bar = lines > visibleLines() && visibleLines() > 0;
        if (bar) {
            flow(lineRoom(true));
        }
        laidW = w;
        laidH = h;
        lineBar.content(lines, visibleLines());
        if (h != before && onResize != null) {
            onResize.accept(h);
        }
    }

    private void flow(int room) {
        int left = 0;
        int line = 0;
        for (int i = 0; i < chips.size(); i++) {
            int width = Math.min(room, naturalWidth(chips.get(i)));
            if (left > 0 && left + width > room) {
                line++;
                left = 0;
            }
            chipLeft[i] = left;
            chipLine[i] = line;
            chipW[i] = width;
            left += width + CHIP_GAP;
        }
        int countRoom = countShows(room) ? TextMeasure.width(countText()) + COUNT_GAP : 0;
        boolean typing = !isFull() || !text.isEmpty();
        int wants = typing ? Math.max(MIN_INPUT, TextMeasure.width(text) + TEXT_IN) : 0;
        if (left > 0 && left + wants + countRoom > room) {
            line++;
            left = 0;
        }
        inputLine = line;
        inputLeft = left;
        inputW = Math.max(0, room - left - countRoom);
        lines = line + 1;
    }

    private void revealLine(int line) {
        if (visibleLines() <= 0) {
            return;
        }
        if (line < lineBar.offset()) {
            lineBar.offset(line);
        } else if (line >= lineBar.offset() + visibleLines()) {
            lineBar.offset(line - visibleLines() + 1);
        }
    }

    private void revealInput() {
        layout();
        revealLine(inputLine);
    }

    private void revealSelected() {
        layout();
        if (selected >= 0) {
            revealLine(chipLine[selected]);
        }
    }

    private int trackY() { return y + 2; }
    private int trackH() { return Math.max(1, h - 4); }

    private boolean overBar(double mx, double my) {
        return bar && mx >= barX() && mx < x + w && my >= y && my < y + h;
    }

    private long age() {
        long age = Redraw.quietClock() - refusedAt;
        if (age < refuseSpan()) {
            Redraw.ask();
        }
        return age;
    }

    private int refuseSpan() {
        return reducedMotion() ? STILL_MS : SHAKE_MS;
    }

    // ---- the list's geometry ----

    /** Puts the list under the field, or above when that holds more of it. */
    private void place() {
        list.place(host.theme(), x, y, w, h, area != null ? area : host.bounds(), 0, MIN_POPUP_W, maxRows);
        layer.x = list.x;
        layer.y = list.y;
        layer.w = list.w;
        layer.h = list.h;
    }

    /** Hidden or disabled closes the list; the focus going elsewhere commits the text. Returns whether the list is open. */
    private boolean sync() {
        if (open && (!visible || !active)) {
            closeList();
        }
        layer.visible = open;
        boolean live = focused && active && visible;
        if (hadFocus && !live) {
            hadFocus = false;
            closeList();
            selectingText = false;
            if (commitOnLeave && active && visible) {
                commitTyped();
            }
        } else if (live) {
            hadFocus = true;
        }
        return open;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        sync();
        layout();
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        boolean live = focused && active;
        frame.field(d, p, x, y, w, h, live);
        if (visibleLines() > 0 && w > 2 * SIDE) {
            boolean covered = open && layer.box().contains(mx, my);
            int hoverChip = active && hovered && !covered && !lineBar.dragging() && !selectingText ? chipAt(mx, my) : -1;
            chipHover.step(hoverChip);
            boolean overClose = hoverChip >= 0 && closeBox(hoverChip).contains(mx, my);
            int shift = refusedAll ? shakeOffset() : 0;
            d.scissor(x + BORDER, y + BORDER, viewRight(), y + h - BORDER);
            for (int i = 0; i < chips.size(); i++) {
                if (lineShows(chipLine[i])) {
                    int dx = shift + (i == refusedChip ? shakeOffset() : 0);
                    drawChip(d, p, frame, i, dx, i == hoverChip, i == hoverChip && overClose);
                }
            }
            if (lineShows(inputLine)) {
                drawInput(d, p, frame, live, shift);
                drawCount(d, p, frame);
            }
            d.endScissor();
            if (bar) {
                lineBar.draw(d, t, barX(), trackY(), trackH(), active && hovered && overBar(mx, my));
                if (!active) {
                    d.fill(barX(), trackY(), barX() + BAR, trackY() + trackH(), Shapes.alpha(FormLook.fieldBack(p, frame), DISABLED_BAR));
                }
            }
        } else {
            chipHover.step(-1);
        }
        if (open && !layered) {
            layer.draw(d, t, mx, my, delta);
        }
    }

    private void drawChip(Draw d, Palette p, FrameStyle frame, int index, int dx, boolean hovered, boolean overClose) {
        Chip chip = chips.get(index);
        int cx = lineLeft() + chipLeft[index] + dx;
        int cy = lineY(chipLine[index]);
        int cw = chipW[index];
        boolean isSelected = index == selected;
        boolean refused = refusing() && index == refusedChip;
        boolean invalid = chip.reason != null;
        int plainInk = frame.fieldText(p);

        int alert = alertInk(p, frame);
        int face;
        int edge;
        int ink;
        if (!active) {
            // The selection shows in the edge alone: a lighter face would swallow the dim text.
            face = Shapes.alpha(plainInk, DISABLED_FACE);
            edge = isSelected ? p.disabledText() : 0;
            ink = p.disabledText();
        } else if (refused) {
            face = Shapes.alpha(p.bad(), REFUSED_FACE);
            edge = alert;
            ink = inkOn(p, frame, face);
        } else if (isSelected) {
            // Not RowLook's ink: it judges on the palette's well, and a light theme's field is dark.
            face = RowLook.face(p, true, focused, true, hovered, chipHover.fade());
            edge = invalid ? alert : 0;
            ink = focused ? p.primaryText() : inkOn(p, frame, face);
        } else if (invalid) {
            face = Shapes.alpha(p.bad(), hovered ? INVALID_FACE + 0x20 : INVALID_FACE);
            edge = alert;
            ink = inkOn(p, frame, face);
        } else {
            float lit = hovered ? chipHover.fade() : 0F;
            face = Shapes.alpha(plainInk, Math.round(REST_FACE + (HOVER_FACE - REST_FACE) * lit));
            edge = 0;
            ink = plainInk;
        }
        if (edge != 0) {
            frame.plate(d, p, cx, cy, cw, CHIP_H, face, edge);
        } else {
            frame.raised(d, p, cx, cy, cw, CHIP_H, face, Shapes.Relief.FLAT);
        }

        boolean close = hasClose(index);
        int left = cx + CHIP_PAD;
        if (invalid) {
            drawMark(d, cx + MARK_LEFT, cy + MARK_TOP, ink);
            left = cx + MARK_LEFT + MARK[0].length() + MARK_GAP;
        }
        int right = cx + cw - (close ? CLOSE_ZONE : CHIP_PAD);
        if (close) {
            drawClose(d, cx + cw - CLOSE_ZONE, cy, ink, overClose, index);
        }
        d.textFit(chip.text, left, RowLook.textY(cy, CHIP_H), Math.max(0, right - left), ink);
    }

    private static void drawMark(Draw d, int left, int top, int ink) {
        for (int row = 0; row < MARK.length; row++) {
            for (int col = 0; col < MARK[row].length(); col++) {
                if (MARK[row].charAt(col) == '#') {
                    d.fill(left + col, top + row, left + col + 1, top + row + 1, ink);
                }
            }
        }
    }

    /** Drawn by hand: the theme's close icon is as tall as the chip's text and would shout on every chip. */
    private void drawClose(Draw d, int zoneX, int cy, int ink, boolean over, int index) {
        int left = zoneX + CLOSE_LEFT;
        int top = cy + CLOSE_TOP;
        if (over) {
            int alpha = heldClose == index ? CLOSE_HELD : CLOSE_HOVER;
            d.fill(left - 1, top - 1, left + CLOSE_SIZE + 1, top + CLOSE_SIZE + 1, Shapes.alpha(ink, alpha));
        }
        int color = over || !active ? ink : Shapes.alpha(ink, CLOSE_REST);
        for (int i = 0; i < CLOSE_SIZE; i++) {
            d.fill(left + i, top + i, left + i + 1, top + i + 1, color);
            if (i != CLOSE_SIZE / 2) {
                d.fill(left + CLOSE_SIZE - 1 - i, top + i, left + CLOSE_SIZE - i, top + i + 1, color);
            }
        }
    }

    private void drawInput(Draw d, Palette p, FrameStyle frame, boolean live, int shift) {
        int left = lineLeft() + inputLeft + shift + TEXT_IN;
        int line = lineY(inputLine);
        int textY = RowLook.textY(line, CHIP_H);
        boolean none = text.isEmpty();
        if (none && chips.isEmpty() && !placeholder.isEmpty()) {
            int color = active ? frame.fieldHint(p) : p.disabledText();
            d.textFit(placeholder, left, textY, Math.max(0, inputW - TEXT_IN), color);
        }
        if (inputW <= 0) {
            return;
        }
        int offset = keepCaretInView();
        boolean refused = refusing() && refusedInput;
        if (shift == 0 && refused) {
            left += shakeOffset();
        }
        int ink = !active ? p.disabledText() : refused ? alertInk(p, frame) : frame.fieldText(p);
        if (!none) {
            d.scissor(left - 1, line, left + inputW, line + CHIP_H);
            int from = Math.min(caret, anchor);
            int to = Math.max(caret, anchor);
            if (live && from != to) {
                int a = left - offset + TextMeasure.width(text.substring(0, from));
                int b = a + TextMeasure.width(text.substring(from, to));
                d.fill(a, textY - 1, b, textY + 9, p.primary());
                d.text(text.substring(0, from), left - offset, textY, ink);
                d.text(text.substring(from, to), a, textY, p.primaryText());
                d.text(text.substring(to), b, textY, ink);
            } else {
                d.text(text, left - offset, textY, ink);
            }
            d.endScissor();
        }
        if (live && selected < 0 && caretShown() && (!none || !isFull())) {
            int cx = left - offset + TextMeasure.width(text.substring(0, caret));
            // In the space after the previous letter, not on the first column of the next one.
            d.fill(cx - 1, textY - 1, cx, textY + 9, frame.fieldText(p));
        }
    }

    /** How far the text is scrolled sideways. Only a text longer than the whole line scrolls. */
    private int keepCaretInView() {
        int full = TextMeasure.width(text);
        int room = Math.max(1, inputW - TEXT_IN);
        if (full <= room) {
            inputScroll = 0;
            return 0;
        }
        int at = TextMeasure.width(text.substring(0, caret));
        if (at < inputScroll) {
            inputScroll = at;
        } else if (at > inputScroll + room) {
            inputScroll = at - room;
        }
        inputScroll = Math.max(0, Math.min(full - room, inputScroll));
        return inputScroll;
    }

    private void drawCount(Draw d, Palette p, FrameStyle frame) {
        if (!countShows(lineRoom(bar))) {
            return;
        }
        int right = lineLeft() + lineRoom(bar);
        int color = !active ? p.disabledText() : refusing() && refusedAll ? alertInk(p, frame) : frame.fieldHint(p);
        d.text(countText(), right - TextMeasure.width(countText()), RowLook.textY(lineY(inputLine), CHIP_H), color);
    }

    /** A translucent face as it looks on the box. */
    private static int over(Palette p, FrameStyle frame, int face) {
        return Shapes.mix(FormLook.fieldBack(p, frame), FormLook.solid(face), (face >>> 24) / 255F);
    }

    /** The ink that reads on a chip's face over the field. */
    private static int inkOn(Palette p, FrameStyle frame, int face) {
        return Shapes.textOn(p, over(p, frame, face));
    }

    /**
     * The red of an alert inside the dark field. A light palette's red is made for its light surface, so it goes
     * halfway or more toward its dark variant's red, as far as 4.5:1 on the field: still saturated, no pale pink.
     */
    private static int alertInk(Palette p, FrameStyle frame) {
        int field = FormLook.fieldBack(p, frame) | 0xFF000000;
        Palette dark = DarkTwin.of(p);
        if (dark == p) {
            return Ink.readable(p, p.bad(), field, Ink.TEXT);
        }
        for (int step = 5; step < 10; step++) {
            int red = Shapes.mix(p.bad() | 0xFF000000, dark.bad() | 0xFF000000, step / 10F);
            if (Shapes.contrast(red, field) >= Ink.TEXT) {
                return red;
            }
        }
        return dark.bad();
    }

    private boolean caretShown() {
        if (reducedMotion()) {
            return true;
        }
        long since = BaseComponent.clock().getAsLong() - caretSince;
        return since < 0 || since % (2 * TextFieldComponent.BLINK_MS) < TextFieldComponent.BLINK_MS;
    }

    private void drawPopup(Draw d, Theme t, int mx, int my) {
        place();
        drawnRows = 0;
        boolean moving = !reducedMotion() && glide.moving();
        list.draw(d, t, mx, my, layer, "", moving ? Math.min(1F, glide.progress()) : 1F);
    }

    // ---- tooltips ----

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (sync() && !layered) {
            return layer.tooltipAt(mx, my);
        }
        return fieldTooltip(mx, my);
    }

    private List<String> fieldTooltip(double mx, double my) {
        if (!visible || !inField(mx, my)) {
            return null;
        }
        if (active) {
            int at = chipAt(mx, my);
            if (at >= 0) {
                if (closeTooltip != null && closeBox(at).contains(mx, my)) {
                    return closeTooltip;
                }
                Chip chip = chips.get(at);
                if (chipTooltip != null) {
                    List<String> lines = chipTooltip.apply(chip.text);
                    if (lines != null && !lines.isEmpty()) {
                        return lines;
                    }
                }
                if (chip.reason != null && !chip.reason.isEmpty()) {
                    return List.of(chip.reason);
                }
                if (tooltip == null && isCut(at)) {
                    return List.of(chip.text);
                }
            }
        }
        return tooltip;
    }

    private boolean isCut(int index) {
        Chip chip = chips.get(index);
        return naturalWidth(chip) > chipW[index];
    }

    // ---- input ----

    private boolean accepts() {
        return visible && active && settled();
    }

    @Override
    protected boolean takesFocus() {
        return visible && active;
    }

    @Override
    protected boolean navigable() {
        return visible && active && settled();
    }

    /** While the list is open, every click belongs to it when it draws itself. */
    @Override
    public boolean isOver(double mx, double my) {
        return inField(mx, my) || (open && !layered);
    }

    @Override
    public void tick() {
        sync();
    }

    @Override
    protected boolean activate() {
        if (!accepts()) {
            return false;
        }
        commitTyped();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        sync();
        if (open && !layered) {
            return layer.mouseClicked(mx, my, button);
        }
        if (!accepts() || button != 0 || !inField(mx, my)) {
            return false;
        }
        return pressField(mx, my);
    }

    private boolean pressField(double mx, double my) {
        hadFocus = true;
        layout();
        if (overBar(mx, my)) {
            lineBar.press(mx, my, barX(), trackY(), trackH());
            return true;
        }
        int at = chipAt(mx, my);
        if (at >= 0) {
            if (closeBox(at).contains(mx, my)) {
                heldClose = at;
                return true;
            }
            BaseComponent.click();
            closeList();
            select(at);
            return true;
        }
        selected = -1;
        moveCaret(indexAtX(mx), false);
        selectingText = true;
        return true;
    }

    private int indexAtX(double mx) {
        keepCaretInView();
        int at = (int) Math.floor(mx) - (lineLeft() + inputLeft + TEXT_IN) + inputScroll;
        if (at <= 0) {
            return 0;
        }
        int index = 0;
        while (index < text.length()) {
            int next = after(index);
            int middle = (TextMeasure.width(text.substring(0, index)) + TextMeasure.width(text.substring(0, next))) / 2;
            if (at < middle) {
                return index;
            }
            index = next;
        }
        return text.length();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (list.dragging() && !layered) {
            return layer.mouseDragged(mx, my, button, dx, dy);
        }
        if (lineBar.drag(my)) {
            return true;
        }
        if (selectingText && active) {
            caret = indexAtX(mx);
            caretSince = BaseComponent.clock().getAsLong();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = lineBar.dragging() || selectingText;
        lineBar.release();
        selectingText = false;
        int held = heldClose;
        heldClose = -1;
        if (held >= 0 && held < chips.size() && accepts() && closeBox(held).contains(mx, my)) {
            removeByPlayer(held, -1);
            return true;
        }
        if (!layered && layer.mouseReleased(mx, my, button)) {
            return true;
        }
        return was || held >= 0;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (open && !layered) {
            return layer.mouseScrolled(mx, my, amount);
        }
        if (!visible || !settled() || !inField(mx, my) || !overflows()) {
            return false;
        }
        lineBar.wheel(amount, ScrollBar.WHEEL_ROWS);
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        sync();
        if (!accepts()) {
            return false;
        }
        layout();
        switch (key) {
            case ENTER -> {
                if (open && list.lit() >= 0) {
                    takeSuggestion(shown.get(list.lit()));
                } else if (selected >= 0) {
                    selected = -1;
                    revealInput();
                } else {
                    commitTyped();
                }
                return true;
            }
            case TAB -> {
                if (!shift && open && list.lit() >= 0) {
                    takeSuggestion(shown.get(list.lit()));
                    return true;
                }
                return false;
            }
            case ESCAPE -> {
                if (open) {
                    BaseComponent.click();
                    closeList();
                    return true;
                }
                if (selected >= 0) {
                    selected = -1;
                    revealInput();
                    return true;
                }
                return false;
            }
            case UP, DOWN -> {
                return arrowUpDown(key == InputKey.DOWN ? 1 : -1);
            }
            case BACKSPACE -> backspace();
            case DELETE -> delete();
            case LEFT -> left(shift);
            case RIGHT -> right(shift);
            case HOME -> {
                if (selected >= 0 || (caret == 0 && anchor == 0 && !shift)) {
                    if (!chips.isEmpty()) {
                        select(0);
                    }
                } else {
                    moveCaret(0, shift);
                }
            }
            case END -> moveCaret(text.length(), shift);
            case SELECT_ALL -> {
                selected = -1;
                anchor = 0;
                caret = text.length();
            }
            case COPY -> copy(false);
            case CUT -> copy(true);
            case PASTE -> paste();
            case SPACE -> { } // the character comes next, through charTyped
            default -> {
                return false;
            }
        }
        return true;
    }

    private void backspace() {
        long now = BaseComponent.clock().getAsLong();
        if (selected >= 0) {
            removeByPlayer(selected, selected > 0 ? selected - 1 : 0);
            return;
        }
        if (hasSelection()) {
            eraseSelection();
            lastErase = now;
            edited();
        } else if (caret > 0) {
            int from = before(caret);
            text = text.substring(0, from) + text.substring(caret);
            caret = from;
            anchor = from;
            lastErase = now;
            edited();
        } else if (text.isEmpty() && !chips.isEmpty()) {
            if (now - lastErase < BACKSPACE_PAUSE_MS) {
                lastErase = now; // still the key held from erasing the text
            } else if (confirmBackspace) {
                select(chips.size() - 1);
            } else {
                removeByPlayer(chips.size() - 1, -1);
            }
        }
    }

    private void delete() {
        if (selected >= 0) {
            removeByPlayer(selected, selected);
        } else if (hasSelection()) {
            eraseSelection();
            edited();
        } else if (caret < text.length()) {
            text = text.substring(0, caret) + text.substring(after(caret));
            anchor = caret;
            edited();
        }
    }

    private void left(boolean shift) {
        if (selected >= 0) {
            select(Math.max(0, selected - 1));
        } else if (hasSelection() && !shift) {
            moveCaret(Math.min(caret, anchor), false);
        } else if (caret > 0) {
            moveCaret(before(caret), shift);
        } else if (!shift && !chips.isEmpty()) {
            closeList();
            select(chips.size() - 1);
        }
    }

    private void right(boolean shift) {
        if (selected >= 0) {
            if (selected < chips.size() - 1) {
                select(selected + 1);
            } else {
                moveCaret(0, false);
            }
        } else if (hasSelection() && !shift) {
            moveCaret(Math.max(caret, anchor), false);
        } else {
            moveCaret(after(caret), shift);
        }
    }

    /** Returns false when there is nothing to walk, so that the panel's arrows move on. */
    private boolean arrowUpDown(int by) {
        if (open) {
            moveLit(by);
            return true;
        }
        if (selected >= 0) {
            walkLines(by);
            return true;
        }
        if (by > 0 && !options.isEmpty() && openListNow()) {
            moveLit(1);
            return true;
        }
        if (by < 0 && !chips.isEmpty()) {
            int line = inputLeft > 0 ? inputLine : inputLine - 1;
            if (line >= 0) {
                selectNear(line, inputLeft);
                return true;
            }
        }
        return false;
    }

    private void walkLines(int by) {
        int line = chipLine[selected] + by;
        int middle = chipLeft[selected] + chipW[selected] / 2;
        if (line < 0) {
            return;
        }
        if (line > chipLine[chips.size() - 1]) {
            selected = -1;
            revealInput();
            return;
        }
        selectNear(line, middle);
    }

    private void selectNear(int line, int middle) {
        int best = -1;
        int bestGap = Integer.MAX_VALUE;
        for (int i = 0; i < chips.size(); i++) {
            if (chipLine[i] == line) {
                int gap = Math.abs(chipLeft[i] + chipW[i] / 2 - middle);
                if (gap < bestGap) {
                    bestGap = gap;
                    best = i;
                }
            }
        }
        if (best >= 0) {
            select(best);
        }
    }

    @Override
    public boolean charTyped(int codePoint) {
        sync();
        if (!accepts()) {
            return false;
        }
        if (isSeparator(codePoint)) {
            selected = -1;
            commitTyped();
            return true;
        }
        if (!storable(codePoint) || (codePoint == ' ' && text.isEmpty())) {
            return true;
        }
        if (isFull()) {
            refuse(new String(Character.toChars(codePoint)), Refusal.FULL, -1);
            return true;
        }
        selected = -1;
        insert(new String(Character.toChars(codePoint)));
        return true;
    }

    // ---- the list as a widget ----

    private void refocus() {
        if (host.focus() != this) {
            host.focus(this);
        }
        hadFocus = true;
    }

    /** The list as the panel's widget: draws it and takes the clicks, wheel turns and drags while it is open. */
    private final class Layer extends BaseComponent implements PopupList.Rows {
        Layer() {
            super(0, 0, 0, 0);
            visible = false;
        }

        @Override
        public int face(Palette p, int row, boolean hover, float fade) {
            if (row == list.lit()) {
                return hover ? p.primaryHover() : p.primary();
            }
            return hover ? RowLook.tint(p, fade) : 0;
        }

        @Override
        public void draw(Draw d, Theme t, int row, int x, int y, int w, int h, int face) {
            int ink = row == list.lit() ? t.palette().primaryText() : Ink.on(t.palette(), face);
            d.textFit(shown.get(row), x + ROW_LEFT, RowLook.textY(y, h), Math.max(0, w - ROW_LEFT - ROW_RIGHT), ink);
            drawnRows++;
        }

        @Override
        protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
            if (sync()) {
                drawPopup(d, t, mx, my);
            }
        }

        /** While open, every click belongs to it: the first one outside only closes the list. */
        @Override
        public boolean isOver(double mx, double my) {
            return open;
        }

        @Override
        public List<String> tooltipAt(double mx, double my) {
            if (!sync()) {
                return null;
            }
            place();
            if (inField(mx, my)) {
                List<String> lines = fieldTooltip(mx, my);
                return lines == null ? List.of() : lines;
            }
            int row = list.rowAt(mx, my);
            if (row < 0) {
                return List.of(); // empty, not null: a panel goes on to the widgets beneath on null
            }
            String label = shown.get(row);
            return TextMeasure.width(label) > list.rowW() - ROW_LEFT - ROW_RIGHT ? List.of(label) : List.of();
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            // No sync here: the panel took the focus away just before this, as this widget takes none.
            if (!open || !TagInputComponent.this.visible || !TagInputComponent.this.active) {
                return false;
            }
            place();
            if (inField(mx, my)) {
                refocus();
                return button != 0 || pressField(mx, my);
            }
            if (!list.contains(mx, my)) {
                BaseComponent.click();
                host.focus(null);
                sync();
                return true;
            }
            refocus();
            if (button != 0 || glide.moving() || list.pressBar(mx, my)) {
                return true;
            }
            int row = list.rowAt(mx, my);
            if (row >= 0) {
                takeSuggestion(shown.get(row));
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            return list.drag(my);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            return list.release();
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double amount) {
            if (!sync()) {
                return false;
            }
            place();
            if (list.contains(mx, my)) {
                list.wheel(amount);
            }
            return true;
        }
    }
}
