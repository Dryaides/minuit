package dev.dryaides.minuit.client;

import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.port.mc.GameIds;
import net.minecraft.item.ItemStack;

/** Game stacks as the kit's {@link ItemView}. Client only. */
public final class MinuitItems {

    private MinuitItems() {
    }

    /** The stack as an icon, its enchantment glint, damage and NBT kept; the id carries the metadata ({@code @meta}). */
    public static ItemView view(ItemStack stack) {
        return new ItemView(GameIds.itemId(stack), stack.getCount(), stack);
    }
}
