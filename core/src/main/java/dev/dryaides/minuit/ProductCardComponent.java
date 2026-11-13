package dev.dryaides.minuit;

import java.util.List;
import java.util.function.Consumer;

/**
 * A {@link CardComponent} for one {@link Product}: its picture, its name, the stock left, its price with the
 * currency's icon in the footer and one button to buy it or put it in a cart. A sale shows the full price struck
 * through and a "-25%" badge on the picture; a sold out product shows a badge and turns its button off; with
 * {@link #balance(long)} set, a product the player cannot pay for shows its price in the bad colour and its
 * button off, with a tooltip saying how much is missing.
 *
 * <pre>{@code
 * Box[] places = CardComponent.grid(at(new Box("shop", 10, 30, 300, 0)), 3, 6, 96, products.size());
 * for (int i = 0; i < places.length; i++) {
 *     add(new ProductCardComponent(places[i], products.get(i), coins)
 *             .onBuy(words.get("mymod.add"), cart::add1).balance(balance));
 * }
 * }</pre>
 *
 * <p>The card only shows; the buy runs on the server, which checks the price, the stock and the balance again.
 */
@Incubating
public class ProductCardComponent extends CardComponent {
    /** The picture's height. */
    public static final int MEDIA_H = 36;
    private static final int BADGE_INSET = 2;

    /** What the card shows, from the product and the balance. */
    public enum State { AVAILABLE, SOLD_OUT, NOT_ENOUGH_FUNDS }

    private final Currency currency;
    private final BadgeComponent badge = new BadgeComponent(new Box("badge", 0, 0, 0, BadgeComponent.HEIGHT));
    private Product product;
    private long balance = Long.MAX_VALUE;
    private ButtonComponent buy;
    private Words words;
    private Theme lastTheme;

    public ProductCardComponent(Box box, Product product, Currency currency) {
        super(box);
        this.currency = currency == null ? Currency.of("", null) : currency;
        lift(false);
        product(product);
    }

    // ---- setup ----

    /** The product again, with a new price or stock. */
    public ProductCardComponent product(Product product) {
        this.product = product == null ? Product.of("", "", null, 0) : product;
        title(this.product.name());
        if (this.product.picture() != null) {
            picture(this.product.picture(), MEDIA_H);
        }
        return this;
    }

    /** The footer's button: "Buy", "Add to cart". It runs with this card's product. */
    public ProductCardComponent onBuy(String label, Consumer<Product> onBuy) {
        footerButton(label, ButtonComponent.Role.PRIMARY, () -> {
            if (onBuy != null && state() == State.AVAILABLE) {
                onBuy.accept(product);
            }
        });
        buy = buttons().get(buttons().size() - 1);
        return this;
    }

    /** What the player has; a product above it shows as not affordable. Unknown by default. */
    public ProductCardComponent balance(long balance) {
        this.balance = balance;
        return this;
    }

    @Override
    void words(Words words) {
        this.words = words;
    }

    // ---- state ----

    public Product product() {
        return product;
    }

    public State state() {
        if (product.soldOut()) {
            return State.SOLD_OUT;
        }
        return product.unitPrice() > balance ? State.NOT_ENOUGH_FUNDS : State.AVAILABLE;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        lastTheme = t;
        State state = state();
        subtitle(stockLine());
        if (buy != null) {
            footerButtonActive(buttons().indexOf(buy), state == State.AVAILABLE);
            buy.tooltip = state == State.NOT_ENOUGH_FUNDS ? List.of(shortLine()) : null;
        }
        super.render(d, t, mx, my, hovered);
        drawBadge(d, t, mx, my, state);
    }

    private void drawBadge(Draw d, Theme t, int mx, int my, State state) {
        String text;
        BadgeComponent.Variant variant;
        if (state == State.SOLD_OUT) {
            text = BaseComponent.text(null, words, "minuit.shop.sold_out", "Sold out");
            variant = BadgeComponent.Variant.SECONDARY;
        } else if (product.onSale()) {
            text = "-" + Math.max(1, product.salePercent()) + "%";
            variant = BadgeComponent.Variant.BAD;
        } else {
            return;
        }
        Box media = mediaBox();
        if (media.h() < BadgeComponent.HEIGHT + 2 * BADGE_INSET) {
            return;
        }
        badge.text(text).variant(variant).active(active);
        badge.x = media.x() + BADGE_INSET;
        badge.y = media.y() + BADGE_INSET;
        // Over the item, which the game draws in its own pass; popped so the lift does not add up card after card.
        d.push(0F, 0F, 1F);
        d.nextLayer();
        badge.draw(d, t, mx, my, delta);
        d.pop();
    }

    private String stockLine() {
        if (product.soldOut()) {
            return "";
        }
        if (!product.limited()) {
            return product.category().isEmpty() ? "" : product.category();
        }
        return ShopWords.left(words, product.stock());
    }

    private String shortLine() {
        return ShopWords.missing(words, currency, product.unitPrice() - balance);
    }

    @Override
    int footerLeadH() {
        return PriceTag.height(lastTheme, currency);
    }

    @Override
    void drawFooterLead(Draw d, Theme t, int left, int top, int room, int barH, int soft) {
        Palette p = t.palette();
        int under = p.surface() | 0xFF000000;
        int ink;
        if (!active) {
            ink = p.disabledText();
        } else if (state() == State.NOT_ENOUGH_FUNDS) {
            ink = Ink.readable(p, p.bad(), under, Ink.TEXT);
        } else if (state() == State.SOLD_OUT) {
            ink = soft;
        } else {
            ink = Ink.on(p, under);
        }
        int faint = active ? p.textFaint() : p.disabledText();
        PriceTag.product(d, t, currency, product, left, top, barH, ink, faint);
    }
}
