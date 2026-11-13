package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A search box over a screen that finds and runs commands, without leaving the keyboard.
 *
 * <pre>{@code
 * CommandPaletteComponent palette = new CommandPaletteComponent(bounds(), this)
 *         .group("World",
 *                 Command.of("Teleport home", this::home).icon(Icon.GLOBE).shortcut("H").keywords("spawn", "bed"),
 *                 Command.of("Save now", this::save).icon(Icon.DOWNLOAD).shortcut("Ctrl+S"))
 *         .group("Look", Command.of("Change theme", this::theme).icon(Icon.PALETTE))
 *         .recent(config.recentCommands)
 *         .onRun(c -> config.recentCommands = palette.recent());
 * add(palette); // last
 * // and from your own key handling, or a button:
 * palette.open();
 * }</pre>
 *
 * <p>With an empty search it shows the recent commands, then every group. With text it ranks by how the
 * label matches (start, word start, inside, scattered letters, then a keyword), brings the group with the best
 * match first, and marks the letters that matched. The filter runs when the text changes and only the rows in
 * view are drawn, so thousands of commands stay light.
 *
 * <h2>Putting it in a panel</h2>
 * Add it <b>after every other widget</b>, as with a dialog, and before any {@link ContextMenuComponent} of the
 * panel, which insists on being the very last. The box is the area it dims and stays in: the panel's bounds or the screen's.
 * Closed, it takes no room, no input and no keyboard stop.
 *
 * <h2>Keys</h2>
 * Typing searches; left, right, home, end, copy and paste act on the text. Up and down move and wrap, skipping
 * disabled rows. Tab and shift+Tab go to the next and previous group. Enter runs the lit row. Escape, or a click
 * outside the card, closes it and gives the panel's focus back. A closed palette has no key of its own: a panel
 * does not see Ctrl+K, so bind what opens it where your screen reads raw keys.
 *
 * <p>{@code active = false} or {@code visible = false} closes it.
 */
public final class CommandPaletteComponent extends BaseComponent {
    /** One thing the palette can run. */
    public static final class Command {
        private final String label;
        private final Runnable run;
        private String id;
        private Icon icon;
        private IconArt iconArt;
        private String shortcut = "";
        private String[] keywords = new String[0];
        private boolean enabled = true;
        private List<String> tooltip;
        /** The disabled reason, kept apart so re-enabling gives the item's own tooltip back. */
        private List<String> reason;

        private Command(String label, Runnable run) {
            this.label = label == null ? "" : label;
            this.run = run;
            this.id = this.label;
        }

        /** Runs the callback, which may be null, after the palette has closed. */
        public static Command of(String label, Runnable run) {
            return new Command(label, run);
        }

        /** What {@link #recent(Collection)} remembers it by, when a label may change. The label by default. */
        public Command id(String id) {
            this.id = id == null ? label : id;
            return this;
        }

        public Command icon(Icon icon) {
            this.icon = icon;
            return this;
        }

        public Command iconArt(IconArt iconArt) {
            this.iconArt = iconArt;
            return this;
        }

        /** Text at the right edge, such as {@code "Ctrl+S"}. It is only a hint: the key is yours to handle. */
        public Command shortcut(String shortcut) {
            this.shortcut = shortcut == null ? "" : shortcut;
            return this;
        }

        /** More words that find this command. They are searched after the label, and nothing marks them. */
        public Command keywords(String... keywords) {
            String[] out = new String[keywords.length];
            for (int i = 0; i < keywords.length; i++) {
                out[i] = TextMatch.fold(keywords[i] == null ? "" : keywords[i]).text;
            }
            this.keywords = out;
            return this;
        }

        public Command tooltip(String... lines) {
            this.tooltip = lines == null || lines.length == 0 ? null : List.of(lines);
            return this;
        }

        public String label() {
            return label;
        }

        public String id() {
            return id;
        }

        private IconArt art(Theme t) {
            return iconArt != null ? iconArt : icon != null ? t.icon(icon) : null;
        }
    }

    private static final class Entry {
        final Command command;
        final int group;
        final TextMatch.Folded folded;

        Entry(Command command, int group) {
            this.command = command;
            this.group = group;
            this.folded = TextMatch.fold(command.label);
        }
    }

