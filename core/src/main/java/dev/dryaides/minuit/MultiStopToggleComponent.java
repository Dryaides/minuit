package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Bounce;
import dev.dryaides.minuit.internal.Maths;

import java.util.function.IntConsumer;

/**
 * A switch with two to five stops: one uniform groove, an optional pixel icon in each stop, and the
 * family's knob over the current one (light / glass / dark, off / auto / on). The groove grows with the
 * stop count. A click on a stop moves the knob there, a drag slides it, a click on the label or Enter
 * steps to the next stop; Left, Right, Home and End move it from the keyboard and the pad.
 */
@Incubating
public class MultiStopToggleComponent extends BaseComponent {

    public static final int MIN_STOPS = 2;
    public static final int MAX_STOPS = 5;
    /** The knob's side when no stop has an icon. */
    private static final int BARE = 8;

    public String label;
    private final IntConsumer onChange;
    private final IconArt[] icons;
    private int value;
    private float knob;
    private float flipFrom;
    private long flipAt = -1;
    private boolean held;

    /**
     * @param stops    how many stops, {@value #MIN_STOPS} to {@value #MAX_STOPS}
     * @param value    the stop it starts on, from 0
     * @param onChange called with the new stop each time the player moves the knob
     */
    public MultiStopToggleComponent(Box box, String label, int stops, int value, IntConsumer onChange) {
        super(box);
        if (stops < MIN_STOPS || stops > MAX_STOPS) {
            throw new IllegalArgumentException("stops must be " + MIN_STOPS + " to " + MAX_STOPS + ", got " + stops);
        }
        this.label = label == null ? "" : label;
        this.onChange = onChange;
        this.icons = new IconArt[stops];
        this.value = Maths.clamp(value, 0, stops - 1);
        this.knob = this.value;
    }

    /** One icon per stop, drawn in the groove and on the knob over it; a null leaves that stop bare. */
    public MultiStopToggleComponent icons(IconArt... icons) {
        for (int i = 0; i < this.icons.length; i++) {
            this.icons[i] = i < icons.length ? icons[i] : null;
        }
        return this;
    }

    /** {@link Icon}s, one per stop, as {@link #icons(IconArt...)}. */
    public MultiStopToggleComponent icons(Icon... icons) {
        IconArt[] art = new IconArt[icons.length];
        for (int i = 0; i < icons.length; i++) {
            art[i] = icons[i] == null ? null : icons[i].glyph();
        }
        return icons(art);
    }

    public int stopCount() {
        return this.icons.length;
    }

    public int value() {
        return this.value;
    }

    /** Moves the knob to {@code stop} at once, without calling back: a restored setting. */
    public void set(int stop) {
        this.value = Maths.clamp(stop, 0, stopCount() - 1);
        this.knob = this.value;
        this.flipAt = -1;
    }

    /** The knob's side: the tallest or widest icon with a pixel of room each side, or {@value #BARE}. */
    public int cell() {
        int side = 0;
        for (IconArt icon : this.icons) {
            if (icon != null) {
                side = Math.max(side, Math.max(icon.width(), icon.height()));
            }
        }
        return side == 0 ? BARE : side + 2;
    }

    /** The distance between two stops. */
    private int pitch() {
        return cell() + 2;
    }

    /** The groove's width: grows by one pitch a stop. */
    public int grooveWidth() {
        return stopCount() * pitch();
    }

    public int grooveHeight() {
        return cell() + 2;
    }

    private int grooveY() {
        return this.y + (this.h - grooveHeight()) / 2;
    }

    private void moveTo(int stop) {
        stop = Maths.clamp(stop, 0, stopCount() - 1);
        if (stop == this.value) {
            return;
        }
        click();
        this.flipFrom = this.knob;
        this.flipAt = clock().getAsLong();
        this.value = stop;
        this.onChange.accept(stop);
    }

