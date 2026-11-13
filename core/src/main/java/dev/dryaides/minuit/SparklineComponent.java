package dev.dryaides.minuit;

import java.util.List;
import java.util.Objects;
import java.util.function.DoubleFunction;

/**
 * A tiny line or row of bars, to sit inline in a row, a card or a table cell. It has no frame, no axis and no
 * legend: the box is the plot. For anything to read off, use a {@link ChartComponent}.
 *
 * <pre>{@code
 * SparklineComponent tps = add(new SparklineComponent(at(new Box("tps", 10, 10, 60, 12)))
 *         .capacity(120).window(120).fixed(0, 20).unit(" TPS"));
 * tps.add(currentTps); // once a tick; does not allocate
 * }</pre>
 *
 * <p>The samples are kept in a hidden {@link ChartComponent}'s {@link ChartComponent.Series}, so the ring, the gaps
 * and the colour order are the chart's, and that chart is never drawn. NaN or an infinity is a gap. With more
 * samples than columns a column reaches the lowest and highest of its samples, so a spike is never lost.
 *
 * <p>Drawing allocates nothing: the columns are worked out once when the data or the size changes, and a frame
 * only draws them. The pointer lights a sample and its value comes as a tooltip. It takes no focus and no keys,
 * since it only shows; give the row around it the focus.
 */
public class SparklineComponent extends BaseComponent {
    /** How the samples are drawn. */
    public enum Mode { LINE, AREA, BAR }

    private static final int AREA_ALPHA = 0x48;
    private static final int LAST_ROOM = 1;

    // Holds the samples and names the colour. Never added to a panel and never drawn.
    private final ChartComponent store = new ChartComponent(new Box("sparkline", 0, 0, 0, 0));
    private final ChartComponent.Series series = store.addSeries("");

    private Mode mode = Mode.LINE;
    private int window;
    private boolean lastDot = true;
    private boolean hasFixed;
    private double fixedLo;
    private double fixedHi;
    private boolean trend;
    private int customColor;
    private boolean hasColor;
    private String unit = "";
    private DoubleFunction<String> valueFormat;

    private boolean dirty = true;
    private int laidW = -1;
    private int laidH = -1;
    private long viewStart;
    private int span;
    private double lo;
    private double hi;
    private boolean any;
    private int plotW;
    private int plotH;
    private int inset;
    private int[] top = new int[0];
    private int[] bottom = new int[0];
    private int baseRow;
    private int lastColumn = -1;
    private int lastRow = -1;

    private Palette colourFor;
    private boolean colourActive;
    private boolean colourUp;
    private int colourVersion;
    private int version;
    private int colourInk;

    private long hoverSample = -1L;
    private boolean smooth;
    private final Smooth smoothLine = new Smooth();
    private final Smooth smoothArea = new Smooth();
    private long refreshes;
    private long smoothKey = Long.MIN_VALUE;
    private boolean smoothShown;
    static final int SMOOTH_BUDGET = 350;
    private long tipSample = -1L;
    private List<String> tip;

    public SparklineComponent(Box box) {
        super(box);
    }

    // ---- setup ----

    /** {@link Mode#LINE} by default. */
    public SparklineComponent mode(Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        dirty = true;
        return this;
    }

    /** How many samples are kept. 256 by default. */
    public SparklineComponent capacity(int capacity) {
        store.capacity(capacity);
        dirty = true;
        return this;
    }

    /** How many samples the width spans, the newest on the right. 0, the default, fits the samples kept. */
    public SparklineComponent window(int window) {
        this.window = Math.max(0, window);
        dirty = true;
        return this;
    }

    /** Keeps the height on this range and cuts what is outside. By default it fits the samples in view. */
    public SparklineComponent fixed(double min, double max) {
        double low = Math.min(min, max);
        double high = Math.max(min, max);
        hasFixed = Double.isFinite(low) && Double.isFinite(high) && high > low;
        fixedLo = low;
        fixedHi = high;
        dirty = true;
        return this;
    }

    /** False for no dot on the newest sample. On by default; bars have none. */
    public SparklineComponent lastDot(boolean lastDot) {
        this.lastDot = lastDot;
        dirty = true;
        return this;
    }

    /** True for the theme's good colour when the last sample is not below the first, and its bad one otherwise. */
    public SparklineComponent trend(boolean trend) {
        this.trend = trend;
        version++;
        return this;
    }

