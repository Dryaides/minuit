package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Events down a line, each with a marker: a {@link ListComponent} whose rows draw the line, so the scrolling, the
 * keys, the selection and the bar are the library's own.
 *
 * <pre>{@code
 * TimelineComponent<Event> log = add(new TimelineComponent<>(at(new Box("log", 10, 10, 200, 120)), Event::title, this::open, 1)
 *         .time(Event::when)
 *         .body(Event::text)
 *         .marker(e -> e.failed() ? TimelineComponent.Marker.BAD : TimelineComponent.Marker.PRIMARY));
 * log.setItems(events);
 * }</pre>
 *
 * <p>The library's list has no keyboard stop of its own, so this adds one: with focus, up and down walk the events,
 * home and end jump, Enter or Space calls the click's callback, Escape clears the selection.
 *
 * <p>Only the rows in view are drawn and only they ask for their words, so a hundred thousand events cost what a
 * screenful costs, and the first look at an event is the only time its words are measured and cut. All rows are
 * one height, which is what keeps it lazy: the constructor's {@code bodyLines} sets the lines of body they have room
 * for, since a list's row height cannot change, and words past them end in an ellipsis, whole in the tooltip.
 *
 * <p>The row order is yours: put the newest first if that is how it should read. After changing an item's words,
 * call {@link #refreshWords()}.
 */
public class TimelineComponent<T> extends ListComponent<T> {
    /** A marker's colour. */
    public enum Marker { PRIMARY, GOOD, WARN, BAD, MUTED }

    private static final int TITLE_H = 11;
    /** The list's face leaves one pixel at the top and bottom of a 15 high row: this centres the line of text in it. */
    private static final int TEXT_TOP = 3;
    private static final int LINE_H = 10;
    private static final int ROW_PAD = 4;
    private static final int TEXT_LEFT = 15;
    private static final int LINE_X = 7;
    private static final int DOT = 7;
    private static final int TIME_GAP = 6;
    private static final int RIGHT_PAD = 3;
    private static final int CACHE_MAX = 512;
    private static final int SELECTED_EDGE = 2;
    private static final int MIN_W = 24;
    private static final int ITEM_EXTRA = 3;
    private static final int ITEM_LINE_X = 10;
    private static final int ITEM_TEXT_LEFT = 21;

    private static final class Words {
        String title = "";
        String time = "";
        String[] lines = new String[0];
        boolean cut;
        List<String> tip;
    }

    /** The list needs its renderer before this object exists. */
    private static final class Late<T> {
        TimelineComponent<T> owner;
    }

    private final Function<T, String> title;
    private final Consumer<T> onClick;
    private final RowLook.HoverFade hoverInk = new RowLook.HoverFade();
    private final int bodyLines;
    private final List<BaseComponent> ownStop = List.of(this);
    private String emptyText;
    private Palette inksFor;
    private boolean inksActive;
    private int restInk;
    private int restSoft;
    private int overRow = -1;
    private final Map<T, Words> cache = new IdentityHashMap<>();
    private final List<T> rows = new ArrayList<>();
    private Function<T, String> time;
    private Function<T, String> body;
    private Function<T, Marker> marker;
    private Function<T, Integer> markerColor;
    private Function<T, Icon> icon;
    private Function<T, IconArt> iconArt;
    /** Rows made for 16 px item markers: 3 taller, the marker column wider. */
    private final boolean itemRows;
    private T tipItem;
    private boolean tipDrawn;
    private int lastMx = Integer.MIN_VALUE;
    private int lastMy = Integer.MIN_VALUE;
    private int cachedW = -1;
    private Theme lastTheme;

    public TimelineComponent(Box box, Function<T, String> title, Consumer<T> onClick) {
        this(box, title, onClick, 0, new Late<>());
    }

    /** @param bodyLines the lines of {@link #body(Function)} each row has room for */
    public TimelineComponent(Box box, Function<T, String> title, Consumer<T> onClick, int bodyLines) {
        this(box, title, onClick, bodyLines, new Late<>());
    }

    /**
     * @param bodyLines the lines of {@link #body(Function)} each row has room for
     * @param itemRows true for markers that are Minecraft items ({@link #iconArt(Function)}): a title band of 18,
     *                 so a 16 px item is drawn whole, and a wider marker column
     */
    public TimelineComponent(Box box, Function<T, String> title, Consumer<T> onClick, int bodyLines, boolean itemRows) {
        this(box, title, onClick, bodyLines, itemRows, new Late<>());
    }

    private TimelineComponent(Box box, Function<T, String> title, Consumer<T> onClick, int bodyLines, Late<T> late) {
        this(box, title, onClick, bodyLines, false, late);
    }

    private TimelineComponent(Box box, Function<T, String> title, Consumer<T> onClick, int bodyLines, boolean itemRows, Late<T> late) {
        super(box, rowHeightFor(bodyLines, itemRows), (d, theme, item, row, hovered, selected) -> late.owner.drawRow(d, theme, item, row, hovered, selected),
                (Consumer<T>) item -> {
                    if (onClick != null) {
                        onClick.accept(item);
                    }
                });
        this.title = title == null ? String::valueOf : title;
        this.onClick = onClick;
        this.bodyLines = Math.max(0, bodyLines);
        this.itemRows = itemRows;
        late.owner = this;
    }

    // ---- setup ----

    /** The short text at a row's right end, a date or a time. */
    public TimelineComponent<T> time(Function<T, String> time) {
        this.time = time;
        refreshWords();
        return this;
    }

    /** Words under the title, in the lines the constructor made room for. */
    public TimelineComponent<T> body(Function<T, String> body) {
        this.body = body;
        refreshWords();
        return this;
    }

    /** A marker's colour for each event. {@link Marker#PRIMARY} by default. */
    public TimelineComponent<T> marker(Function<T, Marker> marker) {
        this.marker = marker;
        return this;
    }

    /** A colour of your own for each event, in place of {@link #marker}; null falls back on it. */
    public TimelineComponent<T> markerColor(Function<T, Integer> markerColor) {
        this.markerColor = markerColor;
        return this;
    }

    /** A theme icon in place of the dot; null for an item keeps the dot. */
    public TimelineComponent<T> icon(Function<T, Icon> icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Art of your own in place of the dot, such as a Minecraft item ({@link ItemView}); null for an event keeps the
     * theme icon or the dot. An item is whole in rows made with {@code itemRows}, else shrunk crisply (n/gs). Under
     * the pointer an item marker shows the game's tooltip, or its name where the game cannot.
     */
    public TimelineComponent<T> iconArt(Function<T, IconArt> iconArt) {
        this.iconArt = iconArt;
        return this;
    }

    public TimelineComponent<T> active(boolean active) {
        this.active = active;
        return this;
    }

    /** What shows with no events. */
    public TimelineComponent<T> emptyText(String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    /** The height a row needs with this many lines of body. Use it when you size the box in whole rows. */
    public static int rowHeightFor(int lines) {
        return TITLE_H + Math.max(0, lines) * LINE_H + ROW_PAD;
    }

    /** The height a row needs; with {@code itemRows}, 3 more, so that the title band is 18 for a 16 px item. */
    public static int rowHeightFor(int lines, boolean itemRows) {
        return rowHeightFor(lines) + (itemRows ? ITEM_EXTRA : 0);
    }

    /** The title's line from the row's top: centred in its band as a Table, Sidebar or Menubar row's text is. */
    private int textTop() {
        return itemRows ? RowLook.textY(0, ItemArt.ITEM_ROW) : TEXT_TOP;
    }

    private int titleBand() {
        return itemRows ? ItemArt.ITEM_ROW : TITLE_H + ROW_PAD;
    }

    private int lineX() {
        return itemRows ? ITEM_LINE_X : LINE_X;
    }

    private int textLeft() {
        return itemRows ? ITEM_TEXT_LEFT : TEXT_LEFT;
    }

    // ---- content ----

    @Override
    public void setItems(List<T> items) {
        rows.clear();
        if (items != null) {
            rows.addAll(items);
        }
        cache.clear();
        super.setItems(rows);
    }

    /** Forgets the cut words, after an event's words changed. */
    public void refreshWords() {
        cache.clear();
    }

    // ---- words ----

    private Words wordsOf(T item, int width) {
        if (width != cachedW) {
            cachedW = width;
            cache.clear();
        }
        Words words = cache.get(item);
        if (words != null) {
            return words;
        }
        if (cache.size() >= CACHE_MAX) {
            cache.clear();
        }
        words = new Words();
        Draw measure = TextMeasure.draw();
        String timeText = time == null ? "" : safe(time.apply(item));
        int timeW = timeText.isEmpty() ? 0 : TextMeasure.width(timeText) + TIME_GAP;
        int room = Math.max(0, width - textLeft() - RIGHT_PAD);
        String whole = safe(title.apply(item));
        words.time = timeW > room / 2 ? "" : timeText;
        words.title = measure.ellipsize(whole, room - (words.time.isEmpty() ? 0 : timeW));
        String bodyText = body == null || bodyLines <= 0 ? "" : safe(body.apply(item));
        boolean cut = !words.title.equals(whole) || words.time.isEmpty() != timeText.isEmpty();
        if (!bodyText.isEmpty() && room > 0) {
            List<String> all = TextMeasure.wrapAll(bodyText, room);
            int count = Math.min(bodyLines, all.size());
            words.lines = new String[count];
            for (int i = 0; i < count; i++) {
                boolean last = i == count - 1 && count < all.size();
                words.lines[i] = last ? measure.ellipsize(all.get(i) + Draw.ELLIPSIS, room) : all.get(i);
            }
            cut |= count < all.size();
        }
        words.cut = cut;
        if (cut) {
            List<String> tip = new ArrayList<>(3);
            tip.add(whole);
            if (!timeText.isEmpty()) {
                tip.add(timeText);
            }
            if (!bodyText.isEmpty()) {
                tip.add(bodyText);
            }
            words.tip = List.copyOf(tip);
        }
        cache.put(item, words);
        return words;
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        lastTheme = t;
        Palette p = t.palette();
        if (inksFor != p || inksActive != active) {
            inksFor = p;
            inksActive = active;
            restInk = RowLook.ink(p, 0, false, active);
            restSoft = active ? RowLook.softInk(p, restInk) : restInk;
        }
        tipItem = null;
        tipDrawn = false;
        lastMx = mx;
        lastMy = my;
        if (w < MIN_W || h < rowHeightFor(bodyLines, itemRows) + 2) {
            // The list's bar has a least height and would stick out of a box this small.
            t.frame().well(d, p, x, y, w, h, focused && active);
            return;
        }
        // From the last frame's row: asking the list for the row under the pointer again would allocate.
        hoverInk.step(overRow);
        overRow = -1;
        super.render(d, t, mx, my, hovered);
        if (tipItem != null && iconArt != null) {
            // The game's own tooltip for the item marker under the pointer; tooltipAt names it where that fails.
            tipDrawn = ItemArt.hoverTooltip(d, iconArt.apply(tipItem), mx, my);
        }
        if (rows.isEmpty()) {
            RowLook.drawEmpty(d, p, emptyText, active, x + 1, y + 1, w - 2, h - 2);
        }
    }

    /** A marker moved towards the ink until it reads on what is under it. */
    private int markerInk(Palette p, T item, int under, int ink) {
        Integer own = markerColor == null || item == null ? null : markerColor.apply(item);
        int color;
        if (own != null) {
            color = DataLook.opaque(own);
        } else {
            Marker kind = marker == null || item == null ? Marker.PRIMARY : marker.apply(item);
            if (kind == null) {
                kind = Marker.PRIMARY;
            }
            color = switch (kind) {
                case GOOD -> p.good();
                case WARN -> p.warn();
                case BAD -> p.bad();
                case MUTED -> p.textSoft();
                default -> p.primary();
            };
        }
        int solid = DataLook.opaque(color);
        return Shapes.contrast(solid, under) >= 3D ? solid : Shapes.mix(solid, DataLook.opaque(ink), 0.5F);
    }

    private void drawRow(Draw d, Theme t, T item, Box row, boolean hovered, boolean selected) {
        Palette p = t.palette();
        // The list paints a hovered or selected row in the raised colour, so its words take the raised ink. The
        // hover fades in: the ink waits till the face is past half way, or the words would vanish on the bare well.
        if (hovered) {
            overRow = System.identityHashCode(item);
        }
        boolean lit = active && (selected || (hovered && hoverInk.fade() >= 0.5F));
        int ink = !active ? p.disabledText() : lit ? p.raisedText() : restInk;
        int soft = lit ? ink : restSoft;
        if (selected) {
            // The list's selected face is close to the well in some themes (vanilla dark): this edge says it in all.
            int edge = active ? markerInk(p, null, DataLook.opaque(p.raised()), ink) : p.disabledText();
            d.fill(row.x(), row.y() + 1, row.x() + SELECTED_EDGE, row.y() + row.h() - 1, edge);
        }
        int lineX = row.x() + lineX();
        int textY = row.y() + textTop();
        IconArt art = markerArt(t, item);
        // The marker is centred on the title's line (8 high), which is centred in the hover and selection band.
        int markH = art == null ? DOT : Math.round(art.height() * ItemArt.scale(d, art.height(), titleBand() - 2));
        int dotTop = textY + (RowLook.FONT_H - markH) / 2;
        int dotBottom = dotTop + markH;
        boolean first = !rows.isEmpty() && rows.get(0) == item;
        boolean last = !rows.isEmpty() && rows.get(rows.size() - 1) == item;
        int rule = Shapes.alpha(DataLook.opaque(soft), lit ? 0x90 : 0x60);
        if (!first) {
            d.fill(lineX, row.y(), lineX + 1, dotTop - 1, rule);
        }
        if (!last) {
            d.fill(lineX, dotBottom + 1, lineX + 1, row.y() + row.h(), rule);
        }
        drawDot(d, t, item, row, art, dotTop, lit, ink);
        Words words = wordsOf(item, row.w());
        int textX = row.x() + textLeft();
        d.text(words.title, textX, textY, ink);
        if (!words.time.isEmpty()) {
            d.text(words.time, row.x() + row.w() - RIGHT_PAD - d.width(words.time), textY, soft);
        }
        for (int i = 0; i < words.lines.length; i++) {
            d.text(words.lines[i], textX, textY + TITLE_H + (itemRows ? ITEM_EXTRA - 2 : 0) + i * LINE_H, soft);
        }
    }

    /** The art in place of the dot: yours, else a theme icon, else null for the dot. */
    private IconArt markerArt(Theme t, T item) {
        IconArt own = iconArt == null ? null : iconArt.apply(item);
        if (own != null) {
            return own;
        }
        Icon named = icon == null ? null : icon.apply(item);
        return named == null ? null : t.icon(named);
    }

    private void drawDot(Draw d, Theme t, T item, Box row, IconArt art, int top, boolean lit, int ink) {
        Palette p = t.palette();
        int left = row.x() + lineX() - DOT / 2;
        int color = !active ? p.disabledText() : markerInk(p, item, DataLook.opaque(lit ? p.raised() : p.well()), ink);
        if (art != null) {
            int room = titleBand() - 2;
            int artW = ItemArt.rowWidth(art, room);
            int ax = row.x() + lineX() - artW / 2;
            // inRow centres the art in a band: a band as tall as the art drawn puts it at top.
            int markH = Math.round(art.height() * ItemArt.scale(d, art.height(), room));
            ItemArt.inRow(d, p, art, ax, top, markH, room, color, active);
            if (art instanceof ItemView && lastMx >= ax && lastMx < ax + artW && lastMy >= top && lastMy < top + markH) {
                tipItem = item;
            }
            return;
        }
        boolean hollow = marker != null && marker.apply(item) == Marker.MUTED && markerColor == null;
        t.frame().dot(d, p, left, top, DOT, hollow ? 0 : color, hollow ? color : 0);
    }

    // ---- tooltips ----

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!visible) {
            return null;
        }
        if (tipItem != null && iconArt != null && iconArt.apply(tipItem) instanceof ItemView view) {
            // The item marker under the pointer: the game drew its tooltip, or its name stands in (the bench).
            return tipDrawn ? null : ItemArt.nameTip(view);
        }
        if (active && cachedW >= 0) {
            T item = rowAt(mx, my);
            if (item != null) {
                Words words = cache.get(item);
                if (words != null && words.cut) {
                    return words.tip;
                }
            }
        }
        return super.tooltipAt(mx, my);
    }

    // ---- keys ----

    @Override
    protected boolean navigable() {
        return visible && active && settled() && !rows.isEmpty();
    }

    @Override
    protected boolean takesFocus() {
        return visible && active;
    }

    /** One stop for the whole list. */
    @Override
    public List<BaseComponent> stops() {
        return navigable() ? ownStop : List.of();
    }

    private int selectedIndex() {
        if (selected == null) {
            return -1;
        }
        return rows.indexOf(selected);
    }

    private void walkTo(int index) {
        T item = rows.get(Math.max(0, Math.min(rows.size() - 1, index)));
        selected = item;
        reveal(item);
    }

    @Override
    protected boolean activate() {
        if (!navigable() || selected == null) {
            return false;
        }
        BaseComponent.click();
        if (onClick != null) {
            onClick.accept(selected);
        }
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!navigable()) {
            return false;
        }
        switch (key) {
            case UP -> walkTo(selected == null ? rows.size() - 1 : selectedIndex() - 1);
            case DOWN -> walkTo(selected == null ? 0 : selectedIndex() + 1);
            case HOME -> walkTo(0);
            case END -> walkTo(rows.size() - 1);
            case ENTER, SPACE -> {
                return activate();
            }
            case ESCAPE -> {
                if (selected == null) {
                    return false;
                }
                selected = null;
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