    /** The stop under {@code mx}, or -1 past the groove. */
    private int stopAt(double mx) {
        int rel = (int) Math.floor(mx) - this.x - 1;
        if (rel < 0 || mx >= this.x + grooveWidth()) {
            return -1;
        }
        return Maths.clamp(rel / pitch(), 0, stopCount() - 1);
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        FrameStyle style = theme.frame();
        int cell = cell();
        int pitch = pitch();
        int gw = grooveWidth();
        int gh = grooveHeight();
        int gy = grooveY();
        long since = this.flipAt < 0 ? -1 : clock().getAsLong() - this.flipAt;
        if (since < 0 || this.held) {
            this.flipFrom = this.value;
        }
        switch (since < 0 || this.held ? MotionStyle.NONE : motion()) {
            case NONE -> this.knob = this.held ? this.knob : this.value;
            case BOUNCY -> {
                int run = Math.round(Math.abs(this.value - this.flipFrom) * pitch);
                int moved = Bounce.knob(since, run);
                this.knob = this.flipFrom + Math.signum(this.value - this.flipFrom) * moved / (float) pitch;
            }
            default -> this.knob += (this.value - this.knob) * 0.35F;
        }
        int groove = this.active ? Shapes.mix(p.track(), p.raisedHover(), this.hoverAnim * 0.3F) : p.disabled();
        style.track(d, p, this.x, gy, gw, gh, groove);
        int ink = this.active ? p.textFaint() : p.disabledText();
        int under = Math.round(this.knob);
        for (int i = 0; i < stopCount(); i++) {
            if (i != under) {
                drawIcon(d, i, this.x + 1 + i * pitch, gy + 1, cell, ink);
            }
        }
        int kx = this.x + 1 + Math.round(this.knob * pitch);
        int face = !this.active ? p.disabled()
                : Shapes.mix(p.raised(), p.raisedHover(), this.held ? 1F : this.hoverAnim);
        Shapes.Relief relief = this.held ? Shapes.Relief.PRESSED : Shapes.Relief.RAISED;
        style.button(d, p, kx, gy + 1, cell, cell, null, face, relief);
        int sink = this.held ? Math.max(1, style.pressTravel()) : 0;
        drawIcon(d, under, kx, gy + 1 + sink, cell - sink, this.active ? p.raisedText() : p.disabledText());
        int tx = this.x + gw + 4;
        if (!this.label.isEmpty() && tx < this.x + this.w) {
            d.textShrink(this.label, tx, this.y + 1, this.x + this.w - tx, this.h, 0.75F,
                    this.active ? p.text() : p.disabledText());
        }
    }

    private void drawIcon(Draw d, int stop, int cx, int cy, int side, int color) {
        IconArt icon = this.icons[stop];
        if (icon != null) {
            d.icon(icon, cx + (cell() - icon.width()) / 2, cy + (side - icon.height()) / 2, color);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !isOver(mx, my) || button != 0) {
            return false;
        }
        int stop = stopAt(mx);
        if (stop < 0) {
            return activate();
        }
        this.held = true;
        this.knob = stop;
        moveTo(stop);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!this.held) {
            return false;
        }
        float at = (float) (mx - this.x - 1 - cell() / 2.0) / pitch();
        this.knob = Maths.clamp(at, 0F, stopCount() - 1F);
        moveTo(Math.round(this.knob));
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!this.held) {
            return false;
        }
        this.held = false;
        this.flipFrom = this.knob;
        this.flipAt = clock().getAsLong();
        return true;
    }

    @Override
    protected boolean navigable() {
        return true;
    }

    /** Enter, Space or the pad's confirm button: the next stop, back to the first after the last. */
    @Override
    protected boolean activate() {
        if (!this.active) {
            return false;
        }
        moveTo((this.value + 1) % stopCount());
        return true;
    }

    /** Left and Right move one stop; Home and End go to the ends. */
    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!this.active) {
            return false;
        }
        int target = switch (key) {
            case LEFT -> this.value - 1;
            case RIGHT -> this.value + 1;
            case HOME -> 0;
            case END -> stopCount() - 1;
            default -> Integer.MIN_VALUE;
        };
        if (target == Integer.MIN_VALUE) {
            return false;
        }
        moveTo(target);
        return true;
    }

    @Override
    protected Box focusBox() {
        return new Box("groove", this.x, grooveY(), grooveWidth(), grooveHeight());
    }
}
