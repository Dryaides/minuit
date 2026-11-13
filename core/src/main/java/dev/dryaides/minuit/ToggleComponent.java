package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Bounce;
import dev.dryaides.minuit.internal.ImageFamily;

import java.util.function.Consumer;

/** An on/off switch, with its label beside it. */
public class ToggleComponent extends BaseComponent {

    public static final int SWITCH = 22;

    public String label;
    private final Consumer<Boolean> onChange;
    public boolean value;
    private float knob;
    /** Where the knob was and when, as the last flip started it; the keyframes of {@link MotionStyle#BOUNCY} run from there. */
    private float flipFrom;
    private long flipAt = -1;

    public ToggleComponent(Box box, String label, boolean value, Consumer<Boolean> onChange) {
        super(box);
        this.label = label;
        this.value = value;
        this.onChange = onChange;
        this.knob = value ? 1 : 0;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        FrameStyle style = theme.frame();
        if (style.gameWidgets()) {
            this.knob = this.value ? 1 : 0;
            boolean lit = this.active && this.hoverAnim >= 0.5F;
            int side = FrameStyle.checkboxSide(this.h);
            style.checkbox(d, p, this.x, this.y + (this.h - side) / 2, side, this.value, lit, this.active);
            int lx = this.x + side + 4;
            d.textShrink(this.label, lx, this.y + 1, this.x + this.w - lx, this.h, 0.75F,
                    this.active ? p.text() : p.disabledText());
            return;
        }
        float target = this.value ? 1 : 0;
        long since = this.flipAt < 0 ? -1 : clock().getAsLong() - this.flipAt;
        // A value set from outside a flip (a restored setting, a mod writing the field) rests on its side at once.
        if (since < 0) {
            this.flipFrom = target;
        }
        switch (since < 0 ? MotionStyle.NONE : motion()) {
            case NONE -> this.knob = target;
            case BOUNCY -> {
                int run = Math.round(Math.abs(target - this.flipFrom) * (SWITCH - 10));
                int moved = Bounce.knob(since, run);
                this.knob = this.flipFrom + Math.signum(target - this.flipFrom) * moved / (float) (SWITCH - 10);
            }
            default -> this.knob += (target - this.knob) * 0.35F;
        }
        int sy = this.y + (this.h - 10) / 2;
        int track;
        if (!this.active) {
            track = p.disabled();
        } else {
            // Official palettes are checked at design time: the OFF groove is the palette track grey, the ON one its primary, unshifted.
            int off = Shapes.mix(p.track(), p.raisedHover(), this.value ? 0F : this.hoverAnim * 0.3F);
            if (this.value) {
                // The palette's own primary, as on buttons and the slider fill; in glass the half's, unmuted.
                Palette m = GlassPalette.marks(p);
                track = Shapes.mix(m.primary(), m.primaryHover(), this.hoverAnim);
            } else {
                track = off;
            }
        }
        FrameStyle shape = style instanceof GlassFrame glass ? glass.base() : style;
        boolean bedrock = (shape instanceof ImageFamily images ? images.base() : shape) == FrameStyles.BEDROCK;
        style.track(d, p, this.x, sy, SWITCH, 10, this.active ? track : bedrock ? p.disabled() : p.wellEdge());
        if (this.active && !this.value) {
            style.trackEdge(d, p, this.x, sy, SWITCH, 10, p.textFaint());
        }
        if (!this.active && !bedrock) {
            Shapes.rounded(d, this.x + 1, sy + 1, SWITCH - 2, 8, 4, track);
        }
        int kx = this.x + 1 + Math.round(this.knob * (SWITCH - 10));
        int knobFace = this.active ? 0xFFF7F7F7 : p.disabledText();
        style.toggleKnob(d, p, kx, sy + 1, knobFace, this.active, this.active && this.hoverAnim >= 0.5F);
        int tx = this.x + SWITCH + 4;
        d.textShrink(this.label, tx, this.y + 1, this.x + this.w - tx, this.h, 0.75F,
                this.active ? p.text() : p.disabledText());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !isOver(mx, my) || button != 0) {
            return false;
        }
        return activate();
    }

    /** The game's checkbox turns its outline white under the keyboard, as under the mouse. */
    @Override
    protected boolean drawFocus(Draw d, Theme theme) {
        FrameStyle style = theme.frame();
        if (!style.gameWidgets()) {
            return false;
        }
        int side = FrameStyle.checkboxSide(this.h);
        style.checkbox(d, theme.palette(), this.x, this.y + (this.h - side) / 2, side, this.value, true, this.active);
        return true;
    }

    @Override
    protected boolean navigable() {
        return true;
    }

    @Override
    protected boolean activate() {
        if (!this.active) {
            return false;
        }
        click();
        this.flipFrom = this.knob;
        this.flipAt = clock().getAsLong();
        this.value = !this.value;
        this.onChange.accept(this.value);
        return true;
    }
}
