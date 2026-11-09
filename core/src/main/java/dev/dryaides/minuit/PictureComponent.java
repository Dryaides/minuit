package dev.dryaides.minuit;

import java.util.List;

/**
 * A {@link Picture} in a box: a screenshot, a map's preview, an item shown large. It keeps the aspect, has
 * the theme's frame, shows the loading and failed looks, and may have a caption under it, a click, and zoom.
 *
 * <pre>{@code
 * add(new PictureComponent(at(new Box("shot", 10, 10, 160, 100)), Picture.of(shot).caption("Spawn, day 1"))
 *         .zoomable(true));
 * add(new PictureComponent(at(new Box("sword", 180, 10, 48, 48)), Picture.item("minecraft:diamond_sword")));
 * }</pre>
 *
 * <p>With {@link #zoomable(boolean)}: the wheel zooms where the pointer is, a drag moves the zoomed picture,
 * a double click zooms to one picture pixel per screen pixel (or twice the fit) and back, and with the
 * focus the arrows move, + and - zoom and 0 resets. The zoom eases, except under reduced motion. With
 * {@link #onClick(Runnable)} a click, Enter or Space runs it; a drag never counts as a click.
 * The tooltip is the picture's {@link Picture#alt(String) alt} words, or the caption when it is cut.
 */
public class PictureComponent extends BaseComponent {
    private static final int CAPTION_GAP = 3;
    private static final int LINE = SurfaceLook.LINE;
    private static final int FONT_H = SurfaceLook.FONT_H;
    private static final float WHEEL_STEP = 1.25F;
    private static final float KEY_STEP = 1.5F;
    private static final float PAN_STEP = 0.1F;
    private static final int ZOOM_MS = 140;
    private static final int DOUBLE_MS = 350;
    private static final int DRAG_START = 3;
    private static final int HOVER_WASH = 0x18;
    private static final int HELD_WASH = 0x30;

    private Picture picture;
    private boolean captionShown = true;
    private boolean framed = true;
    private boolean zoomable;
    private float maxZoom;
    private int outside;
    private Runnable onClick;

    private final PictureLook.View view = new PictureLook.View();
    private final SurfaceLook.Tween zoomNow = new SurfaceLook.Tween(ZOOM_MS, 1F);
    private final SurfaceLook.Tween cxNow = new SurfaceLook.Tween(ZOOM_MS, 0.5F);
    private final SurfaceLook.Tween cyNow = new SurfaceLook.Tween(ZOOM_MS, 0.5F);
    private int gs = 2;
    private boolean holding;
    private boolean dragged;
    private double pressX;
    private double pressY;
    private long lastClick = Long.MIN_VALUE / 2;
    private boolean captionCut;

    public PictureComponent(Box box, Picture picture) {
        super(box);
        this.picture = picture;
    }

    // ---- setup ----

    /** Changes the picture, and resets the zoom. */
    public PictureComponent picture(Picture picture) {
        if (picture != this.picture) {
            this.picture = picture;
            resetZoom(false);
        }
        return this;
    }

    public Picture picture() {
        return picture;
    }

    /** False to keep the picture's caption off. On by default: a caption takes a line under the picture. */
    public PictureComponent caption(boolean shown) {
        this.captionShown = shown;
        return this;
    }

    /** False for no frame: no edge, no rounded corners. On by default. */
    public PictureComponent framed(boolean framed) {
        this.framed = framed;
        return this;
    }

    /** The colour around the box, painted over the corners to round them. The panel's surface by default; 0 for square corners. */
    public PictureComponent outside(int argb) {
        this.outside = argb;
        return this;
    }

    public PictureComponent zoomable(boolean zoomable) {
        this.zoomable = zoomable;
        if (!zoomable) {
            resetZoom(false);
        }
        return this;
    }

    /** The largest zoom, over the fit. 0, the default, allows up to 4 screen pixels per picture pixel, and at least 2. */
    public PictureComponent maxZoom(float maxZoom) {
        this.maxZoom = Math.max(0F, maxZoom);
        return this;
    }

    public PictureComponent onClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    public PictureComponent active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public PictureComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- state ----

    /** The zoom it is going to, over the fit: 1 is not zoomed. */
    public float zoom() {
        return zoomNow.target();
    }

    /** The point of the picture at the middle of the box, from 0 to 1 across. */
    public float centerX() {
        return cxNow.target();
    }

    public float centerY() {
        return cyNow.target();
    }

    public boolean zoomed() {
        return zoom() > 1.001F;
    }

    /** Where the picture is drawn: the box less the caption's line. */
    Box pictureBox() {
        return new Box("picture", x, y, w, Math.max(0, h - captionH()));
    }

    /** Zooms to this much over the fit, with this point of the picture in the middle, kept inside the box. */
    public void zoomTo(float zoom, float centerX, float centerY) {
        float z = Math.max(1F, Math.min(maxZoomNow(), zoom));
        go(z, keepIn(centerX, z, true), keepIn(centerY, z, false), true);
    }

