package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;

/**
 * A window around a {@link PanelComponent}: drag its title bar to move it, Ctrl+wheel or its corner to
 * scale it, its reset button to put it back. Place and scale are saved in {@link #layouts} under
 * the window's key when a drag ends, never on every frame; the fold is saved with them.
 *
 * <p>Build the content panel at (0, 0): the window moves and scales it. The content receives the
 * mouse in its own coordinates, the wheel unless Ctrl is held, and once the window is clicked the
 * keys, typed text and Tab between its widgets; its tooltips show over the window. Escape gives
 * the keyboard back to the panel holding the window.
 */
public final class WindowComponent extends BaseComponent implements ItemSource, ItemDropTarget {

    /** Set by the client host to the file-backed store; in memory otherwise. */
    public static volatile LayoutStore layouts = LayoutStore.memory();

    private static final int TITLE = 18;
    private static final int TITLE_COMPACT = 13;
    private static final int PAD = 4;
    private static final int GRIP = 6;
    private static final float STEP = 0.05F;

    private final String key;
    private final PanelComponent content;
    private final int homeX;
    private final int homeY;
    private String title = "";
    private boolean movable = true;
    private boolean resizable = true;
    private boolean titled = true;
    private boolean compact;
    private boolean collapsible;
    private boolean collapsed;
    /** The saved fold, worn once the window is made {@link #collapsible}. */
    private boolean savedCollapsed;
    private Runnable onClose;
    private final java.util.List<Box> handles = new java.util.ArrayList<>();
    private int minW;
    private int minH;
    private Box screen;

    private int dx;
    private int dy;
    private float scale = 1F;
    private Drag drag = Drag.NONE;
    private double grabX;
    private double grabY;

    private enum Drag { NONE, MOVE, SCALE }

    /**
     * @param key        where the layout is saved, e.g. {@code "mymod:paint"}
     * @param defaultBox the window's place before the player moves it; its size follows the content
     */
    public WindowComponent(String key, PanelComponent content, Box defaultBox) {
        super(defaultBox);
        this.key = key;
        this.content = content;
        this.homeX = defaultBox.x();
        this.homeY = defaultBox.y();
        this.minW = Math.min(content.width(), 40);
        this.minH = Math.min(content.height(), 30);
        LayoutStore.Layout saved = layouts.get(key);
        if (saved != null) {
            this.dx = saved.dx();
            this.dy = saved.dy();
            this.scale = Float.isFinite(saved.scale()) ? saved.scale() : 1F;
            this.savedCollapsed = saved.collapsed();
        }
        place();
    }

    public WindowComponent title(String text) {
        this.title = text;
        return this;
    }

    public WindowComponent movable(boolean on) {
        this.movable = on;
        return this;
    }

    public WindowComponent resizable(boolean on) {
        this.resizable = on;
        place();
        return this;
    }

    /**
     * False hides the title bar, its title and its reset button: the content starts at the top of
     * the frame, so a tab strip can be the window's top. The window then moves from the
     * {@link #dragHandle} areas only.
     */
    public WindowComponent titleBar(boolean shown) {
        this.titled = shown;
        this.collapsed &= shown;
        place();
        return this;
    }

    /**
     * Marks an area of the content, in content coordinates, that moves the window when a press there
     * is not taken by a widget: the empty part of a tab strip, a grip drawn by the caller.
     */
    public WindowComponent dragHandle(Box contentArea) {
        this.handles.add(contentArea);
        return this;
    }

    /** A thinner title bar with a smaller title, for a tool palette. */
    public WindowComponent compact(boolean on) {
        this.compact = on;
        place();
        return this;
    }

    /** Adds a close button to the title bar, which runs {@code action}. */
    public WindowComponent onClose(Runnable action) {
        this.onClose = action;
        return this;
    }

    /** Adds a button that folds the window to its title bar and back; the saved fold comes back with it. */
    public WindowComponent collapsible(boolean on) {
        this.collapsible = on;
        this.collapsed = on && this.savedCollapsed && this.titled;
        place();
        return this;
    }

