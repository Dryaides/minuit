package dev.dryaides.minuit;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * One 18 by 18 slot well. Over a menu slot of a container screen it draws the well and the theme's
 * hover under the game's item, and the game handles the click. In display mode ({@link #item} set, or a
 * callback) it draws the item, its count and tooltip itself, and a click only calls back: nothing moves.
 * A display slot never holds a menu {@code Slot}, so a storage terminal can show any count in it.
 */
public class SlotComponent extends BaseComponent implements ItemSource, ItemDropTarget {

    /** Display mode only; null over a menu slot, where the game draws the stack. */
    public ItemView item;
    /** Drawn short, as {@link #shortCount}; 1 or less draws none. */
    public long count;
    /** Ringed in accent: a target, a result. */
    public boolean marked;
    /** In the drag of a carried stack over several slots: lit like the hover, as the game lights them. */
    public boolean dragged;
    /** Ghost drawn at 40 % while there is no item: an armour slot's outline. */
    public IconArt empty;
    private IntConsumer onClick;
    private IntConsumer onPress;
    private Predicate<ItemView> onDrop;
    private boolean gameTip;

    public SlotComponent(Box box) {
        super(box.x(), box.y(), SlotLayout.PITCH, SlotLayout.PITCH);
    }

    /** Shows {@code shown}, its count from the view; null empties the slot. */
    public SlotComponent item(ItemView shown) {
        return item(shown, shown == null ? 0 : shown.count());
    }

    /** Shows {@code shown} with a count past what a stack holds. */
    public SlotComponent item(ItemView shown, long amount) {
        this.item = shown;
        this.count = amount;
        return this;
    }

    /** Display mode: the mouse button of a click on the slot. */
    public SlotComponent onClick(IntConsumer button) {
        this.onClick = button;
        return this;
    }

    /** Display mode: a mouse press goes here instead of {@link #onClick}, for a host that plays the whole gesture. */
    public SlotComponent onPress(IntConsumer button) {
        this.onPress = button;
        return this;
    }

    /** Display mode: takes an item handed in from outside the kit; the test says whether it took it. */
    public SlotComponent onDrop(Predicate<ItemView> accept) {
        this.onDrop = accept;
        return this;
    }

    /** True over a menu slot: no item shown and nothing to call, so the container screen gives the click to the game. */
    public boolean holdsMenu() {
        return this.item == null && this.onClick == null && this.onPress == null && this.onDrop == null;
    }

    /** Its 18 px well while it holds the menu. */
    @Override
    protected List<SlotLayout.Well> menuWells() {
        return holdsMenu() ? List.of(new SlotLayout.Well(box(), 0, 0, this.empty != null)) : List.of();
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        this.gameTip = cell(d, theme, this.x, this.y, this.item, this.count, this.empty, hovered || this.dragged,
                hovered, this.marked, mx, my);
    }

    /**
     * Draws one well, lit when {@code lit}, and in display mode its item, count and, when {@code tip}, the
     * game's tooltip.
     *
     * @return true when the game queued its own tooltip for the item
     */
    static boolean cell(Draw d, Theme theme, int x, int y, ItemView item, long count, IconArt empty,
                        boolean lit, boolean tip, boolean marked, int mx, int my) {
        theme.frame().slot(d, theme.palette(), x, y, lit, marked);
        return items(d, theme, x, y, 1, item, count, empty, lit, tip, mx, my);
    }

    /** An empty slot's outline, centred on the 16 px item area at {@code ix, iy}: a part's own or a menu slot's. */
    static void ghost(Draw d, Theme theme, IconArt icon, int ix, int iy) {
        icon.draw(d, ix + (16 - icon.width()) / 2, iy + (16 - icon.height()) / 2, theme.palette().text(), 0.4F);
    }

    /** The item of a cell whose 16 px item area starts {@code off} px in from {@code x, y}; no well. */
    static boolean items(Draw d, Theme theme, int x, int y, int off, ItemView item, long count, IconArt empty,
                         boolean lit, boolean tip, int mx, int my) {
        int ix = x + off;
        int iy = y + off;
        if (item == null) {
            if (empty != null) {
                ghost(d, theme, empty, ix, iy);
            }
            if (lit) {
                d.fill(ix, iy, ix + 16, iy + 16, Shapes.SLOT_HOVER_FRONT);
            }
            return false;
        }
        d.item(item, ix, iy);
        String label = count > 1 ? shortCount(count) : null;
        if (label != null) {
            // The count over the item at every size, as the game raises it: a scaled item otherwise covers it.
            // The pose is popped below so the lift does not add up cell after cell and climb over a later tooltip.
            d.push(0F, 0F, 1F);
            d.nextLayer();
        }
        // White with the game's drop shadow in every theme, as the game draws counts: readable over any item.
        if (label == null || d.width(label) <= 16) {
            d.itemDecorations(item, ix, iy, label);
        } else {
            // Too wide for the slot at full size: half size in the corner, as storage mods draw it.
            d.itemDecorations(item, ix, iy, "");
            d.push(ix + 16 - d.width(label) * 0.5F, iy + 12, 0.5F);
            d.text(label, 1, 1, 0xFF3F3F3F);
            d.text(label, 0, 0, 0xFFFFFFFF);
            d.pop();
        }
        if (lit) {
            d.fill(ix, iy, ix + 16, iy + 16, Shapes.SLOT_HOVER_FRONT);
        }
        if (label != null) {
            d.pop();
        }
        return tip && d.itemTooltip(item, mx, my);
    }

    /** The item's name, unless the game shows its own tooltip. */
    static List<String> nameTip(ItemView item, boolean gameTip) {
        return item == null || gameTip ? null : List.of(ItemLookup.game().name(item.id()));
    }

    /**
     * A count in at most four characters, as a storage terminal draws it: 999, 1.2k, 34k, 567k, 1.2M,
     * 34M. The digit after the point is cut, never rounded up, so a slot never shows more than it holds.
     */
    public static String shortCount(long n) {
        if (n < 1000) {
            return Long.toString(n);
        }
        String units = "kMGTPE";
        int unit = 0;
        long scale = 1000;
        while (unit < units.length() - 1 && n / scale >= 1000) {
            scale *= 1000;
            unit++;
        }
        long whole = n / scale;
        long tenth = n % scale * 10 / scale;
        return whole < 10 && tenth > 0 ? whole + "." + tenth + units.charAt(unit) : whole + "" + units.charAt(unit);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        if (!isOver(mx, my)) {
            return null;
        }
        List<String> name = nameTip(this.item, this.gameTip);
        return name != null ? name : super.tooltipAt(mx, my);
    }

    @Override
    public ItemView itemAt(double mx, double my) {
        return isOver(mx, my) ? this.item : null;
    }

    @Override
    public List<Drop> dropTargets() {
        return this.onDrop == null || !this.active ? List.of() : List.of(new Drop(box(), this.onDrop));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.onClick == null && this.onPress == null || !this.active || !isOver(mx, my)) {
            return false;
        }
        (this.onPress != null ? this.onPress : this.onClick).accept(button);
        return true;
    }

    @Override
    protected boolean navigable() {
        return this.onClick != null;
    }

    @Override
    protected boolean activate() {
        if (this.onClick == null || !this.active) {
            return false;
        }
        this.onClick.accept(0);
        return true;
    }

    @Override
    protected int focusCorner() {
        return 0;
    }
}