    /** A colour of your own in place of the theme's. */
    public SparklineComponent color(int color) {
        customColor = color;
        hasColor = true;
        version++;
        return this;
    }

    /** Follows the value in the tooltip, as in {@code " ms"}. */
    public SparklineComponent unit(String unit) {
        this.unit = unit == null ? "" : unit;
        tipSample = -1L;
        return this;
    }

    /** Writes the value in the tooltip, in place of the built-in number. */
    public SparklineComponent valueFormat(DoubleFunction<String> valueFormat) {
        this.valueFormat = valueFormat;
        tipSample = -1L;
        return this;
    }

    /**
     * A smooth line or area, off by default: drawn at screen-pixel size with see-through edges. Bars do not change. It
     * costs rectangles, about three to six times the blocky line's; past {@value #SMOOTH_BUDGET} times the GUI scale it takes fewer edge
     * levels, then GUI units, then the blocky line. Worked out again only when a sample comes or the size changes.
     */
    public SparklineComponent smooth(boolean smooth) {
        this.smooth = smooth;
        smoothKey = Long.MIN_VALUE;
        return this;
    }

    /** Rectangles the last smooth picture took, 0 when blocky. */
    int smoothRects() {
        return smoothShown ? smoothLine.rects() + smoothArea.rects() : 0;
    }

    public SparklineComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public SparklineComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- content ----

    /** Appends a sample. Does not allocate. */
    public SparklineComponent add(double value) {
        series.add(value);
        dirty = true;
        return this;
    }

    /** Replaces every sample. */
    public SparklineComponent set(double... values) {
        series.set(values);
        dirty = true;
        tipSample = -1L;
        return this;
    }

    public SparklineComponent clear() {
        series.clear();
        dirty = true;
        tipSample = -1L;
        return this;
    }

    /** How many samples are held. */
    public int size() {
        return series.size();
    }

    /** The newest sample, or NaN. */
    public double latest() {
        return series.latest();
    }

    /** The lowest sample in view, or NaN. */
    public double low() {
        refresh();
        return any ? lo : Double.NaN;
    }

    /** The highest sample in view, or NaN. */
    public double high() {
        refresh();
        return any ? hi : Double.NaN;
    }

    /** The number of the sample at a pointer's x, counting from the first ever added, or -1. */
    long sampleAt(double mx) {
        refresh();
        if (plotW <= 0 || span <= 0 || series.total() == 0L) {
            return -1L;
        }
        int column = Math.max(0, Math.min(plotW - 1, (int) Math.floor(mx) - x - inset));
        int index;
        if (mode == Mode.BAR && !dense()) {
            index = Math.floorDiv(column - barsLeft(), slot());
        } else {
            index = plotW <= 1 ? 0 : (int) Math.round(column * (span - 1D) / (plotW - 1D));
        }
        long number = viewStart + Math.max(0, Math.min(span - 1, index));
        return Math.max(Math.max(0L, series.total() - series.size()), Math.min(series.total() - 1L, number));
    }

    /** The topmost row a column draws, from the box's top, or -1 when it draws nothing. For a test. */
    int columnTop(int column) {
        refresh();
        return column < 0 || column >= top.length ? -1 : top[column];
    }

    /** The row under the topmost a column draws, from the box's top, or -1. For a test. */
    int columnBottom(int column) {
        refresh();
        return column < 0 || column >= bottom.length ? -1 : bottom[column];
    }

    /** The sample the pointer lit in the last frame, or -1. */
    long hoverSample() {
        return hoverSample;
    }

    // ---- working it out ----

    private boolean dense() {
        return span > plotW;
    }

    private int slot() {
        return Math.max(1, plotW / Math.max(1, span));
    }

    private int barsLeft() {
        return (plotW - slot() * span) / 2;
    }

