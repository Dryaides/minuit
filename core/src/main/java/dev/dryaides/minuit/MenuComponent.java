package dev.dryaides.minuit;

import dev.dryaides.minuit.ContextMenuComponent.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A menu button: a button with three lines that opens a list of items under itself, the actions, checks,
 * section titles and submenus of a {@link ContextMenuComponent}. The list goes to the {@link OverlayLayer}, so
 * the button works anywhere in a panel, in a {@link ScrollComponent} or a window too.
 *
 * <pre>{@code
 * MenuComponent more = add(new MenuComponent(at(new Box("more", 180, 6, 16, 16)), this)
 *         .tooltip(words.get("mymod.more"))
 *         .items(Item.title(words.get("mymod.view")),
 *                 Item.check(words.get("mymod.grid"), grid, on -> grid = on),
 *                 Item.separator(),
 *                 Item.action(words.get("mymod.export"), this::export)));
 * }</pre>
 *
 * <h2>A toolbar that collapses</h2>
 * {@link #collapse} lays a row of widgets left to right; the ones that do not fit before the row's end are
 * hidden and their items come first in this menu, whose button then sits at the end of the row. Call it from
 * the panel's layout, again when the room changes. With everything in place the button hides.
 *
 * <h2>Keys</h2>
 * The button is a stop for Tab and the arrows; Enter or Space opens the list with its first item lit, then the
 * keys of a {@link ContextMenuComponent} apply: up and down move, right opens a submenu, Enter runs, Escape
 * closes and gives the keyboard back to the button. A controller reaches it the same way. A click outside or on
 * the button again closes the list; running an item closes it too.
 */
@Incubating
public class MenuComponent extends BaseComponent {

    /** A widget of a {@link #collapse}d row and the item that stands for it in the menu. */
    public record Tool(BaseComponent widget, Item item) {
        public Tool {
            Objects.requireNonNull(widget, "widget");
            Objects.requireNonNull(item, "item");
        }
    }

    private final ContextMenuComponent menu;
    private final ButtonComponent button;
    private List<Item> items = List.of();
    private List<Item> collapsed = List.of();

    /**
     * @param box  the button
     * @param host the panel this menu is added to
     */
    public MenuComponent(Box box, PanelComponent host) {
        super(box);
        Objects.requireNonNull(host, "host");
        this.button = new ButtonComponent(box, "", this::toggle).icon(Icon.LIST).style(ButtonComponent.Style.GHOST);
        this.menu = new ContextMenuComponent(host.bounds(), host).itemsAt((px, py) -> shownItems());
        this.menu.owned = true;
    }

    /** The same items on every opening; the items of collapsed widgets come before them. */
    public MenuComponent items(Item... items) {
        return items(List.of(items));
    }

    public MenuComponent items(List<Item> items) {
        this.items = List.copyOf(items);
        return this;
    }

    /** The box the list stays in, in the panel's coordinates: the panel by default, the screen for a list that may leave it. */
    public MenuComponent area(Box area) {
        this.menu.x = area.x();
        this.menu.y = area.y();
        this.menu.w = area.w();
        this.menu.h = area.h();
        return this;
    }

    /** The button's icon; three lines by default. */
    public MenuComponent icon(Icon icon) {
        this.button.icon(icon);
        return this;
    }

    /** Text on the button beside its icon; none by default. */
    public MenuComponent label(String label) {
        this.button.label = label;
        return this;
    }

    /** The button's look; {@link ButtonComponent.Style#GHOST} by default, so it sits quietly in a toolbar. */
    public MenuComponent style(ButtonComponent.Style style) {
        this.button.style(style);
        return this;
    }

    @Override
    public MenuComponent tooltip(String... lines) {
        super.tooltip(lines);
        this.button.tooltip(lines);
        return this;
    }

    /** Called when the list closes, after an item ran or not. */
    public MenuComponent onClose(Runnable onClose) {
        this.menu.onClose(onClose);
        return this;
    }

    /** The list of this menu, to grey an item with {@link ContextMenuComponent#disabled} or open it from code. */
    public ContextMenuComponent menu() {
        return this.menu;
    }

    public boolean isOpen() {
        return this.menu.isOpen();
    }

    /** Opens the list under the button with its first item lit. False when there is nothing to show or the button is off. */
    public boolean open() {
        return this.visible && this.active && this.menu.openBelow(box());
    }

    public void close() {
        this.menu.close();
    }

    private void toggle() {
        if (this.menu.isOpen()) {
            this.menu.close();
        } else {
            open();
        }
    }

    private List<Item> shownItems() {
        if (this.collapsed.isEmpty()) {
            return this.items;
        }
        List<Item> all = new ArrayList<>(this.collapsed);
        if (!this.items.isEmpty()) {
            all.add(Item.separator());
            all.addAll(this.items);
        }
        return all;
    }

    /**
     * Lays {@code row} out from {@code x}, {@code gap} apart, keeping each widget's own width and height. Widgets
     * that would pass {@code right} are hidden and their items go first in this menu, whose button moves to the
     * end of the row ({@code right - w}); with the whole row in place the button hides.
     *
     * @return how many widgets of the row stayed in place
     */
    public int collapse(List<Tool> row, int x, int right, int gap) {
        int total = 0;
        for (int i = 0; i < row.size(); i++) {
            total += row.get(i).widget().w + (i > 0 ? gap : 0);
        }
        boolean fits = x + total <= right;
        int end = fits ? right : right - this.w - gap;
        List<Item> out = new ArrayList<>();
        int at = x;
        int kept = 0;
        boolean full = false;
        for (Tool tool : row) {
            BaseComponent widget = tool.widget();
            full |= at + widget.w > end;
            widget.visible = !full;
            if (full) {
                out.add(tool.item());
                continue;
            }
            widget.x = at;
            at += widget.w + gap;
            kept++;
        }
        this.collapsed = List.copyOf(out);
        this.visible = !fits || !this.items.isEmpty();
        if (!fits) {
            this.x = right - this.w;
        }
        if (!this.visible) {
            this.menu.close();
        }
        return kept;
    }

    /** The items of the widgets {@link #collapse} hid, in row order. */
    public List<Item> collapsedItems() {
        return this.collapsed;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        placeButton();
        this.button.draw(d, theme, mx, my, this.delta);
        this.menu.draw(d, theme, mx, my, this.delta);
    }

    private void placeButton() {
        this.button.x = this.x;
        this.button.y = this.y;
        this.button.w = this.w;
        this.button.h = this.h;
        this.button.active = this.active;
    }

    @Override
    public void tick() {
        if (!this.visible || !this.active) {
            this.menu.close();
        }
        this.menu.tick();
    }

    @Override
    void words(Words words) {
        this.button.words(words);
    }

    // ---- input ----

    @Override
    public List<String> tooltipAt(double mx, double my) {
        placeButton();
        return this.button.tooltipAt(mx, my);
    }

    @Override
    protected boolean navigable() {
        return this.visible && this.active;
    }

    @Override
    protected boolean activate() {
        if (!this.visible || !this.active) {
            return false;
        }
        BaseComponent.click();
        toggle();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!this.visible || !this.active) {
            return false;
        }
        placeButton();
        return this.button.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return this.button.mouseReleased(mx, my, button);
    }
}
