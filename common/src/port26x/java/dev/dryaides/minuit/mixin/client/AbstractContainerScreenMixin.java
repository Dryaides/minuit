package dev.dryaides.minuit.mixin.client;

import dev.dryaides.minuit.client.MinuitContainerScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drops vanilla's slot hover sprites on a {@link MinuitContainerScreen}: they are white and vanish on a
 * light theme, and the screen's slot components draw the theme's hover instead. Both methods are private.
 * The pointer's slot lookup skips the slots the screen hides ({@link MinuitContainerScreen#showsSlot}) and
 * finds each slot where the screen draws it ({@link MinuitContainerScreen#slotOffsetX}): hover, tooltip,
 * keys, clicks and drags all go through it.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Shadow
    private boolean isHovering(Slot slot, double x, double y) {
        throw new AssertionError();
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"}, at = @At("HEAD"), cancellable = true)
    private void minuit$themeHover(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if ((Object) this instanceof MinuitContainerScreen<?>) {
            ci.cancel();
        }
    }

    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
    private void minuit$shownSlot(double x, double y, CallbackInfoReturnable<Slot> cir) {
        if ((Object) this instanceof MinuitContainerScreen<?> screen) {
            Slot found = null;
            for (Slot slot : this.menu.slots) {
                if (slot.isActive() && screen.showsSlot(slot)
                        && isHovering(slot, x - screen.slotOffsetX(slot), y - screen.slotOffsetY(slot))) {
                    found = slot;
                    break;
                }
            }
            cir.setReturnValue(found);
        }
    }
}
