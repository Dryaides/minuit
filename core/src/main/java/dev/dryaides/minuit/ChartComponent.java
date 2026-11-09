package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.DoubleFunction;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

/**
 * A small data chart drawn with rectangles only: lines, filled areas or bars, one or several series, in the
 * theme's colours, with round-number ticks, a legend and a crosshair with a readout.
 *
 * <pre>{@code
 * // A static chart: the samples fill the width.
 * ChartComponent sales = add(new ChartComponent(at(new Box("sales", 10, 10, 200, 90)))
 *         .mode(ChartComponent.Mode.BAR)
 *         .title(words.get("mymod.sales"))
 *         .categories(List.of("Mon", "Tue", "Wed")));
 * sales.addSeries(words.get("mymod.iron")).set(4, 9, 6);
 * sales.addSeries(words.get("mymod.gold")).set(1, 3, 2);
 *
 * // A live graph: the newest sample is on the right. Series.add does not allocate.
 * ChartComponent fps = add(new ChartComponent(at(new Box("fps", 10, 10, 200, 60))).live(240).unit(" fps"));
 * ChartComponent.Series line = fps.addSeries("FPS");
 * line.add(currentFps); // once a frame or once a tick
 * }</pre>
 *
 * <h2>Samples</h2>
 * Every series holds a ring of its last {@link #capacity(int)} samples, and a sample's place on the x axis is its
 * number since the series began: sample 5 of every series lines up. NaN or an infinity is a gap: the line
 * stops, the bar is missing, and a sample with a gap on both sides shows as a dot. With more samples than
 * pixel columns, a column draws the lowest to the highest of its samples, so a spike is never lost.
 *
 * <h2>The y axis</h2>
 * {@link Scale#AUTO} fits what is in view, {@link Scale#ZERO} also keeps 0 in view, {@link Scale#HOLD} never
 * shrinks until {@link #resetHold()}, and {@link #fixed(double, double)} stays put and cuts what is outside.
 * {@link #floor(double)} and {@link #ceiling(double)} pin one end. Areas and bars always include 0 unless
 * the range is fixed or pinned, since their length is what is read. When the range changes, the
 * axis glides there; under {@link BaseComponent#reducedMotion} it jumps.
 *
 * <h2>Input</h2>
 * The pointer over the plot shows the crosshair and a readout, inside the box. In a box too small to hold
 * the readout the same lines come through {@link #tooltipAt} as a normal tooltip. A click on a legend entry
 * hides or shows that series; its colour does not move to another. With focus: left, right, home and end walk
 * the samples with the same readout, up and down light a legend entry, Enter or Space toggles that entry
 * or else calls {@link #onSelect(LongConsumer)}, and Escape puts the keys away.
 *
 * <p>Boxes that are too small drop the axes and the header, then draw only the series. The theme gives eight
 * colours; a ninth series reuses the first, so past that use {@link Series#color(int)} or fewer series. Not
 * thread safe: add samples on the render thread. Only a direct child of a panel has been tried.
 */
public class ChartComponent extends BaseComponent {
    /** How the samples are drawn. */
    public enum Mode { LINE, AREA, BAR }

    /** How the y axis follows the data. */
    public enum Scale { AUTO, ZERO, HOLD, FIXED }

    /** One line of samples, in the chart's fixed colour order. Get one from {@link #addSeries(String)}. */
    public final class Series {
        private final String name;
        private final int order;
        private double[] data = new double[capacity];
        private long total;
        private int size;
        private boolean shown = true;
        private boolean customColor;
        private int color;
        private int[] top = new int[0];
        private int[] bottom = new int[0];

        private Series(String name, int order) {
            this.name = name == null ? "" : name;
            this.order = order;
        }

        /** Appends a sample, overwriting the oldest once the ring is full. NaN or an infinity is a gap. Does not allocate. */
        public Series add(double value) {
            double kept = clean(value);
            data[(int) (total % data.length)] = kept;
            total++;
            size = Math.min(size + 1, data.length);
            // Between two frames a sample can come and go: the widest range is kept as it arrives, not as it is drawn.
            if (scale == Scale.HOLD && shown && kept == kept) {
                heldLo = heldLo == heldLo ? Math.min(heldLo, kept) : kept;
                heldHi = heldHi == heldHi ? Math.max(heldHi, kept) : kept;
            }
            changed();
            return this;
        }

        /** Appends a gap. */
        public Series gap() {
            return add(Double.NaN);
        }

        /** Replaces every sample. The chart's capacity grows when these are more. */
        public Series set(double... values) {
            int count = values == null ? 0 : values.length;
            if (count > data.length) {
                capacity(count);
            }
            for (int i = 0; i < count; i++) {
                data[i % data.length] = clean(values[i]);
            }
            total = count;
            size = count;
            changed();
            return this;
        }

        /** Forgets every sample. */
        public Series clear() {
            total = 0L;
            size = 0;
            changed();
            return this;
        }

        /** A colour of your own in place of the theme's. */
        public Series color(int color) {
            this.color = color;
            this.customColor = true;
            return this;
        }

        /** Hides or shows this series, without calling back. */
        public Series shown(boolean shown) {
            this.shown = shown;
            changed();
            return this;
        }

        public boolean shown() {
            return shown;
        }

        public String name() {
            return name;
        }

        /** How many samples were ever added since the last clear: the next sample's number. */
        public long total() {
            return total;
        }

        /** How many samples the ring still holds. */
        public int size() {
            return size;
        }

        /** The newest sample, or NaN. */
        public double latest() {
            return at(total - 1L);
        }

        /** A sample by its number, or NaN when it is a gap, was overwritten or has not come yet. */
        public double at(long number) {
            if (number < 0L || number >= total || number < total - size) {
                return Double.NaN;
            }
            return data[(int) (number % data.length)];
        }

        private void resize(int length) {
            double[] next = new double[length];
            int keep = Math.min(size, length);
            for (long n = total - keep; n < total; n++) {
                next[(int) (n % length)] = data[(int) (n % data.length)];
            }
            data = next;
            size = keep;
        }
    }

    /** Height of the row that holds the title and the legend. */
    public static final int HEADER = 10;

    private static final double HUGE = 1e300D;
    private static final int BORDER = 1;
    /** Room between the border and what sits inside it. */
    private static final int SIDE = 3;
    /** The focus ring takes two of these, and keeps a column clear of the labels. */
    private static final int LABEL_GAP = 4;
    /** A tick's label is centred on its line, so half a label stays free above the plot and below it. */
    private static final int LABEL_HALF = 5;
    private static final int LABEL_RISE = 3;
    private static final int HEADER_GAP = 2;
    private static final int TICK_ROOM = 14;
    private static final int MAX_TICKS = 9;
    private static final int MAX_X_LABELS = 16;
    private static final int X_LABEL_GAP = 6;
    /** A label may be cut down to this before some labels are left out. */
    private static final int MIN_X_LABEL = 36;
    private static final int LEGEND_GAP = 6;
    private static final int SWATCH = 5;
    private static final int SWATCH_GAP = 2;
    private static final int MIN_LEGEND_TEXT = 12;
    private static final int LINE = 10;
    private static final int MAX_BAR = 16;
    private static final int POINT_ROOM = 5;

    private static final int MIN_AXES_W = 64;
    private static final int MIN_AXES_H = 30;
    private static final int MIN_HEADER_W = 60;
    private static final int MIN_HEADER_H = 44;
    private static final int MIN_X_LABELS_H = 48;

    private static final int GRID_ALPHA = 0x24;
    private static final int ZERO_ALPHA = 0x70;
    private static final int AREA_ALPHA = 0x50;
    /**
     * Most rectangles a smooth plot may take per unit of GUI scale before it gives way to a coarser step: an edge
     * costs about one rectangle per screen pixel, so the screen-pixel step of the bench's 118 x 82 plot fits.
     */
    static final int SMOOTH_BUDGET = 950;
    private static final int CROSS_ALPHA = 0xA0;
    private static final int CROSS_SOFT_ALPHA = 0x50;
    private static final float DISABLED_FADE = 0.55F;
    private static final float HIDDEN_FADE = 0.5F;

    /** How fast the axis closes on a new range: a time constant, in milliseconds. */
    private static final double GLIDE_MS = 60D;

    private static final int CARD_GAP = 6;
    private static final int CARD_MARGIN = 2;
    private static final int MIN_CARD_INSET = 3;
    private static final int MAX_CARD_INSET = 4;
    private static final int CARD_COLUMN_GAP = 6;
    private static final int MIN_CARD_NAME = 18;
    private static final int MIN_CARD_LIMIT = 90;

    private static final long NONE = Long.MIN_VALUE;

    private final List<Series> series = new ArrayList<>();
    private final RowLook.HoverFade legendHover = new RowLook.HoverFade();