    private void refresh() {
        if (!dirty && laidW == w && laidH == h) {
            return;
        }
        dirty = false;
        refreshes++;
        laidW = w;
        laidH = h;
        boolean dot = lastDot && mode != Mode.BAR;
        inset = dot && w > 8 ? LAST_ROOM : 0;
        plotW = Math.max(0, w - 2 * inset);
        int rowInset = dot && h > 6 ? LAST_ROOM : 0;
        plotH = Math.max(0, h - 2 * rowInset);
        long total = series.total();
        long held = series.size();
        span = window > 0 ? window : (int) Math.max(1L, held);
        viewStart = total - span;
        scan(total, held);
        if (top.length != plotW) {
            top = new int[plotW];
            bottom = new int[plotW];
        }
        lastColumn = -1;
        lastRow = -1;
        if (plotW <= 0 || plotH <= 0) {
            return;
        }
        baseRow = rowOf(Math.max(lo, Math.min(hi, 0D))) + rowInset;
        if (mode == Mode.BAR && !dense()) {
            buildBars(rowInset);
        } else if (mode == Mode.BAR) {
            buildDenseBars(rowInset);
        } else {
            buildLine(rowInset);
        }
        double newest = series.latest();
        if (dot && newest == newest) {
            lastColumn = columnOf(span - 1);
            lastRow = rowOf(newest) + rowInset;
        }
    }

    private void scan(long total, long held) {
        any = false;
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (long n = Math.max(viewStart, total - held); n < total; n++) {
            double v = series.at(n);
            if (v == v) {
                any = true;
                low = Math.min(low, v);
                high = Math.max(high, v);
            }
        }
        if (hasFixed) {
            lo = fixedLo;
            hi = fixedHi;
            return;
        }
        if (!any) {
            lo = 0D;
            hi = 1D;
            return;
        }
        if (mode != Mode.LINE) {
            low = Math.min(low, 0D);
            high = Math.max(high, 0D);
        }
        lo = low;
        hi = high;
    }

    private int columnOf(int index) {
        if (span <= 1 || plotW <= 1) {
            return plotW / 2;
        }
        return (int) Math.round(index * (plotW - 1D) / (span - 1D));
    }

    /** A flat line sits in the middle. What a fixed range cuts stops at the edge. */
    private int rowOf(double value) {
        int rows = Math.max(0, plotH - 1);
        double range = hi - lo;
        if (range <= 0D) {
            return rows / 2;
        }
        double at = Math.max(0D, Math.min(1D, (value - lo) / range));
        return rows - (int) Math.round(at * rows);
    }

    /** {@link #rowOf} before rounding: row {@code n} is centred on {@code n}. */
    private double rowAt(double value) {
        int rows = Math.max(0, plotH - 1);
        double range = hi - lo;
        if (range <= 0D) {
            return rows / 2;
        }
        return rows - Math.max(0D, Math.min(1D, (value - lo) / range)) * rows;
    }

    private double place(double at) {
        int index = (int) Math.floor(at);
        double part = at - index;
        double a = series.at(viewStart + index);
        if (part < 1e-9D) {
            return a;
        }
        double b = series.at(viewStart + index + 1L);
        return a == a && b == b ? a + (b - a) * part : Double.NaN;
    }

    /** As the chart does: a one-pixel line laid by {@link PixelArt#lineTop}, samples inside a column added whole. */
    private void buildLine(int rowInset) {
        double per = plotW <= 1 ? 0D : (span - 1D) / (plotW - 1D);
        for (int c = 0; c < plotW; c++) {
            top[c] = -1;
            bottom[c] = -1;
            double from;
            double to;
            double centre;
            if (span <= 1) {
                if (c != plotW / 2) {
                    continue;
                }
                from = 0D;
                to = 0D;
                centre = 0D;
            } else {
                centre = c * per;
                from = Math.max(0D, centre - 0.5D * per);
                to = Math.min(span - 1D, centre + 0.5D * per);
            }
            double high = Double.NaN;
            double low = Double.NaN;
            int last = (int) Math.floor(to);
            boolean closes = c == plotW - 1;
            for (int k = (int) Math.ceil(from); k <= last; k++) {
                if (k == to && !closes && span > 1) {
                    continue;
                }
                double v = series.at(viewStart + k);
                if (v == v) {
                    double row = rowAt(v);
                    high = high == high ? Math.min(high, row) : row;
                    low = low == low ? Math.max(low, row) : row;
                }
            }
            double middle = place(centre);
            middle = middle == middle ? rowAt(middle) : high;
            if (middle != middle) {
                continue;
            }
            double left = place(from);
            double right = place(to);
            left = left == left ? rowAt(left) : Double.NaN;
            right = right == right ? rowAt(right) : Double.NaN;
            top[c] = PixelArt.lineTop(left, middle, right, high) + rowInset;
            bottom[c] = PixelArt.lineBottom(left, middle, right, low) + rowInset;
        }
    }

