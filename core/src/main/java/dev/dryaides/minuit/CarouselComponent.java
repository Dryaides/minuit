package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Redraw;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * Slides one at a time, with arrows at the sides, dots beneath, drag, wheel and keys. The arrows have their
 * own gutters, as the pages of a book in the game do, so that they never sit on a slide's words.
 *
 * <pre>{@code
 * add(new CarouselComponent<>(at(new Box("tips", 10, 10, 200, 70)), tips,
 *         (d, theme, tip, slide, hovered) -> d.wrapped(tip, slide.x() + 4, slide.y() + 4, slide.w() - 8, 5, 10, theme.palette().text()))
 *         .loop(true)
 *         .autoPlay(5000)
 *         .onChange(i -> seen(i)));
 * }</pre>
 *
 * <p>Left, right, home and end move when it has the focus, and let the panel have the key at an end without
 * {@link #loop(boolean)}. The wheel moves one slide per notch. A drag follows the pointer and lands on the
 * next slide past a fifth of its width, or on a flick. More dots than fit turn into a counter. With
 * {@link #autoPlay(int)}, slides move on their own, except under the pointer, with the focus, during a drag,
 * when disabled, and <b>never under reduced motion</b>, where every change is also instant.
 * An empty list shows {@link #emptyText(String)}. Only the visible slides are drawn.
 */
public class CarouselComponent<T> extends BaseComponent {
    /** Draws one slide. Words reach to the box's edge at most: it is cut there. */
    @FunctionalInterface
    public interface SlideRenderer<T> {
        void draw(Draw d, Theme theme, T item, Box slide, boolean hovered);
    }

    private static final int BORDER = 2;
    private static final int ARROW_W = 12;
    private static final int ARROW_H = 18;
    private static final int STRIP_H = 10;
    private static final int DOT = 4;
    private static final int DOT_GAP = 3;
    private static final int MIN_VIEW = 16;
    private static final int SLIDE_MS = 220;
    private static final int DRAG_START = 3;
    private static final float DRAG_AT = 0.2F;
    private static final float FLICK = 0.5F;
    private static final int FLICK_FRESH_MS = 80;
    private static final float RESIST = 0.3F;
    private static final int HOVER_WASH = 0x28;
    private static final int HELD_WASH = 0x50;
    private static final int DOT_ALPHA = 0x50;
    private static final int DOT_HOVER_ALPHA = 0xA0;
    private static final int DISABLED_VEIL = 0x90;
    private static final int NONE = -1;
    private static final int PREV = -2;
    private static final int NEXT = -3;

    private final SlideRenderer<T> renderer;
    private List<T> items;
    private boolean loop;
    private int autoMs;
    private String emptyText;
    private Words words;
    private IntConsumer onChange;
    private IntConsumer onClick;
    private boolean framed;

    private int index;
    private final SurfaceLook.Tween position = new SurfaceLook.Tween(SLIDE_MS, 0F);
    private long lastMove;
    private boolean hoveredNow;

    private int held = NONE;
    private boolean pressedView;
    private boolean dragging;
    private double pressX;
    private float pressPos;
    private double lastX;
    private long lastTime;
    private float speed;
    private Box slideBox;

    /**
     * Slides of pictures: screenshots, items, textures. The picture lies flat in the slide, with its fit,
     * and its caption on a dark band along the bottom.
     */
    public static CarouselComponent<Picture> ofPictures(Box box, List<Picture> pictures) {
        return new CarouselComponent<>(box, pictures, pictures(p -> p));
    }

    /** A slide renderer that draws each item's {@link Picture}, flat, with its caption on a band. */
    public static <T> SlideRenderer<T> pictures(Function<T, Picture> picture) {
        return (d, theme, item, slide, hovered) -> {
            Picture pic = picture.apply(item);
            if (pic == null) {
                return;
            }
            pic.letterbox(0).draw(d, theme, slide.x(), slide.y(), slide.w(), slide.h());
            String caption = pic.caption();
            if (!caption.isEmpty() && slide.h() >= 24 && slide.w() >= 24) {
                d.nextLayer();
                int band = SurfaceLook.LINE + 3;
                d.fill(slide.x(), slide.bottom() - band, slide.right(), slide.bottom(), Shapes.alpha(theme.palette().shadow(), 0xB0));
                String line = d.ellipsize(caption, slide.w() - 6);
                d.text(line, slide.x() + (slide.w() - d.width(line) + 1) / 2, slide.bottom() - band + 3, 0xFFFFFFFF);
            }
        };
    }

    public CarouselComponent(Box box, List<T> items, SlideRenderer<T> renderer) {
        super(box);
        this.renderer = renderer;
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    // ---- setup ----

    /** True to go round from the last slide to the first. Off by default. */
    /**
     * True for the theme's bevelled well, for a carousel standing alone on the screen. Off by default: a
     * flat well with a hairline, which sits well inside a panel.
     */
    public CarouselComponent<T> framed(boolean framed) {
        this.framed = framed;
        return this;
    }

    @Override
    protected boolean drawFocus(Draw d, Theme t) {
        return !framed; // the flat look draws its own 1 px ring
    }

    public CarouselComponent<T> loop(boolean loop) {
        this.loop = loop;
        return this;
    }

    /** Moves on by itself, this often. 0 turns it off, which is the default. See the class's notes for when it rests. */
    public CarouselComponent<T> autoPlay(int everyMs) {
        autoMs = Math.max(0, everyMs);
        lastMove = clock().getAsLong();
        return this;
    }

    @Override
    void words(Words words) {
        this.words = words;
    }

    /** What an empty carousel says, in place of the lang entry. */
    public CarouselComponent<T> emptyText(String emptyText) {
        this.emptyText = emptyText == null ? "" : emptyText;
        return this;
    }

    /** Called with the new slide's index when it changes through the player or auto-play. Not through {@link #show}. */
    public CarouselComponent<T> onChange(IntConsumer onChange) {
        this.onChange = onChange;
        return this;
    }

    /** Called with the slide's index on a click on it without a drag, and on Enter. */
    public CarouselComponent<T> onClick(IntConsumer onClick) {
        this.onClick = onClick;
        return this;
    }

    public CarouselComponent<T> active(boolean active) {
        this.active = active;
        return this;
    }

    @Override
    public CarouselComponent<T> tooltip(String... lines) {
        super.tooltip(lines);
        return this;
    }

    // ---- content ----

    public int count() {
        return items.size();
    }

    public int index() {
        return index;
    }

    /** New slides. The index stays if it still exists. */
    public void setItems(List<T> items) {
        this.items = items == null ? List.of() : List.copyOf(items);
        index = Math.max(0, Math.min(index, this.items.size() - 1));
        position.snap(index);
        dragging = false;
        pressedView = false;
    }

    /** Goes to a slide, without calling back. */
    public void show(int slide) {
        if (!items.isEmpty()) {
            moveTo(slide, false, false);
        }
    }

    /** True while a slide is on its way. */
    public boolean moving() {
        return position.moving();
    }

    public boolean isDragging() {
        return dragging;
    }

    /** Where the view stands, in slides: 1.5 is half way from the second to the third. */
    public float position() {
        return position.value();
    }

    /** True when it would move by itself now. */
    public boolean autoPlaying() {
        return autoMs > 0 && visible && !reducedMotion() && active && items.size() > 1 && !hoveredNow && !focused && !dragging;
    }

    /** True when more dots than fit have turned into a counter. */
    public boolean showsCounter() {
        return showsStrip() && dotsWidth() > w - 2 * BORDER;
    }

    /** Where a slide's words go, at rest. */
    Box viewBox() {
        return new Box("view", viewX(), viewY(), viewW(), viewH());
    }

    /** The arrow's place, for a script or a test. */
    Box arrowBox(boolean next) {
        return new Box(next ? "next" : "prev", arrowX(next), arrowY(), ARROW_W, arrowH());
    }

    /** A dot's place, for a script or a test. */
    Box dotBox(int dot) {
        return new Box("dot", dotX(dot), stripY() + (STRIP_H - DOT) / 2, DOT, DOT);
    }

    // ---- geometry ----

    private boolean showsArrows() {
        return items.size() > 1 && w - 2 * BORDER - 2 * ARROW_W >= MIN_VIEW;
    }

    private boolean showsStrip() {
        return items.size() > 1 && h - 2 * BORDER - STRIP_H >= MIN_VIEW;
    }

    private int viewX() {
        return x + BORDER + (showsArrows() ? ARROW_W : 0);
    }

    private int viewY() {
        return y + BORDER;
    }

    private int viewW() {
        return Math.max(0, w - 2 * BORDER - (showsArrows() ? 2 * ARROW_W : 0));
    }

    private int viewH() {
        return Math.max(0, h - 2 * BORDER - (showsStrip() ? STRIP_H : 0));
    }

    private int stripY() {
        return y + h - BORDER - STRIP_H;
    }

    private int arrowX(boolean next) {
        return next ? x + w - BORDER - ARROW_W : x + BORDER;
    }

    private int arrowY() {
        return viewY() + (viewH() - arrowH()) / 2;
    }

    private boolean overArrow(boolean next, double mx, double my) {
        int ax = arrowX(next);
        int ay = arrowY();
        return mx >= ax && mx < ax + ARROW_W && my >= ay && my < ay + arrowH();
    }

    private int arrowH() {
        return Math.min(ARROW_H, viewH());
    }

    private int dotsWidth() {
        return items.size() * DOT + (items.size() - 1) * DOT_GAP;
    }

    private int dotX(int dot) {
        return x + (w - dotsWidth()) / 2 + dot * (DOT + DOT_GAP);
    }

    private int partAt(double mx, double my) {
        if (showsArrows()) {
            if (overArrow(false, mx, my)) {
                return PREV;
            }
            if (overArrow(true, mx, my)) {
                return NEXT;
            }
        }
        if (showsStrip() && !showsCounter() && my >= stripY() && my < stripY() + STRIP_H) {
            int rel = (int) Math.floor(mx) - dotX(0) + DOT_GAP / 2 + 1;
            int cell = DOT + DOT_GAP;
            if (rel >= 0 && rel < cell * items.size()) {
                return rel / cell;
            }
        }
        return NONE;
    }

    private boolean inView(double mx, double my) {
        return mx >= viewX() && mx < viewX() + viewW() && my >= viewY() && my < viewY() + viewH();
    }

    private boolean canGo(int by) {
        if (items.size() < 2) {
            return false;
        }
        return loop || (index + by >= 0 && index + by < items.size());
    }

    // ---- moving ----

    private void moveBy(int by, boolean tell) {
        if (!canGo(by)) {
            // Falls back into place when a drag let go at an end.
            position.go(index);
            return;
        }
        int next = Math.floorMod(index + by, items.size());
        if (loop) {
            // Keep going the same way round: the position wraps when it arrives.
            startTo(next, position.target() + by, tell, false);
        } else {
            startTo(next, next, tell, false);
        }
    }

    /** {@code pick}: the player chose this slide (a dot), which clicks; a step by key or wheel taps. */
    private void moveTo(int slide, boolean tell, boolean pick) {
        int next = Math.floorMod(slide, Math.max(1, items.size()));
        startTo(next, next, tell, pick);
    }

    private void startTo(int next, float target, boolean tell, boolean pick) {
        boolean changed = next != index;
        index = next;
        lastMove = clock().getAsLong();
        position.go(target);
        if (changed && tell) {
            if (pick) {
                BaseComponent.click();
            } else {
                BaseComponent.sound().tap();
            }
            if (onChange != null) {
                onChange.accept(index);
            }
        }
    }

    /** Back to the plain index once a move round the end has landed. */
    private void landed() {
        if (!dragging && !position.moving() && position.target() != index) {
            position.snap(index);
        }
    }

    private void autoStep(long now) {
        if (autoMs <= 0) {
            return;
        }
        if (!autoPlaying() || position.moving()) {
            lastMove = now;
            return;
        }
        Redraw.ask();
        if (now - lastMove >= autoMs) {
            if (canGo(1)) {
                moveBy(1, false);
            } else {
                moveTo(0, false, false);
            }
            if (onChange != null) {
                onChange.accept(index);
            }
        }
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        hoveredNow = hovered;
        autoStep(Redraw.quietClock());
        landed();
        Palette p = t.palette();
        if (framed) {
            t.frame().well(d, p, x, y, w, h, focused && active);
        } else {
            // Flat: the theme's well is bevelled, and inside a panel's frame that stacked three edges.
            PictureLook.flat(d, t, x, y, w, h, true);
            if (focused && active) {
                t.frame().plate(d, p, x, y, w, h, 0, p.accent());
            }
        }
        if (items.isEmpty()) {
            RowLook.drawEmpty(d, p, text(emptyText, words, "minuit.empty", "Nothing here"), active, x, y, w, h);
            return;
        }
        drawSlides(d, t, hovered && active && inView(mx, my));
        int part = active && hovered && !dragging ? partAt(mx, my) : NONE;
        if (showsArrows()) {
            drawArrow(d, t, p, false, part == PREV);
            drawArrow(d, t, p, true, part == NEXT);
        }
        if (showsStrip()) {
            drawStrip(d, t.frame(), p, part);
        }
    }

    private void drawSlides(Draw d, Theme t, boolean over) {
        int vw = viewW();
        int vh = viewH();
        if (vw <= 0 || vh <= 0) {
            return;
        }
        int vx = viewX();
        int vy = viewY();
        Palette p = t.palette();
        float pos = position.value();
        int first = (int) Math.floor(pos);
        int shift = Math.round((pos - first) * vw);
        d.scissor(vx, vy, vx + vw, vy + vh);
        if (shift == 0) {
            if (slideBox == null || slideBox.x() != vx || slideBox.y() != vy || slideBox.w() != vw || slideBox.h() != vh) {
                slideBox = new Box("slide", vx, vy, vw, vh);
            }
            drawOne(d, t, first, slideBox, over);
        } else {
            drawOne(d, t, first, new Box("slide", vx - shift, vy, vw, vh), false);
            drawOne(d, t, first + 1, new Box("slide", vx - shift + vw, vy, vw, vh), false);
        }
        d.endScissor();
        if (!active) {
            // The whole well, not just the view: over a see-through family the view would show as a dark block.
            d.fill(x + 1, y + 1, x + w - 1, y + h - 1, Shapes.alpha(p.well() | 0xFF000000, DISABLED_VEIL));
        }
    }

    private void drawOne(Draw d, Theme t, int at, Box box, boolean over) {
        int size = items.size();
        if (!loop && (at < 0 || at >= size)) {
            return; // a drag past the end shows nothing there
        }
        if (renderer != null) {
            renderer.draw(d, t, items.get(Math.floorMod(at, size)), box, over);
        }
    }

    private void drawArrow(Draw d, Theme t, Palette p, boolean next, boolean over) {
        int ax = arrowX(next);
        int ay = arrowY();
        int ah = arrowH();
        boolean can = active && canGo(next ? 1 : -1);
        int ink = Ink.on(p, p.well());
        if (can && over) {
            d.fill(ax, ay, ax + ARROW_W, ay + ah, Shapes.alpha(ink, held == (next ? NEXT : PREV) ? HELD_WASH : HOVER_WASH));
        }
        IconArt art = t.icon(next ? Icon.NEXT : Icon.PREV);
        int color = !can ? Shapes.alpha(p.disabledText(), 0x80) : over ? ink : Ink.soft(p, p.well());
        d.scissor(ax, ay, ax + ARROW_W, ay + ah);
        art.draw(d, ax + (ARROW_W - art.width()) / 2, ay + (ah - art.height()) / 2, color, 1F);
        d.endScissor();
    }

    private void drawStrip(Draw d, FrameStyle frame, Palette p, int part) {
        int top = stripY();
        int ink = Ink.on(p, p.well());
        if (showsCounter()) {
            String counter = (index + 1) + " / " + items.size();
            d.text(counter, x + (w - d.width(counter)) / 2, top + (STRIP_H - SurfaceLook.FONT_H) / 2 + 1, active ? Ink.soft(p, p.well()) : p.disabledText());
            return;
        }
        int dy = top + (STRIP_H - DOT) / 2;
        int lit = Ink.readable(p, p.markInk(), p.well(), Ink.MARK);
        for (int i = 0; i < items.size(); i++) {
            int dx = dotX(i);
            int color;
            if (!active) {
                color = i == index ? p.disabledText() : Shapes.alpha(p.disabledText(), DOT_ALPHA);
            } else if (i == index) {
                color = lit;
            } else {
                color = Shapes.alpha(ink, part == i ? DOT_HOVER_ALPHA : DOT_ALPHA);
            }
            frame.dot(d, p, dx, dy, DOT, color, 0);
        }
    }

    // ---- input ----

    @Override
    public void tick() {
        autoStep(Redraw.quietClock());
    }

    @Override
    protected boolean takesFocus() {
        return visible && active && !items.isEmpty();
    }

    @Override
    protected boolean navigable() {
        return visible && active && !items.isEmpty();
    }

    @Override
    protected boolean activate() {
        if (!navigable() || onClick == null) {
            return false;
        }
        BaseComponent.click();
        onClick.accept(index);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !active || button != 0 || !isOver(mx, my) || items.isEmpty()) {
            return false;
        }
        int part = partAt(mx, my);
        if (part != NONE) {
            held = part;
            return true;
        }
        if (inView(mx, my)) {
            pressedView = true;
            pressX = mx;
            pressPos = position.value();
            lastX = mx;
            lastTime = clock().getAsLong();
            speed = 0F;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!pressedView || items.size() < 2) {
            return false;
        }
        if (!dragging && Math.abs(mx - pressX) >= DRAG_START) {
            dragging = true;
        }
        if (dragging) {
            float pos = pressPos - (float) ((mx - pressX) / Math.max(1, viewW()));
            if (!loop) {
                float last = items.size() - 1;
                pos = pos < 0F ? pos * RESIST : pos > last ? last + (pos - last) * RESIST : pos;
            }
            position.snap(pos);
            long now = clock().getAsLong();
            if (now > lastTime) {
                speed = (float) ((mx - lastX) / (now - lastTime));
                lastTime = now;
                lastX = mx;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            pressedView = false;
            float moved = position.value() - pressPos;
            // A pointer that stopped before letting go did not flick.
            float flick = clock().getAsLong() - lastTime <= FLICK_FRESH_MS ? speed : 0F;
            int by = moved >= DRAG_AT || flick <= -FLICK ? 1 : moved <= -DRAG_AT || flick >= FLICK ? -1 : 0;
            int from = index;
            if (by != 0 && canGo(by)) {
                if (loop) {
                    // Whichever way round is nearest to where the drag let go.
                    index = Math.floorMod(index + by, items.size());
                    startLoopLanding(from, by);
                } else {
                    moveBy(by, true);
                }
            } else {
                position.go(loop ? pressPos : index);
                lastMove = clock().getAsLong();
            }
            return true;
        }
        if (pressedView) {
            pressedView = false;
            if (inView(mx, my) && onClick != null && active) {
                BaseComponent.click();
                onClick.accept(index);
            }
            return true;
        }
        int was = held;
        held = NONE;
        if (was == NONE) {
            return false;
        }
        if (active && partAt(mx, my) == was) {
            if (was == PREV) {
                moveBy(-1, true);
            } else if (was == NEXT) {
                moveBy(1, true);
            } else {
                moveTo(was, true, true);
            }
        }
        return true;
    }

    private void startLoopLanding(int from, int by) {
        lastMove = clock().getAsLong();
        position.go(Math.round(pressPos) + by);
        BaseComponent.sound().tap();
        if (onChange != null && from != index) {
            onChange.accept(index);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible || !active || !isOver(mx, my) || dragging || amount == 0D) {
            return false;
        }
        int by = amount > 0D ? -1 : 1;
        if (!canGo(by)) {
            return false;
        }
        moveBy(by, true);
        return true;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!visible || !active || items.isEmpty()) {
            return false;
        }
        switch (key) {
            case LEFT, RIGHT -> {
                int by = key == InputKey.RIGHT ? 1 : -1;
                if (!canGo(by)) {
                    return false;
                }
                moveBy(by, true);
                return true;
            }
            case HOME -> {
                moveTo(0, true, false);
                return true;
            }
            case END -> {
                moveTo(items.size() - 1, true, false);
                return true;
            }
            case ENTER, SPACE -> {
                return activate();
            }
            default -> {
                return false;
            }
        }
    }
}
