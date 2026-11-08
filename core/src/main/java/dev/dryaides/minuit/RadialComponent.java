package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.RadialMath;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * A radial wheel: a ring cut in one sector per part, an icon in each. Aim at a part with the mouse,
 * release to pick it; the aimed part stands out of the ring and its name shows in the centre. The aim is the cursor's angle
 * from the centre; inside the dead zone (0.45 of the radius by default) a release cancels. A quick
 * tap (under 150 ms, cursor still in the centre) cancels too, so opening and closing the wheel at
 * once never picks. Each wheel notch turns one page; the chips beside the wheel name the
 * neighbouring pages and turn to them on a click.
 *
 * <p>Parts keep the order they were given in: the gesture to each one is remembered, so never sort
 * them by use. {@link #onPick} receives the item that was under the cursor; check that it still
 * applies when the world may have changed since the last frame.
 *
 * @param <T> what a part stands for
 */
public final class RadialComponent<T> extends BaseComponent {

    /** Draws one part in its slot; the wheel has already drawn the slot's background. */
    @FunctionalInterface
    public interface Renderer<T> {
        void draw(Draw d, Theme theme, T item, Box slot, boolean hot);
    }

    private record Page<T>(String title, List<T> items, Renderer<T> renderer) {
    }

    private static final long TAP_MS = 150;
    private static final long SLIDE_MS = 160;
    private static final int CARD_W = 52;
    private static final int CARD_H = 14;
    private static final int GAP = 2;
    private static final int LIFT = 3;

    private final int radius;
    private final int slotW;
    private final int slotH;
    private final float deadZone;
    private final List<Page<T>> pages;
    private final Function<T, String> label;
    private final Function<T, IconArt> icon;
    private final Supplier<String> centerLabel;
    private final BooleanSupplier keyDown;
    private final Consumer<T> onPick;
    private final Runnable onCancel;
    private final Consumer<T> onHover;
    private final IntConsumer onPage;
    private final boolean dim;

    private int page;
    private int selected = -1;
    /** -1 none, 0 the previous page's card, 1 the next one's. */
    private int hoveredCard = -1;
    private long openedAt;
    private boolean closed;
    private int slideDir;
    private long slideStart;
    /** Ring pixel runs as {x0, x1, y, packed RadialMath.ring value}, rebuilt when the key changes. */
    private final List<int[]> runs = new ArrayList<>();
    private long runsKey = Long.MIN_VALUE;

    private RadialComponent(Builder<T> b) {
        super(b.area);
        this.radius = b.radius;
        this.slotW = b.slotW;
        this.slotH = b.slotH;
        this.deadZone = b.deadZone;
        this.pages = List.copyOf(b.pages);
        this.label = b.label;
        this.icon = b.icon;
        this.centerLabel = b.centerLabel;
        this.keyDown = b.keyDown;
        this.onPick = b.onPick;
        this.onCancel = b.onCancel;
        this.onHover = b.onHover;
        this.onPage = b.onPage;
        this.dim = b.dim;
        this.page = this.pages.isEmpty() ? 0 : Math.floorMod(b.startPage, this.pages.size());
        open();
    }

    /** A wheel centred in {@code area} (usually the whole screen). */
    public static <T> Builder<T> builder(Box area, int radius) {
        return new Builder<>(area, radius);
    }

    /** Starts a new gesture: the tap guard counts from now. */
    public void open() {
        this.openedAt = BaseComponent.clock.getAsLong();
        this.closed = false;
        this.selected = -1;
    }

    public int page() {
        return this.page;
    }

    /** The item under the cursor at the last frame, or null in the dead zone. */
    public T selected() {
        List<T> items = items();
        return this.selected >= 0 && this.selected < items.size() ? items.get(this.selected) : null;
    }

    private List<T> items() {
        return this.pages.isEmpty() ? List.of() : this.pages.get(this.page).items();
    }

    private int cx() {
        return this.x + this.w / 2;
    }

    private int cy() {
        return this.y + this.h / 2;
    }

    private int inner() {
        return this.radius - Math.max(this.slotW, this.slotH) / 2;
    }

    private int outer() {
        return this.radius + Math.max(this.slotW, this.slotH) / 2;
    }

    /** The slot of part {@code i} of {@code n}, clockwise from the top, in the middle of its sector. */
    private Box slot(int i, int n, boolean hot) {
        double angle = Math.toRadians(-90 + i * (360.0 / n));
        double mid = (inner() + outer()) / 2.0 + (hot ? LIFT : 0);
        int ox = cx() + (int) Math.round(mid * Math.cos(angle));
        int oy = cy() + (int) Math.round(mid * Math.sin(angle));
        return new Box("slot", ox - this.slotW / 2, oy - this.slotH / 2, this.slotW, this.slotH);
    }

    private Box card(int side) {
        int reach = outer() + LIFT + 8;
        int left = side == 0 ? cx() - reach - CARD_W : cx() + reach;
        return new Box("card", left, cy() - CARD_H / 2, CARD_W, CARD_H);
    }

    private void aim(int mx, int my) {
        int before = this.selected;
        this.hoveredCard = -1;
        if (this.pages.size() > 1) {
            for (int side = 0; side < 2; side++) {
                if (card(side).contains(mx, my)) {
                    this.hoveredCard = side;
                }
            }
        }
        int n = items().size();
        double dx = mx - cx();
        double dy = my - cy();
        if (this.hoveredCard >= 0 || n == 0 || RadialMath.inDeadZone(dx, dy, this.radius, this.deadZone)) {
            this.selected = -1;
        } else {
            this.selected = RadialMath.sector(dx, dy, n);
        }
        if (this.selected != before && this.selected >= 0) {
            this.onHover.accept(items().get(this.selected));
        }
    }

    private void turn(int dir) {
        if (this.pages.size() <= 1) {
            return;
        }
        this.page = RadialMath.pageStep(this.page, -dir, this.pages.size());
        this.selected = -1;
        this.slideDir = dir;
        this.slideStart = BaseComponent.clock.getAsLong();
        BaseComponent.sound.tap();
        this.onPage.accept(this.page);
    }

    /** Ends the gesture as a release at the last aimed position would. */
    public void release() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        T item = selected();
        if (this.hoveredCard >= 0 || item == null) {
            this.onCancel.run();
            return;
        }
        this.onPick.accept(item);
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        aim(mx, my);
        Palette p = theme.palette();
        if (this.dim) {
            d.fill(this.x, this.y, this.x + this.w, this.y + this.h, p.shadow());
        }
        List<T> items = items();
        int n = items.size();
        Renderer<T> renderer = this.pages.isEmpty() ? null : this.pages.get(this.page).renderer();
        ring(d, p, n);
        for (int i = 0; i < n; i++) {
            boolean hot = i == this.selected;
            renderer.draw(d, theme, items.get(i), slot(i, n, hot), hot);
        }
        disc(d, p);
        T item = selected();
        String middle = item != null ? this.label.apply(item) : this.centerLabel.get();
        int room = (int) (this.radius * this.deadZone * 2) - 4;
        String shown = d.ellipsize(middle, room);
        d.centered(shown, cx(), cy() - 4, item != null ? p.text() : p.bad());
        strip(d, theme);
        if (this.pages.size() > 1) {
            for (int side = 0; side < 2; side++) {
                Box card = card(side);
                theme.frame().raised(d, p, card.x(), card.y(), card.w(), card.h(),
                        side == this.hoveredCard ? p.raisedHover() : p.raised(), true);
                Page<T> other = this.pages.get(Math.floorMod(this.page + (side == 0 ? -1 : 1), this.pages.size()));
                d.textIn(other.title(), new Box("card", card.x() + 3, card.y(), card.w() - 6, card.h()), p.raisedText());
            }
        }
        if (this.keyDown != null && !this.closed && !this.keyDown.getAsBoolean()) {
            releaseOrTap();
        }
    }

    /** The sectors: resting faces {@code surface}, the aimed one {@code primary} over its shadow. */
    private void ring(Draw d, Palette p, int n) {
        long key = ((long) n << 48) ^ ((long) (this.selected + 1) << 40) ^ ((long) cx() << 20) ^ cy();
        if (key != this.runsKey) {
            this.runsKey = key;
            this.runs.clear();
            int reach = outer() + LIFT;
            for (int py = cy() - reach; py <= cy() + reach; py++) {
                int start = 0;
                int code = RadialMath.OUTSIDE;
                for (int px = cx() - reach; px <= cx() + reach + 1; px++) {
                    int c = px > cx() + reach ? RadialMath.OUTSIDE : RadialMath.ring(px + 0.5 - cx(),
                            py + 0.5 - cy(), n, inner(), outer(), GAP, this.selected, LIFT);
                    if (c != code) {
                        if (code != RadialMath.OUTSIDE) {
                            this.runs.add(new int[]{start, px, py, code});
                        }
                        start = px;
                        code = c;
                    }
                }
            }
        }
        for (int[] run : this.runs) {
            boolean hot = run[3] / 4 == this.selected;
            int face = hot ? p.primary() : p.surface();
            int color = switch (run[3] % 4) {
                case RadialMath.SHADOW -> p.shadow();
                case RadialMath.EDGE -> hot ? Shapes.bevelDark(face) : p.surfaceEdge();
                default -> face;
            };
            d.fill(run[0], run[2], run[1], run[2] + 1, color);
        }
    }

    /** The dead zone, drawn as the sunken disc a release cancels in. */
    private void disc(Draw d, Palette p) {
        double r = Math.min(this.radius * this.deadZone, inner() - 3);
        discFill(d, r, p.wellEdge());
        discFill(d, r - 1, p.well());
    }

    private void discFill(Draw d, double r, int color) {
        for (int row = (int) -Math.ceil(r); row < Math.ceil(r); row++) {
            double dy = row + 0.5;
            if (Math.abs(dy) < r) {
                int half = (int) Math.round(Math.sqrt(r * r - dy * dy));
                d.fill(cx() - half, cy() + row, cx() + half, cy() + row + 1, color);
            }
        }
    }

    private static final int STRIP_H = 15;

    /** The page titles above the wheel when there is more than one page, sliding in from the side of the last notch. */
    private void strip(Draw d, Theme theme) {
        Palette p = theme.palette();
        // A wheel with one page has nothing to turn to, so it has no header.
        if (this.pages.size() <= 1) {
            return;
        }
        String title = this.pages.get(this.page).title();
        int titleW = d.width(title);
        int stripY = cy() - outer() - LIFT - 16;
        int bandW = titleW + 40;
        theme.frame().section(d, p, cx() - bandW / 2, stripY - 3, bandW, STRIP_H);
        float k = BaseComponent.reducedMotion ? 1F : RadialMath.slide(this.slideStart, BaseComponent.clock.getAsLong(), SLIDE_MS);
        int shift = this.slideDir == 0 ? 0 : Math.round(this.slideDir * (1F - k) * (titleW / 2F + 40));
        if (k >= 1F) {
            this.slideDir = 0;
        }
        d.scissor(cx() - bandW / 2, stripY - 3, cx() + bandW / 2, stripY - 3 + STRIP_H);
        d.centered(title, cx() + shift, stripY, p.text());
        d.endScissor();
        if (this.pages.size() > 1) {
            int gap = titleW / 2 + 12;
            d.text("<", cx() + shift - gap - 4, stripY, p.textSoft());
            d.text(">", cx() + shift + gap, stripY, p.textSoft());
        }
    }

    private void releaseOrTap() {
        if (this.closed) {
            return;
        }
        if (this.selected < 0 && this.hoveredCard < 0
                && BaseComponent.clock.getAsLong() - this.openedAt < TAP_MS) {
            this.closed = true;
            this.onCancel.run();
            return;
        }
        release();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.keyDown == null && button == 0) {
            // Without a held key each click is its own gesture: a pick ends one, the next click starts another.
            this.closed = false;
        }
        if (this.closed || button != 0) {
            return false;
        }
        aim((int) mx, (int) my);
        if (this.hoveredCard >= 0) {
            turn(this.hoveredCard == 0 ? -1 : 1);
            return true;
        }
        if (this.keyDown == null) {
            releaseOrTap();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if ((this.closed && this.keyDown != null) || this.pages.size() <= 1 || amount == 0) {
            return false;
        }
        turn(amount > 0 ? -1 : 1);
        return true;
    }

    public static final class Builder<T> {
        private final Box area;
        private final int radius;
        private final List<Page<T>> pages = new ArrayList<>();
        private int slotW = 44;
        private int slotH = 24;
        private float deadZone = 0.45F;
        private int startPage;
        private Function<T, String> label = String::valueOf;
        private Function<T, IconArt> icon;
        private Supplier<String> centerLabel = () -> "";
        private BooleanSupplier keyDown;
        private Consumer<T> onPick = item -> {
        };
        private Runnable onCancel = () -> {
        };
        private Consumer<T> onHover = item -> {
        };
        private IntConsumer onPage = index -> {
        };
        private boolean dim = true;

        private Builder(Box area, int radius) {
            this.area = area;
            this.radius = radius;
        }

        /** One page of parts; an empty page has nothing to aim at and a release on it cancels. */
        public Builder<T> page(String title, List<T> items, Renderer<T> renderer) {
            this.pages.add(new Page<>(title, List.copyOf(items), renderer));
            return this;
        }

        /** A page whose parts show their {@link #icon}, or their {@link #label} when they have none. */
        public Builder<T> page(String title, List<T> items) {
            return page(title, items, (d, theme, item, slot, hot) -> {
                int color = hot ? theme.palette().primaryText() : theme.palette().text();
                IconArt glyph = this.icon == null ? null : this.icon.apply(item);
                if (glyph == null) {
                    d.textIn(this.label.apply(item), new Box("label", slot.x() + 2, slot.y(), slot.w() - 4, slot.h()),
                            color);
                    return;
                }
                d.icon(glyph, slot.x() + (slot.w() - glyph.width()) / 2, slot.y() + (slot.h() - glyph.height()) / 2,
                        color);
            });
        }

        /** The icon each part shows in its sector; its name shows only in the centre. */
        public Builder<T> icon(Function<T, IconArt> icon) {
            this.icon = icon;
            return this;
        }

        public Builder<T> slot(int width, int height) {
            this.slotW = width;
            this.slotH = height;
            return this;
        }

        /** Fraction of the radius under which a release cancels. */
        public Builder<T> deadZone(float fraction) {
            this.deadZone = fraction;
            return this;
        }

        public Builder<T> startPage(int index) {
            this.startPage = index;
            return this;
        }

        /** The name of an item, shown in the centre while it is aimed at. */
        public Builder<T> label(Function<T, String> name) {
            this.label = name;
            return this;
        }

        /** Shown in the centre over the dead zone: what a release there does. */
        public Builder<T> centerLabel(Supplier<String> text) {
            this.centerLabel = text;
            return this;
        }

        /** The wheel lives while {@code down} holds and picks on its release. */
        public Builder<T> holdToSelect(BooleanSupplier down) {
            this.keyDown = down;
            return this;
        }

        /** A left click picks (the default). */
        public Builder<T> clickToSelect() {
            this.keyDown = null;
            return this;
        }

        public Builder<T> onPick(Consumer<T> action) {
            this.onPick = action;
            return this;
        }

        /** A release in the dead zone, on a page card or on an empty page, or a quick tap. */
        public Builder<T> onCancel(Runnable action) {
            this.onCancel = action;
            return this;
        }

        /** Each time the aimed part changes; silent unless the caller plays a sound. */
        public Builder<T> onHover(Consumer<T> action) {
            this.onHover = action;
            return this;
        }

        /** After each page turn, to remember the page for the next opening. */
        public Builder<T> onPage(IntConsumer action) {
            this.onPage = action;
            return this;
        }

        /**
         * Whether the wheel dims its whole box behind it (the default, for a wheel over the game).
         * Off for a wheel shown inside a card, where the box is a square and not the screen.
         */
        public Builder<T> dim(boolean on) {
            this.dim = on;
            return this;
        }

        public RadialComponent<T> build() {
            return new RadialComponent<>(this);
        }
    }
}