    private void buildBars(int rowInset) {
        int slot = slot();
        int gap = slot >= 3 ? 1 : 0;
        int left = barsLeft();
        for (int c = 0; c < plotW; c++) {
            top[c] = -1;
            bottom[c] = -1;
            int within = c - left;
            if (within < 0 || within / slot >= span || within % slot >= slot - gap) {
                continue;
            }
            double v = series.at(viewStart + within / slot);
            if (v == v) {
                int row = rowOf(v) + rowInset;
                top[c] = Math.min(row, baseRow);
                bottom[c] = Math.max(row, baseRow) + 1;
            }
        }
    }

    private void buildDenseBars(int rowInset) {
        double base = Math.max(lo, Math.min(hi, 0D));
        for (int c = 0; c < plotW; c++) {
            long from = viewStart + (long) c * span / plotW;
            long to = viewStart + (long) (c + 1) * span / plotW;
            boolean found = false;
            double far = base;
            for (long n = from; n < to; n++) {
                double v = series.at(n);
                if (v == v) {
                    found = true;
                    if (Math.abs(v - base) >= Math.abs(far - base)) {
                        far = v;
                    }
                }
            }
            if (found) {
                int row = rowOf(far) + rowInset;
                top[c] = Math.min(row, baseRow);
                bottom[c] = Math.max(row, baseRow) + 1;
            } else {
                top[c] = -1;
                bottom[c] = -1;
            }
        }
    }

    // ---- drawing ----

    /** Worked out when something it hangs on changes: mixing colours in a frame would allocate. */
    private int colour(Theme t) {
        Palette p = t.palette();
        boolean up = firstInView() <= series.latest();
        if (colourFor == p && colourActive == active && colourUp == up && colourVersion == version) {
            return colourInk;
        }
        colourFor = p;
        colourActive = active;
        colourUp = up;
        colourVersion = version;
        int color;
        if (hasColor) {
            color = SeriesColors.custom(p, customColor, false);
        } else if (trend) {
            color = Ink.readable(p, up ? p.good() : p.bad(), DataLook.back(p), Ink.MARK);
        } else {
            color = DataLook.opaque(p.accent());
        }
        colourInk = active ? color : DataLook.faded(p, color);
        return colourInk;
    }

    private double firstInView() {
        for (long n = Math.max(viewStart, series.total() - series.size()); n < series.total(); n++) {
            double v = series.at(n);
            if (v == v) {
                return v;
            }
        }
        return Double.NaN;
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        refresh();
        hoverSample = -1L;
        if (plotW <= 0 || plotH <= 0) {
            return;
        }
        Palette p = t.palette();
        int color = colour(t);
        int left = x + inset;
        if (!any) {
            // Nothing yet: a quiet line where the data will be, so the row does not look broken.
            int mid = y + h / 2;
            d.fill(left, mid, left + plotW, mid + 1, DataLook.wash(p));
            return;
        }
        boolean lit = active && hovered && settled();
        if (lit) {
            hoverSample = sampleAt(mx);
            drawHoverBand(d, p, left);
        }
        if (smooth && mode != Mode.BAR && buildSmooth(Smooth.scale(d), color)) {
            smoothArea.emit(d, left, y);
            smoothLine.emit(d, left, y);
            return;
        }
        smoothShown = false;
        if (mode == Mode.AREA) {
            int wash = Shapes.alpha(color, AREA_ALPHA);
            for (int c = 0; c < plotW; c++) {
                if (bottom[c] != -1 && bottom[c] <= baseRow) {
                    d.fill(left + c, y + bottom[c], left + c + 1, y + baseRow + 1, wash);
                }
            }
        }
        for (int c = 0; c < plotW; c++) {
            if (bottom[c] != -1) {
                d.fill(left + c, y + top[c], left + c + 1, y + bottom[c], color);
                boolean alone = mode != Mode.BAR && (c == 0 || bottom[c - 1] == -1) && (c == plotW - 1 || bottom[c + 1] == -1);
                if (alone && h > 4) {
                    // A sample with a gap on both sides has no line to show: one pixel would pass for dirt.
                    d.fill(left + c - 1, y + top[c] - 1, left + c + 2, y + top[c] + 2, color);
                }
            }
        }
        if (lastColumn >= 0) {
            d.fill(left + lastColumn - 1, y + lastRow - 1, left + lastColumn + 2, y + lastRow + 2, color);
        }
    }

