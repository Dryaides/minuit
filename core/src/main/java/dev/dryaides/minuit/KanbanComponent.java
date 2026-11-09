package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Columns of cards the player drags from one column to another.
 *
 * <pre>{@code
 * KanbanComponent<Quest> board = add(new KanbanComponent<Quest>(at(new Box("board", 8, 8, 284, 150)), Quest::name)
 *         .subtitle(Quest::reward)
 *         .column(words.get("mymod.todo"), todo)
 *         .column(words.get("mymod.doing"), doing)
 *         .column(words.get("mymod.done"), done)
 *         .limit(1, 3)
 *         .onMove((quest, fromCol, fromRow, toCol, toRow) -> save(quest, toCol, toRow)));
 * }</pre>
 *
 * <p>Press a card and move to lift it: it floats under the pointer, the cards in the column under it slide apart, and
 * an outline marks where it will land. Near a column's top or bottom edge the column scrolls by itself, and near the
 * board's sides so does the board. Escape, or a release over a column that refuses it, puts it back.
 * {@link #onMove} runs only when the card really moved: not for a drop where it began, a cancelled drag, a refusal or
 * the setters.
 *
 * <p>A column refuses a card when it is full ({@link #limit(int, int)}) or when {@link #canDrop(DropRule)} says so; it
 * dims during the drag, and its count goes red when a key tries.
 *
 * <p>With focus: the arrows walk the cards, home and end jump in the column, Ctrl with an arrow moves the card to the
 * next place or column, Ctrl with home or end to the column's ends, Enter or Space calls {@link #onActivate(Consumer)}.
 * The wheel scrolls the column under the pointer, or the board over a header or when the column has nothing to
 * scroll. A click selects, twice opens. Only the cards in view are drawn and only they ask for their words,
 * and a frame at rest allocates nothing once those are cut.
 *
 * <p>Items are matched by identity, so the same object should not be on the board twice. Under
 * {@link BaseComponent#reducedMotion} nothing slides.
 */
public class KanbanComponent<T> extends BaseComponent {
    /** Says whether a card may go to a column. */
    @FunctionalInterface
    public interface DropRule<T> {
        boolean allows(T item, int fromColumn, int toColumn);
    }

    /** Where a card went. */
    @FunctionalInterface
    public interface MoveListener<T> {
        void moved(T item, int fromColumn, int fromRow, int toColumn, int toRow);
    }

    /** Width of a column's scroll bar. */
    static final int BAR = ScrollBar.WIDTH;

    private static final int HEADER = 13;
    private static final int COLUMN_GAP = 4;
    private static final int MIN_COLUMN = 64;
    private static final int SIDE = 2;
    private static final int CARD_GAP = 2;
    private static final int CARD_H = 16;
    private static final int CARD_H_TWO = 26;
    private static final int CARD_PAD = 4;
    private static final int LINE_H = 10;
    private static final int H_BAR = 5;
    private static final int H_BAR_GAP = 2;
    private static final int MIN_THUMB = 8;
    private static final int DRAG_SLOP = 2;
    private static final int SLIDE_MS = 110;
    private static final int REFUSED_MS = 500;
    private static final long DOUBLE_CLICK_MS = 350L;
    private static final long NEVER = Long.MIN_VALUE / 2;
    private static final int REST_FACE = 0x18;
    private static final int REST_EDGE = 0x48;
    private static final int DIM_REFUSING = 0x90;
    private static final int GAP_FACE = 0x30;
    private static final float AUTO_MIN = 50F;
    private static final float AUTO_MAX = 180F;
    private static final float AUTO_BOOST = 2.5F;
    private static final float AUTO_BOOST_MAX = 20F;
    private static final int SIDE_ZONE = 14;
    private static final long MAX_STEP_MS = 100L;
    private static final int RAW_ESCAPE = 256;
    private static final int CACHE_MAX = 512;

    private static final class Column<T> {
        String title = "";
        final List<T> items = new ArrayList<>();
        int limit;
        final ScrollBar bar = new ScrollBar();
        // The autoscroll's fraction of a pixel, kept between frames so a slow scroll still moves.
        double carry;
        int gapFrom = Integer.MAX_VALUE;
        int gapTo = Integer.MAX_VALUE;
        long gapAt = NEVER;
        String fitTitle = "";
        String count = "";
        boolean headerDirty = true;
        boolean refusing;
    }

    private static final class Words {
        String title = "";
        String subtitle = "";
        boolean cut;
        String wholeTitle = "";
        int width;
        IconArt iconArt;
        Picture icon;
        Picture coverFrom;
        Picture coverShown;
    }

    private final Function<T, String> label;
    private final List<Column<T>> columns = new ArrayList<>();
    private FrameStyle frame = FrameStyles.VANILLA;
    private final Map<T, Words> words = new IdentityHashMap<>();
    private final RowLook.HoverFade hover = new RowLook.HoverFade();

    private Function<T, String> subtitle;
    private ListComponent.RowRenderer<T> renderer;
    private DropRule<T> canDrop;
    private MoveListener<T> onMove;
    private Consumer<T> onActivate;
    private Consumer<T> onSelect;
    private String emptyText;
    private int minColumn = MIN_COLUMN;
    private int cardH;
    private Function<T, IconArt> iconArt;
    private Function<T, Picture> cover;
    private int coverH;
    private ItemView tipItem;
    private int lastMx = -100_000;
    private int lastMy = -100_000;

    private int boardScroll;
    private double boardScrollExact;
    private int columnW;
    private int laidW = -1;

    private T selected;
    private int cursorCol = -1;
    private int cursorRow = -1;
    private T lastClicked;
    private long lastClickAt = NEVER;
    private int hoverCol = -1;
    private int hoverRow = -1;
    private int refusedCol = -1;
    private long refusedAt = NEVER;

    private T armed;
    private int armCol;
    private int armRow;
    private int armX;
    private int armY;

    private T dragged;
    private int fromCol;
    private int fromRow;
    private int dropCol = -1;
    private int dropRow = -1;
    private int grabX;
    private int grabY;
    private int dragX;
    private int dragY;
    private int liftY;
    private long lastStep;
    private long edgeSince = NEVER;

    private Palette inksFor;
    private boolean inksActive;
    private int restInk;
    private int softInk;
    private int idleSelectedInk;
    private int slotInk;

    private int barCol = -1;
    private Box[] cardBoxes = new Box[1];
    private boolean boardBar;
    private int boardBarGrab;

    public KanbanComponent(Box box, Function<T, String> label) {
        super(box);
        this.label = Objects.requireNonNull(label, "label");
    }

    /** Adds a column. The items are copied. */
    public KanbanComponent<T> column(String title, Collection<? extends T> items) {
        Column<T> c = new Column<>();
        c.title = title == null ? "" : title;
        if (items != null) {
            for (T item : items) {
                if (item != null) {
                    c.items.add(item);
                }
            }
        }
        columns.add(c);
        laidW = -1;
        return this;
    }

    /** The most cards a column takes; 0 for no limit. Cards past a new limit stay until moved. */
    public KanbanComponent<T> limit(int column, int limit) {
        Column<T> c = columns.get(column);
        c.limit = Math.max(0, limit);
        c.headerDirty = true;
        return this;
    }

    /** A second line on every card. */
    public KanbanComponent<T> subtitle(Function<T, String> subtitle) {
        this.subtitle = subtitle;
        words.clear();
        return this;
    }

    /**
     * An icon before each card's words: an item, a sprite, a {@link Picture}. Items stay crisp at 16 and show
     * the game's own tooltip under the pointer. Cards grow to 18 tall to hold it. Null for none.
     */
    public KanbanComponent<T> iconArt(Function<T, IconArt> iconArt) {
        this.iconArt = iconArt;
        words.clear();
        return this;
    }

    /**
     * A picture across the top of each card, this tall: a screenshot, a map. Null, or a null picture for one
     * card, for none; the card is as tall for all, so that the columns line up.
     */
    public KanbanComponent<T> cover(Function<T, Picture> cover, int height) {
        this.cover = cover;
        this.coverH = cover == null ? 0 : Math.max(0, height);
        return this;
    }

    /** Draws a card's inside in place of its words; the card has drawn its own face. */
    public KanbanComponent<T> renderer(ListComponent.RowRenderer<T> renderer) {
        this.renderer = renderer;
        return this;
    }

    /** Which columns a card may go to. By default all of them with room. */
    public KanbanComponent<T> canDrop(DropRule<T> canDrop) {
        this.canDrop = canDrop;
        return this;
    }

    /** Called when a drag or a key has really moved a card, never otherwise. */
    public KanbanComponent<T> onMove(MoveListener<T> onMove) {
        this.onMove = onMove;
        return this;
    }

    /** Called on a double click, Enter or Space. */
    public KanbanComponent<T> onActivate(Consumer<T> onActivate) {
        this.onActivate = onActivate;
        return this;
    }

    /** Called when a click or a key selects another card. */
    public KanbanComponent<T> onSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /** Text in a column with no cards. */
    public KanbanComponent<T> emptyText(String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    /** The least width of a column: past that the board scrolls sideways. 64 by default. */
    public KanbanComponent<T> minColumnWidth(int width) {
        minColumn = Math.max(20, width);
        laidW = -1;
        return this;
    }

    /** A card's height. By default enough for its words. */
    public KanbanComponent<T> cardHeight(int height) {
        cardH = Math.max(0, height);
        clampAll();
        return this;
    }

    public KanbanComponent<T> active(boolean active) {
        this.active = active;
        if (!active) {
            dropGestures();
        }
        return this;
    }

    @Override
    public KanbanComponent<T> tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** New cards for a column, without calling back. An open drag ends. */
    public void setItems(int column, Collection<? extends T> items) {
        dropGestures();
        Column<T> c = columns.get(column);
        c.items.clear();
        if (items != null) {
            for (T item : items) {
                if (item != null) {
                    c.items.add(item);
                }
            }
        }
        words.clear();
        c.headerDirty = true;
        clampAll();
        clampCursor();
    }

    /** Moves a card without calling back. False when either place is out of range or nothing would change. */
    public boolean move(int column, int row, int toColumn, int toRow) {
        if (dragged != null || column < 0 || column >= columns.size() || toColumn < 0 || toColumn >= columns.size()) {
            return false;
        }
        Column<T> from = columns.get(column);
        Column<T> to = columns.get(toColumn);
        if (row < 0 || row >= from.items.size()) {
            return false;
        }
        int most = column == toColumn ? from.items.size() - 1 : to.items.size();
        if (toRow < 0 || toRow > most || (column == toColumn && row == toRow)) {
            return false;
        }
        to.items.add(toRow, removeAt(from, row));
        to.headerDirty = true;
        clampAll();
        clampCursor();
        return true;
    }

    private T removeAt(Column<T> c, int row) {
        c.headerDirty = true;
        return c.items.remove(row);
    }

    public int columnCount() {
        return columns.size();
    }

    /** The cards of a column, in order. A copy: a drag in progress does not show until it lands. */
    public List<T> items(int column) {
        return List.copyOf(columns.get(column).items);
    }

    public T selected() {
        return selected;
    }

    /** Sets the selection, without calling back. */
    public void select(T item) {
        selected = item;
    }

    public boolean isDragging() {
        return dragged != null;
    }

    public T draggedItem() {
        return dragged;
    }

    /** The column a drag would land in now, or -1 when it would go back. */
    int dropColumn() {
        return dragged == null ? -1 : dropCol;
    }

    /** The place a drag would land at now, or -1. */
    int dropRow() {
        return dragged == null ? -1 : dropRow;
    }

    public int cursorColumn() {
        return cursorCol;
    }

    public int cursorRow() {
        return cursorRow;
    }

    double columnScroll(int column) {
        return barOf(column).offset();
    }

    public void setColumnScroll(int column, double scroll) {
        barOf(column).offset((int) Math.round(scroll));
    }

    int boardScroll() {
        return boardScroll;
    }

    int boardMaxScroll() {
        layout();
        return Math.max(0, boardW() - w);
    }

    /** A column's whole place. */
    Box columnBox(int column) {
        layout();
        return new Box("column", colX(column), y, columnW, colH());
    }

    /** A card's place, as if nothing were moving. */
    Box cardBox(int column, int row) {
        layout();
        return new Box("card", viewX(column), cardTop(column, row), viewW(column), cardH());
    }

    private int cardTop(int column, int row) {
        return viewY() + row * step() - barOf(column).offset();
    }

    /** As Escape does during a drag: everything goes back, with no callback. */
    public void cancelDrag() {
        armed = null;
        if (dragged != null) {
            endDrag(false);
        }
    }

    private void layout() {
        if (laidW == w && columnW > 0) {
            return;
        }
        laidW = w;
        int n = Math.max(1, columns.size());
        columnW = Math.max(minColumn, (w - (n - 1) * COLUMN_GAP) / n);
        for (int i = 0; i < columns.size(); i++) {
            columns.get(i).headerDirty = true;
        }
        clampAll();
    }

    private int boardW() {
        return columns.size() * columnW + Math.max(0, columns.size() - 1) * COLUMN_GAP;
    }

    private boolean boardOverflows() {
        return boardW() > w;
    }

    private int colX(int column) {
        return x + column * (columnW + COLUMN_GAP) - boardScroll;
    }

    private int colH() {
        return Math.max(0, h - (boardOverflows() ? H_BAR + H_BAR_GAP : 0));
    }

    private int cardH() {
        if (cardH > 0) {
            return cardH;
        }
        return (subtitle == null ? (iconArt != null ? ICON_CARD_H : CARD_H) : CARD_H_TWO) + coverRoom();
    }

    private int coverRoom() {
        return coverH > 0 ? coverH + 1 : 0;
    }

    private static final int ICON_CARD_H = 18;
    private static final int ICON_GAP = 3;

    private IconArt iconOf(T item) {
        return iconArt == null || item == null ? null : iconArt.apply(item);
    }

    private int iconRoom(T item) {
        IconArt art = iconOf(item);
        return art == null ? 0 : Math.min(16, art.width()) + ICON_GAP;
    }

    private int step() {
        return cardH() + CARD_GAP;
    }

    private int viewY() {
        return y + HEADER;
    }

    private int viewH() {
        return Math.max(0, colH() - HEADER - SIDE);
    }

    private int barX(int column) {
        return ScrollBar.x(frame, colX(column) + columnW - SIDE);
    }

    private int viewX(int column) {
        return colX(column) + SIDE;
    }

    private int viewW(int column) {
        return Math.max(0, columnW - 2 * SIDE - (columnOverflows(column) ? ScrollBar.room(frame) : 0));
    }

    /** How many cards show in a column: the lifted one has left its own. */
    private int shownCount(int column) {
        int size = columns.get(column).items.size();
        return dragged != null && column == fromCol ? size - 1 : size;
    }

    private T shownItem(int column, int shown) {
        int at = dragged != null && column == fromCol && shown >= fromRow ? shown + 1 : shown;
        return columns.get(column).items.get(at);
    }

    private int contentH(int column) {
        int cards = columns.get(column).items.size();
        if (dragged != null && column != fromCol && column == dropCol) {
            cards++;
        }
        return cards * step() - (cards > 0 ? CARD_GAP : 0);
    }

    private boolean columnOverflows(int column) {
        return contentH(column) > viewH();
    }

    /** The column's scroll, its lengths brought up to date: a drag changes them by a card. */
    private ScrollBar barOf(int column) {
        ScrollBar bar = columns.get(column).bar;
        bar.content(contentH(column), viewH());
        return bar;
    }

    private void clampAll() {
        for (int i = 0; i < columns.size(); i++) {
            barOf(i);
        }
        boardScrollExact = Math.max(0D, Math.min(Math.max(0, boardW() - w), boardScrollExact));
        boardScroll = (int) boardScrollExact;
    }

    private void clampCursor() {
        if (cursorCol < 0) {
            return;
        }
        if (cursorCol >= columns.size()) {
            cursorCol = -1;
            cursorRow = -1;
            return;
        }
        cursorRow = Math.min(cursorRow, columns.get(cursorCol).items.size() - 1);
    }

    private int columnAt(double mx) {
        for (int i = 0; i < columns.size(); i++) {
            int left = colX(i);
            if (mx >= left && mx < left + columnW) {
                return i;
            }
        }
        return -1;
    }

    /** The column nearest a point, for a drag over a gap or past the ends. */
    private int nearestColumn(int mx) {
        int best = -1;
        int bestGap = Integer.MAX_VALUE;
        for (int i = 0; i < columns.size(); i++) {
            int left = colX(i);
            int gap = mx < left ? left - mx : mx >= left + columnW ? mx - (left + columnW - 1) : 0;
            if (gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }

    /** The row of the card at a point of a column, or -1. */
    private int rowAt(int column, double mx, double my) {
        int vx = viewX(column);
        if (mx < vx || mx >= vx + viewW(column) || my < viewY() || my >= viewY() + viewH()) {
            return -1;
        }
        int inside = (int) Math.floor(my) - viewY() + barOf(column).offset();
        int row = inside / step();
        return row < columns.get(column).items.size() && inside % step() < cardH() ? row : -1;
    }

    private boolean overHeader(double my) {
        return my >= y && my < y + HEADER;
    }

    private boolean overColumnBar(int column, double mx, double my) {
        if (!columnOverflows(column)) {
            return false;
        }
        int barX = barX(column);
        return mx >= barX && mx < colX(column) + columnW && my >= viewY() && my < viewY() + viewH();
    }

    private boolean overBoardBar(double mx, double my) {
        return boardOverflows() && my >= y + h - H_BAR && my < y + h && mx >= x && mx < x + w;
    }

    private int boardThumbW() {
        return Math.min(w, Math.max(MIN_THUMB, w * w / Math.max(1, boardW())));
    }

    private int boardThumbX() {
        int most = Math.max(1, boardW() - w);
        return x + (w - boardThumbW()) * boardScroll / most;
    }

    private static float eased(long age) {
        float t = 1F - Math.max(0F, Math.min(1F, age / (float) SLIDE_MS));
        return 1F - t * t * t;
    }

    /** How many steps down a shown card sits, part way through a slide if there is one. */
    private float pushOf(Column<T> c, int shown) {
        float before = shown >= c.gapFrom ? 1F : 0F;
        float after = shown >= c.gapTo ? 1F : 0F;
        if (before == after || reducedMotion || c.gapAt == NEVER) {
            return after;
        }
        long age = BaseComponent.clock.getAsLong() - c.gapAt;
        return age >= SLIDE_MS ? after : before + (after - before) * eased(age);
    }

    private void setGap(int column, int slot) {
        Column<T> c = columns.get(column);
        if (c.gapTo == slot) {
            return;
        }
        c.gapFrom = c.gapTo;
        c.gapTo = slot;
        c.gapAt = reducedMotion ? NEVER : BaseComponent.clock.getAsLong();
    }

    private void resetGaps() {
        for (int i = 0; i < columns.size(); i++) {
            Column<T> c = columns.get(i);
            c.gapFrom = Integer.MAX_VALUE;
            c.gapTo = Integer.MAX_VALUE;
            c.gapAt = NEVER;
        }
    }

    private boolean accepts(T item, int from, int to) {
        if (from != to) {
            Column<T> target = columns.get(to);
            if (target.limit > 0 && target.items.size() >= target.limit) {
                return false;
            }
        }
        return from == to || canDrop == null || canDrop.allows(item, from, to);
    }

    private void lift() {
        T item = armed;
        armed = null;
        dragged = item;
        fromCol = armCol;
        fromRow = armRow;
        Box card = cardBox(armCol, armRow);
        grabX = armX - card.x();
        grabY = armY - card.y();
        liftY = armY;
        lastStep = BaseComponent.clock.getAsLong();
        edgeSince = NEVER;
        resetGaps();
        // The place it left stays open, so nothing jumps as it lifts.
        Column<T> own = columns.get(fromCol);
        own.gapFrom = fromRow;
        own.gapTo = fromRow;
        dropCol = fromCol;
        dropRow = fromRow;
        for (int i = 0; i < columns.size(); i++) {
            columns.get(i).refusing = i != fromCol && !accepts(item, fromCol, i);
        }
        BaseComponent.click();
        aim();
    }

    private int floatTop() {
        int low = viewY();
        int high = Math.max(low, y + colH() - cardH());
        return Math.max(low, Math.min(high, dragY - grabY));
    }

    private int floatLeft() {
        int width = columnW - 2 * SIDE;
        int high = Math.max(x, x + w - width);
        return Math.max(x, Math.min(high, dragX - grabX));
    }

    /** Picks the column and the place under the floating card. */
    private void aim() {
        int column = nearestColumn(dragX);
        if (column < 0) {
            return;
        }
        int nextCol = columns.get(column).refusing ? -1 : column;
        int nextRow = -1;
        if (nextCol >= 0) {
            int top = floatTop() - viewY() + barOf(nextCol).offset();
            nextRow = Math.max(0, Math.min(shownCount(nextCol), Math.floorDiv(top + step() / 2, step())));
        }
        if (nextCol != dropCol) {
            if (dropCol >= 0) {
                setGap(dropCol, Integer.MAX_VALUE);
            }
            dropCol = nextCol;
        }
        dropRow = nextRow;
        if (nextCol >= 0) {
            Column<T> c = columns.get(nextCol);
            if (c.gapTo == Integer.MAX_VALUE) {
                // Opens from the column's end, so a gap never appears at once under the pointer.
                c.gapFrom = shownCount(nextCol);
                c.gapTo = c.gapFrom;
            }
            setGap(nextCol, nextRow);
        }
        clampAll();
    }

    /** Scrolls the column and the board while the pointer holds near an edge. */
    private void stepDrag() {
        long now = BaseComponent.clock.getAsLong();
        long elapsed = Math.max(0L, Math.min(MAX_STEP_MS, now - lastStep));
        lastStep = now;
        boolean moved = false;
        boolean any = false;
        int vy = viewY();
        int vh = viewH();
        int zone = Math.max(1, Math.min(cardH(), vh / 3));
        boolean up = dragY < vy + zone && dragY < liftY;
        boolean down = dragY >= vy + vh - zone && dragY > liftY;
        int column = nearestColumn(dragX);
        if (column >= 0 && (up || down) && barOf(column).max() > 0) {
            any = true;
            Column<T> c = columns.get(column);
            int depthIn = up ? vy + zone - dragY : dragY - (vy + vh - zone) + 1;
            int before = c.bar.offset();
            c.carry += (up ? -1 : 1) * speed(depthIn / (float) zone, now) * elapsed / 1000F;
            int step = (int) c.carry;
            c.carry -= step;
            c.bar.offset(before + step);
            moved = c.bar.offset() != before;
        }
        if (boardOverflows()) {
            boolean left = dragX < x + SIDE_ZONE;
            boolean right = dragX >= x + w - SIDE_ZONE;
            if (left || right) {
                any = true;
                int depthIn = left ? x + SIDE_ZONE - dragX : dragX - (x + w - SIDE_ZONE) + 1;
                double before = boardScrollExact;
                boardScrollExact += (left ? -1 : 1) * speed(depthIn / (float) SIDE_ZONE, now) * elapsed / 1000F;
                boardScrollExact = Math.max(0D, Math.min(boardW() - w, boardScrollExact));
                boardScroll = (int) boardScrollExact;
                moved |= boardScrollExact != before;
            }
        }
        edgeSince = !any ? NEVER : edgeSince == NEVER ? now : edgeSince;
        if (moved) {
            aim();
        }
    }

    private float speed(float depth, long now) {
        float boost = edgeSince == NEVER ? 1F : Math.min(AUTO_BOOST_MAX, 1F + AUTO_BOOST * (now - edgeSince) / 1000F);
        return (AUTO_MIN + (AUTO_MAX - AUTO_MIN) * Math.min(1F, depth)) * boost;
    }

    private void endDrag(boolean commit) {
        T item = dragged;
        int toCol = dropCol;
        int toRow = dropRow;
        dragged = null;
        resetGaps();
        for (int i = 0; i < columns.size(); i++) {
            columns.get(i).refusing = false;
        }
        if (!commit) {
            clampAll();
            return;
        }
        if (toCol < 0) {
            refusedCol = nearestColumn(dragX);
            refusedAt = BaseComponent.clock.getAsLong();
            clampAll();
            return;
        }
        commitMove(item, fromCol, fromRow, toCol, toRow);
    }

    /** Puts a card in its new place, and tells only when that place is not the old one. */
    private void commitMove(T item, int column, int row, int toCol, int toRow) {
        if (column == toCol && row == toRow) {
            clampAll();
            return;
        }
        Column<T> from = columns.get(column);
        Column<T> to = columns.get(toCol);
        removeAt(from, row);
        int place = Math.max(0, Math.min(to.items.size(), toRow));
        to.items.add(place, item);
        to.headerDirty = true;
        cursorCol = toCol;
        cursorRow = place;
        clampAll();
        reveal(toCol, place);
        BaseComponent.click();
        if (onMove != null) {
            onMove.moved(item, column, row, toCol, place);
        }
    }

    private void releaseColumnBar() {
        if (barCol >= 0 && barCol < columns.size()) {
            columns.get(barCol).bar.release();
        }
        barCol = -1;
    }

    private void dropGestures() {
        armed = null;
        releaseColumnBar();
        boardBar = false;
        if (dragged != null) {
            endDrag(false);
        }
    }

    private void reveal(int column, int row) {
        ScrollBar bar = barOf(column);
        int top = row * step();
        if (top < bar.offset()) {
            bar.offset(top);
        } else if (top + cardH() > bar.offset() + viewH()) {
            bar.offset(top + cardH() - viewH());
        }
        int left = column * (columnW + COLUMN_GAP);
        if (left < boardScrollExact) {
            boardScrollExact = left;
        } else if (left + columnW > boardScrollExact + w) {
            boardScrollExact = left + columnW - w;
        }
        boardScrollExact = Math.max(0D, Math.min(Math.max(0, boardW() - w), boardScrollExact));
        boardScroll = (int) boardScrollExact;
    }

    private Words wordsOf(T item, int width) {
        // Each card keeps the width it was cut for: columns with and without a bar differ, and one shared
        // width would throw the words away twice a frame.
        Words known = words.get(item);
        if (known != null && known.width == width) {
            return known;
        }
        if (words.size() >= CACHE_MAX) {
            words.clear();
        }
        known = new Words();
        known.width = width;
        Draw measure = TextMeasure.draw();
        String whole = label.apply(item);
        known.wholeTitle = whole == null ? "" : whole;
        int room = Math.max(0, width - 2 * CARD_PAD - iconRoom(item));
        known.title = measure.ellipsize(known.wholeTitle, room);
        String second = subtitle == null ? null : subtitle.apply(item);
        known.subtitle = second == null ? "" : measure.ellipsize(second, room);
        known.cut = !known.title.equals(known.wholeTitle);
        words.put(item, known);
        return known;
    }

    private void refreshHeader(Column<T> c) {
        c.headerDirty = false;
        c.count = c.limit > 0 ? c.items.size() + "/" + c.limit : Integer.toString(c.items.size());
        int room = Math.max(0, columnW - 2 * CARD_PAD - TextMeasure.width(c.count) - 2);
        c.fitTitle = TextMeasure.draw().ellipsize(c.title, room);
    }

    /** Worked out once for a theme: the row inks judge several colours, which is not for every card of every frame. */
    private void refreshInks(Palette p) {
        if (inksFor == p && inksActive == active) {
            return;
        }
        inksFor = p;
        inksActive = active;
        restInk = RowLook.ink(p, 0, false, active);
        softInk = active ? RowLook.softInk(p, restInk) : restInk;
        idleSelectedInk = RowLook.ink(p, Shapes.alpha(p.primary(), 0x80), true, active);
        int solid = DataLook.opaque(p.primary());
        slotInk = Shapes.contrast(solid, DataLook.opaque(p.well())) >= 2D ? solid : DataLook.opaque(RowLook.ink(p, 0, false, true));
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        layout();
        Palette p = t.palette();
        frame = t.frame();
        refreshInks(p);
        if (!active) {
            dropGestures();
        }
        if (dragged != null) {
            stepDrag();
        }
        hoverCol = -1;
        hoverRow = -1;
        if (active && hovered && settled() && dragged == null && armed == null && barCol < 0 && !boardBar) {
            int column = columnAt(mx);
            if (column >= 0) {
                int row = rowAt(column, mx, my);
                if (row >= 0) {
                    hoverCol = column;
                    hoverRow = row;
                }
            }
        }
        hover.step(hoverCol < 0 ? -1 : hoverCol * 100_000 + hoverRow);
        lastMx = mx;
        lastMy = my;
        tipItem = null;
        if (w <= 0 || h <= 0 || columns.isEmpty()) {
            return;
        }
        d.scissor(x, y, x + w, y + h);
        for (int i = 0; i < columns.size(); i++) {
            int left = colX(i);
            if (left + columnW > x && left < x + w) {
                drawColumn(d, t, i, mx, my, hovered);
            }
        }
        if (boardOverflows()) {
            drawBoardBar(d, p, hovered && overBoardBar(mx, my));
        }
        d.endScissor();
        if (dragged != null) {
            drawFloating(d, t);
        }
        if (tipItem != null && tooltip == null) {
            d.itemTooltip(tipItem, mx, my); // the item is the card's content: the game's own tooltip
        }
    }

    private void drawColumn(Draw d, Theme t, int column, int mx, int my, boolean hovered) {
        Palette p = t.palette();
        Column<T> c = columns.get(column);
        if (c.headerDirty) {
            refreshHeader(c);
        }
        int left = colX(column);
        int colH = colH();
        PictureLook.flat(d, t, left, y, columnW, colH, true); // flat: the board sits in a panel already
        int ink = restInk;
        int soft = softInk;
        boolean alert = active && (BaseComponent.clock.getAsLong() - refusedAt < REFUSED_MS && refusedCol == column
                || (c.limit > 0 && c.items.size() > c.limit));
        if (colH >= HEADER) {
            d.text(c.fitTitle, left + CARD_PAD, y + 3, ink);
            int countW = d.width(c.count);
            d.text(c.count, left + columnW - CARD_PAD - countW, y + 3, alert ? Ink.readable(p.bad(), DataLook.back(p), Ink.MARK) : soft);
        }
        int vh = viewH();
        int vw = viewW(column);
        int vx = viewX(column);
        int vy = viewY();
        if (vh > 0 && vw > 0) {
            d.scissor(Math.max(x, vx), vy, Math.min(x + w, vx + vw), vy + vh);
            drawCards(d, t, column, vx, vy, vw, vh, ink, soft);
            d.endScissor();
        }
        if (columnOverflows(column) && RowLook.barFits(columnW, vh, BAR)) {
            int barX = barX(column);
            barOf(column).draw(d, t, barX, vy, vh, active && hovered && dragged == null && overColumnBar(column, mx, my));
            if (!active) {
                RowLook.dimBar(d, p, barX, vy, BAR, vh);
            }
        }
        if (c.refusing && dragged != null) {
            // Over its text, so it says at once that this column will not take the card.
            d.nextLayer();
            d.fill(left + 1, y + 1, left + columnW - 1, y + colH - 1, Shapes.alpha(DataLook.opaque(p.well()), DIM_REFUSING));
        }
    }

    private void drawCards(Draw d, Theme t, int column, int vx, int vy, int vw, int vh, int ink, int soft) {
        Palette p = t.palette();
        Column<T> c = columns.get(column);
        int count = shownCount(column);
        int scroll = barOf(column).offset();
        int step = step();
        if (count == 0 && !(dragged != null && column == dropCol)) {
            if (emptyText != null) {
                RowLook.drawEmpty(d, p, emptyText, active, vx, vy, vw, vh);
            }
            return;
        }
        if (dragged != null && column == dropCol) {
            drawSlot(d, t, vx, vy + dropRow * step - scroll, vw);
        }
        int first = Math.max(0, scroll / step - 1);
        int end = Math.min(count, (scroll + vh) / step + 2);
        for (int k = first; k < end; k++) {
            int cy = vy + k * step + Math.round(step * pushOf(c, k)) - scroll;
            if (cy + cardH() <= vy || cy >= vy + vh) {
                continue;
            }
            T item = shownItem(column, k);
            boolean isHovered = dragged == null && column == hoverCol && k == hoverRow;
            int slot = 1 + column * (vh / step + 4) + k % (vh / step + 4);
            drawCard(d, t, item, slot, vx, cy, vw, isHovered, vy, vh, ink, soft);
        }
    }

    /** An outline where the card will land. */
    private void drawSlot(Draw d, Theme t, int vx, int top, int vw) {
        t.frame().plate(d, t.palette(), vx, top, vw, cardH(), Shapes.alpha(slotInk, GAP_FACE), slotInk);
    }

    private void drawCard(Draw d, Theme t, T item, int slot, int cx, int cy, int cw, boolean isHovered, int vy, int vh, int ink, int soft) {
        Palette p = t.palette();
        boolean isSelected = item == selected;
        int face = RowLook.face(p, active, focused, isSelected, isHovered, hover.fade());
        int plain = DataLook.opaque(ink);
        FrameStyle frame = t.frame();
        frame.plate(d, p, cx, cy, cw, cardH(), plain & 0xFFFFFF | REST_FACE << 24, 0);
        frame.plate(d, p, cx, cy, cw, cardH(), face >>> 24 != 0 ? face : 0, plain & 0xFFFFFF | REST_EDGE << 24);
        int cardInk;
        if (!isSelected) {
            cardInk = ink;
        } else if (!active) {
            cardInk = RowLook.ink(p, face, true, false);
        } else {
            cardInk = focused ? p.primaryText() : idleSelectedInk;
        }
        if (renderer != null) {
            renderer.draw(d, t, item, rendererBox(slot, cx, cy, cw), isHovered, isSelected);
            return;
        }
        Words known = wordsOf(item, cw);
        int top = cy;
        int bodyH = cardH() - coverRoom();
        if (coverH > 0) {
            Picture pic = cover.apply(item);
            if (pic != null) {
                Picture shown = coverOf(known, pic);
                d.scissor(cx, Math.max(cy, vy), cx + cw, Math.min(cy + coverH + 1, vy + vh));
                shown.draw(d, t, cx + 1, cy + 1, cw - 2, coverH, active);
                if (slot != 0 && PictureLook.canRound(t) && p.well() >>> 24 == 0xFF) {
                    // the column's well around the card's top corners, then the card's ring over the picture's edge
                    PictureLook.corners(d, cx, cy, cw, cardH(), frame.plateCorner(cw, cardH()), p.well());
                    frame.plate(d, p, cx, cy, cw, cardH(), 0, plain & 0xFFFFFF | REST_EDGE << 24);
                }
                d.endScissor();
            }
            top += coverRoom();
        }
        int textX = cx + CARD_PAD;
        IconArt art = iconOf(item);
        if (art != null) {
            int side = Math.min(16, Math.max(0, bodyH - 2));
            int ix = cx + CARD_PAD - 1;
            int iy = top + (bodyH - side) / 2;
            if (side >= 8 && iy >= vy && iy + side <= vy + vh) {
                iconOf(known, art).draw(d, t, ix, iy, side, side, active);
                ItemView item0 = art instanceof ItemView view ? view : art instanceof Picture pic ? pic.item() : null;
                if (item0 != null && isHovered && lastMx >= ix && lastMx < ix + side && lastMy >= iy && lastMy < iy + side) {
                    tipItem = item0;
                }
            }
            textX += Math.min(16, art.width()) + ICON_GAP - 1;
        }
        boolean two = subtitle != null && bodyH >= 2 * LINE_H + 2;
        int titleY = two ? top + 3 : RowLook.textY(top, bodyH);
        if (titleY >= vy && titleY + RowLook.FONT_H <= vy + vh) {
            d.text(known.title, textX, titleY, cardInk);
        }
        if (two && titleY + LINE_H >= vy && titleY + LINE_H + RowLook.FONT_H <= vy + vh) {
            d.text(known.subtitle, textX, titleY + LINE_H, isSelected || !active ? cardInk : soft);
        }
    }

    /** The box handed to the renderer, kept per card so a still board allocates none; slot 0 is the lifted card. */
    private Box rendererBox(int slot, int bx, int by, int bw) {
        if (slot >= cardBoxes.length) {
            cardBoxes = java.util.Arrays.copyOf(cardBoxes, slot + 16);
        }
        Box box = cardBoxes[slot];
        if (box == null || box.x() != bx || box.y() != by || box.w() != bw || box.h() != cardH()) {
            box = new Box("card", bx, by, bw, cardH());
            cardBoxes[slot] = box;
        }
        return box;
    }

    private static Picture iconOf(Words known, IconArt art) {
        if (known.iconArt != art) {
            known.iconArt = art;
            known.icon = Picture.of(art);
        }
        return known.icon;
    }

    /** A picture that would leave bands in a card fills it instead; worked out once per picture. */
    private static Picture coverOf(Words known, Picture pic) {
        if (known.coverFrom != pic) {
            known.coverFrom = pic;
            known.coverShown = pic.art() == null && pic.fit() == Picture.Fit.CONTAIN ? pic.fit(Picture.Fit.COVER) : pic;
        }
        return known.coverShown;
    }

    private void drawBoardBar(Draw d, Palette p, boolean lit) {
        int top = y + h - H_BAR;
        d.fill(x, top, x + w, top + H_BAR, DataLook.opaque(p.well()) & 0xFFFFFF | 0xA0 << 24);
        int ink = DataLook.opaque(restInk);
        int thumb = boardThumbX();
        d.fill(thumb, top + 1, thumb + boardThumbW(), top + H_BAR - 1, Shapes.alpha(ink, lit || boardBar ? 0xC0 : 0x80));
    }

    /** The lifted card: a raised primary face on a solid back, so nothing shows through it in glass. */
    private void drawFloating(Draw d, Theme t) {
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        int left = floatLeft();
        int top = floatTop();
        int width = columnW - 2 * SIDE;
        d.nextLayer(); // the text beneath may sort above a rectangle otherwise
        d.scissor(x, y, x + w, y + h);
        frame.dropShadow(d, p, left, top, width, cardH(), 2);
        frame.plate(d, p, left, top, width, cardH(), DataLook.opaque(p.surface()), 0);
        frame.raised(d, p, left, top, width, cardH(), p.primary(), Shapes.Relief.FLAT);
        if (renderer != null) {
            renderer.draw(d, t, dragged, rendererBox(0, left, top, width), false, true);
        } else {
            Words known = wordsOf(dragged, viewW(fromCol));
            int body = top + coverRoom();
            int bodyH = cardH() - coverRoom();
            Picture pic = coverH > 0 ? cover.apply(dragged) : null;
            if (pic != null) {
                coverOf(known, pic).draw(d, t, left + 1, top + 1, width - 2, coverH);
            }
            int textX = left + CARD_PAD;
            IconArt art = iconOf(dragged);
            if (art != null && bodyH >= 10) {
                int side = Math.min(16, bodyH - 2);
                iconOf(known, art).draw(d, t, textX - 1, body + (bodyH - side) / 2, side, side);
                textX += Math.min(16, art.width()) + ICON_GAP - 1;
            }
            boolean two = subtitle != null && bodyH >= 2 * LINE_H + 2;
            int titleY = two ? body + 3 : RowLook.textY(body, bodyH);
            d.text(known.title, textX, titleY, p.primaryText());
            if (two) {
                d.text(known.subtitle, textX, titleY + LINE_H, p.primaryText());
            }
        }
        d.endScissor();
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!visible) {
            return null;
        }
        if (dragged != null || armed != null || tipItem != null) {
            return List.of();
        }
        if (active) {
            layout();
            int column = columnAt(mx);
            if (column >= 0 && isOver(mx, my)) {
                int row = rowAt(column, mx, my);
                if (row >= 0) {
                    Words known = words.get(columns.get(column).items.get(row));
                    if (known != null && known.cut) {
                        return List.of(known.wholeTitle);
                    }
                }
            }
        }
        return super.tooltipAt(mx, my);
    }

    private boolean accepts() {
        return visible && active && settled();
    }

    @Override
    public void tick() {
        super.tick();
        if (!visible || !active) {
            dropGestures();
        }
    }

    @Override
    protected boolean takesFocus() {
        return visible && active;
    }

    @Override
    protected boolean navigable() {
        if (!accepts()) {
            return false;
        }
        for (int i = 0; i < columns.size(); i++) {
            if (!columns.get(i).items.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean cursorInView() {
        if (cursorCol < 0 || cursorRow < 0 || cursorCol >= columns.size() || cursorRow >= columns.get(cursorCol).items.size()) {
            return false;
        }
        int top = cardTop(cursorCol, cursorRow);
        int left = viewX(cursorCol);
        return top >= viewY() && top + cardH() <= viewY() + viewH() && left >= x && left + viewW(cursorCol) <= x + w;
    }

    @Override
    protected Box focusBox() {
        layout();
        if (cursorInView()) {
            // The ring grows by 2 on each side: this lays it on the card's own edge.
            Box card = cardBox(cursorCol, cursorRow);
            return new Box("card", card.x() + 2, card.y() + 2, Math.max(0, card.w() - 4), Math.max(0, card.h() - 4));
        }
        return super.focusBox();
    }

    @Override
    protected int focusCorner() {
        return cursorInView() ? 0 : super.focusCorner();
    }

    /** No ring while a card floats: the card it marked has left its place. */
    @Override
    protected boolean drawFocus(Draw d, Theme theme) {
        return dragged != null || super.drawFocus(d, theme);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!accepts() || button != 0 || !isOver(mx, my)) {
            return false;
        }
        layout();
        if (dragged != null || armed != null) {
            return true;
        }
        if (overBoardBar(mx, my)) {
            int thumb = boardThumbX();
            boardBarGrab = mx >= thumb && mx < thumb + boardThumbW() ? (int) mx - thumb : boardThumbW() / 2;
            boardBar = true;
            dragBoardBar(mx);
            return true;
        }
        int column = columnAt(mx);
        if (column < 0) {
            return true;
        }
        if (overColumnBar(column, mx, my)) {
            barOf(column).press(mx, my, barX(column), viewY(), viewH());
            barCol = column;
            return true;
        }
        int row = rowAt(column, mx, my);
        if (row < 0) {
            return true;
        }
        T item = columns.get(column).items.get(row);
        BaseComponent.click();
        cursorCol = column;
        cursorRow = row;
        reveal(column, row);
        choose(item);
        armed = item;
        armCol = column;
        armRow = row;
        armX = (int) mx;
        armY = (int) my;
        return true;
    }

    private void choose(T item) {
        boolean changed = item != selected;
        selected = item;
        if (changed && onSelect != null) {
            onSelect.accept(item);
        }
    }

    private void dragBoardBar(double mx) {
        int room = w - boardThumbW();
        if (room > 0) {
            boardScrollExact = (mx - boardBarGrab - x) / room * (boardW() - w);
            boardScrollExact = Math.max(0D, Math.min(boardW() - w, boardScrollExact));
            boardScroll = (int) boardScrollExact;
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (barCol >= 0) {
            barOf(barCol).drag(my);
            return true;
        }
        if (boardBar) {
            dragBoardBar(mx);
            return true;
        }
        if (armed != null && accepts() && (Math.abs((int) my - armY) >= DRAG_SLOP || Math.abs((int) mx - armX) >= DRAG_SLOP)) {
            dragX = (int) mx;
            dragY = (int) my;
            lift();
        }
        if (dragged != null) {
            dragX = (int) mx;
            dragY = (int) my;
            aim();
            return true;
        }
        return armed != null;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) {
            return dragged != null;
        }
        boolean was = barCol >= 0 || boardBar || armed != null || dragged != null;
        releaseColumnBar();
        boardBar = false;
        if (dragged != null) {
            endDrag(visible && active);
            return true;
        }
        if (armed != null) {
            T item = armed;
            armed = null;
            long now = BaseComponent.clock.getAsLong();
            boolean twice = lastClicked == item && now - lastClickAt <= DOUBLE_CLICK_MS;
            lastClicked = twice ? null : item;
            lastClickAt = now;
            if (twice && onActivate != null) {
                onActivate.accept(item);
            }
        }
        return was;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || !settled() || !isOver(mx, my)) {
            return dragged != null;
        }
        layout();
        int column = columnAt(mx);
        if (column >= 0 && !overHeader(my) && !overBoardBar(mx, my) && barOf(column).max() > 0) {
            columns.get(column).bar.wheel(amount, step());
            if (dragged != null) {
                aim();
            }
            return true;
        }
        if (boardOverflows()) {
            boardScrollExact = Math.max(0D, Math.min(boardW() - w, boardScrollExact - amount * (columnW + COLUMN_GAP) / 2D));
            boardScroll = (int) boardScrollExact;
            if (dragged != null) {
                aim();
            }
            return true;
        }
        return dragged != null;
    }

    private void moveCursor(int column, int row) {
        Column<T> c = columns.get(column);
        if (c.items.isEmpty()) {
            return;
        }
        cursorCol = column;
        cursorRow = Math.max(0, Math.min(c.items.size() - 1, row));
        reveal(cursorCol, cursorRow);
        choose(c.items.get(cursorRow));
    }

    /** The next column that way with cards in it, or -1. */
    private int nextFilled(int from, int by) {
        for (int i = from + by; i >= 0 && i < columns.size(); i += by) {
            if (!columns.get(i).items.isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private void startCursor() {
        for (int i = 0; i < columns.size(); i++) {
            if (!columns.get(i).items.isEmpty()) {
                moveCursor(i, 0);
                return;
            }
        }
    }

    /** A key moves the card: to another place of its column, or to the next column at the same height. */
    private void keyMove(int toCol, int toRow) {
        if (toCol < 0 || toCol >= columns.size()) {
            return;
        }
        Column<T> from = columns.get(cursorCol);
        T item = from.items.get(cursorRow);
        if (!accepts(item, cursorCol, toCol)) {
            refusedCol = toCol;
            refusedAt = BaseComponent.clock.getAsLong();
            return;
        }
        int most = toCol == cursorCol ? from.items.size() - 1 : columns.get(toCol).items.size();
        commitMove(item, cursorCol, cursorRow, toCol, Math.max(0, Math.min(most, toRow)));
    }

    @Override
    protected boolean activate() {
        if (!accepts() || cursorCol < 0 || cursorRow < 0 || cursorRow >= columns.get(cursorCol).items.size()) {
            return false;
        }
        BaseComponent.click();
        T item = columns.get(cursorCol).items.get(cursorRow);
        choose(item);
        if (onActivate != null) {
            onActivate.accept(item);
        }
        return true;
    }

    /** Takes Escape during a drag, before the panel would take the focus away. */
    @Override
    protected boolean capturesKeys() {
        return dragged != null || armed != null;
    }

    @Override
    public boolean keyCaptured(KeyPress press) {
        if (press.kind() == KeyPress.Kind.KEY && press.code() == RAW_ESCAPE && (dragged != null || armed != null)) {
            cancelDrag();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (dragged != null || armed != null) {
            if (key == InputKey.ESCAPE) {
                cancelDrag();
            }
            return true;
        }
        if (!accepts() || columns.isEmpty()) {
            return false;
        }
        layout();
        if (key == InputKey.ENTER || key == InputKey.SPACE) {
            return activate();
        }
        if (key == InputKey.ESCAPE) {
            if (cursorCol < 0) {
                return false;
            }
            cursorCol = -1;
            cursorRow = -1;
            return true;
        }
        boolean walks = key == InputKey.UP || key == InputKey.DOWN || key == InputKey.LEFT || key == InputKey.RIGHT
                || key == InputKey.HOME || key == InputKey.END;
        if (!walks) {
            return false;
        }
        if (cursorCol < 0 || cursorRow < 0) {
            startCursor();
            return cursorCol >= 0;
        }
        boolean ctrl = BaseComponent.keys != null && BaseComponent.keys.ctrl();
        int size = columns.get(cursorCol).items.size();
        if (ctrl) {
            switch (key) {
                case UP -> keyMove(cursorCol, cursorRow - 1);
                case DOWN -> keyMove(cursorCol, cursorRow + 1);
                case LEFT -> keyMove(cursorCol - 1, cursorRow);
                case RIGHT -> keyMove(cursorCol + 1, cursorRow);
                case HOME -> keyMove(cursorCol, 0);
                default -> keyMove(cursorCol, size - 1);
            }
            return true;
        }
        switch (key) {
            case UP -> moveCursor(cursorCol, cursorRow - 1);
            case DOWN -> moveCursor(cursorCol, cursorRow + 1);
            case LEFT -> {
                int to = nextFilled(cursorCol, -1);
                if (to >= 0) {
                    moveCursor(to, cursorRow);
                }
            }
            case RIGHT -> {
                int to = nextFilled(cursorCol, 1);
                if (to >= 0) {
                    moveCursor(to, cursorRow);
                }
            }
            case HOME -> moveCursor(cursorCol, 0);
            default -> moveCursor(cursorCol, size - 1);
        }
        return true;
    }
}
