package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A timing minigame bar, first drawn for Overgeared's forging bar: a framed panel with a fill gauge on top
 * and a target strip below, split into zones, with a cursor that sweeps the strip between two arrow marks.
 * The player strikes with a click, Enter or Space; {@link #hit()} says which zone the cursor was in.
 *
 * <p>The cursor position is a function of {@link BaseComponent#clock}, read at each frame, so the bar moves
 * at the rate the screen or the HUD redraws and needs no tick. It moves under reduced motion too: it is the
 * game, not decoration. Drawn without a screen (a HUD callback calling {@link #draw}) it works the same;
 * the mod then calls {@link #hit()} from its own key handler.
 */
@Incubating
public class ZoneGaugeComponent extends BaseComponent {

    /** What a zone is worth; each role takes its colour from the palette. */
    public enum Role { MISS, GOOD, PERFECT }

    /** How the cursor travels. */
    public enum Sweep {
        /** Left to right, then right to left, one pass each way. */
        PING_PONG,
        /** Left to right, then back to the left end at once. */
        ONE_WAY
    }

    /** A span of the strip from {@code start} to {@code end} (0 to 1, end excluded); {@code color} 0 takes the role's. */
    public record Zone(float start, float end, Role role, int color) {
        public Zone(float start, float end, Role role) {
            this(start, end, role, 0);
        }

        public boolean contains(float position) {
            return position >= this.start && position < this.end;
        }
    }

    /** Smallest height that holds the gauge, the arrows and a 3 px strip. */
    public static final int MIN_HEIGHT = 24;
    private static final int PAD = 4;
    private static final int FILL_H = 5;

    private final List<Zone> zones = new ArrayList<>();
    private long passMs = 1500;
    private Sweep sweep = Sweep.PING_PONG;
    private long startedAt;
    private boolean running;
    private float heldAt;
    private Consumer<Zone> onHit;

    /** 0 to 1, the fill gauge on top; negative hides it. */
    public float fill;
    /** The fill gauge's colour; 0 takes the palette's warn. */
    public int fillColor;

    public ZoneGaugeComponent(Box box) {
        super(box);
        if (box.h() < MIN_HEIGHT) {
            throw new IllegalArgumentException(box + " is lower than " + MIN_HEIGHT);
        }
    }

    /** The zones, drawn in order; where two overlap the later one counts. Where none lies is a miss. */
    public ZoneGaugeComponent zones(List<Zone> spans) {
        this.zones.clear();
        this.zones.addAll(spans);
        return this;
    }

    /**
     * Zones centred on the strip: a perfect span {@code perfect} wide inside a good span {@code good} wide,
     * misses on both sides.
     */
    public static List<Zone> centred(float good, float perfect) {
        float g0 = 0.5F - good / 2F;
        float p0 = 0.5F - perfect / 2F;
        return List.of(new Zone(0F, g0, Role.MISS), new Zone(g0, p0, Role.GOOD), new Zone(p0, 1F - p0, Role.PERFECT),
                new Zone(1F - p0, 1F - g0, Role.GOOD), new Zone(1F - g0, 1F, Role.MISS));
    }

    public List<Zone> zones() {
        return List.copyOf(this.zones);
    }

    /** One pass across the strip, in milliseconds. */
    public ZoneGaugeComponent speed(long passMs, Sweep sweep) {
        this.passMs = Math.max(1, passMs);
        this.sweep = sweep;
        return this;
    }

    /** Called with the zone (null for a gap) when the player strikes by click or key. */
    public ZoneGaugeComponent onHit(Consumer<Zone> zoneHit) {
        this.onHit = zoneHit;
        return this;
    }

    /** Sets the cursor going from the left end, now. */
    public ZoneGaugeComponent start() {
        this.startedAt = clock().getAsLong();
        this.running = true;
        return this;
    }

    /** Holds the cursor where it is. */
    public void stop() {
        this.heldAt = position();
        this.running = false;
    }

    public boolean running() {
        return this.running;
    }

    /** The cursor on the strip, 0 at the left end, 1 at the right. */
    public float position() {
        return this.running ? position(clock().getAsLong() - this.startedAt, this.passMs, this.sweep) : this.heldAt;
    }

    /** Where a cursor started {@code elapsedMs} ago stands, for passes {@code passMs} long. */
    public static float position(long elapsedMs, long passMs, Sweep sweep) {
        if (sweep == Sweep.ONE_WAY) {
            return Math.floorMod(elapsedMs, passMs) / (float) passMs;
        }
        long t = Math.floorMod(elapsedMs, 2 * passMs);
        return t < passMs ? t / (float) passMs : 2F - t / (float) passMs;
    }

    /** The zone at {@code position}, the last one listed when they overlap; null in a gap. */
    public Zone zoneAt(float position) {
        Zone found = null;
        for (Zone zone : this.zones) {
            if (zone.contains(position) || position >= 1F && zone.end() >= 1F && zone.start() < 1F) {
                found = zone;
            }
        }
        return found;
    }

    /** The zone under the cursor now; null in a gap. It does not stop the cursor. */
    public Zone hit() {
        return zoneAt(position());
    }

    public int colorOf(Zone zone, Palette p) {
        if (zone.color() != 0) {
            return zone.color();
        }
        return switch (zone.role()) {
            case MISS -> p.well();
            case GOOD -> Shapes.mix(p.well(), p.good(), 0.5F);
            case PERFECT -> p.good();
        };
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        FrameStyle frame = theme.frame();
        frame.panel(d, p, this.x, this.y, this.w, this.h);
        int left = this.x + PAD;
        int inner = this.w - 2 * PAD;
        int top = this.y + PAD;
        if (this.fill >= 0) {
            frame.bar(d, p, left, top, inner, FILL_H, this.fill, this.fillColor != 0 ? this.fillColor : p.warn());
            top += FILL_H + 1;
        }
        int stripTop = top + 3;
        int stripBottom = this.y + this.h - PAD - 3;
        d.fill(left, stripTop, left + inner, stripBottom, p.wellEdge());
        int zx = left + 1;
        int zw = inner - 2;
        d.fill(zx, stripTop + 1, zx + zw, stripBottom - 1, p.well());
        for (Zone zone : this.zones) {
            int a = zx + Math.round(Math.max(0F, zone.start()) * zw);
            int b = zx + Math.round(Math.min(1F, zone.end()) * zw);
            if (b > a) {
                d.fill(a, stripTop + 1, b, stripBottom - 1, colorOf(zone, p));
            }
        }
        int cx = zx + Math.min(zw - 1, Math.round(position() * (zw - 1)));
        int mark = p.text();
        d.fill(cx, stripTop, cx + 1, stripBottom, mark);
        for (int row = 0; row < 3; row++) {
            int half = 2 - row;
            d.fill(cx - half, stripTop - 3 + row, cx + half + 1, stripTop - 2 + row, mark);
            d.fill(cx - half, stripBottom + 2 - row, cx + half + 1, stripBottom + 3 - row, mark);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || button != 0 || !isOver(mx, my)) {
            return false;
        }
        return activate();
    }

    @Override
    protected boolean navigable() {
        return this.onHit != null;
    }

    @Override
    protected boolean activate() {
        if (this.onHit == null || !this.active) {
            return false;
        }
        click();
        this.onHit.accept(hit());
        return true;
    }
}