    private boolean buildSmooth(int gs, int color) {
        long key = ((refreshes * 31L + gs) * 31L + color) * 31L + mode.ordinal();
        if (key == smoothKey) {
            return smoothShown;
        }
        smoothKey = key;
        int[] ladder = Smooth.ladder(gs);
        smoothShown = false;
        for (int step = 0; step < ladder.length; step += 2) {
            int sub = ladder[step];
            paintSmooth(sub, ladder[step + 1], color);
            if (smoothLine.rects() + smoothArea.rects() <= SMOOTH_BUDGET * Smooth.scaleOf(gs)) {
                smoothShown = true;
                return true;
            }
        }
        return false;
    }

    private double yAt(double value, int rowInset) {
        int rows = Math.max(0, plotH - 1);
        double range = hi - lo;
        double at = range <= 0D ? 0.5D : Math.max(0D, Math.min(1D, (value - lo) / range));
        return rows - at * rows + rowInset + 0.5D;
    }

    private double xAt(double index) {
        return span <= 1 || plotW <= 1 ? plotW / 2 + 0.5D : index * (plotW - 1D) / (span - 1D) + 0.5D;
    }

    private void paintSmooth(int sub, int levels, int color) {
        int rowInset = (h - plotH) / 2;
        int fw = plotW * sub;
        int fh = h * sub;
        smoothLine.begin(fw + 2 * sub * inset, fh, sub, levels);
        smoothArea.begin(mode == Mode.AREA ? fw : 0, mode == Mode.AREA ? fh : 0, sub, levels);
        double px = 0D;
        double py = 0D;
        boolean had = false;
        boolean lone = false;
        double r = 0.5D * sub;
        double dot = h > 4 ? 1.5D * sub : r;
        for (int k = 0; k < span; k++) {
            double v = series.at(viewStart + k);
            if (v != v) {
                if (had && lone) {
                    smoothLine.disc(px, py, dot);
                }
                had = false;
                continue;
            }
            double cx = xAt(k) * sub;
            double cy = yAt(v, rowInset) * sub;
            if (had) {
                smoothLine.capsule(px, py, cx, cy, r);
                lone = false;
            } else {
                lone = true;
            }
            px = cx;
            py = cy;
            had = true;
        }
        if (had && lone) {
            smoothLine.disc(px, py, dot);
        }
        if (lastColumn >= 0) {
            smoothLine.disc(xAt(span - 1) * sub, yAt(series.latest(), rowInset) * sub, 1.5D * sub);
        }
        smoothLine.paint(color);
        if (mode == Mode.AREA) {
            double base = (baseRow + 1D) * sub;
            double per = plotW <= 1 ? 0D : (span - 1D) / (plotW - 1D);
            for (int fx = 0; fx < fw; fx++) {
                double at = span <= 1 ? 0D : ((fx + 0.5D) / sub - 0.5D) * per;
                if (at < 0D || at > span - 1D) {
                    continue;
                }
                double v = place(at);
                if (v == v) {
                    smoothArea.column(fx, yAt(v, rowInset) * sub, base, 1F);
                }
            }
            smoothArea.paint(Shapes.alpha(color, AREA_ALPHA));
        }
    }

    private void drawHoverBand(Draw d, Palette p, int left) {
        int index = (int) (hoverSample - viewStart);
        int from;
        int width;
        if (mode == Mode.BAR && !dense()) {
            from = barsLeft() + index * slot();
            width = slot();
        } else {
            from = columnOf(index);
            width = 1;
        }
        d.fill(left + from, y, left + from + width, y + h, RowLook.tint(p, 1F));
    }

    // ---- tooltips ----

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!visible || !active || !isOver(mx, my)) {
            return super.tooltipAt(mx, my);
        }
        long sample = sampleAt(mx);
        double v = sample < 0L ? Double.NaN : series.at(sample);
        if (v != v) {
            return super.tooltipAt(mx, my);
        }
        if (sample != tipSample || tip == null) {
            tipSample = sample;
            tip = List.of((valueFormat != null ? valueFormat.apply(v) : DataLook.number(v)) + unit);
        }
        return tip;
    }
}
