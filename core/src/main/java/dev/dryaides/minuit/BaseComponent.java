package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;
import java.util.List;
import java.util.function.LongSupplier;

/** Base of the kit's widgets: position, hover state, tooltip. */
public abstract class BaseComponent {

    /** Set by the screen host; the kit itself never names the sound engine. */
    public static volatile UiSound sound = UiSound.NONE;

    /** Milliseconds for animations; a bench sets its own to draw a slide frame by frame. */
    public static volatile LongSupplier clock = System::currentTimeMillis;

    /** Skips slides and ticks: a widget jumps to its end state. */
    public static volatile boolean reducedMotion;

    /** The motion style of the screen being built or drawn, set by the mod; see {@link MotionStyle}. */
    public static volatile MotionStyle screenMotion = MotionStyle.SUBTLE;

    /** The player's motion style from {@code config/minuit-client.json}, or null when they chose none. */
    public static volatile MotionStyle playerMotion;

    /** Ctrl and Shift as held now, set by the host's input bridge. */
    public static volatile HeldKeys keys = HeldKeys.NONE;

    /** Milliseconds the hover glow takes to come in; it fades out in {@link #HOVER_OUT_MS}. */
    public static final int HOVER_IN_MS = 90;
    public static final int HOVER_OUT_MS = 140;

    public int x;
    public int y;
    public int w;
    public int h;
    public boolean visible = true;
    public boolean active = true;
    public List<String> tooltip;
    /** This widget's motion style over the screen's, or null to follow the screen. */
    public MotionStyle motionStyle;
    /** Holds the keyboard; set by {@link PanelComponent} on a click. */
    public boolean focused;
    /** 0 at rest, 1 under the mouse, eased between on {@link #clock}. */
    protected float hoverAnim;
    private long hoverTime;
    /** The frame time of the draw in progress, for a widget that draws others inside it. */
    protected float delta = 1F;
    private Glide glide;
    private float glideDistance;

