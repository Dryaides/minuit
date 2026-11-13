package dev.dryaides.minuit;

/**
 * What the player has, on a small well: the currency's icon and the amount. With {@link #cost(long)} it also
 * shows what a purchase would take, "-45", and turns the amount to the bad colour when the cost is above it.
 *
 * <pre>{@code
 * BalanceComponent wallet = add(new BalanceComponent(at(new Box("wallet", 200, 6, 0, 16)), coins).amount(balance));
 * wallet.cost(cart.total());
 * }</pre>
 *
 * <p>A box of width 0 takes the width of its content, grown to the left of its right edge with
 * {@link #alignRight(boolean)}. It shows; it takes no input.
 */
@Incubating
public class BalanceComponent extends BaseComponent {
    private static final int PAD = 4;
    private static final int GAP = 4;

    private final Currency currency;
    private final int fixedW;
    private final int right;
    private long amount;
    private long cost;
    private boolean alignRight;

    public BalanceComponent(Box box, Currency currency) {
        super(box);
        this.currency = currency == null ? Currency.of("", null) : currency;
        this.fixedW = box.w();
        this.right = box.x() + box.w();
    }

    public BalanceComponent amount(long amount) {
        this.amount = amount;
        return this;
    }

    /** What a purchase would take from the amount; 0 hides it. */
    public BalanceComponent cost(long cost) {
        this.cost = Math.max(0, cost);
        return this;
    }

    public BalanceComponent alignRight(boolean alignRight) {
        this.alignRight = alignRight;
        return this;
    }

    public long amount() {
        return amount;
    }

    /** False when the cost shown is above the amount. */
    public boolean covers() {
        return cost <= amount;
    }

    /** The width the content needs in a theme. */
    public int naturalWidth(Theme t) {
        int w = PAD + PriceTag.width(t, currency, amount) + PAD;
        if (cost > 0) {
            w += GAP + TextMeasure.width(costText()) - 1;
        }
        return w;
    }

    private String costText() {
        return "-" + currency.text(cost);
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        if (fixedW <= 0) {
            w = naturalWidth(t);
            if (alignRight) {
                x = right - w;
            }
        }
        if (w <= 0 || h <= 0) {
            return;
        }
        Palette p = t.palette();
        t.frame().well(d, p, x, y, w, h, false);
        int under = p.well() | 0xFF000000;
        int ink;
        if (!active) {
            ink = p.disabledText();
        } else if (!covers()) {
            ink = Ink.readable(p, p.bad(), under, Ink.TEXT);
        } else {
            ink = Ink.on(p, under);
        }
        int band = Math.min(h, PriceTag.height(t, currency));
        int top = y + (h - band) / 2;
        d.scissor(x, y, x + w, y + h);
        int drawn = PriceTag.draw(d, t, currency, amount, x + PAD, top, band, ink);
        if (cost > 0) {
            int soft = active ? Ink.soft(p, under) : p.disabledText();
            d.text(costText(), x + PAD + drawn + GAP, top + (band - SurfaceLook.FONT_H) / 2 + 1, soft);
        }
        d.endScissor();
    }
}
