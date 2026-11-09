package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleFunction;
import java.util.function.IntConsumer;

/**
 * A pie or a donut with a legend, in the theme's colours, the same order as a {@link ChartComponent}'s series.
 *
 * <pre>{@code
 * DonutChartComponent ores = add(new DonutChartComponent(at(new Box("ores", 10, 10, 180, 80)))
 *         .centerLabel(words.get("mymod.blocks"))
 *         .otherLabel(words.get("mymod.other"))
 *         .onSelect(i -> open(i)));
 * ores.slice("Iron", 120).slice("Gold", 40).slice("Diamond", 7);
 * storage.slice("Cobblestone", 1_234, 0, ItemView.of("minecraft:cobblestone"));
 * }</pre>
 *
 * <p>The arcs are whole pixels and never overlap: each row of the picture is one rectangle per colour, worked out
 * once when the slices or the size change, so there is no seam, no doubled translucent edge and no allocation in
 * a frame. A value that is NaN, an infinity, zero or negative has no slice but keeps its legend row.
 *
 * <p><b>Small slices.</b> By default the slices too small to draw cleanly go together into one "Other" slice,
 * last, striped in a neutral colour of the theme. Too small is under {@link #minShare(double)} of the total
 * (half a percent by default) or under about 3 pixels of arc on the ring at this size, so a bigger chart
 * groups less. One small slice alone is never grouped, and the kept slices keep their colours. {@link
 * #maxSlices(int)} also keeps only the biggest ones; {@link #groupSmall(boolean)} turns all of it off. A slice
 * that stays very small, grouped or not, is widened to two clear pixels; the legend and the centre still tell
 * its true share, and Other's share is the sum of its slices. The pointer over Other, on the ring or in the
 * legend, lists its slices with their values and shares. A click on Other, or Enter on it, opens it: the ring
 * then shows only its slices (grouped again if need be), the legend starts with a back row, and the back row,
 * Escape or a click on it come back up. {@link #openOther(boolean)} turns the opening off: Other is then picked
 * like a slice, with {@link #OTHER}.
 *
 * <p><b>Indexes.</b> Every index is a slice's place in the order it was added; Other is {@link #OTHER} and the
 * back row {@link #BACK}. A slice inside Other has no legend row and draws on Other's arc.
 *
 * <p><b>Items.</b> A slice can show an item or any {@link IconArt} in its legend row, for the content of a
 * storage. With one icon the rows grow to 18 so the item keeps its 16; a bigger art shrinks by whole screen
 * pixels, never squashed. A thin bar of the slice's colour stays beside it to match the ring.
 *
 * <p>The pointer over a slice or its legend row lights it, pushes it two pixels out and shows it in the centre;
 * a pie has no centre, so it comes as a tooltip. A click calls {@link #onSelect(IntConsumer)}. With focus: the
 * arrows walk the slices, home and end jump, Enter or Space picks, Escape comes back up from Other, then puts
 * the keys away. The wheel scrolls a legend too long for its room. A box too small for a legend drops it, then
 * the centre goes, and the tooltip keeps telling the values.
 */
public class DonutChartComponent extends BaseComponent {
    /** Where the legend goes. {@link #AUTO} takes the side with more room, and none when neither has any. */
    public enum Legend { AUTO, RIGHT, BOTTOM, NONE }

    /** The index of the slice the small ones go into. */
    public static final int OTHER = -2;
    /** The index of the legend row that comes back up from an opened Other. */
    public static final int BACK = -3;

    private static final int HALO = 2;
    private static final int LINE = 10;
    private static final int ICON_LINE = ItemArt.ITEM_ROW;
    private static final int ICON = 16;
    private static final int ICON_BAR = 2;
    private static final int SWATCH = 5;
    private static final int SWATCH_GAP = 3;
    private static final int LEGEND_GAP = 6;
    private static final int LEGEND_MIN_W = 44;
    private static final int BAR = ScrollBar.WIDTH;
    private static final int MIN_SIDE = 12;
    private static final int MIN_CENTER = 11;
    private static final int TWO_LINES = 16;
    private static final int LINE_PITCH = 10;
    private static final int CENTER_PAD = 2;
    private static final double SEPARATOR = 0.71D;
    private static final int MIN_R_FOR_GAP = 8;
    /** The clear width a slice keeps between its two separators, at the inside of the ring. */
    private static final double MIN_VISIBLE = 2D;
    /** Under this clear width at the inside of the ring a slice goes into Other. */
    private static final double GROUP_UNDER = 3D;
    private static final int TIP_ROWS = 6;
    private static final float LIT_MIX = 0.22F;
    private static final float OTHER_SOFT = 0.4F;
    private static final float STRIPE = 0.35F;
    private static final String[] BACK_ART = {"..#", ".#.", "#..", ".#.", "..#"};

    private static final class Slice {
        final String name;
        final double value;
        final int color;
        final boolean custom;
        final IconArt icon;
        final int index;
        double from;
        double to;
        boolean grouped;
        boolean cut;
        String legendText = "";
        String share = "";
        String centerName = "";
        String centerValue = "";

        Slice(String name, double value, int color, boolean custom, IconArt icon, int index) {
            this.name = name == null ? "" : name;
            this.value = Double.isFinite(value) && value > 0D ? value : 0D;
            this.color = color;
            this.custom = custom;
            this.icon = icon;
            this.index = index;
        }
    }

