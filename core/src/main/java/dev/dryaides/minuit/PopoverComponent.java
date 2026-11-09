package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A small floating panel opened from a widget: a few words, or a few widgets, that do not deserve a dialog.
 * It flips to the other side of its anchor when it does not fit, and never leaves its area.
 *
 * <pre>{@code
 * public class ColoursPanel extends PanelComponent {
 *     public ColoursPanel(Theme theme, Words words, int left, int top) {
 *         super(theme, words, left, top);
 *         ButtonComponent more = add(new ButtonComponent(at(new Box("more", 10, 10, 60, 20)), words.get("mymod.more"), () -> pop.toggle()));
 *         // Last, so that it is drawn on top and offered every input first.
 *         pop = add(new PopoverComponent(more, this).title(words.get("mymod.more")).text(words.get("mymod.more.body")));
 *         pop.add(new ToggleComponent(new Box("t", 0, 0, 120, 12), words.get("mymod.grid"), true, on -> { }), 8, 34);
 *     }
 * }
 * }</pre>
 *
 * <h2>Putting it in a panel</h2>
 * Add it <b>after every other widget</b>, and have the anchor's own action call {@link #toggle()}. While it
 * is open, the first click outside only closes it, so it presses nothing beneath; a click on the anchor
 * closes it too, and does not reopen it. {@link #passClicks(boolean)} lets a click outside go on to what
 * is beneath. Escape closes it and gives the focus back. Tab cycles inside it when it holds a widget that
 * takes the keyboard; with none, Tab closes it and moves on.
 *
 * <p>A child's position is relative to the card's top left; the card moves it as it flips. Widgets beneath
 * still show their hover, because a panel gives them the real pointer position. {@code active = false} keeps
 * it open with its widgets off. Only a direct child of a panel has been tried.
 */
public class PopoverComponent extends BaseComponent {
    private static final int PAD = 7;
    private static final int LINE = SurfaceLook.LINE;
    private static final int GAP = 3;
    private static final int OPEN_MS = 110;
    private static final int SLIDE = 5;
    private static final int OFF_POINTER = -100_000;
    private static final int MIN_W = 40;

    private final PanelComponent host;
    private final SurfaceAnchor anchor;
    private final List<Child> children = new ArrayList<>();
    private String title = "";
    private String text = "";
    private int cardW = 140;
    private boolean passClicks;
    private Runnable onOpen;
    private Runnable onClose;

    private final SurfaceBody body;
    private Glide glide = new Glide(Slide.UP, OPEN_MS);
    private BaseComponent focusBefore;
    private boolean open;
    private boolean dirty = true;
    private List<String> lines = List.of();
    private int shownLines;
    private int contentH;
    private boolean bodyBuilt;

    private boolean laidActive = true;

    private static final class Child {
        final BaseComponent widget;
        final int rx;
        final int ry;
        boolean wanted = true;

        Child(BaseComponent widget, int rx, int ry) {
            this.widget = widget;
            this.rx = rx;
            this.ry = ry;
        }
    }

    /** @param anchor the widget it points at; the anchor's box is read when it opens and as it moves */
    public PopoverComponent(BaseComponent anchor, PanelComponent host) {
        this(Objects.requireNonNull(anchor, "anchor"), null, host);
    }

    /** @param anchor a place it points at, in the panel's coordinates */
    public PopoverComponent(Box anchor, PanelComponent host) {
        this(null, Objects.requireNonNull(anchor, "anchor"), host);
    }

    private PopoverComponent(BaseComponent widget, Box box, PanelComponent host) {
        super(0, 0, 0, 0);
        this.host = Objects.requireNonNull(host, "host");
        this.anchor = new SurfaceAnchor(widget, box, host);
        this.body = new SurfaceBody(host.theme(), host.words);
        visible = false;
        glide.snap(false);
    }

    // ---- setup ----

    public PopoverComponent title(String title) {
        this.title = title == null ? "" : title;
        dirty = true;
        return this;
    }

    public PopoverComponent text(String text) {
        this.text = text == null ? "" : text;
        dirty = true;
        return this;
    }

    /** The card's width, 140 by default. It stays inside the area. */
    public PopoverComponent width(int width) {
        cardW = Math.max(MIN_W, width);
        dirty = true;
        return this;
    }

    /** The side it would rather sit on. Below by default. */
    public PopoverComponent side(SurfaceSide side) {
        anchor.side = side == null ? SurfaceSide.BOTTOM : side;
        return this;
    }

    /** How it lines up with the anchor along that side. Its start by default. */
    public PopoverComponent align(SurfaceAlign align) {
        anchor.align = align == null ? SurfaceAlign.START : align;
        return this;
    }

    /** The space between the anchor and the card, 6 by default. */
    public PopoverComponent gap(int gap) {
        anchor.gap = Math.max(0, gap);
        return this;
    }

    /** False for no small pointer towards the anchor. On by default. */
    public PopoverComponent arrow(boolean arrow) {
        anchor.arrow = arrow;
        return this;
    }

    /** The box it stays in, in the panel's coordinates. The panel's bounds by default; pass the screen's to leave it. */
    public PopoverComponent area(Box area) {
        anchor.area = area;
        return this;
    }

    /** True to let a click outside reach what is beneath as it closes. Off by default. */
    public PopoverComponent passClicks(boolean passClicks) {
        this.passClicks = passClicks;
        return this;
    }

    public PopoverComponent onOpen(Runnable onOpen) {
        this.onOpen = onOpen;
        return this;
    }

    /** Runs each time it closes, whatever closed it. */
    public PopoverComponent onClose(Runnable onClose) {
        this.onClose = onClose;
        return this;
    }

    public PopoverComponent active(boolean active) {
        this.active = active;
        return this;
    }

    /** Adds a widget at a place relative to the card's top left. The card grows to hold it. */
    public <W extends BaseComponent> W add(W widget, int rx, int ry) {
        children.add(new Child(widget, rx, ry));
        bodyBuilt = false;
        dirty = true;
        return widget;
    }

    // ---- state ----

    /** Opens and takes the panel's focus. False when the anchor is hidden or the popover disabled in the panel. */
    public boolean open() {
        if (open) {
            return true;
        }
        if (!anchor.shown()) {
            return false;
        }
        BaseComponent current = host.focus();
        focusBefore = current == this ? null : current;
        host.closeOverlay();
        open = true;
        visible = true;
        dirty = true;
        layout();
        glide = new Glide(anchor.slideFrom(), OPEN_MS);
        if (reducedMotion) {
            glide.snap(true);
        } else {
            glide.replay();
        }
        host.focus(this);
        if (onOpen != null) {
            onOpen.run();
        }
        return true;
    }

    /** Closes and gives the focus back. */
    public void close() {
        closeKeeping(true);
    }

    private void closeKeeping(boolean giveFocusBack) {
        if (!open) {
            return;
        }
        open = false;
        if (reducedMotion) {
            glide.snap(false);
        } else {
            glide.hide();
        }
        settle();
        if (giveFocusBack && host.focus() == this) {
            host.focus(focusBefore);
        }
        focusBefore = null;
        if (onClose != null) {
            onClose.run();
        }
    }

    public void toggle() {
        if (open) {
            close();
        } else {
            open();
        }
    }

    public boolean isOpen() {
        return open;
    }

    /** True while the card glides. Clicks in it wait for the end; keys do not. */
    public boolean animating() {
        return glide.moving();
    }

    /** The card's place. */
    Box cardBox() {
        layout();
        return new Box("card", anchor.x, anchor.y, anchor.w, anchor.h);
    }

    /** The side it ended up on after flipping. */
    public SurfaceSide placedSide() {
        layout();
        return anchor.placed;
    }

    /** True when the small pointer is drawn: not when the card had to cover its anchor. */
    public boolean showsArrow() {
        layout();
        return anchor.arrowAt >= 0;
    }

    /** How many lines of the text are shown; fewer than it wraps to only in a very short area. */
    int shownLines() {
        layout();
        return shownLines;
    }

    private void settle() {
        if (!open && visible && !glide.moving()) {
            visible = false;
        }
    }

    // ---- layout ----

    private void layout() {
        if (dirty) {
            dirty = false;
            int inner = Math.max(1, Math.min(cardW, anchor.maxWidth()) - 2 * PAD);
            lines = text.isEmpty() ? List.of() : TextMeasure.wrapAll(text, inner);
            int top = PAD + (title.isEmpty() ? 0 : LINE + (lines.isEmpty() ? 0 : GAP - 1));
            int textBottom = top + lines.size() * LINE;
            int kidsBottom = 0;
            for (int i = 0; i < children.size(); i++) {
                Child c = children.get(i);
                kidsBottom = Math.max(kidsBottom, c.ry + c.widget.h);
            }
            if (!bodyBuilt) {
                bodyBuilt = true;
                body.clearWidgets();
                for (int i = 0; i < children.size(); i++) {
                    body.put(children.get(i).widget);
                }
            }
            contentH = Math.max(textBottom - (lines.isEmpty() ? 0 : 2), kidsBottom) + PAD;
        }
        anchor.place(Math.min(cardW, anchor.maxWidth()), contentH);
        x = anchor.x;
        y = anchor.y;
        w = anchor.w;
        h = anchor.h;
        int top = PAD + (title.isEmpty() ? 0 : LINE + (lines.isEmpty() ? 0 : GAP - 1));
        shownLines = Math.min(lines.size(), Math.max(0, (h - top - PAD + 2) / LINE));
        body.place(x, y, w, h);
        for (int i = 0; i < children.size(); i++) {
            Child c = children.get(i);
            c.widget.x = x + c.rx;
            c.widget.y = y + c.ry;
            if (active != laidActive) {
                if (!active) {
                    c.wanted = c.widget.active;
                }
                c.widget.active = active && c.wanted;
            }
        }
        laidActive = active;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        settle();
        if (!visible) {
            return;
        }
        if (open && !anchor.shown()) {
            closeKeeping(true);
        }
        layout();
        if (open && host.focus() == null) {
            host.focus(this);
        }
        d.nextLayer(); // the text beneath may sort above a rectangle otherwise
        boolean moving = glide.moving();
        if (moving) {
            int pad = SurfaceAnchor.ARROW_H;
            d.scissor(x - pad, y - pad, x + w + pad, y + h + pad);
        }
        d.push(Math.round(glide.offsetX(SLIDE)), Math.round(glide.offsetY(SLIDE)), 1F);
        t.frame().popup(d, t.palette(), x, y, w, h);
        anchor.drawArrow(d, t);
        drawWords(d, t.palette());
        body.use(t);
        boolean live = open && !moving && active;
        body.draw(d, live ? mx : OFF_POINTER, live ? my : OFF_POINTER, delta);
        d.pop();
        if (moving) {
            d.endScissor();
        }
    }

    private void drawWords(Draw d, Palette p) {
        int under = p.surface() | 0xFF000000;
        int room = w - 2 * PAD;
        if (room < 8) {
            return;
        }
        int line = y + PAD;
        if (!title.isEmpty() && line + SurfaceLook.FONT_H <= y + h) {
            d.textFit(title, x + PAD, line, room, active ? Ink.on(p, under) : p.disabledText());
            line += LINE + (lines.isEmpty() ? 0 : GAP - 1);
        }
        int soft = active ? Ink.soft(p, under) : p.disabledText();
        for (int i = 0; i < shownLines; i++) {
            String row = lines.get(i);
            d.text(i == shownLines - 1 && shownLines < lines.size() ? d.ellipsize(row + Draw.ELLIPSIS, room) : row, x + PAD, line, soft);
            line += LINE;
        }
    }

    /** The panel must not ring the whole card with nothing to press; the card's own stops are ringed inside. */
    @Override
    protected boolean drawFocus(Draw d, Theme t) {
        return true;
    }

    @Override
    public void tick() {
        settle();
        if (visible) {
            body.tickAll();
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!open) {
            return null;
        }
        layout();
        if (box().contains(mx, my) && !glide.moving() && active) {
            List<String> inner = body.tooltipAt(mx, my);
            return inner != null ? inner : List.of();
        }
        return passClicks ? null : List.of(); // empty, not null: a panel goes on to the widgets beneath on null
    }

    // ---- input ----

    @Override
    public boolean isOver(double mx, double my) {
        if (!open) {
            return false;
        }
        return !passClicks || box().contains(mx, my);
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
    protected boolean capturesKeys() {
        return open && body.capturing();
    }

    @Override
    public boolean keyCaptured(KeyPress press) {
        return open && body.rawKey(press);
    }

    @Override
    protected boolean activate() {
        return open && keyPressed(InputKey.ENTER, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!open) {
            return false;
        }
        layout();
        if (box().contains(mx, my)) {
            if (active && !glide.moving()) {
                body.mouseClicked(mx, my, button);
            }
            return true;
        }
        if (anchor.contains(mx, my)) {
            BaseComponent.click();
            closeKeeping(true);
            return true; // the anchor's own press would open it again
        }
        if (passClicks) {
            closeKeeping(false);
            return false;
        }
        BaseComponent.click();
        closeKeeping(true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return visible && body.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return open && active && body.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!open) {
            return false;
        }
        layout();
        if (box().contains(mx, my)) {
            body.mouseScrolled(mx, my, amount);
            return true;
        }
        return !passClicks;
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!open) {
            return false;
        }
        layout();
        if (body.capturing()) {
            body.keyPressed(key, shift);
            return true;
        }
        if (key == InputKey.ESCAPE) {
            if (body.overlay() != null) {
                body.closeOverlay();
            } else {
                BaseComponent.click();
                closeKeeping(true);
            }
            return true;
        }
        if (key == InputKey.TAB && body.stops().isEmpty()) {
            closeKeeping(true);
            return false;
        }
        if (!active) {
            return true;
        }
        body.keyPressed(key, shift);
        return true;
    }

    @Override
    public boolean charTyped(int codePoint) {
        if (!open) {
            return false;
        }
        if (active) {
            body.charTyped(codePoint);
        }
        return true;
    }
}
