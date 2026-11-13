package dev.dryaides.minuit.form;

import dev.dryaides.minuit.wire.Fields;
import dev.dryaides.minuit.wire.Limits;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The server side of a shop screen: a {@link FormHandler} that keeps the cart and charges for it.
 * The client only shows the catalogue and sends the player's cart; on the cart's checkout the shop
 * reads every line again from the catalogue, checks stock and balance, and only then calls
 * {@link Till#pay}. A press on a product card adds one to the cart.
 *
 * <pre>{@code
 * Shop shop = new Shop(catalogue::get, till, "coins", "cart", null);
 * Form form = Form.custom("myshop:shop")
 *         .balance("coins", Text.lit("Coins"), till.balance(player), null)
 *         .product("bread", Text.lit("Bread"), new Image.Item("minecraft:bread"), 12).stock(40)
 *         .cart("cart", Text.lit("Buy"), lines -> { })
 *         .build();
 * }</pre>
 *
 * Card ids are the catalogue's product ids; the line of a product in the cart has the id
 * {@code line.<product id>}, so product ids are at most {@link #MAX_PRODUCT_ID} characters, which
 * {@link Form.Nodes#product} enforces.
 *
 * <p>The checkout press carries the total the player confirmed. When the catalogue's total for the
 * same lines differs (a sale ended, a dynamic price moved), nothing is charged: the cart is shown
 * again at the new prices and the player confirms again.
 */
public final class Shop implements FormHandler {

    /** The prefix of a cart line's id. */
    public static final String LINE = "line.";

    /** The longest product id: its line id {@code line.<id>} must fit a node id. */
    public static final int MAX_PRODUCT_ID = Limits.NODE_ID - LINE.length();

    /** One product as the server sells it now. {@code salePrice} and {@code stock} are -1 when unused. */
    public record Offer(String id, Text name, Image image, long price, long salePrice, int stock) {

        public Offer {
            if (price < 0 || salePrice < -1 || stock < -1) {
                throw new IllegalArgumentException("offer " + id + ": price " + price + ", sale " + salePrice
                        + ", stock " + stock);
            }
        }

        public long unitPrice() {
            return salePrice >= 0 && salePrice < price ? salePrice : price;
        }

        public boolean limited() {
            return stock >= 0;
        }
    }

    /** One cart line as charged: the offer read at checkout and the quantity. */
    public record Line(Offer offer, int quantity) {
    }

    /** Why a checkout did or did not go through. */
    public enum Result {
        BOUGHT, EMPTY, SOLD_OUT, NOT_ENOUGH_FUNDS,
        /** The catalogue's total is not the one the player confirmed. */
        PRICE_CHANGED,
        /** {@link Till#pay} turned the payment down. */
        PAY_FAILED;

        /** What the player reads. */
        public Text reason() {
            return switch (this) {
                case BOUGHT -> Text.lit("");
                case EMPTY -> Text.tr("minuit.shop.empty", "Your cart is empty");
                case SOLD_OUT -> Text.tr("minuit.shop.problem.stock", "Not that many in stock");
                case NOT_ENOUGH_FUNDS -> Text.tr("minuit.shop.refused.funds", "Not enough money");
                case PRICE_CHANGED -> Text.tr("minuit.shop.refused.price", "Prices changed: check your cart and confirm again");
                case PAY_FAILED -> Text.tr("minuit.shop.refused.pay", "The payment did not go through");
            };
        }
    }

    /** What {@link Till#pay} did: taken, or turned down with a reason the player reads (null for the default one). */
    public record Payment(boolean done, Text reason) {

        public static final Payment DONE = new Payment(true, null);

        public static Payment failed(Text reason) {
            return new Payment(false, reason);
        }
    }

    /** The mod's side of the money. */
    public interface Till {

        /** What the player of {@code session} has now. */
        long balance(FormSession session);

        /**
         * Takes {@code total} and hands over the lines; stock and balance were checked just before.
         * Returns {@link Payment#failed} when the money could not be taken: nothing is counted as bought.
         */
        Payment pay(FormSession session, List<Line> lines, long total);

        /** A checkout refused; the screen already shows the fresh stock and balance. */
        default void refused(FormSession session, Result why) {
        }
    }

    private final Function<String, Offer> catalogue;
    private final Till till;
    private final String balanceId;
    private final String cartId;
    private final FormHandler rest;

    /**
     * @param catalogue the offer for a product id now, or null when it is not sold
     * @param balanceId the {@code balance} node, or null when the screen shows none
     * @param rest      gets every input the shop does not handle
     */
    public Shop(Function<String, Offer> catalogue, Till till, String balanceId, String cartId, FormHandler rest) {
        this.catalogue = catalogue;
        this.till = till;
        this.balanceId = balanceId;
        this.cartId = cartId;
        this.rest = rest == null ? new FormHandler() { } : rest;
    }

    @Override
    public void pressed(FormSession session, String id) {
        pressed(session, id, dev.dryaides.minuit.wire.InputMessage.NONE);
    }

    @Override
    public void pressed(FormSession session, String id, long confirmed) {
        if (id.equals(this.cartId)) {
            buy(session, confirmed);
            return;
        }
        Offer offer = "product".equals(session.kind(id)) && !id.startsWith(LINE) ? this.catalogue.apply(id) : null;
        if (offer == null) {
            this.rest.pressed(session, id);
            return;
        }
        add(session, offer);
    }

    @Override
    public void changed(FormSession session, String id, Value value) {
        if (!id.startsWith(LINE) || !"product".equals(session.kind(id))) {
            this.rest.changed(session, id, value);
        }
    }

    @Override
    public void closed(FormSession session, CloseReason why) {
        this.rest.closed(session, why);
    }

    /** Adds one of {@code offer} to the cart, up to its stock. */
    public void add(FormSession session, Offer offer) {
        String line = LINE + offer.id();
        List<String> ids = session.childIds(this.cartId);
        if (ids.contains(line)) {
            int now = ((Value.Num) session.value(line)).value();
            int max = offer.limited() ? Math.min(offer.stock(), Limits.QUANTITY)
                    : Limits.QUANTITY;
            if (now < max) {
                session.set(line, new Value.Num(now + 1));
            }
            return;
        }
        if (offer.limited() && offer.stock() < 1) {
            return;
        }
        session.refusal(this.cartId, Text.lit(""));
        List<Line> lines = lines(session);
        lines.add(new Line(offer, 1));
        show(session, lines);
    }

    /**
     * Checks the cart against the catalogue, the total the player confirmed and the balance, then
     * charges it and empties the cart.
     */
    public Result buy(FormSession session, long confirmed) {
        List<Line> lines = lines(session);
        List<Line> wanted = new ArrayList<>();
        for (Line line : lines) {
            if (line.quantity() > 0) {
                wanted.add(line);
            }
        }
        long balance = this.till.balance(session);
        Result result = wanted.isEmpty() || total(wanted) == confirmed ? check(wanted, balance) : Result.PRICE_CHANGED;
        Text reason = result.reason();
        if (result == Result.BOUGHT) {
            Payment payment = this.till.pay(session, wanted, total(wanted));
            if (payment == null || !payment.done()) {
                result = Result.PAY_FAILED;
                reason = payment == null || payment.reason() == null ? result.reason() : payment.reason();
                balance = this.till.balance(session);
            }
        }
        if (result != Result.BOUGHT) {
            List<Line> kept = new ArrayList<>();
            for (Line line : lines) {
                kept.add(line.offer().limited() && line.quantity() > line.offer().stock()
                        ? new Line(line.offer(), line.offer().stock()) : line);
            }
            show(session, kept);
            refresh(session, lines, balance);
            session.refusal(this.cartId, reason);
            this.till.refused(session, result);
            return result;
        }
        session.refusal(this.cartId, Text.lit(""));
        byte[] count = session.field(this.cartId, Fields.PURCHASES);
        session.field(this.cartId, Fields.PURCHASES, Fields.varint(count == null ? 1 : Fields.readVarint(count) + 1));
        List<Line> after = new ArrayList<>();
        for (Line line : wanted) {
            Offer now = this.catalogue.apply(line.offer().id());
            after.add(new Line(now == null ? line.offer() : now, 0));
        }
        show(session, List.of());
        refresh(session, after, this.till.balance(session));
        return Result.BOUGHT;
    }

    /** The verdict on {@code lines} for a player who has {@code balance}. */
    public static Result check(List<Line> lines, long balance) {
        if (lines.isEmpty()) {
            return Result.EMPTY;
        }
        for (Line line : lines) {
            if (line.offer().limited() && line.quantity() > line.offer().stock()) {
                return Result.SOLD_OUT;
            }
        }
        return total(lines) > balance ? Result.NOT_ENOUGH_FUNDS : Result.BOUGHT;
    }

    /** The sum at unit prices, saturating at {@link Long#MAX_VALUE}. */
    public static long total(List<Line> lines) {
        long sum = 0;
        for (Line line : lines) {
            long unit = line.offer().unitPrice();
            long part = line.quantity() == 0 ? 0
                    : unit > Long.MAX_VALUE / line.quantity() ? Long.MAX_VALUE : unit * line.quantity();
            sum = sum > Long.MAX_VALUE - part ? Long.MAX_VALUE : sum + part;
        }
        return sum;
    }

    /** The cart's lines, each read again from the catalogue; a line no longer sold is dropped. */
    private List<Line> lines(FormSession session) {
        List<Line> lines = new ArrayList<>();
        for (String id : session.childIds(this.cartId)) {
            if (!id.startsWith(LINE)) {
                continue;
            }
            Offer offer = this.catalogue.apply(id.substring(LINE.length()));
            if (offer != null && session.value(id) instanceof Value.Num n) {
                lines.add(new Line(offer, n.value()));
            }
        }
        return lines;
    }

    private void show(FormSession session, List<Line> lines) {
        session.children(this.cartId, c -> {
            for (Line line : lines) {
                c.product(LINE + line.offer().id(), line.offer().name(), line.offer().image(), line.offer().price());
                if (line.offer().salePrice() >= 0 && line.offer().salePrice() < line.offer().price()) {
                    c.sale(line.offer().salePrice());
                }
                c.stock(line.offer().stock()).quantity(Math.min(line.quantity(), Limits.QUANTITY));
            }
        });
    }

    /** The balance and every card's price and stock as they are now. */
    private void refresh(FormSession session, List<Line> lines, long balance) {
        if (this.balanceId != null) {
            session.amount(this.balanceId, Math.max(0, balance));
        }
        for (Line line : lines) {
            if ("product".equals(session.kind(line.offer().id()))) {
                session.price(line.offer().id(), line.offer().price(), line.offer().salePrice());
                session.stock(line.offer().id(), line.offer().stock());
            }
        }
    }
}
