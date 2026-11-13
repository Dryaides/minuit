package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A full-screen viewer for pictures: it darkens the screen, shows one picture as large as fits, with a
 * counter, the caption, arrows to the previous and next ones and a close button. For screenshots, maps, a
 * mod's gallery. Pictures with a {@link Picture#large(Picture) large} file show that one once loaded.
 *
 * <pre>{@code
 * LightboxComponent viewer;
 * ...
 * GridComponent<Shot> grid = add(new GridComponent<>(at(new Box("shots", 10, 10, 280, 0)), 4, 40, 4, 4, shots,
 *         Picture.cells(Shot::picture, Shot::name), shot -> viewer.open(this, shots.indexOf(shot))));
 * // Last, so that it is drawn on top and offered every input first, as a dialog.
 * viewer = add(new LightboxComponent(bounds()).pictures(shots.stream().map(Shot::picture).toList()));
 * }</pre>
 *
 * <p>Add it after every other widget, with the screen's box or {@code bounds()}, then {@link #open(PanelComponent, int)}.
 * Escape or the close button closes; a click on the dark area around the picture too, unless
 * {@link #closeOnBackdrop(boolean)} is off. Left and right, the arrows, or the wheel with shift go to the
 * previous and next pictures; home and end to the first and last. The picture zooms as a zoomable
 * {@link PictureComponent} does: wheel, drag, double click, + - 0, and the arrows move it while zoomed.
 * It fits any screen: under 220 units wide the arrows go over the picture and the caption takes one line.
 * It fades in, except under reduced motion.
 */
public final class LightboxComponent extends BaseComponent {
    private static final int BAR_H = 20;
    private static final int SIDE = 26;
    private static final int NARROW = 220;
    private static final int ARROW_W = 20;
    private static final int ARROW_H = 32;
    private static final int CLOSE = 16;
    private static final int LINE = SurfaceLook.LINE;
    private static final int FONT_H = SurfaceLook.FONT_H;
    private static final int OPEN_MS = 160;
    private static final int CHANGE_MS = 120;
    private static final int DIM_ALPHA = 0xE8;
    private static final int INK = 0xFFFFFFFF;
    private static final int INK_SOFT = 0xFFC8C8CC;
    private static final int NONE = 0;
    private static final int PREV = 1;
    private static final int NEXT = 2;
    private static final int CLOSE_PART = 3;
    private static final int PICTURE = 4;

    private List<Picture> pictures = List.of();
    private boolean loop;
    private boolean closeOnBackdrop = true;
    private IntConsumer onChange;
    private Runnable onClose;

    private final PictureComponent viewer;
    private final SurfaceLook.Tween shown = new SurfaceLook.Tween(OPEN_MS, 0F);
    private final SurfaceLook.Tween change = new SurfaceLook.Tween(CHANGE_MS, 1F);
    private Picture before;
    private PanelComponent host;
    private BaseComponent focusBefore;
    private boolean open;
    private int index;
    private int held = NONE;
    private List<String> captionLines = List.of();

    /** @param area what to darken and fill: the screen's box or the panel's {@code bounds()} */
    public LightboxComponent(Box area) {
        super(area);
        visible = false;
        viewer = new PictureComponent(new Box("picture", 0, 0, 0, 0), null).zoomable(true).caption(false).framed(false);
    }

    // ---- setup ----

    public LightboxComponent pictures(List<Picture> pictures) {
        this.pictures = pictures == null ? List.of() : List.copyOf(pictures);
        if (index >= this.pictures.size()) {
            index = Math.max(0, this.pictures.size() - 1);
        }
        before = null;
        viewer.picture(current());
        return this;
    }

    public List<Picture> pictures() {
        return pictures;
    }

    /** From the last picture, next goes to the first. Off by default. */
    public LightboxComponent loop(boolean loop) {
        this.loop = loop;
        return this;
    }

    public LightboxComponent closeOnBackdrop(boolean closeOnBackdrop) {
        this.closeOnBackdrop = closeOnBackdrop;
        return this;
    }

    /** Called with the new index when the picture changes. */
    public LightboxComponent onChange(IntConsumer onChange) {
        this.onChange = onChange;
        return this;
    }

    public LightboxComponent onClose(Runnable onClose) {
        this.onClose = onClose;
        return this;
    }

    // ---- state ----

    public int index() {
        return index;
    }

    public int count() {
        return pictures.size();
    }

    public boolean isOpen() {
        return open;
    }

    /** The picture shown, with its zoom: to read or to zoom from code. */
    public PictureComponent viewer() {
        return viewer;
    }

    /** "3 / 12", as drawn. */
    public String counter() {
        return pictures.isEmpty() ? "" : (index + 1) + " / " + pictures.size();
    }

    /** The place of the picture area, once laid out. */
    Box pictureArea() {
        return viewer.box();
    }

    Box arrowBox(boolean next) {
        Box area = viewer.box();
        int top = area.y() + (area.h() - ARROW_H) / 2;
        if (narrow()) {
            return new Box(next ? "next" : "prev", next ? area.right() - ARROW_W - 2 : area.x() + 2, top, ARROW_W, ARROW_H);
        }
        return new Box(next ? "next" : "prev", next ? x + w - SIDE + (SIDE - ARROW_W) / 2 : x + (SIDE - ARROW_W) / 2, top, ARROW_W, ARROW_H);
    }

    Box closeBox() {
        return new Box("close", x + w - CLOSE - 2, y + (BAR_H - CLOSE) / 2, CLOSE, CLOSE);
    }

    // ---- opening, moving ----

    /**
     * Opens on a picture and takes the panel's focus.
     *
     * @throws IllegalStateException if the panel does not hold it, or if a widget other than a dialog or a
     *         viewer comes after it
     */
    public void open(PanelComponent panel, int at) {
        List<BaseComponent> widgets = panel.widgets();
        int place = widgets.indexOf(this);
        if (place < 0) {
            throw new IllegalStateException("Add the viewer to the panel before opening it");
        }
        for (int i = place + 1; i < widgets.size(); i++) {
            if (!(widgets.get(i) instanceof DialogComponent) && !(widgets.get(i) instanceof LightboxComponent)) {
                throw new IllegalStateException("A viewer must come after every other widget of its panel");
            }
        }
        if (!open) {
            BaseComponent current = panel.focus();
            focusBefore = current == this ? null : current;
        }
        panel.closeOverlay();
        host = panel;
        open = true;
        visible = true;
        held = NONE;
        index = pictures.isEmpty() ? 0 : Math.max(0, Math.min(pictures.size() - 1, at));
        before = null;
        viewer.picture(current());
        viewer.resetZoom();
        change.snap(1F);
        if (reducedMotion()) {
            shown.snap(1F);
        } else {
            shown.snap(0F);
            shown.go(1F);
        }
        panel.focus(this);
    }

    /** Closes and runs {@link #onClose(Runnable)}. */
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        visible = false;
        held = NONE;
        shown.snap(0F);
        if (host != null) {
            host.focus(focusBefore);
        }
        focusBefore = null;
        if (onClose != null) {
            onClose.run();
        }
    }

    /** Shows this picture; out of range, nothing happens unless it loops. */
    public void show(int at) {
        if (pictures.isEmpty()) {
            return;
        }
        int next = loop ? Math.floorMod(at, pictures.size()) : at;
        if (next < 0 || next >= pictures.size() || next == index) {
            return;
        }
        before = current();
        index = next;
        viewer.picture(current());
        if (reducedMotion() || !open) {
            change.snap(1F);
            before = null;
        } else {
            change.snap(0F);
            change.go(1F);
        }
        if (onChange != null) {
            onChange.accept(index);
        }
    }

    public void next() {
        show(index + 1);
    }

    public void previous() {
        show(index - 1);
    }

    private boolean hasPrev() {
        return pictures.size() > 1 && (loop || index > 0);
    }

    private boolean hasNext() {
        return pictures.size() > 1 && (loop || index < pictures.size() - 1);
    }

    private Picture current() {
        if (pictures.isEmpty()) {
            return null;
        }
        return shownForm(pictures.get(index));
    }

    /** The large file if there is one, contained, on no band: the dark around is the letterbox. */
    private static Picture shownForm(Picture p) {
        Picture big = p.largest();
        Picture.Fit fit = big.art() != null ? Picture.Fit.INTEGER : Picture.Fit.CONTAIN;
        return big.fit(fit).letterbox(0).caption(p.caption()).alt(p.alt());
    }

    // ---- layout ----

    private boolean narrow() {
        return w < NARROW;
    }

    private void layout(Draw d) {
        Picture pic = pictures.isEmpty() ? null : pictures.get(index);
        String caption = pic == null ? "" : pic.caption();
        int room = Math.max(10, w - 16);
        List<String> lines = caption.isEmpty() ? List.of() : (d == null ? TextMeasure.wrapAll(caption, room) : d.wrapAll(caption, room));
        int max = narrow() || h < 160 ? 1 : 2;
        if (lines.size() > max) {
            List<String> cut = new ArrayList<>(lines.subList(0, max));
            String last = cut.get(max - 1) + Draw.ELLIPSIS;
            cut.set(max - 1, d == null ? last : d.ellipsize(last, room));
            lines = cut;
        }
        captionLines = lines;
        int bottom = lines.isEmpty() ? 6 : lines.size() * LINE + 6;
        int side = narrow() ? 4 : SIDE;
        viewer.place(x + side, y + BAR_H, Math.max(0, w - 2 * side), Math.max(0, h - BAR_H - bottom));
        // A Picture on the large file changes once that one loads.
        Picture now = current();
        if (now != null && viewer.picture() != null && now.textureId() != null && !now.textureId().equals(viewer.picture().textureId())) {
            viewer.picture(now);
        }
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        if (!open) {
            return;
        }
        layout(d);
        if (host != null && host.focus() != this) {
            host.focus(this);
        }
        Palette p = t.palette();
        float fade = Math.max(0F, Math.min(1F, shown.value()));
        int back = Shapes.luminance(p.shadow() | 0xFF000000) < 0.2D ? p.shadow() & 0xFFFFFF : 0x101012;
        d.nextLayer();
        d.fill(x, y, x + w, y + h, Math.round(DIM_ALPHA * fade) << 24 | back);
        d.nextLayer();
        Box area = viewer.box();
        float step = change.value();
        if (before != null && step < 1F) {
            PictureLook.paint(d, t, before, area.x(), area.y(), area.w(), area.h(), null, true, fade);
        } else {
            before = null;
        }
        if (viewer.picture() != null) {
            float alpha = fade * (before == null ? 1F : step);
            if (alpha >= 1F) {
                viewer.draw(d, t, mx, my, delta);
            } else {
                PictureLook.paint(d, t, viewer.picture(), area.x(), area.y(), area.w(), area.h(), null, true, alpha);
            }
        }
        if (fade < 0.5F) {
            return;
        }
        int part = partAt(mx, my);
        drawBar(d, t, part);
        drawArrows(d, t, part);
        int ty = y + h - captionLines.size() * LINE - 3;
        for (String line : captionLines) {
            d.text(line, x + (w - d.width(line) + 1) / 2, ty, INK);
            ty += LINE;
        }
    }

    private void drawBar(Draw d, Theme t, int part) {
        if (!pictures.isEmpty()) {
            d.text(counter(), x + 6, y + (BAR_H - FONT_H) / 2 + 1, INK_SOFT);
        }
        Box c = closeBox();
        if (part == CLOSE_PART) {
            t.frame().raised(d, t.palette(), c.x(), c.y(), c.w(), c.h(), held == CLOSE_PART ? 0x50FFFFFF : 0x30FFFFFF, Shapes.Relief.FLAT);
        }
        IconArt icon = t.icon(Icon.CLOSE);
        icon.draw(d, c.x() + (c.w() - icon.width()) / 2, c.y() + (c.h() - icon.height()) / 2, INK, 1F);
    }

    private void drawArrows(Draw d, Theme t, int part) {
        for (int i = 0; i < 2; i++) {
            boolean next = i == 1;
            if (next ? !hasNext() : !hasPrev()) {
                continue;
            }
            Box b = arrowBox(next);
            int over = next ? NEXT : PREV;
            // Over the picture when narrow: darker, so that the arrow reads on a bright sky too.
            int face = part == over ? (held == over ? 0x50FFFFFF : 0x30FFFFFF) : narrow() ? 0x80000000 : 0x40000000;
            t.frame().raised(d, t.palette(), b.x(), b.y(), b.w(), b.h(), face, Shapes.Relief.FLAT);
            IconArt icon = t.icon(next ? Icon.NEXT : Icon.PREV);
            icon.draw(d, b.x() + (b.w() - icon.width()) / 2, b.y() + (b.h() - icon.height()) / 2, INK, 1F);
        }
    }

    // ---- input ----

    private int partAt(double mx, double my) {
        if (closeBox().contains(mx, my)) {
            return CLOSE_PART;
        }
        if (hasPrev() && arrowBox(false).contains(mx, my)) {
            return PREV;
        }
        if (hasNext() && arrowBox(true).contains(mx, my)) {
            return NEXT;
        }
        return viewer.box().contains(mx, my) ? PICTURE : NONE;
    }

    /** Over the picture itself, not the dark band around a contained one. */
    private boolean overPicture(double mx, double my) {
        Picture pic = viewer.picture();
        if (pic == null || !viewer.box().contains(mx, my)) {
            return false;
        }
        if (viewer.zoomed()) {
            return true;
        }
        Box area = viewer.box();
        Box placed = pic.placed(area.x(), area.y(), area.w(), area.h(), 1);
        return pic.state() != AsyncImage.State.READY || placed.contains(mx, my);
    }

    @Override
    public boolean isOver(double mx, double my) {
        return open;
    }

    @Override
    protected boolean takesFocus() {
        return open;
    }

    @Override
    protected boolean navigable() {
        return open;
    }

    @Override
    public List<BaseComponent> stops() {
        return open ? List.of(this) : List.of();
    }

    @Override
    protected boolean drawFocus(Draw d, Theme t) {
        return true;
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!open) {
            return null;
        }
        // Empty, not null: the panel would look for a tooltip beneath otherwise.
        if (overPicture(mx, my) && viewer.picture() != null && !viewer.picture().alt().isEmpty() && !viewer.zoomed()) {
            return List.of(viewer.picture().alt());
        }
        return List.of();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!open) {
            return false;
        }
        layout(null);
        int part = partAt(mx, my);
        if (button == 0 && (part == CLOSE_PART || part == PREV || part == NEXT)) {
            held = part;
            return true;
        }
        if (overPicture(mx, my)) {
            viewer.focused = true;
            viewer.mouseClicked(mx, my, button);
        } else if (button == 0) {
            held = closeOnBackdrop ? CLOSE_PART + 10 : NONE;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!open) {
            return false;
        }
        int was = held;
        held = NONE;
        viewer.mouseReleased(mx, my, button);
        int part = partAt(mx, my);
        if (was == CLOSE_PART && part == CLOSE_PART || was == CLOSE_PART + 10 && !overPicture(mx, my) && part != PREV && part != NEXT) {
            BaseComponent.click();
            close();
        } else if (was == PREV && part == PREV) {
            BaseComponent.click();
            previous();
        } else if (was == NEXT && part == NEXT) {
            BaseComponent.click();
            next();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!open) {
            return false;
        }
        viewer.mouseDragged(mx, my, button, dx, dy);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!open) {
            return false;
        }
        if (heldKeys() != null && heldKeys().shift()) {
            if (amount > 0) {
                previous();
            } else if (amount < 0) {
                next();
            }
        } else if (viewer.box().contains(mx, my)) {
            viewer.mouseScrolled(mx, my, amount);
        }
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!open) {
            return false;
        }
        viewer.focused = true;
        switch (key) {
            case ESCAPE -> {
                BaseComponent.click();
                close();
            }
            case LEFT, RIGHT -> {
                if (viewer.zoomed()) {
                    viewer.keyPressed(key, shift);
                } else if (key == InputKey.LEFT) {
                    previous();
                } else {
                    next();
                }
            }
            case UP, DOWN -> viewer.keyPressed(key, shift);
            case HOME -> {
                if (viewer.zoomed()) {
                    viewer.resetZoom();
                } else {
                    show(0);
                }
            }
            case END -> show(pictures.size() - 1);
            default -> { }
        }
        return true;
    }

    @Override
    public boolean charTyped(int codePoint) {
        if (!open) {
            return false;
        }
        viewer.focused = true;
        viewer.charTyped(codePoint);
        return true;
    }

    @Override
    public void tick() {
        if (open && viewer.picture() != null) {
            layout(null);
        }
    }
}
