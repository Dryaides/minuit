package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * What a shopper narrowed a product grid to: a search text, a sort order and two switches. The filter never
 * changes the catalogue, it picks and orders the products to show.
 */
@Incubating
public final class ShopFilter {

    /** The orders on offer; {@code FEATURED} keeps the catalogue's own. */
    public enum Sort {
        FEATURED, PRICE_UP, PRICE_DOWN, NAME
    }

    public String query = "";
    public Sort sort = Sort.FEATURED;
    public boolean inStockOnly;
    public boolean affordableOnly;

    /** True when any control narrows or reorders the grid. */
    public boolean active() {
        return !this.query.isBlank() || this.sort != Sort.FEATURED || this.inStockOnly || this.affordableOnly;
    }

    /**
     * The products that pass, in the chosen order. A product matches the query when every word of it is found,
     * ignoring case, in its name or category; affordable means the current price is within {@code balance}.
     */
    public List<Product> apply(List<Product> products, long balance) {
        String[] words = this.query.toLowerCase(Locale.ROOT).trim().split("\s+");
        List<Product> kept = new ArrayList<>();
        for (Product p : products) {
            if (this.inStockOnly && p.soldOut()) {
                continue;
            }
            if (this.affordableOnly && p.unitPrice() > balance) {
                continue;
            }
            String haystack = (p.name() + " " + p.category()).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) {
                all &= haystack.contains(w);
            }
            if (all) {
                kept.add(p);
            }
        }
        switch (this.sort) {
            case PRICE_UP -> kept.sort(Comparator.comparingLong(Product::unitPrice));
            case PRICE_DOWN -> kept.sort(Comparator.comparingLong(Product::unitPrice).reversed());
            case NAME -> kept.sort(Comparator.comparing(Product::name, String.CASE_INSENSITIVE_ORDER));
            case FEATURED -> { }
        }
        return kept;
    }
}
