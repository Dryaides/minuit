package dev.dryaides.minuit;

/**
 * An amount with its currency's icon, for any widget that shows a price or a balance. The icon is drawn at its own
 * size, never scaled: a band shorter than the icon gets the icon's height from {@link #height}. A glyph icon
 * takes the colour of the amount.
 *
 * <pre>{@code
 * int w = PriceTag.draw(d, theme, coins, 120, left, top, PriceTag.height(theme, coins), palette.text());
 * }</pre>
 */
@Incubating
public final class PriceTag {
    /** Between the icon and the amount, and between a struck full price and the sale price. */
    public static final int GAP = 2;
    private static final int FONT_H = SurfaceLook.FONT_H;

    private PriceTag() {
    }

    /** The band an amount needs: the font's height or the icon's, whichever is taller. */
    public static int height(Theme t, Currency c) {
        return Math.max(FONT_H, c.icon(t).height());
    }

    public static int width(Theme t, Currency c, long amount) {
        return c.icon(t).width() + GAP + textWidth(c.text(amount));
    }

    /** The width of a sale price: the full price struck through, then the icon and the sale price. */
    public static int saleWidth(Theme t, Currency c, long full, long now) {
        return textWidth(c.text(full)) + GAP + 1 + width(t, c, now);
    }

    /** The width of a product's price as {@link #product} draws it. */
    public static int width(Theme t, Currency c, Product product) {
        return product.onSale() ? saleWidth(t, c, product.price(), product.salePrice()) : width(t, c, product.unitPrice());
    }

    /**
     * Draws the icon then the amount, centred in a band {@code h} tall from {@code y}, and returns the width
     * drawn.
     */
    public static int draw(Draw d, Theme t, Currency c, long amount, int x, int y, int h, int ink) {
        IconArt icon = c.icon(t);
        icon.draw(d, x, y + (h - icon.height()) / 2, ink, 1F);
        int textX = x + icon.width() + GAP;
        String text = c.text(amount);
        d.text(text, textX, textY(y, h), ink);
        return textX + textWidth(text) - x;
    }

    /** As {@link #draw}, with the right edge at {@code right}. */
    public static int drawRight(Draw d, Theme t, Currency c, long amount, int right, int y, int h, int ink) {
        int w = width(t, c, amount);
        return draw(d, t, c, amount, right - w, y, h, ink);
    }

    /** The full price struck through in {@code faint}, then the sale price; returns the width drawn. */
    public static int drawSale(Draw d, Theme t, Currency c, long full, long now, int x, int y, int h, int ink, int faint) {
        String was = c.text(full);
        int wasW = textWidth(was);
        int top = textY(y, h);
        d.text(was, x, top, faint);
        d.fill(x - 1, top + 3, x + wasW + 1, top + 4, faint);
        return wasW + GAP + 1 + draw(d, t, c, now, x + wasW + GAP + 1, y, h, ink);
    }

    /** A product's price: struck and sale price when it is on sale, else its price. */
    public static int product(Draw d, Theme t, Currency c, Product product, int x, int y, int h, int ink, int faint) {
        if (product.onSale()) {
            return drawSale(d, t, c, product.price(), product.salePrice(), x, y, h, ink, faint);
        }
        return draw(d, t, c, product.unitPrice(), x, y, h, ink);
    }

    /** The font's last column is the space after the letter: it is not part of the drawn width. */
    private static int textWidth(String text) {
        return Math.max(0, TextMeasure.width(text) - 1);
    }

    private static int textY(int y, int h) {
        return y + (h - FONT_H) / 2 + 1;
    }
}