    public boolean collapsed() {
        return this.collapsed;
    }

    /** Folds or unfolds the window and saves the fold with its place. */
    public void collapse(boolean folded) {
        this.collapsed = folded && this.titled;
        this.savedCollapsed = this.collapsed;
        place();
        save();
    }

    /** Pixels the frame adds around the content across: the padding on both sides. */
    public static int chromeWidth() {
        return 2 * PAD;
    }

    /**
     * Pixels the frame adds around the content down: title band (or top padding with the bar
     * hidden), and bottom padding, so a caller can size the content to a screen.
     */
    public static int chromeHeight(boolean titleBar, boolean compact) {
        return (titleBar ? compact ? TITLE_COMPACT : TITLE : PAD) + PAD;
    }

    /** The smallest the content may be scaled down to, in pixels. */
    public WindowComponent minSize(int width, int height) {
        this.minW = width;
        this.minH = height;
        place();
        return this;
    }

    /** Keeps the whole window inside {@code bounds}, the screen, also after the game window resizes. */
    public WindowComponent within(Box bounds) {
        this.screen = bounds;
        place();
        return this;
    }


    public float scale() {
        return this.scale;
    }

    private float minScale() {
        return Math.max(0.25F, Math.max(this.minW / (float) contentW(), this.minH / (float) contentH()));
    }

    /** The content's size, at least 1, so an empty panel scales like a 1 px one instead of giving NaN. */
    private int contentW() {
        return Math.max(1, this.content.width());
    }

    private int contentH() {
        return Math.max(1, this.content.height());
    }

    private float maxScale() {
        if (this.screen == null) {
            return 3F;
        }
        float byW = (this.screen.w() - 2 * PAD) / (float) contentW();
        float byH = (this.screen.h() - top() - PAD) / (float) contentH();
        return Math.max(minScale(), Math.min(3F, Math.min(byW, byH)));
    }

    /** Applies scale bounds and keeps the window on screen; sets the widget's box. */
    private void place() {
        if (!this.resizable) {
            this.scale = 1F;
        }
        this.scale = Maths.clamp(this.scale, minScale(), maxScale());
        this.w = Math.round(this.content.width() * this.scale) + 2 * PAD;
        this.h = this.collapsed ? top() : top() + Math.round(this.content.height() * this.scale) + PAD;
        int left = this.homeX + this.dx;
        int top = this.homeY + this.dy;
        if (this.screen != null) {
            left = Maths.clamp(left, this.screen.x(), Math.max(this.screen.x(), this.screen.right() - this.w));
            top = Maths.clamp(top, this.screen.y(), Math.max(this.screen.y(), this.screen.bottom() - this.h));
            this.dx = left - this.homeX;
            this.dy = top - this.homeY;
        }
        this.x = left;
        this.y = top;
    }

    private void save() {
        layouts.put(this.key, new LayoutStore.Layout(this.dx, this.dy, this.scale, this.collapsed));
    }

    /** Back to the default place and scale, saved as such. */
    public void reset() {
        this.dx = 0;
        this.dy = 0;
        this.scale = 1F;
        place();
        save();
    }

    private int contentX() {
        return this.x + PAD;
    }

    private int top() {
        return this.titled ? this.compact ? TITLE_COMPACT : TITLE : PAD;
    }

    private int contentY() {
        return this.y + top();
    }

    private double inX(double mx) {
        return (mx - contentX()) / this.scale;
    }

    private double inY(double my) {
        return (my - contentY()) / this.scale;
    }

    private Box titleBar() {
        return new Box("title", this.x, this.y, this.w, top());
    }

    /** The title bar's buttons from the right: close, fold, reset; slot 0 is the rightmost. */
    private Box button(int slot) {
        int size = 11;
        int inset = this.compact ? 1 : PAD;
        return new Box("button", this.x + this.w - PAD - 2 - (slot + 1) * (size + 2), this.y + inset, size, size);
    }

