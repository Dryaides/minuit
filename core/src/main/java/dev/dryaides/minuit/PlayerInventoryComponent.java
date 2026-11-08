package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's 3 by 9 inventory over the hotbar, in one {@link SlotLayout#playerInventory} box. Display
 * mode takes the 36 items in vanilla's index order: 0 to 8 the hotbar, 9 to 35 the rows.
 */
public class PlayerInventoryComponent extends BaseComponent implements ItemSource, ItemDropTarget {

    public final SlotGridComponent rows;
    public final HotbarComponent hotbar;
    /** Drawn {@link SlotLayout#LABEL_GAP} over the first row, as vanilla's "Inventory"; null for none (a container screen draws its own). */
    public String label;

    public PlayerInventoryComponent(Box inventory) {
        super(inventory);
        this.rows = new SlotGridComponent(SlotLayout.inventoryRows(inventory), 3, 9);
        this.hotbar = new HotbarComponent(SlotLayout.hotbar(inventory));
        this.hotbar.framesSelection = false;
    }

    /** Display mode: the 36 items in vanilla's index order, null for an empty slot. */
    public PlayerInventoryComponent items(List<ItemView> all36) {
        List<ItemView> padded = new ArrayList<>(all36);
        while (padded.size() < 36) {
            padded.add(null);
        }
        this.hotbar.items(padded.subList(0, 9));
        this.rows.items(padded.subList(9, 36));
        return this;
    }

    public PlayerInventoryComponent label(String text) {
        this.label = text;
        return this;
    }

    /** Display mode: the vanilla index (0 to 35) and the mouse button of a click. */
    public PlayerInventoryComponent onClick(java.util.function.BiConsumer<Integer, Integer> indexAndButton) {
        this.hotbar.onClick(indexAndButton);
        this.rows.onClick((cell, button) -> indexAndButton.accept(cell + 9, button));
        return this;
    }

    /** True over menu slots, so the container screen gives the click to the game. */
    public boolean holdsMenu() {
        return this.rows.holdsMenu() && this.hotbar.holdsMenu();
    }

    @Override
    protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
        if (this.label != null) {
            d.text(this.label, this.x + 1, this.y + 1 - SlotLayout.LABEL_GAP, theme.palette().text());
        }
        this.rows.draw(d, theme, mx, my, this.delta);
        this.hotbar.draw(d, theme, mx, my, this.delta);
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        List<String> tip = this.rows.tooltipAt(mx, my);
        return tip != null ? tip : this.hotbar.tooltipAt(mx, my);
    }

    @Override
    public ItemView itemAt(double mx, double my) {
        ItemView item = this.rows.itemAt(mx, my);
        return item != null ? item : this.hotbar.itemAt(mx, my);
    }

    @Override
    public List<Drop> dropTargets() {
        List<Drop> out = new ArrayList<>(this.rows.dropTargets());
        out.addAll(this.hotbar.dropTargets());
        return out;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return this.rows.mouseClicked(mx, my, button) || this.hotbar.mouseClicked(mx, my, button);
    }

    @Override
    public List<BaseComponent> stops() {
        List<BaseComponent> out = new ArrayList<>(this.rows.stops());
        out.addAll(this.hotbar.stops());
        return out;
    }

    @Override
    public void tick() {
        this.rows.tick();
        this.hotbar.tick();
    }
}
