package dev.dryaides.minuit;

import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * A value on a radial dial with thresholds: the arc fills up to the value in the colour of the zone it is in,
 * and a thin band outside the arc shows every zone.
 *
 * <pre>{@code
 * GaugeComponent load = add(new GaugeComponent(at(new Box("load", 10, 10, 70, 60)), 0, 100, 42)
 *         .zones(60, 85)
 *         .label(words.get("mymod.load"))
 *         .unit("%"));
 * load.setValue(current);
 * }</pre>
 *
 * <p>{@link #zones(double...)} takes the limits between the zones. One limit gives good and bad, two give good, warn and
 * bad; {@link #zoneColors(int...)} gives other colours. Without limits the arc takes the theme's primary colour. A
 * value change glides, and jumps under {@link BaseComponent#reducedMotion}. NaN or an infinity shows a dash and an
 * empty arc, and a value outside the range stops at the arc's ends, while the text says the true value.
 *
 * <p>The arc is whole pixels, worked out once for a size, and a frame only draws it, with no allocation at rest. It
 * only shows: no focus, no keys. A box too small for the words drops the label, then the value, then the
 * band; the tooltip then tells what was dropped.
 */
public class GaugeComponent extends BaseComponent {
    private static final int BUCKETS = 720;
    private static final int BAND_W = 2;
    private static final int BAND_GAP = 1;
    private static final int MIN_R_FOR_BAND = 12;
    private static final int LINE_PITCH = 10;
    private static final int CENTER_PAD = 2;
    private static final double SEPARATOR = 0.55D;
    private static final double GLIDE_MS = 70D;
    static final int SMOOTH_BUDGET = 750;

    private final double min;
    private final double max;
    private double target;
    private double shown;
    private long lastStep = Long.MIN_VALUE;
    private boolean firstFrame = true;

    private double[] cuts = new double[0];
    private int[] zoneColors = new int[0];
    private int sweep = 270;
    private int thickness;
    private String label = "";
    private String unit = "";
    private DoubleFunction<String> valueFormat;

    private boolean dirty = true;
    private int laidW = -1;
    private int laidH = -1;
    private int side;
    private int radius;
    private int discX;
    private int discY;
    private int inner;
    private boolean band;
    private int[] cells = new int[0];
    private final int[] colours = new int[2 * BUCKETS];
    private double colouredAt = Double.NaN;
    private Palette colouredFor;
    private boolean colouredActive;
    private int[] zoneInks = new int[0];
    private int trackInk;
    private boolean smooth;
    private final Smooth layer = new Smooth();
    private long smoothKey = Long.MIN_VALUE;
    private boolean smoothShown;

    private boolean textDirty = true;
    private String valueText = "";
    private String fitValue = "";
    private String fitLabel = "";
    private boolean showValue;
    private boolean showLabel;
    private int textY;
    private List<String> tip;

    /** @param value the value at the start; it shows at once */
    public GaugeComponent(Box box, double min, double max, double value) {
        super(box);
        this.min = Math.min(min, max);
        this.max = Math.max(min, max);
        this.target = value;
        this.shown = Double.isFinite(value) ? clamp(value) : this.min;
    }

    // ---- setup ----

    /** The limits between the zones, in value units, in any order. */
    public GaugeComponent zones(double... limits) {
        cuts = limits == null ? new double[0] : limits.clone();
        Arrays.sort(cuts);
        dirty = true;
        return this;
    }

    /** The zones' colours, from the lowest up; a missing one falls back on the theme's. */
    public GaugeComponent zoneColors(int... colors) {
        zoneColors = colors == null ? new int[0] : colors.clone();
        colouredFor = null;
        return this;
    }

    /** How far round the arc goes, in degrees: from 90 to 360. It sits centred on the top. 270 by default. */
    public GaugeComponent sweep(int degrees) {
        sweep = Math.max(90, Math.min(360, degrees));
        dirty = true;
        return this;
    }

    /** The width of the arc. By default a quarter of the radius, and at least 3. */
    public GaugeComponent thickness(int thickness) {
        this.thickness = Math.max(0, thickness);
        dirty = true;
        return this;
    }

    /** The line under the value. */
    public GaugeComponent label(String label) {
        this.label = label == null ? "" : label;
        textDirty = true;
        return this;
    }

    /** Follows the value, as in {@code "%"}. */
    public GaugeComponent unit(String unit) {
        this.unit = unit == null ? "" : unit;
        textDirty = true;
        return this;
    }

    /** Writes the value in place of the built-in number. */
    public GaugeComponent valueFormat(DoubleFunction<String> valueFormat) {
        this.valueFormat = valueFormat;
        textDirty = true;
        return this;
    }

    /**
     * Smooth arc, off by default: drawn at screen-pixel size with see-through edges, and the end of the filled part
     * blends into the track. It costs rectangles, about one per screen pixel of edge; past {@value #SMOOTH_BUDGET}
     * times the GUI scale it takes fewer edge levels, then GUI units, then the blocky arc. Worked out again only
     * while the value glides.
     */
    public GaugeComponent smooth(boolean smooth) {
        this.smooth = smooth;
        smoothKey = Long.MIN_VALUE;
        return this;
    }

    /** Rectangles the last smooth arc took, 0 when blocky. */
    int smoothRects() {
        return smoothShown ? layer.rects() : 0;
    }

    public GaugeComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public GaugeComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- content ----

    /** Sets the value; the arc glides there. */
    public void setValue(double value) {
        if (value != target && !(value != value && target != target)) {
            target = value;
            textDirty = true;
        }
    }

    /** Sets the value with no glide. */
    public void snap(double value) {
        setValue(value);
        shown = Double.isFinite(value) ? clamp(value) : min;
    }

    public double value() {
        return target;
    }

    /** Where the arc has got to, right now. */
    double shownValue() {
        return shown;
    }

    /** The index of the zone a value is in, from 0. */
    public int zoneOf(double value) {
        int zone = 0;
        while (zone < cuts.length && value >= cuts[zone]) {
            zone++;
        }
        return zone;
    }

    /** The colour the arc has at this value in a theme. */
    public int colorFor(Theme theme, double value) {
        return zoneInk(theme.palette(), zoneOf(value));
    }

    /** The square the arc is drawn in. */
    Box discBox() {
        refresh();
        return new Box("disc", discX, discY, side, side);
    }

    /** True when the last layout had room for the outer band. */
    boolean bandShows() {
        refresh();
        return band;
    }

    /** True when the value's text is drawn. */
    boolean valueShows() {
        refresh();
        return showValue;
    }

    /** True when the label is drawn. */
    boolean labelShows() {
        refresh();
        return showLabel;
    }

    private double clamp(double value) {
        return Math.max(min, Math.min(max, value));
    }

    private double fractionOf(double value) {
        return max > min ? (clamp(value) - min) / (max - min) : 0D;
    }

    // ---- working it out ----

    private void refresh() {
        if (dirty || laidW != w || laidH != h) {
            dirty = false;
            laidW = w;
            laidH = h;
            layout();
            buildCells();
            colouredFor = null;
            textDirty = true;
        }
        if (textDirty) {
            textDirty = false;
            buildText();
        }
    }

    private void layout() {
        double down = sweep >= 180 ? Math.sin(Math.toRadians((sweep - 180) / 2D)) : 0D;
        boolean under = !label.isEmpty() && sweep <= 200;
        int roomH = under ? h - LINE_PITCH : h;
        int byHeight = (int) Math.floor(roomH / (1D + down));
        int byWidth = w / 2;
        radius = Math.max(0, Math.min(byHeight, byWidth));
        side = 2 * radius;
        int used = (int) Math.ceil(radius * (1D + down));
        discX = x + (w - side) / 2;
        discY = y + Math.max(0, (roomH - used) / 2);
        band = radius >= MIN_R_FOR_BAND;
        int bandRoom = band ? BAND_W + BAND_GAP : 0;
        int ring = thickness > 0 ? thickness : Math.max(3, (radius - bandRoom) / 4);
        inner = Math.max(0, radius - bandRoom - ring);
    }

    /**
     * One cell a pixel: its place along the arc, plus {@link #BUCKETS} in the band; or -1. The band and the gap are
     * rings measured in cells from the rim, so both keep their width all the way round; the arc ends on the inner disc.
     */
    private void buildCells() {
        int need = side * side;
        if (cells.length != need) {
            cells = new int[need];
        }
        double mid = side / 2D;
        int arcFrom = band ? BAND_W + BAND_GAP : 0;
        double span = sweep / 360D;
        double start = 1D - span / 2D;
        for (int row = 0; row < side; row++) {
            double dy = row + 0.5D - mid;
            int k = PixelArt.fromMiddle(row, side);
            int rim = PixelArt.span(radius, k);
            int bandIn = PixelArt.span(radius, k, BAND_W);
            int arcOut = PixelArt.span(radius, k, arcFrom);
            int hole = PixelArt.span(inner, k);
            for (int col = 0; col < side; col++) {
                int i = PixelArt.fromMiddle(col, side);
                int cell = -1;
                if (i < rim && i >= hole) {
                    double dx = col + 0.5D - mid;
                    double turn = DataLook.turnOf(dx, dy) - start;
                    // Cells on the end lines are in at both ends, so the two ends mirror each other.
                    if (turn < -1e-9D) {
                        turn += 1D;
                    }
                    if (turn > 1D - 1e-9D) {
                        turn = 0D;
                    }
                    if (turn <= span + 1e-9D) {
                        double along = Math.min(1D, Math.max(0D, turn / span));
                        int bucket = Math.min(BUCKETS - 1, (int) (along * BUCKETS));
                        if (i < arcOut) {
                            cell = bucket;
                        } else if (band && i >= bandIn && !onCut(dx, dy, start, span)) {
                            cell = BUCKETS + bucket;
                        }
                    }
                }
                cells[row * side + col] = cell;
            }
        }
    }

    /** True on the line between two zones, in the band. */
    private boolean onCut(double dx, double dy, double start, double span) {
        for (int i = 0; i < cuts.length; i++) {
            if (cuts[i] <= min || cuts[i] >= max) {
                continue;
            }
            if (PixelArt.onRay(start + span * fractionOf(cuts[i]), dx, dy)) {
                return true;
            }
        }
        return false;
    }

    private void buildText() {
        Draw measure = TextMeasure.draw();
        valueText = Double.isFinite(target) ? (valueFormat != null ? valueFormat.apply(target) : DataLook.number(target)) + unit : "-";
        boolean under = sweep <= 200;
        int middleRoom = inner * 2 - 2 * CENTER_PAD;
        tip = null;
        showValue = false;
        showLabel = false;
        if (under) {
            // The words sit where the disc's lower half would be: the value in the arc's middle, the label beneath.
            int room = Math.min(w, chord(inner, LINE_PITCH));
            showValue = inner >= LINE_PITCH && room >= 8;
            fitValue = measure.ellipsize(valueText, room);
            fitLabel = measure.ellipsize(label, w);
            showLabel = !label.isEmpty() && h >= LINE_PITCH + side / 2;
            textY = discY + radius - LINE_PITCH + 1;
        } else {
            boolean two = !label.isEmpty() && inner >= 13;
            int room = two ? chord(inner, LINE_PITCH) : middleRoom;
            showValue = inner >= 7 && room >= 8;
            showLabel = two && showValue;
            fitValue = measure.ellipsize(valueText, room);
            fitLabel = measure.ellipsize(label, chord(inner, LINE_PITCH));
            textY = two ? discY + radius - LINE_PITCH + 1 : discY + radius - 4;
        }
    }

    private static int chord(int circle, int atDistance) {
        double half = Math.sqrt(Math.max(0D, (double) circle * circle - (double) atDistance * atDistance));
        return Math.max(0, (int) Math.floor(2D * half) - 2 * CENTER_PAD);
    }

    private int zoneInk(Palette p, int zone) {
        if (zone < zoneColors.length) {
            return DataLook.opaque(zoneColors[zone]);
        }
        int count = cuts.length + 1;
        int color;
        if (count == 1) {
            color = p.primary();
        } else if (count == 2) {
            color = zone == 0 ? p.good() : p.bad();
        } else if (zone == 0) {
            color = p.good();
        } else if (zone >= count - 1) {
            color = p.bad();
        } else {
            color = p.warn();
        }
        return Ink.readable(color, DataLook.back(p), Ink.MARK);
    }

    // ---- drawing ----

    private void step() {
        if (!Double.isFinite(target)) {
            shown = min;
            lastStep = BaseComponent.clock.getAsLong();
            firstFrame = false;
            return;
        }
        double goal = clamp(target);
        long now = BaseComponent.clock.getAsLong();
        if (firstFrame || reducedMotion) {
            shown = goal;
        } else if (shown != goal) {
            long elapsed = lastStep == Long.MIN_VALUE ? 0L : Math.max(0L, now - lastStep);
            shown += (goal - shown) * (1D - Math.exp(-elapsed / GLIDE_MS));
            if (Math.abs(goal - shown) < (max - min) / 720D) {
                shown = goal;
            }
        }
        lastStep = now;
        firstFrame = false;
    }

    private void paintCells(Palette p) {
        double at = Double.isFinite(target) ? fractionOf(shown) : -1D;
        if (colouredFor == p && colouredActive == active && colouredAt == at) {
            return;
        }
        boolean newColours = colouredFor != p || colouredActive != active || zoneInks.length != cuts.length + 1;
        colouredFor = p;
        colouredActive = active;
        colouredAt = at;
        if (newColours) {
            if (zoneInks.length != cuts.length + 1) {
                zoneInks = new int[cuts.length + 1];
            }
            for (int i = 0; i < zoneInks.length; i++) {
                int ink = zoneInk(p, i);
                zoneInks[i] = active ? ink : DataLook.faded(p, ink);
            }
            trackInk = DataLook.wash(p);
        }
        int zone = Double.isFinite(target) ? zoneOf(shown) : 0;
        int fill = zoneInks[Math.min(zone, zoneInks.length - 1)];
        int filled = (int) Math.round(Math.max(0D, at) * BUCKETS);
        for (int i = 0; i < BUCKETS; i++) {
            colours[i] = i < filled ? fill : trackInk;
        }
        if (newColours) {
            for (int i = 0; i < BUCKETS; i++) {
                double value = min + (i + 0.5D) / BUCKETS * (max - min);
                colours[BUCKETS + i] = zoneInks[Math.min(zoneOf(value), zoneInks.length - 1)];
            }
        }
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        refresh();
        step();
        if (side <= 0) {
            return;
        }
        Palette p = t.palette();
        paintCells(p);
        if (smooth && buildSmooth(Smooth.scale(d), p)) {
            layer.emit(d, discX, discY);
        } else {
            smoothShown = false;
            DataLook.cells(d, discX, discY, side, cells, colours);
        }
        int middle = discX + radius;
        if (showValue) {
            d.text(fitValue, middle - d.width(fitValue) / 2, textY, active ? Ink.on(p, DataLook.back(p)) : p.disabledText());
        }
        if (showLabel) {
            int soft = active ? Ink.soft(p, DataLook.back(p)) : p.disabledText();
            d.text(fitLabel, middle - d.width(fitLabel) / 2, textY + LINE_PITCH, soft);
        }
    }

    // ---- smooth ----

    private boolean buildSmooth(int gs, Palette p) {
        long key = ((((long) side * 31L + gs) * 31L + Double.doubleToLongBits(colouredAt)) * 31L
                + System.identityHashCode(p)) * 31L + (active ? 1 : 0) + 7L * radius + 13L * inner;
        if (key == smoothKey) {
            return smoothShown;
        }
        smoothKey = key;
        int[] ladder = Smooth.ladder(gs);
        smoothShown = false;
        for (int step = 0; step < ladder.length; step += 2) {
            int sub = ladder[step];
            if ((long) side * sub * side * sub > Smooth.MAX_CELLS) {
                continue;
            }
            paintSmooth(sub, ladder[step + 1]);
            if (layer.rects() <= SMOOTH_BUDGET * Smooth.scaleOf(gs)) {
                smoothShown = true;
                return true;
            }
        }
        return false;
    }

    private static float clamp01(double v) {
        return v <= 0D ? 0F : v >= 1D ? 1F : (float) v;
    }

    private void paintSmooth(int sub, int levels) {
        int fs = side * sub;
        layer.begin(fs, fs, sub, levels);
        double px = 1D / sub;
        double mid = side / 2D;
        int arcOuter = band ? radius - BAND_W - BAND_GAP : radius;
        double span = sweep / 360D;
        double start = 1D - span / 2D;
        double at = colouredAt;
        int zone = Double.isFinite(target) ? zoneOf(shown) : 0;
        int fill = zoneInks[Math.min(zone, zoneInks.length - 1)];
        int marks = 0;
        double[] sines = new double[cuts.length];
        double[] cosines = new double[cuts.length];
        for (int i = 0; i < cuts.length; i++) {
            if (cuts[i] <= min || cuts[i] >= max) {
                continue;
            }
            double angle = 2D * Math.PI * (start + span * fractionOf(cuts[i]));
            sines[marks] = Math.sin(angle);
            cosines[marks++] = Math.cos(angle);
        }
        for (int fy = 0; fy < fs; fy++) {
            double dy = (fy + 0.5D) * px - mid;
            for (int fx = 0; fx < fs; fx++) {
                double dx = (fx + 0.5D) * px - mid;
                double r = Math.sqrt(dx * dx + dy * dy);
                if (r < inner - px || r > radius + px) {
                    continue;
                }
                double turn = DataLook.turnOf(dx, dy) - start;
                if (turn < 0D) {
                    turn += 1D;
                }
                double round = 2D * Math.PI * r;
                float angular;
                if (turn <= span) {
                    angular = clamp01(Math.min(turn, span - turn) * round / px + 0.5D);
                } else {
                    angular = clamp01(0.5D - Math.min(turn - span, 1D - turn) * round / px);
                }
                if (angular <= 0F) {
                    continue;
                }
                double along = Math.max(0D, Math.min(1D, turn <= span ? turn / span : (turn - span < 1D - turn ? 1D : 0D)));
                float arc = clamp01((r - inner) / px + 0.5D) * clamp01((arcOuter - r) / px + 0.5D) * angular;
                int ink = 0;
                if (arc > 0F) {
                    float filled = at < 0D ? 0F : layer.quantize(clamp01((at - along) * span * round / px + 0.5D));
                    ink = layer.tint(Smooth.lerp(trackInk, fill, filled), arc);
                } else if (band) {
                    float ring = clamp01((r - (radius - BAND_W)) / px + 0.5D) * clamp01((radius - r) / px + 0.5D) * angular;
                    if (ring > 0F) {
                        for (int i = 0; i < marks; i++) {
                            double ahead = sines[i] * dx - cosines[i] * dy;
                            double across = cosines[i] * dx + sines[i] * dy;
                            if (ahead > 0D) {
                                ring *= clamp01((Math.abs(across) - SEPARATOR) / px + 0.5D);
                            }
                        }
                        double value = min + along * (max - min);
                        ink = layer.tint(zoneInks[Math.min(zoneOf(value), zoneInks.length - 1)], ring);
                    }
                }
                if (ink != 0) {
                    layer.set(fx, fy, ink);
                }
            }
        }
        layer.merge();
    }

    // ---- tooltips ----

    @Override
    public List<String> tooltipAt(double mx, double my) {
        List<String> own = super.tooltipAt(mx, my);
        if (own != null && !own.isEmpty()) {
            return own;
        }
        if (!visible || !active || !isOver(mx, my)) {
            return own;
        }
        refresh();
        boolean told = showValue && fitValue.equals(valueText) && (label.isEmpty() || (showLabel && fitLabel.equals(label)));
        if (told) {
            return own;
        }
        if (tip == null) {
            tip = List.of(label.isEmpty() ? valueText : label + ": " + valueText);
        }
        return tip;
    }
}