    private Mode mode = Mode.LINE;
    private Scale scale = Scale.AUTO;
    private double fixedLo = Double.NaN;
    private double fixedHi = Double.NaN;
    private double floor = Double.NaN;
    private double ceiling = Double.NaN;
    private int capacity = 256;
    private int window;
    private int nextOrder;
    private boolean axes = true;
    private boolean points;
    private boolean smooth;
    private final List<Smooth> smoothAreas = new ArrayList<>();
    private final List<Smooth> smoothLines = new ArrayList<>();
    private long columnsBuilt;
    private long smoothKey = Long.MIN_VALUE;
    private boolean smoothShown;
    private int smoothRects;
    private Boolean legend;
    private String title = "";
    private String emptyText;
    private String unit = "";
    private DoubleFunction<String> valueFormat;
    private LongFunction<String> xLabel;
    private LongConsumer onSelect;
    private BiConsumer<Series, Boolean> onToggle;

    // What the data says, refreshed when it changes.
    private boolean dataDirty = true;
    private boolean layoutDirty = true;
    private boolean columnsDirty = true;
    private long version;
    private long newest;
    private long viewStart;
    private int win = 1;
    private int shownCount;
    private boolean hasData;
    private double dataMin;
    private double dataMax;
    private double heldLo = Double.NaN;
    private double heldHi = Double.NaN;

    // The axis: where it is going and where it is. Ticks are only rebuilt when the target moves.
    private boolean hasRange;
    private double targetLo;
    private double targetHi;
    private double shownLo;
    private double shownHi;
    private boolean shownSet;
    private long lastGlide = NONE;
    private double columnsLo;
    private double columnsHi;
    private final double[] tickValue = new double[MAX_TICKS + 2];
    private final String[] tickText = new String[MAX_TICKS + 2];
    private int tickCount;
    private int tickRoom = -1;
    private double tickFromLo = Double.NaN;
    private double tickFromHi = Double.NaN;
    private int labelW;

    // Layout, relative to the box, so that a moved box needs none of it again.
    private int laidW = -1;
    private int laidH = -1;
    private int plotL;
    private int plotT;
    private int plotW;
    private int plotH;
    private boolean showY;
    private boolean showXLabels;
    private boolean showHeader;
    private boolean showLegend;
    private int titleW;
    private int[] legendL = new int[0];
    private int[] legendW = new int[0];

    private final long[] xTickSample = new long[MAX_X_LABELS];
    private final String[] xTickText = new String[MAX_X_LABELS];
    private final int[] xTickW = new int[MAX_X_LABELS];
    private int xTickCount;

    private Palette colourFor;
    private SeriesPalette seriesPalette = SeriesPalette.THEME;
    private boolean inkActive;
    private int ink;
    private int softInk;
    private int cardInk;
    private int back;

    // The pointer and the keys.
    private long cursor = NONE;
    private int litLegend = -1;
    private long crossSample = NONE;
    private boolean crossByPointer;
    private int crossMy;
    private long crossSince = NONE;

    // The readout, rebuilt when what it says changes.
    private long cardSample = NONE;
    private long cardVersion = -1L;
    private String cardHead;
    private String[] cardNames = new String[0];
    private String[] cardValues = new String[0];
    private int[] cardSeries = new int[0];
    private String cardMore = "";
    private int cardMoreCount;
    private int cardRows;
    private int cardNameW;
    private int cardValueW;
    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;
    private boolean cardDrawn;

    public ChartComponent(Box box) {
        super(box);
    }

    // ---- setup ----

    /** The colour scheme of the series without a colour of their own; {@link SeriesPalette#THEME} by default. */
    @Incubating
    public ChartComponent seriesPalette(SeriesPalette seriesPalette) {
        this.seriesPalette = Objects.requireNonNull(seriesPalette, "seriesPalette");
        dirtyAll();
        return this;
    }

    /** {@link Mode#LINE} by default. */
    public ChartComponent mode(Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        dirtyAll();
        return this;
    }

    /** {@link Scale#AUTO} by default. {@link Scale#FIXED} needs {@link #fixed(double, double)}, which sets it. */
    public ChartComponent scale(Scale scale) {
        this.scale = Objects.requireNonNull(scale, "scale");
        dirtyAll();
        return this;
    }

    /** Keeps the axis on this range and cuts what falls outside. The ends are swapped if need be. */
    public ChartComponent fixed(double min, double max) {
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        if (Double.isFinite(lo) && Double.isFinite(hi) && hi > lo) {
            fixedLo = lo;
            fixedHi = hi;
            scale = Scale.FIXED;
            dirtyAll();
        }
        return this;
    }

    /** Pins the low end of the axis; NaN lets it follow the data again. */
    public ChartComponent floor(double floor) {
        this.floor = floor;
        dirtyAll();
        return this;
    }

    /** Pins the high end of the axis; NaN lets it follow the data again. */
    public ChartComponent ceiling(double ceiling) {
        this.ceiling = ceiling;
        dirtyAll();
        return this;
    }

    /** With {@link Scale#HOLD}: forgets the widest range seen. */
    public void resetHold() {
        heldLo = Double.NaN;
        heldHi = Double.NaN;
        dirtyAll();
    }

    /** How many samples each series keeps. 256 by default. The newest are kept when it shrinks. */
    public ChartComponent capacity(int capacity) {
        int next = Math.max(1, capacity);
        if (next != this.capacity) {
            this.capacity = next;
            for (int i = 0; i < series.size(); i++) {
                series.get(i).resize(next);
            }
            dirtyAll();
        }
        return this;
    }

    /** How many samples the x axis spans, the newest on the right. 0, the default, fits the samples kept. */
    public ChartComponent window(int window) {
        this.window = Math.max(0, window);
        dirtyAll();
        return this;
    }

    /** For a live graph: keeps this many samples and spans them all, so the line comes in from the right. */
    public ChartComponent live(int samples) {
        capacity(samples);
        return window(samples);
    }

    /** False for no ticks and no labels at all. On by default; a tiny box drops them by itself. */
    public ChartComponent axes(boolean axes) {
        this.axes = axes;
        dirtyAll();
        return this;
    }

    /** True for a dot on every sample where they are far enough apart. Off by default. */
    /**
     * Smooth lines and areas, off by default: drawn at screen-pixel size with see-through edges, instead of whole GUI
     * pixels. Bars are rectangles already and do not change. It costs rectangles: a line takes about three to six
     * times the blocky one's, an area more; past {@value #SMOOTH_BUDGET} times the GUI scale the picture takes fewer edge levels, then GUI
     * units with see-through edges, then the blocky look. The picture is kept until the data, the range or the size
     * changes, so a live chart works it out every frame a sample comes.
     */
    public ChartComponent smooth(boolean smooth) {
        this.smooth = smooth;
        smoothKey = Long.MIN_VALUE;
        return this;
    }

    public boolean smooth() {
        return smooth;
    }

    /** Rectangles the last smooth plot took, 0 when blocky. */
    int smoothRects() {
        return smoothShown ? smoothRects : 0;
    }

    public ChartComponent points(boolean points) {
        this.points = points;
        return this;
    }

    /** Says whether the legend shows. Unset, it shows for two series or more: the title names a lone one. */
    public ChartComponent legend(boolean legend) {
        this.legend = legend;
        dirtyAll();
        return this;
    }

    /** The text at the top left. */
    public ChartComponent title(String title) {
        this.title = title == null ? "" : title;
        dirtyAll();
        return this;
    }

