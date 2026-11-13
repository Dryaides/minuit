package dev.dryaides.minuit.form;

import dev.dryaides.minuit.wire.Fields;
import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.ScreenMessage;
import dev.dryaides.minuit.wire.WireNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shop of bread at 12 coins: the player's cart goes through the wire, and the server alone
 * decides whether it is paid.
 */
class ShopTest {

    private final List<byte[]> sent = new ArrayList<>();
    private final FormServer<String> server = new FormServer<String>((p, b) -> this.sent.add(b), () -> 5_000);
    private final Map<String, Shop.Offer> catalogue = new HashMap<>();
    private final List<Long> paid = new ArrayList<>();
    private final List<Shop.Result> refused = new ArrayList<>();
    private long coins;
    private boolean bankDown;
    private int seq;

    private FormSession open() {
        this.catalogue.put("bread", new Shop.Offer("bread", Text.lit("Bread"), new Image.Item("minecraft:bread"), 12, -1, 3));
        Shop shop = new Shop(this.catalogue::get, new Shop.Till() {
            @Override
            public long balance(FormSession s) {
                return ShopTest.this.coins;
            }

            @Override
            public Shop.Payment pay(FormSession s, List<Shop.Line> lines, long total) {
                if (ShopTest.this.bankDown) {
                    return Shop.Payment.failed(Text.lit("Bank closed"));
                }
                ShopTest.this.coins -= total;
                ShopTest.this.paid.add(total);
                return Shop.Payment.DONE;
            }

            @Override
            public void refused(FormSession s, Shop.Result why) {
                ShopTest.this.refused.add(why);
            }
        }, "coins", "cart", null);
        Form form = Form.custom("test:shop")
                .balance("coins", Text.lit("Coins"), this.coins, null)
                .product("bread", Text.lit("Bread"), new Image.Item("minecraft:bread"), 12).stock(3)
                .cart("cart", Text.lit("Buy"), lines -> { })
                .build();
        return this.server.open("p", form, shop);
    }

    private void press(FormSession s, String id) {
        this.server.receive("p", new InputMessage.Press(s.id(), ++this.seq, id).encode());
    }

    /** The checkout press after the player confirmed a dialog showing {@code total}. */
    private void checkout(FormSession s, long total) {
        this.server.receive("p", new InputMessage.Press(s.id(), ++this.seq, "cart", total).encode());
    }

    /** The last value the server sent for {@code tag} of {@code nodeId}, or null. */
    private byte[] lastSent(String nodeId, int tag) {
        byte[] last = null;
        for (byte[] bytes : this.sent) {
            if (ScreenMessage.decode(bytes) instanceof ScreenMessage.Patch patch) {
                for (ScreenMessage.FieldSet set : patch.sets()) {
                    if (set.nodeId().equals(nodeId) && set.tag() == tag) {
                        last = set.value();
                    }
                }
            }
        }
        return last;
    }

    @Test
    void threeBreadsForThirtyCoinsAreRefused() {
        this.coins = 30;
        FormSession s = open();
        press(s, "bread");
        press(s, "bread");
        press(s, "bread");
        checkout(s, 36);

        assertEquals(List.of(Shop.Result.NOT_ENOUGH_FUNDS), this.refused);
        assertEquals(List.of(), this.paid);
        assertEquals(30, this.coins);
        assertEquals(new Value.Num(3), s.value("line.bread"));
    }

    @Test
    void stockSoldElsewhereBeforeCheckoutIsRefusedAndTheLineShrinks() {
        this.coins = 100;
        FormSession s = open();
        press(s, "bread");
        press(s, "bread");
        this.catalogue.put("bread", new Shop.Offer("bread", Text.lit("Bread"), null, 12, -1, 1));
        checkout(s, 24);

        assertEquals(List.of(Shop.Result.SOLD_OUT), this.refused);
        assertEquals(List.of(), this.paid);
        assertEquals(new Value.Num(1), s.value("line.bread"));
    }

