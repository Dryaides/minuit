package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BalanceComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.CardComponent;
import dev.dryaides.minuit.Cart;
import dev.dryaides.minuit.CartComponent;
import dev.dryaides.minuit.CheckoutComponent;
import dev.dryaides.minuit.Currency;
import dev.dryaides.minuit.DialogComponent;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.Picture;
import dev.dryaides.minuit.Product;
import dev.dryaides.minuit.ProductCardComponent;
import dev.dryaides.minuit.ShopFilter;
import dev.dryaides.minuit.ShopFilterBar;
import dev.dryaides.minuit.ShopToast;
import dev.dryaides.minuit.TabsComponent;
import dev.dryaides.minuit.ToastComponent;
import java.util.ArrayList;
import java.util.List;

/** The shop bricks composed into one shop, then each brick's states. */
public final class ShopDemo implements Demo {
    private static final Currency COINS = Currency.of("Coins", null);
    private static final Currency EMERALDS = Currency.of("Emeralds", ItemView.of("minecraft:emerald"));

    private static final Product BREAD = Product.of("bread", "Bread", Picture.item("minecraft:bread"), 12).stock(40).category("Food");
    private static final Product APPLE = Product.of("apple", "Golden apple", Picture.item("minecraft:golden_apple"), 250).stock(3).category("Food");
    private static final Product SWORD = Product.of("sword", "Iron sword", Picture.item("minecraft:iron_sword"), 120).sale(90).category("Tools");
    private static final Product PICK = Product.of("pick", "Diamond pickaxe", Picture.item("minecraft:diamond_pickaxe"), 1250).stock(0).category("Tools");
    private static final List<Product> CATALOGUE = List.of(BREAD, APPLE, SWORD, PICK);

    private static final int CARD_H = 92;

    /** A whole shop: categories, a grid of cards, the balance and a cart. */
    private static final class Shop {
        final Scene s;
        final Cart cart = new Cart();
        final CartComponent cartView;
        final BalanceComponent wallet;
        final List<ProductCardComponent> cards = new ArrayList<>();
        long balance;

        Shop(Scene s, long balance, Currency currency) {
            this.s = s;
            this.balance = balance;
            s.add(new TabsComponent(new Box("tabs", 8, 8, 228, 16), List.of("All", "Food", "Tools"), 0, i -> { }));
            wallet = s.add(new BalanceComponent(new Box("wallet", 412, 8, 0, 16), currency).alignRight(true).amount(balance));
            Box[] places = CardComponent.grid(new Box("p", 8, 30, 228, 0), 2, 6, CARD_H, CATALOGUE.size());
            for (int i = 0; i < places.length; i++) {
                cards.add(s.add(new ProductCardComponent(places[i], CATALOGUE.get(i), currency)
                        .onBuy("Add", this::add).balance(balance)));
            }
            cartView = s.add(new CartComponent(new Box("cart", 244, 30, 168, CARD_H * 2 + 6), cart, currency)
                    .balance(balance).onChange(this::changed));
        }

        void add(Product product) {
            cart.add(product, 1);
            cartView.refresh();
            changed();
        }

        void changed() {
            wallet.cost(cart.total());
        }
    }

    /** The filter bar over a grid of the catalogue's cards, as the form panel lays them out. */
    private static void filtered(Scene s, ShopFilter filter, long balance) {
        ShopFilterBar.build(8, 8, 404, filter, null, s::add, s::overlay, () -> { });
        List<Product> shown = filter.apply(CATALOGUE, balance);
        int top = 8 + ShopFilterBar.height() + 6;
        Box[] places = CardComponent.grid(new Box("p", 8, top, 404, 0), 4, 6, CARD_H, shown.size());
        for (int i = 0; i < places.length; i++) {
            s.add(new ProductCardComponent(places[i], shown.get(i), COINS).onBuy("Add", p -> { }).balance(balance));
        }
    }

