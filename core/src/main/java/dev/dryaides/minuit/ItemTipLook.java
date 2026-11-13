package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ContainerTab;

/**
 * A tooltip in a theme's popup: the frame round the lines and the inks that read on it. {@link TipDelay} draws
 * minuit's own tips with it; the game's tooltips wear it too when the player turns on "Theme item tooltips
 * everywhere", through the hooks of each Minecraft version, which call {@link #begin} where the game draws its box,
 * {@link #nextInk} and {@link #keeps} for each text line, and {@link #end} once the tooltip is drawn. Only the box and
 * the text lines change: pictures other mods put in a tooltip (maps, bundles, icons) draw as they always do.
 *
 * <p>Not API: called on the render thread by the kit's own hooks.
 */
@Internal
public final class ItemTipLook {

    /** From the tooltip's lines to the frame's edge, on every side: the game's 3 pixels and its outer line. */
    public static final int PAD = 4;

    private static boolean hooked;
    private static ItemTipLook game;
    private static int gameGeneration = -1;
    private static ItemTipLook open;
    private static int line;

    private final Theme theme;
    private final int face;
    private final int ink;
    private final int soft;

    ItemTipLook(Theme theme) {
        this.theme = theme;
        Palette p = theme.palette();
        // the popup lays a solid backing under a see-through surface, so its words sit on the solid colour
        this.face = p.surface() | 0xFF000000;
        this.ink = Ink.on(p, this.face);
        this.soft = Ink.soft(p, this.face);
    }

    /** Called by the Minecraft versions whose hooks draw the game's tooltips in the theme, before any screen opens. */
    public static void hook() {
        hooked = true;
    }

    /** True when this Minecraft version can draw the game's tooltips in the theme: the picker offers the option. */
    public static boolean hooked() {
        return hooked;
    }

    /**
     * Starts one of the game's tooltips. Returns the look to draw it in, the theme the player picked for minuit's
     * own screens, or null to leave the game's box: when {@code themed} is false (the option is off, or a mod or
     * the item chose its own tooltip box).
     */
    public static ItemTipLook begin(boolean themed) {
        line = 0;
        open = null;
        if (!themed) {
            return null;
        }
        if (game == null || gameGeneration != Themes.generation()) {
            game = new ItemTipLook(Themes.byId(ContainerTab.THEME_ID));
            gameGeneration = Themes.generation();
        }
        open = game;
        return open;
    }

    /** The look of the tooltip being drawn, or null outside a themed one. */
    public static ItemTipLook current() {
        return open;
    }

    /** Ends the tooltip: text drawn after it keeps the game's colours. */
    public static void end() {
        open = null;
        line = 0;
    }

    /** The frame round lines that start at {@code x, y} and fill {@code w} by {@code h}, as the game measures them. */
    public void frame(Draw d, int x, int y, int w, int h) {
        this.theme.frame().popup(d, this.theme.palette(), x - PAD, y - PAD, w + 2 * PAD, h + 2 * PAD);
    }

    /** The ink of the next text line: the first names the thing, the rest is said about it. */
    public int nextInk() {
        return line++ == 0 ? this.ink : this.soft;
    }

    /**
     * True when a line's own colour {@code rgb} reads on the popup (4.5:1). One that does not falls back to the line's
     * ink: the game's colours were chosen for its dark box (white names, grey lore) and vanish on a pale popup, while a
     * rarity colour that still reads keeps its meaning.
     */
    public boolean keeps(int rgb) {
        return reads(rgb, this.face);
    }

    static boolean reads(int rgb, int face) {
        return Shapes.contrast(rgb | 0xFF000000, face | 0xFF000000) >= Ink.TEXT;
    }

    int face() {
        return this.face;
    }

    int ink() {
        return this.ink;
    }

    int soft() {
        return this.soft;
    }
}
