package dev.dryaides.minuit;

import java.util.function.IntConsumer;

/**
 * An icon in a well with its label under it, one target for the mouse and the keyboard, as the
 * minecraft.wiki's front page lays its sections out. Under the mouse the well lights
 * ({@link FrameStyle#well(Draw, Palette, int, int, int, int, boolean, boolean)}) and the label takes the
 * text colour; held, the well grows the family's {@link FrameStyle#pressTravel} down.
 */
public class IconTileComponent extends BaseComponent {

    /** The label's row under the well: 1 px of gap, 8 px of text, 1 px under it. */
    public static final int LABEL = 10;

    public String label;
    private Icon themeIcon;
    private IconArt icon;
    private boolean pressed;
    private final IntConsumer action;

    public IconTileComponent(Box box, String label, IntConsumer action) {
        super(box);
        this.label = label;
        this.action = action;
    }

    public IconTileComponent(Box box, String label, Runnable action) {
        this(box, label, button -> action.run());
    }

    /** An icon looked up in the theme at draw time, so the theme's own art wins. */
    public IconTileComponent icon(Icon icon) {
        this.themeIcon = icon;
        return this;
    }

    public IconTileComponent icon(IconArt icon) {
        this.icon = icon;
        return this;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        FrameStyle frame = theme.frame();
        boolean down = this.active && this.pressed && hovered;
        boolean lit = this.active && (hovered || this.focused);
        int wellH = Math.max(6, this.h - LABEL - (down ? 0 : frame.pressTravel()));
        frame.well(d, p, this.x, this.y, this.w, wellH, false, lit);
        IconArt art = this.themeIcon != null ? theme.icon(this.themeIcon) : this.icon;
        int ink = !this.active ? p.disabledText() : lit ? p.text() : p.textSoft();
        if (art != null) {
            int restH = this.h - LABEL - frame.pressTravel();
            art.draw(d, this.x + (this.w - art.width()) / 2, this.y + (restH - art.height()) / 2, p.text(),
                    this.active ? 1F : 0.55F);
        }
        if (this.label != null && !this.label.isEmpty()) {
            int fit = Math.min(d.width(this.label), this.w);
            d.textFit(this.label, this.x + (this.w - fit) / 2, this.y + this.h - LABEL + 1, this.w, ink);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !isOver(mx, my) || button > 1) {
            return false;
        }
        click();
        this.pressed = true;
        this.action.accept(button);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        this.pressed = false;
        return false;
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
        this.action.accept(0);
        return true;
    }
}
