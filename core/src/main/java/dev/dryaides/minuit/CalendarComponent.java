package dev.dryaides.minuit;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A month as a grid of days, to pick one day or a range. Dates are {@link LocalDate}: a day on a wall calendar,
 * with no time and no zone, so nothing shifts across a time zone. "Today" is only what you say it is, through
 * {@link #today(LocalDate)}; by default it is the computer's local date when the calendar is made.
 *
 * <pre>{@code
 * add(new CalendarComponent(at(new Box("day", 10, 10, CalendarComponent.WIDTH, CalendarComponent.HEIGHT)), this::pick)
 *         .weekStart(DayOfWeek.MONDAY)
 *         .labels(words, "mymod.calendar")
 *         .disabled(day -> day.getDayOfWeek() == DayOfWeek.SUNDAY));
 * }</pre>
 *
 * <h2>Names</h2>
 * The panel's lang file ({@code minuit.month.1..12}, {@code minuit.day.1..7}) until you say otherwise: pass your
 * own with {@link #monthNames} and {@link #dayNames}, or read them from your own keys with
 * {@link #labels(Words, String)}.
 *
 * <h2>Keys</h2>
 * The grid is one keyboard stop, and the arrows walk the days, stepping over disabled ones. Enter or Space
 * picks. Home and End go to the week's ends. With Shift: left and right change the month, up and down the
 * year, Home and End go to the month's ends. Escape drops a range that was started, then lets go of the focus.
 * The wheel changes the month. The header's two arrows are mouse only, as the keys above do the same job.
 *
 * <h2>Ranges</h2>
 * With {@link #range(boolean)}, the first pick starts a range and the second ends it, in either order. Between
 * the two, the pointer or the keys show what it would cover. A range cannot cross a disabled day: a second pick
 * on the far side of one starts a new range there.
 *
 * <p>The size is free: the grid takes what its box gives, in whole pixels, and draws only its title below
 * about {@code 84 x 78}. Only a direct child of a panel, or a {@link DatePickerComponent}'s popup, has been tried.
 */
public class CalendarComponent extends BaseComponent {
    /** The width that gives every day an even cell of 18. */
    public static final int WIDTH = 7 * 18;
    /** The height that gives every week an even row of 12. */
    public static final int HEIGHT = 14 + 11 + 6 * 12;

    private static final int HEADER = 14;
    private static final int WEEKDAYS = 11;
    private static final int ROWS = 6;
    private static final int DAYS = 7 * ROWS;
    private static final int MIN_CELL_W = 12;
    private static final int MIN_CELL_H = 9;
    private static final int NAV_W = 14;
    private static final int SLIDE_MS = 120;
    private static final int SLIDE_BY = 24;
    private static final int BAND_ALPHA = 0x58;
    private static final int PREVIEW_ALPHA = 0x38;
    private static final float OUTSIDE_FADE = 0.55F;
    private static final long NONE = Long.MIN_VALUE;
    /** How far a key looks for a day that is not disabled. */
    private static final int MAX_WALK = 366;
    private static final int PREV = 0;
    private static final int NEXT = 1;
    private static final int NO_NAV = -1;
    private static final String[] NUMBERS = new String[32];

    static {
        for (int i = 0; i < NUMBERS.length; i++) {
            NUMBERS[i] = Integer.toString(i);
        }
    }

    private static final String[] ENGLISH_MONTHS = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};
    /** Monday first, the order of {@link DayOfWeek}. */
    private static final String[] ENGLISH_DAYS = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};

    private final Consumer<LocalDate> onSelect;
    private BiConsumer<LocalDate, LocalDate> onRange;
    private Runnable onMonth;

    @Override
    void words(Words words) {
        if (monthNames == ENGLISH_MONTHS) {
            monthNames = names(words, "minuit.month", ENGLISH_MONTHS);
        }
        if (dayNames == ENGLISH_DAYS) {
            dayNames = names(words, "minuit.day", ENGLISH_DAYS);
        }
        rebuild();
    }

    private String[] monthNames = ENGLISH_MONTHS;
    private String[] dayNames = ENGLISH_DAYS;
    private DayOfWeek weekStart = DayOfWeek.MONDAY;
    private boolean rangeMode;
    private boolean showOutside = true;
    private Predicate<LocalDate> disabled;
    private Function<LocalDate, List<String>> dayTooltip;
    private long min = NONE;
    private long max = NONE;
    private long today = Math.floorDiv(System.currentTimeMillis() - new java.util.Date().getTimezoneOffset() * 60000L, 86400000L);

    private int year;
    private int month;
    private long first;
    private long monthFirst;
    private long monthLast;
    private final int[] numbers = new int[DAYS];
    private final boolean[] off = new boolean[DAYS];
    private String title = "";
    private final String[] heads = new String[7];

    private long value = NONE;
    private long rangeFrom = NONE;
    private long rangeTo = NONE;
    private long anchor = NONE;
    private long cursor = NONE;
    private boolean keyed;
    private int heldNav = NO_NAV;

    private final RowLook.HoverFade cellHover = new RowLook.HoverFade();
    private final RowLook.HoverFade navHover = new RowLook.HoverFade();
    private final Glide slideLeft = new Glide(Slide.LEFT, SLIDE_MS);
    private final Glide slideRight = new Glide(Slide.RIGHT, SLIDE_MS);
    private Glide slide;

    /**
     * @param box the calendar; {@link #WIDTH} by {@link #HEIGHT} gives even cells
     * @param onSelect called with the day when the player picks one, in single mode; may be null
     */
    public CalendarComponent(Box box, Consumer<LocalDate> onSelect) {
        super(box);
        this.onSelect = onSelect;
        LocalDate now = LocalDate.ofEpochDay(today);
        year = now.getYear();
        month = now.getMonthValue();
        rebuild();
    }

    /** What the calendar marks as today, and the month it opens on while nothing is picked. */
    public CalendarComponent today(LocalDate today) {
        if (today != null) {
            this.today = today.toEpochDay();
            if (value == NONE && rangeFrom == NONE) {
                show(YearMonth.from(today));
            }
        }
        return this;
    }

    /** The first column. Monday by default. */
    public CalendarComponent weekStart(DayOfWeek weekStart) {
        if (weekStart != null) {
            this.weekStart = weekStart;
            rebuild();
        }
        return this;
    }

    /** Twelve month names, January first. Anything else keeps the ones in place. */
    public CalendarComponent monthNames(List<String> names) {
        if (names != null && names.size() == 12) {
            monthNames = names.toArray(new String[0]);
            rebuild();
        }
        return this;
    }

    /** Seven short day names, Monday first, whatever the week's start: they should fit a cell, two letters do. */
    public CalendarComponent dayNames(List<String> names) {
        if (names != null && names.size() == 7) {
            dayNames = names.toArray(new String[0]);
            rebuild();
        }
        return this;
    }

    /**
     * Reads the names from your lang file: {@code <prefix>.month.1} to {@code .month.12} and {@code <prefix>.day.1}
     * (Monday) to {@code .day.7}. A key missing from the file keeps the name in place.
     */
    public CalendarComponent labels(Words words, String prefix) {
        String[] months = monthNames.clone();
        String[] days = dayNames.clone();
        for (int i = 0; i < 12; i++) {
            String key = prefix + ".month." + (i + 1);
            String text = words.get(key);
            if (text != null && !text.equals(key)) {
                months[i] = text;
            }
        }
        for (int i = 0; i < 7; i++) {
            String key = prefix + ".day." + (i + 1);
            String text = words.get(key);
            if (text != null && !text.equals(key)) {
                days[i] = text;
            }
        }
        monthNames = months;
        dayNames = days;
        rebuild();
        return this;
    }

    /** True to pick a range. Off by default. */
    public CalendarComponent range(boolean range) {
        rangeMode = range;
        anchor = NONE;
        return this;
    }

    /** Called with both ends, first day first, when a range is complete. */
    public CalendarComponent onRange(BiConsumer<LocalDate, LocalDate> onRange) {
        this.onRange = onRange;
        return this;
    }

    /** Called when the month in view changes through the player. */
    public CalendarComponent onMonth(Runnable onMonth) {
        this.onMonth = onMonth;
        return this;
    }

    /** Which days cannot be picked. It runs when the month changes, not every frame. */
    public CalendarComponent disabled(Predicate<LocalDate> disabled) {
        this.disabled = disabled;
        rebuild();
        return this;
    }

    /** The first and last day that can be picked; null for no limit. The arrows stop at the limits' months. */
    public CalendarComponent limits(LocalDate first, LocalDate last) {
        min = first == null ? NONE : first.toEpochDay();
        max = last == null ? NONE : last.toEpochDay();
        rebuild();
        return this;
    }

    /** False to leave the cells of the months before and after blank. On by default. */
    public CalendarComponent showOutsideDays(boolean showOutside) {
        this.showOutside = showOutside;
        return this;
    }

    /** Lines for the day under the pointer, to say why it is disabled, say. */
    public CalendarComponent dayTooltip(Function<LocalDate, List<String>> dayTooltip) {
        this.dayTooltip = dayTooltip;
        return this;
    }

    public CalendarComponent active(boolean active) {
        this.active = active;
        if (!active) {
            anchor = NONE;
        }
        return this;
    }

    @Override
    public CalendarComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** The day picked, in single mode, or null. */
    public LocalDate value() {
        return value == NONE ? null : LocalDate.ofEpochDay(value);
    }

    /** Sets the day and shows its month, without calling back. Null for none. */
    public void setValue(LocalDate day) {
        value = day == null ? NONE : day.toEpochDay();
        if (day != null) {
            cursor = value;
            show(YearMonth.from(day));
        }
    }

    public LocalDate rangeStart() {
        return rangeFrom == NONE ? null : LocalDate.ofEpochDay(rangeFrom);
    }

    public LocalDate rangeEnd() {
        return rangeTo == NONE ? null : LocalDate.ofEpochDay(rangeTo);
    }

    /** Sets the range and shows its first month, without calling back. The ends can come in either order. Null for none. */
    public void setRange(LocalDate from, LocalDate to) {
        anchor = NONE;
        if (from == null || to == null) {
            rangeFrom = NONE;
            rangeTo = NONE;
            return;
        }
        rangeFrom = Math.min(from.toEpochDay(), to.toEpochDay());
        rangeTo = Math.max(from.toEpochDay(), to.toEpochDay());
        cursor = rangeFrom;
        show(YearMonth.from(LocalDate.ofEpochDay(rangeFrom)));
    }

    /** True between the two picks of a range. */
    public boolean rangeStarted() {
        return anchor != NONE;
    }

    /** Forgets a range that was started. A complete one stays. */
    public void cancelRange() {
        anchor = NONE;
    }

    /** The month in view. */
    public YearMonth shownMonth() {
        return YearMonth.of(year, month);
    }

    /** Shows a month, without calling back and without a slide. */
    public void show(YearMonth month) {
        if (month != null) {
            this.year = month.getYear();
            this.month = month.getMonthValue();
            slide = null;
            rebuild();
        }
    }

    /** The day the keys are on. */
    LocalDate cursor() {
        return LocalDate.ofEpochDay(cursorOrStart());
    }

    /** The title in view, for a script or a test. */
    String title() {
        return title;
    }

    /** What a column's head reads, for a script or a test. */
    String head(int column) {
        return heads[column];
    }

    public boolean isDisabled(LocalDate day) {
        return isOff(day.toEpochDay());
    }

    /** Where a day's cell is, for a script or a test. A day out of view gets an empty box. */
    Box dayBox(LocalDate day) {
        int cell = (int) (day.toEpochDay() - first);
        if (cell < 0 || cell >= DAYS) {
            return new Box("day", 0, 0, 0, 0);
        }
        return new Box("day", cellX(cell), cellY(cell), cellW(), cellH());
    }

    Box prevBox() {
        return new Box("prev", x, y, NAV_W, HEADER - 1);
    }

    Box nextBox() {
        return new Box("next", x + w - NAV_W, y, NAV_W, HEADER - 1);
    }

    private void rebuild() {
        LocalDate start = LocalDate.of(year, month, 1);
        int lead = Math.floorMod(start.getDayOfWeek().getValue() - weekStart.getValue(), 7);
        monthFirst = start.toEpochDay();
        monthLast = monthFirst + start.lengthOfMonth() - 1;
        first = monthFirst - lead;
        for (int i = 0; i < DAYS; i++) {
            long day = first + i;
            numbers[i] = LocalDate.ofEpochDay(day).getDayOfMonth();
            off[i] = computeOff(day);
        }
        title = monthNames[month - 1] + " " + year;
        for (int c = 0; c < 7; c++) {
            heads[c] = dayNames[Math.floorMod(weekStart.getValue() - 1 + c, 7)];
        }
    }

    private boolean computeOff(long day) {
        if ((min != NONE && day < min) || (max != NONE && day > max)) {
            return true;
        }
        return disabled != null && disabled.test(LocalDate.ofEpochDay(day));
    }

    private boolean isOff(long day) {
        long cell = day - first;
        return cell >= 0 && cell < DAYS ? off[(int) cell] : computeOff(day);
    }

    private long cursorOrStart() {
        if (cursor != NONE) {
            return cursor;
        }
        if (rangeFrom != NONE) {
            return rangeFrom;
        }
        if (value != NONE) {
            return value;
        }
        return today >= monthFirst && today <= monthLast ? today : monthFirst;
    }

    private boolean canGoBack() {
        return min == NONE || monthFirst > min;
    }

    private boolean canGoOn() {
        return max == NONE || monthLast < max;
    }

    /** The player moves by months. */
    private void turn(int by) {
        YearMonth to = YearMonth.of(year, month).plusMonths(by);
        moveTo(to, by > 0);
        if (cursor != NONE) {
            long inMonth = Math.max(monthFirst, Math.min(monthLast, cursor));
            cursor = inMonth;
        }
    }

    private void moveTo(YearMonth to, boolean forward) {
        if (to.getYear() == year && to.getMonthValue() == month) {
            return;
        }
        year = to.getYear();
        month = to.getMonthValue();
        rebuild();
        if (BaseComponent.reducedMotion()) {
            slide = null;
        } else {
            slide = forward ? slideRight : slideLeft;
            slide.replay();
        }
        if (onMonth != null) {
            onMonth.run();
        }
    }

    private void pick(long day) {
        if (isOff(day)) {
            return;
        }
        BaseComponent.click();
        cursor = day;
        if (day < monthFirst || day > monthLast) {
            moveTo(YearMonth.from(LocalDate.ofEpochDay(day)), day > monthLast);
        }
        if (!rangeMode) {
            value = day;
            if (onSelect != null) {
                onSelect.accept(LocalDate.ofEpochDay(day));
            }
            return;
        }
        if (anchor == NONE || !clearBetween(anchor, day)) {
            anchor = day;
            rangeFrom = NONE;
            rangeTo = NONE;
            return;
        }
        rangeFrom = Math.min(anchor, day);
        rangeTo = Math.max(anchor, day);
        anchor = NONE;
        if (onRange != null) {
            onRange.accept(LocalDate.ofEpochDay(rangeFrom), LocalDate.ofEpochDay(rangeTo));
        }
    }

    private boolean clearBetween(long a, long b) {
        long from = Math.min(a, b);
        long to = Math.max(a, b);
        for (long day = from + 1; day < to; day++) {
            if (isOff(day)) {
                return false;
            }
        }
        return true;
    }

    private int cellW() {
        return w / 7;
    }

    private int cellH() {
        return Math.max(0, (h - HEADER - WEEKDAYS) / ROWS);
    }

    private int gridX() {
        return x + (w - 7 * cellW()) / 2;
    }

    private int gridY() {
        return y + HEADER + WEEKDAYS;
    }

    private int cellX(int cell) {
        return gridX() + (cell % 7) * cellW();
    }

    private int cellY(int cell) {
        return gridY() + (cell / 7) * cellH();
    }

    private boolean gridFits() {
        return cellW() >= MIN_CELL_W && cellH() >= MIN_CELL_H;
    }

    private int cellAt(double mx, double my) {
        if (!gridFits() || mx < gridX() || my < gridY()) {
            return -1;
        }
        int col = (int) (mx - gridX()) / cellW();
        int row = (int) (my - gridY()) / cellH();
        if (col > 6 || row >= ROWS) {
            return -1;
        }
        int cell = row * 7 + col;
        boolean inMonth = first + cell >= monthFirst && first + cell <= monthLast;
        return inMonth || showOutside ? cell : -1;
    }

    private int navAt(double mx, double my) {
        if (my < y || my >= y + HEADER - 1 || h < HEADER || w < 2 * NAV_W) {
            return NO_NAV;
        }
        if (mx >= x && mx < x + NAV_W) {
            return PREV;
        }
        return mx >= x + w - NAV_W && mx < x + w ? NEXT : NO_NAV;
    }

    private boolean sliding() {
        return slide != null && slide.moving() && !BaseComponent.reducedMotion();
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        Palette p = t.palette();
        if (!gridFits()) {
            cellHover.step(-1);
            navHover.step(NO_NAV);
            if (h >= FormLook.FONT_H) {
                d.textFit(title, x, y, Math.max(0, w), active ? p.text() : p.disabledText());
            }
            return;
        }
        boolean live = active && hovered;
        int nav = live ? navAt(mx, my) : NO_NAV;
        navHover.step(nav);
        drawHeader(d, t, p, nav);
        int moving = sliding() ? Math.round(slide.offsetX(SLIDE_BY)) : 0;
        int hover = live && moving == 0 ? cellAt(mx, my) : -1;
        cellHover.step(hover);
        int scissorRight = gridX() + 7 * cellW();
        int scissorBottom = gridY() + ROWS * cellH();
        boolean clip = moving != 0;
        if (clip) {
            d.scissor(gridX(), y + HEADER, scissorRight, scissorBottom);
        }
        drawWeekdays(d, p);
        drawCells(d, t, p, hover, moving);
        if (clip) {
            d.endScissor();
        }
    }

    private void drawHeader(Draw d, Theme t, Palette p, int nav) {
        int ink = active ? p.text() : p.disabledText();
        boolean arrows = w >= 2 * NAV_W + 24;
        if (arrows) {
            drawNav(d, t, p, PREV, t.icon(Icon.PREV), canGoBack(), nav);
            drawNav(d, t, p, NEXT, t.icon(Icon.NEXT), canGoOn(), nav);
        }
        int room = arrows ? w - 2 * NAV_W - 2 : w;
        int textW = Math.min(room, TextMeasure.width(title) - 1);
        d.textFit(title, x + (w - textW) / 2, y + (HEADER - 1 - FormLook.FONT_H) / 2 + 1, room, ink);
    }

    private void drawNav(Draw d, Theme t, Palette p, int which, IconArt art, boolean can, int nav) {
        int left = which == PREV ? x : x + w - NAV_W;
        boolean on = active && can;
        boolean held = on && heldNav == which;
        float lit = held ? 1F : nav == which && on ? navHover.fade() : 0F;
        int face = on ? Shapes.mix(p.raised(), p.raisedHover(), lit) : FormLook.disabledFace(p);
        Shapes.Relief relief = held ? Shapes.Relief.PRESSED : on ? Shapes.Relief.RAISED : Shapes.Relief.FLAT;
        t.frame().raised(d, p, left, y, NAV_W, HEADER - 1, face, relief);
        int ink = on ? Ink.on(p, face) : p.disabledText();
        art.draw(d, left + (NAV_W - art.width()) / 2, y + (HEADER - 1 - art.height()) / 2, ink, 1F);
    }

    private void drawWeekdays(Draw d, Palette p) {
        int ink = active ? Ink.soft(p, p.surface()) : p.disabledText();
        int top = y + HEADER + (WEEKDAYS - FormLook.FONT_H) / 2;
        int cw = cellW();
        for (int c = 0; c < 7; c++) {
            String head = heads[c];
            int width = d.width(head) - 1;
            d.textFit(head, gridX() + c * cw + Math.max(0, (cw - width) / 2), top, cw, ink);
        }
    }

    private void drawCells(Draw d, Theme t, Palette p, int hover, int moving) {
        int cw = cellW();
        int ch = cellH();
        FrameStyle frame = t.frame();
        int surface = FormLook.surface(p);
        int fade = Shapes.mix(FormLook.solid(p.text()), surface, OUTSIDE_FADE);
        // A range being made: from its anchor to where the pointer or the keys are.
        long fromPreview = NONE;
        long toPreview = NONE;
        if (anchor != NONE) {
            long end = hover >= 0 && !off[hover] ? first + hover : keyed && focused ? cursorOrStart() : anchor;
            fromPreview = Math.min(anchor, end);
            toPreview = Math.max(anchor, end);
        }
        if (rangeFrom != NONE && rangeFrom != rangeTo) {
            int face = active ? Shapes.alpha(p.primary(), BAND_ALPHA) : Shapes.alpha(p.text(), 0x18);
            drawBand(d, frame, p, rangeFrom, rangeTo, face, moving, cw, ch);
        }
        if (fromPreview != NONE && fromPreview != toPreview) {
            drawBand(d, frame, p, fromPreview, toPreview, Shapes.alpha(p.primary(), PREVIEW_ALPHA), moving, cw, ch);
        }
        for (int cell = 0; cell < DAYS; cell++) {
            long day = first + cell;
            boolean inMonth = day >= monthFirst && day <= monthLast;
            if (!inMonth && !showOutside) {
                continue;
            }
            int cx = cellX(cell) + moving;
            int cy = cellY(cell);
            boolean dayOff = off[cell] || !active;
            boolean end = marked(day);
            int ink;
            if (end) {
                int face = !active ? FormLook.disabledFace(p) : p.primary();
                frame.plate(d, p, cx, cy, cw, ch, cell == hover ? p.primaryHover() : face, 0);
                ink = active ? p.primaryText() : p.disabledText();
            } else {
                if (cell == hover && !off[cell]) {
                    frame.plate(d, p, cx, cy, cw, ch, RowLook.tint(p, cellHover.fade()), 0);
                }
                ink = dayOff ? p.disabledText() : inMonth ? p.text() : fade;
            }
            drawNumber(d, cell, cx, cy, cw, ch, ink, dayOff && inMonth && active);
            if (day == today) {
                int edge = end || !active ? ink : Ink.readable(p, p.accent(), surface, Ink.MARK);
                frame.plate(d, p, cx, cy, cw, ch, 0, edge);
            }
        }
        if (focused && keyed && active && moving == 0) {
            long at = cursorOrStart();
            int cell = (int) (at - first);
            if (cell >= 0 && cell < DAYS) {
                frame.plate(d, p, cellX(cell), cellY(cell), cw, ch, 0, FormLook.solid(p.text()));
            }
        }
    }

    /** One plate per week row from {@code start} to {@code stop}, under the ends' own plates, so a range reads as one piece. */
    private void drawBand(Draw d, FrameStyle frame, Palette p, long start, long stop, int face, int moving, int cw, int ch) {
        long from = showOutside ? start : Math.max(start, monthFirst);
        long to = showOutside ? stop : Math.min(stop, monthLast);
        for (int row = 0; row < ROWS; row++) {
            long rowFirst = first + 7L * row;
            long a = Math.max(from, rowFirst);
            long b = Math.min(to, rowFirst + 6);
            if (a > b) {
                continue;
            }
            int left = cellX((int) (a - first)) + (a == start && marked(a) ? cw / 2 : 0);
            int right = cellX((int) (b - first)) + (b == stop && marked(b) ? cw - cw / 2 : cw);
            if (right > left) {
                frame.plate(d, p, left + moving, cellY(row * 7), right - left, ch, face, 0);
            }
        }
    }

    private boolean marked(long day) {
        return day == value || day == rangeFrom || day == rangeTo || day == anchor;
    }

    private void drawNumber(Draw d, int cell, int cx, int cy, int cw, int ch, int ink, boolean strike) {
        String number = NUMBERS[numbers[cell]];
        int width = d.width(number) - 1;
        int tx = cx + (cw - width) / 2;
        int ty = cy + (ch - FormLook.FONT_H + 1) / 2;
        d.text(number, tx, ty, ink);
        if (strike) {
            d.fill(tx - 1, ty + 3, tx + width + 1, ty + 4, ink);
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        int cell = cellAt(mx, my);
        if (cell >= 0 && dayTooltip != null) {
            List<String> lines = dayTooltip.apply(LocalDate.ofEpochDay(first + cell));
            if (lines != null && !lines.isEmpty()) {
                return lines;
            }
        }
        return tooltip;
    }

    @Override
    protected boolean takesFocus() {
        return visible && active;
    }

    @Override
    protected boolean navigable() {
        return visible && active && settled();
    }

    /** The keys come in with a visible cursor; a click leaves none until a key is pressed. */
    @Override
    protected void enter(boolean forward) {
        keyed = true;
    }

    /** The grid draws its own ring, on the day the keys are on. */
    @Override
    protected boolean drawFocus(Draw d, Theme t) {
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !active || button != 0 || !isOver(mx, my)) {
            return false;
        }
        keyed = false;
        int nav = navAt(mx, my);
        if (nav != NO_NAV) {
            if (nav == PREV ? canGoBack() : canGoOn()) {
                BaseComponent.sound().tap();
                heldNav = nav;
                turn(nav == PREV ? -1 : 1);
            }
            return true;
        }
        int cell = cellAt(mx, my);
        if (cell >= 0) {
            pick(first + cell);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        heldNav = NO_NAV;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || !active || !isOver(mx, my) || amount == 0D) {
            return false;
        }
        if (amount > 0D ? canGoBack() : canGoOn()) {
            BaseComponent.sound().tap();
            turn(amount > 0D ? -1 : 1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!visible || !active) {
            return false;
        }
        switch (key) {
            case LEFT -> {
                if (shift) {
                    jump(LocalDate.ofEpochDay(cursorOrStart()).minusMonths(1).toEpochDay(), -1);
                } else {
                    walk(-1);
                }
            }
            case RIGHT -> {
                if (shift) {
                    jump(LocalDate.ofEpochDay(cursorOrStart()).plusMonths(1).toEpochDay(), 1);
                } else {
                    walk(1);
                }
            }
            case UP -> {
                if (shift) {
                    jump(LocalDate.ofEpochDay(cursorOrStart()).minusYears(1).toEpochDay(), -1);
                } else {
                    walk(-7);
                }
            }
            case DOWN -> {
                if (shift) {
                    jump(LocalDate.ofEpochDay(cursorOrStart()).plusYears(1).toEpochDay(), 1);
                } else {
                    walk(7);
                }
            }
            case HOME -> {
                if (shift) {
                    jump(LocalDate.ofEpochDay(cursorOrStart()).withDayOfMonth(1).toEpochDay(), 1);
                } else {
                    long at = cursorOrStart();
                    int back = Math.floorMod(LocalDate.ofEpochDay(at).getDayOfWeek().getValue() - weekStart.getValue(), 7);
                    jump(at - back, 1);
                }
            }
            case END -> {
                if (shift) {
                    LocalDate at = LocalDate.ofEpochDay(cursorOrStart());
                    jump(at.withDayOfMonth(at.lengthOfMonth()).toEpochDay(), -1);
                } else {
                    long at = cursorOrStart();
                    int back = Math.floorMod(LocalDate.ofEpochDay(at).getDayOfWeek().getValue() - weekStart.getValue(), 7);
                    jump(at + 6 - back, -1);
                }
            }
            case ENTER, SPACE -> {
                keyed = true;
                pick(cursorOrStart());
            }
            case ESCAPE -> {
                if (anchor == NONE) {
                    return false;
                }
                BaseComponent.click();
                anchor = NONE;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Moves by a step, past disabled days, and stops where there is none left to land on. */
    private void walk(int step) {
        keyed = true;
        long at = cursorOrStart();
        for (int i = 1; i <= MAX_WALK; i++) {
            long to = at + (long) step * i;
            if ((min != NONE && to < min) || (max != NONE && to > max)) {
                return;
            }
            if (!isOff(to)) {
                land(to);
                return;
            }
        }
    }

    /** Goes to a day, or the nearest one that can be picked in the direction given. */
    private void jump(long to, int back) {
        keyed = true;
        long day = to;
        if (min != NONE) {
            day = Math.max(min, day);
        }
        if (max != NONE) {
            day = Math.min(max, day);
        }
        for (int i = 0; i <= MAX_WALK; i++) {
            long at = day + (long) back * i;
            if ((min != NONE && at < min) || (max != NONE && at > max)) {
                return;
            }
            if (!isOff(at)) {
                land(at);
                return;
            }
        }
    }

    private void land(long day) {
        cursor = day;
        if (day < monthFirst || day > monthLast) {
            moveTo(YearMonth.from(LocalDate.ofEpochDay(day)), day > monthLast);
        }
    }

    /** Lets the owner say the keys are in use, as a popup's first frame has had no {@link #enter}. */
    void keyedNow() {
        keyed = true;
    }
}
