package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * The player menu's equipment as wells: the four armour slots head to feet, the offhand and, when asked, the
 * 2x2 craft grid with its result. Over the game's {@code InventoryMenu} it places those menu slots wherever it
 * stands, so a kit can lay them out instead of hiding them (a hidden armour slot still takes a shift-clicked
 * chestplate on the server). Empty wells show the game's armour and shield outlines in the theme's ghost; a
 * part's own {@link SlotComponent#empty} replaces them.
 *
 * <p>Display mode: set the parts' items, as on any {@link SlotComponent}.
 */
@Incubating
public class EquipmentComponent extends BaseComponent implements ItemSource {

    /** How the armour and the offhand line up. */
    public enum Layout {
        /** Armour head to feet down one column, the offhand under the boots after a gap; the craft grid right of it. */
        COLUMN,
        /** Armour head to feet in one row, the offhand after a gap; the craft grid under it. */
        ROW
    }

    /** Head, chest, legs, feet. */
    public final SlotComponent[] armour = new SlotComponent[4];
    public final SlotComponent offhand;
    /** The 2x2 grid in reading order; empty without crafting. */
    public final SlotComponent[] craft;
    /** The craft result, null without crafting. */
    public final SlotComponent result;
    private final int menuX;
    private final int menuY;
    private final List<SlotComponent> parts = new ArrayList<>();

    /**
     * {@code x, y} is the top left of the first well; {@code menuX, menuY} the top left of the game's container
     * on the same axes ({@code leftPos, topPos}), where the menu's own slots sit. {@code crafting} adds the 2x2
     * grid and its result.
     */
    public EquipmentComponent(String name, int x, int y, int menuX, int menuY, Layout layout, boolean crafting) {
        super(new Box(name, x, y, width(layout, crafting), height(layout, crafting)));
        this.menuX = menuX;
        this.menuY = menuY;
        int p = SlotLayout.PITCH;
        boolean column = layout == Layout.COLUMN;
        for (int i = 0; i < 4; i++) {
            this.armour[i] = part(column ? x : x + i * p, column ? y + i * p : y);
        }
        this.offhand = part(column ? x : x + 4 * p + SlotLayout.HOTBAR_GAP, column ? y + 4 * p + SlotLayout.HOTBAR_GAP : y);
        if (crafting) {
            int cx = column ? x + p + GAP : x;
            int cy = column ? y : y + p + GAP;
            this.craft = new SlotComponent[4];
            for (int i = 0; i < 4; i++) {
                this.craft[i] = part(cx + (i % 2) * p, cy + (i / 2) * p);
            }
            this.result = part(cx + 3 * p, cy + p / 2);
        } else {
            this.craft = new SlotComponent[0];
            this.result = null;
        }
    }

    /** Between the armour block and the craft grid, and between the grid and its result. */
    public static final int GAP = 18;

    public static int width(Layout layout, boolean crafting) {
        int p = SlotLayout.PITCH;
        if (layout == Layout.COLUMN) {
            return crafting ? p + GAP + 4 * p : p;
        }
        return 5 * p + SlotLayout.HOTBAR_GAP;
    }

    public static int height(Layout layout, boolean crafting) {
        int p = SlotLayout.PITCH;
        if (layout == Layout.COLUMN) {
            return 5 * p + SlotLayout.HOTBAR_GAP;
        }
        return crafting ? p + GAP + 2 * p : p;
    }

    private SlotComponent part(int x, int y) {
        SlotComponent s = new SlotComponent(new Box("well", x, y, SlotLayout.PITCH, SlotLayout.PITCH));
        this.parts.add(s);
        return s;
    }

    /** Own outlines for the empty armour (head to feet) and offhand wells; null keeps the game's. */
    public EquipmentComponent ghosts(IconArt head, IconArt chest, IconArt legs, IconArt feet, IconArt offhand) {
        this.armour[0].empty = head;
        this.armour[1].empty = chest;
        this.armour[2].empty = legs;
        this.armour[3].empty = feet;
        this.offhand.empty = offhand;
        return this;
    }

    /** The part showing a player menu slot ({@link SlotLayout#playerMenuEquipment} indices), or null. */
    public SlotComponent part(int menuIndex) {
        if (menuIndex >= SlotLayout.PLAYER_MENU_HEAD && menuIndex < SlotLayout.PLAYER_MENU_HEAD + 4) {
            return this.armour[menuIndex - SlotLayout.PLAYER_MENU_HEAD];
        }
        if (menuIndex == SlotLayout.PLAYER_MENU_OFFHAND) {
            return this.offhand;
        }
        if (this.result == null) {
            return null;
        }
        return menuIndex == SlotLayout.PLAYER_MENU_RESULT ? this.result
                : menuIndex >= 1 && menuIndex <= 4 ? this.craft[menuIndex - 1] : null;
    }

    /**
     * For each part that holds the menu, the menu slot's well at the game's place, shifted onto the part: the
     * screen then shows and finds the slot where the part stands.
     */
    @Override
    protected List<SlotLayout.Well> menuWells() {
        List<SlotLayout.Well> out = new ArrayList<>();
        for (SlotLayout.Cell cell : SlotLayout.playerMenuEquipment()) {
            SlotComponent part = part(cell.index());
            if (part == null || !part.holdsMenu() || !part.visible) {
                continue;
            }
            int ix = this.menuX + cell.x();
            int iy = this.menuY + cell.y();
            out.add(new SlotLayout.Well(new Box("slot" + cell.index(), ix - 1, iy - 1, SlotLayout.PITCH, SlotLayout.PITCH),
                    part.x + 1 - ix, part.y + 1 - iy, part.empty != null));
        }
        return out;
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        for (SlotComponent part : this.parts) {
            part.draw(d, theme, mx, my, this.delta);
        }
        if (this.result != null) {
            int ax = this.craft[1].x + SlotLayout.PITCH + 2;
            int ay = this.result.y + SlotLayout.PITCH / 2 - 1;
            d.fill(ax, ay, ax + GAP - 4, ay + 2, theme.palette().text() & 0x00FFFFFF | 0x66000000);
        }
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        for (SlotComponent part : this.parts) {
            List<String> tip = part.tooltipAt(mx, my);
            if (tip != null) {
                return tip;
            }
        }
        return null;
    }

    @Override
    public ItemView itemAt(double mx, double my) {
        for (SlotComponent part : this.parts) {
            ItemView item = part.itemAt(mx, my);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    @Override
    public void tick() {
        for (SlotComponent part : this.parts) {
            part.tick();
        }
    }
}