    /** Back to the fit, eased unless reduced motion. */
    public void resetZoom() {
        resetZoom(true);
    }

    private void resetZoom(boolean ease) {
        go(1F, 0.5F, 0.5F, ease);
    }

    private void go(float z, float cx, float cy, boolean ease) {
        if (ease && !reducedMotion) {
            zoomNow.go(z);
            cxNow.go(cx);
            cyNow.go(cy);
        } else {
            zoomNow.snap(z);
            cxNow.snap(cx);
            cyNow.snap(cy);
        }
    }

    // ---- layout ----

    private int captionH() {
        return captionShown && picture != null && !picture.caption().isEmpty() && h >= 2 * LINE + CAPTION_GAP ? CAPTION_GAP + FONT_H : 0;
    }

    private float fitScale() {
        Box b = pictureBox();
        float[] s = picture == null ? null : PictureLook.fitScale(picture, b.w() * gs, b.h() * gs, gs);
        return s == null ? 1F : Math.min(s[0], s[1]);
    }

    private float maxZoomNow() {
        if (maxZoom > 0F) {
            return Math.max(1F, maxZoom);
        }
        return Math.max(2F, 4F / fitScale());
    }

    /** Drawn width or height in device pixels at this zoom. */
    private float drawn(float z, boolean across) {
        if (picture == null) {
            return 0F;
        }
        Box b = pictureBox();
        float[] s = PictureLook.fitScale(picture, b.w() * gs, b.h() * gs, gs);
        if (s == null) {
            return 0F;
        }
        return (across ? picture.sourceWidth() * s[0] : picture.sourceHeight() * s[1]) * z;
    }

    private float keepIn(float c, float z, boolean across) {
        Box b = pictureBox();
        float size = drawn(z, across);
        float room = (across ? b.w() : b.h()) * gs;
        if (size <= room || size <= 0F) {
            return 0.5F;
        }
        float half = room / 2F / size;
        return Math.max(half, Math.min(1F - half, c));
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        gs = Math.max(1, d.guiScale());
        Box b = pictureBox();
        if (picture == null || b.w() <= 0 || b.h() <= 0) {
            return;
        }
        Palette p = t.palette();
        float z = zoomNow.value();
        PictureLook.View v = z > 1.001F || zoomNow.moving() ? view.set(z, cxNow.value(), cyNow.value()) : null;
        PictureLook.paint(d, t, picture, b.x(), b.y(), b.w(), b.h(), v, active, 1F);
        boolean over = active && isOver(mx, my) && my < b.bottom();
        if (onClick != null && over) {
            int wash = holding && !dragged ? HELD_WASH : HOVER_WASH;
            d.fill(b.x(), b.y(), b.right(), b.bottom(), Shapes.alpha(p.text(), wash));
        }
        if (framed) {
            int around = outside != 0 ? outside : p.surface();
            PictureLook.frame(d, t, b.x(), b.y(), b.w(), b.h(), around);
        }
        if (zoomed() && b.w() >= 40 && b.h() >= 20) {
            drawZoomBadge(d, t.frame(), p, b);
        }
        drawCaption(d, p);
    }

    private void drawZoomBadge(Draw d, FrameStyle frame, Palette p, Box b) {
        float z = zoom();
        String label = (z < 10F ? String.format(java.util.Locale.ROOT, "%.1f", z) : String.valueOf(Math.round(z))) + "x";
        int tw = d.width(label);
        int bx = b.right() - tw - 7;
        int by = b.y() + 3;
        d.nextLayer();
        frame.plate(d, p, bx, by, tw + 4, FONT_H + 3, Shapes.alpha(p.shadow(), 0xB0), 0);
        d.text(label, bx + 2, by + 2, 0xFFFFFFFF);
    }

    private void drawCaption(Draw d, Palette p) {
        int ch = captionH();
        captionCut = false;
        if (ch == 0) {
            return;
        }
        String text = picture.caption();
        int under = p.surface() | 0xFF000000;
        int ink = active ? Ink.soft(p, under) : p.disabledText();
        captionCut = d.width(text) > w;
        String line = d.ellipsize(text, w);
        d.text(line, x + (w - d.width(line) + 1) / 2, y + h - FONT_H, ink);
    }

    @Override
    protected Box focusBox() {
        return pictureBox();
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        if (tooltip != null) {
            return tooltip;
        }
        if (picture == null) {
            return null;
        }
        if (my >= y + h - captionH() && captionCut) {
            return List.of(picture.caption());
        }
        return picture.alt().isEmpty() || zoomed() || holding ? null : List.of(picture.alt());
    }

    // ---- input ----

    @Override
    protected boolean navigable() {
        return visible && active && (onClick != null || zoomable);
    }

