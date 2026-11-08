package dev.dryaides.minuit.client;

import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.port.mc.GameIds;
import net.minecraft.world.item.ItemStack;

/** Game stacks as the kit's {@link ItemView}. Client only. */
public final class MinuitItems {

    private MinuitItems() {
    }

    /** The stack as an icon, its enchantment glint, damage and components kept. */
    public static ItemView view(ItemStack stack) {
        return new ItemView(GameIds.ITEMS.getKey(stack.getItem()).toString(), stack.getCount(), stack);
    }
}
