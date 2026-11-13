package dev.dryaides.minuit;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The sums of a purchase, for a confirm dialog: the balance now, the cart's total and the balance after. When the
 * player cannot pay, the balance after shows in the bad colour with what is missing under it.
 * {@link #dialog} puts it in a {@link DialogComponent#confirm} whose confirm button stays off until the balance
 * covers the cart.
 *
 * <pre>{@code
 * DialogComponent checkout = add(CheckoutComponent.dialog(bounds(), words.get("mymod.checkout.title"), cart, coins,
 *         () -> balance, words.get("mymod.buy"), words.get("gui.cancel"), this::sendBuy));
 * ...
 * checkout.open(this);
 * }</pre>
 *
 * <p>The sums are read each frame, so the dialog follows a balance that changes while it is open.
 */
@Incubating
public class CheckoutComponent extends BaseComponent {
    /** The width the dialog gives it. */
    public static final int WIDTH = 180;
    private static final int RULE_GAP = 3;
    private static final int FONT_H = SurfaceLook.FONT_H;

    private final Cart cart;
    private final Currency currency;
    private final LongSupplier balance;
    private DialogComponent dialog;
    private int confirmIndex = -1;
    private Words words;

    public CheckoutComponent(Box box, Cart cart, Currency currency, LongSupplier balance) {
        super(box);
        this.cart = Objects.requireNonNull(cart, "cart");
        this.currency = currency == null ? Currency.of("", null) : currency;
        this.balance = Objects.requireNonNull(balance, "balance");
        this.h = preferredHeight(null);
    }

    /**
     * A confirm dialog over {@code area} with these sums as its content. Confirm runs {@code onConfirm} only while
     * {@link Cart#problem(long)} is {@link Cart.Problem#NONE}; it is the default action, as in
     * {@link DialogComponent#confirm}.
     */
    public static DialogComponent dialog(Box area, String title, Cart cart, Currency currency, LongSupplier balance,
            String confirmLabel, String cancelLabel, Runnable onConfirm) {
        CheckoutComponent sums = new CheckoutComponent(new Box("checkout", 0, 0, WIDTH, 0), cart, currency, balance);
        DialogComponent dialog = DialogComponent.confirm(area, title, "", confirmLabel, cancelLabel, () -> {
            if (onConfirm != null && cart.problem(balance.getAsLong()) == Cart.Problem.NONE) {
                onConfirm.run();
            }
        }, null);
        dialog.content(sums);
        sums.dialog = dialog;
        sums.confirmIndex = 1;
        sums.sync();
        return dialog;
    }

    @Override
    void words(Words words) {
        this.words = words;
    }

    /** Three rows, a rule, and a line for what is missing. */
    private int preferredHeight(Theme t) {
        int row = rowH(t);
        return 3 * row + 2 * RULE_GAP + 1 + SurfaceLook.LINE;
    }

    private int rowH(Theme t) {
        return PriceTag.height(t, currency) + 2;
    }

    private void sync() {
        if (dialog != null && confirmIndex >= 0) {
            dialog.actionActive(confirmIndex, cart.problem(balance.getAsLong()) == Cart.Problem.NONE);
        }
    }

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        sync();
        Palette p = t.palette();
        int under = p.surface() | 0xFF000000;
        int ink = active ? Ink.on(p, under) : p.disabledText();
        int soft = active ? Ink.soft(p, under) : p.disabledText();
        int bad = active ? Ink.readable(p, p.bad(), under, Ink.TEXT) : p.disabledText();
        long now = balance.getAsLong();
        long after = cart.after(now);
        int row = rowH(t);
        int band = PriceTag.height(t, currency);
        int top = y;
        row(d, t, BaseComponent.text(null, words, "minuit.shop.balance", "Balance"), now, top, band, soft, ink);
        top += row;
        row(d, t, BaseComponent.text(null, words, "minuit.shop.total", "Total"), cart.total(), top, band, soft, ink);
        top += row + RULE_GAP;
        d.fill(x, top, x + w, top + 1, p.surfaceEdge());
        top += 1 + RULE_GAP;
        boolean short_ = after < 0;
        row(d, t, BaseComponent.text(null, words, "minuit.shop.after", "After"), after, top, band, soft, short_ ? bad : ink);
        top += row;
        Cart.Problem problem = cart.problem(now);
        String says = switch (problem) {
            case NOT_ENOUGH_FUNDS -> ShopWords.missing(words, currency, cart.shortfall(now));
            case SOLD_OUT -> BaseComponent.text(null, words, "minuit.shop.problem.stock", "Not that many in stock");
            case EMPTY -> BaseComponent.text(null, words, "minuit.shop.empty", "Your cart is empty");
            case NONE -> "";
        };
        if (!says.isEmpty()) {
            d.textFit(says, x, top + 1, w, bad);
        }
    }

    private void row(Draw d, Theme t, String label, long amount, int top, int band, int labelInk, int amountInk) {
        int amountW = PriceTag.width(t, currency, amount);
        d.textFit(label, x, top + (band - FONT_H) / 2 + 1, Math.max(0, w - amountW - 4), labelInk);
        PriceTag.drawRight(d, t, currency, amount, x + w, top, band, amountInk);
    }
}
