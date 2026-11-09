package dev.dryaides.minuit;

import java.util.List;
import java.util.Locale;

/**
 * Someone's face: a player's head when its skin is there, their initials on a colour until then or instead.
 *
 * <pre>{@code
 * add(new AvatarComponent(at(new Box("me", 10, 10, 24, 24)), "Alex Stone").skin(PlayerSkins.of(uuid, name))
 *         .status(AvatarComponent.Status.ONLINE));
 * }</pre>
 *
 * <p>The colour comes from the name, so the same player keeps it from one screen to another, and from the
 * palette, so it follows the theme. The head is square and takes the shorter side of the box, centred. A skin
 * that is still loading or that failed shows the initials, not a blank.
 */
public class AvatarComponent extends BaseComponent {
    /** A dot on the corner that says if the player is there. */
    public enum Status { NONE, ONLINE, AWAY, BUSY, OFFLINE }

    private static final int MIN_FOR_STATUS = 14;
    private static final int STATUS_SMALL = 4;
    private static final int STATUS_LARGE = 6;
    private static final int LARGE_FROM = 28;
    /** Below this side there is no room for two letters; below {@link #MIN_FOR_LETTER}, none for one. */
    private static final int MIN_FOR_TWO = 15;
    private static final int MIN_FOR_LETTER = 9;

    private String name;
    private String initials;
    private AsyncImage skin;
    private Picture picture;
    private Status status = Status.NONE;

    public AvatarComponent(Box box, String name) {
        super(box);
        name(name);
    }

    public final AvatarComponent name(String name) {
        this.name = name == null ? "" : name.strip();
        this.initials = initialsOf(this.name);
        return this;
    }

    public String name() {
        return name;
    }

    public String initials() {
        return initials;
    }

    /** The skin to draw the head from; null for initials only. */
    public AvatarComponent skin(AsyncImage skin) {
        this.skin = skin;
        return this;
    }

    /**
     * A picture instead of a head: a photo, a mod's logo, an item. An image fills the square around its
     * focal point; an item or sprite sits on the name's colour, scaled by whole pixels. Until an image has
     * loaded, and if it fails, the skin or the initials show. It wins over {@link #skin(AsyncImage)}.
     */
    public AvatarComponent picture(Picture picture) {
        this.picture = picture;
        return this;
    }

    public Picture picture() {
        return picture;
    }

    public AvatarComponent status(Status status) {
        this.status = status == null ? Status.NONE : status;
        return this;
    }

    public Status status() {
        return status;
    }

    public AvatarComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public AvatarComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    /** The first letters of the first and last word, upper case. "Alex Stone" gives AS, "Alex" gives A, "" gives "". */
    public static String initialsOf(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] words = name.strip().split("[\\s_\\-.]+");
        if (words.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(4);
        appendFirst(out, words[0]);
        if (words.length > 1) {
            appendFirst(out, words[words.length - 1]);
        }
        return out.toString().toUpperCase(Locale.ROOT);
    }

    private static void appendFirst(StringBuilder out, String word) {
        if (!word.isEmpty()) {
            out.appendCodePoint(word.codePointAt(0));
        }
    }

    /** Which of the palette's colours a name wears, the same on every screen. */
    static int faceOf(Palette p, String name) {
        return FormLook.solid(switch (Math.floorMod(name.hashCode(), 6)) {
            case 0 -> p.primary();
            case 1 -> p.accent();
            case 2 -> p.accentDeep();
            case 3 -> p.good();
            case 4 -> p.warn();
            default -> p.destructive();
        });
    }

    /** Draws one head or set of initials. Used by {@link AvatarGroupComponent} too. */
    static void paint(Draw d, Theme t, String initials, int face, AsyncImage skin, boolean live, int left, int top, int side) {
        paint(d, t, initials, face, skin, live, left, top, side, side);
    }

    /** With the part of the head left bare by what lies over it: the letters are centred there, not cut. */
    static void paint(Draw d, Theme t, String initials, int face, AsyncImage skin, boolean live, int left, int top, int side, int bare) {
        Palette p = t.palette();
        if (skin != null && skin.state() == AsyncImage.State.READY) {
            d.scissor(left, top, left + side, top + side);
            PlayerHeadComponent.draw(d, p, skin, left, top, side);
            d.endScissor();
            if (!live) {
                d.fill(left, top, left + side, top + side, Shapes.alpha(FormLook.surface(p), 0xA0));
            }
            return;
        }
        int back = live ? face : FormLook.disabledFace(p);
        t.frame().plate(d, p, left, top, side, side, back, 0);
        if (side < MIN_FOR_LETTER || initials.isEmpty()) {
            return;
        }
        String shown = (side < MIN_FOR_TWO || bare < TextMeasure.width(initials) + 1) && initials.length() > 1 ? initials.substring(0, initials.offsetByCodePoints(0, 1)) : initials;
        int ink = live ? Ink.on(p, back) : p.disabledText();
        int width = TextMeasure.width(shown) - 1;
        d.text(shown, left + (bare - width) / 2, top + (side - FormLook.FONT_H + 1) / 2, ink);
    }

    /** A picture in the square: rounded like the initials' tile on a plain surface, veiled when off. */
    static void paintPicture(Draw d, Theme t, Picture picture, int face, boolean live, int left, int top, int side) {
        Palette p = t.palette();
        int corner = t.frame().plateCorner(side, side);
        if (picture.art() != null) {
            int back = live ? face : FormLook.disabledFace(p);
            t.frame().plate(d, p, left, top, side, side, back, 0);
            int pad = side >= 20 ? 2 : 1;
            picture.draw(d, t, left + pad, top + pad, side - 2 * pad, side - 2 * pad, true);
        } else {
            Picture shown = picture.fit() == Picture.Fit.CONTAIN ? picture.fit(Picture.Fit.COVER) : picture;
            shown.draw(d, t, left, top, side, side, true);
            if (PictureLook.canRound(t)) {
                Picture.corners(d, left, top, side, side, corner, FormLook.surface(p));
            }
        }
        if (!live) {
            d.fill(left, top, left + side, top + side, Shapes.alpha(FormLook.surface(p), 0xA0));
        }
    }

    /** The dot of a status on the bottom right corner, with a ring of the surface so it stands apart from the head. */
    static void paintStatus(Draw d, FrameStyle frame, Palette p, Status status, boolean live, int right, int bottom, int side) {
        if (status == Status.NONE || side < MIN_FOR_STATUS) {
            return;
        }
        int size = side >= LARGE_FROM ? STATUS_LARGE : STATUS_SMALL;
        int color = !live ? p.disabledText() : switch (status) {
            case ONLINE -> p.good();
            case AWAY -> p.warn();
            case BUSY -> p.bad();
            default -> p.disabledText();
        };
        int left = right - size;
        int top = bottom - size;
        frame.dot(d, p, left - 1, top - 1, size + 2, FormLook.surface(p), 0);
        frame.dot(d, p, left, top, size, FormLook.solid(color), 0);
    }

    private int side() {
        return Math.min(w, h);
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        int side = side();
        if (side <= 0) {
            return;
        }
        int left = x + (w - side) / 2;
        int top = y + (h - side) / 2;
        if (picture != null && picture.state() == AsyncImage.State.READY) {
            paintPicture(d, t, picture, faceOf(t.palette(), name), active, left, top, side);
        } else {
            paint(d, t, initials, faceOf(t.palette(), name), skin, active, left, top, side);
        }
        paintStatus(d, t.frame(), t.palette(), status, active, left + side, top + side, side);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        if (tooltip != null) {
            return tooltip;
        }
        return name.isEmpty() ? null : List.of(name);
    }
}
