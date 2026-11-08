package dev.dryaides.minuit;

/**
 * A card that shows a theme before it is picked: a small window drawn in {@link #look}, with its
 * frame, a title, two lines of text, a sunken box and a button. The outline around it is drawn in
 * the theme of the panel that holds the card, so the selected one reads on any of them.
 */
public final class ThemePreview extends BaseComponent {

    public Theme look;
    public String name;
    public boolean selected;
    private final Runnable action;

    public ThemePreview(Box box, Theme look, String name, Runnable action) {
        super(box);
        this.look = look;
        this.name = name;
        this.action = action;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        paint(d, theme, this.look, this.name, box(), this.hoverAnim, this.selected);
    }

    /**
     * Draws a card for {@code look} in {@code cell}, its ring in {@code theme}: the same card as the
     * widget, for a {@link GridComponent} cell. The ring reaches 2 pixels left, right and above the cell
     * and 4 below it.
     */
    public static void paint(Draw d, Theme theme, Theme look, String name, Box cell, boolean hovered,
                             boolean selected) {
        paint(d, theme, look, name, cell, hovered ? 1F : 0F, selected);
    }

    /** The same card, its hover ring faded by {@code glow}, 0 at rest and 1 under the mouse. */
    public static void paint(Draw d, Theme theme, Theme look, String name, Box cell, float glow,
                             boolean selected) {
        int x = cell.x();
        int y = cell.y();
        int w = cell.w();
        int h = cell.h();
        if (selected || glow > 0F) {
            int faint = theme.palette().textFaint();
            int ring = selected ? theme.palette().primary() : Shapes.mix(Shapes.alpha(faint, 0), faint, glow);
            int r = look.frame().corner();
            Shapes.ring(d, x - 2, y - 2, w + 4, h + 6, r == 0 ? 0 : r + 2, ring);
        }
        Palette p = look.palette();
        FrameStyle frame = look.frame();
        frame.panel(d, p, x, y, w, h);
        int inset = 7;
        int left = x + inset;
        int top = y + inset;
        int right = x + w - inset;
        int side = Math.min(22, (w - 2 * inset) / 3);
        d.textFit(name, left, top, right - left, p.text());
        int lines = right - side - 4 - left;
        d.fill(left, top + 12, left + lines * 3 / 4, top + 14, p.textSoft());
        d.fill(left, top + 17, left + lines / 2, top + 19, p.textFaint());
        frame.well(d, p, right - side, top + 11, side, 7, false);
        d.fill(right - side + 2, top + 13, right - side + side / 2, top + 15, p.accent());
        frame.raised(d, p, right - side, top + 20, side, 8, p.primary(), Shapes.Relief.RAISED);
    }

    /**
     * A chroma as a compact chip for a {@link GridComponent} cell: its surface, text and primary as three
     * swatches, then its name in {@code theme}, the page's own theme. The ring reaches 2 pixels around the
     * cell and 4 under it, like {@link #paint}'s.
     */
    public static void paintPalette(Draw d, Theme theme, Theme look, String name, Box cell, float glow,
                                    boolean selected) {
        Palette page = theme.palette();
        int x = cell.x();
        int y = cell.y();
        int h = cell.h();
        if (selected || glow > 0F) {
            int faint = page.textFaint();
            int ring = selected ? page.primary() : Shapes.mix(Shapes.alpha(faint, 0), faint, glow);
            Shapes.ring(d, x - 2, y - 2, cell.w() + 4, h + 4, 0, ring);
        }
        Palette p = look.palette();
        int[] swatches = {p.surface(), p.text(), p.primary()};
        int side = h - 2;
        int stripW = swatches.length * side + 2;
        d.fill(x, y, x + stripW, y + h, page.wellEdge());
        for (int i = 0; i < swatches.length; i++) {
            int sx = x + 1 + i * side;
            d.fill(sx, y + 1, sx + side, y + h - 1, swatches[i] | 0xFF000000);
        }
        int textX = x + stripW + 4;
        d.textFit(name, textX, y + (h - 8) / 2, x + cell.w() - textX, selected ? page.text() : page.textSoft());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.active || button != 0 || !isOver(mx, my)) {
            return false;
        }
        click();
        this.action.run();
        return true;
    }
}
