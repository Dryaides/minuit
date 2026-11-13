package bench.api;

import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.HeldKeys;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.TextMeasure;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.Words;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

/**
 * What a demo's script sees: add widgets, then play input. Time only moves when the script says so
 * (or when the renderer lets animations settle), so every picture comes out the same.
 */
public final class Scene {
    /** The simulated time, shared with BaseComponent.clock() while rendering. */
    public static final class Sim implements LongSupplier, HeldKeys {
        public long now = 1_000_000L;
        public boolean ctrl;
        public boolean shift;
        @Override public long getAsLong() { return now; }
        @Override public boolean ctrl() { return ctrl; }
        @Override public boolean shift() { return shift; }
    }

    private static final int FRAME_MS = 16;
    private static final int TICK_MS = 50;

    private final Theme theme;
    private final Words words;
    private final ScenePanel panel;
    private final Sim sim;
    private final Draw sink = TextMeasure.draw();
    private int mx = -10_000;
    private int my = -10_000;
    private int settleMs = 500;
    private long sinceTick;
    private boolean hasCaption;

    /** @param sim null in game, where the real clock runs and waitMs only ticks */
    public Scene(Theme theme, Words words, int width, int height, Sim sim) {
        this.theme = theme;
        this.words = words;
        this.sim = sim;
        this.panel = new ScenePanel(theme, words, width, height);
    }

    // ---- building ----

    public Theme theme() { return theme; }
    public Palette palette() { return theme.palette(); }
    public Words words() { return words; }
    public ScenePanel panel() { return panel; }

    private boolean showcase;

    /** Marks the scene as a site or gallery picture: {@link #fixture} widgets stay out of it. */
    public Scene showcase() { showcase = true; return this; }

    /** A test widget around the component (a button behind a dialog); left out of a showcase picture. */
    public <W extends BaseComponent> W fixture(W widget) {
        return showcase ? widget : panel.put(widget);
    }

    /** Adds a widget to the scene's panel, like PanelComponent.add. */
    public <W extends BaseComponent> W add(W widget) {
        return panel.put(widget);
    }

    /** Draws by hand with the theme: for primitives that are not widgets yet. */
    public void paint(BiConsumer<Draw, Theme> painter) {
        add(new BaseComponent(0, 0, panel.width(), panel.height()) {
            @Override protected void render(Draw d, Theme t, int x, int y, boolean hovered) {
                painter.accept(d, t);
            }
        });
    }

    /** No panel surface behind the widgets: they sit on the backdrop. */
    public void noSurface() { panel.surface = false; }

    /** Height of the bottom band the caption line keeps clear: the panel frame is up to 7 rows and the glyph cell 8. */
    public static final int CAPTION_BAND = 16;

    /** The caption band in {@code frame}: a thick bottom lip (vanilla, thick-lip) widens it so the line clears the rim by 2. */
    static int captionBand(dev.dryaides.minuit.FrameStyle frame) {
        return Math.max(CAPTION_BAND, 9 + frame.panelInsets().bottom() + frame.panelShadow() + 2);
    }

    /** A status line at the panel bottom, above the frame. Demos size their stage so widgets end above it. */
    public void caption(java.util.function.Supplier<String> text) {
        hasCaption = true;
        paint((d, t) -> {
            int left = Math.max(6, t.frame().panelInsets().left() + 4);
            int widgets = Integer.MAX_VALUE;
            for (BaseComponent w : panel.widgets()) {
                if (w.visible && w.w > 0 && w.w < panel.width() && w.x >= left) {
                    widgets = Math.min(widgets, w.x);
                }
            }
            if (widgets <= 12) {
                left = widgets; // in line with the widgets above it
            }
            d.textFit(text.get(), left, panel.height() - captionBand(t.frame()), panel.width() - 2 * left, t.palette().text());
        });
    }

    private boolean keepHeight;

    /** The declared height stays: for a demo whose popup fills a panel-wide host, which {@link #fitHeight} cannot measure. */
    public void keepHeight() { keepHeight = true; }

