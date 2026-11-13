package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Lists;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * A grid of cells whose shade tells a value, on the theme's primary colour: a free grid, or a calendar of days in
 * week columns.
 *
 * <pre>{@code
 * HeatmapComponent days = add(new HeatmapComponent(at(new Box("days", 10, 10, 280, 70)))
 *         .calendar(LocalDate.of(2026, 1, 1), blocksMinedPerDay)
 *         .legendWords(words.get("mymod.less"), words.get("mymod.more"))
 *         .onSelect(day -> open(day)));
 * }</pre>
 *
 * <p>A value of 0 or less than the range takes the lowest shade, NaN or an infinity is a day with no data, and
 * {@link #levels(int)} sets how many shades there are. The cells fit the box; when a calendar would come
 * out too small to see, the height decides, the newest days are in view and the wheel moves along the
 * weeks. The pointer or the keys outline a cell, and the line under the grid and the tooltip tell its
 * value. A click calls {@link #onSelect(IntConsumer)}. With focus: the arrows walk the cells, home and end go along
 * the row, Enter or Space picks, Escape puts the keys away. Small boxes drop the footer and the labels. A frame
 * allocates nothing: shades and words are worked out when the data, the size or the cell changes.
 */
public class HeatmapComponent extends BaseComponent {
    private static final int LINE = 10;
    private static final int LABEL_GAP = 3;
    private static final int FOOT_GAP = 2;
    private static final int MAX_PITCH = 13;
    private static final int MIN_READABLE = 4;
    private static final int MIN_PITCH = 3;
    private static final int SWATCH = 5;
    private static final int SWATCH_STEP = 6;
    private static final int RAMP_GAP = 3;
    private static final int DAYS = 7;
    private static final float RAMP_LOW = 0.35F;
    private static final int NOTHING_ALPHA = 0x14;
    private static final int NO_DATA_ALPHA = 0x30;
    private static final List<String> MONTHS = List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");
    private static final List<String> WEEKDAYS = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

    private boolean isCalendar;
    private int rows;
    private int cols;
    private double[] values = new double[0];
    private LocalDate start;
    private DayOfWeek firstDay = DayOfWeek.MONDAY;
    private int offset;
    private List<String> rowNames = List.of();
    private List<String> colNames = List.of();
    @Override
    void words(Words words) {
        if (monthNames == MONTHS) {
            monthNames = List.of(names(words, "minuit.month.short", MONTHS.toArray(new String[0])));
        }
        if (dayNames == WEEKDAYS) {
            dayNames = List.of(names(words, "minuit.day.short", WEEKDAYS.toArray(new String[0])));
        }
        changed();
    }

    private List<String> monthNames = MONTHS;
    private List<String> dayNames = WEEKDAYS;
    private int fixedCell;
    private int levels = 5;
    private boolean hasRange;
    private double rangeLo;
    private double rangeHi;
    private String less = "";
    private String more = "";
    private String unit = "";
    private String noData = "-";
    private String emptyText;
    private boolean footer = true;
    private DoubleFunction<String> valueFormat;
    private IntFunction<String> cellName;
    private IntConsumer onSelect;

    private boolean dataDirty = true;
    private boolean layoutDirty = true;
    private boolean freshCalendar;
    private int laidW = -1;
    private int laidH = -1;
    private byte[] shade = new byte[0];
    private double lo;
    private double hi;
    private int pitch;
    private int cell;
    private int gridX;
    private int gridY;
    private int visCols;
    private int visRows;
    private int firstCol;
    private int firstRow;
    private boolean showTop;
    private boolean showLeft;
    private boolean showFoot;
    private int leftW;
    private int rowStride = 1;
    private int colStride = 1;
    private int[] monthCols = new int[0];
    private String[] monthText = new String[0];
    private int[] monthW = new int[0];
    private int[] colW = new int[0];
    private int lessW;
    private String[] rowText = new String[0];
    private String lessText = "";
    private String moreText = "";
    private int rampX;
    private boolean rampShown;
    private int readoutW;

    private Palette rampFor;
    private boolean rampActive;
    private int[] ramp = new int[0];
    private int noDataInk;

    private int cursorCol = -1;
    private int cursorRow = -1;
    private int hoverCol = -1;
    private int hoverRow = -1;
    private int readFor = -2;
    private int readW = -1;
    private String readText = "";
    private String readWhole = "";
    private int tipFor = -2;
    private List<String> tip;

    public HeatmapComponent(Box box) {
        super(box);
    }

    // ---- setup ----

    /** A free grid. {@code values} goes row by row, and is not copied; call {@link #refresh()} when it changes. */
    public HeatmapComponent grid(int rows, int cols, double... values) {
        isCalendar = false;
        this.rows = Math.max(0, rows);
        this.cols = Math.max(0, cols);
        this.values = values == null ? new double[0] : values;
        offset = 0;
        changed();
        return this;
    }

    /** A calendar from this day on, a column a week. {@code values} has one value a day, and is not copied. */
    public HeatmapComponent calendar(LocalDate first, double... perDay) {
        isCalendar = true;
        start = first;
        values = perDay == null ? new double[0] : perDay;
        rows = DAYS;
        placeCalendar();
        freshCalendar = true;
        changed();
        return this;
    }

    private void placeCalendar() {
        offset = Math.floorMod(start.getDayOfWeek().getValue() - firstDay.getValue(), DAYS);
        cols = values.length == 0 ? 0 : (offset + values.length + DAYS - 1) / DAYS;
    }

    /** The weekday a calendar's columns start on. Monday by default. */
    public HeatmapComponent firstDay(DayOfWeek firstDay) {
        this.firstDay = firstDay;
        if (isCalendar) {
            placeCalendar();
        }
        changed();
        return this;
    }

    /** Names beside a grid's rows. */
    public HeatmapComponent rowLabels(List<String> names) {
        rowNames = Lists.labels(names);
        layoutDirty = true;
        return this;
    }

    /** Names above a grid's columns. */
    public HeatmapComponent colLabels(List<String> names) {
        colNames = Lists.labels(names);
        layoutDirty = true;
        return this;
    }

    /** The twelve months' short names, in place of the English ones. */
    public HeatmapComponent monthNames(List<String> names) {
        if (names != null && names.size() == 12) {
            monthNames = Lists.labels(names);
            changed();
        }
        return this;
    }

    /** The seven weekdays' short names from Monday on, in place of the English ones. */
    public HeatmapComponent dayNames(List<String> names) {
        if (names != null && names.size() == DAYS) {
            dayNames = Lists.labels(names);
            changed();
        }
        return this;
    }

    /** A cell's size. By default the cells fit the box. */
    public HeatmapComponent cell(int size) {
        fixedCell = Math.max(0, size);
        layoutDirty = true;
        return this;
    }

    /** How many shades. 5 by default, and at least 2. */
    public HeatmapComponent levels(int levels) {
        this.levels = Math.max(2, Math.min(32, levels));
        rampFor = null;
        changed();
        return this;
    }

    /** The values the lowest and the highest shade stand for. By default the data's own, from 0 or below. */
    public HeatmapComponent range(double min, double max) {
        hasRange = Double.isFinite(min) && Double.isFinite(max) && max > min;
        rangeLo = min;
        rangeHi = max;
        changed();
        return this;
    }

    /** The words at the ends of the shades' legend. */
    public HeatmapComponent legendWords(String less, String more) {
        this.less = less == null ? "" : less;
        this.more = more == null ? "" : more;
        layoutDirty = true;
        return this;
    }

    /** False for no line of readout and legend under the grid. */
    public HeatmapComponent footer(boolean footer) {
        this.footer = footer;
        layoutDirty = true;
        return this;
    }

    /** Follows every value, as in {@code " blocks"}. */
    public HeatmapComponent unit(String unit) {
        this.unit = unit == null ? "" : unit;
        readFor = -2;
        tipFor = -2;
        return this;
    }

    /** Writes the values in place of the built-in number. */
    public HeatmapComponent valueFormat(DoubleFunction<String> valueFormat) {
        this.valueFormat = valueFormat;
        readFor = -2;
        tipFor = -2;
        return this;
    }

    /** Names a cell by its index, in place of the date or the row and column. */
    public HeatmapComponent cellName(IntFunction<String> cellName) {
        this.cellName = cellName;
        readFor = -2;
        tipFor = -2;
        return this;
    }

    /** What a cell with no data says in place of a value. */
    public HeatmapComponent noDataText(String noData) {
        this.noData = noData == null ? "" : noData;
        readFor = -2;
        tipFor = -2;
        return this;
    }

    /** What shows with no cells. */
    public HeatmapComponent emptyText(String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    /** Called with a cell's index on a click, or on Enter or Space with the keys on it. */
    public HeatmapComponent onSelect(IntConsumer onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    public HeatmapComponent active(boolean active) {
        this.active = active;
        if (!active) {
            cursorCol = -1;
            cursorRow = -1;
        }
        return this;
    }

    @Override
    public HeatmapComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** Reads the values again after they were changed in place. */
    public void refresh() {
        changed();
    }

    private void changed() {
        dataDirty = true;
        layoutDirty = true;
        readFor = -2;
        tipFor = -2;
    }

    // ---- reading ----

    public int columns() {
        return cols;
    }

    public int rowCount() {
        return rows;
    }

    /** The cell's index in the values, or -1 for a calendar's blank corner, or past the end. */
    public int indexAt(int col, int row) {
        if (col < 0 || row < 0 || col >= cols || row >= rows) {
            return -1;
        }
        int index = isCalendar ? col * DAYS + row - offset : row * cols + col;
        return index >= 0 && index < values.length ? index : -1;
    }

    /** The shade a cell's value takes, from 0, or -1 for no data. */
    public int levelOf(int index) {
        settle();
        return index < 0 || index >= shade.length ? -1 : shade[index];
    }

    /** A cell's place. */
    Box cellBox(int col, int row) {
        settle();
        return new Box("cell", gridX + (col - firstCol) * pitch, gridY + (row - firstRow) * pitch, cell, cell);
    }

    /** The cell's column under a point in the grid, or -1. */
    int colAt(double mx, double my) {
        settle();
        if (pitch <= 0 || mx < gridX || my < gridY) {
            return -1;
        }
        int col = (int) Math.floor((mx - gridX) / pitch);
        int row = (int) Math.floor((my - gridY) / pitch);
        return col < visCols && row < visRows ? firstCol + col : -1;
    }

    /** The cell's row under a point in the grid, or -1. */
    int rowAt(double mx, double my) {
        settle();
        if (pitch <= 0 || mx < gridX || my < gridY) {
            return -1;
        }
        int col = (int) Math.floor((mx - gridX) / pitch);
        int row = (int) Math.floor((my - gridY) / pitch);
        return col < visCols && row < visRows ? firstRow + row : -1;
    }

    /** The first column in view. */
    int firstColumn() {
        settle();
        return firstCol;
    }

    int visibleColumns() {
        settle();
        return visCols;
    }

    public int cursorColumn() {
        return cursorCol;
    }

    public int cursorRow() {
        return cursorRow;
    }

    boolean footerShows() {
        settle();
        return showFoot;
    }

    boolean labelsShow() {
        settle();
        return showTop || showLeft;
    }

    /** What the line under the grid says about a cell, whole. */
    public String describe(int index) {
        if (index < 0 || index >= values.length) {
            return "";
        }
        double v = values[index];
        String value = Double.isFinite(v) ? (valueFormat != null ? valueFormat.apply(v) : DataLook.number(v)) + unit : noData;
        return name(index) + ": " + value;
    }

    private String name(int index) {
        if (cellName != null) {
            String named = cellName.apply(index);
            return named == null ? "" : named;
        }
        if (isCalendar) {
            return start.plusDays(index).toString();
        }
        int row = cols <= 0 ? 0 : index / cols;
        int col = cols <= 0 ? 0 : index % cols;
        String r = row < rowNames.size() ? rowNames.get(row) : Integer.toString(row + 1);
        String c = col < colNames.size() ? colNames.get(col) : Integer.toString(col + 1);
        return r + " / " + c;
    }

    // ---- working it out ----

    private void settle() {
        if (dataDirty) {
            dataDirty = false;
            shades();
        }
        if (layoutDirty || laidW != w || laidH != h) {
            layoutDirty = false;
            laidW = w;
            laidH = h;
            layout();
        }
    }

    private void shades() {
        if (shade.length != values.length) {
            shade = new byte[values.length];
        }
        lo = 0D;
        hi = 0D;
        if (hasRange) {
            lo = rangeLo;
            hi = rangeHi;
        } else {
            for (double v : values) {
                if (Double.isFinite(v)) {
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
            }
        }
        for (int i = 0; i < values.length; i++) {
            double v = values[i];
            if (!Double.isFinite(v)) {
                shade[i] = -1;
            } else if (v <= lo || hi <= lo) {
                shade[i] = 0;
            } else {
                // The first shade is for nothing at all: anything above it shows.
                double at = Math.min(1D, (v - lo) / (hi - lo));
                shade[i] = (byte) Math.max(1, Math.min(levels - 1, (int) Math.ceil(at * (levels - 1))));
            }
        }
        if (isCalendar) {
            monthLabels();
        }
    }

    private void monthLabels() {
        int count = 0;
        int[] at = new int[values.length / 28 + 2];
        String[] names = new String[at.length];
        LocalDate day = start.withDayOfMonth(1);
        LocalDate end = start.plusDays(values.length);
        boolean first = true;
        while (day.isBefore(end) && count < at.length) {
            LocalDate shown = first ? start : day;
            first = false;
            int col = (offset + (int) (shown.toEpochDay() - start.toEpochDay())) / DAYS;
            at[count] = col;
            names[count] = monthNames.get(shown.getMonthValue() - 1);
            count++;
            day = day.plusMonths(1);
        }
        monthCols = java.util.Arrays.copyOf(at, count);
        monthText = java.util.Arrays.copyOf(names, count);
        monthW = new int[count];
        for (int i = 0; i < count; i++) {
            monthW[i] = TextMeasure.width(monthText[i]);
        }
    }

    private void layout() {
        Draw measure = TextMeasure.draw();
        boolean hasTop = isCalendar ? cols > 0 : !colNames.isEmpty();
        boolean hasLeft = isCalendar ? cols > 0 : !rowNames.isEmpty();
        showTop = hasTop && h >= 34;
        showFoot = footer && h >= 40 && w >= 60;
        int foot = showFoot ? LINE + FOOT_GAP : 0;
        int top = showTop ? LINE : 0;
        showLeft = hasLeft && w >= 70;
        leftW = 0;
        if (showLeft) {
            int widest = 0;
            if (isCalendar) {
                for (int i = 0; i < DAYS; i++) {
                    widest = Math.max(widest, TextMeasure.width(dayNames.get(i)));
                }
            } else {
                for (String name : rowNames) {
                    widest = Math.max(widest, TextMeasure.width(name));
                }
            }
            leftW = Math.min(widest, w / 3) + LABEL_GAP;
        }
        int roomW = Math.max(0, w - leftW);
        int roomH = Math.max(0, h - top - foot);
        if (rows <= 0 || cols <= 0) {
            pitch = 0;
            cell = 0;
            visCols = 0;
            visRows = 0;
            return;
        }
        if (fixedCell > 0) {
            pitch = fixedCell + 1;
        } else {
            int fit = Math.min(roomW / cols, roomH / rows);
            if (fit >= MIN_READABLE) {
                pitch = Math.min(MAX_PITCH, fit);
            } else {
                // Too many to fit: the height decides, and the wheel goes along the rest.
                pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, roomH / rows));
                pitch = Math.max(MIN_PITCH, Math.min(pitch, roomW / Math.max(1, Math.min(cols, 8))));
            }
        }
        cell = Math.max(1, pitch - 1);
        visCols = Math.min(cols, roomW / pitch);
        visRows = Math.min(rows, roomH / pitch);
        gridX = x + leftW;
        gridY = y + top;
        if (freshCalendar) {
            freshCalendar = false;
            firstCol = cols - visCols;
        }
        firstCol = Math.max(0, Math.min(cols - visCols, firstCol));
        firstRow = Math.max(0, Math.min(rows - visRows, firstRow));
        rowStride = Math.max(isCalendar ? 2 : 1, (LINE + pitch - 1) / pitch);
        colStride = 1;
        if (showLeft) {
            int count = isCalendar ? DAYS : rowNames.size();
            if (rowText.length != count) {
                rowText = new String[count];
            }
            for (int i = 0; i < count; i++) {
                String raw = isCalendar ? dayNames.get(Math.floorMod(firstDay.getValue() - 1 + i, DAYS)) : rowNames.get(i);
                rowText[i] = measure.ellipsize(raw, leftW - LABEL_GAP);
            }
        }
        if (!isCalendar && showTop) {
            int widest = 0;
            colW = new int[colNames.size()];
            for (int i = 0; i < colW.length; i++) {
                colW[i] = TextMeasure.width(colNames.get(i));
                widest = Math.max(widest, colW[i]);
            }
            colStride = Math.max(1, (widest + 3 + pitch - 1) / pitch);
        }
        layoutFooter(measure);
    }

    private void layoutFooter(Draw measure) {
        rampShown = false;
        readoutW = w;
        if (!showFoot) {
            return;
        }
        int rampW = levels * SWATCH_STEP - 1;
        int words = (less.isEmpty() ? 0 : TextMeasure.width(less) + RAMP_GAP) + (more.isEmpty() ? 0 : TextMeasure.width(more) + RAMP_GAP);
        boolean withWords = rampW + words <= w * 3 / 5;
        int total = rampW + (withWords ? words : 0);
        if (total > w / 2 + 20) {
            return;
        }
        rampShown = true;
        lessText = withWords ? less : "";
        moreText = withWords ? more : "";
        lessW = lessText.isEmpty() ? 0 : TextMeasure.width(lessText);
        rampX = x + w - total;
        readoutW = Math.max(0, w - total - 6);
    }

    private void paintRamp(Palette p) {
        if (rampFor == p && rampActive == active && ramp.length == levels) {
            return;
        }
        rampFor = p;
        rampActive = active;
        if (ramp.length != levels) {
            ramp = new int[levels];
        }
        int back = DataLook.back(p);
        int top = Ink.readable(p, p.markInk(), DataLook.back(p), Ink.MARK);
        int low = Shapes.mix(back, top, RAMP_LOW);
        // Quieter than the first real shade: a wash as strong as a shape's would read as more than that shade.
        ramp[0] = Shapes.alpha(DataLook.opaque(Ink.on(p, back)), NOTHING_ALPHA);
        for (int i = 1; i < levels; i++) {
            float at = levels <= 2 ? 1F : (i - 1F) / (levels - 2F);
            int color = Shapes.mix(low, top, at);
            ramp[i] = active ? color : DataLook.faded(p, color);
        }
        noDataInk = Shapes.alpha(DataLook.opaque(Ink.on(p, back)), NO_DATA_ALPHA);
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        settle();
        Palette p = t.palette();
        paintRamp(p);
        hoverCol = -1;
        hoverRow = -1;
        if (visCols <= 0 || visRows <= 0) {
            if (cols <= 0 || rows <= 0) {
                RowLook.drawEmpty(d, p, emptyText, active, x, y, w, h);
            }
            return;
        }
        if (active && hovered && settled()) {
            int col = colAt(mx, my);
            int row = rowAt(mx, my);
            if (col >= 0 && row >= 0 && indexAt(col, row) >= 0) {
                hoverCol = col;
                hoverRow = row;
            }
        }
        drawLabels(d, p);
        int endCol = firstCol + visCols;
        int endRow = firstRow + visRows;
        for (int row = firstRow; row < endRow; row++) {
            int top = gridY + (row - firstRow) * pitch;
            for (int col = firstCol; col < endCol; col++) {
                int index = indexAt(col, row);
                if (index < 0) {
                    continue;
                }
                int level = shade[index];
                int left = gridX + (col - firstCol) * pitch;
                if (level >= 0) {
                    d.fill(left, top, left + cell, top + cell, ramp[level]);
                } else if (cell >= 3) {
                    // Hollow: no data must not pass for a low value.
                    DataLook.outline(d, left, top, cell, cell, noDataInk);
                }
            }
        }
        int ink = active ? Ink.on(p, DataLook.back(p)) : p.disabledText();
        if (focused && active && cursorCol >= 0) {
            mark(d, cursorCol, cursorRow, Ink.soft(p, DataLook.back(p)));
        }
        if (hoverCol >= 0) {
            mark(d, hoverCol, hoverRow, ink);
        }
        if (showFoot) {
            drawFooter(d, p);
        }
    }

    /** An outline round a cell, in the gap between cells. */
    private void mark(Draw d, int col, int row, int color) {
        if (col < firstCol || col >= firstCol + visCols || row < firstRow || row >= firstRow + visRows) {
            return;
        }
        int left = gridX + (col - firstCol) * pitch;
        int top = gridY + (row - firstRow) * pitch;
        DataLook.outline(d, left - 1, top - 1, cell + 2, cell + 2, color);
    }

    private void drawLabels(Draw d, Palette p) {
        int soft = active ? Ink.soft(p, DataLook.back(p)) : p.disabledText();
        if (showLeft && rowText.length > 0) {
            int from = firstRow;
            for (int row = from; row < firstRow + visRows && row < rowText.length; row++) {
                if (row % rowStride != 0) {
                    continue;
                }
                int top = gridY + (row - firstRow) * pitch;
                d.text(rowText[row], x, top + (cell - RowLook.FONT_H) / 2 + 1, soft);
            }
        }
        if (!showTop) {
            return;
        }
        if (isCalendar) {
            int edge = Integer.MIN_VALUE;
            for (int i = 0; i < monthCols.length; i++) {
                int col = monthCols[i];
                if (col < firstCol || col >= firstCol + visCols) {
                    continue;
                }
                int left = gridX + (col - firstCol) * pitch;
                int wide = monthW[i];
                boolean nextTooClose = i + 1 < monthCols.length && gridX + (monthCols[i + 1] - firstCol) * pitch < left + wide + 2;
                if (left >= edge && !nextTooClose && left + wide <= x + w) {
                    d.text(monthText[i], left, y, soft);
                    edge = left + wide + 3;
                }
            }
        } else {
            for (int col = firstCol; col < firstCol + visCols && col < colNames.size(); col++) {
                if (col % colStride != 0) {
                    continue;
                }
                String name = colNames.get(col);
                int centre = gridX + (col - firstCol) * pitch + cell / 2;
                int left = Math.max(x, Math.min(x + w - colW[col], centre - colW[col] / 2));
                d.text(name, left, y, soft);
            }
        }
    }

    private void drawFooter(Draw d, Palette p) {
        int soft = active ? Ink.soft(p, DataLook.back(p)) : p.disabledText();
        int foot = y + h - LINE;
        int index = hoverCol >= 0 ? indexAt(hoverCol, hoverRow) : focused && active && cursorCol >= 0 ? indexAt(cursorCol, cursorRow) : -1;
        if (index >= 0) {
            if (readFor != index || readW != readoutW) {
                readFor = index;
                readW = readoutW;
                readWhole = describe(index);
                readText = TextMeasure.draw().ellipsize(readWhole, readoutW);
            }
            d.text(readText, x, foot + 1, active ? Ink.on(p, DataLook.back(p)) : p.disabledText());
        }
        if (!rampShown) {
            return;
        }
        int at = rampX;
        if (!lessText.isEmpty()) {
            d.text(lessText, at, foot + 1, soft);
            at += lessW + RAMP_GAP;
        }
        int swatchTop = foot + (LINE - SWATCH) / 2;
        for (int i = 0; i < levels; i++) {
            d.fill(at, swatchTop, at + SWATCH, swatchTop + SWATCH, ramp[i]);
            at += SWATCH_STEP;
        }
        if (!moreText.isEmpty()) {
            d.text(moreText, at - 1 + RAMP_GAP, foot + 1, soft);
        }
    }

    // ---- tooltips ----

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!visible) {
            return null;
        }
        if (active) {
            int col = colAt(mx, my);
            int row = rowAt(mx, my);
            int index = col >= 0 && row >= 0 ? indexAt(col, row) : -1;
            if (index >= 0) {
                if (tipFor != index || tip == null) {
                    tipFor = index;
                    tip = List.of(describe(index));
                }
                return tip;
            }
        }
        return super.tooltipAt(mx, my);
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
        return accepts() && values.length > 0 && cols > 0;
    }

    @Override
    protected Box focusBox() {
        settle();
        if (cursorCol >= 0 && cursorCol >= firstCol && cursorCol < firstCol + visCols && cursorRow >= firstRow && cursorRow < firstRow + visRows) {
            return new Box("cell", gridX + (cursorCol - firstCol) * pitch, gridY + (cursorRow - firstRow) * pitch, cell, cell);
        }
        return super.focusBox();
    }

    @Override
    protected int focusCorner() {
        return cursorCol >= 0 ? 0 : super.focusCorner();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!accepts() || button != 0 || !isOver(mx, my)) {
            return false;
        }
        int col = colAt(mx, my);
        int row = rowAt(mx, my);
        int index = col >= 0 && row >= 0 ? indexAt(col, row) : -1;
        if (index < 0) {
            return true;
        }
        cursorCol = col;
        cursorRow = row;
        if (onSelect != null) {
            BaseComponent.click();
            onSelect.accept(index);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || !settled() || !isOver(mx, my)) {
            return false;
        }
        settle();
        int by = amount > 0D ? -1 : 1;
        if (visRows < rows) {
            firstRow = Math.max(0, Math.min(rows - visRows, firstRow + by));
            return true;
        }
        if (visCols < cols) {
            firstCol = Math.max(0, Math.min(cols - visCols, firstCol + by));
            return true;
        }
        return false;
    }

    private void moveCursor(int col, int row) {
        int c = Math.max(0, Math.min(cols - 1, col));
        int r = Math.max(0, Math.min(rows - 1, row));
        if (indexAt(c, r) < 0) {
            return;
        }
        cursorCol = c;
        cursorRow = r;
        if (c < firstCol) {
            firstCol = c;
        } else if (c >= firstCol + visCols) {
            firstCol = c - visCols + 1;
        }
        if (r < firstRow) {
            firstRow = r;
        } else if (r >= firstRow + visRows) {
            firstRow = r - visRows + 1;
        }
    }

    /** Where the keys start: the last cell in view that holds a value. */
    private void startCursor() {
        for (int col = firstCol + visCols - 1; col >= firstCol; col--) {
            for (int row = firstRow + visRows - 1; row >= firstRow; row--) {
                if (indexAt(col, row) >= 0) {
                    cursorCol = col;
                    cursorRow = row;
                    return;
                }
            }
        }
    }

    @Override
    protected boolean activate() {
        if (!accepts() || cursorCol < 0) {
            return false;
        }
        int index = indexAt(cursorCol, cursorRow);
        if (index >= 0 && onSelect != null) {
            BaseComponent.click();
            onSelect.accept(index);
        }
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!accepts() || values.length == 0 || cols <= 0) {
            return false;
        }
        settle();
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
        boolean walks = key == InputKey.LEFT || key == InputKey.RIGHT || key == InputKey.UP || key == InputKey.DOWN
                || key == InputKey.HOME || key == InputKey.END;
        if (!walks) {
            return false;
        }
        if (cursorCol < 0) {
            startCursor();
            return true;
        }
        switch (key) {
            case LEFT -> moveCursor(cursorCol - 1, cursorRow);
            case RIGHT -> moveCursor(cursorCol + 1, cursorRow);
            case UP -> moveCursor(cursorCol, cursorRow - 1);
            case DOWN -> moveCursor(cursorCol, cursorRow + 1);
            case HOME -> moveCursor(0, cursorRow);
            default -> moveCursor(cols - 1, cursorRow);
        }
        return true;
    }
}
