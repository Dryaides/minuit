package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A sheet that slides in from an edge of its area, for more than a dialog should hold: filters, details, a
 * list of settings. It has a title, a wrapped message that scrolls when it does not fit, an optional child, up
 * to three actions at the end, a close button, and a handle to drag it away.
 *
 * <pre>{@code
 * public class WorldsPanel extends PanelComponent {
 *     private final DrawerComponent details;
 *
 *     public WorldsPanel(Theme theme, Words words, int left, int top) {
 *         super(theme, words, left, top);
 *         add(new ButtonComponent(at(new Box("more", 10, 10, 90, 20)), words.get("mymod.more"), () -> details.open(this)));
 *         // Last, so that it is drawn on top and offered every input first.
 *         details = add(new DrawerComponent(bounds(), SurfaceSide.RIGHT, 150).title(words.get("mymod.details")).message(about));
 *     }
 * }
 * }</pre>
 *
 * <h2>Modal or not</h2>
 * Modal by default, as a dialog: it dims what is beneath and takes all input, Tab stays inside it, and a click
 * on the dim closes it. With {@link #modal(boolean) modal(false)} there is no dim and the rest of the screen
 * keeps working: it takes only what lands on the sheet, Tab walks through it and on to the panel's next
 * widget, and Escape closes it only while it has the focus.
 *
 * <h2>Closing</h2>
 * Escape, the close button, the dim, or the handle: dragged past a third of the sheet or flicked towards its edge,
 * it goes; let go earlier, and it comes back. A drag follows the pointer even under reduced motion, and then the
 * sheet jumps to its end when let go. {@code active = false} keeps it open with its parts off, for a busy state;
 * only the wheel still works. The sheet's size stays inside the area.
 *
 * <p>Add it <b>after every other widget</b>, as a dialog. A child places its parts from its {@code x} and {@code y}.
 * While a modal drawer is open, the widgets beneath still show their hover, because a panel gives them the real
 * pointer position; they take no click, key or tooltip. Only a direct child of a panel has been tried.
 */
public class DrawerComponent extends BaseComponent {
    private static final int MAX_ACTIONS = 3;
    private static final int SLIDE_MS = 190;
    private static final int PAD = 8;
    private static final int GAP = 6;
    private static final int LINE = SurfaceLook.LINE;
    private static final int FONT_H = SurfaceLook.FONT_H;
    /** The grip's strip along the free edge. */
    private static final int HANDLE = 8;
    private static final int GRIP_LENGTH = 24;
    private static final int GRIP_THICK = 2;
    private static final int CLOSE = 11;
    private static final int BUTTON_H = 18;
    private static final int BUTTON_MIN = 50;
    private static final int BUTTON_PAD = 14;
    private static final int SCROLL_ROOM = ScrollComponent.BAR + 3;
    private static final int OFF_POINTER = -100_000;
    private static final int MIN_SIZE = 40;
    /** The frame reaches past the area on three sides, so that only its free edge and corners show. */
    private static final int OVERSCAN = 8;
    private static final float CLOSE_AT = 1F / 3F;
    private static final float FLICK = 0.45F;
    /** A pointer that stopped before letting go did not flick. */
    private static final int FLICK_FRESH_MS = 80;
    private static final int CLOSE_HOVER = 0x30;
    private static final int CLOSE_HELD = 0x60;
    private static final int GRIP_ALPHA = 0x70;

    private final SurfaceSide side;
    private final List<Action> actions = new ArrayList<>();
    private final SurfaceLook.Tween shown = new SurfaceLook.Tween(SLIDE_MS, 0F);
    private int size;
    private String title = "";
    private String message = "";
    private BaseComponent content;
    private boolean modal = true;
    private boolean closeOnBackdrop = true;
    private boolean showsHandle = true;
    private boolean showsClose = true;
    private Runnable onOpen;
    private Runnable onClose;

    private PanelComponent host;
    private BaseComponent focusBefore;
    private SurfaceBody body;
    private ScrollComponent scroll;
    private List<ButtonComponent> buttons = List.of();
    private List<String> titleLines = List.of();
    private Box sheet = new Box("sheet", 0, 0, 0, 0);
    private Box handleAt = sheet;
    private Box closeAt = sheet;
    private boolean open;
    private boolean dirty = true;
    private int laidX;
    private int laidY;
    private int laidW;
    private int laidH;
    private boolean dragging;
    private boolean closeHeld;
    private boolean leaving;
    private double dragStart;
    private float dragShown;
    private double lastPos;
    private long lastTime;
    private float speed;

    /**
     * @param area what it slides over; {@code PanelComponent.bounds()} or the screen's box
     * @param side the edge it comes from
     * @param size how far it reaches in from that edge
     */
    public DrawerComponent(Box area, SurfaceSide side, int size) {
        super(area);
        this.side = Objects.requireNonNull(side, "side");
        this.size = Math.max(MIN_SIZE, size);
        visible = false;
    }

    // ---- setup ----

    public DrawerComponent title(String title) {
        this.title = title == null ? "" : title;
        dirty = true;
        return this;
    }

    public DrawerComponent message(String message) {
        this.message = message == null ? "" : message;
        dirty = true;
        return this;
    }

    /** A child under the message, scrolled with it. Null removes it. Give it the size it wants first. */
    public DrawerComponent content(BaseComponent content) {
        this.content = content;
        dirty = true;
        return this;
    }

    /**
     * Adds a button along the sheet's end. Pressing it closes the drawer, then runs the callback.
     *
     * @throws IllegalStateException past the third action
     */
    public DrawerComponent action(String label, ButtonComponent.Role role, Runnable onPress) {
        if (actions.size() >= MAX_ACTIONS) {
            throw new IllegalStateException("A drawer has at most " + MAX_ACTIONS + " actions");
        }
        actions.add(new Action(Objects.requireNonNull(label, "label"), role == null ? ButtonComponent.Role.SECONDARY : role, onPress));
        dirty = true;
        return this;
    }

    /** False for a sheet that leaves the rest of the screen working, with no dim. On by default. */
    public DrawerComponent modal(boolean modal) {
        this.modal = modal;
        return this;
    }

    /** False to keep a modal drawer open on a click on the dim. On by default. */
    public DrawerComponent closeOnBackdrop(boolean closeOnBackdrop) {
        this.closeOnBackdrop = closeOnBackdrop;
        return this;
    }

    /** False for no grip and no drag. On by default. */
    public DrawerComponent handle(boolean handle) {
        showsHandle = handle;
        dirty = true;
        return this;
    }

    /** False for no close button. Escape still closes it. On by default. */
    public DrawerComponent closeButton(boolean closeButton) {
        showsClose = closeButton;
        dirty = true;
        return this;
    }

    /** How far it reaches in from its edge. */
    public DrawerComponent size(int size) {
        this.size = Math.max(MIN_SIZE, size);
        dirty = true;
        return this;
    }

    public DrawerComponent onOpen(Runnable onOpen) {
        this.onOpen = onOpen;
        return this;
    }

    /** Runs each time it closes, whatever closed it, before an action's own callback. */
    public DrawerComponent onClose(Runnable onClose) {
        this.onClose = onClose;
        return this;
    }

    public DrawerComponent active(boolean active) {
        this.active = active;
        syncActive();
        return this;
    }

    // ---- opening and closing ----

    /**
     * Slides in and takes the panel's focus.
     *
     * @param panel the panel this drawer was added to
     */
    public void open(PanelComponent panel) {
        host = Objects.requireNonNull(panel, "panel");
        if (!open) {
            BaseComponent current = panel.focus();
            focusBefore = current == this ? null : current;
        }
        panel.closeOverlay();
        boolean was = open;
        open = true;
        visible = true;
        if (body == null) {
            body = new SurfaceBody(panel.theme(), panel.words);
        }
        dirty = true;
        dragging = false;
        shown.go(1F);
        panel.focus(this);
        if (!was && onOpen != null) {
            onOpen.run();
        }
    }

    /** Slides away and gives the focus back. */
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        dragging = false;
        closeHeld = false;
        shown.go(0F);
        settle();
        if (host != null && host.focus() == this) {
            host.focus(focusBefore);
        }
        focusBefore = null;
        if (onClose != null) {
            onClose.run();
        }
    }

    public boolean isOpen() {
        return open;
    }

    /** True while it slides, not while it is dragged. Clicks wait for it to rest; keys do not. */
    public boolean animating() {
        return shown.moving();
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean isModal() {
        return modal;
    }

    /** How far it is in: 1 at rest, 0 away. */
    public float progress() {
        return shown.value();
    }

    /** The sheet's place at rest. */
    Box sheetBox() {
        if (body != null) {
            layout();
        }
        return sheet;
    }

    /** The grip's strip, at rest, for a script or a test. */
    Box handleBox() {
        sheetBox();
        return handleAt;
    }

    /** The close button's place, at rest. */
    Box closeBox() {
        sheetBox();
        return closeAt;
    }

    /** The action the keyboard is on, or -1. */
    public int focusedAction() {
        if (body == null) {
            return -1;
        }
        BaseComponent at = body.focus() != null ? body.focus() : body.cursor();
        return at instanceof ButtonComponent ? buttons.indexOf(at) : -1;
    }

    /** The panel inside, for a {@link DropdownComponent}'s overlay in the content. */
    public void showOverlay(BaseComponent widget) {
        if (body != null) {
            body.showOverlay(widget);
        }
    }

    private void settle() {
        if (!open && visible && !shown.moving() && shown.value() <= 0F) {
            visible = false;
        }
    }

    // ---- layout ----

    private int insetTop() {
        return side == SurfaceSide.BOTTOM && showsHandle ? HANDLE : 0;
    }

    private int insetBottom() {
        return side == SurfaceSide.TOP && showsHandle ? HANDLE : 0;
    }

    private int insetStart() {
        return side == SurfaceSide.RIGHT && showsHandle ? HANDLE : 0;
    }

    private int insetEnd() {
        return side == SurfaceSide.LEFT && showsHandle ? HANDLE : 0;
    }

    private int reach() {
        return side.vertical() ? Math.min(size, h) : Math.min(size, w);
    }

    private void layout() {
        if (!dirty && laidX == x && laidY == y && laidW == w && laidH == h) {
            return;
        }
        dirty = false;
        laidX = x;
        laidY = y;
        laidW = w;
        laidH = h;
        int reach = reach();
        sheet = switch (side) {
            case LEFT -> new Box("sheet", x, y, reach, h);
            case RIGHT -> new Box("sheet", x + w - reach, y, reach, h);
            case TOP -> new Box("sheet", x, y, w, reach);
            default -> new Box("sheet", x, y + h - reach, w, reach);
        };
        handleAt = switch (side) {
            case LEFT -> new Box("handle", sheet.right() - HANDLE, sheet.y(), HANDLE, sheet.h());
            case RIGHT -> new Box("handle", sheet.x(), sheet.y(), HANDLE, sheet.h());
            case TOP -> new Box("handle", sheet.x(), sheet.bottom() - HANDLE, sheet.w(), HANDLE);
            default -> new Box("handle", sheet.x(), sheet.y(), sheet.w(), HANDLE);
        };
        closeAt = new Box("close", sheet.right() - insetEnd() - PAD + 3 - CLOSE, sheet.y() + insetTop() + PAD - 2, CLOSE, CLOSE);
        int left = sheet.x() + insetStart() + PAD;
        int inner = Math.max(1, sheet.w() - insetStart() - insetEnd() - 2 * PAD);
        int top = sheet.y() + insetTop() + PAD;
        int bottom = sheet.bottom() - insetBottom() - PAD;

        int titleRoom = Math.max(1, inner - (showsClose ? CLOSE + 2 : 0));
        titleLines = title.isEmpty() ? List.of() : fitTitle(titleRoom);
        int headH = Math.max(titleLines.size() * LINE - (titleLines.isEmpty() ? 0 : 2), showsClose ? CLOSE - 5 : 0);
        int flowTop = top + headH + (headH > 0 ? GAP : 0);
        int actionsH = actions.isEmpty() ? 0 : BUTTON_H + GAP;
        int room = Math.max(LINE, bottom - actionsH - flowTop);

        List<String> lines = wrap(inner);
        int viewW = inner;
        if (flowHeight(lines) > room) {
            viewW = Math.max(1, inner - SCROLL_ROOM);
            lines = wrap(viewW);
        }
        int flowH = flowHeight(lines);
        int viewH = Math.min(flowH, room);
        if (flowH > room && content == null) {
            viewH = Math.max(LINE, room - room % LINE); // whole lines, none cut in half at the bottom
        }
        boolean overflow = flowH > room;
        ScrollComponent view = overflow ? new ScrollComponent(new Box("view", left, flowTop, inner, viewH)) : null;
        if (view != null) {
            view.step = LINE;
        }
        List<BaseComponent> flow = new ArrayList<>();
        int flowY = flowTop;
        if (!lines.isEmpty()) {
            flow.add(new TextBlock(new Box("message", left, flowY, viewW, lines.size() * LINE), lines));
            flowY += lines.size() * LINE + (content != null ? GAP : 0);
        }
        if (content != null) {
            content.x = left;
            content.y = flowY;
            flow.add(content);
        }
        if (view != null) {
            flow.forEach(view::add);
            flow = List.of(view);
        }
        scroll = view;

        body.clearWidgets();
        body.place(sheet.x(), sheet.y(), sheet.w(), sheet.h());
        flow.forEach(body::put);
        buttons = buildButtons(left, inner, bottom);
        buttons.forEach(body::put);
        syncActive();
    }

    private List<ButtonComponent> buildButtons(int left, int inner, int bottom) {
        if (actions.isEmpty()) {
            return List.of();
        }
        int[] widths = new int[actions.size()];
        int total = GAP * (actions.size() - 1);
        for (int i = 0; i < widths.length; i++) {
            widths[i] = Math.max(BUTTON_MIN, TextMeasure.width(actions.get(i).label) + BUTTON_PAD);
            total += widths[i];
        }
        boolean squeezed = total > inner;
        int share = squeezed ? Math.max(1, (inner - GAP * (actions.size() - 1)) / actions.size()) : 0;
        int bx = squeezed ? left : left + inner - total;
        List<ButtonComponent> built = new ArrayList<>();
        for (int i = 0; i < widths.length; i++) {
            int width = squeezed ? share : widths[i];
            Action action = actions.get(i);
            ButtonComponent button = new ButtonComponent(new Box("action" + i, bx, bottom - BUTTON_H, width, BUTTON_H), action.label, () -> press(action));
            button.role(action.role);
            built.add(button);
            bx += width + GAP;
        }
        return built;
    }

    private List<String> fitTitle(int width) {
        List<String> all = TextMeasure.wrapAll(title, width);
        if (all.size() <= 1) {
            return all;
        }
        return List.of(TextMeasure.draw().ellipsize(title, width));
    }

    private List<String> wrap(int width) {
        return message.isEmpty() ? List.of() : TextMeasure.wrapAll(message, width);
    }

    private int flowHeight(List<String> lines) {
        int height = lines.size() * LINE;
        if (content != null) {
            height += content.h + (lines.isEmpty() ? 0 : GAP);
        }
        return height;
    }

    private void syncActive() {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).active = active;
        }
    }

    private boolean ready() {
        if (body == null) {
            return false;
        }
        layout();
        return open;
    }

    private void press(Action action) {
        close();
        if (action.onPress != null) {
            action.onPress.run();
        }
    }

    private float offsetNow() {
        return (1F - shown.value()) * reach();
    }

    private int dx() {
        int off = Math.round(offsetNow());
        return side == SurfaceSide.LEFT ? -off : side == SurfaceSide.RIGHT ? off : 0;
    }

    private int dy() {
        int off = Math.round(offsetNow());
        return side == SurfaceSide.TOP ? -off : side == SurfaceSide.BOTTOM ? off : 0;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        settle();
        if (!visible || body == null) {
            return;
        }
        layout();
        if (open && modal && host != null && host.focus() != this) {
            host.focus(this);
        }
        Palette p = t.palette();
        FrameStyle frame = t.frame();
        float progress = shown.value();
        d.nextLayer();
        if (modal) {
            int alpha = Math.round(SurfaceLook.DIM_ALPHA * Math.max(0F, Math.min(1F, progress)));
            d.fill(x, y, x + w, y + h, (alpha << 24) | (p.shadow() & 0xFFFFFF));
            d.nextLayer();
        }
        int ox = dx();
        int oy = dy();
        boolean live = open && !shown.moving() && !dragging && active;
        body.use(t);
        d.scissor(x, y, x + w, y + h);
        d.push(ox, oy, 1F);
        drawSheet(d, t, frame);
        if (showsHandle && sheet.w() > 0 && sheet.h() > 0) {
            drawGrip(d, p, live && handleBox().contains(mx, my));
        }
        drawTitle(d, p);
        if (showsClose && sheet.w() >= CLOSE + 2 * PAD && sheet.h() >= CLOSE + PAD) {
            drawClose(d, t, p, live && closeBox().contains(mx, my));
        }
        body.draw(d, live ? mx : OFF_POINTER, live ? my : OFF_POINTER, delta);
        d.pop();
        d.endScissor();
    }

    private void drawSheet(Draw d, Theme t, FrameStyle frame) {
        int sx = sheet.x();
        int sy = sheet.y();
        int sw = sheet.w();
        int sh = sheet.h();
        switch (side) {
            case LEFT -> {
                sx -= OVERSCAN;
                sw += OVERSCAN;
                sy -= OVERSCAN;
                sh += 2 * OVERSCAN;
            }
            case RIGHT -> {
                sw += OVERSCAN;
                sy -= OVERSCAN;
                sh += 2 * OVERSCAN;
            }
            case TOP -> {
                sy -= OVERSCAN;
                sh += OVERSCAN;
                sx -= OVERSCAN;
                sw += 2 * OVERSCAN;
            }
            default -> {
                sh += OVERSCAN;
                sx -= OVERSCAN;
                sw += 2 * OVERSCAN;
            }
        }
        t.frame().popup(d, t.palette(), sx, sy, sw, sh);
    }

    private void drawGrip(Draw d, Palette p, boolean hover) {
        Box strip = handleBox();
        int under = p.surface() | 0xFF000000;
        int ink = Ink.on(p, under);
        int color = dragging ? p.primary() | 0xFF000000 : hover ? ink : Shapes.alpha(ink, GRIP_ALPHA);
        if (side.vertical()) {
            int len = Math.min(GRIP_LENGTH, Math.max(0, strip.w() - 4));
            int gx = strip.x() + (strip.w() - len) / 2;
            int gy = strip.y() + (strip.h() - GRIP_THICK) / 2;
            d.fill(gx, gy, gx + len, gy + GRIP_THICK, color);
        } else {
            int len = Math.min(GRIP_LENGTH, Math.max(0, strip.h() - 4));
            int gx = strip.x() + (strip.w() - GRIP_THICK) / 2;
            int gy = strip.y() + (strip.h() - len) / 2;
            d.fill(gx, gy, gx + GRIP_THICK, gy + len, color);
        }
    }

    private void drawTitle(Draw d, Palette p) {
        if (titleLines.isEmpty()) {
            return;
        }
        int under = p.surface() | 0xFF000000;
        d.text(titleLines.get(0), sheet.x() + insetStart() + PAD, sheet.y() + insetTop() + PAD, active ? Ink.on(p, under) : p.disabledText());
    }

    private void drawClose(Draw d, Theme t, Palette p, boolean over) {
        Box c = closeBox();
        int under = p.surface() | 0xFF000000;
        int ink = active ? Ink.on(p, under) : p.disabledText();
        if (over) {
            d.fill(c.x(), c.y(), c.right(), c.bottom(), Shapes.alpha(ink, closeHeld ? CLOSE_HELD : CLOSE_HOVER));
        }
        IconArt art = t.icon(Icon.CLOSE);
        art.draw(d, c.x() + (CLOSE - art.width()) / 2, c.y() + (CLOSE - art.height()) / 2, over ? ink : active ? Ink.soft(p, under) : ink, 1F);
    }

    /** The panel must not ring the whole area; the sheet's own panel draws the ring on its stops. */
    @Override
    protected boolean drawFocus(Draw d, Theme t) {
        return true;
    }

    @Override
    protected Box focusBox() {
        return sheet;
    }

    @Override
    public void tick() {
        settle();
        if (visible && body != null) {
            body.tickAll();
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!open || body == null) {
            return null;
        }
        layout();
        boolean onSheet = sheet.contains(mx, my);
        if (!onSheet) {
            return modal ? List.of() : null; // empty, not null: a panel goes on to the widgets beneath on null
        }
        if (!active || shown.moving() || dragging) {
            return List.of();
        }
        List<String> inner = body.tooltipAt(mx, my);
        return inner != null ? inner : List.of();
    }

    // ---- input ----

    private boolean onSheetNow(double mx, double my) {
        return sheet.contains(mx - dx(), my - dy());
    }

    @Override
    public boolean isOver(double mx, double my) {
        if (body == null || !visible) {
            return false;
        }
        layout();
        return (open && modal) || onSheetNow(mx, my);
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
        return open && !leaving ? List.of(this) : List.of();
    }

    @Override
    protected boolean capturesKeys() {
        return open && body != null && body.capturing();
    }

    @Override
    public boolean keyCaptured(KeyPress press) {
        return open && active && body != null && body.rawKey(press);
    }

    @Override
    protected boolean activate() {
        return ready() && active && body.keyPressed(InputKey.ENTER, false);
    }

    private double along(double mx, double my) {
        return side.vertical() ? my : mx;
    }

    /** Towards the edge it came from, in pixels. */
    private double towardsEdge(double delta) {
        return side == SurfaceSide.LEFT || side == SurfaceSide.TOP ? -delta : delta;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!ready()) {
            // A sheet on its way out still covers what it covers, but has nothing to press.
            return visible && !modal && onSheetNow(mx, my);
        }
        boolean onSheet = sheet.contains(mx, my);
        if (shown.moving() || !active) {
            return modal || onSheet;
        }
        if (onSheet || body.overlay() != null) {
            if (button == 0 && showsHandle && handleBox().contains(mx, my)) {
                startDrag(mx, my);
                return true;
            }
            if (button == 0 && showsClose && closeBox().contains(mx, my)) {
                closeHeld = true;
                return true;
            }
            body.mouseClicked(mx, my, button);
            return true;
        }
        if (!modal) {
            return false;
        }
        if (closeOnBackdrop && button == 0) {
            BaseComponent.click();
            close();
        }
        return true;
    }

    private void startDrag(double mx, double my) {
        dragging = true;
        dragStart = along(mx, my);
        dragShown = shown.value();
        lastPos = dragStart;
        lastTime = clock.getAsLong();
        speed = 0F;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double ddx, double ddy) {
        if (!dragging) {
            if (!ready()) {
                return false;
            }
            if (!shown.moving() && active) {
                return body.mouseDragged(mx, my, button, ddx, ddy) || modal;
            }
            return modal;
        }
        double pos = along(mx, my);
        double moved = towardsEdge(pos - dragStart);
        // Never further in than it rests: a sheet has nothing behind it to show.
        shown.snap(Math.max(0F, Math.min(1F, dragShown - (float) (moved / Math.max(1, reach())))));
        long now = clock.getAsLong();
        if (now > lastTime) {
            speed = (float) (towardsEdge(pos - lastPos) / (now - lastTime));
            lastTime = now;
            lastPos = pos;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            BaseComponent.click();
            boolean fresh = clock.getAsLong() - lastTime <= FLICK_FRESH_MS;
            if (1F - shown.value() >= CLOSE_AT || (fresh && speed >= FLICK)) {
                close();
            } else {
                shown.go(1F);
            }
            return true;
        }
        if (closeHeld) {
            closeHeld = false;
            if (open && active && closeBox().contains(mx, my)) {
                BaseComponent.click();
                close();
            }
            return true;
        }
        if (!ready()) {
            return false;
        }
        return body.mouseReleased(mx, my, button) || modal;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!ready()) {
            return false;
        }
        if (!shown.moving() && sheet.contains(mx, my)) {
            body.mouseScrolled(mx, my, amount);
            return true;
        }
        return modal || sheet.contains(mx, my);
    }

    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        if (!ready()) {
            return false;
        }
        if (body.capturing()) {
            if (active) {
                body.keyPressed(key, shift);
            }
            return true;
        }
        if (key == InputKey.ESCAPE) {
            if (body.overlay() != null) {
                body.closeOverlay();
            } else if (active) {
                BaseComponent.click();
                close();
            }
            return true;
        }
        if (!active) {
            return modal;
        }
        if (key == InputKey.TAB && !modal && body.atEnd(shift)) {
            // On to the panel's next widget. A panel sorts its stops by place, and this box is the whole area, so
            // with no cursor yet it would land right back here: it is no stop while the panel moves on.
            host.focus(null);
            leaving = true;
            host.keyPressed(key, shift);
            leaving = false;
            return true;
        }
        if (scroll != null && (key == InputKey.UP || key == InputKey.DOWN || key == InputKey.HOME || key == InputKey.END) && !(contentHasKeys())) {
            switch (key) {
                case UP -> scroll.setScroll(scroll.scroll() - LINE);
                case DOWN -> scroll.setScroll(scroll.scroll() + LINE);
                case HOME -> scroll.setScroll(0);
                default -> scroll.setScroll(scroll.contentHeight());
            }
            return true;
        }
        boolean used = body.keyPressed(key, shift);
        return used || modal;
    }

    private boolean contentHasKeys() {
        return content != null && !content.stops().isEmpty();
    }

    @Override
    public boolean charTyped(int codePoint) {
        if (!ready()) {
            return false;
        }
        if (active) {
            body.charTyped(codePoint);
        }
        return true;
    }

    // ---- parts ----

    private static final class Action {
        final String label;
        final ButtonComponent.Role role;
        final Runnable onPress;

        Action(String label, ButtonComponent.Role role, Runnable onPress) {
            this.label = label;
            this.role = role;
            this.onPress = onPress;
        }
    }

    /** The message: lines already wrapped to the width they sit in. */
    private static final class TextBlock extends BaseComponent {
        private final List<String> lines;

        TextBlock(Box box, List<String> lines) {
            super(box);
            this.lines = lines;
        }

        @Override
        protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
            Palette p = t.palette();
            int ink = Ink.soft(p, p.surface() | 0xFF000000);
            int line = y;
            for (int i = 0; i < lines.size(); i++) {
                d.text(lines.get(i), x, line, ink);
                line += LINE;
            }
        }
    }
}