    /** What shows with nothing to draw. */
    public ChartComponent emptyText(String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    /** Follows every value in the readout, as in {@code " ms"}. */
    public ChartComponent unit(String unit) {
        this.unit = unit == null ? "" : unit;
        cardVersion = -1L;
        return this;
    }

    /** Writes the values, on the axis and in the readout, in place of the built-in numbers. */
    public ChartComponent valueFormat(DoubleFunction<String> valueFormat) {
        this.valueFormat = valueFormat;
        tickFromLo = Double.NaN;
        dirtyAll();
        return this;
    }

    /** Names a sample by its number, for the labels under the plot and the readout's first line. */
    public ChartComponent xLabel(LongFunction<String> xLabel) {
        this.xLabel = xLabel;
        dirtyAll();
        return this;
    }

    /** A name for each sample by its number: shorthand for {@link #xLabel(LongFunction)}. */
    public ChartComponent categories(List<String> names) {
        List<String> copy = Lists.labels(names);
        return xLabel(copy.isEmpty() ? null : n -> n >= 0L && n < copy.size() ? copy.get((int) n) : "");
    }

    /** Called with a sample's number on a click in the plot, or on Enter or Space with the keys on a sample. */
    public ChartComponent onSelect(LongConsumer onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /** Called with the series and whether it now shows, when the player toggles it. */
    public ChartComponent onToggle(BiConsumer<Series, Boolean> onToggle) {
        this.onToggle = onToggle;
        return this;
    }

    public ChartComponent active(boolean active) {
        this.active = active;
        if (!active) {
            cursor = NONE;
            litLegend = -1;
        }
        return this;
    }

    @Override
    public ChartComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- content ----

    /** Adds a series, with the next colour of the theme's order. */
    public Series addSeries(String name) {
        Series s = new Series(name, nextOrder++);
        series.add(s);
        dirtyAll();
        return s;
    }

    public boolean removeSeries(Series s) {
        boolean removed = series.remove(s);
        if (removed) {
            litLegend = -1;
            dirtyAll();
        }
        return removed;
    }

    public int seriesCount() {
        return series.size();
    }

    public Series series(int index) {
        return series.get(index);
    }

    /** Forgets every sample of every series; the series stay. Also resets {@link #resetHold()}. */
    public void clear() {
        for (int i = 0; i < series.size(); i++) {
            series.get(i).clear();
        }
        cursor = NONE;
        resetHold();
    }

    public Mode mode() {
        return mode;
    }

    public int capacity() {
        return capacity;
    }

    // ---- what is showing, for a script or a test ----

    /** True when at least one shown series has a sample in view. */
    public boolean hasData() {
        refresh();
        return hasData;
    }

    /** The lowest sample in view, or NaN. */
    public double dataMin() {
        refresh();
        return hasData ? dataMin : Double.NaN;
    }

    /** The highest sample in view, or NaN. */
    public double dataMax() {
        refresh();
        return hasData ? dataMax : Double.NaN;
    }

    /** The bottom of the axis, once any glide has ended; NaN with nothing to draw. */
    public double rangeMin() {
        refresh();
        return hasRange ? targetLo : Double.NaN;
    }

    /** The top of the axis, once any glide has ended; NaN with nothing to draw. */
    public double rangeMax() {
        refresh();
        return hasRange ? targetHi : Double.NaN;
    }

    /** The bottom of the axis in the last frame: differs from {@link #rangeMin()} only during a glide. */
    double shownMin() {
        return shownLo;
    }

    /** The top of the axis in the last frame: differs from {@link #rangeMax()} only during a glide. */
    double shownMax() {
        return shownHi;
    }

    int tickCount() {
        refresh();
        return showY ? tickCount : 0;
    }

    double tickValue(int index) {
        refresh();
        return tickValue[index];
    }

    String tickText(int index) {
        refresh();
        return tickText[index];
    }

    /** How many labels are under the plot. */
    int xLabelCount() {
        refresh();
        return showXLabels ? xTickCount : 0;
    }

    String xLabelText(int index) {
        refresh();
        return xTickText[index];
    }

    /** Where the series are drawn. */
    Box plotBox() {
        refresh();
        return new Box("plot", x + plotL, y + plotT, plotW, plotH);
    }

    /** True when the title and legend row has room. */
    boolean headerShows() {
        refresh();
        return showHeader;
    }

    boolean legendShows() {
        refresh();
        return showLegend;
    }

    /** A legend entry's place. Empty when the legend does not show. */
    Box legendBox(int index) {
        refresh();
        if (!showLegend || index < 0 || index >= series.size()) {
            return new Box("legend" + index, 0, 0, 0, 0);
        }
        return new Box("legend" + index, x + legendL[index], y + headerTop(), legendW[index], HEADER);
    }

    /** The first sample number of the x axis. Negative while a live graph fills up. */
    long viewStart() {
        refresh();
        return viewStart;
    }

    /** How many samples the x axis spans. */
    int viewSpan() {
        refresh();
        return win;
    }

    /** The sample at a pointer's x: the nearest for lines, the one whose slot it is for bars. -1 with no samples. */
    long sampleAt(double mx) {
        refresh();
        return newest <= 0L || plotW <= 0 ? -1L : sampleAtX((int) Math.floor(mx));
    }

    /** The column a sample is drawn at. */
    int sampleX(long number) {
        refresh();
        return x + plotL + columnOf((int) (number - viewStart));
    }

    /** The row a value is drawn at, with the axis as it was in the last frame. */
    int valueY(double value) {
        return y + plotT + rowOf(value);
    }

    /** The sample under the crosshair in the last frame, or -1. */
    long crossSample() {
        return crossSample == NONE ? -1L : crossSample;
    }

    /** The sample the keys are on, or -1. */
    public long cursor() {
        return cursor == NONE ? -1L : cursor;
    }

    /** The legend entry the keys are on, or -1. */
    int litLegend() {
        return litLegend;
    }

    /** True when the last frame drew the readout. */
    boolean readoutShown() {
        return cardDrawn;
    }

    /** The readout's place in the last frame. */
    Box readoutBox() {
        return new Box("readout", cardX, cardY, cardW, cardH);
    }

    /** How far the crosshair has faded in, right now: from 0 to 1. Always 1 under reduced motion. */
    float crossFade() {
        if (crossSample == NONE) {
            return 0F;
        }
        if (reducedMotion || crossSince == NONE) {
            return 1F;
        }
        return Math.max(0F, Math.min(1F, (BaseComponent.clock.getAsLong() - crossSince) / (float) BaseComponent.HOVER_IN_MS));
    }

    /** The colour a series has in a theme: fixed by the order it was added in, whatever else is hidden. */
    public int seriesColor(Theme theme, Series s) {
        return s.customColor ? s.color | 0xFF000000 : SeriesColors.color(theme.palette(), seriesPalette, s.order);
    }

    // ---- data ----

    private static double clean(double value) {
        if (!Double.isFinite(value)) {
            return Double.NaN;
        }
        return Math.max(-HUGE, Math.min(HUGE, value));
    }

    private void changed() {
        version++;
        dataDirty = true;
    }

    private void dirtyAll() {
        version++;
        dataDirty = true;
        layoutDirty = true;
    }

    private boolean keysLegend() {
        return showLegend && series.size() > 1;
    }

    /** Brings everything up to date. Costs nothing when nothing changed, and allocates only when the words change. */
    private void refresh() {
        boolean sized = layoutDirty || laidW != w || laidH != h;
        if (!dataDirty && !sized) {
            return;
        }
        if (dataDirty) {
            scan();
        }
        layoutVertical();
        target();
        layoutHorizontal();
        if (showXLabels) {
            buildXLabels();
        } else {
            xTickCount = 0;
        }
        dataDirty = false;
        layoutDirty = false;
        columnsDirty = true;
        laidW = w;
        laidH = h;
    }

    private void scan() {
        newest = 0L;
        shownCount = 0;
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            newest = Math.max(newest, s.total);
            if (s.shown) {
                shownCount++;
            }
        }
        if (window > 0) {
            win = window;
        } else {
            win = (int) Math.max(1L, Math.min(newest, capacity));
        }
        viewStart = newest - win;
        hasData = false;
        dataMin = Double.POSITIVE_INFINITY;
        dataMax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            if (!s.shown) {
                continue;
            }
            for (long n = Math.max(viewStart, s.total - s.size); n < s.total; n++) {
                double v = s.data[(int) (n % s.data.length)];
                if (v == v) {
                    hasData = true;
                    if (v < dataMin) {
                        dataMin = v;
                    }
                    if (v > dataMax) {
                        dataMax = v;
                    }
                }
            }
        }
    }

    private void target() {
        boolean fixedOn = scale == Scale.FIXED && fixedLo == fixedLo;
        if (!fixedOn && !hasData) {
            hasRange = false;
            shownSet = false;
            tickCount = 0;
            tickFromLo = Double.NaN;
            return;
        }
        double lo;
        double hi;
        if (fixedOn) {
            lo = fixedLo;
            hi = fixedHi;
        } else {
            lo = dataMin;
            hi = dataMax;
            if (scale == Scale.HOLD) {
                heldLo = heldLo == heldLo ? Math.min(heldLo, lo) : lo;
                heldHi = heldHi == heldHi ? Math.max(heldHi, hi) : hi;
                lo = heldLo;
                hi = heldHi;
            }
        }
        boolean pinLo = fixedOn || Double.isFinite(floor);
        boolean pinHi = fixedOn || Double.isFinite(ceiling);
        if (!fixedOn) {
            boolean withZero = scale == Scale.ZERO || mode != Mode.LINE;
            if (withZero) {
                lo = Math.min(lo, 0D);
                hi = Math.max(hi, 0D);
            }
            if (pinLo) {
                lo = floor;
                hi = Math.max(hi, lo);
            }
            if (pinHi) {
                hi = ceiling;
                lo = Math.min(lo, hi);
            }
            if (hi - lo <= Math.max(Math.abs(hi), Math.abs(lo)) * 1e-9D) {
                double pad = lo == 0D ? 1D : Math.abs(lo) * 0.1D;
                if (lo == 0D && withZero || pinLo) {
                    hi = lo + pad;
                } else if (pinHi) {
                    lo = hi - pad;
                } else {
                    lo -= pad;
                    hi += pad;
                }
            }
        }
        buildTicks(lo, hi, pinLo, pinHi);
        hasRange = true;
    }

    private int maxTicks() {
        return Math.max(2, Math.min(MAX_TICKS, plotH / TICK_ROOM + 1));
    }

    private static double niceStep(double rough) {
        double power = Math.pow(10D, Math.floor(Math.log10(rough)));
        double fraction = rough / power;
        double nice = fraction <= 1D ? 1D : fraction <= 2D ? 2D : fraction <= 5D ? 5D : 10D;
        return nice * power;
    }

