package dev.dryaides.minuit;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopFilterTest {

    private static final Product BREAD = Product.of("bread", "Bread", null, 12).stock(40).category("Food");
    private static final Product APPLE = Product.of("apple", "Golden apple", null, 250).stock(3).category("Food");
    private static final Product SWORD = Product.of("sword", "Iron sword", null, 120).sale(90).category("Tools");
    private static final Product PICK = Product.of("pick", "diamond pickaxe", null, 1250).stock(0).category("Tools");
    private static final List<Product> SHELF = List.of(BREAD, APPLE, SWORD, PICK);

    private static List<String> ids(List<Product> products) {
        return products.stream().map(Product::id).toList();
    }

    /** A shopper with 200 coins who wants what is in stock: the sold-out pick and the 250 apple drop out. */
    @Test
    void inStockAndAffordableUseTheSalePriceAndTheBalance() {
        ShopFilter filter = new ShopFilter();
        filter.inStockOnly = true;
        filter.affordableOnly = true;
        assertEquals(List.of("bread", "sword"), ids(filter.apply(SHELF, 200)));
        assertEquals(List.of("bread", "apple", "sword"), ids(filter.apply(SHELF, 250)));
    }

    /** Prices as the shopper pays them: bread 12, sword 90 on sale (not 120), apple 250, pick 1250. */
    @Test
    void priceOrdersGoByTheCurrentPriceAndNameIgnoresCase() {
        ShopFilter filter = new ShopFilter();
        filter.sort = ShopFilter.Sort.PRICE_UP;
        assertEquals(List.of("bread", "sword", "apple", "pick"), ids(filter.apply(SHELF, 0)));
        filter.sort = ShopFilter.Sort.PRICE_DOWN;
        assertEquals(List.of("pick", "apple", "sword", "bread"), ids(filter.apply(SHELF, 0)));
        filter.sort = ShopFilter.Sort.NAME;
        assertEquals(List.of("bread", "pick", "apple", "sword"), ids(filter.apply(SHELF, 0)));
    }

    /** "apple" hits only the golden apple; "tools iron" needs both words, in the category and the name. */
    @Test
    void queryNeedsEveryWordInNameOrCategoryIgnoringCase() {
        ShopFilter filter = new ShopFilter();
        assertFalse(filter.active());
        filter.query = "APPLE";
        assertEquals(List.of("apple"), ids(filter.apply(SHELF, 0)));
        filter.query = "tools iron";
        assertEquals(List.of("sword"), ids(filter.apply(SHELF, 0)));
        filter.query = "zzz";
        assertTrue(filter.apply(SHELF, 0).isEmpty());
        assertTrue(filter.active());
    }
}