    /** A line of the list. What it draws is cut once, not on every frame. */
    private static final class Row {
        final Entry entry;
        final String title;
        final int top;
        final int height;
        int room = -1;
        String[] pieces;
        int[] pieceX;
        int[] pieceW;
        boolean[] pieceMark;
        boolean cut;
        int shortcutRoom = -1;
        String shortcut;

        Row(Entry entry, String title, int top, int height) {
            this.entry = entry;
            this.title = title;
            this.top = top;
            this.height = height;
        }
    }

    private static final int ROW = 14;
    /** Art taller than an item is shrunk to 16 (by n/gs, crisp). */
    private static final int ART_MAX = 16;
    private static final int TITLE_H = 13;
    private static final int MARGIN = 4;
    private static final int LEFT = 3;
    private static final int RIGHT = 3;
    private static final int GAP = 4;
    private static final int FIELD_H = 16;
    private static final int FIELD_GAP = 3;
    private static final int FOOT_H = 11;
    private static final int RULE_ALPHA = 0x40;
    private static final int DIM_ALPHA = 0xA0;
    private static final int HINT_PAD = 5;
    private static final int OPEN_MS = 110;

    private final PanelComponent host;
    private final TextFieldComponent field;
    private final Glide glide = new Glide(Slide.DOWN, OPEN_MS);
    private final List<String> groupTitles = new ArrayList<>();
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Object[]> disabledIds = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();
    /** The height of a command's row: {@link #rowHeight()}. */
    private int rowH = ROW;
    private final List<String> recentIds = new ArrayList<>();

    private String recentTitle;
    private String emptyText;
    private Words words;
    private String footer = "Up, Down: move   Enter: run   Esc: close";
    private static final String ENGLISH_PLACEHOLDER = "Type a command...";
    private String placeholder = ENGLISH_PLACEHOLDER;
    private int maxRecent = 5;
    private int maxRows = 8;
    private int maxWidth = 240;
    private Consumer<Command> onRun;
    private Runnable onClose;

    private boolean open;
    private BaseComponent focusBefore;
    private String query = "";
    private int lit = -1;
    private final ScrollBar bar = new ScrollBar();
    private int contentH;
    private int lastMx = Integer.MIN_VALUE;
    private int lastMy = Integer.MIN_VALUE;

    // The card's place, worked out from ints on every frame: nothing here allocates.
    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;
    private Insets inset = new Insets(0, 0, 0, 0);
    private FrameStyle frame = FrameStyles.VANILLA;
    private int listY;
    private int listH;
    private boolean footShown;
    private int footCutFor = -1;
    private String footCut = "";

    /**
     * @param area the box it dims and stays in, in the panel's coordinates
     * @param host the panel this palette is added to
     */
    public CommandPaletteComponent(Box area, PanelComponent host) {
        super(area);
        this.host = Objects.requireNonNull(host, "host");
        this.field = new TextFieldComponent(new Box("search", 0, 0, 1, 1), "", this::edited).placeholder(placeholder);
    }

    /** Adds a group. A null title gives commands with no heading. */
    public CommandPaletteComponent group(String title, Command... commands) {
        return group(title, Arrays.asList(commands));
    }

    /** Adds a group. A null title gives commands with no heading. */
    public CommandPaletteComponent group(String title, Collection<Command> commands) {
        int index = groupTitles.size();
        groupTitles.add(title == null ? "" : title);
        if (commands != null) {
            for (Command command : commands) {
                if (command != null) {
                    applyDisabled(command, null);
                    entries.add(new Entry(command, index));
                }
            }
        }
        if (open) {
            refilter();
        }
        return this;
    }

    /**
     * Greys out the commands whose {@link Command#id()} is {@code id}, or makes them runnable again: drawn disabled,
     * skipped by the keyboard, not run by Enter or a click. The {@code tip} lines show over the row, to say why; none
     * keeps the command's own tooltip. It holds for groups added later. The id is the label unless {@link Command#id(String)} set another.
     */
    public CommandPaletteComponent disabled(String id, boolean disabled, String... tip) {
        Objects.requireNonNull(id, "id");
        Object[] how = {disabled, tip};
        disabledIds.put(id, how);
        for (Entry entry : entries) {
            if (entry.command.id.equals(id)) {
                applyDisabled(entry.command, how);
            }
        }
        return this;
    }