    private static double nextStep(double step) {
        return niceStep(step * 1.0001D);
    }

    /** Round numbers, from the data's ends outwards unless an end is pinned. Rebuilt only when the data's ends move. */
    private void buildTicks(double lo, double hi, boolean pinLo, boolean pinHi) {
        int room = maxTicks();
        if (lo == tickFromLo && hi == tickFromHi && room == tickRoom) {
            return;
        }
        tickFromLo = lo;
        tickFromHi = hi;
        tickRoom = room;
        double step = niceStep((hi - lo) / Math.max(1, room - 1));
        double first;
        double last;
        int count;
        for (int guard = 0; ; guard++) {
            first = Math.floor(lo / step + 1e-9D);
            last = Math.ceil(hi / step - 1e-9D);
            if (pinLo) {
                first = Math.ceil(lo / step - 1e-9D);
            }
            if (pinHi) {
                last = Math.floor(hi / step + 1e-9D);
            }
            count = (int) Math.min(MAX_TICKS + 3D, Math.max(0D, last - first + 1D));
            if (count <= room || guard > 8) {
                break;
            }
            step = nextStep(step);
        }
        targetLo = pinLo ? lo : first * step;
        targetHi = pinHi ? hi : last * step;
        tickCount = Math.min(count, tickValue.length);
        double biggest = Math.max(Math.abs(targetLo), Math.abs(targetHi));
        for (int i = 0; i < tickCount; i++) {
            double value = (first + i) * step;
            if (Math.abs(value) < step * 1e-6D) {
                value = 0D;
            }
            tickValue[i] = value;
            tickText[i] = valueFormat != null ? safe(valueFormat.apply(value)) : axisText(value, step, biggest);
        }
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    /** The axis's numbers: all with the decimals the step needs, in thousands or more only when they get long. */
    static String axisText(double value, double step, double biggest) {
        if (biggest >= 1e15D || step < 1e-5D) {
            return value == 0D ? "0" : shortSci(value);
        }
        double unit = biggest >= 1e12D ? 1e12D : biggest >= 1e9D ? 1e9D : biggest >= 1e6D ? 1e6D : biggest >= 1e4D ? 1e3D : 1D;
        String suffix = unit == 1e12D ? "T" : unit == 1e9D ? "G" : unit == 1e6D ? "M" : unit == 1e3D ? "k" : "";
        double scaled = step / unit;
        int decimals = 0;
        double tens = 1D;
        while (decimals < 6 && Math.abs(scaled * tens - Math.rint(scaled * tens)) > 1e-6D) {
            decimals++;
            tens *= 10D;
        }
        return plain(value / unit, decimals, false) + suffix;
    }

    /** As {@code 2.5e-9} or {@code 1e300}: an axis has no room for more. */
    private static String shortSci(double value) {
        String text = String.format(Locale.ROOT, "%.1e", value);
        return text.replace(".0e", "e").replace("e+", "e").replace("e-0", "e-");
    }

    /** No locale, so the decimal mark is the same for everyone and the words stay the game's own. */
    static String plain(double value, int decimals, boolean strip) {
        double tens = Math.pow(10D, decimals);
        long scaled = Math.round(Math.abs(value) * tens);
        if (scaled == 0L) {
            return "0";
        }
        long whole = scaled / (long) tens;
        long part = scaled % (long) tens;
        StringBuilder out = new StringBuilder();
        if (value < 0D) {
            out.append('-');
        }
        out.append(whole);
        if (decimals > 0 && !(strip && part == 0L)) {
            String digits = Long.toString(part);
            out.append('.');
            for (int i = digits.length(); i < decimals; i++) {
                out.append('0');
            }
            out.append(digits);
            if (strip) {
                int end = out.length();
                while (out.charAt(end - 1) == '0') {
                    end--;
                }
                out.setLength(end);
            }
        }
        return out.toString();
    }

    /** A value in the readout: exact enough to read, without the axis's rounding. */
    private String readoutText(double value) {
        if (value != value) {
            return "-";
        }
        if (valueFormat != null) {
            return safe(valueFormat.apply(value));
        }
        double abs = Math.abs(value);
        String number;
        if (abs >= 1e15D || (abs > 0D && abs < 1e-4D)) {
            number = String.format(Locale.ROOT, "%.3e", value);
        } else {
            number = plain(value, abs >= 1D ? 2 : 4, true);
        }
        return number + unit;
    }

    // ---- layout ----

    private boolean wantsLegend() {
        return legend != null ? legend && !series.isEmpty() : series.size() >= 2;
    }

    private int headerTop() {
        return BORDER + 2;
    }

    private void layoutVertical() {
        boolean fits = w >= MIN_AXES_W && h >= MIN_AXES_H;
        showHeader = (!title.isEmpty() || wantsLegend()) && w >= MIN_HEADER_W && h >= MIN_HEADER_H;
        showXLabels = axes && fits && xLabel != null && h >= MIN_X_LABELS_H && newest > 0L;
        boolean axisRoom = axes && fits;
        int top;
        if (showHeader) {
            top = headerTop() + HEADER + (axisRoom ? LABEL_HALF + HEADER_GAP : SIDE);
        } else {
            top = axisRoom ? BORDER + LABEL_HALF : BORDER + SIDE - 1;
        }
        int bottom;
        if (showXLabels) {
            bottom = BORDER + 2 + LINE + 1;
        } else {
            bottom = axisRoom ? BORDER + LABEL_HALF : BORDER + SIDE - 1;
        }
        plotT = top;
        plotH = Math.max(0, h - top - bottom);
        showY = axisRoom;
    }

    private void layoutHorizontal() {
        boolean withLabels = showY && hasRange && tickCount > 0;
        labelW = 0;
        if (withLabels) {
            for (int i = 0; i < tickCount; i++) {
                labelW = Math.max(labelW, TextMeasure.width(tickText[i]));
            }
            labelW = Math.min(labelW, w * 2 / 5);
        }
        int left = withLabels ? BORDER + SIDE + labelW + LABEL_GAP : BORDER + (showY ? SIDE : SIDE - 1);
        int right = showY ? BORDER + SIDE : BORDER + SIDE - 1;
        plotL = left;
        plotW = Math.max(0, w - left - right);
        layoutHeader();
    }

    private void layoutHeader() {
        int count = series.size();
        if (legendL.length != count) {
            legendL = new int[count];
            legendW = new int[count];
        }
        showLegend = false;
        titleW = 0;
        if (!showHeader) {
            return;
        }
        int left = BORDER + SIDE;
        int room = w - 2 * (BORDER + SIDE);
        int natural = TextMeasure.width(title);
        boolean wanted = wantsLegend();
        if (!wanted) {
            titleW = Math.min(natural, room);
            return;
        }
        int needs = 0;
        for (int i = 0; i < count; i++) {
            needs += SWATCH + SWATCH_GAP + TextMeasure.width(series.get(i).name) + (i > 0 ? LEGEND_GAP : 0);
        }
        int titleRoom = title.isEmpty() ? 0 : Math.min(natural, Math.max(room / 3, room - needs - LEGEND_GAP));
        int legendRoom = room - (titleRoom > 0 ? titleRoom + LEGEND_GAP : 0);
        int share = fairShare(legendRoom - LEGEND_GAP * (count - 1), count);
        if (share < SWATCH + SWATCH_GAP + MIN_LEGEND_TEXT) {
            titleW = Math.min(natural, room);
            return;
        }
        titleW = titleRoom;
        showLegend = true;
        int used = 0;
        for (int i = 0; i < count; i++) {
            int width = Math.min(share, SWATCH + SWATCH_GAP + TextMeasure.width(series.get(i).name));
            legendW[i] = width;
            used += width + (i > 0 ? LEGEND_GAP : 0);
        }
        int at = left + room - used;
        for (int i = 0; i < count; i++) {
            legendL[i] = at;
            at += legendW[i] + LEGEND_GAP;
        }
    }

    /** The width that lets short entries keep theirs and shares what they leave among the long ones. */
    private int fairShare(int room, int count) {
        int share = room / Math.max(1, count);
        for (int pass = 0; pass < 3; pass++) {
            int spare = room;
            int longer = 0;
            for (int i = 0; i < count; i++) {
                int width = SWATCH + SWATCH_GAP + TextMeasure.width(series.get(i).name);
                if (width <= share) {
                    spare -= width;
                } else {
                    longer++;
                }
            }
            if (longer == 0) {
                return share;
            }
            share = spare / longer;
        }
        return share;
    }

    /** Labels on round multiples of a stride, so that they scroll with the data rather than change. */
    private void buildXLabels() {
        xTickCount = 0;
        if (plotW <= 0 || newest <= 0L) {
            return;
        }
        long firstSample = Math.max(0L, viewStart);
        long span = newest - firstSample;
        int widest = 1;
        long probe = Math.max(1L, span / 8L);
        for (long n = firstSample; n < newest; n += probe) {
            String text = xLabel.apply(n);
            widest = Math.max(widest, text == null ? 0 : TextMeasure.width(text));
        }
        double perSample = mode == Mode.BAR && win <= plotW ? plotW / win : plotW / (double) Math.max(1, win - 1);
        long stride = 1L;
        while (stride * perSample < Math.min(widest, MIN_X_LABEL) + X_LABEL_GAP) {
            stride = nextStride(stride);
        }
        // Long names are cut to their room, and the readout's first line has them whole.
        int cap = (int) Math.max(1D, Math.min(plotW, stride * perSample - X_LABEL_GAP));
        long n = stride == 1L ? firstSample : (firstSample + stride - 1L) / stride * stride;
        for (; n < newest && xTickCount < MAX_X_LABELS; n += stride) {
            String text = xLabel.apply(n);
            if (text == null || text.isEmpty()) {
                continue;
            }
            xTickSample[xTickCount] = n;
            xTickText[xTickCount] = text;
            xTickW[xTickCount] = Math.min(TextMeasure.width(text), cap);
            xTickCount++;
        }
    }

    private static long nextStride(long stride) {
        long power = 1L;
        while (power * 10L <= stride) {
            power *= 10L;
        }
        long lead = stride / power;
        return lead == 1L ? 2L * power : lead == 2L ? 5L * power : 10L * power;
    }

    // ---- geometry ----

    private boolean barsDense() {
        return win > plotW;
    }

    private int slot() {
        return Math.max(1, plotW / Math.max(1, win));
    }

    private int barsLeft() {
        return (plotW - slot() * win) / 2;
    }

    /** The column, from the plot's left, that a place on the axis is drawn at. */
    private int columnOf(int index) {
        if (mode == Mode.BAR) {
            if (barsDense()) {
                return (int) ((long) index * plotW / win);
            }
            return barsLeft() + index * slot() + slot() / 2;
        }
        if (win <= 1 || plotW <= 1) {
            return plotW / 2;
        }
        return (int) Math.round(index * (plotW - 1D) / (win - 1D));
    }

    private long sampleAtX(int mx) {
        int column = Math.max(0, Math.min(plotW - 1, mx - x - plotL));
        int index;
        if (mode == Mode.BAR) {
            index = barsDense() ? (int) ((long) column * win / plotW) : Math.floorDiv(column - barsLeft(), slot());
        } else if (win <= 1 || plotW <= 1) {
            index = 0;
        } else {
            index = (int) Math.round(column * (win - 1D) / (plotW - 1D));
        }
        index = Math.max(0, Math.min(win - 1, index));
        return Math.max(firstKept(), Math.min(newest - 1L, viewStart + index));
    }

    /** The oldest sample the keys and the pointer can reach. */
    private long firstKept() {
        return Math.max(0L, viewStart);
    }

    /** A row from the top of the plot. Far outside values stop close by: the view cuts them anyway. */
    private int rowOf(double value) {
        double span = shownHi - shownLo;
        int rows = Math.max(0, plotH - 1);
        double at = span > 0D ? (value - shownLo) / span * rows : 0D;
        at = Math.max(-2D * plotH - 2D, Math.min(3D * plotH + 2D, at));
        return rows - (int) Math.round(at);
    }

    /** {@link #rowOf} before rounding: row {@code n} is centred on {@code n}. */
    private double rowAt(double value) {
        double span = shownHi - shownLo;
        int rows = Math.max(0, plotH - 1);
        double at = span > 0D ? (value - shownLo) / span * rows : 0D;
        return rows - Math.max(-2D * plotH - 2D, Math.min(3D * plotH + 2D, at));
    }

    private double baseValue() {
        return Math.max(shownLo, Math.min(shownHi, 0D));
    }

    private boolean inPlot(int mx, int my) {
        return mx >= x + plotL && mx < x + plotL + plotW && my >= y + plotT && my < y + plotT + plotH;
    }

    private int legendAt(double mx, double my) {
        if (!showLegend || my < y + headerTop() || my >= y + headerTop() + HEADER) {
            return -1;
        }
        for (int i = 0; i < series.size(); i++) {
            int left = x + legendL[i];
            if (mx >= left && mx < left + legendW[i]) {
                return i;
            }
        }
        return -1;
    }

    // ---- the columns ----

    /** Where each series crosses each pixel column. Kept until the data, the axis or the size changes. */
    private void buildColumns() {
        if (!columnsDirty && columnsLo == shownLo && columnsHi == shownHi) {
            return;
        }
        columnsDirty = false;
        columnsBuilt++;
        columnsLo = shownLo;
        columnsHi = shownHi;
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            if (s.top.length != plotW) {
                s.top = new int[plotW];
                s.bottom = new int[plotW];
            }
            if (s.shown) {
                if (mode != Mode.BAR) {
                    buildLine(s);
                } else if (barsDense()) {
                    buildDenseBars(s);
                }
            }
        }
    }