    protected BaseComponent(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    protected BaseComponent(Box box) {
        this(box.x(), box.y(), box.w(), box.h());
    }

    public Box box() {
        return new Box(getClass().getSimpleName(), this.x, this.y, this.w, this.h);
    }

    /**
     * Slides this widget, and what it draws inside it, with {@code glide} over {@code distance} px:
     * drawn off its place while it moves, not drawn while away. It takes no pointer and no keyboard
     * stop until it rests at its place, so a click never lands on a moving picture. Null removes it.
     */
    public BaseComponent glide(Glide glide, int distance) {
        this.glide = glide;
        this.glideDistance = distance;
        return this;
    }

    public Glide glide() {
        return this.glide;
    }

    /** False while its glide carries it or keeps it away. */
    protected final boolean settled() {
        return this.glide == null || this.glide.shown() && !this.glide.moving();
    }

    public boolean isOver(double mx, double my) {
        return this.visible && settled() && mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
    }

    public BaseComponent tooltip(String... lines) {
        this.tooltip = lines.length == 0 ? null : List.of(lines);
        return this;
    }

    /** The game draws a tip's box from this far above the point it is given. */
    static final int TIP_RISE = 16;

    /**
     * The height the host lays this widget's tip from, in place of the pointer's: a widget whose tip would hide the
     * things it names returns a height that puts the tip's box under itself.
     */
    public int tooltipY(double mx, double my) {
        return (int) Math.floor(my);
    }

    /**
     * The lines to show while the mouse is at ({@code mx}, {@code my}), or null. {@link PanelComponent} asks
     * every widget and draws the answer; a widget that holds others, or whose tip depends on the part
     * under the mouse, overrides this.
     */
    public List<String> tooltipAt(double mx, double my) {
        return this.tooltip != null && isOver(mx, my) ? this.tooltip : null;
    }

    /** Draws the widget; {@code delta} is the frame time in ticks, for animations. */
    public final void draw(Draw d, Theme theme, int mx, int my, float delta) {
        if (!this.visible || this.glide != null && this.glide.away()) {
            return;
        }
        float gx = this.glide == null ? 0F : this.glide.offsetX(this.glideDistance);
        float gy = this.glide == null ? 0F : this.glide.offsetY(this.glideDistance);
        boolean moved = gx != 0F || gy != 0F;
        if (moved) {
            d.push(gx, gy, 1F);
        }
        this.delta = delta;
        boolean hovered = this.active && isOver(mx, my);
        this.hoverAnim = hoverStep(this.hoverAnim, hovered, clock.getAsLong());
        render(d, theme, mx - Math.round(gx), my - Math.round(gy), hovered);
        if (moved) {
            d.pop();
        }
    }

    /** The hover glow after the time since the last frame; a jump under {@link #reducedMotion}. */
    float hoverStep(float from, boolean hovered, long now) {
        long last = this.hoverTime;
        this.hoverTime = now;
        return glow(from, hovered, last == 0 ? -1 : now - last);
    }

    /**
     * A hover glow {@code dtMs} after it was {@code from}: in over {@link #HOVER_IN_MS}, out over
     * {@link #HOVER_OUT_MS}. It jumps under {@link #reducedMotion} and on a first frame ({@code dtMs < 0}).
     */
    static float glow(float from, boolean hovered, long dtMs) {
        if (reducedMotion || dtMs < 0) {
            return hovered ? 1F : 0F;
        }
        float dt = Maths.clamp(dtMs, 0L, 250L);
        return hovered ? Math.min(1F, from + dt / HOVER_IN_MS) : Math.max(0F, from - dt / HOVER_OUT_MS);
    }

    /** The style this widget moves with now. */
    protected MotionStyle motion() {
        return MotionStyle.resolve(reducedMotion, playerMotion, this.motionStyle, screenMotion);
    }

    protected abstract void render(Draw d, Theme theme, int mx, int my, boolean hovered);

    /** The panel's translations, given when the panel adds this widget; a widget with default texts keeps them. */
    void words(Words words) {
    }

    /**
     * A default text: {@code own} when the mod set one, else the lang entry {@code key}, else {@code english}
     * when no panel gave words or the lang file lacks the key (the bench).
     */
    static String text(String own, Words words, String key, String english) {
        if (own != null) {
            return own;
        }
        if (words != null) {
            String found = words.get(key);
            if (!found.equals(key)) {
                return found;
            }
        }
        return english;
    }

    /** {@code count} names from {@code prefix.1} on; a key the lang file lacks keeps the English name. */
    static String[] names(Words words, String prefix, String[] english) {
        String[] out = english.clone();
        if (words != null) {
            for (int i = 0; i < out.length; i++) {
                String key = prefix + "." + (i + 1);
                String found = words.get(key);
                if (found != null && !found.equals(key)) {
                    out[i] = found;
                }
            }
        }
        return out;
    }

    /** Once per game tick, from the panel holding it; a widget holding others passes it on. */
    public void tick() {
    }

    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    /** True for a widget that takes the keyboard when clicked. */
    protected boolean takesFocus() {
        return false;
    }

    /**
     * True while this widget, once focused, wants the raw key or mouse button rather than the kit's
     * {@link InputKey}: a key binding field waiting for its key.
     */
    protected boolean capturesKeys() {
        return false;
    }

    /** A raw key or mouse button while this widget {@link #capturesKeys}; true when it used it. */
    public boolean keyCaptured(KeyPress key) {
        return false;
    }

    /**
     * True for a widget Tab and the arrows stop on (see {@link PanelComponent#keyPressed}). A widget that
     * takes the keyboard is one; a control that reacts to Enter overrides this.
     */
    protected boolean navigable() {
        return takesFocus();
    }

    /**
     * The places the keyboard stops on in this widget, in reading order. A widget holding others,
     * from any package, overrides this to list its children so Tab and the arrows reach them.
     */
    public List<BaseComponent> stops() {
        return navigable() ? List.of(this) : List.of();
    }

    /**
     * The wells this widget draws over menu slots, screen coordinates; empty for a widget that holds no menu
     * slot. A container screen places a menu slot on the first well that holds its item. A slot part of your
     * own overrides this; the built-in ones list their cells while they hold the menu ({@code holdsMenu}).
     */
    @Incubating
    protected List<SlotLayout.Well> menuWells() {
        return List.of();
    }

    /** The keyboard arrives on this stop, forward by Tab or back by Shift+Tab. */
    protected void enter(boolean forward) {
    }

    /** Enter or Space while the keyboard is on this stop; true when it did something. */
    protected boolean activate() {
        return false;
    }

    /** The box the focus ring goes around, screen coordinates. */
    protected Box focusBox() {
        return box();
    }

    /** {@link #focusBox} in this family, for a widget whose outline the family draws past its box. */
    protected Box focusBox(FrameStyle frame) {
        return focusBox();
    }

    /**
     * Marks this widget as the keyboard's stop its own way, over its last draw; false leaves the panel
     * to ring it.
     */
    protected boolean drawFocus(Draw d, Theme theme) {
        return false;
    }

    /** The corner radius of {@link #focusBox}, which the ring follows. */
    protected int focusCorner() {
        return 3;
    }

    /** {@link #focusCorner} in this family, for a widget whose corners the family draws: the ring is that corner grown. */
    protected int focusCorner(FrameStyle frame) {
        return focusCorner();
    }

    /** A key while this widget is {@link #focused}, or while the keyboard is on this stop; true when it used it. */
    public boolean keyPressed(InputKey key, boolean shift) {
        return false;
    }

    /** A typed character, as a code point, while this widget is {@link #focused}. */
    public boolean charTyped(int codepoint) {
        return false;
    }

    public static void click() {
        sound.click();
    }
}