    @Override
    protected boolean activate() {
        if (!visible || !active || onClick == null) {
            return false;
        }
        BaseComponent.click();
        onClick.run();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !active || button != 0 || !pictureBox().contains(mx, my)) {
            return false;
        }
        if (!zoomable && onClick == null) {
            return false;
        }
        holding = true;
        dragged = false;
        pressX = mx;
        pressY = my;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!holding) {
            return false;
        }
        if (!dragged && Math.abs(mx - pressX) + Math.abs(my - pressY) >= DRAG_START) {
            dragged = true;
        }
        if (dragged && zoomable && zoomed()) {
            float z = zoom();
            float ww = drawn(z, true);
            float hh = drawn(z, false);
            float cx = ww <= 0F ? 0.5F : cxNow.target() - (float) (dx * gs / ww);
            float cy = hh <= 0F ? 0.5F : cyNow.target() - (float) (dy * gs / hh);
            go(z, keepIn(cx, z, true), keepIn(cy, z, false), false);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!holding) {
            return false;
        }
        holding = false;
        if (dragged || !visible || !active || !pictureBox().contains(mx, my)) {
            return true;
        }
        long now = clock.getAsLong();
        // Without zoom a double click means nothing, so each click of a fast pair counts.
        boolean twice = zoomable && now - lastClick <= DOUBLE_MS;
        lastClick = twice ? Long.MIN_VALUE / 2 : now;
        if (zoomable && twice) {
            if (zoomed()) {
                resetZoom();
            } else {
                float natural = picture != null && picture.kind() == Picture.Kind.IMAGE ? 1F / fitScale() : 2F;
                zoomAt(mx, my, natural > 1.2F ? natural : 2F);
            }
        } else if (onClick != null && !twice) {
            BaseComponent.click();
            onClick.run();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || !active || !zoomable || !pictureBox().contains(mx, my) || picture == null
                || picture.state() != AsyncImage.State.READY) {
            return false;
        }
        float z = (float) (zoom() * Math.pow(WHEEL_STEP, amount));
        zoomAt(mx, my, z);
        return true;
    }

    /** Zooms keeping the point under the pointer where it is. */
    private void zoomAt(double mx, double my, float target) {
        float from = zoom();
        float z = Math.max(1F, Math.min(maxZoomNow(), target));
        Box b = pictureBox();
        float ox = (float) ((mx - (b.x() + b.w() / 2F)) * gs);
        float oy = (float) ((my - (b.y() + b.h() / 2F)) * gs);
        float w0 = drawn(from, true);
        float h0 = drawn(from, false);
        float w1 = drawn(z, true);
        float h1 = drawn(z, false);
        // The point of the picture under the pointer now, read where it is drawn now (kept inside the box).
        float cx0 = keepIn(cxNow.target(), from, true);
        float cy0 = keepIn(cyNow.target(), from, false);
        float u = w0 <= 0F ? 0.5F : cx0 + ox / w0;
        float v = h0 <= 0F ? 0.5F : cy0 + oy / h0;
        float cx = w1 <= 0F ? 0.5F : u - ox / w1;
        float cy = h1 <= 0F ? 0.5F : v - oy / h1;
        go(z, keepIn(cx, z, true), keepIn(cy, z, false), true);
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!visible || !active || !focused) {
            return false;
        }
        if (zoomable && zoomed()) {
            float z = zoom();
            float stepX = drawn(z, true) <= 0F ? 0F : pictureBox().w() * gs * PAN_STEP / drawn(z, true);
            float stepY = drawn(z, false) <= 0F ? 0F : pictureBox().h() * gs * PAN_STEP / drawn(z, false);
            switch (key) {
                case LEFT -> { pan(-stepX, 0F); return true; }
                case RIGHT -> { pan(stepX, 0F); return true; }
                case UP -> { pan(0F, -stepY); return true; }
                case DOWN -> { pan(0F, stepY); return true; }
                case HOME -> { resetZoom(); return true; }
                default -> { }
            }
        }
        if (key == InputKey.ENTER || key == InputKey.SPACE) {
            return activate();
        }
        return false;
    }

    private void pan(float dx, float dy) {
        float z = zoom();
        go(z, keepIn(cxNow.target() + dx, z, true), keepIn(cyNow.target() + dy, z, false), true);
    }

    @Override
    public boolean charTyped(int codePoint) {
        if (!visible || !active || !focused || !zoomable) {
            return false;
        }
        Box b = pictureBox();
        switch (codePoint) {
            case '+', '=' -> zoomAt(b.x() + b.w() / 2D, b.y() + b.h() / 2D, zoom() * KEY_STEP);
            case '-' -> zoomAt(b.x() + b.w() / 2D, b.y() + b.h() / 2D, zoom() / KEY_STEP);
            case '0' -> resetZoom();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** For the viewer: input while it is not in a panel. */
    void place(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }
}
