package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A shopping {@link Cart} on screen: a scrolling list of lines (picture, name, a {@link StepperComponent} for the
 * quantity, the line's price and a remove button), then the total, what stops the purchase if anything does, and
 * the checkout button. An empty cart says so. The steppers stop at the stock; the checkout button is off while
 * {@link Cart#problem(long)} is not {@link Cart.Problem#NONE}.
 *
 * <pre>{@code
 * CartComponent cart = add(new CartComponent(at(new Box("cart", 220, 30, 150, 160)), basket, coins)
 *         .balance(balance).onChange(this::refresh).onCheckout(() -> checkout.open(this)));
 * }</pre>
 *
 * <p>The widget changes the {@link Cart} it is given. After the screen changes the cart (a card's "Add"), call
 * {@link #refresh()}.
 */
@Incubating
public class CartComponent extends BaseComponent {
    /** The height of one line. */
    public static final int ROW = 20;
    private static final int PAD = 4;
    private static final int GAP = 4;
    private static final int PIC = 16;
    private static final int STEPPER_W = 46;
    private static final int STEPPER_H = 16;
    private static final int REMOVE_W = 16;
    private static final int BUTTON_H = 16;
    private static final int LINE = SurfaceLook.LINE;
    private static final int FONT_H = SurfaceLook.FONT_H;

    private final Cart cart;
    private final Currency currency;
    private final ListComponent<Product> list;
    private final ButtonComponent checkout;
    private long balance = Long.MAX_VALUE;
    private Runnable onChange = () -> { };
    private Runnable onCheckout = () -> { };
    private StepperComponent.VetoListener onStockVeto;
    private Words words;
    private Theme theme;
    private String checkoutLabel;
    private String emptyText;

    public CartComponent(Box box, Cart cart, Currency currency) {
        super(box);
        this.cart = Objects.requireNonNull(cart, "cart");
        this.currency = currency == null ? Currency.of("", null) : currency;
        this.list = new ListComponent<>(new Box("lines", box.x(), box.y(), box.w(), Math.max(ROW, box.h() - footerH(null))),
                ROW, this::drawLine, p -> { });
        // The list fills the room down to the pinned footer, so it keeps its height instead of hugging its lines.
        this.list.fit = false;
        this.list.rowWidgets(this::lineWidgets);
        this.checkout = new ButtonComponent(new Box("checkout", box.x(), box.y() + box.h() - BUTTON_H, box.w(), BUTTON_H), "Checkout", () -> {
            if (cart.problem(balance) == Cart.Problem.NONE) {
                onCheckout.run();
            }
        });
        this.checkout.role(ButtonComponent.Role.PRIMARY);
        refresh();
    }

    // ---- setup ----

    /** What the player has; checkout stays off while the cart costs more. Unknown by default. */
    public CartComponent balance(long balance) {
        this.balance = balance;
        return this;
    }

    /** Runs after a stepper or a remove button changed the cart. */
    public CartComponent onChange(Runnable onChange) {
        this.onChange = onChange == null ? () -> { } : onChange;
        return this;
    }

    /** Runs on the checkout button, only while nothing stops the purchase. */
    public CartComponent onCheckout(Runnable onCheckout) {
        this.onCheckout = onCheckout == null ? () -> { } : onCheckout;
        return this;
    }

    /** Hears a "+" refused because the stock has no more, so the screen can say so. Set it before lines exist. */
    public CartComponent onStockVeto(StepperComponent.VetoListener listener) {
        this.onStockVeto = listener;
        return this;
    }

    /** The checkout button's words; from the lang file ({@code minuit.shop.checkout}) by default. */
    public CartComponent checkoutLabel(String label) {
        this.checkoutLabel = label;
        return this;
    }

    /** What an empty cart says; from the lang file ({@code minuit.shop.empty}) by default. */
    public CartComponent emptyText(String text) {
        this.emptyText = text;
        return this;
    }

    @Override
    void words(Words words) {
        this.words = words;
    }

    /** Reads the cart again: lines added, removed or restocked by the screen. A stepper's own steps need no call. */
    public void refresh() {
        List<Product> products = new ArrayList<>();
        for (Cart.Line line : cart.lines()) {
            products.add(line.product());
        }
        if (!products.equals(list.items())) {
            list.setItems(products);
            return;
        }
        for (int i = 0; i < products.size(); i++) {
            for (BaseComponent w : list.widgetsOf(i)) {
                if (w instanceof StepperComponent stepper) {
                    stepper.value = cart.quantity(products.get(i).id());
                }
            }
        }
    }

    // ---- state ----

    public Cart cart() {
        return cart;
    }

    /** The checkout button, to read or to give a tooltip. */
    public ButtonComponent checkoutButton() {
        return checkout;
    }

    // ---- lines ----

    private List<BaseComponent> lineWidgets(Product product, int index, Box row) {
        int removeX = row.x() + row.w() - REMOVE_W;
        ButtonComponent remove = new ButtonComponent(new Box("remove", removeX, row.y() + (ROW - REMOVE_W) / 2, REMOVE_W, REMOVE_W), "", () -> {
            cart.remove(product.id());
            refresh();
            onChange.run();
        }).icon(Icon.TRASH).style(ButtonComponent.Style.GHOST);
        remove.tooltip(BaseComponent.text(null, words, "minuit.shop.remove", "Remove"));
        int quantity = cart.quantity(product.id());
        StepperComponent stepper = new StepperComponent(
                new Box("quantity", stepperX(row), row.y() + (ROW - STEPPER_H) / 2, STEPPER_W, STEPPER_H),
                1, Math.max(Cart.maxQuantity(product), quantity), quantity, v -> {
                    cart.set(product, v);
                    onChange.run();
                });
        stepper.canStep((from, to) -> to < from || to <= Cart.maxQuantity(product));
        if (onStockVeto != null) {
            stepper.onVeto(onStockVeto);
        }
        return List.of(stepper, remove);
    }

    /** The widest line price now: every line's stepper sits left of it, so the steppers and prices form columns. */
    private int priceRoom() {
        int room = 0;
        for (Cart.Line line : cart.lines()) {
            room = Math.max(room, PriceTag.width(theme, currency, Cart.lineTotal(line)));
        }
        return room;
    }

    private int stepperX(Box row) {
        return row.x() + row.w() - REMOVE_W - GAP - priceRoom() - GAP - STEPPER_W;
    }

    private void drawLine(Draw d, Theme t, Product product, Box row, boolean hovered, boolean selected) {
        Palette p = t.palette();
        int under = p.well() | 0xFF000000;
        int ink = active ? Ink.on(p, under) : p.disabledText();
        int soft = active ? Ink.soft(p, under) : p.disabledText();
        int quantity = cart.quantity(product.id());
        boolean tooMany = product.limited() && quantity > product.stock();
        if (product.picture() != null) {
            product.picture().draw(d, t, row.x(), row.y() + (ROW - PIC) / 2, PIC, PIC, active);
        }
        int stepperX = stepperX(row);
        for (BaseComponent w : list.widgetsOf(list.items().indexOf(product))) {
            if (w instanceof StepperComponent stepper) {
                stepper.x = stepperX; // the price column moves when a line's price gains a digit
            }
        }
        int nameX = row.x() + PIC + GAP;
        int nameRoom = Math.max(0, stepperX - GAP - nameX);
        int nameInk = tooMany && active ? Ink.readable(p, p.bad(), under, Ink.TEXT) : ink;
        d.textFit(product.name(), nameX, row.y() + (ROW - FONT_H) / 2 + 1, nameRoom, nameInk);
        int band = PriceTag.height(t, currency);
        long total = Cart.lineTotal(new Cart.Line(product, quantity));
        PriceTag.drawRight(d, t, currency, total, row.x() + row.w() - REMOVE_W - GAP, row.y() + (ROW - band) / 2, band, soft);
    }

    // ---- layout ----

    private int footerH(Theme t) {
        return GAP + PriceTag.height(t, currency) + 2 + LINE + GAP + BUTTON_H;
    }

    private void place() {
        list.x = x;
        list.y = y;
        list.w = w;
        list.max = Math.max(ROW, h - footerH(theme));
        list.h = list.max;
        list.active = active;
        checkout.x = x;
        checkout.w = w;
        checkout.y = y + h - BUTTON_H;
        checkout.label = BaseComponent.text(checkoutLabel, words, "minuit.shop.checkout", "Checkout");
        checkout.active = active && cart.problem(balance) == Cart.Problem.NONE;
    }

    /** What stops the purchase, in words; empty when nothing does or the cart is empty. */
    public String problemText() {
        return switch (cart.problem(balance)) {
            case NONE, EMPTY -> "";
            case SOLD_OUT -> BaseComponent.text(null, words, "minuit.shop.problem.stock", "Not that many in stock");
            case NOT_ENOUGH_FUNDS -> ShopWords.missing(words, currency, cart.shortfall(balance));
        };
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        theme = t;
        place();
        Palette p = t.palette();
        if (cart.isEmpty()) {
            t.frame().well(d, p, list.x, list.y, list.w, list.h, false);
            String text = BaseComponent.text(emptyText, words, "minuit.shop.empty", "Your cart is empty");
            RowLook.drawEmpty(d, p, text, active, list.x, list.y, list.w, list.h);
        } else {
            list.draw(d, t, mx, my, delta);
        }
        int under = p.surface() | 0xFF000000;
        int ink = active ? Ink.on(p, under) : p.disabledText();
        int soft = active ? Ink.soft(p, under) : p.disabledText();
        int bad = active ? Ink.readable(p, p.bad(), under, Ink.TEXT) : p.disabledText();
        int band = PriceTag.height(t, currency);
        int top = list.y + list.h + GAP;
        d.text(BaseComponent.text(null, words, "minuit.shop.total", "Total"), x + PAD, top + (band - FONT_H) / 2 + 1, soft);
        boolean short_ = cart.problem(balance) == Cart.Problem.NOT_ENOUGH_FUNDS;
        PriceTag.drawRight(d, t, currency, cart.total(), x + w - PAD, top, band, short_ ? bad : ink);
        String says = problemText();
        if (!says.isEmpty()) {
            d.textFit(says, x + PAD, top + band + 2 + 1, w - 2 * PAD, bad);
        }
        checkout.draw(d, t, mx, my, delta);
    }

    // ---- input ----

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (checkout.isOver(mx, my)) {
            return checkout.tooltipAt(mx, my);
        }
        return !cart.isEmpty() && list.isOver(mx, my) ? list.tooltipAt(mx, my) : null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible || !active) {
            return false;
        }
        place();
        if (checkout.isOver(mx, my)) {
            return checkout.mouseClicked(mx, my, button);
        }
        return !cart.isEmpty() && list.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean used = checkout.mouseReleased(mx, my, button);
        return list.mouseReleased(mx, my, button) || used;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return list.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        return visible && list.isOver(mx, my) && list.mouseScrolled(mx, my, amount);
    }

    @Override
    public void tick() {
        list.tick();
    }

    @Override
    public List<BaseComponent> stops() {
        place();
        List<BaseComponent> out = new ArrayList<>(list.stops());
        out.addAll(checkout.stops());
        return out;
    }
}
