package dev.dryaides.minuit;

/**
 * A count with an icon on the HUD: a small panel with a 16 px item icon (or any {@link IconArt}) and a number,
 * {@code "12"} or {@code "12/64"} with a maximum. It widens with the number; the text is built only when the
 * number changes. At or past the maximum the number takes the palette's warn colour.
 *
 * <pre>{@code
 * HudCounter arrows = HudLayer.main().add(new HudCounter("mymod", "arrows", HudAnchor.BOTTOM_RIGHT, "minecraft:arrow"));
 * arrows.count(player.countArrows());
 * }</pre>
 */
@Incubating
public class HudCounter extends HudPart {

    private static final int PAD = 3;
    private static final int ICON = 16;

    private String itemId;
    private IconArt icon;
    private int count;
    private int max;
    private String text = "0";
    private boolean measure = true;
    private int textWidth;

    public HudCounter(String modId, String id, HudAnchor anchor, String itemId) {
        super(modId, id, anchor, PAD + ICON + 3 + 6 + PAD + 1, PAD + ICON + PAD);
        this.itemId = itemId;
    }

    public HudCounter(String modId, String id, HudAnchor anchor, IconArt icon) {
        super(modId, id, anchor, PAD + icon.width() + 3 + 6 + PAD + 1, PAD + Math.max(ICON, icon.height()) + PAD);
        this.icon = icon;
    }

    public HudCounter count(int n) {
        if (n != this.count) {
            this.count = n;
            rebuild();
        }
        return this;
    }

    /** Shows {@code count/max}; 0 for no maximum. */
    public HudCounter max(int most) {
        if (most != this.max) {
            this.max = most;
            rebuild();
        }
        return this;
    }

    public int count() {
        return this.count;
    }

    private void rebuild() {
        this.text = this.max > 0 ? this.count + "/" + this.max : Integer.toString(this.count);
        this.measure = true;
    }

    private int iconWidth() {
        return this.icon != null ? this.icon.width() : ICON;
    }

    @Override
    protected void update(Draw d, float partial) {
        if (fontChanged()) {
            this.measure = true;
        }
        if (this.measure) {
            this.measure = false;
            this.textWidth = d.width(this.text);
            size(PAD + iconWidth() + 3 + this.textWidth + PAD + 1, this.h);
        }
    }

    @Override
    protected void draw(Draw d, Theme theme, int x, int y, float partial) {
        Palette p = theme.palette();
        theme.frame().panel(d, p, x, y, this.w, this.h);
        int inner = this.h - 2 * PAD;
        if (this.icon != null) {
            d.icon(this.icon, x + PAD, y + PAD + (inner - this.icon.height()) / 2, p.text());
        } else {
            d.item(this.itemId, x + PAD, y + PAD + (inner - ICON) / 2);
        }
        int color = this.max > 0 && this.count >= this.max ? p.warn() : p.text();
        d.text(this.text, x + PAD + iconWidth() + 3, y + (this.h - 8) / 2 + 1, color);
    }
}
