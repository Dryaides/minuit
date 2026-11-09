package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A text field with a list of suggestions that follows what is typed. Options starting with the text
 * come first, in their own order, then the fuzzy matches, best first; the letters that matched are
 * marked. Only the rows in view are drawn, and the filter runs when the text changes, not on every frame,
 * so a list of 10,000 options stays light.
 *
 * <pre>{@code
 * public class BlocksPanel extends PanelComponent {
 *     public BlocksPanel(Theme theme, Words words, int left, int top) {
 *         super(theme, words, left, top);
 *         ComboBoxComponent<Block> block = add(new ComboBoxComponent<>(at(new Box("block", 10, 10, 160, 16)),
 *                 this, blocks, Block::name)
 *                 .placeholder(words.get("mymod.block"))
 *                 .emptyText(words.get("mymod.no_block"))
 *                 .onSelect(this::use));
 *         add(new ButtonComponent(at(new Box("ok", 10, 34, 80, 20)), words.get("gui.done"), this::save));
 *         // Last, so that the list is drawn over the button and offered every input first.
 *         add(block.popup());
 *     }
 * }
 * }</pre>
 *
 * <h2>Putting it in a panel</h2>
 * Add {@link #popup()} <b>after every other widget</b> of the panel, one for each combo box. Without
 * that call the combo box draws the list itself, which only works when the combo box comes after what
 * the list can cover. A panel's {@code overlay} is not used because it gets no wheel turns and no drags,
 * which a list of 10,000 needs. {@link #area(Box)} is the box the list has to stay in: the panel by
 * default, or pass the screen's box for a list that may leave the panel.
 *
 * <h2>Keys</h2>
 * <ul>
 * <li>Typing filters and opens the list. Left, right, home, end, copy, paste and the rest act on the
 * text as in a {@link TextFieldComponent}.</li>
 * <li>Down and up open the list, then move and wrap. Enter takes the lit row and closes.</li>
 * <li>Tab writes the lit row, or the first one, into the text without choosing it. With nothing more
 * to complete it moves on as usual, which commits.</li>
 * <li>Escape closes the list and keeps the text. With the list closed, it lets go of the focus.</li>
 * </ul>
 *
 * <h2>Leaving the field</h2>
 * By default only an option can be the value: on leaving, text that is an option's label (ignoring
 * case and accents) chooses it, an empty text clears the value, and anything else goes back to the
 * value's label. With {@link #freeText(boolean)} the text is kept and {@link #onCommit} gets it.
 * While the list is open, the first click outside only closes it, so it presses nothing beneath.
 *
 * <p>{@code active = false} or {@code visible = false} closes an open list. The widgets beneath still
 * show their hover, because a panel gives them the real pointer position. Only a direct child of a
 * panel has been tried: not one inside a {@link ScrollComponent}, a {@link WindowComponent} or a dialog.
 */
public class ComboBoxComponent<T> extends BaseComponent implements ItemSource {
    /** Height of a row of the list, the same as the rows of a context menu. */
    public static final int ROW = RowLook.POPUP_ROW;
    private static final int LEFT = RowLook.POPUP_PAD;
    private static final int RIGHT = RowLook.POPUP_PAD;
    private static final int COLUMN_GAP = 3;
    private static final int MIN_POPUP_W = 60;
    private static final int OPEN_MS = 110;
    /** The field's own inner margin, as a {@link TextFieldComponent} has it. */
    private static final int TEXT_PAD = 4;
    private static final int BUTTON_W = 12;
    /** Between the text's end and the first button, and between the last button and the frame's border. */
    private static final int BUTTON_MARGIN = 1;
    private static final int BORDER = 1;
    /** How much of the field's height stays free above and below a button's wash: bevels live there. */
    private static final int WASH_INSET = 3;
    private static final int MIN_BUTTON_H = 9;
    /** Less text room than this, and the buttons go: first clear, then the arrow. */
    private static final int MIN_TEXT = 24;
    private static final int CLEAR = 0;
    private static final int ARROW = 1;
    private static final int NO_BUTTON = -1;
    private static final int HOVER_WASH = 0x28;
    private static final int HELD_WASH = 0x50;
    private static final int CHOSEN_ALPHA = 0x80;
    private static final int CHOSEN_HOVER_ALPHA = 0xA0;
    private static final int DISABLED_HINT_ALPHA = 0xB0;
    private static final int STITCH_BLEED = 6;
    /** Where the field is cut when a picture comes first: past its border and corner, short of its text. */
    private static final int LEAD_CUT = 3;

    private final PanelComponent host;
    private final Function<T, String> label;
    private final TextFieldComponent field;
    private final Layer layer = new Layer();
    private final RowLook.HoverFade buttonHover = new RowLook.HoverFade();
    private PopupList list = new PopupList(ROW);

    private List<T> items = List.of();
    private String[] labels = new String[0];
    private TextMatch.Folded[] folded = new TextMatch.Folded[0];
    private Map<String, Integer> byFolded;

    private int[] shown = new int[0];
    private int shownCount;
    private String query = "";

    private String placeholder = "";
    private String emptyText;
    private Words words;
    private boolean freeText;
    private boolean clearable = true;
    private boolean fuzzy = true;
    private boolean openOnClick = true;
    private boolean layered;
    private int maxRows = 8;
    private int popupWidth;
    private Box area;
    private Function<T, List<String>> rowTooltip;
    private List<String> clearTooltip;
    private Consumer<T> onSelect;
    private Consumer<String> onText;
    private Consumer<String> onCommit;

    private T selected;
    private String committed = "";
    private boolean filtering;
    private boolean open;
    private boolean hadFocus;
    private int heldButton = NO_BUTTON;
    private int drawnRows;
    private Theme lastTheme;
    private Function<T, IconArt> iconArt;
    private boolean itemTooltips = true;
    private boolean itemTipShown;
    private final Glide glide = new Glide(Slide.DOWN, OPEN_MS);

    /**
     * @param box the field
     * @param host the panel this combo box is added to
     * @param options what the list offers; nulls are dropped, null means none
     * @param label an option's text
     */
    public ComboBoxComponent(Box box, PanelComponent host, Collection<? extends T> options, Function<T, String> label) {
        super(box);
        this.host = Objects.requireNonNull(host, "host");
        this.label = Objects.requireNonNull(label, "label");
        this.field = new TextFieldComponent(box, "", this::edited);
        setOptions(options);
    }

    // ---- setup ----

    /** What the empty field shows while it has no focus. */
    public ComboBoxComponent<T> placeholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    /** The line the list shows with no option to offer. English by default: pass your own translation. */
    /** The line the list shows with no option to offer; the lang entry {@code minuit.no_results} by default. */
    public ComboBoxComponent<T> emptyText(String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    /** True to keep text that is no option. Enter then takes the text itself until an arrow lights a row. Off by default. */
    public ComboBoxComponent<T> freeText(boolean freeText) {
        this.freeText = freeText;
        return this;
    }

    /** Whether the field shows a button that empties it. On by default. */
    public ComboBoxComponent<T> clearable(boolean clearable) {
        this.clearable = clearable;
        return this;
    }

    /** False to match only letters in a row: prefixes first, then the same letters anywhere. On by default. */
    public ComboBoxComponent<T> fuzzy(boolean fuzzy) {
        this.fuzzy = fuzzy;
        refilter();
        return this;
    }

    /** False to open the list only by typing, the arrow button or the keys. On by default. */
    public ComboBoxComponent<T> openOnClick(boolean openOnClick) {
        this.openOnClick = openOnClick;
        return this;
    }

    /** How many rows the list shows before it scrolls. 8 by default; it shows fewer when the area is short. */
    public ComboBoxComponent<T> maxRows(int maxRows) {
        this.maxRows = Math.max(1, maxRows);
        return this;
    }

    /** The width of the list, when it should differ from the field's. It stays inside the area. */
    public ComboBoxComponent<T> popupWidth(int popupWidth) {
        this.popupWidth = Math.max(0, popupWidth);
        return this;
    }

    /** The box the list stays in, in the panel's coordinates. The panel's bounds by default. */
    public ComboBoxComponent<T> area(Box area) {
        this.area = area;
        return this;
    }

    public ComboBoxComponent<T> maxLength(int maxLength) {
        field.maxLength(maxLength);
        return this;
    }

    /** Lines for the row under the pointer. A label cut short shows in full without this. */
    public ComboBoxComponent<T> rowTooltip(Function<T, List<String>> rowTooltip) {
        this.rowTooltip = rowTooltip;
        return this;
    }

    /**
     * A picture before each option's label, and before the value's in the field; null for an option shows none.
     * With a picture the rows are 18 px tall, room for a 16 px item; give the field 18 px too. An option whose
     * label is null or empty and whose picture is an {@link ItemView} shows, and is searched by, the item's name
     * in the player's language.
     */
    public ComboBoxComponent<T> iconArt(Function<T, IconArt> iconArt) {
        this.iconArt = iconArt;
        list = new PopupList(iconArt != null ? ItemArt.ITEM_ROW : ROW);
        setOptions(items);
        if (selected != null && field.text().equals(committed)) {
            field.setText(textOf(selected));
            committed = field.text();
        }
        return this;
    }

    /** Whether an item row shows the game's own tooltip (its name without a world). On by default; a {@link #rowTooltip} wins. */
    public ComboBoxComponent<T> itemTooltips(boolean itemTooltips) {
        this.itemTooltips = itemTooltips;
        return this;
    }

    /** A tooltip for the clear button. */
    public ComboBoxComponent<T> clearTooltip(String... lines) {
        this.clearTooltip = lines == null || lines.length == 0 ? null : List.of(lines);
        return this;
    }

    /** Called when the value changes through the player, with null when it is cleared. Not through {@link #select}. */
    public ComboBoxComponent<T> onSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /** Called on every change the player makes to the text, Tab's completion and a choice included. */
    public ComboBoxComponent<T> onText(Consumer<String> onText) {
        this.onText = onText;
        return this;
    }

    /** With {@link #freeText(boolean)}: called when the text is settled by Enter, a choice, clear or leaving, if it changed. */
    public ComboBoxComponent<T> onCommit(Consumer<String> onCommit) {
        this.onCommit = onCommit;
        return this;
    }

    public ComboBoxComponent<T> active(boolean active) {
        this.active = active;
        sync();
        return this;
    }

    @Override
    public ComboBoxComponent<T> tooltip(String... lines) {
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

    /** New options. The value stays, matched by equality, even if no option has it any more. */
    public final void setOptions(Collection<? extends T> options) {
        List<T> copy = new ArrayList<>();
        if (options != null) {
            for (T option : options) {
                if (option != null) {
                    copy.add(option);
                }
            }
        }
        items = copy;
        int count = copy.size();
        labels = new String[count];
        folded = new TextMatch.Folded[count];
        byFolded = null;
        for (int i = 0; i < count; i++) {
            labels[i] = textOf(copy.get(i));
            folded[i] = TextMatch.fold(labels[i]);
        }
        shown = new int[count];
        refilter();
    }

    public List<T> options() {
        return Collections.unmodifiableList(items);
    }

    /** The value: the option chosen, or null. */
    public T selected() {
        return selected;
    }

    /** Sets the value and shows its label, without calling back. Null empties the field. */
    public void select(T option) {
        selected = option;
        String text = option == null ? "" : textOf(option);
        field.setText(text);
        committed = field.text();
        filtering = false;
        refilter();
    }

    public String text() {
        return field.text();
    }

    /** Sets the text as it is, without calling back. The value only follows when the field is next left. */
    public void setText(String text) {
        field.setText(text == null ? "" : text);
        filtering = open;
        refilter();
    }

    // ---- the list ----

    /**
     * Opens the list and takes the panel's focus, as the arrow button does.
     *
     * @return false when the combo box is hidden or disabled
     */
    public boolean open() {
        if (!visible || !active) {
            return false;
        }
        if (host.focus() != this) {
            host.focus(this);
            hadFocus = true;
        }
        openList(0);
        return true;
    }

    /** Closes the list and keeps the text. */
    public void close() {
        open = false;
        layer.visible = false;
        list.release();
    }

    public boolean isOpen() {
        return open;
    }

    boolean isAbove() {
        return list.above();
    }

    Box popupBox() {
        place();
        return layer.box();
    }

    int shownCount() {
        return shownCount;
    }

    T shownAt(int row) {
        return items.get(shown[row]);
    }

    int lit() {
        return list.lit();
    }

    /** How many rows the last frame drew: only the rows in view, however many options there are. */
    int drawnRows() {
        return drawnRows;
    }

    /** Where a row is, before the view's cut. */
    Box rowBox(int row) {
        place();
        return new Box("row" + row, list.rowX(), list.rowY(row), list.rowW(), list.rowH);
    }

    /** The label's char indices that matched the text in a row, ascending: what the row marks. */
    int[] matchedChars(int row) {
        return marks(shown[row]);
    }

    private String textOf(T option) {
        String text = label.apply(option);
        if ((text == null || text.isEmpty()) && artOf(option) instanceof ItemView item) {
            return ItemArt.name(item);
        }
        return text == null ? "" : text;
    }

    private IconArt artOf(T option) {
        return iconArt == null || option == null ? null : iconArt.apply(option);
    }

    /** The side a picture takes in a box {@code room} tall: an item shrinks to fit, other art keeps its width. */
    private static int artSide(IconArt art, int room) {
        if (art == null) {
            return 0;
        }
        return ItemArt.isItem(art) ? Math.min(art.height(), room) : art.width();
    }

    /** Draws a picture in a box {@code room} tall at its middle; an item crisply, with its count at full size. */
    private void drawArt(Draw d, Palette p, IconArt art, int ax, int top, int room, int color) {
        if (!ItemArt.isItem(art)) {
            art.draw(d, ax, top + (room - art.height()) / 2, color, 1F);
            return;
        }
        int side = artSide(art, room);
        int at = top + (room - side) / 2;
        ItemArt.fit(d, p, art, ax, at, side, color, active);
        if (side >= art.height()) {
            ItemArt.decorations(d, (ItemView) art, ax, at);
        }
    }

    /** The value's picture in the field. */
    private IconArt valueArt() {
        return artOf(selected);
    }

    /** What the value's picture takes at the field's left, its gap included. */
    private int lead() {
        int side = artSide(valueArt(), Math.max(0, h - 2));
        return side > 0 ? side + COLUMN_GAP : 0;
    }

    private boolean overValueArt(double mx, double my) {
        int lead = lead();
        return lead > 0 && inField(mx, my) && mx >= x + TEXT_PAD && mx < x + TEXT_PAD + lead - COLUMN_GAP;
    }

    private void openList(int direction) {
        if (open) {
            return;
        }
        filtering = !field.text().equals(committed);
        open = true;
        layer.visible = true;
        list.reset();
        refilter();
        if (direction != 0 && list.lit() < 0 && shownCount > 0) {
            list.lit(direction > 0 ? 0 : shownCount - 1);
        }
        revealLit();
        if (reducedMotion) {
            glide.snap(true);
        } else {
            glide.replay();
        }
    }

    private void revealLit() {
        place();
        list.reveal();
    }

    /** Hidden or disabled closes the list; the focus going elsewhere settles the text. Returns whether the list is open. */
    private boolean sync() {
        if (open && (!visible || !active)) {
            close();
        }
        layer.visible = open;
        boolean live = focused && active && visible;
        if (hadFocus && !live) {
            hadFocus = false;
            close();
            settle();
        } else if (live) {
            hadFocus = true;
        }
        return open;
    }

    // ---- value ----

    private int exactIndex(String text) {
        if (byFolded == null) {
            byFolded = new HashMap<>();
            for (int i = 0; i < folded.length; i++) {
                byFolded.putIfAbsent(folded[i].text, i);
            }
        }
        Integer found = byFolded.get(TextMatch.fold(text).text);
        return found == null ? -1 : found;
    }

    /** What leaving the field does. */
    private void settle() {
        String text = field.text();
        int exact = text.isEmpty() ? -1 : exactIndex(text);
        filtering = false;
        if (freeText) {
            setValue(exact < 0 ? null : items.get(exact));
            commitText();
        } else if (text.isEmpty()) {
            setValue(null);
            committed = "";
        } else if (exact >= 0) {
            setValue(items.get(exact));
            field.setText(labels[exact]);
            committed = field.text();
        } else {
            field.setText(selected == null ? "" : textOf(selected));
            committed = field.text();
        }
        refilter();
    }

    private void setValue(T value) {
        boolean changed = !Objects.equals(value, selected);
        selected = value;
        if (changed && onSelect != null) {
            onSelect.accept(value);
        }
    }

    private void commitText() {
        String text = field.text();
        boolean changed = !text.equals(committed);
        committed = text;
        if (changed && freeText && onCommit != null) {
            onCommit.accept(text);
        }
    }

    private void choose(int index) {
        BaseComponent.click();
        boolean textChanges = !labels[index].equals(field.text());
        field.setText(labels[index]);
        filtering = false;
        close();
        if (textChanges && onText != null) {
            onText.accept(field.text());
        }
        setValue(items.get(index));
        commitText();
        refilter();
    }

    private void clear() {
        BaseComponent.click();
        boolean had = !field.text().isEmpty();
        field.setText("");
        filtering = false;
        if (had && onText != null) {
            onText.accept("");
        }
        setValue(null);
        commitText();
        refilter();
    }

    /** The field's own callback: the player changed the text. */
    private void edited(String text) {
        filtering = true;
        if (onText != null) {
            onText.accept(text);
        }
        refilter();
        if (!open && focused && active) {
            openList(0);
        }
    }

    /** Tab. Returns false when there is nothing more to write, so that the panel moves on. */
    private boolean complete() {
        if (!open || shownCount == 0) {
            return false;
        }
        int index = shown[Math.max(0, list.lit())];
        String target = labels[index];
        if (TextMatch.fold(field.text()).text.equals(folded[index].text)) {
            return false;
        }
        BaseComponent.click();
        field.setText(target);
        filtering = true;
        if (onText != null) {
            onText.accept(field.text());
        }
        refilter();
        for (int row = 0; row < shownCount; row++) {
            if (shown[row] == index) {
                list.lit(row);
                revealLit();
                break;
            }
        }
        return true;
    }

    private void enter() {
        if (open && list.lit() >= 0) {
            choose(shown[list.lit()]);
            return;
        }
        String text = field.text();
        int exact = text.isEmpty() ? -1 : exactIndex(text);
        if (exact >= 0) {
            choose(exact);
        } else if (freeText || text.isEmpty()) {
            BaseComponent.click();
            close();
            setValue(null);
            commitText();
            filtering = false;
            refilter();
        }
        // No such option and no free text: it stays as it is, for the player to go on typing.
    }

    // ---- matching ----

    /** The option's rank, or {@link TextMatch#NONE}; without fuzzy matching the query must appear whole. */
    private long rank(int index) {
        TextMatch.Folded option = folded[index];
        return !fuzzy && !option.text.contains(query) ? TextMatch.NONE : TextMatch.rank(option, query);
    }

    private int[] marks(int index) {
        return query.isEmpty() || rank(index) == TextMatch.NONE ? new int[0] : TextMatch.marks(folded[index], query);
    }

    /** Works out the rows again from the text. Runs when the text or the options change, not on every frame. */
    private void refilter() {
        query = filtering ? TextMatch.foldQuery(field.text()) : "";
        if (query.isEmpty()) {
            shownCount = shown.length;
            for (int i = 0; i < shownCount; i++) {
                shown[i] = i;
            }
        } else {
            long[] ranks = new long[folded.length];
            int count = 0;
            for (int i = 0; i < folded.length; i++) {
                long rank = rank(i);
                if (rank != TextMatch.NONE) {
                    ranks[count++] = rank | i;
                }
            }
            Arrays.sort(ranks, 0, count);
            for (int i = 0; i < count; i++) {
                shown[i] = (int) (ranks[i] & TextMatch.INDEX_MASK);
            }
            shownCount = count;
        }
        list.count(shownCount);
        int lit = -1;
        if (filtering) {
            // An emptied field lights nothing: Enter then clears the value, as leaving does.
            if (!freeText && !query.isEmpty() && shownCount > 0) {
                lit = 0;
            }
        } else if (selected != null) {
            for (int row = 0; row < shownCount; row++) {
                if (selected.equals(items.get(shown[row]))) {
                    lit = row;
                    break;
                }
            }
        }
        list.offset(0);
        list.lit(lit);
        if (open) {
            revealLit();
        }
    }

    // ---- geometry ----

    /** Puts the list under the field, or above when that holds more of it. */
    private void place() {
        list.place(host.theme(), x, y, w, h, area != null ? area : host.bounds(), popupWidth, MIN_POPUP_W, maxRows);
        layer.x = list.x;
        layer.y = list.y;
        layer.w = list.w;
        layer.h = list.h;
    }

    private boolean inField(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean showsClear() {
        return clearable && active && !field.text().isEmpty() && w - zoneFor(2) - TEXT_PAD >= MIN_TEXT;
    }

    private boolean showsArrow() {
        return h >= MIN_BUTTON_H && w - zoneFor(1) - TEXT_PAD >= MIN_TEXT;
    }

    private static int zoneFor(int buttons) {
        return buttons * BUTTON_W + 2 * BUTTON_MARGIN + BORDER;
    }

    /** Width of the strip of buttons at the right end. */
    private int zone() {
        return !showsArrow() ? 0 : zoneFor(showsClear() ? 2 : 1);
    }

    private int buttonX(int button) {
        int right = x + w - BORDER - BUTTON_MARGIN - BUTTON_W;
        return button == ARROW ? right : right - BUTTON_W;
    }

    private int buttonAt(double mx, double my) {
        if (!inField(mx, my) || !showsArrow()) {
            return NO_BUTTON;
        }
        if (mx >= buttonX(ARROW)) {
            return ARROW;
        }
        return showsClear() && mx >= buttonX(CLEAR) ? CLEAR : NO_BUTTON;
    }

    /** The inner field reaches 4 past the text's end: its own margin falls under the buttons. */
    private void placeField() {
        int zone = zone();
        int lead = lead();
        field.x = x + lead;
        field.y = y;
        field.h = h;
        field.w = (zone > 0 ? w - zone + TEXT_PAD : w) - lead;
        field.active = active;
        field.focused = focused && active;
    }

    private int textRoom() {
        int zone = zone();
        return Math.max(0, (zone > 0 ? w - zone - TEXT_PAD : w - 2 * TEXT_PAD) - lead());
    }

    private boolean textCut() {
        return TextMeasure.width(field.text()) > textRoom();
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        lastTheme = t;
        sync();
        placeField();
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        if (field.focused && h >= RowLook.FONT_H) {
            drawLiveField(d, t, mx, my);
        } else {
            drawRestingField(d, p, frame);
        }
        drawButtons(d, t, p, frame, active && inField(mx, my) ? buttonAt(mx, my) : NO_BUTTON);
        itemTipShown = false;
        if (open && !layered) {
            layer.draw(d, t, mx, my, delta);
        } else if (hovered && active && itemTooltips && !hasOwnTip() && overValueArt(mx, my)) {
            itemTipShown = ItemArt.hoverTooltip(d, valueArt(), mx, my);
        }
    }

    /**
     * The library's own field does the text, but it would draw its frame only as wide as its text. So two
     * halves are stitched: the field's left part and a full frame's right part, each cut at the seam.
     * A frame drawn twice would show in a see-through family, and a frame end would show as a line.
     */
    private void drawLiveField(Draw d, Theme t, int mx, int my) {
        int zone = zone();
        int lead = lead();
        if (zone == 0 && lead == 0) {
            field.draw(d, t, mx, my, delta);
            return;
        }
        // With a picture the field starts right of it: its own left end is cut away, a full frame's shows instead.
        int start = lead > 0 ? field.x + LEAD_CUT : x - STITCH_BLEED;
        int seam = zone > 0 ? x + w - zone : x + w + STITCH_BLEED;
        d.scissor(start, y - STITCH_BLEED, seam, y + h + STITCH_BLEED);
        field.draw(d, t, mx, my, delta);
        d.endScissor();
        if (lead > 0) {
            d.scissor(x - STITCH_BLEED, y - STITCH_BLEED, start, y + h + STITCH_BLEED);
            t.frame().field(d, t.palette(), x, y, w, h, true);
            d.endScissor();
            drawArt(d, t.palette(), valueArt(), x + TEXT_PAD, y + 1, h - 2, t.frame().fieldText(t.palette()));
        }
        if (zone > 0) {
            d.scissor(seam, y - STITCH_BLEED, x + w + STITCH_BLEED, y + h + STITCH_BLEED);
            t.frame().field(d, t.palette(), x, y, w, h, true);
            d.endScissor();
        }
    }

    /** At rest the text starts at its first letter and ends in dots when cut, which the field does not do. */
    private void drawRestingField(Draw d, Palette p, FrameStyle frame) {
        frame.field(d, p, x, y, w, h, false);
        int room = textRoom();
        if (room <= 0 || h < RowLook.FONT_H) {
            return; // no slivers of letters
        }
        String text = field.text();
        boolean hint = text.isEmpty();
        int color = !active ? disabledInk(p, frame) : hint ? frame.fieldHint(p) : frame.fieldText(p);
        int lead = lead();
        d.scissor(x, y, x + w, y + h);
        if (lead > 0) {
            drawArt(d, p, valueArt(), x + TEXT_PAD, y + 1, h - 2, color);
        }
        d.textFit(hint ? placeholder : text, x + TEXT_PAD + lead, y + (h - RowLook.FONT_H) / 2, room, color);
        d.endScissor();
    }

    private void drawButtons(Draw d, Theme t, Palette p, FrameStyle frame, int hoverButton) {
        buttonHover.step(hoverButton);
        if (showsArrow()) {
            drawButton(d, t.icon(Icon.CARET), p, frame, ARROW, hoverButton);
            if (showsClear()) {
                drawButton(d, t.icon(Icon.CLOSE), p, frame, CLEAR, hoverButton);
            }
        }
    }

    private void drawButton(Draw d, IconArt art, Palette p, FrameStyle frame, int button, int hoverButton) {
        int bx = buttonX(button);
        boolean over = button == hoverButton;
        boolean held = button == heldButton && over;
        int ink = frame.fieldText(p);
        if (active && over && h > 2 * WASH_INSET) {
            int alpha = held ? HELD_WASH : Math.round(HOVER_WASH * buttonHover.fade());
            d.fill(bx, y + WASH_INSET, bx + BUTTON_W, y + h - WASH_INSET, Shapes.alpha(ink, alpha));
        }
        int color = !active ? disabledInk(p, frame) : over ? ink : frame.fieldHint(p);
        d.scissor(bx, y, bx + BUTTON_W, y + h);
        art.draw(d, bx + (BUTTON_W - art.width()) / 2, y + (h - art.height()) / 2, color, 1F);
        d.endScissor();
    }

    /**
     * The palette's disabled text is made for its surface. Light families have a dark field, where it reads
     * brighter than an enabled hint (soft, bubbly): the hint at reduced strength is used there instead.
     */
    private static int disabledInk(Palette p, FrameStyle frame) {
        int text = frame.fieldText(p);
        int hint = frame.fieldHint(p);
        return Shapes.contrast(p.disabledText(), text) >= Shapes.contrast(hint, text) ? p.disabledText() : Shapes.alpha(hint, DISABLED_HINT_ALPHA);
    }

    private void drawPopup(Draw d, Theme t, int mx, int my) {
        place();
        drawnRows = 0;
        boolean moving = !reducedMotion && glide.moving();
        String empty = BaseComponent.text(emptyText, words, "minuit.no_results", "No results");
        list.draw(d, t, mx, my, layer, empty, moving ? Math.min(1F, glide.progress()) : 1F);
        int hoverRow = moving || list.dragging() ? -1 : list.rowAt(mx, my);
        if (hoverRow >= 0 && itemTooltips && !rowHasOwnTip(shownAt(hoverRow))) {
            itemTipShown = ItemArt.hoverTooltip(d, artOf(shownAt(hoverRow)), mx, my);
        }
    }

    private boolean isValue(int index) {
        return selected != null && selected.equals(items.get(index));
    }

    /**
     * Three looks that cannot be confused, loudest first: the keyboard's row in the full primary colour,
     * the value in the same colour at half strength with a check mark, the pointer's row as a light wash.
     */
    private int rowFace(Palette p, int row, boolean hover, float fade) {
        if (row == list.lit()) {
            return !focused ? Shapes.alpha(p.primary(), CHOSEN_ALPHA) : hover ? p.primaryHover() : p.primary();
        }
        if (isValue(shown[row])) {
            return Shapes.alpha(p.primary(), hover ? CHOSEN_HOVER_ALPHA : CHOSEN_ALPHA);
        }
        return hover ? RowLook.tint(p, fade) : 0;
    }

    private void drawRow(Draw d, Theme t, int row, int rx, int ry, int rw, int rh, int face) {
        Palette p = t.palette();
        int index = shown[row];
        boolean isLit = row == list.lit();
        boolean isValue = isValue(index);
        int under = Ink.seen(p, face);
        int ink = isLit && focused ? p.primaryText() : Ink.on(p, face);
        int textY = RowLook.textY(ry, rh);
        int right = rx + rw - RIGHT;
        if (isValue) {
            IconArt mark = t.icon(Icon.CHECK);
            mark.draw(d, right - mark.width(), ry + (rh - mark.height()) / 2, ink, 1F);
            right -= mark.width() + COLUMN_GAP;
        }
        int left = rx + LEFT;
        IconArt art = artOf(items.get(index));
        if (art != null) {
            drawArt(d, p, art, left, ry, rh, ink);
            left += artSide(art, rh) + COLUMN_GAP;
        }
        int room = right - left;
        if (room > 0) {
            drawLabel(d, p, labels[index], marks(index), left, textY, room, ink, under, isLit || isValue);
        }
        drawnRows++;
    }

    private boolean rowHasOwnTip(T option) {
        if (rowTooltip != null) {
            List<String> lines = rowTooltip.apply(option);
            return lines != null && !lines.isEmpty();
        }
        return false;
    }

    private boolean hasOwnTip() {
        return tooltip != null && !tooltip.isEmpty();
    }

    /** The item under the pointer: an option of the open list, or the value's picture in the field. */
    @Override
    public ItemView itemAt(double mx, double my) {
        if (!visible) {
            return null;
        }
        if (open && !layered) {
            ItemView row = layer.itemAt(mx, my);
            if (row != null) {
                return row;
            }
        }
        return overValueArt(mx, my) && valueArt() instanceof ItemView item ? item : null;
    }

    /**
     * The label with its matched letters marked: underlined, which reads on any face, and in the accent
     * colour where that is readable. The cut comes first, so a match past the dots marks nothing.
     */
    private void drawLabel(Draw d, Palette p, String text, int[] marked, int left, int top, int room, int ink, int under, boolean onColour) {
        if (marked.length == 0) {
            d.textFit(text, left, top, room, ink);
            return;
        }
        String cut = d.ellipsize(text, room);
        int kept = cut.equals(text) ? text.length() : Math.max(0, cut.length() - Draw.ELLIPSIS.length());
        int mark = !onColour && Shapes.contrast(p.accent(), under) >= Ink.TEXT ? p.accent() : ink;
        int from = 0;
        int cursor = 0;
        while (from < kept) {
            while (cursor < marked.length && marked[cursor] < from) {
                cursor++;
            }
            boolean isMark = cursor < marked.length && marked[cursor] == from;
            int to = from + 1;
            int look = cursor + 1;
            if (isMark) {
                while (to < kept && look < marked.length && marked[look] == to) {
                    to++;
                    look++;
                }
            } else {
                int next = cursor < marked.length ? marked[cursor] : kept;
                to = Math.min(kept, next);
            }
            String piece = text.substring(from, to);
            int at = left + d.width(text.substring(0, from));
            d.text(piece, at, top, isMark ? mark : ink);
            if (isMark) {
                d.fill(at, top + RowLook.FONT_H, at + Math.max(1, d.width(piece) - 1), top + RowLook.FONT_H + 1, mark);
            }
            from = to;
        }
        if (kept < cut.length()) {
            d.text(cut.substring(kept), left + d.width(text.substring(0, kept)), top, ink);
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (sync() && !layered) {
            return layer.tooltipAt(mx, my);
        }
        return fieldTooltip(mx, my);
    }

    private List<String> fieldTooltip(double mx, double my) {
        if (!inField(mx, my)) {
            return null;
        }
        if (active && buttonAt(mx, my) == CLEAR && clearTooltip != null) {
            return clearTooltip;
        }
        if (tooltip != null) {
            return tooltip;
        }
        if (active && itemTooltips && !open && overValueArt(mx, my) && valueArt() instanceof ItemView item) {
            return itemTipShown ? List.of() : ItemArt.nameTip(item);
        }
        return !focused && buttonAt(mx, my) == NO_BUTTON && textCut() ? List.of(field.text()) : null;
    }

    // ---- input ----

    private boolean accepts() {
        return visible && active && settled();
    }

    /** While the list is open, every click belongs to it when it draws itself. */
    @Override
    public boolean isOver(double mx, double my) {
        return inField(mx, my) || (open && !layered);
    }

    @Override
    protected boolean takesFocus() {
        return visible && active;
    }

    @Override
    protected boolean navigable() {
        return visible && active && settled();
    }

    @Override
    public void tick() {
        sync();
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
        placeField();
        int hit = buttonAt(mx, my);
        if (hit == CLEAR) {
            heldButton = hit;
            clear();
            return true;
        }
        if (hit == ARROW) {
            heldButton = hit;
            BaseComponent.click();
            if (open) {
                close();
            } else {
                openList(0);
            }
            return true;
        }
        // The field needs one frame's measures before it can place the caret, and a resting field draws none.
        if (lastTheme != null) {
            field.draw(TextMeasure.draw(), lastTheme, (int) mx, (int) my, delta);
        }
        field.mouseClicked(mx, my, 0);
        if (openOnClick && !open) {
            openList(0);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (list.dragging() && !layered) {
            return layer.mouseDragged(mx, my, button, dx, dy);
        }
        return field.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        heldButton = NO_BUTTON;
        field.mouseReleased(mx, my, button);
        if (!layered) {
            return layer.mouseReleased(mx, my, button);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return open && !layered && layer.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        sync();
        if (!accepts()) {
            return false;
        }
        placeField();
        switch (key) {
            case DOWN, UP -> {
                if (open) {
                    place();
                    list.key(key);
                } else {
                    openList(key == InputKey.DOWN ? 1 : -1);
                }
                return true;
            }
            case ENTER -> {
                enter();
                return true;
            }
            case ESCAPE -> {
                if (open) {
                    BaseComponent.click();
                    close();
                    return true;
                }
                return false;
            }
            case TAB -> {
                return !shift && complete();
            }
            case SPACE -> {
                return true; // the character comes next, through charTyped
            }
            default -> {
                return field.keyPressed(key, shift);
            }
        }
    }

    @Override
    public boolean charTyped(int codePoint) {
        sync();
        if (!accepts()) {
            return false;
        }
        placeField();
        return field.charTyped(codePoint);
    }

    // ---- the list as a widget ----

    private void refocus() {
        if (host.focus() != this) {
            host.focus(this);
        }
        hadFocus = true;
        placeField();
    }

    @Override
    void words(Words words) {
        this.words = words;
    }

    /** The list as the panel's widget: draws it and takes the clicks, wheel turns and drags while it is open. */
    private final class Layer extends BaseComponent implements ItemSource, PopupList.Rows {
        Layer() {
            super(0, 0, 0, 0);
            visible = false;
        }

        @Override
        public int face(Palette p, int row, boolean hover, float fade) {
            return rowFace(p, row, hover, fade);
        }

        @Override
        public void draw(Draw d, Theme t, int row, int x, int y, int w, int h, int face) {
            drawRow(d, t, row, x, y, w, h, face);
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
            T option = shownAt(row);
            if (rowTooltip != null) {
                List<String> lines = rowTooltip.apply(option);
                if (lines != null && !lines.isEmpty()) {
                    return lines;
                }
            }
            IconArt art = artOf(option);
            if (itemTooltips && art instanceof ItemView item) {
                return itemTipShown ? List.of() : ItemArt.nameTip(item);
            }
            int room = list.rowW() - LEFT - RIGHT - (art == null ? 0 : artSide(art, list.rowH) + COLUMN_GAP);
            if (isValue(shown[row]) && lastTheme != null) {
                room -= lastTheme.icon(Icon.CHECK).width() + COLUMN_GAP;
            }
            return TextMeasure.width(labels[shown[row]]) > room ? List.of(labels[shown[row]]) : List.of();
        }

        @Override
        public ItemView itemAt(double mx, double my) {
            if (!open || iconArt == null) {
                return null;
            }
            int row = list.rowAt(mx, my);
            return row >= 0 && artOf(shownAt(row)) instanceof ItemView item ? item : null;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            // No sync here: the panel took the focus away just before this, as this widget takes none.
            if (!open || !ComboBoxComponent.this.visible || !ComboBoxComponent.this.active) {
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
                choose(shown[row]);
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
