package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ReelMath;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A horizontal roulette, after Meccha Crafteleon's map roulette: a strip of cards runs under a
 * marker for 3200 ms, eases out (cubic) and stops on the card asked for. A tick sounds each time a
 * new card reaches the marker, its pitch rising toward the stop, and one dry tick marks the landing.
 * With {@link BaseComponent#reducedMotion} the strip jumps to the result without ticks.
 *
 * <p>The cards run inside a well of the theme, each one a {@link FrameStyle#section} with an
 * optional picture and its label; the landed card gets a ring of the accent colour.
 *
 * <pre>{@code
 * ReelComponent<Map> reel = new ReelComponent<>(box, maps, Map::name).image(Map::thumbnail).onLand(m -> start(m));
 * reel.spin(maps.indexOf(drawn), seedFromServer);
 * }</pre>
 */
public class ReelComponent<T> extends BaseComponent {

    private final List<T> cards;
    private final Function<T, String> label;
    private Function<T, AsyncImage> image;
    private Function<T, AsyncImage> head;
    private CardDecorator<T> decorator;
    private java.util.function.ToIntFunction<Palette> ringColor = Palette::accent;
    private boolean muted;
    private Consumer<T> onLand = t -> {
    };
    private int cardW = 96;
    private int cardH = 62;
    private int gap = 6;
    private long delay;
    private int noRepeat = ReelMath.NO_REPEAT;

    private List<Integer> strip;
    private int landing;
    private long start = -1;
    private int lastTick = -1;
    private boolean landed;

    /** Draws over a card after the kit's own drawing: a band, a line, a badge. */
    public interface CardDecorator<T> {
        void draw(Draw d, Theme theme, T card, Box cardBox, boolean landed);
    }

    /**
     * Where a spin stands, to carry it over a rebuild of the panel: {@link #state} before, then
     * {@link #restore} on the new reel, and the strip goes on without a new spin, a second tick or a
     * second landing.
     */
    public record Spin(List<Integer> strip, int landing, long start, boolean landed) {
    }

    public ReelComponent(Box box, List<T> cards, Function<T, String> label) {
        super(box);
        if (cards.isEmpty()) {
            throw new IllegalArgumentException("a reel needs at least one card");
        }
        this.cards = List.copyOf(cards);
        this.label = label;
        this.strip = java.util.stream.IntStream.range(0, this.cards.size()).boxed().toList();
    }

    /** The picture of a card, drawn above its label; a pending one shows a placeholder until it is ready. */
    public ReelComponent<T> image(Function<T, AsyncImage> image) {
        this.image = image;
        return this;
    }

    /** A player's face for each card, from its skin ({@code PlayerHeadComponent}), drawn above the label. */
    public ReelComponent<T> head(Function<T, AsyncImage> skin) {
        this.head = skin;
        return this;
    }

    /** Extra drawing on each card. */
    public ReelComponent<T> decorate(CardDecorator<T> decorator) {
        this.decorator = decorator;
        return this;
    }

    /** The ring colour of the landed card and of the marker after landing, a palette role; the accent by default. */
    public ReelComponent<T> ringColor(java.util.function.ToIntFunction<Palette> role) {
        this.ringColor = role;
        return this;
    }

    /**
     * True silences this reel's ticks and landing. A row of staggered reels mutes all but one, the one
     * that lands last, so the ticks stay one stream.
     */
    public ReelComponent<T> muted(boolean on) {
        this.muted = on;
        return this;
    }

    public Spin state() {
        return new Spin(this.strip, this.landing, this.start, this.landed);
    }

    /** Takes over a spin from {@link #state}; a landed one does not call {@code onLand} again. */
    public ReelComponent<T> restore(Spin spin) {
        if (spin == null || spin.strip().isEmpty()) {
            return this;
        }
        this.strip = List.copyOf(spin.strip());
        this.landing = spin.landing();
        this.start = spin.start();
        this.landed = spin.landed();
        this.lastTick = this.start < 0 || clock().getAsLong() < this.start ? -1
                : ReelMath.under(ReelMath.offset(progress(), this.landing, step()), step());
        return this;
    }

    /** Called once when the strip stops, with the card under the marker. */
    public ReelComponent<T> onLand(Consumer<T> onLand) {
        this.onLand = onLand;
        return this;
    }

    /** Card size and the gap between cards; 96 by 62 with a gap of 6 by default. */
    public ReelComponent<T> card(int width, int height, int gap) {
        this.cardW = width;
        this.cardH = height;
        this.gap = gap;
        return this;
    }

    /**
     * No card twice among any {@code cards} consecutive cards of a spin's strip; 3 by default, 1 allows
     * repeats. A pool with fewer cards than that gets the widest window it can fill.
     */
    public ReelComponent<T> noRepeatWithin(int cards) {
        this.noRepeat = Math.max(1, cards);
        return this;
    }

    /** Each spin waits {@code ms} before it moves, for a screen still opening; the strip holds still until then. */
    public ReelComponent<T> delay(long ms) {
        this.delay = Math.max(0, ms);
        return this;
    }

    /** Spins onto card {@code target}, the strip drawn from {@code seed}: the same seed gives every player the same strip. */
    public ReelComponent<T> spin(int target, long seed) {
        if (target < 0 || target >= this.cards.size()) {
            throw new IndexOutOfBoundsException(target);
        }
        ReelMath.Strip s = ReelMath.strip(this.cards.size(), target, new Random(seed), this.noRepeat);
        this.strip = s.cards();
        this.landing = s.landing();
        this.start = clock().getAsLong() + this.delay;
        this.lastTick = -1;
        this.landed = false;
        return this;
    }

    /** Spins onto card {@code target} along a strip of its own. */
    public ReelComponent<T> spin(int target) {
        return spin(target, new Random().nextLong());
    }

    /** Spins onto a card drawn from {@code rng}, which also draws the strip. */
    public ReelComponent<T> spin(Random rng) {
        return spin(rng.nextInt(this.cards.size()), rng.nextLong());
    }

    /** True from the start of a spin until it lands. */
    public boolean spinning() {
        return this.start >= 0 && !this.landed;
    }

    /** The card the reel stopped on, or null before the first landing. */
    public T result() {
        return this.landed ? this.cards.get(this.strip.get(this.landing)) : null;
    }

    private int step() {
        return this.cardW + this.gap;
    }

    private double progress() {
        return this.start < 0 || clock().getAsLong() < this.start ? 0 : ReelMath.progress(clock().getAsLong() - this.start, reducedMotion());
    }

    /** Plays the ticks and the landing for the current time; the draw calls it every frame. */
    /** A hidden, folded or culled reel is not rendered but still lands. */
    @Override
    public void tick() {
        update();
    }

    void update() {
        if (this.start < 0 || this.landed || clock().getAsLong() < this.start) {
            return;
        }
        double t = progress();
        if (!reducedMotion() && t < 1.0) {
            int under = ReelMath.under(ReelMath.offset(t, this.landing, step()), step());
            if (under != this.lastTick) {
                this.lastTick = under;
                if (!this.muted) {
                    sound().tick((float) t);
                }
            }
        }
        if (t >= 1.0) {
            this.landed = true;
            if (!this.muted) {
                sound().land();
            }
            this.onLand.accept(result());
        }
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        update();
        Palette p = theme.palette();
        FrameStyle f = theme.frame();
        f.well(d, p, this.x, this.y, this.w, this.h, false);
        double offset = this.start < 0 ? 0 : ReelMath.offset(progress(), this.landing, step());
        int cx = this.x + this.w / 2;
        int top = this.y + (this.h - this.cardH) / 2;
        int half = this.w / 2 - 2;

        d.scissor(this.x + 2, this.y + 2, this.x + this.w - 2, this.y + this.h - 2);
        // At rest the strip has not been dealt yet: the cards repeat both ways, so the marker never sits on an empty half.
        boolean rest = this.start < 0;
        int first = (int) Math.floor((offset - half) / step()) - 1;
        int last = (int) Math.ceil((offset + half) / step()) + 1;
        if (!rest) {
            first = Math.max(0, first);
            last = Math.min(this.strip.size() - 1, last);
        }
        for (int i = first; i <= last; i++) {
            int sx = (int) Math.round(cx - this.cardW / 2.0 + i * step() - offset);
            boolean prize = this.landed && i == this.landing;
            T card = this.cards.get(rest ? Math.floorMod(i, this.cards.size()) : this.strip.get(i));
            f.section(d, p, sx, top, this.cardW, this.cardH);
            if (prize) {
                Shapes.ring(d, sx - 1, top - 1, this.cardW + 2, this.cardH + 2, f.corner() + 1,
                        this.ringColor.applyAsInt(p));
            }
            int textY = top + this.cardH - 13;
            if (this.head != null) {
                int size = Math.max(8, this.cardH - 22);
                PlayerHeadComponent.draw(d, p, this.head.apply(card), sx + (this.cardW - size) / 2, top + 5, size);
            } else if (this.image != null) {
                int thumbW = Math.min(this.cardW - 8, (this.cardH - 20) * 16 / 9);
                int thumbH = thumbW * 9 / 16;
                AsyncImage.draw(d, p, this.image.apply(card), sx + (this.cardW - thumbW) / 2, top + 5, thumbW, thumbH);
            } else {
                textY = top + (this.cardH - 8) / 2;
            }
            String text = d.ellipsize(this.label.apply(card), this.cardW - 6);
            d.centered(text, sx + this.cardW / 2, textY, prize ? p.text() : p.textSoft());
            if (this.decorator != null) {
                this.decorator.draw(d, theme, card, new Box("card", sx, top, this.cardW, this.cardH), prize);
            }
        }
        d.endScissor();

        int marker = this.landed ? this.ringColor.applyAsInt(p) : p.primary();
        d.fill(cx - 1, this.y + 1, cx + 1, top - 1, marker);
        d.fill(cx - 1, top + this.cardH + 1, cx + 1, this.y + this.h - 1, marker);
        d.fill(cx - 3, this.y + 1, cx + 3, this.y + 3, marker);
        d.fill(cx - 3, this.y + this.h - 3, cx + 3, this.y + this.h - 1, marker);
    }
}