    private double place(Series s, double at) {
        int index = (int) Math.floor(at);
        double part = at - index;
        double a = s.at(viewStart + index);
        if (part < 1e-9D) {
            return a;
        }
        double b = s.at(viewStart + index + 1L);
        return a == a && b == b ? a + (b - a) * part : Double.NaN;
    }

    /**
     * A one-pixel line drawn column by column, as {@link PixelArt#lineTop} lays it. Samples inside the column are
     * added whole, so that a peak or a dip keeps its tip.
     */
    private void buildLine(Series s) {
        double per = plotW <= 1 ? 0D : (win - 1D) / (plotW - 1D);
        for (int c = 0; c < plotW; c++) {
            s.top[c] = -1;
            s.bottom[c] = -1;
            double from;
            double to;
            double centre;
            if (win <= 1) {
                if (c != plotW / 2) {
                    continue;
                }
                from = 0D;
                to = 0D;
                centre = 0D;
            } else if (plotW <= 1) {
                from = 0D;
                to = win - 1D;
                centre = to / 2D;
            } else {
                centre = c * per;
                from = Math.max(0D, centre - 0.5D * per);
                to = Math.min(win - 1D, centre + 0.5D * per);
            }
            double high = Double.NaN;
            double low = Double.NaN;
            int last = (int) Math.floor(to);
            boolean closes = c == plotW - 1 || plotW <= 1;
            for (int k = (int) Math.ceil(from); k <= last; k++) {
                if (k == to && !closes && win > 1) {
                    continue;
                }
                double v = s.at(viewStart + k);
                if (v == v) {
                    double row = rowAt(v);
                    high = high == high ? Math.min(high, row) : row;
                    low = low == low ? Math.max(low, row) : row;
                }
            }
            double middle = place(s, centre);
            middle = middle == middle ? rowAt(middle) : high;
            if (middle != middle) {
                continue;
            }
            double left = place(s, from);
            double right = place(s, to);
            left = left == left ? rowAt(left) : Double.NaN;
            right = right == right ? rowAt(right) : Double.NaN;
            s.top[c] = PixelArt.lineTop(left, middle, right, high);
            s.bottom[c] = PixelArt.lineBottom(left, middle, right, low);
        }
    }

    /** More samples than columns: a column's bar reaches the sample furthest from the base. */
    private void buildDenseBars(Series s) {
        double base = baseValue();
        int baseRow = rowOf(base);
        for (int c = 0; c < plotW; c++) {
            long from = viewStart + (long) c * win / plotW;
            long to = viewStart + (long) (c + 1) * win / plotW;
            boolean any = false;
            double far = base;
            for (long n = from; n < to; n++) {
                double v = s.at(n);
                if (v == v) {
                    any = true;
                    if (Math.abs(v - base) >= Math.abs(far - base)) {
                        far = v;
                    }
                }
            }
            if (!any) {
                s.top[c] = -1;
                s.bottom[c] = -1;
            } else {
                int row = rowOf(far);
                s.top[c] = Math.min(row, baseRow);
                s.bottom[c] = Math.max(row, baseRow) + 1;
            }
        }
    }

    // ---- colours ----

    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    /** The well under the series, and the inks on the panel and on the readout card; the series colours come from SeriesColors. */
    private void pickColours(Palette p) {
        if (p == colourFor) {
            if (inkActive != active) {
                pickInks(p);
            }
            return;
        }
        colourFor = p;
        pickInks(p);
        cardInk = Ink.on(p, opaque(p.surface()));
        back = Shapes.mix(opaque(p.surface()), opaque(p.well()), (p.well() >>> 24) / 255F);
    }

    private void pickInks(Palette p) {
        inkActive = active;
        ink = RowLook.ink(p, 0, false, active);
        softInk = active ? RowLook.softInk(p, ink) : ink;
    }


    private int colourOf(Series s) {
        int base = s.customColor ? opaque(s.color) : SeriesColors.color(colourFor, seriesPalette, s.order);
        return active ? base : Shapes.mix(base, back, DISABLED_FADE);
    }

    // ---- drawing ----

