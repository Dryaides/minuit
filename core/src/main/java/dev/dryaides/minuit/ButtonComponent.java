package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Bounce;

import dev.dryaides.minuit.internal.Maths;
import java.util.function.IntConsumer;

/**
 * A raised plate with an optional icon. The action receives the mouse button, so a value button
 * can step forward on a left click and back on a right click.
 */
public class ButtonComponent extends BaseComponent {

    public enum Style {
        /** The one action the screen exists for. */
        PRIMARY,
        RAISED,
        /** Drawn flat until hovered. */
        GHOST
    }

    /**
     * What the button is for, drawn by the frame style in the palette's roles. It replaces
     * {@link #style} when set.
     */
    public enum Role {
        /** The action a screen exists for, ringed in the accent colour. */
        HERO,
        PRIMARY,
        /** An outlined plate, quieter than a plain one. */
        SECONDARY,
        /** Deletes or resets, in the palette's destructive colours. */
        DESTRUCTIVE;

        /**
         * How long this role's press takes against the family's press time: the hero and a destructive
         * button sink slower and weigh more, a secondary one is quicker.
         */
        @Incubating
        public float pressWeight() {
            return switch (this) {
                case HERO -> 1.25F;
                case PRIMARY -> 1F;
                case SECONDARY -> 0.75F;
                case DESTRUCTIVE -> 1.5F;
            };
        }
    }

    public String label;
    public Role role;
    public IconArt icon;
    public Icon themeIcon;
    /** Drawn on the face after the icon and label, moving and clipped with it like they are. */
    public Content content;
    public Style style = Style.RAISED;
    /**
     * The option currently in effect: the plain plate held in its hover face, whatever the style
     * or role.
     */
    public boolean selected;
    /** The largest text scale, so a row of buttons can share one size. */
    public float maxScale = 1F;
    /** Held down: set by the click, cleared by the release, shown only while the mouse is over it. */
    private boolean pressed;
    /** When the last press let go, for the pop of {@link MotionStyle#BOUNCY}; -1 before any. */
    private long releasedAt = -1;
    /** A face of its own over the role's, 0 for none. */
    private int tint;
    /** 0 at rest, 1 sunk all the way, for a family with a press time. */
    private float pressAnim;
    private long pressTime;
    private final IntConsumer action;

    public ButtonComponent(Box box, String label, IntConsumer action) {
        super(box);
        this.label = label;
        this.action = action;
    }

    public ButtonComponent(Box box, String label, Runnable action) {
        this(box, label, button -> action.run());
    }

    /** An icon looked up in the theme at draw time, so the theme's own art wins. */
    public ButtonComponent icon(Icon icon) {
        this.themeIcon = icon;
        return this;
    }