    private void applyDisabled(Command command, Object[] how) {
        Object[] h = how == null ? disabledIds.get(command.id) : how;
        if (h == null) {
            return;
        }
        String[] tip = (String[]) h[1];
        command.enabled = !(Boolean) h[0];
        command.reason = command.enabled || tip == null || tip.length == 0 ? null : List.of(tip);
    }

    /** Removes every group and command. */
    public CommandPaletteComponent clearCommands() {
        groupTitles.clear();
        entries.clear();
        if (open) {
            refilter();
        }
        return this;
    }

    /** The ids of the commands run lately, latest first, as {@link #recent()} gave them. Unknown ids are skipped. */
    public CommandPaletteComponent recent(Collection<String> ids) {
        recentIds.clear();
        if (ids != null) {
            recentIds.addAll(ids);
        }
        if (open) {
            refilter();
        }
        return this;
    }

    /** How many recent commands an empty search shows. 5 by default; 0 hides the group. */
    public CommandPaletteComponent maxRecent(int count) {
        maxRecent = Math.max(0, count);
        return this;
    }

    /** The heading of the recent commands; the lang entry {@code minuit.palette.recent} by default. */
    public CommandPaletteComponent recentTitle(String title) {
        recentTitle = title == null ? "" : title;
        return this;
    }

    /** What the empty search box says; the lang entry {@code minuit.palette.placeholder} by default. */
    public CommandPaletteComponent placeholder(String text) {
        placeholder = text == null ? "" : text;
        field.placeholder(placeholder);
        return this;
    }

    /** The line when nothing matches; the lang entry {@code minuit.no_results} by default. */
    public CommandPaletteComponent emptyText(String text) {
        emptyText = text == null ? "" : text;
        return this;
    }

    /** The line of key hints under the list; null removes it. English by default: pass your own translation. */
    public CommandPaletteComponent footer(String text) {
        footer = text;
        footCutFor = -1;
        return this;
    }

    /** How many rows of height the list keeps, so that the card does not jump as the results change. 8 by default. */
    public CommandPaletteComponent maxRows(int rows) {
        maxRows = Math.max(1, rows);
        return this;
    }

    /** The card's width; it stays inside the area. 240 by default. */
    public CommandPaletteComponent maxWidth(int width) {
        maxWidth = Math.max(40, width);
        return this;
    }

    /** Called when a command is run, after the palette has closed and before the command's own callback. */
    public CommandPaletteComponent onRun(Consumer<Command> onRun) {
        this.onRun = onRun;
        return this;
    }

    /** Runs whenever the palette closes, for any reason, before a command that was run. */
    public CommandPaletteComponent onClose(Runnable onClose) {
        this.onClose = onClose;
        return this;
    }

    @Override
    void words(Words words) {
        this.words = words;
        if (placeholder.equals(ENGLISH_PLACEHOLDER)) {
            placeholder = text(null, words, "minuit.palette.placeholder", ENGLISH_PLACEHOLDER);
            field.placeholder(placeholder);
        }
    }

    /**
     * Opens with an empty search.
     *
     * @return false when it is hidden or disabled
     * @throws IllegalStateException if the panel does not hold the palette
     */
    public boolean open() {
        if (!host.widgets().contains(this)) {
            throw new IllegalStateException("Add the palette to the panel before opening it");
        }
        if (!visible || !active) {
            return false;
        }
        if (open) {
            return true;
        }
        host.closeOverlay();
        BaseComponent before = host.focus();
        focusBefore = before == this ? null : before;
        open = true;
        bar.release();
        lastMx = Integer.MIN_VALUE;
        field.setText("");
        field.active = true;
        host.focus(this);
        refilter();
        if (reducedMotion()) {
            glide.snap(true);
        } else {
            glide.replay();
        }
        return true;
    }

