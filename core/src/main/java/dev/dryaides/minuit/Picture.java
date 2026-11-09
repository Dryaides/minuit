package dev.dryaides.minuit;

import java.util.Objects;
import java.util.function.Function;

/**
 * Something to show in a box: a loaded image ({@link AsyncImage}), a texture of a mod's assets, an item
 * ({@link ItemView}), a sprite ({@link SpriteIcon}) or any other {@link IconArt}, with how it fills the box.
 * Every component that takes a picture takes all of these, items included.
 *
 * <pre>{@code
 * Picture shot = Picture.of(screenshot).fit(Picture.Fit.COVER).focus(0.5F, 0.3F).caption("Base, day 12");
 * Picture sword = Picture.item("minecraft:diamond_sword");                 // crisp, whole-pixel scaling
 * Picture logo = Picture.texture("mymod:textures/gui/logo.png", 64, 32);  // a PNG of the mod, size known
 * shot.draw(d, theme, 10, 10, 160, 90);                                   // in any renderer of your own
 * }</pre>
 *
 * <h2>Fit</h2>
 * {@link Fit#CONTAIN} shows all of it, with bands of the letterbox colour; {@link Fit#COVER} fills the box
 * and crops around the {@link #focus(float, float) focal point}; {@link Fit#STRETCH} fills and distorts;
 * {@link Fit#SCALE_DOWN} is contain, but never larger than the natural size; {@link Fit#NATURAL} is the
 * natural size, centred and cut; {@link Fit#INTEGER} scales by whole screen pixels only, for pixel art.
 * The natural size of a loaded image is one image pixel per <b>screen</b> pixel; of a texture, one texel
 * per GUI unit, as the game draws its own; of an item or sprite, its usual size (16 for an item).
 * Images and textures default to {@code CONTAIN}, items, sprites and icons to {@code INTEGER}.
 *
 * <h2>Quality</h2>
 * The game samples these textures to the nearest pixel, without mipmaps. A picture drawn much smaller than
 * its file then drops most of its pixels: thin lines vanish, text sparkles. The picture is placed on whole
 * screen pixels, not GUI units, and when it is shrunk more than about 1.5 times it is drawn 4 or 9 times
 * with small offsets and averaged ({@link #smooth(boolean)}, on by default for images). That helps, but the
 * best result is a file already the size it is shown at: see {@link PictureBytes#shrink(byte[], int, int)}.
 * Rounded corners are painted over with the colour around the box, as a texture cannot be clipped round.
 *
 * <p>Immutable: each setter returns a new picture.
 */
public final class Picture implements IconArt {
    /** What the picture is made of. */
    public enum Kind { IMAGE, TEXTURE, ITEM, SPRITE, ICON }

    /** How the picture fills its box. */
    public enum Fit { CONTAIN, COVER, STRETCH, SCALE_DOWN, NATURAL, INTEGER }

    /** The letterbox colour of the theme: its well. */
    public static final int THEME = 1;

    private final Kind kind;
    private final AsyncImage image;
    private final String texture;
    private final int textureW;
    private final int textureH;
    private final IconArt art;
    private Fit fit;
    private float focusX = 0.5F;
    private float focusY = 0.5F;
    private String caption = "";
    private String alt = "";
    private int sizeW;
    private int sizeH;
    private int letterbox = THEME;
    private boolean smooth;
    private Picture large;

    private Picture(Kind kind, AsyncImage image, String texture, int textureW, int textureH, IconArt art) {
        this.kind = kind;
        this.image = image;
        this.texture = texture;
        this.textureW = textureW;
        this.textureH = textureH;
        this.art = art;
        this.fit = kind == Kind.IMAGE || kind == Kind.TEXTURE ? Fit.CONTAIN : Fit.INTEGER;
        this.smooth = kind == Kind.IMAGE;
    }

    private Picture copy() {
        Picture p = new Picture(kind, image, texture, textureW, textureH, art);
        p.fit = fit;
        p.focusX = focusX;
        p.focusY = focusY;
        p.caption = caption;
        p.alt = alt;
        p.sizeW = sizeW;
        p.sizeH = sizeH;
        p.letterbox = letterbox;
        p.smooth = smooth;
        p.large = large;
        return p;
    }

    // ---- making one ----

    /** A loaded image: a screenshot, a downloaded picture. Its loading and failed states show. */
    public static Picture of(AsyncImage image) {
        return new Picture(Kind.IMAGE, Objects.requireNonNull(image, "image"), null, 0, 0, null);
    }

