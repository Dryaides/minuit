package dev.dryaides.minuit;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A field that shows a date and opens a {@link CalendarComponent} to pick it, for one day or a range.
 *
 * <pre>{@code
 * public class EventPanel extends PanelComponent {
 *     public EventPanel(Theme theme, Words words, int left, int top) {
 *         super(theme, words, left, top);
 *         DatePickerComponent when = add(new DatePickerComponent(at(new Box("when", 10, 10, 120, 16)), this)
 *                 .placeholder(words.get("mymod.pick_a_day"))
 *                 .format(day -> day.getDayOfMonth() + "/" + day.getMonthValue() + "/" + day.getYear())
 *                 .onChange(this::setDay));
 *         when.calendar().labels(words, "mymod.calendar").today(serverDay);
 *         // Last, so that the calendar is drawn over the rest and offered every input first.
 *         add(when.popup());
 *     }
 * }
 * }</pre>
 *
 * <h2>Keys</h2>
 * Enter, Space, down or up opens it, and the keys then act on the calendar: see {@link CalendarComponent}. A
 * pick closes it (the second one, for a range). Escape drops a started range, then closes, then lets go of
 * the focus. Backspace or Delete clears the field, while closed. There is no typing a date: it would need a
 * format per language, and the calendar's keys do the same work.
 *
 * <h2>Putting it in a panel</h2>
 * As for a {@link ComboBoxComponent}: add {@link #popup()} <b>after every other widget</b>. The popup stays
 * in {@link #area(Box)}, the panel by default: it opens below, above when more of it fits there, and shrinks if the
 * area is too small. While it is open, the first click outside only closes it. Only a direct child of a panel
 * has been tried.
 */
public class DatePickerComponent extends BaseComponent {
    private static final int TEXT_PAD = 4;
    private static final int BUTTON_W = 12;
    private static final int BUTTON_MARGIN = 1;
    private static final int BORDER = 1;
    private static final int WASH_INSET = 3;
    private static final int MIN_TEXT = 24;
    private static final int MARGIN = 2;
    private static final int RING_ROOM = 2;
    private static final int OPEN_MS = 110;
    private static final int HOVER_WASH = 0x28;
    private static final int HELD_WASH = 0x50;
    private static final int CLEAR = 0;
    private static final int OPEN = 1;
    private static final int NO_BUTTON = -1;
    private static final String[] ART_CALENDAR = {
            ".#...#.",
            "#######",
            "#.....#",
            "#.#.#.#",
            "#.....#",
            "#.#.#.#",
            "#######"};
    private static final String[] ART_CLEAR = {
            "#...#",
            ".#.#.",
            "..#..",
            ".#.#.",
            "#...#"};
    private static final int CALENDAR_ART = 7;
    private static final int CLEAR_ART = 5;

    private final PanelComponent host;
    private final CalendarComponent calendar;
    private final Popup popup = new Popup();
    private final RowLook.HoverFade hover = new RowLook.HoverFade();
    private final Glide glide = new Glide(Slide.DOWN, OPEN_MS);

    private String placeholder = "";
    private String separator = " - ";
    private Function<LocalDate, String> format = LocalDate::toString;
    private boolean clearable = true;
    private boolean layered;
    private boolean ranged;
    private Box area;
    private Consumer<LocalDate> onChange;
    private BiConsumer<LocalDate, LocalDate> onRange;

    private LocalDate value;
    private LocalDate from;
    private LocalDate to;
    private String shown = "";
    private boolean open;
    private boolean above;
    private boolean hadFocus;
    private int heldButton = NO_BUTTON;

    /**
     * @param box the field
     * @param host the panel this picker is added to
     */
    public DatePickerComponent(Box box, PanelComponent host) {
        super(box);
        this.host = Objects.requireNonNull(host, "host");
        this.calendar = new CalendarComponent(new Box("calendar", 0, 0, CalendarComponent.WIDTH, CalendarComponent.HEIGHT), this::picked);
        calendar.onRange(this::pickedRange);
    }

    @Override
    void words(Words words) {
        calendar.words(words);
    }

    /** The calendar inside, for its week start, names, disabled days, limits and today. */
    public CalendarComponent calendar() {
        return calendar;
    }

    public DatePickerComponent placeholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        return this;
    }

    /** How a date reads in the field. ISO, 2026-11-06, by default: pass your own for a player's language. */
    public DatePickerComponent format(Function<LocalDate, String> format) {
        this.format = format == null ? LocalDate::toString : format;
        refreshText();
        return this;
    }

    /** True to pick a range. Off by default. */
    public DatePickerComponent range(boolean range) {
        ranged = range;
        calendar.range(range);
        return this;
    }

    /** What stands between a range's two dates. */
    public DatePickerComponent separator(String separator) {
        this.separator = separator == null ? " - " : separator;
        refreshText();
        return this;
    }

    /** Whether a filled field shows a button that empties it. On by default. */
    public DatePickerComponent clearable(boolean clearable) {
        this.clearable = clearable;
        return this;
    }

    /** The box the popup stays in, in the panel's coordinates. The panel's bounds by default. */
    public DatePickerComponent area(Box area) {
        this.area = area;
        return this;
    }

    /** Called with the day when the player picks or clears it (with null). Not through {@link #setValue}. */
    public DatePickerComponent onChange(Consumer<LocalDate> onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Called with both ends when the player finishes a range, or with nulls when they clear it. */
    public DatePickerComponent onRange(BiConsumer<LocalDate, LocalDate> onRange) {
        this.onRange = onRange;
        return this;
    }

    public DatePickerComponent active(boolean active) {
        this.active = active;
        sync();
        return this;
    }

    @Override
    public DatePickerComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** The calendar as a widget of its own: add it after every other widget of the panel. */
    public BaseComponent popup() {
        layered = true;
        return popup;
    }

    public LocalDate value() {
        return value;
    }

    /** Sets the day and shows it, without calling back. Null empties the field. */
    public void setValue(LocalDate day) {
        value = day;
        calendar.setValue(day);
        refreshText();
    }

    public LocalDate rangeStart() {
        return from;
    }

    public LocalDate rangeEnd() {
        return to;
    }

    /** Sets the range and shows it, without calling back. Nulls empty the field. */
    public void setRange(LocalDate start, LocalDate end) {
        calendar.setRange(start, end);
        from = calendar.rangeStart();
        to = calendar.rangeEnd();
        refreshText();
    }

    /** What the field reads, or an empty text. */
    public String text() {
        return shown;
    }

    private boolean filled() {
        return ranged ? from != null : value != null;
    }

    private void refreshText() {
        if (ranged) {
            shown = from == null ? "" : format.apply(from) + separator + format.apply(to);
        } else {
            shown = value == null ? "" : format.apply(value);
        }
    }

    private void picked(LocalDate day) {
        value = day;
        refreshText();
        close();
        if (onChange != null) {
            onChange.accept(day);
        }
    }

    private void pickedRange(LocalDate start, LocalDate end) {
        from = start;
        to = end;
        refreshText();
        close();
        if (onRange != null) {
            onRange.accept(start, end);
        }
    }

    private void clear() {
        BaseComponent.click();
        if (ranged) {
            setRange(null, null);
            if (onRange != null) {
                onRange.accept(null, null);
            }
        } else {
            setValue(null);
            if (onChange != null) {
                onChange.accept(null);
            }
        }
    }

    /** Opens the calendar and takes the panel's focus. False when the picker is hidden or disabled. */
    public boolean open() {
        if (!visible || !active) {
            return false;
        }
        if (host.focus() != this) {
            host.focus(this);
        }
        hadFocus = true;
        openNow();
        return true;
    }

    /** Closes the calendar. A range started and not finished is dropped. */
    public void close() {
        open = false;
        popup.visible = false;
        calendar.cancelRange();
    }

    public boolean isOpen() {
        return open;
    }

    /** True when it opened above the field, or last did. */
    boolean isAbove() {
        return above;
    }

    Box popupBox() {
        place();
        return popup.box();
    }

    private void openNow() {
        if (open) {
            return;
        }
        open = true;
        popup.visible = true;
        above = false;
        place();
        if (reducedMotion) {
            glide.snap(true);
        } else {
            glide.replay();
        }
    }

    private Box areaBox() {
        return area != null ? area : host.bounds();
    }

    private Insets inset() {
        return PopupList.inset(host.theme().frame());
    }

    /** Below, or above when that holds more of it; never out of the area, where it gives up some height. */
    private void place() {
        Box a = areaBox();
        int top = a.y() + MARGIN;
        int bottom = a.bottom() - MARGIN;
        Insets in = inset();
        int wantH = CalendarComponent.HEIGHT + in.top() + in.bottom();
        int wantW = CalendarComponent.WIDTH + in.left() + in.right();
        int roomBelow = bottom - (y + h + RING_ROOM);
        int roomAbove = y - RING_ROOM - top;
        int here = above ? roomAbove : roomBelow;
        int there = above ? roomBelow : roomAbove;
        if (here < wantH && there > here) {
            above = !above;
            here = there;
        }
        popup.h = Math.min(wantH, Math.max(here, Math.min(wantH, bottom - top)));
        popup.y = above ? y - RING_ROOM - popup.h : y + h + RING_ROOM;
        popup.y = Math.max(top, Math.min(popup.y, bottom - popup.h));
        popup.w = Math.min(Math.max(1, a.w() - 2 * MARGIN), wantW);
        popup.x = Math.max(a.x() + MARGIN, Math.min(x, a.right() - MARGIN - popup.w));
        calendar.x = popup.x + in.left();
        calendar.y = popup.y + in.top();
        calendar.w = Math.max(0, popup.w - in.left() - in.right());
        calendar.h = Math.max(0, popup.h - in.top() - in.bottom());
    }

    /** Hidden or disabled closes the popup; so does the focus going elsewhere. Returns whether it is open. */
    private boolean sync() {
        if (open && (!visible || !active)) {
            close();
        }
        popup.visible = open;
        boolean live = focused && active && visible;
        if (hadFocus && !live) {
            hadFocus = false;
            close();
        } else if (live) {
            hadFocus = true;
        }
        return open;
    }

    private boolean inField(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean showsOpen() {
        return w - BUTTON_W - 2 * BUTTON_MARGIN - BORDER - TEXT_PAD >= MIN_TEXT;
    }

    private boolean showsClear() {
        return clearable && active && filled() && showsOpen() && w - 2 * BUTTON_W - 2 * BUTTON_MARGIN - BORDER - TEXT_PAD >= MIN_TEXT;
    }

    private int zone() {
        return !showsOpen() ? 0 : (showsClear() ? 2 : 1) * BUTTON_W + 2 * BUTTON_MARGIN + BORDER;
    }

    private int buttonX(int button) {
        int right = x + w - BORDER - BUTTON_MARGIN - BUTTON_W;
        return button == OPEN ? right : right - BUTTON_W;
    }

    private int buttonAt(double mx, double my) {
        if (!inField(mx, my) || !showsOpen()) {
            return NO_BUTTON;
        }
        if (mx >= buttonX(OPEN)) {
            return OPEN;
        }
        return showsClear() && mx >= buttonX(CLEAR) ? CLEAR : NO_BUTTON;
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        sync();
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        frame.field(d, p, x, y, w, h, focused && active);
        int zone = zone();
        int room = Math.max(0, zone > 0 ? w - zone - TEXT_PAD : w - 2 * TEXT_PAD);
        if (room > 0 && h >= FormLook.FONT_H) {
            boolean hint = shown.isEmpty();
            int color = !active ? FormLook.fieldDisabled(p, frame) : hint ? frame.fieldHint(p) : frame.fieldText(p);
            d.scissor(x, y, x + w, y + h);
            d.textFit(hint ? placeholder : shown, x + TEXT_PAD, y + (h - FormLook.FONT_H) / 2, room, color);
            d.endScissor();
        }
        int over = active && inField(mx, my) && !coveredBy(mx, my) ? buttonAt(mx, my) : NO_BUTTON;
        hover.step(over);
        if (showsOpen()) {
            drawButton(d, p, frame, OPEN, ART_CALENDAR, CALENDAR_ART, over);
            if (showsClear()) {
                drawButton(d, p, frame, CLEAR, ART_CLEAR, CLEAR_ART, over);
            }
        }
        if (open && !layered) {
            popup.draw(d, t, mx, my, delta);
        }
    }

    private boolean coveredBy(int mx, int my) {
        return open && popup.box().contains(mx, my);
    }

    private void drawButton(Draw d, Palette p, FrameStyle frame, int button, String[] art, int size, int over) {
        int bx = buttonX(button);
        boolean isOver = button == over;
        int ink = frame.fieldText(p);
        if (isOver && h > 2 * WASH_INSET) {
            int alpha = heldButton == button ? HELD_WASH : Math.round(HOVER_WASH * hover.fade());
            d.fill(bx, y + WASH_INSET, bx + BUTTON_W, y + h - WASH_INSET, Shapes.alpha(ink, alpha));
        }
        int color = !active ? FormLook.fieldDisabled(p, frame) : isOver || (button == OPEN && open) ? ink : frame.fieldHint(p);
        FormLook.pixels(d, art, bx + (BUTTON_W - size) / 2, y + (h - size) / 2, color);
    }

    private void drawPopup(Draw d, Theme t, int mx, int my) {
        place();
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        d.nextLayer(); // the text beneath may sort above a rectangle otherwise
        boolean moving = !reducedMotion && glide.moving();
        int px = popup.x;
        int py = popup.y;
        int pw = popup.w;
        int ph = popup.h;
        if (moving) {
            int showing = Math.max(1, Math.round(ph * Math.max(0F, Math.min(1F, glide.progress()))));
            int clipTop = above ? py + ph - showing : py;
            d.scissor(px - MARGIN, clipTop, px + pw + MARGIN, clipTop + showing);
        }
        frame.popup(d, p, px, py, pw, ph);
        calendar.focused = focused;
        calendar.draw(d, t, moving ? -1 : mx, moving ? -1 : my, delta);
        if (moving) {
            d.endScissor();
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (sync() && !layered) {
            return popup.tooltipAt(mx, my);
        }
        return fieldTooltip(mx, my);
    }

    private List<String> fieldTooltip(double mx, double my) {
        if (!inField(mx, my)) {
            return null;
        }
        if (tooltip != null) {
            return tooltip;
        }
        int room = zone() > 0 ? w - zone() - TEXT_PAD : w - 2 * TEXT_PAD;
        return !shown.isEmpty() && TextMeasure.width(shown) > room ? List.of(shown) : null;
    }

    @Override
    public boolean isOver(double mx, double my) {
        return inField(mx, my) && visible && settled() || (open && !layered);
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
            return popup.mouseClicked(mx, my, button);
        }
        if (!visible || !active || button != 0 || !inField(mx, my)) {
            return false;
        }
        return pressField(mx, my);
    }

    private boolean pressField(double mx, double my) {
        hadFocus = true;
        int hit = buttonAt(mx, my);
        heldButton = hit;
        if (hit == CLEAR) {
            clear();
            return true;
        }
        BaseComponent.click();
        if (open) {
            close();
        } else {
            openNow();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        heldButton = NO_BUTTON;
        calendar.mouseReleased(mx, my, button);
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return open && !layered && popup.mouseScrolled(mx, my, amount);
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        sync();
        if (!visible || !active) {
            return false;
        }
        if (open) {
            if (key == InputKey.ESCAPE) {
                if (!calendar.keyPressed(key, shift)) {
                    BaseComponent.click();
                    close();
                }
                return true;
            }
            if (key == InputKey.TAB) {
                close();
                return false;
            }
            return calendar.keyPressed(key, shift) || key != InputKey.OTHER;
        }
        switch (key) {
            case ENTER, SPACE, DOWN, UP -> {
                BaseComponent.click();
                calendar.keyedNow();
                openNow();
                return true;
            }
            case BACKSPACE, DELETE -> {
                if (clearable && filled()) {
                    clear();
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void refocus() {
        if (host.focus() != this) {
            host.focus(this);
        }
        hadFocus = true;
    }

    /** Draws the calendar and takes the clicks and wheel turns while it is open. Its box is the popup's. */
    private final class Popup extends BaseComponent {
        Popup() {
            super(0, 0, 0, 0);
            visible = false;
        }

        @Override
        protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
            if (sync()) {
                drawPopup(d, t, mx, my);
            }
        }

        /** While open, every click belongs to it: the first one outside only closes the calendar. */
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
            List<String> lines = calendar.tooltipAt(mx, my);
            return lines == null ? List.of() : lines; // empty, not null: a panel goes on to the widgets beneath on null
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            // No sync here: the panel took the focus away just before this, as this widget takes none.
            if (!open || !DatePickerComponent.this.visible || !DatePickerComponent.this.active) {
                return false;
            }
            place();
            if (inField(mx, my)) {
                refocus();
                return button != 0 || pressField(mx, my);
            }
            if (!box().contains(mx, my)) {
                BaseComponent.click();
                host.focus(null);
                sync();
                return true;
            }
            refocus();
            if (button == 0 && !glide.moving()) {
                calendar.mouseClicked(mx, my, button);
            }
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            calendar.mouseReleased(mx, my, button);
            return false;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double amount) {
            if (!sync()) {
                return false;
            }
            place();
            if (box().contains(mx, my)) {
                calendar.mouseScrolled(mx, my, amount);
            }
            return true;
        }
    }
}