    private int buttons() {
        return (this.onClose != null ? 1 : 0) + (this.collapsible ? 1 : 0) + 1;
    }

    private Box closeBox() {
        return this.onClose == null ? null : button(0);
    }

    private Box foldBox() {
        return this.collapsible ? button(this.onClose != null ? 1 : 0) : null;
    }

    private Box resetBox() {
        return button(buttons() - 1);
    }

    private static boolean over(Box box, double mx, double my) {
        return box != null && box.contains(mx, my);
    }

    private void titleButton(Draw d, Theme theme, Box box, Icon icon, int mx, int my) {
        if (box == null) {
            return;
        }
        Palette p = theme.palette();
        boolean lit = box.contains(mx, my);
        if (lit) {
            Shapes.rounded(d, box.x(), box.y(), box.w(), box.h(), 2, p.raisedHover());
        }
        int color = lit ? p.raisedText() : p.textSoft();
        if (icon == null) {
            int cy = box.y() + box.h() / 2;
            d.fill(box.x() + 2, cy, box.right() - 2, cy + 1, color);
            if (this.collapsed) {
                d.fill(box.x() + box.w() / 2, box.y() + 2, box.x() + box.w() / 2 + 1, box.bottom() - 2, color);
            }
            return;
        }
        IconArt art = theme.icon(icon);
        art.draw(d, box.x() + (box.w() - art.width()) / 2, box.y() + (box.h() - art.height()) / 2, color, 1F);
    }