    /** An item, drawn by the game. */
    public static Picture of(ItemView item) {
        return new Picture(Kind.ITEM, null, null, 0, 0, Objects.requireNonNull(item, "item"));
    }

    /** A sprite of an atlas. */
    public static Picture of(SpriteIcon sprite) {
        return new Picture(Kind.SPRITE, null, null, 0, 0, Objects.requireNonNull(sprite, "sprite"));
    }

    /** Any icon: an item or a sprite keep their kind, a theme's icon is tinted with the text colour. A picture is returned as is. */
    public static Picture of(IconArt art) {
        Objects.requireNonNull(art, "art");
        if (art instanceof Picture picture) {
            return picture;
        }
        if (art instanceof ItemView item) {
            return of(item);
        }
        if (art instanceof SpriteIcon sprite) {
            return of(sprite);
        }
        return new Picture(Kind.ICON, null, null, 0, 0, art);
    }

    /** An item by id, as {@code "minecraft:apple"}. */
    public static Picture item(String id) {
        return of(ItemView.of(id));
    }

    /** A PNG of a mod's assets, as {@code "mymod:textures/gui/map.png"}, and its size in pixels. */
    public static Picture texture(String id, int width, int height) {
        return new Picture(Kind.TEXTURE, null, Objects.requireNonNull(id, "id"), Math.max(1, width), Math.max(1, height), null);
    }

    // ---- settings ----

    public Picture fit(Fit fit) {
        Picture p = copy();
        p.fit = fit == null ? Fit.CONTAIN : fit;
        return p;
    }

    /** {@link Fit#INTEGER}, without smoothing: whole pixels for pixel art. */
    public Picture pixelArt() {
        Picture p = fit(Fit.INTEGER);
        p.smooth = false;
        return p;
    }

    /**
     * The point to keep in view when {@link Fit#COVER} or {@link Fit#NATURAL} crops: 0 is the left or top
     * edge, 1 the right or bottom one. The middle by default.
     */
    public Picture focus(float x, float y) {
        Picture p = copy();
        p.focusX = clamp01(x);
        p.focusY = clamp01(y);
        return p;
    }

    /** Words shown with the picture by the components that have room: a gallery, a viewer, a card. */
    public Picture caption(String caption) {
        Picture p = copy();
        p.caption = caption == null ? "" : caption;
        return p;
    }

    /** What the picture shows, in words: the tooltip, and the text of the failed state. */
    public Picture alt(String alt) {
        Picture p = copy();
        p.alt = alt == null ? "" : alt;
        return p;
    }

    /** The size it takes where an icon is expected ({@link IconArt}), in GUI units. */
    public Picture size(int width, int height) {
        Picture p = copy();
        p.sizeW = Math.max(0, width);
        p.sizeH = Math.max(0, height);
        return p;
    }

    /** The colour of the bands around a contained picture: {@link #THEME} (the default) or any ARGB; 0 for none. */
    public Picture letterbox(int argb) {
        Picture p = copy();
        p.letterbox = argb;
        return p;
    }

    /** Averages several samples when the picture is shrunk. On for loaded images, off for the others. */
    public Picture smooth(boolean smooth) {
        Picture p = copy();
        p.smooth = smooth;
        return p;
    }

    /** A bigger file of the same picture, for where it is shown large: the full-screen viewer uses it. */
    public Picture large(Picture large) {
        Picture p = copy();
        p.large = large;
        return p;
    }

    // ---- reading ----

    public Kind kind() { return kind; }
    public Fit fit() { return fit; }
    public float focusX() { return focusX; }
    public float focusY() { return focusY; }
    public String caption() { return caption; }
    public String alt() { return alt; }
    public boolean smooth() { return smooth; }
    public int letterbox() { return letterbox; }
    /** The bigger file, or null. */
    public Picture large() { return large; }
    /** The loaded image, or null for the other kinds. */
    public AsyncImage image() { return image; }
    /** The item, or null for the other kinds. */
    public ItemView item() { return art instanceof ItemView item ? item : null; }
    /** The item, sprite or icon, or null for images and textures. */
    public IconArt art() { return art; }

    /** This picture, or its {@link #large(Picture) large} file once that one is loaded. */
    public Picture largest() {
        return large != null && large.state() == AsyncImage.State.READY ? large : this;
    }

