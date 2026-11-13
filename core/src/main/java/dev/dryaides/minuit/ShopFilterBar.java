package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds the controls that edit a {@link ShopFilter}: a search field and a sort dropdown on one line, the
 * "in stock" and "affordable" switches under them. Tab reaches each in turn. Every edit calls {@code changed}
 * after updating the filter, so the owner rebuilds the grid it filters.
 */
@Incubating
public final class ShopFilterBar {

    public static final int FIELD_H = 18;
    public static final int SWITCH_H = 16;
    public static final int GAP = 4;
    /** Width of the sort dropdown when the bar is wide enough to share a line. */
    private static final int SORT_W = 112;

    private ShopFilterBar() {
    }

    public static int height() {
        return FIELD_H + GAP + SWITCH_H;
    }

    /**
     * Adds the controls at {@code (x, y)} over {@code w} pixels and returns the height used.
     *
     * @param overlay where the open sort list goes, as for {@link DropdownComponent}
     */
    public static int build(int x, int y, int w, ShopFilter filter, Words words, Consumer<BaseComponent> place,
                            Consumer<BaseComponent> overlay, Runnable changed) {
        int sortW = Math.min(SORT_W, w / 2);
        int fieldW = w - sortW - GAP;
        TextFieldComponent field = new TextFieldComponent(new Box("", x, y, fieldW, FIELD_H), filter.query, s -> {
            filter.query = s;
            changed.run();
        }).maxLength(40).placeholder(BaseComponent.text(null, words, "minuit.shop.search", "Search"));
        place.accept(field);

        ShopFilter.Sort[] orders = ShopFilter.Sort.values();
        String[] names = {
            BaseComponent.text(null, words, "minuit.shop.sort.featured", "Featured"),
            BaseComponent.text(null, words, "minuit.shop.sort.price_up", "Cheapest first"),
            BaseComponent.text(null, words, "minuit.shop.sort.price_down", "Dearest first"),
            BaseComponent.text(null, words, "minuit.shop.sort.name", "Name A-Z")};
        DropdownComponent drop = new DropdownComponent(new Box("", x + fieldW + GAP, y, sortW, FIELD_H),
                names[filter.sort.ordinal()], x + fieldW + GAP, sortW, overlay);
        List<DropdownComponent.Line> lines = new ArrayList<>();
        for (ShopFilter.Sort order : orders) {
            lines.add(new DropdownComponent.Entry(names[order.ordinal()], "", "", "",
                    order == filter.sort ? DropdownComponent.State.SELECTED : DropdownComponent.State.SELECTABLE, -1,
                    List.of(), () -> {
                        filter.sort = order;
                        changed.run();
                    }));
        }
        drop.lines = lines;
        place.accept(drop);

        int row = y + FIELD_H + GAP;
        int half = (w - GAP) / 2;
        place.accept(new ToggleComponent(new Box("", x, row, half, SWITCH_H),
                BaseComponent.text(null, words, "minuit.shop.in_stock", "In stock only"), filter.inStockOnly, v -> {
                    filter.inStockOnly = v;
                    changed.run();
                }));
        place.accept(new ToggleComponent(new Box("", x + half + GAP, row, w - half - GAP, SWITCH_H),
                BaseComponent.text(null, words, "minuit.shop.affordable", "Affordable only"), filter.affordableOnly, v -> {
                    filter.affordableOnly = v;
                    changed.run();
                }));
        return height();
    }
}