    private Box grip() {
        return new Box("grip", this.x + this.w - GRIP, this.y + this.h - GRIP, GRIP, GRIP);
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        Palette p = theme.palette();
        theme.frame().shadow(d, p, this.x + 2, this.y + 2, this.w, this.h, p.shadow());
        theme.frame().panel(d, p, this.x, this.y, this.w, this.h);
        if (this.titled) {
            int inset = this.compact ? 2 : PAD;
            theme.frame().header(d, p, this.x + inset, this.y + inset, this.w - 2 * inset, top() - inset - 1);
            int room = this.w - 2 * PAD - 6 - buttons() * (this.compact ? 10 : 12);
            if (this.compact) {
                d.push(this.x + inset + 3, this.y + 3, 0.75F);
                d.textFit(this.title, 0, 0, (int) (room / 0.75F), p.text());
                d.pop();
            } else {
                d.textFit(this.title, this.x + PAD + 3, this.y + PAD + 3, room, p.text());
            }
            titleButton(d, theme, closeBox(), Icon.CLOSE, mx, my);
            titleButton(d, theme, foldBox(), null, mx, my);
            titleButton(d, theme, resetBox(), Icon.REFRESH, mx, my);
        }
        if (this.collapsed) {
            return;
        }
        d.push(contentX(), contentY(), this.scale);
        this.content.draw(d, (int) Math.floor(inX(mx)), (int) Math.floor(inY(my)), 1F);
        d.pop();
        if (this.resizable) {
            Box g = grip();
            int color = g.contains(mx, my) || this.drag == Drag.SCALE ? p.raisedHover() : p.frameDark();
            for (int i = 0; i < GRIP; i += 2) {
                d.fill(g.right() - 1 - i, g.bottom() - 1, g.right() - i, g.bottom(), color);
                d.fill(g.right() - 1, g.bottom() - 1 - i, g.right(), g.bottom() - i, color);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.visible || !isOver(mx, my)) {
            return false;
        }
        if (this.titled && button == 0) {
            if (over(closeBox(), mx, my)) {
                BaseComponent.click();
                this.onClose.run();
                return true;
            }
            if (over(foldBox(), mx, my)) {
                BaseComponent.click();
                collapse(!this.collapsed);
                return true;
            }
            if (resetBox().contains(mx, my)) {
                BaseComponent.click();
                reset();
                return true;
            }
        }
        if (this.collapsed) {
            if (button == 0 && this.movable && titleBar().contains(mx, my)) {
                grab(mx, my);
            }
            return true;
        }
        if (button == 0 && this.resizable && grip().contains(mx, my)) {
            this.drag = Drag.SCALE;
            return true;
        }
        if (button == 0 && this.movable && this.titled && titleBar().contains(mx, my)) {
            grab(mx, my);
            return true;
        }
        if (!this.content.mouseClicked(inX(mx), inY(my), button) && button == 0 && this.movable) {
            for (Box handle : this.handles) {
                if (handle.contains((int) Math.floor(inX(mx)), (int) Math.floor(inY(my)))) {
                    grab(mx, my);
                    break;
                }
            }
        }
        return true;
    }

    private void grab(double mx, double my) {
        this.drag = Drag.MOVE;
        this.grabX = mx - this.x;
        this.grabY = my - this.y;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double ddx, double ddy) {
        switch (this.drag) {
            case MOVE -> {
                this.dx = (int) Math.round(mx - this.grabX) - this.homeX;
                this.dy = (int) Math.round(my - this.grabY) - this.homeY;
                place();
                return true;
            }
            case SCALE -> {
                this.scale = (float) Math.max((mx - contentX()) / contentW(),
                        (my - contentY()) / contentH());
                place();
                return true;
            }
            default -> {
                return this.content.mouseDragged(inX(mx), inY(my), button, ddx / this.scale, ddy / this.scale);
            }
        }
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (this.drag != Drag.NONE) {
            this.drag = Drag.NONE;
            save();
            return true;
        }
        return this.content.mouseReleased(inX(mx), inY(my), button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!isOver(mx, my)) {
            return false;
        }
        if (this.collapsed) {
            return true;
        }
        // Ctrl+wheel scales the window before the content scrolls; the host reports Ctrl in keys.
        if (this.resizable && keys.ctrl() && amount != 0) {
            this.scale += amount > 0 ? STEP : -STEP;
            place();
            save();
            return true;
        }
        return this.content.mouseScrolled(inX(mx), inY(my), amount);
    }

    /** A click on the window gives it the keyboard, which it hands to its content. */
    @Override
    protected boolean takesFocus() {
        return true;
    }

    /**
     * Keys go to the content, which moves between its own widgets with Tab and the arrows; a key it
     * leaves goes back to the panel holding the window.
     */
    @Override
    public boolean keyPressed(InputKey key, boolean shift) {
        return !this.collapsed && this.content.keyPressed(key, shift);
    }

    @Override
    public boolean charTyped(int codepoint) {
        return !this.collapsed && this.content.charTyped(codepoint);
    }

    @Override
    public ItemView itemAt(double mx, double my) {
        if (!isOver(mx, my) || this.collapsed || my < contentY()) {
            return null;
        }
        return ItemSource.under(this.content, inX(mx), inY(my));
    }

    /** The content's drop targets, moved and scaled onto the screen. */
    @Override
    public java.util.List<Drop> dropTargets() {
        if (this.collapsed) {
            return java.util.List.of();
        }
        java.util.List<Drop> out = new java.util.ArrayList<>();
        for (Drop drop : ItemDropTarget.all(this.content)) {
            Box a = drop.area();
            out.add(new Drop(new Box(a.name(), contentX() + Math.round(a.x() * this.scale), contentY() + Math.round(a.y() * this.scale),
                    Math.round(a.w() * this.scale), Math.round(a.h() * this.scale)),
                    drop.hit() == null ? null : (x, y) -> drop.hit().at(Math.round((x - contentX()) / this.scale),
                            Math.round((y - contentY()) / this.scale)),
                    drop.fits(), drop.accept()));
        }
        return out;
    }

    @Override
    public java.util.List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        if (!this.collapsed && my >= contentY()) {
            java.util.List<String> inner = this.content.tooltipAt(inX(mx), inY(my));
            if (inner != null) {
                return inner;
            }
        }
        return super.tooltipAt(mx, my);
    }

    @Override
    public void tick() {
        this.content.tickAll();
    }
}
