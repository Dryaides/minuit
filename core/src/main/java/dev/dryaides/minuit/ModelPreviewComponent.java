package dev.dryaides.minuit;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A card with a voxel model on a turntable, a title and a line under it, as a shop or a gallery shows a product.
 *
 * <pre>{@code
 * add(new ModelPreviewComponent(at(new Box("crate", 10, 10, 84, 104)), crate, "Oak crate")
 *         .subtitle("12 planks")
 *         .badge("New")
 *         .onClick(this::buy));
 * }</pre>
 *
 * The model stands still until the pointer or the focus comes, and then turns; when they leave it goes back to its
 * first view. A still card costs a replay of its rectangles, so a grid of them is cheap. A click, Enter or Space calls
 * {@link #onClick}. A drag of more than 2 units turns the model and presses nothing. The arrows turn it and Home
 * resets it. The wheel is left to what is around, as cards are often in a scrolling list.
 * Under {@link BaseComponent#reducedMotion} it never turns by itself.
 *
 * <p>The text goes first in a short box: under 54 high the card is the picture alone, and under 64 it has no second line.
 * A title that is cut shows whole in a tooltip. For cards inside a {@link GridComponent}, see {@link #cells}.
 */
@Incubating
public class ModelPreviewComponent extends BaseComponent {
    /** When the model turns by itself. */
    public enum Spin { OFF, ON_HOVER, ALWAYS }

    private static final int PAD = 4;
    private static final int LINE_H = 10;
    private static final int MIN_FOR_TITLE = 54;
    private static final int MIN_FOR_SUBTITLE = 64;
    private static final int MIN_W_FOR_TEXT = 24;
    private static final int MIN_SIDE = 8;
    private static final int DRAG_SLOP = 2;
    private static final float DEFAULT_YAW = 35F;
    private static final float DEFAULT_PITCH = 25F;
    private static final float SPIN_SPEED = 50F;
    private static final int HOVER_RING = 0x60;
    private static final int HELD_WASH = 0x20;
    private static final int BADGE_PAD = 3;
    private static final int BADGE_H = 11;
    private static final int MIN_W_FOR_BADGE = 40;

    private final Scene3DComponent scene;
    private String title;
    private String subtitle = "";
    private String badge = "";
    private String emptyText = "";
    private Runnable onClick;
    private Spin spin = Spin.ON_HOVER;
    private boolean selected;
    private boolean wasSpinning;
    private boolean framed = true;
    /** The frame's insets as last drawn: a framed card keeps its caption off a thick rim. */
    private Insets frameIn = new Insets(3, 3, 3, 3);

    private boolean pressed;
    private boolean turning;
    private boolean moved;
    private double pressX;
    private double pressY;

    /**
     * @param model what stands on the card; null or an empty one shows {@link #emptyText}
     * @param title the first line; null or empty for none
     */
    public ModelPreviewComponent(Box box, VoxelModel model, String title) {
        super(box);
        this.title = title == null ? "" : title;
        scene = new Scene3DComponent(box.named(box.name() + "-scene"))
                .floor(Scene3DComponent.Floor.DISC)
                .view(DEFAULT_YAW, DEFAULT_PITCH)
                .model(model);
        scene.autoRotate(false, SPIN_SPEED);
    }

    // ---- setup ----

    public ModelPreviewComponent subtitle(String subtitle) {
        this.subtitle = subtitle == null ? "" : subtitle;
        return this;
    }

    public ModelPreviewComponent title(String title) {
        this.title = title == null ? "" : title;
        return this;
    }

    /** A short word at the card's top left, such as a price or "New". Cards under 40 wide have none. */
    public ModelPreviewComponent badge(String badge) {
        this.badge = badge == null ? "" : badge;
        return this;
    }

    public ModelPreviewComponent model(VoxelModel model) {
        scene.model(model);
        return this;
    }

    /** Called on a click, Enter or Space. */
    public ModelPreviewComponent onClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    public ModelPreviewComponent selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    /** {@link Spin#ON_HOVER} by default. */
    public ModelPreviewComponent spin(Spin spin) {
        this.spin = spin == null ? Spin.OFF : spin;
        return this;
    }

    /** The view at rest, in degrees. */
    public ModelPreviewComponent view(float yaw, float pitch) {
        scene.view(yaw, pitch);
        return this;
    }

    /** Parallel projection at the classic isometric angle. */
    public ModelPreviewComponent isometric() {
        scene.isometric();
        return this;
    }

    /** False takes the disc away. On by default. */
    public ModelPreviewComponent turntable(boolean turntable) {
        scene.floor(turntable ? Scene3DComponent.Floor.DISC : Scene3DComponent.Floor.NONE);
        return this;
    }

    /** How many GUI units one picture cell takes. 1 by default. */
    public ModelPreviewComponent pixelSize(int pixelSize) {
        scene.pixelSize(pixelSize);
        return this;
    }

    /**
     * Smooth edges on the model, off by default: see {@link Scene3DComponent#smooth(boolean)}. About two to four
     * times the rectangles of the blocky picture, kept under the scene's rectangle budget.
     */
    public ModelPreviewComponent smooth(boolean smooth) {
        scene.smooth(smooth);
        return this;
    }

    /**
     * True by default: the card draws the theme's own panel. False draws a flat rounded face with a faint edge, for a
     * card that already sits in a panel, where a second thick rim would stack. Selection and hover rings stay.
     */
    public ModelPreviewComponent framed(boolean framed) {
        this.framed = framed;
        return this;
    }

    /** What the picture's place shows with no model. None by default. Pass it through your {@link Words}. */
    public ModelPreviewComponent emptyText(String emptyText) {
        this.emptyText = emptyText == null ? "" : emptyText;
        return this;
    }

    public ModelPreviewComponent active(boolean active) {
        this.active = active;
        scene.active(active);
        if (!active) {
            pressed = false;
            turning = false;
        }
        return this;
    }

    @Override
    public ModelPreviewComponent tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- state ----

    public boolean selected() {
        return selected;
    }

    /** The view inside the card, for its camera and what its pictures took. */
    Scene3DComponent scene() {
        return scene;
    }

    /** True while the model turns by itself. */
    boolean spinning() {
        return scene.rotating();
    }

    /** Where the model is drawn. */
    Box pictureBox() {
        return new Box("picture", x + 1, y + 1, Math.max(0, w - 2), Math.max(0, h - 2 - footer()));
    }

    private boolean showsText() {
        return w >= MIN_W_FOR_TEXT && h >= MIN_FOR_TITLE;
    }

    private boolean showsSubtitle() {
        return showsText() && h >= MIN_FOR_SUBTITLE && !subtitle.isEmpty();
    }

    private int footer() {
        if (!showsText() || (title.isEmpty() && subtitle.isEmpty())) {
            return 0;
        }
        int lines = (title.isEmpty() ? 0 : 1) + (showsSubtitle() ? 1 : 0);
        return lines == 0 ? 0 : lines * LINE_H + 2 + rim();
    }

    /** Rows of a framed card's bottom rim past the 2 every family leaves. */
    private int rim() {
        return framed ? Math.max(0, frameIn.bottom() - 2) : 0;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        int corner = frame.corner();
        frameIn = frame.panelInsets();
        if (framed) {
            frame.panel(d, p, x, y, w, h);
        } else {
            frame.well(d, p, x, y, w, h, false);
        }
        if (w < MIN_SIDE || h < MIN_SIDE) {
            return;
        }
        boolean live = hovered || focused || turning;
        boolean wantSpin = active && !reducedMotion() && !turning && !pressed
                && (spin == Spin.ALWAYS || (spin == Spin.ON_HOVER && live));
        if (wasSpinning && !wantSpin && !live && active && !reducedMotion()) {
            scene.reset();
        }
        wasSpinning = wantSpin;
        scene.spinning(wantSpin);
        scene.active(active);
        scene.step();

        Box picture = pictureBox();
        int back = section(p);
        if (scene.isEmpty()) {
            RowLook.drawEmpty(d, p, emptyText, active, picture.x(), picture.y(), picture.w(), picture.h());
        } else {
            scene.drawScene(d, t, picture.x(), picture.y(), picture.w(), picture.h(), corner - 1);
        }
        if (pressed && !turning && active) {
            frame.plate(d, p, x, y, w, h, Shapes.alpha(p.text(), HELD_WASH), 0);
        }
        drawText(d, p, back);
        drawBadge(d, t, back);
        if (selected) {
            int ring = active ? p.primary() : p.disabledText();
            selectionRing(d, t, x, y, w, h, ring | 0xFF000000);
            drawCheck(d, t, ring | 0xFF000000);
        } else if (active && hovered) {
            frame.plate(d, p, x, y, w, h, 0, Shapes.alpha(p.text(), HOVER_RING));
        }
    }

    /** Two plate edges, one inside the other: a selection reads at a glance on any model. */
    private static void selectionRing(Draw d, Theme t, int x, int y, int w, int h, int color) {
        t.frame().plate(d, t.palette(), x, y, w, h, 0, color);
        t.frame().plate(d, t.palette(), x + 1, y + 1, w - 2, h - 2, 0, color);
    }

    /** The card's face, solid. */
    private static int section(Palette p) {
        return p.surface() | 0xFF000000;
    }

    private void drawText(Draw d, Palette p, int back) {
        int lines = footer();
        if (lines == 0) {
            return;
        }
        int pad = framed ? Math.max(PAD, Math.max(frameIn.left(), frameIn.right()) + 2) : PAD;
        int left = x + pad;
        int room = Math.max(0, w - 2 * pad);
        int line = y + h - lines;
        int ink = active ? Ink.on(p, back) : p.disabledText();
        if (!title.isEmpty()) {
            d.textFit(title, left, line, room, ink);
            line += LINE_H;
        }
        if (showsSubtitle()) {
            int soft = active ? Ink.soft(p, back) : p.disabledText();
            d.textFit(subtitle, left, line, room, soft);
        }
    }

    private void drawBadge(Draw d, Theme t, int back) {
        Palette p = t.palette();
        if (badge.isEmpty() || w < MIN_W_FOR_BADGE || h < MIN_FOR_TITLE) {
            return;
        }
        int room = w - 2 * PAD;
        int bw = Math.min(room, d.width(badge) + 2 * BADGE_PAD);
        int bx = x + PAD;
        int by = y + PAD;
        int face = active ? p.accent() | 0xFF000000 : Shapes.mix(back, p.disabledText() | 0xFF000000, 0.3F);
        d.nextLayer();
        t.frame().plate(d, p, bx, by, bw, BADGE_H, face, 0);
        d.textFit(badge, bx + BADGE_PAD, by + 2, bw - 2 * BADGE_PAD, active ? Ink.on(p, face) : p.disabledText());
    }

    private void drawCheck(Draw d, Theme t, int ring) {
        IconArt icon = t.icon(Icon.CHECK);
        if (w < MIN_W_FOR_BADGE || h < MIN_FOR_TITLE) {
            return;
        }
        int size = Math.max(icon.width(), icon.height()) + 2;
        int cx = x + w - PAD - size;
        int cy = y + PAD;
        t.frame().plate(d, t.palette(), cx, cy, size, size, ring, 0);
        int ink = active ? Ink.on(t.palette(), ring) : t.palette().surface() | 0xFF000000;
        d.icon(icon, cx + (size - icon.width()) / 2, cy + (size - icon.height()) / 2, ink);
    }

    // ---- input ----

    private boolean accepts() {
        return visible && active && settled();
    }

    @Override
    protected boolean takesFocus() {
        return accepts();
    }

    @Override
    protected boolean navigable() {
        return accepts();
    }

    @Override
    protected boolean activate() {
        if (!accepts() || onClick == null) {
            return false;
        }
        BaseComponent.click();
        onClick.run();
        return true;
    }

    private boolean inBox(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!accepts() || button != 0 || !inBox(mx, my)) {
            return false;
        }
        pressed = true;
        turning = false;
        moved = false;
        pressX = mx;
        pressY = my;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!pressed) {
            return false;
        }
        if (!moved && (Math.abs(mx - pressX) > DRAG_SLOP || Math.abs(my - pressY) > DRAG_SLOP)) {
            moved = true;
            if (!scene.isEmpty()) {
                turning = true;
                scene.startTurn(pressX, pressY);
            }
        }
        if (turning) {
            scene.turnBy(mx, my);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = pressed;
        boolean clicked = pressed && !moved && inBox(mx, my);
        pressed = false;
        turning = false;
        scene.endTurn();
        if (clicked) {
            activate();
        }
        return was;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!accepts()) {
            return false;
        }
        if (key == InputKey.ENTER || key == InputKey.SPACE) {
            return activate();
        }
        return !scene.isEmpty() && scene.turnKey(key, shift);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!visible || !inBox(mx, my)) {
            return null;
        }
        if (tooltip == null && showsText() && !title.isEmpty() && TextMeasure.width(title) > w - 2 * PAD) {
            return List.of(title);
        }
        return tooltip;
    }

    // ---- cards in a grid ----

    /**
     * Draws models in a {@link GridComponent}'s cells, for many models where each widget would be too much. Each model keeps
     * one small picture. The hovered one turns, unless {@link BaseComponent#reducedMotion} is on. Each cell draws its
     * own well, hover and selection, with a name along its bottom when it is tall enough.
     *
     * @param model the model of an item; null for none
     * @param name its name; null for none
     */
    public static <T> GridComponent.CellRenderer<T> cells(Function<T, VoxelModel> model, Function<T, String> name) {
        return cells(model, name, false);
    }

    /** As {@link #cells(Function, Function)}, with smooth edges when asked: see {@link #smooth(boolean)}. */
    public static <T> GridComponent.CellRenderer<T> cells(Function<T, VoxelModel> model, Function<T, String> name, boolean smooth) {
        Map<VoxelModel, Scene3DComponent> scenes = new IdentityHashMap<>();
        return (d, theme, item, cell, hovered, isSelected) -> {
            Palette p = theme.palette();
            int corner = theme.frame().corner();
            theme.frame().well(d, p, cell.x(), cell.y(), cell.w(), cell.h(), false);
            VoxelModel m = model.apply(item);
            String label = name == null ? null : name.apply(item);
            boolean named = label != null && !label.isEmpty() && cell.h() >= MIN_FOR_TITLE;
            int pictureH = cell.h() - (named ? LINE_H + 2 : 0);
            if (m != null) {
                Scene3DComponent scene = scenes.computeIfAbsent(m, k -> {
                    Scene3DComponent made = new Scene3DComponent(new Box("cell", 0, 0, 0, 0))
                            .floor(Scene3DComponent.Floor.DISC)
                            .view(DEFAULT_YAW, DEFAULT_PITCH)
                            .model(k);
                    made.autoRotate(false, SPIN_SPEED);
                    made.smooth(smooth);
                    return made;
                });
                boolean wantSpin = hovered && !reducedMotion();
                if (!wantSpin && scene.rotating()) {
                    scene.reset();
                }
                scene.spinning(wantSpin);
                scene.step();
                scene.drawScene(d, theme, cell.x() + 1, cell.y() + 1, cell.w() - 2, pictureH - 2, corner - 1);
            }
            if (named) {
                d.textFit(label, cell.x() + PAD, cell.bottom() - LINE_H - 1, Math.max(0, cell.w() - 2 * PAD), RowLook.ink(p, p.well(), false, true));
            }
            if (isSelected) {
                selectionRing(d, theme, cell.x(), cell.y(), cell.w(), cell.h(), p.primary() | 0xFF000000);
            } else if (hovered) {
                theme.frame().plate(d, p, cell.x(), cell.y(), cell.w(), cell.h(), 0, Shapes.alpha(RowLook.ink(p, p.well(), false, true), HOVER_RING));
            }
        };
    }
}
