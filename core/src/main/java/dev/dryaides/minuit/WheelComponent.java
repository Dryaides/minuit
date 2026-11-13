package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Redraw;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A prize wheel: segments around a hub, a pointer at the top, and a spin that eases out and lands exactly on the
 * segment it was told to.
 *
 * <pre>{@code
 * WheelComponent wheel = add(new WheelComponent(at(new Box("wheel", 10, 10, 120, 120)))
 *         .add(new WheelComponent.Segment("Diamond").item("minecraft:diamond").weight(1))
 *         .add(new WheelComponent.Segment("Iron").item("minecraft:iron_ingot").weight(4))
 *         .add("Nothing")
 *         .onLand(s -> give(s)));
 * // Multiplayer: the server picks the landing and a seed and sends both, everyone calls the same line.
 * wheel.spin(landing, seed);
 * }</pre>
 *
 * <h2>Segments</h2>
 * A segment has a label, a weight (its share of the wheel, also its odds for {@link #spin(Random)}), a colour (the
 * chroma's own colours by default, not darkened as a chart's lines are) and content: an item
 * ({@link Segment#item(ItemView)}), any {@link IconArt} such as a {@link SpriteIcon}, an {@link AsyncImage}, or a renderer of your own. Items and sprites are drawn at the
 * largest scale that fits the segment and stays crisp: a whole number of screen pixels per texel. Text cannot turn,
 * so labels and icons stay upright at the middle of their segment. An icon shrinks, never under its own size, to
 * make room for its label; when a label still does not fit on every segment of one size, none shows, the icons stand
 * for them and the tooltip names them.
 *
 * <h2>Spins</h2>
 * {@link #spin(int, long)} lands on a given segment; the seed only decides the number of turns and where in the
 * segment the pointer stops, so every player who gets the same two numbers sees the same spin. {@link #state()}
 * and {@link #restore(Spin)} hand a spin in progress to a screen opened late. With reduced motion the wheel jumps
 * to the result. A click on the hub, or Space or Enter with focus, asks for a spin: {@link #onSpinRequest(Runnable)}
 * to ask a server, or a local random spin when none is set.
 *
 * <h2>Cost</h2>
 * The game cannot turn a picture, so the wheel is rows of rectangles, worked out again only when the angle moves.
 * Each rectangle costs real time in the game, so a frame keeps to a budget of rectangles by using coarser cells.
 * The cell stays the same turning and at rest, so the pixel art and the colours do not change when it lands.
 * The rim and the lines between segments are the same rectangle as the row behind them, so they cost nothing.
 *
 * <h2>Full screen</h2>
 * {@link #fullscreen(boolean)} makes the box the area to dim (the screen's box or the panel's bounds): the wheel is
 * hidden until {@link #open(PanelComponent)}, then fills the area, and closes on Escape, or after landing with
 * {@link #closeOnLand(long)}. Add it last, as a {@link DialogComponent}.
 */
public class WheelComponent extends BaseComponent {
    /** Draws a segment's content in a square: the square's top left and side, in GUI units. */
    @FunctionalInterface
    public interface Content {
        void draw(Draw d, Theme theme, Segment segment, int left, int top, int side, boolean active);
    }

    /** A spin, enough to show it again anywhere. {@code start} is on the {@link BaseComponent#clock}, in ms. */
    public record Spin(int landing, long seed, long start, double from, long duration, boolean landed) {
    }

    /** One slice of the wheel. Changing one after it is added needs {@link WheelComponent#changed()}. */
    public static final class Segment {
        private final String label;
        private double weight = 1D;
        private int color;
        private boolean custom;
        private IconArt icon;
        private AsyncImage image;
        private Content content;
        private Object data;

        // Laid out by the wheel.
        int index;
        double from;
        double to;
        double contentR;
        String shown = "";
        int shownW;
        String restShown = "";
        int restW;
        int restX;
        int restY;
        float iconScale;
        int iconSide;
        int ink;
        int laidImageW = -1;

        public Segment(String label) {
            this.label = label == null ? "" : label;
        }

        /** Its share of the wheel and its odds. 1 by default; 0 or less leaves it off the wheel. */
        public Segment weight(double weight) {
            this.weight = Double.isFinite(weight) && weight > 0D ? weight : 0D;
            return this;
        }

        /** A colour of your own in place of the theme's. */
        public Segment color(int argb) {
            this.color = argb | 0xFF000000;
            this.custom = true;
            return this;
        }

        public Segment item(ItemView item) {
            this.icon = item;
            return this;
        }

        public Segment item(String id) {
            return item(ItemView.of(id));
        }

        /** Any icon: an {@link ItemView}, a {@link SpriteIcon}, a theme icon. */
        public Segment icon(IconArt icon) {
            this.icon = icon;
            return this;
        }

        /** A picture that may still be loading: a well with dots shows meanwhile. */
        public Segment image(AsyncImage image) {
            this.image = image;
            return this;
        }

        /** Draws the content yourself, in the square an icon would take. */
        public Segment content(Content content) {
            this.content = content;
            return this;
        }

        /** Anything of yours, for {@link WheelComponent#onLand(Consumer)}. */
        public Segment data(Object data) {
            this.data = data;
            return this;
        }

        public String label() {
            return label;
        }

        public double weight() {
            return weight;
        }

        public IconArt icon() {
            return icon;
        }

        public AsyncImage image() {
            return image;
        }

        public Object data() {
            return data;
        }

        /** Its place on the wheel, once added. */
        public int index() {
            return index;
        }

        boolean hasIcon() {
            return icon != null || image != null || content != null;
        }
    }

    private static final int MAX_RECTS = 700;
    public static final long DEFAULT_DURATION = 4200L;
    private static final int MAX_CELL = 8;
    private static final int MIN_TURNS = 4;
    private static final int EXTRA_TURNS = 3;
    private static final float MAX_ICON_SCALE = 4F;
    private static final int TEXT_H = 8;
    private static final int DIM_ALPHA = 0xB0;
    private static final float LIT_MIX = 0.25F;
    /** After a live landing the winner's inner rim blinks on and off: whole steps, no geometry change. */
    private static final long WON_BLINK = 150L;
    private static final int WON_BLINKS = 6;
    private static final long TICK_GAP_MS = 35L;

    private final List<Segment> segments = new ArrayList<>();
    private final List<Segment> view = Collections.unmodifiableList(segments);

    /** Null until the mod sets one: the lang entry then. */
    private String hubText;
    private boolean hubButton = true;
    private String emptyText;
    private Words words;
    private String title = "";
    private long duration = DEFAULT_DURATION;
    private long delay;
    private boolean muted;
    private Consumer<Segment> onLand;
    private Runnable onSpinRequest;
    private Supplier<Random> randomSource = Random::new;
    private Runnable onClose;

    private boolean fullscreen;
    private boolean open;
    private long closeOnLand = -1L;
    private PanelComponent host;
    private BaseComponent focusBefore;

    // The spin.
    private Spin spin;
    private double end;
    private double rotation;
    /** The angle the picture is drawn at: {@link #rotation} in whole steps while it turns. */
    private double drawn;
    private boolean landed;
    private boolean fired = true;
    private long landedAt;
    private int result = -1;
    private boolean quietLand;
    private boolean[] wonMask;
    private int tickAt = -1;
    private long tickTime;

    // Layout.
    private boolean dirty = true;
    private int laidX = Integer.MIN_VALUE;
    private int laidY;
    private int laidW;
    private int laidH;
    private int laidGs;
    private double total;
    private int live;
    private int cx;
    private int cy;
    private int radius;
    private int hubR;
    private int pointerH;
    private int fullCount;
    private String hubShown = "";
    private String fitEmpty = "";
    private String titleShown = "";

    // Rectangles: one frame's rows, kept while nothing moves.
    private int[] rx0 = new int[256];
    private int[] ry0 = new int[256];
    private int[] rx1 = new int[256];
    private int[] ry1 = new int[256];
    private int[] rc = new int[256];
    private int runs;
    private double builtRot = Double.NaN;
    private int builtCell;
    private boolean builtSeps;
    private int builtVersion = -1;
    private int version;
    private int cell = 1;
    private int cellKnown;
    private int cellsFor = -1;
    private int lastRects;
    private double[] gl = new double[64];
    private double[] gr = new double[64];

    // Colours: 0..n-1 segments, n the rim, n+1 the hub.
    private int[] colours = new int[2];
    private int lit = -1;
    private SeriesPalette seriesPalette = SeriesPalette.THEME;

    public WheelComponent(Box box) {
        super(box);
    }

    // ---- setup ----

    /** The colour scheme of the segments without a colour of their own; {@link SeriesPalette#THEME} by default. */
    @Incubating
    public WheelComponent seriesPalette(SeriesPalette seriesPalette) {
        this.seriesPalette = Objects.requireNonNull(seriesPalette, "seriesPalette");
        dirty = true;
        return this;
    }

    /** Adds a segment. A spin in progress stops where it is. */
    public WheelComponent add(Segment segment) {
        segments.add(Objects.requireNonNull(segment, "segment"));
        changed();
        return this;
    }

    public WheelComponent add(String label) {
        return add(new Segment(label));
    }

    /** Puts these in place of every segment. */
    public WheelComponent segments(List<Segment> list) {
        list.forEach(segment -> Objects.requireNonNull(segment, "segment"));
        segments.clear();
        segments.addAll(list);
        changed();
        return this;
    }

    public WheelComponent clear() {
        segments.clear();
        changed();
        return this;
    }

    public List<Segment> segments() {
        return view;
    }

    /** Call after changing a segment already on the wheel. A spin in progress stops where it is. */
    public WheelComponent changed() {
        dirty = true;
        version++;
        if (spin != null && !landed) {
            spin = null;
            landed = false;
        }
        if (result >= segments.size()) {
            result = -1;
        }
        return this;
    }

    /** The hub's text, "Spin" by default; empty for none. */
    public WheelComponent hubText(String hubText) {
        this.hubText = hubText == null ? "" : hubText;
        dirty = true;
        return this;
    }

    /** False for a plain hub that does not spin on a click. A button by default. */
    public WheelComponent hubButton(boolean hubButton) {
        this.hubButton = hubButton;
        return this;
    }

    @Override
    void words(Words words) {
        this.words = words;
        dirty = true;
    }

    public WheelComponent emptyText(String emptyText) {
        this.emptyText = emptyText == null ? "" : emptyText;
        dirty = true;
        return this;
    }

    /** A line above the wheel in full screen. */
    public WheelComponent title(String title) {
        this.title = title == null ? "" : title;
        dirty = true;
        return this;
    }

    /** How long a spin takes, in ms. {@value #DEFAULT_DURATION} by default. */
    public WheelComponent duration(long duration) {
        this.duration = Math.max(1L, duration);
        return this;
    }

    /** A wait before the wheel starts turning, as for a sound or a message first. */
    public WheelComponent delay(long delay) {
        this.delay = Math.max(0L, delay);
        return this;
    }

    /** Where spins without a seed take their randomness. Each spin asks for a fresh one; the default is {@code new Random()}. */
    public WheelComponent randomSource(Supplier<Random> source) {
        this.randomSource = Objects.requireNonNull(source);
        return this;
    }

    /** No ticks and no landing sound. */
    public WheelComponent muted(boolean muted) {
        this.muted = muted;
        return this;
    }

    /** Called once when a spin lands, not for a restored spin that had already landed. */
    public WheelComponent onLand(Consumer<Segment> onLand) {
        this.onLand = onLand;
        return this;
    }

    /** Called by a click on the hub, Space or Enter, in place of a local random spin: ask the server here. */
    public WheelComponent onSpinRequest(Runnable onSpinRequest) {
        this.onSpinRequest = onSpinRequest;
        return this;
    }

    public WheelComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public WheelComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- full screen ----

    /** True to treat the box as the area to dim, and to stay hidden until {@link #open(PanelComponent)}. */
    public WheelComponent fullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        visible = !fullscreen || open;
        dirty = true;
        return this;
    }

    /** In full screen, closes this long after landing. -1, the default, keeps it open. */
    public WheelComponent closeOnLand(long ms) {
        this.closeOnLand = ms;
        return this;
    }

    public WheelComponent onClose(Runnable onClose) {
        this.onClose = onClose;
        return this;
    }

    /** Shows the full screen wheel over the panel and takes its focus. */
    public void open(PanelComponent panel) {
        if (!open) {
            BaseComponent current = panel.focus();
            focusBefore = current == this ? null : current;
        }
        panel.closeOverlay();
        host = panel;
        open = true;
        visible = true;
        panel.focus(this);
    }

    /** Hides the full screen wheel. A spin in progress keeps going and still calls {@link #onLand(Consumer)}. */
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        visible = !fullscreen;
        if (host != null) {
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

    // ---- spinning ----

    /** Spins to a segment. The seed sets the turns and the stop inside the segment. Ignored while turning. */
    public WheelComponent spin(int landing, long seed) {
        refresh();
        if (spinning() || landing < 0 || landing >= segments.size() || segments.get(landing).weight <= 0D) {
            return this;
        }
        start(new Spin(landing, seed, clock().getAsLong() + delay, rotation, duration, false));
        return this;
    }

    /** Spins to a segment with a seed of its own: only for one player. */
    public WheelComponent spin(int landing) {
        return spin(landing, randomSource.get().nextLong());
    }

    /** Picks a segment by weight, then spins to it. */
    public WheelComponent spin(Random random) {
        int landing = pick(random);
        return landing < 0 ? this : spin(landing, random.nextLong());
    }

    /** A segment by weight, or -1 with none. */
    public int pick(Random random) {
        refresh();
        if (total <= 0D) {
            return -1;
        }
        double at = random.nextDouble() * total;
        for (int i = 0; i < segments.size(); i++) {
            at -= segments.get(i).weight;
            if (at < 0D && segments.get(i).weight > 0D) {
                return i;
            }
        }
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (segments.get(i).weight > 0D) {
                return i;
            }
        }
        return -1;
    }

    /** The spin to send to a screen opened late, or null before any. */
    public Spin state() {
        return spin == null ? null : new Spin(spin.landing(), spin.seed(), spin.start(), spin.from(), spin.duration(), landed);
    }

    /** Picks up a spin: one in progress goes on and lands, one that had landed shows its result quietly. */
    public WheelComponent restore(Spin state) {
        refresh();
        if (state == null || state.landing() < 0 || state.landing() >= segments.size()) {
            return this;
        }
        start(state);
        if (state.landed()) {
            rotation = end;
            landed = true;
            fired = true;
            result = state.landing();
            landedAt = clock().getAsLong();
            quietLand = true;
        }
        return this;
    }

    private void start(Spin s) {
        spin = s;
        end = endFor(s.landing(), s.seed(), s.from());
        rotation = s.from();
        landed = false;
        fired = false;
        quietLand = false;
        result = -1;
        tickAt = pointed();
    }

    private double endFor(int landing, long seed, double from) {
        Random r = new Random(seed);
        int turns = MIN_TURNS + r.nextInt(EXTRA_TURNS);
        double f = 0.18D + 0.64D * r.nextDouble();
        Segment s = segments.get(landing);
        double at = s.from + f * (s.to - s.from);
        double base = -at - from;
        base -= Math.floor(base);
        return from + turns + base;
    }

    private static double ease(double u) {
        double v = 1D - u;
        return 1D - v * v * v * v;
    }

    /** Moves the spin on to now: lands, ticks, closes. Called from both a frame and a tick. */
    private void update() {
        long now = Redraw.quietClock();
        if (spin != null && !landed) {
            if (reducedMotion() || now >= spin.start() + spin.duration()) {
                rotation = end;
                landed = true;
                landedAt = now;
                result = spin.landing();
            } else if (now > spin.start()) {
                double u = (now - spin.start()) / (double) spin.duration();
                double distance = end - spin.from();
                rotation = spin.from() + distance * ease(u);
                double v = 1D - u;
                int at = pointed();
                if (at != tickAt) {
                    tickAt = at;
                    if (!muted && sound() != null && now - tickTime >= TICK_GAP_MS) {
                        tickTime = now;
                        sound().tick((float) Math.min(1D, v * 1.5D));
                    }
                }
            }
        }
        if (landed && !fired) {
            fired = true;
            if (!muted && sound() != null) {
                sound().land();
            }
            if (onLand != null && result >= 0 && result < segments.size()) {
                onLand.accept(segments.get(result));
            }
        }
        if (fullscreen && open && landed && closeOnLand >= 0L && spin != null && now >= landedAt + closeOnLand
                && landedAt >= spin.start()) {
            close();
        }
        if (spin != null && !landed || fullscreen && open && landed && closeOnLand >= 0L) {
            Redraw.ask();
        }
    }

    /** True from a spin's start (its delay included) until it lands. */
    public boolean spinning() {
        return spin != null && !landed;
    }

    public boolean landed() {
        return landed;
    }

    /** The segment landed on, or -1. */
    public int result() {
        return result;
    }

    /** How far the wheel has turned, in turns, clockwise. */
    double rotation() {
        return rotation;
    }

    /** The segment under the pointer, or -1. */
    int pointed() {
        refresh();
        double w = -rotation;
        return segmentAtTurn(w - Math.floor(w));
    }

    // ---- what the last frame did ----

    /** Rectangles sent by the last frame, text, items and pictures left out. */
    int rectCount() {
        return lastRects;
    }

    /** The cell, in GUI units, of the last frame's picture. */
    int cellSize() {
        return cell;
    }

    /** The square the wheel is drawn in. */
    Box wheelBox() {
        refresh();
        return new Box("wheel", cx - radius, cy - radius, 2 * radius + 1, 2 * radius + 1);
    }

    Box hubBox() {
        refresh();
        return new Box("hub", cx - hubR, cy - hubR, 2 * hubR + 1, 2 * hubR + 1);
    }

    /**
     * What a segment's label shows: the whole label, a shortened one, or empty when it does not fit. The label of a
     * segment with an icon shows only at rest, placed for the angle it rests at.
     */
    String labelShown(int index) {
        refresh();
        Segment s = segments.get(index);
        if (spinning()) {
            return s.iconSide > 0 ? "" : s.shown;
        }
        restLabels();
        return s.restShown.isEmpty() && s.iconSide <= 0 ? s.shown : s.restShown;
    }

    /** The scale of a segment's icon in the last frame, 0 for none. */
    float iconScale(int index) {
        return segments.get(index).iconScale;
    }

    /** The side of a segment's icon, in GUI units, in the last frame, 0 for none. */
    int iconSide(int index) {
        return segments.get(index).iconSide;
    }

    /** The middle of a segment's content right now, for pointing at it. */
    int[] contentAt(int index) {
        refresh();
        Segment s = segments.get(index);
        double a = 2D * Math.PI * ((s.from + s.to) / 2D + rotation);
        return new int[] {(int) Math.floor(cx + 0.5D + Math.sin(a) * s.contentR), (int) Math.floor(cy + 0.5D - Math.cos(a) * s.contentR)};
    }

    // ---- layout ----

    private int rim() {
        return Math.max(1, Math.round(radius / 28F));
    }

    private int innerR() {
        return radius - Math.max(cell, rim());
    }

    private int hubOut() {
        return hubR + Math.max(1, Math.round(radius / 40F));
    }

    private void refresh() {
        int gs = Math.max(1, laidGs);
        if (!dirty && laidX == x && laidY == y && laidW == w && laidH == h) {
            return;
        }
        dirty = false;
        laidX = x;
        laidY = y;
        laidW = w;
        laidH = h;
        total = 0D;
        live = 0;
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            s.index = i;
            total += s.weight;
            if (s.weight > 0D) {
                live++;
            }
        }
        double at = 0D;
        for (Segment s : segments) {
            s.from = at;
            at += total <= 0D ? 0D : s.weight / total;
            s.to = at;
        }
        for (int i = segments.size() - 1; i >= 0; i--) {
            if (segments.get(i).weight > 0D) {
                segments.get(i).to = 1D;
                break;
            }
        }
        Draw measure = TextMeasure.draw();
        int top = y;
        int room = h;
        titleShown = "";
        if (fullscreen) {
            if (!title.isEmpty()) {
                titleShown = measure.ellipsize(title, Math.max(0, w / 2 - 8));
                top += 26;
                room -= 26;
            }
            room -= 20;
            radius = Math.max(0, Math.min(w - 16, room - 8) / 2);
            pointerH = pointer(radius);
            int above = pointerH * 2 / 5;
            radius = Math.max(0, Math.min(w - 16, room - 8 - above) / 2);
            pointerH = pointer(radius);
            above = pointerH * 2 / 5;
            cx = x + w / 2;
            cy = top + (room - (2 * radius + above)) / 2 + above + radius;
        } else {
            int side = Math.min(w, h);
            radius = Math.max(0, side / 2 - 1);
            pointerH = pointer(radius);
            int above = pointerH * 2 / 5;
            radius = Math.max(0, Math.min((w - 2) / 2, (h - above - 1) / 2));
            pointerH = pointer(radius);
            above = pointerH * 2 / 5;
            cx = x + w / 2;
            cy = y + (h - (2 * radius + above)) / 2 + above + radius;
        }
        // Many segments: the middle would be slivers thinner than a cell, so the hub grows over them.
        int many = (int) Math.round(live * 2.2D / (2D * Math.PI));
        hubR = radius < 10 ? 0 : Math.max(5, Math.max(Math.round(radius * 0.22F), Math.min(Math.round(radius * 0.4F), many)));
        String wanted = text(hubText, words, "minuit.wheel.spin", "Spin");
        hubShown = "";
        if (hubR > 0 && !wanted.isEmpty()) {
            int fit = (int) Math.floor(2D * Math.sqrt(Math.max(0, hubR * hubR - 25))) - 2;
            String cut = measure.ellipsize(wanted, fit);
            hubShown = cut.equals(wanted) || cut.length() >= 4 ? cut : "";
        }
        fitEmpty = measure.ellipsize(text(emptyText, words, "minuit.wheel.empty", "No segments"), Math.max(0, 2 * radius - 6));
        version++;
        cell = 1;
        fullCount = estimate();
        contentLayout(gs);
    }

    /** The pointer's height: rows of a {@link Shapes#arrow}, its base {@code 2h - 1} wide. */
    private static int pointer(int r) {
        return r < 6 ? 0 : Math.max(3, Math.min(11, Math.round(r / 8F) + 1));
    }

    /** About how many rectangles a picture of one-unit cells takes. */
    private int estimate() {
        int ri = radius - rim();
        int crossings = live > 1 ? live * Math.max(0, ri - hubOut()) : 0;
        return 2 * (2 * radius) + crossings + 2 * pointerH + 4;
    }

    /** Where each segment's content goes and how big: the largest that stays inside it at every angle. */
    private void contentLayout(int gs) {
        Draw measure = TextMeasure.draw();
        fitMemo.clear();
        fitRi = radius - rim() - 0.5D;
        fitHo = hubR > 0 ? hubOut() + 0.5D : 0D;
        fitMargin = live > 1 ? Math.max(1D, radius / 100D) + 0.5D : 0D;
        for (Segment s : segments) {
            s.laidImageW = s.image == null ? -1 : s.image.width();
            s.contentR = 0D;
            set(s, 0F, 0, "");
            if (s.weight <= 0D || fitRi <= fitHo) {
                continue;
            }
            fitArc = Math.PI * (s.to - s.from);
            fitContent(s, measure, gs);
        }
    }

    private final Map<String, Double> fitMemo = new HashMap<>();
    private double fitRi;
    private double fitHo;
    private double fitMargin;
    /** Half the current segment's angle, in radians. */
    private double fitArc;

    private int nativeSide(Segment s) {
        if (s.image != null) {
            return s.image.state() == AsyncImage.State.READY ? Math.max(1, Math.max(s.image.width(), s.image.height())) : 16;
        }
        if (s.icon != null) {
            return Math.max(1, Math.max(s.icon.width(), s.icon.height()));
        }
        return 16;
    }

    /** The distance from the middle at which a block fits the current segment at every angle, or -1. */
    private double place(int bw, int bh) {
        String key = fitArc + "|" + bw + "|" + bh;
        Double memo = fitMemo.get(key);
        if (memo != null) {
            return memo;
        }
        double found = -1D;
        if (live == 1 && fitHo <= 0D) {
            found = blockFits(bw, bh, 0D) ? 0D : -1D;
        } else {
            int first = -1;
            int last = -1;
            for (int k = 0; k <= 24; k++) {
                double r = fitHo + (fitRi - fitHo) * k / 24D;
                if (blockFits(bw, bh, r)) {
                    if (first < 0) {
                        first = k;
                    }
                    last = k;
                } else if (first >= 0) {
                    break;
                }
            }
            if (first >= 0) {
                found = fitHo + (fitRi - fitHo) * (first + last) / 48D;
            }
        }
        fitMemo.put(key, found);
        return found;
    }

    /** True when a block, its middle at distance r, stays inside the segment wherever the wheel has turned it. */
    private boolean blockFits(double bw, double bh, double r) {
        double hw = bw / 2D;
        double hh = bh / 2D;
        for (int k = 0; k < 16; k++) {
            double phi = 2D * Math.PI * (k + 0.5D) / 16D;
            double px = Math.sin(phi) * r;
            double py = -Math.cos(phi) * r;
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    if (i == 0 && j == 0) {
                        continue;
                    }
                    double qx = px + i * hw;
                    double qy = py + j * hh;
                    double d2 = qx * qx + qy * qy;
                    if (d2 > fitRi * fitRi || d2 < fitHo * fitHo) {
                        return false;
                    }
                    if (live > 1 && fitArc < Math.PI) {
                        double delta = gap(Math.atan2(qx, -qy) - phi);
                        double left = fitArc - delta;
                        if (left < 0D || (left < Math.PI / 2D && Math.sqrt(d2) * Math.sin(left) < fitMargin)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * The angle between two directions, 0 to pi. Not Math.IEEEremainder: TeaVM compiles it as a truncating
     * remainder, which leaves a difference near 2 pi whole and made every block fail on the site.
     */
    private static double gap(double a) {
        double full = 2D * Math.PI;
        return Math.abs(a - full * Math.floor(a / full + 0.5D));
    }

    /** The widest label under something this tall that still fits, in whole pixels; -1 for none. */
    private int widestLabel(int full, int side) {
        int lo = 0;
        int hi = full;
        int best = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (place(Math.max(side, mid), side + (side > 0 ? 1 : 0) + TEXT_H) >= 0D) {
                best = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return best;
    }

    /** Picks the icon's scale and the label's text for a segment: the icon first, as big as stays crisp. */
    private void fitContent(Segment s, Draw measure, int gs) {
        int full = TextMeasure.width(s.label);
        if (!s.hasIcon()) {
            fitLabelOnly(s, measure, full);
            return;
        }
        int nat = nativeSide(s);
        boolean crisp = s.content == null;
        float step = crisp ? 1F / gs : 1F / nat;
        float alone = 0F;
        double aloneR = -1D;
        for (float sc = top(nat, gs, crisp); sc * nat >= 8F - 1e-4F; sc -= step) {
            int side = Math.round(nat * sc);
            double r = place(side, side);
            if (r >= 0D) {
                alone = sc;
                aloneR = r;
                break;
            }
        }
        if (alone <= 0F) {
            fitLabelOnly(s, measure, full);
            return;
        }
        // A smaller icon, never under its native size, when that makes room for the label under or over it.
        if (full > 0) {
            for (float sc = alone; sc >= 1F - 1e-4F; sc -= step) {
                int side = Math.round(nat * sc);
                int room = widestLabel(full, side);
                if (room > 0 && !shorten(measure, s.label, room).isEmpty()) {
                    alone = sc;
                    aloneR = place(side, side);
                    break;
                }
            }
        }
        // The label of an icon is placed at rest, at the angle the wheel stopped at: see restLabels.
        s.contentR = aloneR;
        set(s, alone, Math.round(nat * alone), "");
    }

    private void fitLabelOnly(Segment s, Draw measure, int full) {
        if (s.label.isEmpty()) {
            return;
        }
        double r = place(full, TEXT_H);
        if (r >= 0D) {
            s.contentR = r;
            set(s, 0F, 0, s.label);
            return;
        }
        int room = widestLabel(full, 0);
        String cut = room < 0 ? "" : shorten(measure, s.label, room);
        if (!cut.isEmpty()) {
            s.contentR = place(TextMeasure.width(cut), TEXT_H);
            set(s, 0F, 0, cut);
        }
    }

    /** The largest scale tried: at most 4, a whole number of screen pixels per texel when crisp. */
    private static float top(int nat, int gs, boolean crisp) {
        if (crisp) {
            return (float) Math.floor(MAX_ICON_SCALE * gs) / gs;
        }
        return Math.max(1F, (float) Math.floor(MAX_ICON_SCALE * nat) / nat);
    }

    private static void set(Segment s, float scale, int side, String label) {
        s.iconScale = scale;
        s.iconSide = side;
        s.shown = label;
        s.shownW = TextMeasure.width(label);
    }

    /** The label, shortened to the width, or empty when fewer than three letters would stay. */
    private static String shorten(Draw measure, String label, int width) {
        if (label.isEmpty() || width <= 0) {
            return "";
        }
        if (TextMeasure.width(label) <= width) {
            return label;
        }
        String cut = measure.ellipsize(label, width);
        int letters = cut.endsWith(Draw.ELLIPSIS) ? cut.length() - Draw.ELLIPSIS.length() : cut.length();
        return letters >= Math.min(3, label.length()) && TextMeasure.width(cut) <= width ? cut : "";
    }

    // ---- the picture ----

    private int segmentAtTurn(double turn) {
        int n = segments.size();
        if (total <= 0D || n == 0) {
            return -1;
        }
        int lo = 0;
        int hi = n - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (segments.get(mid).to > turn) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return segments.get(lo).weight > 0D ? lo : -1;
    }

    private void addRect(int x0, int y0, int x1, int y1, int colour) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        if (runs > 0 && rc[runs - 1] == colour && ry0[runs - 1] == y0 && ry1[runs - 1] == y1 && rx1[runs - 1] == x0) {
            rx1[runs - 1] = x1;
            return;
        }
        if (runs == rx0.length) {
            int more = runs * 2;
            rx0 = java.util.Arrays.copyOf(rx0, more);
            ry0 = java.util.Arrays.copyOf(ry0, more);
            rx1 = java.util.Arrays.copyOf(rx1, more);
            ry1 = java.util.Arrays.copyOf(ry1, more);
            rc = java.util.Arrays.copyOf(rc, more);
        }
        rx0[runs] = x0;
        ry0[runs] = y0;
        rx1[runs] = x1;
        ry1[runs] = y1;
        rc[runs] = colour;
        runs++;
    }

    /**
     * Rows of the wheel at this cell: the rim row behind, the segments in front with gaps for the lines. The disc, the
     * rim and the hub's ring are {@link PixelArt} shapes of an odd side in cells of {@code c} (odd too), centred on the
     * middle of pixel {@code (cx, cy)}, so the thin rings keep their width and the pointer's tip pixel sits on the axis.
     */
    private void build(int c, boolean seps) {
        runs = 0;
        int r = radius;
        double rc = (r + 0.5D) / c;
        int rimCells = Math.max(1, (int) Math.round(Math.max(c, rim()) / (double) c));
        int ho = hubR > 0 ? hubOut() : 0;
        double hoc = (ho + 0.5D) / c;
        int hubRing = Math.max(1, (int) Math.round((ho - hubR) / (double) c));
        double sepW = seps ? Math.max(1D, r / 50D) : 0D;
        int half = (c - 1) / 2;
        int jMax = (int) Math.ceil(rc);
        int n = segments.size();
        for (int j = -jMax; j <= jMax; j++) {
            int y0 = Math.max(cy - half + j * c, cy - r);
            int y1 = Math.min(cy - half + (j + 1) * c, cy + r + 1);
            if (y1 <= y0) {
                continue;
            }
            int k = Math.abs(j);
            int cols = PixelArt.span(rc, k, true);
            if (cols <= 0) {
                continue;
            }
            double dyc = (y0 + y1) / 2D - cy - 0.5D;
            addRect(colAt(-reach(cols, c)), y0, colAt(reach(cols, c)), y1, n);
            int iCols = PixelArt.span(rc, k, rimCells, true);
            if (iCols <= 0) {
                continue;
            }
            double hi = reach(iCols, c);
            double oc = ho > 0 ? reach(PixelArt.span(hoc, k, true), c) : 0D;
            if (oc > 0D) {
                if (oc < hi) {
                    band(-hi, -oc, y0, y1, dyc, c, sepW);
                }
                double hc = reach(PixelArt.span(hoc, k, hubRing, true), c);
                if (hc > 0D) {
                    addRect(colAt(-hc), y0, colAt(hc), y1, n + 1);
                }
                if (oc < hi) {
                    band(oc, hi, y0, y1, dyc, c, sepW);
                }
            } else {
                band(-hi, hi, y0, y1, dyc, c, sepW);
            }
        }
    }

    /** How far the outer edge of the {@code cols}-th cell from the middle is from the centre. */
    private static double reach(int cols, int c) {
        return cols <= 0 ? 0D : (cols - 0.5D) * c;
    }

    /** The pixel column at this distance from the centre, the middle of pixel {@code cx}. */
    private int colAt(double fromCentre) {
        return (int) Math.round(cx + 0.5D + fromCentre);
    }

    /** The nearest cell edge to this distance from the centre: edges lie half a cell off the middle. */
    private static double edgeNear(double v, int c) {
        return (Math.round(v / c - 0.5D) + 0.5D) * c;
    }

    /** One stretch of a row, from {@code left} to {@code right} around the middle: runs of segments between lines. */
    private void band(double left, double right, int y0, int y1, double dyc, int c, double sepW) {
        int count = 0;
        int n = segments.size();
        if (live > 1) {
            if (gl.length < n) {
                gl = new double[n * 2];
                gr = new double[n * 2];
            }
            double a0 = y0 - cy - 0.5D;
            double b0 = y1 - cy - 0.5D;
            for (int k = 0; k < n; k++) {
                Segment s = segments.get(k);
                if (s.weight <= 0D) {
                    continue;
                }
                double ang = 2D * Math.PI * (s.from + drawn);
                double sx = Math.sin(ang);
                double sy = -Math.cos(ang);
                if (Math.abs(sy) < 1e-7D) {
                    continue;
                }
                double lo;
                double hi;
                if (sepW > 0D) {
                    double a = a0;
                    double b = b0;
                    if (sy > 0D) {
                        a = Math.max(a, 0D);
                    } else {
                        b = Math.min(b, 0D);
                    }
                    if (a >= b) {
                        continue;
                    }
                    double xa = a * sx / sy;
                    double xb = b * sx / sy;
                    double pad = sepW / (2D * Math.abs(sy));
                    lo = Math.min(xa, xb) - pad;
                    hi = Math.max(xa, xb) + pad;
                } else {
                    if (dyc * sy <= 0D) {
                        continue;
                    }
                    lo = dyc * sx / sy;
                    hi = lo;
                }
                if (hi < left || lo > right) {
                    continue;
                }
                double l = edgeNear(lo, c);
                double h2 = edgeNear(hi, c);
                if (sepW > 0D && h2 - l < c) {
                    l = edgeNear((lo + hi) / 2D - c / 2D, c);
                    h2 = l + c;
                }
                // Insertion by the left edge: a row crosses few lines.
                int at = count++;
                while (at > 0 && gl[at - 1] > l) {
                    gl[at] = gl[at - 1];
                    gr[at] = gr[at - 1];
                    at--;
                }
                gl[at] = l;
                gr[at] = h2;
            }
        }
        double pos = left;
        for (int i = 0; i < count; i++) {
            double l = Math.max(left, gl[i]);
            if (l > pos) {
                run(pos, Math.min(l, right), y0, y1, dyc);
            }
            pos = Math.max(pos, gr[i]);
            if (pos >= right) {
                return;
            }
        }
        if (pos < right) {
            run(pos, right, y0, y1, dyc);
        }
    }

    private void run(double from, double to, int y0, int y1, double dyc) {
        if (to <= from) {
            return;
        }
        double turn = DataLook.turnOf((from + to) / 2D, dyc) - drawn;
        int at = segmentAtTurn(turn - Math.floor(turn));
        if (at < 0) {
            at = segments.size();
        }
        addRect(colAt(from), y0, colAt(to), y1, at);
    }

    /** Builds the rows again only when the angle, the cell or the lines changed. */
    private void picture() {
        if (cellsFor != version) {
            cellsFor = version;
            cellKnown = 0;
        }
        int known = cellKnown;
        // Odd cells only: the middle cell is centred on the wheel's middle pixel.
        int c = (known > 0 ? known : Math.max(1, Math.min(MAX_CELL, fullCount / MAX_RECTS / 2))) | 1;
        boolean seps = live > 1 && minArc() >= 4D * c;
        drawn = drawnAngle();
        if (builtVersion == version && builtRot == drawn && builtCell == c && builtSeps == seps) {
            return;
        }
        while (true) {
            cell = c;
            seps = live > 1 && minArc() >= 4D * c;
            build(c, seps);
            if (runs + 2 * pointerH <= MAX_RECTS || c >= MAX_CELL) {
                break;
            }
            c += 2;
        }
        // One cell for every angle and speed, so the wheel looks the same turning and at rest; it only grows.
        cellKnown = c;
        builtVersion = version;
        builtRot = drawn;
        builtCell = c;
        builtSeps = seps;
    }

    /**
     * While it turns, the wheel is drawn at whole steps of about one pixel at the rim, a quarter turn being a whole
     * number of steps. Every frame between two steps then draws the very same pixels: drawn at the exact angle, the
     * lines and edges would round another way each frame and flicker even when the wheel barely moves. At rest the
     * angle is exact, so the landing shows where it really stopped.
     */
    private double drawnAngle() {
        if (!spinning()) {
            return rotation;
        }
        int steps = Math.max(4, 4 * (int) Math.round(2D * Math.PI * radius / 4D));
        return Math.round(rotation * steps) / (double) steps;
    }

    /** The thinnest segment's width at the rim, in GUI units. */
    private double minArc() {
        double least = Double.MAX_VALUE;
        for (Segment s : segments) {
            if (s.weight > 0D) {
                least = Math.min(least, (s.to - s.from) * 2D * Math.PI * radius);
            }
        }
        return least;
    }


    private int rimColour(Palette p) {
        int text = DataLook.opaque(p.text());
        int back = DataLook.back(p);
        if (Shapes.luminance(text) < Shapes.luminance(back)) {
            return Shapes.mix(text, back, 0.15F);
        }
        return Shapes.mix(back, 0xFF000000, 0.55F);
    }

    private int baseColour(Theme t, int i) {
        Segment s = segments.get(i);
        if (s.custom) {
            return s.color;
        }
        int order = i;
        // The last next to the first: no two neighbours of the same colour.
        if (live > DataLook.COLOURS && i == segments.size() - 1 && i % DataLook.COLOURS == 0) {
            order = DataLook.COLOURS / 2;
        }
        return DataLook.opaque(SeriesColors.fill(t.palette(), seriesPalette, order));
    }

    private boolean won(int i) {
        return i == result && landed && !spinning();
    }

    private boolean wonRimShown() {
        if (quietLand || reducedMotion()) {
            return true;
        }
        long step = (clock().getAsLong() - landedAt) / WON_BLINK;
        return step < 0 || step >= WON_BLINKS || step % 2 == 0;
    }

    /**
     * An inner rim around the winner: the winner's pixels whose neighbour one cell away is not the
     * winner, so it follows the cell grid of the rows.
     */
    private int drawWonRim(Draw d, int ink) {
        int seg = result;
        int left = cx - radius - 1;
        int top = cy - radius - 1;
        int side = 2 * radius + 3;
        if (wonMask == null || wonMask.length < side * side) {
            wonMask = new boolean[side * side];
        } else {
            java.util.Arrays.fill(wonMask, 0, side * side, false);
        }
        for (int i = 0; i < runs; i++) {
            if (rc[i] != seg) {
                continue;
            }
            for (int y = Math.max(ry0[i], top); y < Math.min(ry1[i], top + side); y++) {
                for (int x = Math.max(rx0[i], left); x < Math.min(rx1[i], left + side); x++) {
                    wonMask[(y - top) * side + x - left] = true;
                }
            }
        }
        int c = Math.max(1, cell);
        int count = 0;
        for (int y = 0; y < side; y++) {
            int start = -1;
            for (int x = 0; x <= side; x++) {
                boolean edge = x < side && wonMask[y * side + x] && !inside(wonMask, side, x, y, c);
                if (edge && start < 0) {
                    start = x;
                } else if (!edge && start >= 0) {
                    d.fill(left + start, top + y, left + x, top + y + 1, ink);
                    count++;
                    start = -1;
                }
            }
        }
        return count;
    }

    /** Two pixels deep at one pixel a cell, so the rim stands apart from the one-pixel lines; one cell otherwise. */
    private static boolean inside(boolean[] mask, int side, int x, int y, int c) {
        int depth = c == 1 ? 2 : c;
        for (int k = c == 1 ? 1 : c; k <= depth; k++) {
            if (!(in(mask, side, x - k, y) && in(mask, side, x + k, y) && in(mask, side, x, y - k) && in(mask, side, x, y + k))) {
                return false;
            }
        }
        return true;
    }

    private static boolean in(boolean[] mask, int side, int x, int y) {
        return x >= 0 && y >= 0 && x < side && y < side && mask[y * side + x];
    }

    private void paintColours(Theme t, boolean hubLit) {
        Palette p = t.palette();
        int n = segments.size();
        if (colours.length < n + 2) {
            colours = new int[n + 2];
        }
        for (int i = 0; i < n; i++) {
            int c = baseColour(t, i);
            if (!active) {
                c = DataLook.faded(p, c);
            } else if (i == lit || won(i)) {
                c = Shapes.mix(c, 0xFFFFFFFF, LIT_MIX);
            }
            colours[i] = c;
            segments.get(i).ink = Ink.on(p, c);
        }
        colours[n] = rimColour(p);
        int hub = !active ? p.disabled() : hubLit ? p.raisedHover() : p.raised();
        colours[n + 1] = DataLook.opaque(hub);
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        int gs = Math.max(1, d.guiScale());
        if (gs != laidGs) {
            laidGs = gs;
            dirty = true;
        }
        refresh();
        update();
        if (fullscreen && !open) {
            lastRects = 0;
            return;
        }
        Palette p = t.palette();
        int sent = 0;
        if (fullscreen) {
            d.nextLayer();
            d.fill(x, y, x + w, y + h, (DIM_ALPHA << 24) | (p.shadow() & 0xFFFFFF));
            d.nextLayer();
            sent++;
        }
        for (Segment s : segments) {
            if (s.image != null && s.image.width() != s.laidImageW) {
                dirty = true;
            }
        }
        refresh();
        if (radius <= 0) {
            lastRects = sent;
            return;
        }
        boolean idle = !spinning();
        boolean pointer = active && hovered && idle && settled();
        boolean overHub = pointer && hubButton && overHub(mx, my);
        lit = pointer && !overHub ? segmentAt(mx, my) : -1;
        if (live == 0) {
            sent += drawEmpty(d, p);
            lastRects = sent;
            return;
        }
        paintColours(t, overHub);
        picture();
        for (int i = 0; i < runs; i++) {
            d.fill(rx0[i], ry0[i], rx1[i], ry1[i], colours[rc[i]]);
        }
        sent += runs;
        if (active && result >= 0 && won(result) && wonRimShown()) {
            sent += drawWonRim(d, DataLook.opaque(Ink.on(p, colours[result])));
        }
        sent += drawPointer(d, p);
        drawContent(d, t);
        if (!hubShown.isEmpty()) {
            int ink = !active ? p.disabledText() : DataLook.opaque(p.raisedText());
            d.text(hubShown, cx + 1 - d.width(hubShown) / 2, cy - 4, ink);
        }
        if (fullscreen) {
            drawScreenText(d, p);
        }
        lastRects = sent;
    }

    private int drawEmpty(Draw d, Palette p) {
        int count = 0;
        int r = radius;
        int ri = r - rim();
        int face = DataLook.wash(p);
        int rimC = Shapes.alpha(rimColour(p), 0x80);
        for (int row = -r; row <= r; row++) {
            int k = Math.abs(row);
            int half = PixelArt.span(r + 0.5D, k, true);
            if (half <= 0) {
                continue;
            }
            int halfIn = PixelArt.span(r + 0.5D, k, r - ri, true);
            int y0 = cy + row;
            if (halfIn <= 0) {
                d.fill(cx + 1 - half, y0, cx + half, y0 + 1, rimC);
                count++;
            } else {
                d.fill(cx + 1 - half, y0, cx + 1 - halfIn, y0 + 1, rimC);
                d.fill(cx + 1 - halfIn, y0, cx + halfIn, y0 + 1, face);
                d.fill(cx + halfIn, y0, cx + half, y0 + 1, rimC);
                count += 3;
            }
        }
        if (!fitEmpty.isEmpty()) {
            d.text(fitEmpty, cx + 1 - d.width(fitEmpty) / 2, cy - 4, active ? Ink.soft(p, DataLook.back(p)) : p.disabledText());
        }
        return count;
    }

    /** A triangle at the top, pointing down into the rim. */
    private int drawPointer(Draw d, Palette p) {
        int ph = pointerH;
        if (ph <= 0) {
            return 0;
        }
        int edge = colours[segments.size()];
        int face = active ? DataLook.opaque(p.primary()) : DataLook.opaque(p.disabled());
        if (Shapes.contrast(face, edge) < 1.6D) {
            face = DataLook.opaque(p.accent());
        }
        // The face, one row up and two shorter, leaves a one-pixel outline in the rim colour on every side.
        int tip = cy - radius - ph * 2 / 5 + ph - 1;
        Shapes.arrow(d, cx, tip, ph, Shapes.Point.DOWN, edge);
        Shapes.arrow(d, cx, tip - 1, ph - 2, Shapes.Point.DOWN, face);
        return 2 * ph - 2;
    }

    /**
     * Places the labels of icons for the angle the wheel rests at: below the icon, or above it, shortened to fit.
     * Worked out again only when the wheel stops somewhere else. While it turns they are not drawn.
     */
    private void restLabels() {
        if (restVersion == version && restRot == rotation) {
            return;
        }
        restVersion = version;
        restRot = rotation;
        Draw measure = TextMeasure.draw();
        int tried = 0;
        int failed = 0;
        double least = Double.MAX_VALUE;
        double most = 0D;
        for (Segment s : segments) {
            s.restShown = "";
            s.restW = 0;
            if (s.weight <= 0D || s.label.isEmpty()) {
                continue;
            }
            fitArc = Math.PI * (s.to - s.from);
            double phi = 2D * Math.PI * ((s.from + s.to) / 2D + rotation);
            int full = TextMeasure.width(s.label);
            int room;
            if (s.iconSide > 0) {
                double px = Math.round(Math.sin(phi) * s.contentR);
                double py = Math.round(-Math.cos(phi) * s.contentR);
                double belowTop = py - s.iconSide / 2 + s.iconSide + 1;
                double aboveTop = py - s.iconSide / 2 - 1 - TEXT_H;
                int below = widestAt(phi, px, belowTop, full);
                int above = widestAt(phi, px, aboveTop, full);
                room = Math.max(above, below);
                s.restX = (int) px;
                s.restY = (int) (above > below ? aboveTop : belowTop);
                least = Math.min(least, s.to - s.from);
                most = Math.max(most, s.to - s.from);
            } else {
                // Text alone: the widest along the middle line of the segment.
                room = 0;
                for (int k = 0; k <= 16; k++) {
                    double r = fitHo + (fitRi - fitHo) * k / 16D;
                    double px = Math.round(Math.sin(phi) * r);
                    double py = Math.round(-Math.cos(phi) * r);
                    int got = widestAt(phi, px, py - TEXT_H / 2, full);
                    if (got > room) {
                        room = got;
                        s.restX = (int) px;
                        s.restY = (int) py - TEXT_H / 2;
                    }
                }
            }
            String cut = room <= 0 ? "" : shorten(measure, s.label, room);
            if (s.iconSide <= 0 && TextMeasure.width(cut) < s.shownW) {
                cut = "";
            }
            s.restShown = cut;
            s.restW = TextMeasure.width(cut);
            if (s.iconSide > 0) {
                tried++;
                if (cut.isEmpty()) {
                    failed++;
                }
            }
        }
        // Segments of one size where a label does not fit: none, the icons stand for them and the tooltip names them,
        // so the wheel never looks labelled at random.
        if (tried > 1 && failed > 0 && most <= least * 1.5D) {
            for (Segment s : segments) {
                if (s.iconSide > 0) {
                    s.restShown = "";
                    s.restW = 0;
                }
            }
        }
    }

    private int restVersion = -1;
    private double restRot = Double.NaN;

    /** The widest label, its top at {@code top} and centred on {@code mid}, that stays in the segment at angle phi. */
    private int widestAt(double phi, double mid, double top, int full) {
        int lo = 1;
        int hi = full;
        int best = 0;
        while (lo <= hi) {
            int m = (lo + hi) >>> 1;
            if (rectIn(phi, mid - m / 2D, top, m, TEXT_H)) {
                best = m;
                lo = m + 1;
            } else {
                hi = m - 1;
            }
        }
        return best;
    }

    private boolean rectIn(double phi, double left, double top, double rw, double rh) {
        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) {
                double qx = left + rw * i / 2D;
                double qy = top + rh * j / 2D;
                double d2 = qx * qx + qy * qy;
                if (d2 > fitRi * fitRi || d2 < fitHo * fitHo) {
                    return false;
                }
                if (live > 1 && fitArc < Math.PI) {
                    double delta = gap(Math.atan2(qx, -qy) - phi);
                    double left2 = fitArc - delta;
                    if (left2 < 0D || (left2 < Math.PI / 2D && Math.sqrt(d2) * Math.sin(left2) < fitMargin)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void drawContent(Draw d, Theme t) {
        Palette p = t.palette();
        boolean still = !spinning();
        if (still) {
            restLabels();
        }
        for (Segment s : segments) {
            if (s.weight <= 0D) {
                continue;
            }
            int ink = active ? s.ink : p.disabledText();
            double a = 2D * Math.PI * ((s.from + s.to) / 2D + drawn);
            int px = (int) Math.floor(cx + 0.5D + Math.sin(a) * s.contentR);
            int py = (int) Math.floor(cy + 0.5D - Math.cos(a) * s.contentR);
            if (s.iconSide > 0) {
                drawIcon(d, t, s, px - s.iconSide / 2, py - s.iconSide / 2);
            }
            if (still && !s.restShown.isEmpty()) {
                d.text(s.restShown, cx + s.restX - s.restW / 2, cy + s.restY, ink);
            } else if (s.iconSide <= 0 && !s.shown.isEmpty()) {
                // Turning: the label that fits at every angle.
                d.text(s.shown, px - s.shownW / 2, py - TEXT_H / 2, ink);
            }
        }
    }

    private void drawIcon(Draw d, Theme t, Segment s, int left, int top) {
        if (s.content != null) {
            s.content.draw(d, t, s, left, top, s.iconSide, active);
            return;
        }
        float sc = s.iconScale;
        if (s.image != null) {
            AsyncImage img = s.image;
            if (img.state() == AsyncImage.State.READY) {
                int iw = img.width();
                int ih = img.height();
                int nat = Math.max(iw, ih);
                int ox = left + Math.round((nat - iw) * sc / 2F);
                int oy = top + Math.round((nat - ih) * sc / 2F);
                d.push(ox, oy, sc);
                AsyncImage.draw(d, t.palette(), img, 0, 0, iw, ih);
                d.pop();
            } else {
                AsyncImage.draw(d, t.palette(), img, left, top, s.iconSide, s.iconSide);
            }
            return;
        }
        IconArt icon = s.icon;
        if (sc == 1F) {
            icon.draw(d, left, top, icon.width(), active ? 1F : 0.5F);
            return;
        }
        d.push(left, top, sc);
        icon.draw(d, 0, 0, icon.width(), active ? 1F : 0.5F);
        d.pop();
    }

    private void drawScreenText(Draw d, Palette p) {
        int ink = DataLook.opaque(Ink.on(p, 0xFF000000 | (p.shadow() & 0xFFFFFF)));
        ink = Shapes.contrast(0xFFFFFFFF, 0xFF000000 | (p.shadow() & 0xFFFFFF)) > Shapes.contrast(ink, 0xFF000000 | (p.shadow() & 0xFFFFFF))
                ? 0xFFFFFFFF : ink;
        if (!titleShown.isEmpty()) {
            int tw = d.width(titleShown);
            d.push(cx - tw, y + 6, 2F);
            d.text(titleShown, 0, 0, ink);
            d.pop();
        }
        if (landed && result >= 0 && !spinning()) {
            String line = d.ellipsize(segments.get(result).label, Math.max(0, w - 16));
            d.text(line, cx - d.width(line) / 2, cy + radius + 8, ink);
        }
    }

    // ---- pointer and keys ----

    private boolean overHub(double mx, double my) {
        if (hubR <= 0) {
            return false;
        }
        double dx = mx - cx - 0.5D;
        double dy = my - cy - 0.5D;
        return dx * dx + dy * dy <= (hubR + 0.5D) * (hubR + 0.5D);
    }

    /** The segment at a point, or -1 off the wheel and on the hub. */
    int segmentAt(double mx, double my) {
        refresh();
        double dx = mx - cx - 0.5D;
        double dy = my - cy - 0.5D;
        double r2 = dx * dx + dy * dy;
        double ri = radius - rim() + 0.5D;
        double ho = hubR > 0 ? hubOut() + 0.5D : 0D;
        if (r2 > ri * ri || r2 < ho * ho) {
            return -1;
        }
        double turn = DataLook.turnOf(dx, dy) - rotation;
        return segmentAtTurn(turn - Math.floor(turn));
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!visible || !active || spinning() || overHub(mx, my)) {
            return super.tooltipAt(mx, my);
        }
        int at = segmentAt(mx, my);
        if (at >= 0) {
            Segment s = segments.get(at);
            String name = s.label;
            if (name.isEmpty() && s.icon instanceof ItemView item) {
                name = item.id();
            }
            long pct = Math.round(s.weight / total * 1000D);
            String odds = (pct % 10 == 0 ? String.valueOf(pct / 10) : (pct / 10) + "." + (pct % 10)) + "%";
            return name.isEmpty() ? List.of(odds) : List.of(name, odds);
        }
        return super.tooltipAt(mx, my);
    }

    /** Asks for a spin as a click on the hub does. False when it cannot spin now. */
    public boolean requestSpin() {
        refresh();
        if (!visible || !active || spinning() || live == 0) {
            return false;
        }
        BaseComponent.click();
        if (onSpinRequest != null) {
            onSpinRequest.run();
        } else {
            spin(randomSource.get());
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        update();
    }

    @Override
    protected boolean takesFocus() {
        return visible && active && (!fullscreen || open);
    }

    @Override
    public List<BaseComponent> stops() {
        if (fullscreen) {
            return open ? List.of(this) : List.of();
        }
        return super.stops();
    }

    @Override
    protected Box focusBox() {
        if (fullscreen) {
            return wheelBox();
        }
        return super.focusBox();
    }

    @Override
    protected boolean activate() {
        return requestSpin();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) {
            return false;
        }
        if (fullscreen && open) {
            if (button == 0 && active && hubButton && overHub(mx, my) && settled()) {
                requestSpin();
            }
            return true;
        }
        if (!isOver(mx, my) || button != 0) {
            return false;
        }
        refresh();
        if (active && hubButton && overHub(mx, my) && settled()) {
            requestSpin();
            return true;
        }
        return segmentAt(mx, my) >= 0;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return fullscreen && open;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!visible) {
            return false;
        }
        if (key == InputKey.ESCAPE && fullscreen && open) {
            close();
            return true;
        }
        if ((key == InputKey.SPACE || key == InputKey.ENTER) && active) {
            requestSpin();
            return true;
        }
        return fullscreen && open && key != InputKey.TAB;
    }
}
