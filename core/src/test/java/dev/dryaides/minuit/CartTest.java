package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CartTest {

    private static final Product BREAD = Product.of("bread", "Bread", null, 12);
    private static final Product SWORD = Product.of("sword", "Iron sword", null, 120).sale(90);
    private static final Product TORCH = Product.of("torch", "Torch", null, 5);

    /** 3 bread at 12, a 120 sword on sale at 90, 2 torches at 5: 36 + 90 + 10 = 136, 166 at full price. */
    private static Cart basket() {
        Cart cart = new Cart();
        cart.add(BREAD, 3);
        cart.add(SWORD, 1);
        cart.add(TORCH, 2);
        return cart;
    }

    @Test
    void totalsUseTheSalePriceAndTheBalanceCoversThem() {
        Cart cart = basket();
        assertEquals(136, cart.total());
        assertEquals(166, cart.fullTotal());
        assertEquals(6, cart.count());
        assertEquals(364, cart.after(500));
        assertEquals(0, cart.shortfall(500));
        assertEquals(Cart.Problem.NONE, cart.problem(500));
        assertEquals(25, SWORD.salePercent());
    }

    @Test
    void aBalanceBelowTheTotalSaysWhatIsMissing() {
        Cart cart = basket();
        assertEquals(-36, cart.after(100));
        assertEquals(36, cart.shortfall(100));
        assertEquals(Cart.Problem.NOT_ENOUGH_FUNDS, cart.problem(100));
        cart.set(TORCH, 0); // 126 left: still 26 short
        assertEquals(26, cart.shortfall(100));
        cart.remove("sword"); // 36: paid with 64 to spare
        assertEquals(64, cart.after(100));
        assertEquals(Cart.Problem.NONE, cart.problem(100));
        cart.remove("bread");
        assertEquals(Cart.Problem.EMPTY, cart.problem(100));
    }

    @Test
    void stockCapsTheQuantityAndARestockBelowItBlocksTheBuy() {
        Cart cart = new Cart();
        Product two = BREAD.stock(2);
        assertEquals(2, cart.add(two, 3));
        assertEquals(0, cart.add(two, 1));
        assertEquals(24, cart.total());
        cart.restock(two.stock(1)); // someone bought one meanwhile
        assertEquals(Cart.Problem.SOLD_OUT, cart.problem(1000));
        assertEquals(0, cart.add(Product.of("gone", "Gone", null, 1).stock(0), 1));
    }

    @Test
    void hugePricesStopAtTheLargestSumInsteadOfTurningNegative() {
        Cart cart = new Cart();
        cart.add(Product.of("star", "Star", null, Long.MAX_VALUE / 2 + 1), 2);
        assertEquals(Long.MAX_VALUE, cart.total());
        assertEquals(Cart.Problem.NOT_ENOUGH_FUNDS, cart.problem(1000));
        assertEquals(Long.MAX_VALUE - 1000, cart.shortfall(1000));
    }
}
