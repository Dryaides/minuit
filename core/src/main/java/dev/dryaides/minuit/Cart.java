package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player is about to buy: lines of a product and a quantity, in the order they were added. It does the
 * shop's sums; {@link CartComponent} shows it. The server keeps the last word: it rebuilds a cart from its own
 * catalogue and the quantities it received, and charges only when {@link #problem(long)} is {@link Problem#NONE}.
 * Sums stop at {@link Long#MAX_VALUE} rather than wrap round to a negative price.
 *
 * <pre>{@code
 * Cart cart = new Cart();
 * cart.add(bread, 3);
 * if (cart.problem(balance) == Cart.Problem.NONE) {
 *     buy(cart);
 * }
 * }</pre>
 */
@Incubating
public final class Cart {

    /** Why a cart cannot be bought now, in the order they are checked. */
    public enum Problem { NONE, EMPTY, SOLD_OUT, NOT_ENOUGH_FUNDS }

    /** The quantity cap of one line when the product has no stock limit. */
    public static final int MAX_QUANTITY = 999;

    /** One product and how many. */
    public record Line(Product product, int quantity) {
    }

    private final List<Line> lines = new ArrayList<>();

    /** Adds this many, on top of any already in the cart, up to the stock; returns how many were added. */
    public int add(Product product, int quantity) {
        if (product == null || quantity <= 0) {
            return 0;
        }
        int had = quantity(product.id());
        int now = set(product, (int) Math.min((long) had + quantity, Integer.MAX_VALUE));
        return now - had;
    }

    /**
     * Sets the quantity of a product, kept between 0 and its stock (or {@link #MAX_QUANTITY}); 0 removes the
     * line. Returns the quantity kept.
     */
    public int set(Product product, int quantity) {
        if (product == null) {
            return 0;
        }
        int kept = Math.max(0, Math.min(quantity, maxQuantity(product)));
        int at = indexOf(product.id());
        if (kept == 0) {
            if (at >= 0) {
                lines.remove(at);
            }
        } else if (at >= 0) {
            lines.set(at, new Line(product, kept));
        } else {
            lines.add(new Line(product, kept));
        }
        return kept;
    }

    public void remove(String productId) {
        int at = indexOf(productId);
        if (at >= 0) {
            lines.remove(at);
        }
    }

    public void clear() {
        lines.clear();
    }

    /**
     * Puts a fresh copy of a product in its line, with its new price and stock, keeping the quantity even when
     * it is now above the stock: {@link #problem(long)} then says {@link Problem#SOLD_OUT}.
     */
    public void restock(Product product) {
        int at = product == null ? -1 : indexOf(product.id());
        if (at >= 0) {
            lines.set(at, new Line(product, lines.get(at).quantity()));
        }
    }

    public List<Line> lines() {
        return List.copyOf(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** How many of this product are in the cart. */
    public int quantity(String productId) {
        int at = indexOf(productId);
        return at < 0 ? 0 : lines.get(at).quantity();
    }

    /** How many things in all, for a count on the cart's tab. */
    public int count() {
        long n = 0;
        for (Line line : lines) {
            n += line.quantity();
        }
        return (int) Math.min(n, Integer.MAX_VALUE);
    }

    /** The most one line of this product may hold. */
    public static int maxQuantity(Product product) {
        return product.limited() ? Math.min(product.stock(), MAX_QUANTITY) : MAX_QUANTITY;
    }

    public static long lineTotal(Line line) {
        return times(line.product().unitPrice(), line.quantity());
    }

    /** The price of the whole cart, at sale prices. */
    public long total() {
        long sum = 0;
        for (Line line : lines) {
            sum = plus(sum, lineTotal(line));
        }
        return sum;
    }

    /** What the full prices would have come to; above {@link #total()} when a line is on sale. */
    public long fullTotal() {
        long sum = 0;
        for (Line line : lines) {
            sum = plus(sum, times(line.product().price(), line.quantity()));
        }
        return sum;
    }

    /** The balance once the cart is paid; below 0 when the player cannot pay. */
    public long after(long balance) {
        long total = total();
        return balance < Long.MIN_VALUE + total ? Long.MIN_VALUE : balance - total;
    }

    /** How much the player lacks, 0 when the balance covers the cart. */
    public long shortfall(long balance) {
        long after = after(balance);
        return after >= 0 ? 0 : after == Long.MIN_VALUE ? Long.MAX_VALUE : -after;
    }

    public Problem problem(long balance) {
        if (lines.isEmpty()) {
            return Problem.EMPTY;
        }
        for (Line line : lines) {
            if (line.product().limited() && line.quantity() > line.product().stock()) {
                return Problem.SOLD_OUT;
            }
        }
        return shortfall(balance) > 0 ? Problem.NOT_ENOUGH_FUNDS : Problem.NONE;
    }

    private int indexOf(String productId) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).product().id().equals(productId)) {
                return i;
            }
        }
        return -1;
    }

    private static long times(long price, int quantity) {
        if (quantity <= 0 || price <= 0) {
            return 0;
        }
        return price > Long.MAX_VALUE / quantity ? Long.MAX_VALUE : price * quantity;
    }

    private static long plus(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