    private final List<Slice> slices = new ArrayList<>();
    private final List<Slice> rows = new ArrayList<>();
    private final List<Slice> grouped = new ArrayList<>();
    private Slice other = new Slice("Other", 0D, 0, false, null, OTHER);
    private Slice back = new Slice("Back", 0D, 0, false, null, BACK);
    private final RowLook.HoverFade hover = new RowLook.HoverFade();

    private boolean pie;
    private int thickness;
    private Legend legend = Legend.AUTO;
    private String centerLabel = "";
    private String centerValue;
    private String emptyText;
    private String unit = "";
    private boolean shares = true;
    private DoubleFunction<String> valueFormat;
    private IntConsumer onSelect;
    private boolean groupSmall = true;
    private double minShare = 0.005D;
    private int maxSlices;
    /** Null until the mod sets one: the lang entry then. */
    private String otherLabel;
    private String backLabel;
    private Words words;
    private String moreLabel = "more";
    private boolean openOther = true;
    private int depth;
    private double groupValue;
    private boolean icons;

    private boolean dirty = true;
    private int laidW = -1;
    private int laidH = -1;
    private double total;
    private double levelTotal;
    private int discX;
    private int discY;
    private int side;
    private int outer;
    private int inner;
    private int[] cells = new int[0];
    private int[] colours = new int[0];
    private int otherInk;
    private int otherStripe;
    private boolean legendShown;
    private int legendX;
    private int legendY;
    private int legendW;
    private int legendRows;
    private final ScrollBar legendScroll = new ScrollBar();
    private boolean legendBar;
    private int rowH = LINE;
    private String idleTop = "";
    private String idleBottom = "";
    private String fitEmpty = "";
    private boolean centerShown;
    private boolean centerTwoLines;
    private int centerRoom;

    private int cursor = -1;
    private int lit = -1;
    private int litFor = -2;
    private float litFade = -1F;
    private Palette colouredFor;
    private SeriesPalette seriesPalette = SeriesPalette.THEME;
    private boolean colouredActive;
    private Theme colouredTheme;
    private int tipFor = -1;
    private List<String> tip;

    public DonutChartComponent(Box box) {
        super(box);
    }

    // ---- setup ----

    /** The colour scheme of the series without a colour of their own; {@link SeriesPalette#THEME} by default. */
    @Incubating
    public DonutChartComponent seriesPalette(SeriesPalette seriesPalette) {
        this.seriesPalette = Objects.requireNonNull(seriesPalette, "seriesPalette");
        colouredFor = null;
        dirty = true;
        return this;
    }

    /** Fills the middle: a pie has no centre text. */
    public DonutChartComponent pie(boolean pie) {
        this.pie = pie;
        dirty = true;
        return this;
    }

    /** The width of the ring. By default a third of the radius, and at least 4. */
    public DonutChartComponent thickness(int thickness) {
        this.thickness = Math.max(0, thickness);
        dirty = true;
        return this;
    }

    /** {@link Legend#AUTO} by default. */
    public DonutChartComponent legend(Legend legend) {
        this.legend = Objects.requireNonNull(legend, "legend");
        dirty = true;
        return this;
    }

    /** The line under the total in the centre. */
    public DonutChartComponent centerLabel(String centerLabel) {
        this.centerLabel = centerLabel == null ? "" : centerLabel;
        dirty = true;
        return this;
    }

    /** What the centre says in place of the total when no slice is lit. */
    public DonutChartComponent centerValue(String centerValue) {
        this.centerValue = centerValue;
        dirty = true;
        return this;
    }

    /** What shows with nothing to draw. */
    public DonutChartComponent emptyText(String emptyText) {
        this.emptyText = emptyText;
        dirty = true;
        return this;
    }

    /** Follows every value, as in {@code " kg"}. */
    public DonutChartComponent unit(String unit) {
        this.unit = unit == null ? "" : unit;
        dirty = true;
        return this;
    }

    /** Writes the values in place of the built-in number. */
    public DonutChartComponent valueFormat(DoubleFunction<String> valueFormat) {
        this.valueFormat = valueFormat;
        dirty = true;
        return this;
    }

    /** False for the values in the legend in place of the shares. Shares by default. */
    public DonutChartComponent shares(boolean shares) {
        this.shares = shares;
        dirty = true;
        return this;
    }