    @Test
    void aQuantityAboveTheStockSentByTheClientIsRefused() {
        this.coins = 100;
        FormSession s = open();
        press(s, "bread");
        this.server.receive("p", new InputMessage.Change(s.id(), ++this.seq, "line.bread", Fields.varint(4)).encode());

        assertEquals(new Value.Num(1), s.value("line.bread"));
    }

    @Test
    void twoBreadsAreChargedTwentyFourAndTheCartEmpties() {
        this.coins = 30;
        FormSession s = open();
        press(s, "bread");
        this.server.receive("p", new InputMessage.Change(s.id(), ++this.seq, "line.bread", Fields.varint(2)).encode());
        this.sent.clear();
        checkout(s, 24);

        assertEquals(List.of(24L), this.paid);
        assertEquals(6, this.coins);
        assertEquals(List.of(), s.childIds("cart"));
        boolean emptied = false;
        boolean balance = false;
        boolean counted = false;
        for (byte[] bytes : this.sent) {
            if (ScreenMessage.decode(bytes) instanceof ScreenMessage.Patch patch) {
                for (ScreenMessage.FieldSet set : patch.sets()) {
                    emptied |= set.nodeId().equals("cart") && set.tag() == Fields.CHILDREN
                            && WireNode.decodeChildren(set.value()).isEmpty();
                    balance |= set.nodeId().equals("coins") && set.tag() == Fields.AMOUNT
                            && Fields.readVarlong(set.value()) == 6;
                    counted |= set.nodeId().equals("cart") && set.tag() == Fields.PURCHASES
                            && Fields.readVarint(set.value()) == 1;
                }
            }
        }
        assertTrue(emptied);
        assertTrue(balance);
        assertTrue(counted);
    }

    @Test
    void aSaleThatEndsBeforeCheckoutChargesNothingAndShowsTheNewPrice() {
        this.coins = 200;
        FormSession s = open();
        this.catalogue.put("bread", new Shop.Offer("bread", Text.lit("Bread"), null, 12, 8, 20));
        press(s, "bread");
        this.server.receive("p", new InputMessage.Change(s.id(), ++this.seq, "line.bread", Fields.varint(10)).encode());
        this.catalogue.put("bread", new Shop.Offer("bread", Text.lit("Bread"), null, 12, -1, 20));
        this.sent.clear();
        checkout(s, 80);

        assertEquals(List.of(Shop.Result.PRICE_CHANGED), this.refused);
        assertEquals(List.of(), this.paid);
        assertEquals(200, this.coins);
        WireNode line = WireNode.decodeChildren(lastSent("cart", Fields.CHILDREN)).get(0);
        assertTrue(line.field(Fields.SALE).isEmpty());
        assertEquals(10, Fields.readVarint(line.field(Fields.QUANTITY).orElseThrow()));
        assertEquals(1, Fields.readVarint(lastSent("cart", Fields.REFUSALS)));
        assertEquals(12, Fields.readVarlong(lastSent("bread", Fields.SALE)));

        checkout(s, 120);

        assertEquals(List.of(120L), this.paid);
        assertEquals(80, this.coins);
    }

    @Test
    void aPaymentTheBankTurnsDownCountsNoPurchaseAndKeepsTheCart() {
        this.coins = 100;
        this.bankDown = true;
        FormSession s = open();
        press(s, "bread");
        this.sent.clear();
        checkout(s, 12);

        assertEquals(List.of(Shop.Result.PAY_FAILED), this.refused);
        assertEquals(null, lastSent("cart", Fields.PURCHASES));
        assertEquals(1, Fields.readVarint(lastSent("cart", Fields.REFUSALS)));
        assertEquals(Text.lit("Bank closed"), Fields.readText(lastSent("cart", Fields.REASON)));
        assertEquals(new Value.Num(1), s.value("line.bread"));
    }

    @Test
    void anOfferWithANegativePriceOrStockIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Shop.Offer("bread", Text.lit("Bread"), null, -5, -1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new Shop.Offer("bread", Text.lit("Bread"), null, 12, -1, -3));
    }
}