    public ButtonComponent icon(IconArt icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Any art a button carries besides its icon and label (a skin head, a preview): minuit calls it
     * inside the face, offset by the press sink and clipped to the face, so it moves as text does.
     */
    @FunctionalInterface
    public interface Content {
        /** {@code x, y, w, h} is the face as drawn this frame; {@code color} is the label's ink. */
        void draw(Draw d, int x, int y, int w, int h, int color);
    }

    public ButtonComponent content(Content content) {
        this.content = content;
        return this;
    }

    public ButtonComponent style(Style style) {
        this.style = style;
        return this;
    }

    /**
     * A face colour of its own instead of the role's: the hover lifts it 10 % toward white and the label
     * takes whichever text colour reads on it. 0 goes back to the role's face.
     */
    public ButtonComponent tint(int color) {
        this.tint = color;
        return this;
    }

    public ButtonComponent role(Role role) {
        this.role = role;
        return this;
    }

    public ButtonComponent selected(boolean value) {
        this.selected = value;
        return this;
    }

    public ButtonComponent active(boolean value) {
        this.active = value;
        return this;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        Role role = this.selected ? null : this.role;
        Style shown = this.selected ? Style.RAISED : role == Role.HERO || role == Role.PRIMARY ? Style.PRIMARY
                : role == Role.SECONDARY ? Style.RAISED : this.style;
        int base;
        int hover;
        int textColor;
        switch (shown) {
            case PRIMARY -> {
                base = p.primary();
                hover = p.primaryHover();
                textColor = p.primaryText();
            }
            case GHOST -> {
                base = Shapes.alpha(p.raised(), 0);
                hover = p.raised();
                textColor = Shapes.textFor(p, p.text(), Shapes.mix(base, hover, this.hoverAnim));
            }
            default -> {
                base = p.raised();
                hover = p.raisedHover();
                textColor = Shapes.textFor(p, p.raisedText(), base);
            }
        }
        if (this.selected) {
            base = theme.frame().selectedFace(p, 0F);
            hover = theme.frame().selectedFace(p, 1F);
            textColor = Shapes.textOn(p, theme.frame().selectedFace(p, this.hoverAnim));
        }
        if (role == Role.DESTRUCTIVE) {
            base = p.destructive();
            hover = p.destructiveHover();
            textColor = p.destructiveText();
        }
        if (this.tint != 0 && !this.selected) {
            base = this.tint;
            hover = Shapes.mix(this.tint, 0xFFFFFFFF, 0.10F);
            textColor = Shapes.textOn(p, Shapes.mix(base, hover, this.hoverAnim));
        }
        boolean down = this.active && this.pressed && hovered;
        boolean bouncy = motion() == MotionStyle.BOUNCY;
        // Bouncy squashes the plate 1 px from the top while held, then pops it 1 px above rest.
        int squash = 0;
        if (bouncy && this.active) {
            long since = this.releasedAt < 0 ? -1 : clock().getAsLong() - this.releasedAt;
            squash = down ? 1 : since < 0 ? 0 : Bounce.pop(since);
        }
        FrameStyle frame = this.label == null || this.label.isEmpty() ? theme.frame().iconStyle() : theme.frame();
        int lip = frame.buttonLip(this.h);
        // A face sinks at most into its own lip, so a small button's press is as deep as its lip.
        int travel = lip > 0 ? Math.min(lip, frame.pressTravel()) : frame.pressTravel();
        int selectedTravel = lip > 0 ? Math.min(lip, frame.selectedTravel()) : frame.selectedTravel();
        // A family with a press time sinks the face a whole pixel at a time; -1 lets the relief draw the press.
        int sunk = -1;
        if (frame.pressMs() > 0 && !bouncy && !this.selected) {
            long now = clock().getAsLong();
            long dt = this.pressTime == 0 ? -1 : now - this.pressTime;
            this.pressTime = now;
            float ms = frame.pressMs() * (role == null ? 1F : role.pressWeight());
            float step = reducedMotion() || dt < 0 ? 1F : Maths.clamp(dt, 0L, 250L) / ms;
            this.pressAnim = down ? Math.min(1F, this.pressAnim + step) : Math.max(0F, this.pressAnim - step);
            sunk = Math.round(this.pressAnim * travel);
        }
        int py = this.y + squash;
        int ph = this.h - squash;
        if (!this.active && (frame.buttonLip(this.h) > 0 || !FrameStyle.bleedOf(frame).equals(Insets.NONE))) {
            frame.button(d, p, this.x, this.y, this.w, this.h, role, p.disabled(), Shapes.Relief.RAISED);
            textColor = Ink.disabled(p, p.disabled());
        } else if (!this.active) {
            Shapes.rounded(d, this.x, this.y, this.w, this.h, 3, p.wellEdge());
            Shapes.rounded(d, this.x + 1, this.y + 1, this.w - 2, this.h - 2, 2, p.disabled());
            textColor = Ink.disabled(p, p.disabled());
        } else if (sunk >= 0 && (shown != Style.GHOST || this.hoverAnim >= 0.05F)) {
            frame.button(d, p, this.x, py, this.w, ph, role, Shapes.mix(base, hover, this.hoverAnim),
                    Shapes.Relief.RAISED, sunk);
        } else if (shown != Style.GHOST || this.hoverAnim >= 0.05F) {
            frame.button(d, p, this.x, py, this.w, ph, role, Shapes.mix(base, hover, this.hoverAnim),
                    down ? Shapes.Relief.PRESSED
                            : this.selected ? theme.frame().selectedRelief() : Shapes.Relief.RAISED);
        }
        if (this.selected && this.active && !down) {
            frame.selectedMark(d, p, this.x, py, this.w, ph, Shapes.mix(base, hover, this.hoverAnim));
        }
        int sink = bouncy ? squash : sunk >= 0 ? sunk : down ? travel : this.selected ? selectedTravel : 0;
        int shiftX = bouncy || !down ? 0 : frame.pressShiftX();
        IconArt drawnIcon = this.themeIcon != null ? theme.icon(this.themeIcon) : this.icon;
        // A 16 px item or sprite in a button under 20 px shrinks to fit inside the plate's rim.
        float iconScale = drawnIcon == null || drawnIcon.height() <= this.h - 4 ? 1F
                : iconFit(d.guiScale(), Math.max(1, this.h - 2), drawnIcon.height());
        int iconW = drawnIcon == null ? 0 : Math.round(drawnIcon.width() * iconScale);
        int iconH = drawnIcon == null ? 0 : Math.round(drawnIcon.height() * iconScale);
        int iconPart = drawnIcon == null ? 0 : iconW + (this.label != null && !this.label.isEmpty() ? 3 : 0);
        int room = this.w - 6 - iconPart;
        String text = this.label;
        float scale = 1F;
        int textWidth = 0;
        if (text != null && !text.isEmpty()) {
            // Long translations shrink, then get cut, so they never leave the button.
            scale = Math.min(this.maxScale, room / (float) Math.max(1, d.width(text)));
            if (scale < 0.75F) {
                scale = 0.75F;
                text = d.ellipsize(text, (int) (room / scale));
            }
            textWidth = Math.round(d.width(text) * scale);
        }
        int cx = this.x + shiftX + (this.w - iconPart - textWidth) / 2;
        int top = this.y + sink;
        // The face's height: icon and content centre on it and are clipped to it, so a sunk icon never leaves the plate.
        int faceH = this.h - lip;
        d.scissor(this.x + 1, top + 1, this.x + this.w - 1, Math.min(this.y + this.h, top + faceH) - 1);
        if (drawnIcon != null) {
            int iconY = textWidth == 0 || iconScale < 1F ? top + (faceH - iconH) / 2
                    : Draw.iconTop(top + (faceH - 8) / 2, iconH);
            if (iconScale < 1F) {
                d.push(cx, iconY, iconScale);
                drawnIcon.draw(d, 0, 0, textColor, this.active ? 1F : 0.55F);
                d.pop();
            } else {
                drawnIcon.draw(d, cx, iconY, textColor, this.active ? 1F : 0.55F);
            }
            cx += iconPart;
        }
        if (this.content != null) {
            this.content.draw(d, this.x + shiftX, top, this.w, faceH, textColor);
        }
        d.endScissor();
        if (text != null && !text.isEmpty()) {
            // Text is cut only at the sides: a face shorter than the glyphs lets them overlap the rim and lip
            // rather than lose their top row.
            d.scissor(this.x + 1, this.y, this.x + this.w - 1, this.y + this.h);
            float glyphH = 8 * scale;
            float ty = top + (faceH - glyphH) / 2F - (scale < 1F ? 0.5F : 0F);
            ty = Math.max(this.y, Math.min(ty, this.y + this.h - glyphH));
            if (scale < 1F) {
                d.push(cx, ty, scale);
                d.text(text, 0, 0, textColor);
                d.pop();
            } else {
                d.text(text, cx, (int) Math.floor(ty), textColor);
            }
            d.endScissor();
        }
    }

    /**
     * The least height a button with a label needs in {@code style}: the font's 9 px line, the style's lip and
     * one row above and below, so the whole label sits on the face. A shorter button still shows the whole
     * label (text is never clipped vertically), but it overlaps the rim or lip.
     */
    public static int minLabelHeight(FrameStyle style) {
        int h = 9 + 2;
        while (h < 9 + 2 + style.buttonLip(h)) {
            h++;
        }
        return h;
    }

    /**
     * The largest scale at which one texture pixel covers a whole number of screen pixels ({@link Draw#crispScale}'s
     * rule) and the icon stays within {@code room} GUI pixels: at GUI scale 3, a 16 px item in a 14 px button
     * gets 2/3 (10.7 px, inside the plate's 1 px rim). A renderer without a GUI scale gets {@code room / height}.
     */
    static float iconFit(int guiScale, int room, int height) {
        if (guiScale <= 0) {
            return room / (float) height;
        }
        int pixels = Maths.clamp((long) Math.floor(guiScale * room / (double) height), 1, guiScale);
        return pixels / (float) guiScale;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || !isOver(mx, my) || button > 1) {
            return false;
        }
        sound().press(this.role);
        this.pressed = true;
        this.action.accept(button);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (this.pressed) {
            this.releasedAt = clock().getAsLong();
        }
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
        sound().press(this.role);
        this.releasedAt = clock().getAsLong();
        this.action.accept(0);
        return true;
    }

    @Override
    protected Box focusBox(FrameStyle frame) {
        return FrameStyle.withBleed(frame, focusBox());
    }

    @Override
    protected int focusCorner(FrameStyle frame) {
        return frame.buttonCorner();
    }
}
