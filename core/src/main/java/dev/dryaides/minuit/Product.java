package dev.dryaides.minuit;

/**
 * One thing a shop sells. Prices are whole units of the shop's {@link Currency}.
 *
 * <pre>{@code
 * Product bread = Product.of("bread", "Bread", Picture.item("minecraft:bread"), 12).stock(40).category("food");
 * Product sword = Product.of("sword", "Iron sword", Picture.item("minecraft:iron_sword"), 120).sale(90);
 * }</pre>
 *
 * @param id        the mod's key for it, sent back with a purchase
 * @param picture   an item, a sprite or an image; may be null
 * @param price     the full price, 0 or more
 * @param salePrice the price while on sale, below {@code price}; -1 when not on sale
 * @param stock     how many are left; -1 when there is no limit
 * @param category  the tab or filter it belongs to; may be empty
 */
@Incubating
public record Product(String id, String name, Picture picture, long price, long salePrice, int stock, String category) {

    public static final int UNLIMITED = -1;
    public static final long NO_SALE = -1;

    public Product {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        category = category == null ? "" : category;
        price = Math.max(0, price);
        salePrice = salePrice < 0 || salePrice >= price ? NO_SALE : salePrice;
        stock = stock < 0 ? UNLIMITED : stock;
    }

    public static Product of(String id, String name, Picture picture, long price) {
        return new Product(id, name, picture, price, NO_SALE, UNLIMITED, "");
    }

    /** On sale at this price; a price not below the full one, or -1, ends the sale. */
    public Product sale(long salePrice) {
        return new Product(id, name, picture, price, salePrice, stock, category);
    }

    /** This many left; -1 for no limit. */
    public Product stock(int stock) {
        return new Product(id, name, picture, price, salePrice, stock, category);
    }

    public Product category(String category) {
        return new Product(id, name, picture, price, salePrice, stock, category);
    }

    public boolean onSale() {
        return salePrice != NO_SALE;
    }

    /** What one costs now. */
    public long unitPrice() {
        return onSale() ? salePrice : price;
    }

    public boolean soldOut() {
        return stock == 0;
    }

    public boolean limited() {
        return stock != UNLIMITED;
    }

    /** The sale's cut in whole percent, rounded down so a shop never promises more than it gives; 0 off sale. */
    public int salePercent() {
        if (!onSale() || price == 0) {
            return 0;
        }
        long cut = price - salePrice;
        return (int) (price <= Long.MAX_VALUE / 100 ? cut * 100 / price : cut / (price / 100));
    }
}
