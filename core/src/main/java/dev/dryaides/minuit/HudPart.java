package dev.dryaides.minuit;

import java.util.function.BooleanSupplier;

/**
 * One overlay on the game's HUD, drawn every frame outside any screen by {@link HudLayer}: a bar, a counter, a
 * small panel, or any component through {@link HudWidget}. It wears the theme the player picked for
 * {@link #modId}. The layer places it from its {@link HudAnchor} and offset, clear of the vanilla HUD and of the
 * parts placed before it.
 *
 * <p>Render thread only. Set the anchor, offset and z order before {@link HudLayer#add}; {@link #at} may move
 * it later.
 */
@Incubating
public abstract class HudPart {

    private static final BooleanSupplier ALWAYS = () -> true;

    public final String modId;
    public final String id;
    final String key;
    HudAnchor anchor;
    int dx;
    int dy;
    int z;
    BooleanSupplier visible = ALWAYS;
    protected int w;
    protected int h;

    int x;
    int y;
    boolean shown;
    boolean resized = true;
    Theme theme;
    private int fontSeen = -1;

    protected HudPart(String modId, String id, HudAnchor anchor, int w, int h) {
        this.modId = modId;
        this.id = id;
        this.key = "hud/" + modId + ":" + id;
        this.anchor = anchor;
        this.w = w;
        this.h = h;
    }

    /** Moves the part to {@code anchor}, {@code dx} and {@code dy} GUI pixels from its origin (right and down). */
    public HudPart at(HudAnchor anchor, int dx, int dy) {
        this.anchor = anchor;
        this.dx = dx;
        this.dy = dy;
        this.resized = true;
        return this;
    }

    /** Draw order among parts, higher on top; set before {@link HudLayer#add}. */
    public HudPart z(int order) {
        this.z = order;
        return this;
    }

    /** Shows the part only while {@code when} answers true; asked once per frame. */
    public HudPart visibleWhen(BooleanSupplier when) {
        this.visible = when == null ? ALWAYS : when;
        return this;
    }

    /** Changes the size; the layer places the parts again on the next frame. */
    protected final void size(int width, int height) {
        if (width != this.w || height != this.h) {
            this.w = width;
            this.h = height;
            this.resized = true;
        }
    }

    /**
     * Once per frame while visible, before placing and drawing: read the game's state, measure text that changed.
     * Called often: allocate nothing unless something changed.
     */
    protected void update(Draw d, float partial) {
    }

    /** True once after the game's font changed (and on the first call): measured text must be measured again. */
    protected final boolean fontChanged() {
        int now = TextMeasure.fontGeneration();
        if (now == this.fontSeen) {
            return false;
        }
        this.fontSeen = now;
        return true;
    }

    /** Draws the part with its top left at ({@code x}, {@code y}), whole GUI pixels. */
    protected abstract void draw(Draw d, Theme theme, int x, int y, float partial);

    public HudAnchor anchor() {
        return this.anchor;
    }

    /** Where the last layout put it. */
    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public int width() {
        return this.w;
    }

    public int height() {
        return this.h;
    }

    /** Whether it drew on the last frame. */
    public boolean shown() {
        return this.shown;
    }
}