    /** Called with a slice's index on a click, or on Enter or Space with the keys on it. */
    public DonutChartComponent onSelect(IntConsumer onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    /** False to draw every slice on its own, however small, and to ignore {@link #maxSlices(int)}. True by default. */
    public DonutChartComponent groupSmall(boolean groupSmall) {
        this.groupSmall = groupSmall;
        depth = 0;
        dirty = true;
        return this;
    }

    /** A slice under this share of the total, from 0 to 1, goes into Other. 0.005 by default; 0 for the size alone. */
    public DonutChartComponent minShare(double minShare) {
        this.minShare = Double.isFinite(minShare) ? Math.max(0D, Math.min(1D, minShare)) : 0D;
        dirty = true;
        return this;
    }

    /** Keeps at most this many slices, the biggest, and groups the rest. 0, the default, for no limit. */
    public DonutChartComponent maxSlices(int maxSlices) {
        this.maxSlices = Math.max(0, maxSlices);
        dirty = true;
        return this;
    }

    /** The name of the slice the small ones go into. "Other" by default: pass it through your translations. */
    public DonutChartComponent otherLabel(String otherLabel) {
        this.otherLabel = otherLabel == null ? "" : otherLabel;
        dirty = true;
        return this;
    }

    @Override
    void words(Words words) {
        this.words = words;
        dirty = true;
    }

    private String otherText() {
        return text(otherLabel, words, "minuit.donut.other", "Other");
    }

    private String backText() {
        return text(backLabel, words, "minuit.back", "Back");
    }

    /** The legend row that comes back up from an opened Other. "Back" by default. */
    public DonutChartComponent backLabel(String backLabel) {
        this.backLabel = backLabel == null ? "" : backLabel;
        dirty = true;
        return this;
    }

    /** The word after the count of slices Other's tooltip leaves out, as in "+4 more". "more" by default. */
    public DonutChartComponent moreLabel(String moreLabel) {
        this.moreLabel = moreLabel == null ? "" : moreLabel;
        tipFor = -1;
        return this;
    }

    /** False to pick Other like a slice, with {@link #OTHER}, in place of opening it. True by default. */
    public DonutChartComponent openOther(boolean openOther) {
        this.openOther = openOther;
        if (!openOther) {
            depth = 0;
        }
        dirty = true;
        return this;
    }

    public DonutChartComponent active(boolean active) {
        this.active = active;
        if (!active) {
            cursor = -1;
        }
        return this;
    }

    @Override
    public DonutChartComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- content ----

    /** Adds a slice with the next colour of the theme's order. */
    public DonutChartComponent slice(String name, double value) {
        slices.add(new Slice(name, value, 0, false, null, slices.size()));
        changed();
        return this;
    }

    /** Adds a slice with a colour of your own. */
    public DonutChartComponent slice(String name, double value, int color) {
        slices.add(new Slice(name, value, color, true, null, slices.size()));
        changed();
        return this;
    }

    /**
     * Adds a slice with an item or another picture in its legend row. A colour of 0 takes the next colour of the
     * theme's order. A null icon is a plain slice.
     */
    public DonutChartComponent slice(String name, double value, int color, IconArt icon) {
        slices.add(new Slice(name, value, color, color != 0, icon, slices.size()));
        changed();
        return this;
    }

    /** Forgets every slice. */
    public DonutChartComponent clear() {
        slices.clear();
        cursor = -1;
        legendScroll.offset(0);
        depth = 0;
        changed();
        return this;
    }

    private void changed() {
        dirty = true;
        colouredFor = null;
        tipFor = -1;
    }

    public int sliceCount() {
        return slices.size();
    }

    public double total() {
        refresh();
        return total;
    }

    /** A slice's share of the total, from 0 to 1. Other's is the sum of its slices'. */
    public double share(int index) {
        refresh();
        if (total <= 0D) {
            return 0D;
        }
        if (index == OTHER) {
            return other.value / total;
        }
        return index == BACK ? 0D : slices.get(index).value / total;
    }

    /** The start of the arc a slice is drawn on, in clockwise turns from the top. Other's for a slice inside it. */
    public double arcFrom(int index) {
        refresh();
        Slice s = arcOf(index);
        return s == null ? 0D : s.from;
    }

    /** The end of the arc a slice is drawn on, in clockwise turns from the top. Other's for a slice inside it. */
    public double arcTo(int index) {
        refresh();
        Slice s = arcOf(index);
        return s == null ? 0D : s.to;
    }

    private Slice arcOf(int index) {
        int row = rowOf(index);
        if (row >= 0) {
            return rows.get(row);
        }
        if (index >= 0 && index < slices.size() && slices.get(index).grouped) {
            return other;
        }
        return null;
    }

    /** True when the slice is inside Other, at the level in view. */
    public boolean inOther(int index) {
        refresh();
        return index >= 0 && index < slices.size() && slices.get(index).grouped;
    }

    /** How many slices are inside Other, at the level in view. 0 when there is no Other. */
    public int otherCount() {
        refresh();
        return rowOf(OTHER) >= 0 ? grouped.size() : 0;
    }

    /** How many times Other is opened: 0 shows every slice. */
    public int depth() {
        refresh();
        return depth;
    }

    /** How many legend rows there are: the slices in view, Other, and the back row when Other is opened. */
    public int rowCount() {
        refresh();
        return rows.size();
    }

    /** The square the disc is drawn in, without the room for a lit slice. */
    Box discBox() {
        refresh();
        return new Box("disc", discX + HALO, discY + HALO, 2 * outer, 2 * outer);
    }

    public boolean legendShows() {
        refresh();
        return legendShown;
    }

    public boolean centerShows() {
        refresh();
        return centerShown;
    }

    /** How many legend rows are in view. */
    int legendRowsShown() {
        refresh();
        return legendRows;
    }

    /** The first legend row in view. */
    int legendFirst() {
        refresh();
        return legendScroll.offset();
    }

    /** A slice's legend row, or Other's or the back row's. Empty when it is not in view. */
    Box legendBox(int index) {
        refresh();
        int row = rowOf(index);
        if (!legendShown || row < legendScroll.offset() || row >= legendScroll.offset() + legendRows) {
            return new Box("legend" + index, 0, 0, 0, 0);
        }
        return new Box("legend" + index, legendX, legendY + (row - legendScroll.offset()) * rowH, legendTextW(), rowH);
    }

    /** The slice lit in the last frame, {@link #OTHER}, {@link #BACK}, or -1. */
    int lit() {
        return indexOf(lit);
    }

    /** The slice the keys are on, {@link #OTHER}, {@link #BACK}, or -1. */
    public int cursor() {
        return indexOf(cursor);
    }

    /** The slice at a point of the disc, {@link #OTHER}, or -1. */
    int sliceAt(double mx, double my) {
        return indexOf(rowAt(mx, my));
    }

    private int indexOf(int row) {
        return row < 0 || row >= rows.size() ? -1 : rows.get(row).index;
    }

    private int rowOf(int index) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).index == index) {
                return i;
            }
        }
        return -1;
    }

    private int rowAt(double mx, double my) {
        refresh();
        if (side <= 0 || levelTotal <= 0D) {
            return -1;
        }
        double dx = mx - (discX + side / 2D);
        double dy = my - (discY + side / 2D);
        double r2 = dx * dx + dy * dy;
        double reach = outer + HALO;
        if (r2 > reach * reach || r2 < (double) inner * inner) {
            return -1;
        }
        return rowOfTurn(DataLook.turnOf(dx, dy));
    }

    // ---- working it out ----

    private String valueText(double value) {
        return (valueFormat != null ? valueFormat.apply(value) : DataLook.number(value)) + unit;
    }

    private static String shareText(double share) {
        if (share > 0D && share < 0.01D) {
            return "<1%";
        }
        return Math.round(share * 100D) + "%";
    }

    private void refresh() {
        if (!dirty && laidW == w && laidH == h) {
            return;
        }
        dirty = false;
        laidW = w;
        laidH = h;
        total = 0D;
        icons = false;
        for (int i = 0; i < slices.size(); i++) {
            total += slices.get(i).value;
            icons |= slices.get(i).icon != null;
        }
        rowH = icons ? ICON_LINE : LINE;
        back = back.name.equals(backText()) ? back : new Slice(backText(), 0D, 0, false, null, BACK);
        rows.clear();
        rows.addAll(slices);
        layout();
        // The grouping needs the ring's size and the legend's room needs the rows: twice settles both.
        for (int pass = 0; pass < 2; pass++) {
            group();
            layout();
        }
        arcs();
        buildCells();
        buildText();
        colouredFor = null;
        tipFor = -1;
        if (cursor >= rows.size()) {
            cursor = -1;
        }
    }

    /** Works out the rows of the level in view: the slices kept, Other last, the back row first when opened. */
    private void group() {
        for (int i = 0; i < slices.size(); i++) {
            Slice s = slices.get(i);
            s.grouped = false;
            s.from = 0D;
            s.to = 0D;
        }
        List<Slice> members = slices;
        int level = 0;
        while (true) {
            int small = markSmall(members);
            if (level == depth || small == 0) {
                depth = level;
                rows.clear();
                grouped.clear();
                groupValue = 0D;
                if (level > 0) {
                    rows.add(back);
                }
                for (int i = 0; i < members.size(); i++) {
                    Slice s = members.get(i);
                    if (s.cut) {
                        s.grouped = true;
                        grouped.add(s);
                        groupValue += s.value;
                    } else {
                        rows.add(s);
                    }
                }
                if (small > 0) {
                    other = new Slice(otherText(), groupValue, 0, false, null, OTHER);
                    rows.add(other);
                }
                return;
            }
            List<Slice> next = new ArrayList<>(small);
            for (int i = 0; i < members.size(); i++) {
                if (members.get(i).cut) {
                    next.add(members.get(i));
                }
            }
            members = next;
            level++;
        }
    }

    /** Marks the slices to group with {@code cut} and counts them: never one alone, never all of them. */
    private int markSmall(List<Slice> members) {
        double sum = 0D;
        int positive = 0;
        for (int i = 0; i < members.size(); i++) {
            members.get(i).cut = false;
            if (members.get(i).value > 0D) {
                sum += members.get(i).value;
                positive++;
            }
        }
        if (!groupSmall || positive < 3 || sum <= 0D) {
            return 0;
        }
        double around = 2D * Math.PI * ringRadius();
        double gap = outer >= MIN_R_FOR_GAP ? 2D * SEPARATOR : 0D;
        List<Slice> biggest = new ArrayList<>(positive);
        for (int i = 0; i < members.size(); i++) {
            Slice s = members.get(i);
            if (s.value <= 0D) {
                continue;
            }
            double part = s.value / sum;
            s.cut = part < minShare || (around > 0D && part * around < GROUP_UNDER + gap);
            biggest.add(s);
        }
        biggest.sort((a, b) -> Double.compare(b.value, a.value));
        if (maxSlices > 0) {
            for (int i = maxSlices; i < biggest.size(); i++) {
                biggest.get(i).cut = true;
            }
        }
        int small = 0;
        for (int i = 0; i < biggest.size(); i++) {
            small += biggest.get(i).cut ? 1 : 0;
        }
        if (small == positive) {
            // All of them small: keep as many of the biggest as the ring holds, with room for Other.
            int holds = around > 0D ? (int) Math.floor(around / (GROUP_UNDER + gap)) - 1 : 1;
            int keep = Math.max(1, Math.min(maxSlices > 0 ? maxSlices : positive, holds));
            for (int i = 0; i < keep; i++) {
                biggest.get(i).cut = false;
            }
            small -= keep;
        }
        if (small < 2) {
            for (int i = 0; i < biggest.size(); i++) {
                biggest.get(i).cut = false;
            }
            return 0;
        }
        return small;
    }

    /** Where a slice is thinnest: the inside of the ring, or halfway out on a pie. */
    private double ringRadius() {
        return Math.max(inner, outer / 2D);
    }

    private void layout() {
        int widest = 0;
        for (int i = 0; i < rows.size(); i++) {
            widest = Math.max(widest, TextMeasure.width(rows.get(i).name));
        }
        int mark = icons ? ICON_BAR + 1 + ICON : SWATCH;
        int rowsN = rows.size();
        int wanted = mark + SWATCH_GAP + widest + LEGEND_GAP + TextMeasure.width("100%");
        Legend place = legend;
        if (place == Legend.AUTO) {
            int roomRight = w - Math.min(h, w * 3 / 5) - LEGEND_GAP;
            if (rowsN == 0) {
                place = Legend.NONE;
            } else if (roomRight >= Math.min(wanted, 60)) {
                place = Legend.RIGHT;
            } else if (h - w >= Math.min(rowsN, 2) * rowH + LEGEND_GAP) {
                place = Legend.BOTTOM;
            } else {
                place = Legend.NONE;
            }
        }
        int discRoomW = w;
        int discRoomH = h;
        legendShown = false;
        if (place == Legend.RIGHT && rowsN > 0) {
            int legendSide = Math.max(LEGEND_MIN_W, Math.min(wanted, w - Math.min(h, w * 3 / 5) - LEGEND_GAP));
            if (w - legendSide - LEGEND_GAP >= MIN_SIDE + 2 * HALO && h >= rowH) {
                discRoomW = w - legendSide - LEGEND_GAP;
                legendShown = true;
                legendW = legendSide;
                legendX = x + discRoomW + LEGEND_GAP;
                legendRows = Math.min(rowsN, h / rowH);
                legendY = y + (h - legendRows * rowH) / 2;
            }
        } else if (place == Legend.BOTTOM && rowsN > 0) {
            int fit = Math.min(rowsN, Math.max(1, (h - MIN_SIDE - 2 * HALO - LEGEND_GAP) / rowH));
            if (h - fit * rowH - LEGEND_GAP >= MIN_SIDE + 2 * HALO && w >= LEGEND_MIN_W) {
                discRoomH = h - fit * rowH - LEGEND_GAP;
                legendShown = true;
                legendW = Math.min(w, wanted);
                legendX = x + (w - legendW) / 2;
                legendRows = fit;
                legendY = y + discRoomH + LEGEND_GAP;
            }
        }
        legendBar = legendShown && legendRows < rowsN;
        if (legendBar && legendW < LEGEND_MIN_W + BAR) {
            legendBar = false;
        }
        legendScroll.content(legendShown ? rowsN : 0, legendRows);
        side = Math.max(0, Math.min(discRoomW, discRoomH));
        outer = Math.max(0, side / 2 - HALO);
        side = outer > 0 ? 2 * (outer + HALO) : 0;
        discX = x + (discRoomW - side) / 2;
        discY = y + (discRoomH - side) / 2;
        int ring = thickness > 0 ? thickness : Math.max(4, outer / 3);
        inner = pie ? 0 : Math.max(0, outer - ring);
        int middle = pie ? 0 : 2 * inner;
        centerShown = middle >= MIN_CENTER * 2;
        centerTwoLines = inner >= TWO_LINES;
        centerRoom = centerTwoLines ? chord(inner, LINE_PITCH) : chord(inner, 4);
    }

    /** The width inside a circle at a distance from its middle. */
    private static int chord(int radius, int atDistance) {
        double half = Math.sqrt(Math.max(0D, (double) radius * radius - (double) atDistance * atDistance));
        return Math.max(0, (int) Math.floor(2D * half) - 2 * CENTER_PAD);
    }

    /** Gives each row its arc, widening those too thin to see clearly at the cost of the rest. */
    private void arcs() {
        levelTotal = 0D;
        int parts = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).value > 0D) {
                levelTotal += rows.get(i).value;
                parts++;
            }
        }
        if (levelTotal <= 0D) {
            for (int i = 0; i < rows.size(); i++) {
                rows.get(i).from = 0D;
                rows.get(i).to = 0D;
            }
            return;
        }
        double radius = ringRadius();
        double gap = parts > 1 && outer >= MIN_R_FOR_GAP ? 2D * SEPARATOR : 0D;
        double least = radius <= 0D ? 0D : (MIN_VISIBLE + gap) / (2D * Math.PI * radius);
        least = Math.min(least, 1D / parts);
        double spare = 1D;
        double big = 0D;
        for (int i = 0; i < rows.size(); i++) {
            double value = rows.get(i).value;
            if (value <= 0D) {
                continue;
            }
            if (value / levelTotal < least) {
                spare -= least;
            } else {
                big += value;
            }
        }
        double scale = big > 0D ? Math.max(0D, spare) / big : 0D;
        double at = 0D;
        for (int i = 0; i < rows.size(); i++) {
            Slice s = rows.get(i);
            s.from = at;
            if (s.value > 0D) {
                at += s.value / levelTotal < least ? least : s.value * scale;
            }
            s.to = at;
        }
        // A rounding error must not leave a hole at the top.
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i).value > 0D) {
                rows.get(i).to = 1D + 1e-9D;
                break;
            }
        }
    }

    /**
     * One cell a pixel: row {@code i}, or {@code n + i} in the ring a lit slice grows into, {@code 2n} for an empty
     * ring, and {@code 2n + 1} or {@code 2n + 2} for Other's stripes, or -1.
     */
    private void buildCells() {
        int count = rows.size();
        int need = side * side;
        if (cells.length != need) {
            cells = new int[need];
        }
        if (colours.length != 2 * count + 3) {
            colours = new int[2 * count + 3];
        }
        double mid = side / 2D;
        int parts = 0;
        for (int i = 0; i < count; i++) {
            if (rows.get(i).value > 0D) {
                parts++;
            }
        }
        boolean empty = parts == 0;
        int reach = side / 2;
        for (int row = 0; row < side; row++) {
            double dy = row + 0.5D - mid;
            int k = PixelArt.fromMiddle(row, side);
            int halo = PixelArt.span(reach, k);
            int ring = PixelArt.span(reach, k, HALO);
            int hole = PixelArt.span(inner, k);
            for (int col = 0; col < side; col++) {
                int i = PixelArt.fromMiddle(col, side);
                int cell = -1;
                if (i < halo && i >= hole) {
                    boolean inRing = i < ring;
                    if (empty) {
                        cell = inRing ? 2 * count : -1;
                    } else {
                        double dx = col + 0.5D - mid;
                        int at = rowOfTurn(DataLook.turnOf(dx, dy));
                        if (at >= 0 && !(parts > 1 && outer >= MIN_R_FOR_GAP && onSeparator(rows.get(at), dx, dy))) {
                            boolean stripe = rows.get(at) == other && ((row + col) >> 1 & 1) == 1;
                            if (stripe) {
                                cell = inRing ? 2 * count + 1 : 2 * count + 2;
                            } else {
                                cell = inRing ? at : count + at;
                            }
                        }
                    }
                }
                cells[row * side + col] = cell;
            }
        }
    }

    private int rowOfTurn(double turn) {
        for (int i = 0; i < rows.size(); i++) {
            Slice s = rows.get(i);
            if (s.value > 0D && turn >= s.from && turn < s.to) {
                return i;
            }
        }
        return -1;
    }

    /** True on the one-pixel line at either edge of a slice, so that the gap is one line between two slices. */
    private static boolean onSeparator(Slice s, double dx, double dy) {
        return PixelArt.onRay(s.from, dx, dy) || PixelArt.onRay(s.to, dx, dy);
    }

    private void buildText() {
        Draw measure = TextMeasure.draw();
        int mark = icons ? ICON_BAR + 1 + ICON : SWATCH;
        int textRoom = legendTextW() - mark - SWATCH_GAP;
        if (depth > 0) {
            idleTop = levelTotal > 0D ? valueText(levelTotal) : "";
            idleBottom = otherText();
        } else {
            idleTop = centerValue != null ? centerValue : total > 0D ? valueText(total) : "";
            idleBottom = centerLabel;
        }
        int shareW = 0;
        for (int i = 0; i < rows.size(); i++) {
            Slice s = rows.get(i);
            s.share = s == back ? "" : shares ? shareText(total <= 0D ? 0D : s.value / total) : valueText(s.value);
            shareW = Math.max(shareW, TextMeasure.width(s.share));
        }
        for (int i = 0; i < rows.size(); i++) {
            Slice s = rows.get(i);
            s.legendText = measure.ellipsize(s.name, Math.max(0, textRoom - (s == back ? 0 : shareW + LEGEND_GAP)));
            s.centerName = measure.ellipsize(s.name, centerRoom);
            s.centerValue = measure.ellipsize(s.share, centerRoom);
        }
        fitEmpty = emptyText == null ? "" : measure.ellipsize(emptyText, centerRoom);
        idleTop = measure.ellipsize(idleTop, centerRoom);
        idleBottom = measure.ellipsize(idleBottom, centerRoom);
    }

    private int legendTextW() {
        return legendBar ? legendW - BAR - 1 : legendW;
    }

    // ---- drawing ----

    private int sliceColour(Theme t, int row) {
        Slice s = rows.get(row);
        if (s == other) {
            return otherInk;
        }
        if (s == back) {
            return active ? Ink.soft(t.palette(), DataLook.back(t.palette())) : t.palette().disabledText();
        }
        int color = s.custom ? DataLook.opaque(s.color) : DataLook.seriesColour(t, seriesPalette, s.index);
        return active ? color : DataLook.faded(t.palette(), color);
    }

    private int legendRowAt(double mx, double my) {
        if (!legendShown || mx < legendX || mx >= legendX + legendTextW() || my < legendY || my >= legendY + legendRows * rowH) {
            return -1;
        }
        int row = legendScroll.offset() + (int) Math.floor((my - legendY) / rowH);
        return row < rows.size() ? row : -1;
    }

    private boolean overLegend(double mx, double my) {
        return legendShown && mx >= legendX && mx < legendX + legendW && my >= legendY && my < legendY + legendRows * rowH;
    }

    private void paintCells(Theme t) {
        Palette p = t.palette();
        int count = rows.size();
        float fade = hover.row() == lit ? hover.fade() : 1F;
        if (colouredFor == p && colouredTheme == t && colouredActive == active && litFor == lit && litFade == fade) {
            return;
        }
        colouredFor = p;
        colouredTheme = t;
        colouredActive = active;
        litFor = lit;
        litFade = fade;
        int neutral = Ink.readable(Shapes.mix(DataLook.opaque(p.textSoft()), DataLook.back(p), OTHER_SOFT), DataLook.back(p), Ink.MARK);
        otherInk = active ? neutral : DataLook.faded(p, neutral);
        otherStripe = Shapes.mix(otherInk, DataLook.back(p), STRIPE);
        int lightInk = DataLook.opaque(Ink.on(p, DataLook.back(p)));
        int otherRow = -1;
        for (int i = 0; i < count; i++) {
            int color = sliceColour(t, i);
            int bright = Shapes.mix(color, lightInk, LIT_MIX * fade);
            colours[i] = i == lit ? bright : color;
            colours[count + i] = i == lit ? bright : 0;
            if (rows.get(i) == other) {
                otherRow = i;
            }
        }
        colours[2 * count] = DataLook.wash(p);
        int stripe = otherRow >= 0 && otherRow == lit ? Shapes.mix(otherStripe, lightInk, LIT_MIX * fade) : otherStripe;
        colours[2 * count + 1] = stripe;
        colours[2 * count + 2] = otherRow >= 0 && otherRow == lit ? stripe : 0;
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        refresh();
        Palette p = t.palette();
        boolean pointer = active && hovered && settled();
        int over = -1;
        if (pointer) {
            over = legendRowAt(mx, my);
            if (over < 0) {
                over = rowAt(mx, my);
            }
        }
        hover.step(over);
        lit = over >= 0 ? over : focused && active ? cursor : -1;
        paintCells(t);
        if (side > 0) {
            DataLook.cells(d, discX, discY, side, cells, colours);
            if (centerShown) {
                drawCenter(d, p);
            }
        }
        if (total <= 0D && !centerShown && emptyText != null && !emptyText.isEmpty()) {
            RowLook.drawEmpty(d, p, emptyText, active, x, y, w, h);
        }
        if (legendShown) {
            drawLegend(d, t, over, hovered && overLegend(mx, my));
        }
    }

    private void drawCenter(Draw d, Palette p) {
        int middle = discX + side / 2;
        int cy = discY + side / 2;
        int ink = active ? Ink.on(p, DataLook.back(p)) : p.disabledText();
        int soft = active ? Ink.soft(p, DataLook.back(p)) : p.disabledText();
        String top;
        String bottom;
        if (total <= 0D) {
            top = fitEmpty;
            bottom = "";
        } else if (lit >= 0 && rows.get(lit) != back) {
            Slice s = rows.get(lit);
            top = s.centerValue;
            bottom = s.centerName;
        } else {
            top = idleTop;
            bottom = idleBottom;
        }
        if (centerTwoLines && !bottom.isEmpty()) {
            d.text(top, middle - d.width(top) / 2, cy - LINE_PITCH + 1, ink);
            d.text(bottom, middle - d.width(bottom) / 2, cy + 1, soft);
        } else {
            d.text(top, middle - d.width(top) / 2, cy - 4, ink);
        }
    }

    private void drawLegend(Draw d, Theme t, int over, boolean inLegend) {
        Palette p = t.palette();
        int textW = legendTextW();
        int soft = active ? Ink.soft(p, DataLook.back(p)) : p.disabledText();
        int ink = active ? Ink.on(p, DataLook.back(p)) : p.disabledText();
        int mark = icons ? ICON_BAR + 1 + ICON : SWATCH;
        for (int row = 0; row < legendRows; row++) {
            int index = legendScroll.offset() + row;
            Slice s = rows.get(index);
            int top = legendY + row * rowH;
            if (index == lit) {
                float fade = index == over ? hover.fade() : 1F;
                d.fill(legendX - 1, top, legendX + textW + 1, top + rowH, RowLook.tint(p, fade));
            }
            int color = sliceColour(t, index);
            if (s == back) {
                DataLook.art(d, BACK_ART, legendX + (mark - 3) / 2, top + (rowH - BACK_ART.length) / 2, color);
            } else if (icons && s.icon != null) {
                drawIcon(d, p, s, color, legendX, top);
            } else if (icons) {
                drawSwatch(d, s, color, legendX + ICON_BAR + 1 + (ICON - SWATCH) / 2, top + (rowH - SWATCH) / 2);
            } else {
                drawSwatch(d, s, color, legendX, top + (rowH - SWATCH) / 2);
            }
            int textY = top + (rowH - LINE) / 2 + 1;
            d.text(s.legendText, legendX + mark + SWATCH_GAP, textY, index == lit ? ink : soft);
            d.text(s.share, legendX + textW - d.width(s.share), textY, index == lit ? ink : soft);
        }
        if (legendBar) {
            int barX = ScrollBar.x(t.frame(), legendX + legendW);
            int trackH = legendRows * rowH;
            legendScroll.draw(d, t, barX, legendY, trackH, active && inLegend);
            if (!active) {
                RowLook.dimBar(d, p, barX, legendY, BAR, trackH);
            }
        }
    }

    private void drawSwatch(Draw d, Slice s, int color, int left, int top) {
        if (s == other) {
            d.fill(left, top, left + SWATCH, top + SWATCH, color);
            for (int r = 0; r < SWATCH; r++) {
                for (int c = 0; c < SWATCH; c++) {
                    if (((r + c) >> 1 & 1) == 1) {
                        d.fill(left + c, top + r, left + c + 1, top + r + 1, otherStripe);
                    }
                }
            }
        } else if (s.value > 0D) {
            d.fill(left, top, left + SWATCH, top + SWATCH, color);
        } else {
            DataLook.outline(d, left, top, SWATCH, SWATCH, color);
        }
    }

    /** The slice's colour as a thin bar, then its picture in 16 units: a bigger one shrinks by whole screen pixels. */
    private void drawIcon(Draw d, Palette p, Slice s, int color, int left, int top) {
        int barTop = top + 1;
        if (s.value > 0D) {
            d.fill(left, barTop, left + ICON_BAR, top + rowH - 1, color);
        } else {
            DataLook.outline(d, left, barTop, ICON_BAR, rowH - 2, color);
        }
        int ix = left + ICON_BAR + 1;
        int iy = top + (rowH - ICON) / 2;
        // Guarded: an item before a world is loaded draws a stand-in letter. Shrinks by whole screen pixels only.
        ItemArt.fit(d, p, s.icon, ix, iy, ICON, active ? Ink.on(p, DataLook.back(p)) : p.disabledText(), active);
    }

    // ---- tooltips ----

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!visible) {
            return null;
        }
        if (active) {
            refresh();
            int row = legendRowAt(mx, my);
            int at = row >= 0 ? row : rowAt(mx, my);
            if (at >= 0 && rows.get(at) == other) {
                return tipOf(at);
            }
            if (at >= 0 && rows.get(at) != back) {
                Slice s = rows.get(at);
                boolean centerTells = centerShown && row < 0;
                boolean legendTells = row >= 0 && s.legendText.equals(s.name);
                if (!centerTells && !legendTells) {
                    return tipOf(at);
                }
                if (centerTells && !s.centerName.equals(s.name)) {
                    return tipOf(at);
                }
            }
        }
        return super.tooltipAt(mx, my);
    }

    private String line(double value) {
        return valueText(value) + " (" + shareText(total <= 0D ? 0D : value / total) + ")";
    }

    private List<String> tipOf(int row) {
        if (tipFor != row || tip == null) {
            tipFor = row;
            Slice s = rows.get(row);
            if (s != other) {
                tip = List.of(s.name, line(s.value));
            } else {
                List<Slice> sorted = new ArrayList<>(grouped);
                sorted.sort((a, b) -> Double.compare(b.value, a.value));
                List<String> lines = new ArrayList<>();
                lines.add(otherText() + ": " + line(s.value));
                int shown = sorted.size() <= TIP_ROWS ? sorted.size() : TIP_ROWS - 1;
                for (int i = 0; i < shown; i++) {
                    lines.add(sorted.get(i).name + ": " + line(sorted.get(i).value));
                }
                if (sorted.size() > shown) {
                    lines.add("+" + (sorted.size() - shown) + (moreLabel.isEmpty() ? "" : " " + moreLabel));
                }
                tip = List.copyOf(lines);
            }
        }
        return tip;
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
        return accepts() && !slices.isEmpty();
    }

    private boolean cursorInView() {
        return cursor >= 0 && legendShown && cursor >= legendScroll.offset() && cursor < legendScroll.offset() + legendRows;
    }

    @Override
    protected int focusCorner() {
        return cursorInView() ? 0 : super.focusCorner();
    }

    @Override
    protected Box focusBox() {
        refresh();
        if (cursorInView()) {
            // The ring grows by 2 on each side: this lays it just around the row's lit face.
            int top = legendY + (cursor - legendScroll.offset()) * rowH;
            return new Box("row", legendX, top + 1, legendTextW(), rowH - 2);
        }
        return super.focusBox();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!accepts() || button != 0 || !isOver(mx, my)) {
            return false;
        }
        refresh();
        int at = legendRowAt(mx, my);
        if (at < 0) {
            at = rowAt(mx, my);
        }
        if (at < 0) {
            return overLegend(mx, my);
        }
        cursor = at;
        return pick(at);
    }

    /** Opens Other, comes back up, or calls {@link #onSelect}. */
    private boolean pick(int row) {
        Slice s = rows.get(row);
        if (s == back) {
            BaseComponent.click();
            goUp();
        } else if (s == other && openOther) {
            BaseComponent.click();
            depth++;
            dirty = true;
            legendScroll.offset(0);
            refresh();
            cursor = 0;
        } else if (onSelect != null) {
            BaseComponent.click();
            onSelect.accept(s.index);
        }
        return true;
    }

    private void goUp() {
        depth = Math.max(0, depth - 1);
        dirty = true;
        legendScroll.offset(0);
        refresh();
        cursor = rowOf(OTHER);
        if (cursor >= 0) {
            moveCursor(cursor);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || !settled() || !isOver(mx, my)) {
            return false;
        }
        refresh();
        if (!overLegend(mx, my) || legendRows >= rows.size()) {
            return false;
        }
        legendScroll.wheel(Math.signum(amount), 1);
        return true;
    }

    private void moveCursor(int to) {
        int count = rows.size();
        cursor = Math.max(0, Math.min(count - 1, to));
        if (legendShown) {
            if (cursor < legendScroll.offset()) {
                legendScroll.offset(cursor);
            } else if (cursor >= legendScroll.offset() + legendRows) {
                legendScroll.offset(cursor - legendRows + 1);
            }
        }
    }

    @Override
    protected boolean activate() {
        if (!accepts() || cursor < 0) {
            return false;
        }
        refresh();
        if (cursor >= rows.size()) {
            return false;
        }
        return pick(cursor);
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!accepts() || slices.isEmpty()) {
            return false;
        }
        refresh();
        switch (key) {
            case LEFT, UP -> moveCursor(cursor < 0 ? rows.size() - 1 : cursor - 1);
            case RIGHT, DOWN -> moveCursor(cursor < 0 ? 0 : cursor + 1);
            case HOME -> moveCursor(0);
            case END -> moveCursor(rows.size() - 1);
            case ENTER, SPACE -> {
                return activate();
            }
            case ESCAPE -> {
                if (depth > 0) {
                    boolean keys = cursor >= 0;
                    goUp();
                    if (!keys) {
                        cursor = -1;
                    }
                } else if (cursor < 0) {
                    return false;
                } else {
                    cursor = -1;
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
