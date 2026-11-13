package dev.dryaides.minuit;

/**
 * The toast after a purchase, as a {@link ToastSpec}: in game {@code client.Toasts.show} puts it in the toast
 * stack, on a panel a {@link ToastComponent} shows it. Show it when the server says the purchase went through,
 * not when the button is pressed.
 *
 * <pre>{@code
 * Toasts.show(ShopToast.bought(words, words.get("mymod.bought"), cart, coins));
 * }</pre>
 */
@Incubating
public final class ShopToast {
    /** One toast key for every purchase: a second one replaces the first instead of stacking. */
    public static final String KEY = "minuit.shop.bought";

    private ShopToast() {
    }

    /** "3 x Bread, 36 Coins", with the product's item or sprite as the icon. */
    public static ToastSpec bought(Words words, String title, Product product, int quantity, Currency currency) {
        String total = currency.text(Cart.lineTotal(new Cart.Line(product, quantity)));
        String body = ShopWords.bought(words, quantity, product.name(), total, currency.name());
        return ToastSpec.of(title, body).icon(iconOf(product)).key(KEY);
    }

    /** One product: as above. Several: the count of things and the total, with the first line's icon. */
    public static ToastSpec bought(Words words, String title, Cart cart, Currency currency) {
        if (cart.lines().size() == 1) {
            Cart.Line line = cart.lines().get(0);
            return bought(words, title, line.product(), line.quantity(), currency);
        }
        String body = ShopWords.boughtMany(words, cart.count(), currency.text(cart.total()), currency.name());
        return ToastSpec.of(title, body).icon(cart.isEmpty() ? null : iconOf(cart.lines().get(0).product())).key(KEY);
    }

    /** A toast icon is 16 px: an item or a sprite fits, a screenshot does not. */
    private static IconArt iconOf(Product product) {
        Picture picture = product.picture();
        return picture == null ? null : picture.art();
    }
}