    /** Closes it, running nothing but {@link #onClose}, and gives the panel's focus back. */
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        bar.release();
        if (host.focus() == this) {
            host.focus(focusBefore);
        }
        focusBefore = null;
        if (onClose != null) {
            onClose.run();
        }
    }

    public boolean isOpen() {
        return open;
    }

    /** Sets the search text, as if typed. */
    public void setQuery(String text) {
        field.setText(text == null ? "" : text);
        refilter();
    }

    public String query() {
        return field.text();
    }

    /** The ids of the commands run lately, latest first: keep them, to pass to {@link #recent(Collection)}. */
    public List<String> recent() {
        return new ArrayList<>(recentIds);
    }

    /** The lit command, or null. */
    public Command litCommand() {
        return lit >= 0 && lit < rows.size() ? rows.get(lit).entry.command : null;
    }

    int rowCount() {
        return rows.size();
    }

    /** A row's title, or its command's label. */
    String rowText(int row) {
        Row r = rows.get(row);
        return r.entry == null ? r.title : r.entry.command.label;
    }

    /** The rows as text, titles in brackets: {@code [World] Teleport home | Save now}. */
    String listing() {
        StringBuilder out = new StringBuilder();
        boolean afterTitle = true;
        for (Row r : rows) {
            if (out.length() > 0) {
                out.append(r.entry == null || afterTitle ? " " : " | ");
            }
            afterTitle = r.entry == null;
            out.append(r.entry == null ? "[" + r.title + "]" : r.entry.command.label);
        }
        return out.toString();
    }

    /** The label's char indices a row marks. */
    int[] matchedChars(int row) {
        Row r = rows.get(row);
        return r.entry == null ? new int[0] : TextMatch.marks(r.entry.folded, query);
    }

    Box rowBox(int row) {
        measure();
        Row r = rows.get(row);
        return new Box("row" + row, rowX(), listY + r.top - bar.offset(), rowW(), r.height);
    }

    Box cardBox() {
        measure();
        return new Box("card", cardX, cardY, cardW, cardH);
    }

    int scrollOffset() {
        return bar.offset();
    }

    private void edited(String text) {
        refilter();
    }

    private void refilter() {
        query = TextMatch.foldQuery(field.text());
        rowH = rowHeight();
        rows.clear();
        contentH = 0;
        if (query.isEmpty()) {
            listAll();
        } else {
            listMatches();
        }
        bar.offset(0);
        lit = stepFrom(-1, 1);
    }

    private void addTitle(String title) {
        if (!title.isEmpty()) {
            rows.add(new Row(null, title, contentH, TITLE_H));
            contentH += TITLE_H;
        }
    }

    private void addCommand(Entry entry) {
        rows.add(new Row(entry, null, contentH, rowH));
        contentH += rowH;
    }

    /**
     * One height for every command row, so that the card does not jump as the results change: 14, or 18 when a
     * command shows a 16 px item (a pixel above and below). Taller art is shrunk to 16 by n/gs.
     */
    private int rowHeight() {
        int height = ROW;
        for (Entry entry : entries) {
            IconArt art = entry.command.iconArt;
            if (art != null) {
                height = Math.max(height, Math.min(ART_MAX, art.height()) + 2);
            }
        }
        return height;
    }

    private Entry findById(String id) {
        for (Entry entry : entries) {
            if (entry.command.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    private void listAll() {
        List<Entry> recentRows = new ArrayList<>();
        for (String id : recentIds) {
            if (recentRows.size() >= maxRecent) {
                break;
            }
            Entry entry = findById(id);
            if (entry != null && !recentRows.contains(entry)) {
                recentRows.add(entry);
            }
        }
        if (!recentRows.isEmpty()) {
            addTitle(BaseComponent.text(recentTitle, words, "minuit.palette.recent", "Recent"));
            for (Entry entry : recentRows) {
                addCommand(entry);
            }
        }
        for (int g = 0; g < groupTitles.size(); g++) {
            boolean titled = false;
            for (Entry entry : entries) {
                if (entry.group == g && !recentRows.contains(entry)) {
                    if (!titled) {
                        addTitle(groupTitles.get(g));
                        titled = true;
                    }
                    addCommand(entry);
                }
            }
        }
    }

    private void listMatches() {
        long[] ranks = new long[entries.size()];
        int count = 0;
        for (int i = 0; i < entries.size(); i++) {
            long rank = rankEntry(entries.get(i));
            if (rank != TextMatch.NONE) {
                ranks[count++] = rank | i;
            }
        }
        Arrays.sort(ranks, 0, count);
        // Groups come in the order of their best match; inside a group, best first.
        boolean[] done = new boolean[groupTitles.size()];
        for (int i = 0; i < count; i++) {
            int group = entries.get((int) (ranks[i] & TextMatch.INDEX_MASK)).group;
            if (done[group]) {
                continue;
            }
            done[group] = true;
            addTitle(groupTitles.get(group));
            for (int k = i; k < count; k++) {
                Entry entry = entries.get((int) (ranks[k] & TextMatch.INDEX_MASK));
                if (entry.group == group) {
                    addCommand(entry);
                }
            }
        }
    }

    private long rankEntry(Entry entry) {
        long rank = TextMatch.rank(entry.folded.text, query);
        if (rank != TextMatch.NONE) {
            return rank;
        }
        for (String keyword : entry.command.keywords) {
            if (keyword.contains(query)) {
                return TextMatch.rankOf(TextMatch.KEYWORD, keyword.startsWith(query) ? 0 : 1);
            }
        }
        return TextMatch.NONE;
    }

    private boolean stop(int row) {
        Row r = rows.get(row);
        return r.entry != null && r.entry.command.enabled;
    }

    private int stepFrom(int from, int by) {
        for (int i = from + by; i >= 0 && i < rows.size(); i += by) {
            if (stop(i)) {
                return i;
            }
        }
        return -1;
    }

    private void moveLit(int by) {
        int next = stepFrom(lit, by);
        if (next < 0) {
            next = by > 0 ? stepFrom(-1, 1) : stepFrom(rows.size(), -1);
        }
        if (next >= 0) {
            lit = next;
            reveal(next);
        }
    }

    /** To the first row of the next or the previous group, wrapping. */
    private void moveGroup(int by) {
        if (rows.isEmpty()) {
            return;
        }
        int size = rows.size();
        int start = Math.max(0, lit);
        // Back to the top of this group, so that going backwards lands on the previous group's first row.
        int title = start;
        while (title >= 0 && rows.get(title).entry != null) {
            title--;
        }
        int at = by > 0 ? start : Math.max(0, title);
        for (int i = 0; i < size; i++) {
            at = Math.floorMod(at + by, size);
            if (rows.get(at).entry == null) {
                if (by > 0) {
                    int first = stepFrom(at, 1);
                    if (first >= 0) {
                        lit = first;
                        reveal(first);
                    }
                    return;
                }
                int first = stepFrom(at, 1);
                if (first >= 0 && first != start) {
                    lit = first;
                    reveal(first);
                    return;
                }
            }
        }
    }

    private void reveal(int row) {
        measure();
        Row r = rows.get(row);
        int top = r.top;
        if (row > 0 && rows.get(row - 1).entry == null) {
            top = rows.get(row - 1).top; // a title comes along with its first row
        }
        if (top < bar.offset()) {
            bar.offset(top);
        } else if (r.top + r.height > bar.offset() + listH) {
            bar.offset(r.top + r.height - listH);
        }
    }

    private void run(int row) {
        Row r = rows.get(row);
        if (r.entry == null || !r.entry.command.enabled) {
            return;
        }
        Command command = r.entry.command;
        BaseComponent.click();
        recentIds.remove(command.id);
        recentIds.add(0, command.id);
        close();
        if (onRun != null) {
            onRun.accept(command);
        }
        if (command.run != null) {
            command.run.run();
        }
    }

    private void measure() {
        Theme t = host.theme();
        frame = t.frame();
        inset = PopupList.inset(frame);
        cardW = Math.max(1, Math.min(maxWidth, w - 2 * MARGIN));
        int chrome = inset.top() + inset.bottom() + FIELD_H + FIELD_GAP;
        int foot = footer == null ? 0 : FOOT_H + 1;
        int room = h - 2 * MARGIN;
        int wanted = maxRows * rowH;
        footShown = foot > 0 && chrome + foot + 2 * rowH <= room;
        int fit = room - chrome - (footShown ? foot : 0);
        listH = Math.max(rowH, Math.min(wanted, fit));
        cardH = chrome + listH + (footShown ? foot : 0);
        cardX = x + Math.max(0, (w - cardW) / 2);
        // Above the middle, so that the top of the list stays near the eyes.
        cardY = y + Math.max(Math.min(MARGIN, Math.max(0, (h - cardH) / 2)), (h - cardH) / 3);
        listY = cardY + inset.top() + FIELD_H + FIELD_GAP;
        field.x = cardX + inset.left();
        field.y = cardY + inset.top();
        field.w = Math.max(1, cardW - inset.left() - inset.right());
        field.h = FIELD_H;
        bar.content(contentH, listH);
    }

    private boolean overflows() { return contentH > listH; }
    private int rowX() { return cardX + inset.left(); }
    private int rowW() { return Math.max(0, cardW - inset.left() - inset.right() - (overflows() ? ScrollBar.room(frame) : 0)); }
    private int barX() { return ScrollBar.x(frame, cardX + cardW - inset.right()); }

    private boolean inCard(double mx, double my) {
        return mx >= cardX && mx < cardX + cardW && my >= cardY && my < cardY + cardH;
    }

    private boolean overBar(double mx, double my) {
        return overflows() && mx >= barX() && mx < barX() + ScrollBar.WIDTH && my >= listY && my < listY + listH;
    }

    private int firstVisible() {
        int low = 0;
        int high = rows.size() - 1;
        int found = rows.size();
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Row r = rows.get(mid);
            if (r.top + r.height > bar.offset()) {
                found = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return found;
    }

    private int rowAt(double mx, double my) {
        if (mx < rowX() || mx >= rowX() + rowW() || my < listY || my >= listY + listH) {
            return -1;
        }
        int inner = (int) Math.floor(my) - listY + bar.offset();
        for (int i = firstVisible(); i < rows.size(); i++) {
            Row r = rows.get(i);
            if (inner >= r.top && inner < r.top + r.height) {
                return r.entry == null ? -1 : i;
            }
            if (r.top > inner) {
                break;
            }
        }
        return -1;
    }

    private boolean sync() {
        if (open && (!visible || !active)) {
            close();
        }
        return open;
    }

    @Override
    public void tick() {
        sync();
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        if (!sync()) {
            return;
        }
        if (host.focus() != this) {
            host.focus(this); // a button that opened the palette takes the focus after its press
        }
        measure();
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        boolean moving = !reducedMotion() && glide.moving();
        float shown = moving ? Math.max(0F, Math.min(1F, glide.progress())) : 1F;
        d.nextLayer(); // the text beneath may sort above a rectangle otherwise
        d.fill(x, y, x + w, y + h, (Math.round(DIM_ALPHA * shown) << 24) | (p.shadow() & 0xFFFFFF));
        if (!moving && !bar.dragging() && (mx != lastMx || my != lastMy)) {
            if (lastMx != Integer.MIN_VALUE) {
                int at = rowAt(mx, my);
                if (at >= 0 && stop(at)) {
                    lit = at;
                }
            }
            lastMx = mx;
            lastMy = my;
        }
        d.nextLayer();
        d.scissor(x, y, x + w, moving ? cardY + Math.max(1, Math.round(cardH * shown)) + MARGIN : y + h);
        frame.popup(d, p, cardX, cardY, cardW, cardH);
        field.focused = true;
        field.active = active;
        field.draw(d, t, mx, my, delta);
        if (field.text().isEmpty() && !placeholder.isEmpty() && active) {
            // The field keeps its hint for when it has no focus, and here it always has it: the hint would never show.
            d.textFit(placeholder, field.x + HINT_PAD, field.y + (field.h - RowLook.FONT_H) / 2, field.w - 2 * HINT_PAD, frame.fieldHint(p));
        }
        drawList(d, t, p, mx, my);
        if (footShown) {
            drawFooter(d, p);
        }
        d.endScissor();
    }

    private void drawList(Draw d, Theme t, Palette p, int mx, int my) {
        int rx = rowX();
        int rw = rowW();
        int under = NavLook.solid(p.surface());
        if (rw <= 0 || listH <= 0) {
            return;
        }
        d.scissor(rx, listY, rx + rw, listY + listH);
        if (rows.isEmpty()) {
            RowLook.drawEmpty(d, p, BaseComponent.text(emptyText, words, "minuit.no_results", "No results"), active, rx, listY, rw, Math.min(listH, 3 * ROW));
        } else {
            for (int i = firstVisible(); i < rows.size(); i++) {
                Row r = rows.get(i);
                int ry = listY + r.top - bar.offset();
                if (ry >= listY + listH) {
                    break;
                }
                boolean cut = RowLook.textCut(ry, r.height, listY, listH);
                if (r.entry == null) {
                    if (!cut) {
                        int room = Math.max(0, rw - LEFT - RIGHT);
                        if (r.shortcutRoom != room) {
                            r.shortcutRoom = room;
                            r.shortcut = TextMeasure.draw().ellipsize(r.title, room);
                        }
                        d.text(r.shortcut, rx + LEFT, ry + r.height - RowLook.FONT_H - 1, Ink.soft(p, under));
                    }
                } else {
                    drawCommand(d, t, p, r, i, ry, rx, rw, under, cut);
                }
            }
        }
        d.endScissor();
        bar.draw(d, t, barX(), listY, listH, bar.dragging() || overBar(mx, my));
    }

    private void drawCommand(Draw d, Theme t, Palette p, Row r, int index, int ry, int rx, int rw, int under, boolean cut) {
        Command command = r.entry.command;
        boolean isLit = index == lit;
        if (isLit) {
            t.frame().row(d, p, rx, ry, rw, r.height, p.primary());
        }
        if (cut) {
            return;
        }
        int ink = !command.enabled ? NavLook.disabled(p, under) : isLit ? p.primaryText() : Ink.on(p, under);
        int soft = !command.enabled || isLit ? ink : Ink.soft(p, under);
        int cx = rx + LEFT;
        int right = rx + rw - RIGHT;
        IconArt art = command.art(t);
        if (art != null) {
            // Fitted to the row (crisp n/gs shrink), veiled when disabled: an item ignores the grey ink. No item tooltip.
            int fitRoom = Math.min(ART_MAX, r.height - 2);
            ItemArt.inRow(d, p, art, cx, ry, r.height, fitRoom, soft, command.enabled);
            cx += ItemArt.rowWidth(art, fitRoom) + GAP;
        }
        int textY = RowLook.textY(ry, r.height);
        if (!command.shortcut.isEmpty()) {
            int room = Math.max(0, (right - cx) / 2);
            if (r.shortcutRoom != room) {
                r.shortcutRoom = room;
                r.shortcut = room <= 0 ? "" : TextMeasure.draw().ellipsize(command.shortcut, room);
            }
            if (!r.shortcut.isEmpty()) {
                int sw = d.width(r.shortcut);
                d.text(r.shortcut, right - sw + 1, textY, soft);
                right -= sw + GAP;
            }
        }
        int room = right - cx;
        if (room > 0) {
            prepare(r, room);
            int markInk = !command.enabled || isLit || Shapes.contrast(p.accent(), under) < Ink.TEXT ? ink : p.accent();
            for (int k = 0; k < r.pieces.length; k++) {
                boolean mark = r.pieceMark[k];
                int px = cx + r.pieceX[k];
                d.text(r.pieces[k], px, textY, mark ? markInk : ink);
                if (mark) {
                    d.fill(px, textY + RowLook.FONT_H, px + r.pieceW[k], textY + RowLook.FONT_H + 1, markInk);
                }
            }
        }
    }

    /** Cuts a row's label for its room and splits it at the marked letters; a frame only reads the result. */
    private void prepare(Row r, int room) {
        if (r.room == room) {
            return;
        }
        r.room = room;
        String text = r.entry.command.label;
        Draw measure = TextMeasure.draw();
        String shown = measure.ellipsize(text, room);
        r.cut = !shown.equals(text);
        int kept = r.cut ? Math.max(0, shown.length() - Draw.ELLIPSIS.length()) : text.length();
        int[] marked = TextMatch.marks(r.entry.folded, query);
        List<String> pieces = new ArrayList<>();
        List<int[]> places = new ArrayList<>();
        int from = 0;
        int cursor = 0;
        while (from < kept) {
            while (cursor < marked.length && marked[cursor] < from) {
                cursor++;
            }
            boolean isMark = cursor < marked.length && marked[cursor] == from;
            int to = from + 1;
            if (isMark) {
                int look = cursor + 1;
                while (to < kept && look < marked.length && marked[look] == to) {
                    to++;
                    look++;
                }
            } else {
                to = Math.max(to, Math.min(kept, cursor < marked.length ? marked[cursor] : kept));
            }
            String piece = text.substring(from, to);
            int width = measure.width(piece);
            pieces.add(piece);
            places.add(new int[] {measure.width(text.substring(0, from)), Math.max(1, width - 1), isMark ? 1 : 0});
            from = to;
        }
        if (kept < shown.length()) {
            pieces.add(shown.substring(kept));
            places.add(new int[] {measure.width(text.substring(0, kept)), 0, 0});
        }
        int count = pieces.size();
        r.pieces = pieces.toArray(new String[0]);
        r.pieceX = new int[count];
        r.pieceW = new int[count];
        r.pieceMark = new boolean[count];
        for (int k = 0; k < count; k++) {
            r.pieceX[k] = places.get(k)[0];
            r.pieceW[k] = places.get(k)[1];
            r.pieceMark[k] = places.get(k)[2] == 1;
        }
    }

    private void drawFooter(Draw d, Palette p) {
        int under = NavLook.solid(p.surface());
        int ruleY = listY + listH;
        int rx = cardX + inset.left();
        int rw = cardW - inset.left() - inset.right();
        d.fill(rx, ruleY, rx + rw, ruleY + 1, Shapes.alpha(p.text(), RULE_ALPHA));
        int room = Math.max(0, rw - LEFT - RIGHT);
        if (footCutFor != room) {
            footCutFor = room;
            footCut = room <= 0 ? "" : TextMeasure.draw().ellipsize(footer, room);
        }
        d.text(footCut, rx + LEFT, ruleY + 1 + (FOOT_H - RowLook.FONT_H) / 2 + 1, Ink.soft(p, under));
    }

    /** It rings nothing: the search box is always where the keys go. */
    @Override
    protected boolean drawFocus(Draw d, Theme t) {
        return true;
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!sync()) {
            return null; // closed, the widgets beneath answer
        }
        measure();
        int at = rowAt(mx, my);
        if (at < 0) {
            return List.of(); // empty, not null: a panel goes on to the widgets beneath on null
        }
        Row r = rows.get(at);
        List<String> lines = (!r.entry.command.enabled && r.entry.command.reason != null ? r.entry.command.reason : r.entry.command.tooltip);
        if (lines != null) {
            return lines;
        }
        return r.cut ? List.of(r.entry.command.label) : List.of();
    }

    @Override
    public boolean isOver(double mx, double my) {
        return open;
    }

    @Override
    protected boolean takesFocus() {
        return open;
    }

    @Override
    protected boolean navigable() {
        return open;
    }

    @Override
    public List<BaseComponent> stops() {
        return open ? List.of(this) : List.of();
    }

    @Override
    protected boolean activate() {
        if (sync() && lit >= 0) {
            run(lit);
        }
        return open;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!sync()) {
            return false;
        }
        measure();
        if (!inCard(mx, my)) {
            BaseComponent.click();
            close();
            return true;
        }
        if (button != 0) {
            return true;
        }
        if (overBar(mx, my)) {
            bar.press(mx, my, barX(), listY, listH);
            return true;
        }
        if (field.isOver(mx, my)) {
            field.mouseClicked(mx, my, button);
            return true;
        }
        int at = rowAt(mx, my);
        if (at >= 0) {
            run(at);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!open) {
            return false;
        }
        if (bar.drag(my)) {
            return true;
        }
        field.mouseDragged(mx, my, button, dx, dy);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!open) {
            return false;
        }
        bar.release();
        field.mouseReleased(mx, my, button);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!sync()) {
            return false;
        }
        measure();
        if (inCard(mx, my) && overflows()) {
            bar.wheel(amount, ScrollBar.WHEEL_ROWS * ROW);
        }
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!sync()) {
            return false;
        }
        measure();
        switch (key) {
            case UP -> moveLit(-1);
            case DOWN -> moveLit(1);
            case TAB -> moveGroup(shift ? -1 : 1);
            case ENTER -> {
                if (lit >= 0) {
                    run(lit);
                }
            }
            case ESCAPE -> {
                BaseComponent.click();
                close();
            }
            case SPACE -> { } // the character comes next, through charTyped
            default -> field.keyPressed(key, shift);
        }
        return true;
    }

    @Override
    public boolean charTyped(int codePoint) {
        if (!sync()) {
            return false;
        }
        measure();
        field.charTyped(codePoint);
        return true;
    }
}