    private void stepRange() {
        if (!hasRange) {
            return;
        }
        long now = BaseComponent.clock.getAsLong();
        if (!shownSet || reducedMotion) {
            shownLo = targetLo;
            shownHi = targetHi;
            shownSet = true;
        } else if (shownLo != targetLo || shownHi != targetHi) {
            long elapsed = lastGlide == NONE ? 0L : Math.max(0L, now - lastGlide);
            double step = 1D - Math.exp(-elapsed / GLIDE_MS);
            shownLo += (targetLo - shownLo) * step;
            shownHi += (targetHi - shownHi) * step;
            double near = (targetHi - targetLo) / Math.max(1, plotH) / 2D;
            if (Math.abs(targetLo - shownLo) < near && Math.abs(targetHi - shownHi) < near) {
                shownLo = targetLo;
                shownHi = targetHi;
            }
        }
        lastGlide = now;
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        Palette p = t.palette();
        pickColours(p);
        refresh();
        stepRange();
        t.frame().well(d, p, x, y, w, h, focused && active);
        cardDrawn = false;

        boolean inside = active && hovered && settled();
        int overLegend = inside ? legendAt(mx, my) : -1;
        legendHover.step(overLegend);
        if (showHeader) {
            drawHeader(d, p, overLegend);
        }
        if (plotW <= 0 || plotH <= 0) {
            crossAt(NONE, false, 0);
            return;
        }
        if (hasRange) {
            drawGrid(d);
            buildColumns();
        }
        if (!hasRange || !hasData) {
            crossAt(NONE, false, 0);
            int top = showHeader ? y + plotT : y + BORDER;
            int high = showHeader ? plotH : h - 2 * BORDER;
            RowLook.drawEmpty(d, p, emptyText, active, x + BORDER, top, w - 2 * BORDER, high);
            return;
        }
        pickCross(inside, mx, my);
        if (mode == Mode.BAR && crossSample != NONE) {
            drawBand(d, p);
        }
        d.scissor(x + plotL, y + plotT, x + plotL + plotW, y + plotT + plotH);
        if (crossSample != NONE) {
            // Under the series: a translucent line over one would change its colour along that row.
            drawCrossLines(d);
        }
        if (mode == Mode.BAR) {
            drawBars(d);
        } else {
            if (smooth && buildSmooth(Smooth.scale(d))) {
                drawSmooth(d);
            } else {
                smoothShown = false;
                if (mode == Mode.AREA) {
                    drawAreas(d);
                }
                drawLines(d);
            }
        }
        d.endScissor();
        if (mode != Mode.BAR) {
            if (!smoothShown) {
                drawDots(d);
            }
            if (crossSample != NONE) {
                drawCrossMarkers(d);
            }
        }
        if (showXLabels) {
            drawXLabels(d);
        }
        if (crossSample != NONE) {
            drawCard(d, t, p);
        }
    }