    /**
     * The panel height that leaves the same margin under the last widget as above the first, counted on
     * the widgets as they stand after the script (an open popup counts). A caption line adds its band.
     * The margin is the top one, at most 8 (a demo that leaves room above for a tooltip keeps its room),
     * and at least the frame's bottom lip and shadow plus 2, so a thick-lipped family keeps the last widget off its rim.
     * Returns -1 when a widget touches the top edge (a full-bleed demo), a modal is open or {@link #keepHeight} was called: the declared height stays.
     */
    public int fitHeight() {
        if (keepHeight) {
            return -1;
        }
        int top = Integer.MAX_VALUE;
        int bottom = 0;
        for (dev.dryaides.minuit.BaseComponent w : panel.widgets()) {
            String kind = w.getClass().getSimpleName();
            if (w instanceof dev.dryaides.minuit.LightboxComponent lb && !lb.isOpen()) {
                continue; // a closed viewer waits for a click; the grid under it sets the height
            }
            if ((kind.equals("DialogComponent") || kind.equals("DrawerComponent") || kind.equals("LightboxComponent")
                    || kind.equals("CommandPaletteComponent"))) {
                return -1;
            }
            if (!w.visible || w.w <= 0 || w.h <= 0 || (w.x <= 0 && w.y <= 0 && w.w >= panel.width() && w.h >= panel.height())) {
                continue;
            }
            top = Math.min(top, w.y);
            bottom = Math.max(bottom, w.y + w.h);
        }
        if (top < 4 || top == Integer.MAX_VALUE) {
            return -1;
        }
        dev.dryaides.minuit.FrameStyle frame = theme.frame();
        int margin = Math.max(Math.min(top, 8), frame.panelInsets().bottom() + frame.panelShadow() + 2);
        return hasCaption ? bottom + 4 + captionBand(frame) : bottom + margin;
    }

    /** Shows a widget above the rest, like a panel's overlay (an open dropdown's popup). */
    public void overlay(BaseComponent widget) { panel.showOverlay(widget); }

    /** How long animations run after the script ends, before the picture. Default 500 ms. */
    public void settle(int ms) { settleMs = ms; }

    // ---- input ----

    public void move(int x, int y) { mx = x; my = y; frame(); }
    public void press(int x, int y) { press(x, y, 0); }
    public void press(int x, int y, int button) { move(x, y); panel.mouseClicked(x, y, button); frame(); }
    public void release(int x, int y) { release(x, y, 0); }
    public void release(int x, int y, int button) { move(x, y); panel.mouseReleased(x, y, button); frame(); }
    public void click(int x, int y) { press(x, y); release(x, y); }
    public void rightClick(int x, int y) { press(x, y, 1); release(x, y, 1); }

    /** Presses at the start, moves to the end and keeps holding. Follow with release() to let go. */
    public void hold(int x1, int y1, int x2, int y2) {
        press(x1, y1);
        int steps = Math.max(4, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) / 4);
        int px = x1;
        int py = y1;
        for (int i = 1; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            mx = x;
            my = y;
            panel.mouseDragged(x, y, 0, x - px, y - py);
            frame();
            px = x;
            py = y;
        }
    }

    public void drag(int x1, int y1, int x2, int y2) { hold(x1, y1, x2, y2); release(x2, y2); }
    /** Wheel at a point: positive is up, as in game. */
    public void scroll(int x, int y, double amount) { move(x, y); panel.mouseScrolled(x, y, amount); frame(); }
    public void key(InputKey key) { key(key, false); }
    /** A key, as a panel gets it. The boolean is true while shift is held. */
    public void key(InputKey key, boolean shift) { panel.keyPressed(key, shift); frame(); }
    public void type(String text) { text.codePoints().forEach(panel::charTyped); frame(); }
    public void focus(BaseComponent widget) { panel.focus(widget); frame(); }
    public void tick(int ticks) { for (int i = 0; i < ticks; i++) panel.tickAll(); }

    /** Holds ctrl and shift for the widgets that read BaseComponent.heldKeys(). */
    public void held(boolean ctrl, boolean shift) {
        if (sim != null) { sim.ctrl = ctrl; sim.shift = shift; }
    }

    /** Lets time pass: frames run, and ticks fire every 50 ms as in game. */
    public void waitMs(int ms) {
        if (sim == null) { tick(ms / TICK_MS); return; }
        for (int t = 0; t < ms; t += FRAME_MS) {
            sim.now += FRAME_MS;
            sinceTick += FRAME_MS;
            while (sinceTick >= TICK_MS) { sinceTick -= TICK_MS; panel.tickAll(); }
            frame();
        }
    }

    public int mouseX() { return mx; }
    public int mouseY() { return my; }
    public int settleMs() { return settleMs; }

    private void frame() {
        panel.draw(sink, mx, my, 1F);
        panel.drawTooltip(sink, mx, my);
    }
}