    /** Pending or failed for an image that is not there; ready for every other kind. */
    public AsyncImage.State state() {
        return kind == Kind.IMAGE ? image.state() : AsyncImage.State.READY;
    }

    /** The texture to draw, for an image or a texture; null otherwise or before it is loaded. */
    public String textureId() {
        if (kind == Kind.TEXTURE) {
            return texture;
        }
        return kind == Kind.IMAGE && image.state() == AsyncImage.State.READY ? image.texture() : null;
    }

    /** Width of the source: pixels of an image or texture, GUI units of an item or icon. 0 before an image loads. */
    public int sourceWidth() {
        return switch (kind) {
            case IMAGE -> image.state() == AsyncImage.State.READY ? image.width() : 0;
            case TEXTURE -> textureW;
            default -> art.width();
        };
    }

    public int sourceHeight() {
        return switch (kind) {
            case IMAGE -> image.state() == AsyncImage.State.READY ? image.height() : 0;
            case TEXTURE -> textureH;
            default -> art.height();
        };
    }

    // ---- drawing ----

    /** Draws it in a box with the theme: letterbox, picture, and the loading or failed look. */
    public void draw(Draw d, Theme t, int x, int y, int w, int h) {
        PictureLook.paint(d, t, this, x, y, w, h, null, true, 1F);
    }

    public void draw(Draw d, Theme t, Box box) {
        draw(d, t, box.x(), box.y(), box.w(), box.h());
    }

    /** With the disabled look when {@code live} is false. */
    public void draw(Draw d, Theme t, int x, int y, int w, int h, boolean live) {
        PictureLook.paint(d, t, this, x, y, w, h, null, live, 1F);
    }

    /**
     * Paints the colour around the box over its corners, so that the picture looks rounded like the theme's
     * frames. Only right on a plain colour: a see-through surface would show squares.
     */
    public static void corners(Draw d, int x, int y, int w, int h, int radius, int outside) {
        PictureLook.corners(d, x, y, w, h, radius, outside);
    }

    /**
     * Where the picture lands in a box at this GUI scale, in GUI units, rounded outwards. With COVER or
     * NATURAL it may be larger than the box: what lies outside is cut.
     */
    public Box placed(int x, int y, int w, int h, int guiScale) {
        int gs = Math.max(1, guiScale);
        int[] r = PictureLook.place(this, x * gs, y * gs, w * gs, h * gs, gs, null);
        if (r == null) {
            return new Box("placed", x, y, 0, 0);
        }
        int left = Math.floorDiv(r[0], gs);
        int top = Math.floorDiv(r[1], gs);
        int right = -Math.floorDiv(-(r[0] + r[2]), gs);
        int bottom = -Math.floorDiv(-(r[1] + r[3]), gs);
        return new Box("placed", left, top, right - left, bottom - top);
    }

    /**
     * Cells of pictures for a {@link GridComponent}: the picture fills the cell (cover), a caption band
     * comes over its bottom when {@code label} gives words. For a gallery of screenshots.
     */
    public static <T> GridComponent.CellRenderer<T> cells(Function<T, Picture> picture, Function<T, String> label) {
        return GridComponent.glowing((d, t, item, cell, glow, selected) -> PictureLook.cell(d, t, picture.apply(item),
                label == null ? "" : label.apply(item), cell, glow, selected));
    }

    // ---- as an icon ----

    /** The {@link #size(int, int)} given, or the natural size of an item or icon, or 32 by 32. */
    @Override
    public int width() {
        if (sizeW > 0) {
            return sizeW;
        }
        return art != null ? art.width() : 32;
    }

    @Override
    public int height() {
        if (sizeH > 0) {
            return sizeH;
        }
        return art != null ? art.height() : 32;
    }

    /** As an icon, without a theme: the loading and failed looks use the colour given. */
    @Override
    public void draw(Draw d, int x, int y, int color, float alpha) {
        PictureLook.paintBare(d, this, x, y, width(), height(), color, alpha);
    }

    private static float clamp01(float v) {
        return Float.isNaN(v) ? 0.5F : Math.max(0F, Math.min(1F, v));
    }

    @Override
    public String toString() {
        return "Picture[" + kind + " " + switch (kind) {
            case IMAGE -> image.state() + " " + image.texture();
            case TEXTURE -> texture;
            default -> String.valueOf(art);
        } + " " + fit + "]";
    }
}