    private void drawHeader(Draw d, Palette p, int overLegend) {
        int textY = y + headerTop() + 1;
        if (titleW > 0) {
            d.textFit(title, x + BORDER + SIDE, textY, titleW, ink);
        }
        if (!showLegend) {
            return;
        }
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            int left = x + legendL[i];
            int top = y + headerTop();
            if (i == overLegend || (i == litLegend && focused)) {
                float lit = i == litLegend && focused ? 1F : legendHover.fade();
                d.fill(left - 1, top, left + legendW[i] + 1, top + HEADER, RowLook.tint(p, lit));
            }
            int color = colourOf(s);
            int swatchTop = top + (HEADER - SWATCH) / 2;
            if (s.shown) {
                d.fill(left, swatchTop, left + SWATCH, swatchTop + SWATCH, color);
            } else {
                d.fill(left, swatchTop, left + SWATCH, swatchTop + 1, color);
                d.fill(left, swatchTop + SWATCH - 1, left + SWATCH, swatchTop + SWATCH, color);
                d.fill(left, swatchTop + 1, left + 1, swatchTop + SWATCH - 1, color);
                d.fill(left + SWATCH - 1, swatchTop + 1, left + SWATCH, swatchTop + SWATCH - 1, color);
            }
            int textLeft = left + SWATCH + SWATCH_GAP;
            int textColor = s.shown ? softInk : Shapes.mix(opaque(softInk), back, HIDDEN_FADE);
            d.textFit(s.name, textLeft, textY, Math.max(0, legendW[i] - SWATCH - SWATCH_GAP), textColor);
        }
    }

    private void drawGrid(Draw d) {
        if (!showY) {
            return;
        }
        int left = x + plotL;
        int right = left + plotW;
        int labelRight = left - LABEL_GAP;
        for (int i = 0; i < tickCount; i++) {
            double value = tickValue[i];
            int row = rowOf(value);
            if (row < 0 || row >= plotH) {
                continue;
            }
            int lineY = y + plotT + row;
            boolean axis = value == 0D || (i == 0 && value > 0D);
            d.fill(left, lineY, right, lineY + 1, Shapes.alpha(ink, axis ? ZERO_ALPHA : GRID_ALPHA));
            d.textFitRight(tickText[i], labelRight, lineY - LABEL_RISE, labelW, softInk);
        }
    }

    // ---- smooth ----

    private boolean buildSmooth(int gs) {
        long key = columnsBuilt * 31L + gs;
        key = key * 31L + plotW;
        key = key * 31L + plotH;
        key = key * 31L + mode.ordinal();
        key = key * 31L + (points ? 1 : 0);
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            key = key * 31L + (s.shown ? colourOf(s) : 7);
        }
        if (key == smoothKey) {
            return smoothShown;
        }
        smoothKey = key;
        while (smoothLines.size() < series.size()) {
            smoothLines.add(new Smooth());
            smoothAreas.add(new Smooth());
        }
        int[] ladder = Smooth.ladder(gs);
        smoothShown = false;
        for (int step = 0; step < ladder.length; step += 2) {
            int sub = ladder[step];
            if ((long) plotW * sub * plotH * sub > Smooth.MAX_CELLS) {
                continue;
            }
            smoothRects = paintSmooth(sub, ladder[step + 1]);
            if (smoothRects <= SMOOTH_BUDGET * Smooth.scaleOf(gs)) {
                smoothShown = true;
                return true;
            }
        }
        return false;
    }

    private double xAt(double index) {
        if (win <= 1 || plotW <= 1) {
            return plotW / 2 + 0.5D;
        }
        return index * (plotW - 1D) / (win - 1D) + 0.5D;
    }

    private double yAt(double value) {
        double span = shownHi - shownLo;
        int rows = Math.max(0, plotH - 1);
        double at = span > 0D ? (value - shownLo) / span * rows : 0D;
        at = Math.max(-2D * plotH - 2D, Math.min(3D * plotH + 2D, at));
        return rows - at + 0.5D;
    }

    private int paintSmooth(int sub, int levels) {
        int total = 0;
        int fw = plotW * sub;
        int fh = plotH * sub;
        boolean every = points && win > 1 && (plotW - 1D) / (win - 1D) >= POINT_ROOM;
        double baseBottom = (rowOf(baseValue()) + 1D) * sub;
        double baseTop = rowOf(baseValue()) * (double) sub;
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            Smooth line = smoothLines.get(i);
            Smooth area = smoothAreas.get(i);
            line.begin(fw, fh, sub, levels);
            area.begin(mode == Mode.AREA ? fw : 0, mode == Mode.AREA ? fh : 0, sub, levels);
            if (!s.shown) {
                continue;
            }
            int color = colourOf(s);
            double px = 0D;
            double py = 0D;
            boolean had = false;
            boolean lone = false;
            for (int k = 0; k < win; k++) {
                double v = s.at(viewStart + k);
                if (v != v) {
                    if (had && lone) {
                        line.disc(px, py, 1.5D * sub);
                    }
                    had = false;
                    continue;
                }
                double cx = xAt(k) * sub;
                double cy = yAt(v) * sub;
                if (had) {
                    line.capsule(px, py, cx, cy, 0.5D * sub);
                    lone = false;
                } else {
                    lone = true;
                }
                if (every) {
                    line.disc(cx, cy, 1.5D * sub);
                }
                px = cx;
                py = cy;
                had = true;
            }
            if (had && lone) {
                line.disc(px, py, 1.5D * sub);
            }
            if (mode == Mode.AREA) {
                double per = plotW <= 1 ? 0D : (win - 1D) / (plotW - 1D);
                for (int fx = 0; fx < fw; fx++) {
                    double gx = (fx + 0.5D) / sub - 0.5D;
                    double at = win <= 1 ? 0D : gx * per;
                    if (at < 0D || at > win - 1D) {
                        continue;
                    }
                    double v = place(s, at);
                    if (v != v) {
                        continue;
                    }
                    double top = yAt(v) * sub;
                    if (top < baseBottom) {
                        area.column(fx, top, baseBottom, 1F);
                    } else {
                        area.column(fx, baseTop, top, 1F);
                    }
                }
                area.paint(Shapes.alpha(color, AREA_ALPHA));
                total += area.rects();
            }
            line.paint(color);
            total += line.rects();
        }
        return total;
    }

    private void drawSmooth(Draw d) {
        int left = x + plotL;
        int top = y + plotT;
        if (mode == Mode.AREA) {
            for (int i = 0; i < series.size() && i < smoothAreas.size(); i++) {
                smoothAreas.get(i).emit(d, left, top);
            }
        }
        for (int i = 0; i < series.size() && i < smoothLines.size(); i++) {
            smoothLines.get(i).emit(d, left, top);
        }
    }

    private void drawLines(Draw d) {
        int left = x + plotL;
        int top = y + plotT;
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            if (!s.shown) {
                continue;
            }
            int color = colourOf(s);
            for (int c = 0; c < plotW; c++) {
                if (s.bottom[c] != -1) {
                    d.fill(left + c, top + s.top[c], left + c + 1, top + s.bottom[c], color);
                }
            }
        }
    }

    private void drawAreas(Draw d) {
        int left = x + plotL;
        int top = y + plotT;
        int baseRow = rowOf(baseValue());
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            if (!s.shown) {
                continue;
            }
            int color = Shapes.alpha(colourOf(s), AREA_ALPHA);
            for (int c = 0; c < plotW; c++) {
                if (s.bottom[c] == -1) {
                    continue;
                }
                if (s.bottom[c] <= baseRow) {
                    d.fill(left + c, top + s.bottom[c], left + c + 1, top + baseRow + 1, color);
                } else if (s.top[c] > baseRow) {
                    d.fill(left + c, top + baseRow, left + c + 1, top + s.top[c], color);
                }
            }
        }
    }

    /** A sample with a gap on both sides has no line to show: it gets a dot. So does every sample with points on. */
    private void drawDots(Draw d) {
        int left = x + plotL;
        int top = y + plotT;
        boolean every = points && win > 1 && (plotW - 1D) / (win - 1D) >= POINT_ROOM;
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            if (!s.shown) {
                continue;
            }
            int color = colourOf(s);
            for (int c = 0; c < plotW; c++) {
                if (s.bottom[c] == -1) {
                    continue;
                }
                boolean alone = (c == 0 || s.bottom[c - 1] == -1) && (c == plotW - 1 || s.bottom[c + 1] == -1);
                if (alone && s.bottom[c] - s.top[c] <= 1) {
                    int row = s.top[c];
                    if (row >= 0 && row < plotH) {
                        d.fill(left + c - 1, top + row - 1, left + c + 2, top + row + 2, color);
                    }
                }
            }
            if (every) {
                for (long n = Math.max(0L, viewStart); n < s.total; n++) {
                    double v = s.at(n);
                    if (v == v) {
                        int row = rowOf(v);
                        if (row >= 0 && row < plotH) {
                            int c = columnOf((int) (n - viewStart));
                            d.fill(left + c - 1, top + row - 1, left + c + 2, top + row + 2, color);
                        }
                    }
                }
            }
        }
    }

    private void drawBars(Draw d) {
        int left = x + plotL;
        int top = y + plotT;
        if (barsDense()) {
            for (int i = 0; i < series.size(); i++) {
                Series s = series.get(i);
                if (!s.shown) {
                    continue;
                }
                int color = colourOf(s);
                for (int c = 0; c < plotW; c++) {
                    if (s.bottom[c] != -1) {
                        d.fill(left + c, top + s.top[c], left + c + 1, top + s.bottom[c], color);
                    }
                }
            }
            return;
        }
        int slot = slot();
        int gap = slot >= 6 ? 2 : slot >= 3 ? 1 : 0;
        int count = Math.max(1, shownCount);
        int barW = Math.max(1, Math.min(MAX_BAR, (slot - gap) / count));
        int group = barW * count;
        int inset = Math.max(0, (slot - group) / 2);
        double base = baseValue();
        int baseRow = rowOf(base);
        int place = 0;
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            if (!s.shown) {
                continue;
            }
            int color = colourOf(s);
            for (int k = 0; k < win; k++) {
                double v = s.at(viewStart + k);
                if (v != v) {
                    continue;
                }
                int barX = left + barsLeft() + k * slot + inset + place * barW;
                int row = rowOf(v);
                int from = v >= base ? row : baseRow;
                int to = v >= base ? baseRow + 1 : row + 1;
                d.fill(barX, top + from, barX + barW, top + to, color);
            }
            place++;
        }
    }

    private void drawBand(Draw d, Palette p) {
        int left = x + plotL;
        int index = (int) (crossSample - viewStart);
        int from = barsDense() ? columnOf(index) : barsLeft() + index * slot();
        int width = barsDense() ? 1 : slot();
        d.fill(left + from, y + plotT, left + from + width, y + plotT + plotH, RowLook.tint(p, crossFade()));
    }

    // ---- the crosshair ----

    private void crossAt(long sample, boolean byPointer, int pointerY) {
        if (sample == NONE) {
            crossSample = NONE;
            crossSince = NONE;
            return;
        }
        if (crossSample == NONE) {
            crossSince = reducedMotion ? NONE : BaseComponent.clock.getAsLong();
        }
        crossSample = sample;
        crossByPointer = byPointer;
        crossMy = pointerY;
    }

    private void pickCross(boolean inside, int mx, int my) {
        if (inside && inPlot(mx, my)) {
            crossAt(sampleAtX(mx), true, my);
        } else if (focused && active && cursor != NONE) {
            cursor = Math.max(firstKept(), Math.min(newest - 1L, cursor));
            crossAt(cursor, false, 0);
        } else {
            crossAt(NONE, false, 0);
        }
    }

    /** The value the crosshair's level line sits on: the one nearest the pointer, or the first with the keys. */
    private int crossRow() {
        int best = Integer.MIN_VALUE;
        int bestGap = Integer.MAX_VALUE;
        int pointerRow = crossMy - y - plotT;
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            if (!s.shown) {
                continue;
            }
            double v = s.at(crossSample);
            if (v == v) {
                int row = rowOf(v);
                int gap = crossByPointer ? Math.abs(row - pointerRow) : 0;
                if (gap < bestGap) {
                    bestGap = gap;
                    best = row;
                }
            }
        }
        return best;
    }

    private void drawCrossLines(Draw d) {
        int left = x + plotL;
        int top = y + plotT;
        float fade = crossFade();
        if (mode != Mode.BAR) {
            int column = left + columnOf((int) (crossSample - viewStart));
            d.fill(column, top, column + 1, top + plotH, Shapes.alpha(ink, Math.round(CROSS_ALPHA * fade)));
        }
        int row = crossRow();
        if (row >= 0 && row < plotH) {
            d.fill(left, top + row, left + plotW, top + row + 1, Shapes.alpha(ink, Math.round(CROSS_SOFT_ALPHA * fade)));
        }
    }

    private void drawCrossMarkers(Draw d) {
        int column = x + plotL + columnOf((int) (crossSample - viewStart));
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            if (!s.shown) {
                continue;
            }
            double v = s.at(crossSample);
            if (v != v) {
                continue;
            }
            int row = rowOf(v);
            if (row < 0 || row >= plotH) {
                continue;
            }
            int cy = y + plotT + row;
            d.fill(column - 2, cy - 2, column + 3, cy + 3, back);
            d.fill(column - 1, cy - 1, column + 2, cy + 2, colourOf(s));
        }
    }

    private void drawXLabels(Draw d) {
        int left = x + plotL;
        int textY = y + plotT + plotH + 3;
        int edge = Integer.MIN_VALUE;
        for (int i = 0; i < xTickCount; i++) {
            int index = (int) (xTickSample[i] - viewStart);
            if (index < 0 || index >= win) {
                continue;
            }
            int width = xTickW[i];
            int centre = left + columnOf(index);
            int tx = Math.max(left, Math.min(left + plotW - width, centre - width / 2));
            if (tx < edge) {
                continue;
            }
            d.textFit(xTickText[i], tx, textY, width, softInk);
            edge = tx + width + X_LABEL_GAP;
        }
    }

    // ---- the readout ----

    private int cardInset(Theme t) {
        return Math.max(MIN_CARD_INSET, Math.min(MAX_CARD_INSET, t.frame().corner()));
    }

    private void buildCard() {
        if (cardSample == crossSample && cardVersion == version) {
            return;
        }
        cardSample = crossSample;
        cardVersion = version;
        cardHead = xLabel == null ? null : xLabel.apply(crossSample);
        if (cardHead != null && cardHead.isEmpty()) {
            cardHead = null;
        }
        int count = 0;
        for (int i = 0; i < series.size(); i++) {
            if (series.get(i).shown) {
                count++;
            }
        }
        if (cardNames.length != count) {
            cardNames = new String[count];
            cardValues = new String[count];
            cardSeries = new int[count];
        }
        cardNameW = 0;
        cardValueW = 0;
        int row = 0;
        for (int i = 0; i < series.size(); i++) {
            Series s = series.get(i);
            if (!s.shown) {
                continue;
            }
            cardNames[row] = s.name;
            cardValues[row] = readoutText(s.at(crossSample));
            cardSeries[row] = i;
            cardNameW = Math.max(cardNameW, TextMeasure.width(s.name));
            cardValueW = Math.max(cardValueW, TextMeasure.width(cardValues[row]));
            row++;
        }
        cardRows = row;
    }

    /** Places the readout beside the crosshair, inside the box. False when the box cannot hold it. */
    private boolean placeCard(int inset) {
        int head = cardHead == null ? 0 : 1;
        int roomH = h - 2 * CARD_MARGIN - 2 * BORDER - 2 * inset;
        int rows = roomH / LINE;
        if (rows < head + Math.min(1, cardRows)) {
            return false;
        }
        int shownRows = Math.min(cardRows + head, rows);
        int headW = head > 0 ? TextMeasure.width(cardHead) : 0;
        int bodyW = SWATCH + 3 + cardNameW + CARD_COLUMN_GAP + cardValueW;
        int inner = Math.max(headW, cardRows == 0 ? 0 : bodyW);
        // Long names are cut before the readout hides the whole plot: the legend's tooltip has them whole.
        int limit = Math.min(w - 2 * CARD_MARGIN - 2 * BORDER, Math.max(MIN_CARD_LIMIT, w * 2 / 3));
        int width = Math.min(inner + 2 * inset, limit);
        if (cardRows > 0 && width - 2 * inset - SWATCH - 3 - CARD_COLUMN_GAP - cardValueW < Math.min(cardNameW, MIN_CARD_NAME)) {
            return false;
        }
        cardW = width;
        cardH = shownRows * LINE + 2 * inset;
        int hiddenRows = cardRows + head - shownRows;
        if (hiddenRows > 0 && cardMoreCount != hiddenRows) {
            cardMoreCount = hiddenRows;
            cardMore = "+" + (hiddenRows + 1);
        }
        int column = x + plotL + columnOf((int) (crossSample - viewStart));
        int spot = crossByPointer ? crossMy : y + plotT + plotH / 2;
        int low = x + BORDER + CARD_MARGIN;
        int high = x + w - BORDER - CARD_MARGIN - cardW;
        int beside = column + CARD_GAP;
        if (beside > high) {
            beside = column - CARD_GAP - cardW;
        }
        cardX = Math.max(low, Math.min(high, beside));
        int lowY = y + BORDER + CARD_MARGIN;
        int highY = y + h - BORDER - CARD_MARGIN - cardH;
        cardY = Math.max(lowY, Math.min(highY, spot - cardH / 2));
        return true;
    }

    private void drawCard(Draw d, Theme t, Palette p) {
        buildCard();
        int inset = cardInset(t);
        if (!placeCard(inset)) {
            return;
        }
        cardDrawn = true;
        d.nextLayer(); // the text beneath may sort above a rectangle otherwise
        FrameStyle frame = t.frame();
        frame.dropShadow(d, p, cardX, cardY, cardW, cardH, 2);
        int surface = p.surface();
        if (surface >>> 24 != 0xFF) {
            // A see-through family (glass) would show the chart through the words: hide it first.
            int corner = Math.max(1, frame.corner());
            int solid = opaque(surface);
            d.fill(cardX + corner, cardY + 1, cardX + cardW - corner, cardY + cardH - 1, solid);
            d.fill(cardX + 1, cardY + corner, cardX + cardW - 1, cardY + cardH - corner, solid);
        }
        frame.panel(d, p, cardX, cardY, cardW, cardH);
        int left = cardX + inset;
        int innerW = cardW - 2 * inset;
        int lineY = cardY + inset;
        int rows = (cardH - 2 * inset) / LINE;
        int row = 0;
        if (cardHead != null) {
            d.textFit(cardHead, left, lineY + 1, innerW, cardInk);
            lineY += LINE;
            row++;
        }
        int listed = rows - row;
        int more = cardRows - listed;
        for (int i = 0; i < cardRows && row < rows; i++, row++) {
            if (more > 0 && row == rows - 1) {
                d.text(cardMore, left, lineY + 1, cardInk);
                break;
            }
            int color = colourOf(series.get(cardSeries[i]));
            d.fill(left, lineY + (LINE - SWATCH) / 2, left + SWATCH, lineY + (LINE - SWATCH) / 2 + SWATCH, color);
            int nameLeft = left + SWATCH + 3;
            int valueW = TextMeasure.width(cardValues[i]);
            d.text(cardValues[i], left + innerW - valueW, lineY + 1, cardInk);
            d.textFit(cardNames[i], nameLeft, lineY + 1, Math.max(0, left + innerW - valueW - CARD_COLUMN_GAP - nameLeft), cardInk);
            lineY += LINE;
        }
    }

    // ---- tooltips ----

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!visible) {
            return null;
        }
        if (active) {
            refresh();
            int entry = legendAt(mx, my);
            if (entry >= 0) {
                Series s = series.get(entry);
                if (SWATCH + SWATCH_GAP + TextMeasure.width(s.name) > legendW[entry]) {
                    return List.of(s.name);
                }
                return tooltip;
            }
            if (showHeader && !title.isEmpty() && titleW < TextMeasure.width(title)
                    && my >= y + headerTop() && my < y + headerTop() + HEADER
                    && mx >= x + BORDER + SIDE && mx < x + BORDER + SIDE + titleW) {
                return List.of(title);
            }
            if (hasData && inPlot((int) Math.floor(mx), (int) Math.floor(my))) {
                if (cardDrawn) {
                    return null;
                }
                if (crossSample != NONE) {
                    return fallbackLines();
                }
            }
        }
        return super.tooltipAt(mx, my);
    }

    /** The readout as tooltip lines, for a box too small to hold it. */
    private List<String> fallbackLines() {
        buildCard();
        List<String> lines = new ArrayList<>(cardRows + 1);
        if (cardHead != null) {
            lines.add(cardHead);
        }
        for (int i = 0; i < cardRows; i++) {
            lines.add(cardNames[i].isEmpty() ? cardValues[i] : cardNames[i] + ": " + cardValues[i]);
        }
        return lines;
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
        refresh();
        return accepts() && (hasData || keysLegend());
    }

    @Override
    protected Box focusBox() {
        refresh();
        if (litLegend >= 0 && showLegend && litLegend < series.size()) {
            // The ring grows by 2 on each side: this lays it just outside the entry's lit face.
            return new Box("legend", x + legendL[litLegend], y + headerTop() + 1, legendW[litLegend], HEADER - 2);
        }
        if (showY && plotW > 0 && plotH > 0) {
            // Round the plot, what the keys walk. Round the box it would double the well's own lit border.
            return new Box("plot", x + plotL, y + plotT, plotW, plotH);
        }
        return super.focusBox();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!accepts() || button != 0 || !isOver(mx, my)) {
            return false;
        }
        refresh();
        int entry = legendAt(mx, my);
        if (entry >= 0) {
            toggle(entry);
            return true;
        }
        if (hasData && onSelect != null && inPlot((int) Math.floor(mx), (int) Math.floor(my))) {
            BaseComponent.click();
            onSelect.accept(sampleAtX((int) Math.floor(mx)));
        }
        return true;
    }

    private void toggle(int index) {
        Series s = series.get(index);
        BaseComponent.click();
        s.shown(!s.shown);
        if (onToggle != null) {
            onToggle.accept(s, s.shown);
        }
    }

    private void moveCursor(long to) {
        litLegend = -1;
        cursor = Math.max(firstKept(), Math.min(newest - 1L, to));
    }

    @Override
    protected boolean activate() {
        if (!accepts()) {
            return false;
        }
        refresh();
        if (litLegend >= 0 && litLegend < series.size()) {
            toggle(litLegend);
            return true;
        }
        if (cursor != NONE && hasData && onSelect != null) {
            BaseComponent.click();
            onSelect.accept(cursor);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!accepts()) {
            return false;
        }
        refresh();
        switch (key) {
            case LEFT -> {
                if (!hasData) {
                    return false;
                }
                moveCursor(cursor == NONE ? newest - 1L : cursor - 1L);
            }
            case RIGHT -> {
                if (!hasData) {
                    return false;
                }
                moveCursor(cursor == NONE ? newest - 1L : cursor + 1L);
            }
            case HOME -> {
                if (!hasData) {
                    return false;
                }
                moveCursor(firstKept());
            }
            case END -> {
                if (!hasData) {
                    return false;
                }
                moveCursor(newest - 1L);
            }
            case UP, DOWN -> {
                if (!keysLegend()) {
                    return false;
                }
                int by = key == InputKey.DOWN ? 1 : -1;
                litLegend = litLegend < 0 ? (by > 0 ? 0 : series.size() - 1) : Math.floorMod(litLegend + by, series.size());
                cursor = NONE;
            }
            case ENTER, SPACE -> {
                return activate();
            }
            case ESCAPE -> {
                if (cursor == NONE && litLegend < 0) {
                    return false;
                }
                cursor = NONE;
                litLegend = -1;
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
