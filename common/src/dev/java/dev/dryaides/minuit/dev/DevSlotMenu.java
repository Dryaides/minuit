package dev.dryaides.minuit.dev;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.SlotLayout;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Dev only, never in the shipped jar: a 2 by 9 chest vanilla lacks, laid out by {@link SlotLayout} on
 * both sides. The server keeps one container for the session, so closing and reopening shows what the
 * server holds.
 */
public class DevSlotMenu extends AbstractContainerMenu {

    public static final int ROWS = 2;
    public static final Box CHEST = SlotLayout.grid("chest", 8, 18, ROWS, 9);
    public static final Box INV = SlotLayout.playerInventory("inv", 8, SlotLayout.inventoryTop(18, ROWS));
    public static final int WIDTH = 176;
    public static final int HEIGHT = SlotLayout.inventoryTop(18, ROWS) + 76 + SlotLayout.EDGE;
    /** Server side: the one dev chest. */
    public static final SimpleContainer SHARED = new SimpleContainer(ROWS * 9);

    private final Container container;

    /** Client side, from the open packet. */
    public DevSlotMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, new SimpleContainer(ROWS * 9));
    }

    public DevSlotMenu(MenuType<?> type, int id, Inventory inventory, Container container) {
        super(type, id);
        this.container = container;
        for (SlotLayout.Cell c : SlotLayout.cells(CHEST, 0)) {
            addSlot(new Slot(container, c.index(), c.x(), c.y()));
        }
        for (SlotLayout.Cell c : SlotLayout.playerCells(INV)) {
            addSlot(new Slot(inventory, c.index(), c.x(), c.y()));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        Slot slot = this.slots.get(slotIndex);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        int chest = ROWS * 9;
        boolean done = slotIndex < chest ? moveItemStackTo(stack, chest, this.slots.size(), true)
                : moveItemStackTo(stack, 0, chest, false);
        if (!done) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }
}