    @Override
    public void states(Stage stage) {
        stage.state("shop: cards, balance and a cart", 420, 228, s -> {
            Shop shop = new Shop(s, 500, COINS);
            shop.cart.add(BREAD, 3);
            shop.cart.add(SWORD, 1);
            shop.cartView.refresh();
            shop.changed();
            ProductCardComponent apple = shop.cards.get(1);
            s.move(0, 0); // one frame, so the footer buttons sit where they are drawn
            s.click(apple.buttons().get(0).x + 4, apple.buttons().get(0).y + 4);
            s.move(60, 70);
        });
        stage.state("shop: not enough funds", 420, 228, s -> {
            Shop shop = new Shop(s, 200, COINS);
            shop.cart.add(BREAD, 2);
            shop.cart.add(SWORD, 2);
            shop.cartView.refresh();
            shop.changed();
        });
        stage.state("shop: empty cart, item currency", 420, 228, s -> new Shop(s, 1500, EMERALDS));

        stage.state("filter: default bar over the catalogue", 420, 144, s -> filtered(s, new ShopFilter(), 500));
        stage.state("filter: cheapest first, in stock, affordable", 420, 144, s -> {
            ShopFilter filter = new ShopFilter();
            filter.sort = ShopFilter.Sort.PRICE_UP;
            filter.inStockOnly = true;
            filter.affordableOnly = true;
            filtered(s, filter, 200);
        });
        stage.state("filter: search with no match", 420, 144, s -> {
            ShopFilter filter = new ShopFilter();
            filter.query = "zzz";
            filtered(s, filter, 500);
        });

        stage.state("card: normal, sale, sold out, short", 456, 108, s -> {
            Box[] places = CardComponent.grid(new Box("p", 8, 8, 440, 0), 4, 6, CARD_H, 4);
            Product[] products = {BREAD, SWORD, PICK, APPLE};
            for (int i = 0; i < places.length; i++) {
                s.add(new ProductCardComponent(places[i], products[i], COINS).onBuy("Buy", p -> { }).balance(200));
            }
        });
        stage.state("card: item currency, hovered button", 124, 108, s -> {
            ProductCardComponent card = s.add(new ProductCardComponent(new Box("c", 8, 8, 108, CARD_H), SWORD, EMERALDS)
                    .onBuy("Buy", p -> { }));
            s.move(0, 0);
            s.move(card.buttons().get(0).x + 4, card.buttons().get(0).y + 4);
        });

        stage.state("cart: stepper at the stock", 184, 140, s -> {
            Cart cart = new Cart();
            cart.add(APPLE, 3);
            cart.add(BREAD, 1);
            CartComponent view = s.add(new CartComponent(new Box("cart", 8, 8, 168, 124), cart, COINS).balance(2000));
            view.refresh();
            s.move(150, 20);
        });
        stage.state("cart: restocked below the quantity", 184, 140, s -> {
            Cart cart = new Cart();
            cart.add(APPLE, 3);
            cart.restock(APPLE.stock(1));
            s.add(new CartComponent(new Box("cart", 8, 8, 168, 124), cart, COINS).balance(2000));
        });

        stage.state("checkout: enough", 300, 176, s -> checkout(s, 500));
        stage.state("checkout: short", 300, 176, s -> checkout(s, 100));

        stage.state("balance: amount, cost, short", 184, 66, s -> {
            s.add(new BalanceComponent(new Box("a", 8, 8, 0, 14), COINS).amount(1250));
            s.add(new BalanceComponent(new Box("b", 8, 26, 0, 14), COINS).amount(500).cost(136));
            s.add(new BalanceComponent(new Box("c", 8, 44, 0, 18), EMERALDS).amount(100).cost(136));
        });

        stage.state("purchase toast", 176, 44, s -> {
            Cart cart = new Cart();
            cart.add(BREAD, 3);
            s.add(new ToastComponent(new Box("t", 8, 6, 160, 32), ShopToast.bought(s.words(), "Purchase complete", cart, COINS)));
        });
    }

    private static void checkout(Scene s, long balance) {
        Cart cart = new Cart();
        cart.add(BREAD, 3);
        cart.add(SWORD, 1);
        cart.add(Product.of("torch", "Torch", Picture.item("minecraft:torch"), 5), 2);
        DialogComponent dialog = s.add(CheckoutComponent.dialog(new Box("area", 0, 0, 300, 176), "Buy these?", cart, COINS,
                () -> balance, "Buy", "Cancel", () -> { }));
        dialog.open(s.panel());
    }
}
