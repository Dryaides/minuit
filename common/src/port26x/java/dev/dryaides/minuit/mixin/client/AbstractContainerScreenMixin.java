package dev.dryaides.minuit.mixin.client;

import dev.dryaides.minuit.SlotPlacement;
import dev.dryaides.minuit.SpriteIcon;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.client.MinuitContainerScreen;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.internal.ContainerTab;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drops vanilla's slot hover sprites on a {@link MinuitContainerScreen}: they are white and vanish on a
 * light theme, and the screen's slot components draw the theme's hover instead. Both methods are private.
 * The pointer's slot lookup skips the slots the screen hides ({@link MinuitContainerScreen#showsSlot}) and
 * those no slot part places, and finds each slot where the screen draws it
 * ({@link MinuitContainerScreen#slotOffsetX}): hover, tooltip, keys, clicks and drags all go through it.
 * The game's empty-slot outline is never drawn on a kit screen; the theme draws it from the same sprite.
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

    /** The outline the game would draw in the slot being drawn, kept for the theme to draw at its end. */
    @Unique
    private Identifier minuit$ghost;

    @Redirect(method = "extractSlot", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getNoItemIcon()Lnet/minecraft/resources/Identifier;"))
    private Identifier minuit$themeGhost(Slot slot) {
        if ((Object) this instanceof MinuitContainerScreen<?> screen && screen.placement() != SlotPlacement.NONE) {
            this.minuit$ghost = slot.getNoItemIcon();
            return null;
        }
        return slot.getNoItemIcon();
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    private void minuit$drawGhost(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        Identifier ghost = this.minuit$ghost;
        this.minuit$ghost = null;
        if (ghost != null && (Object) this instanceof MinuitContainerScreen<?> screen) {
            screen.placement().drawGhost(new GuiDraw(graphics), slot.index, SpriteIcon.gui(ghost.toString(), 16, 16), slot.x, slot.y);
        }
    }

    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
    private void minuit$shownSlot(double x, double y, CallbackInfoReturnable<Slot> cir) {
        if ((Object) this instanceof MinuitContainerScreen<?> screen) {
            Slot found = null;
            if (!screen.placement().reachable(x, y)) {
                cir.setReturnValue(null);
                return;
            }
            for (Slot slot : this.menu.slots) {
                if (slot.isActive() && screen.placement().finds(slot.index) && screen.showsSlot(slot)
                        && isHovering(slot, x - screen.slotOffsetX(slot) - screen.placement().shiftX(slot.index),
                                y - screen.slotOffsetY(slot) - screen.placement().shiftY(slot.index))) {
                    found = slot;
                    break;
                }
            }
            cir.setReturnValue(found);
        }
    }

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    /** minuit's own container screens draw their tab; the creative tabs fill the top edge. */
    @Unique
    private boolean minuit$hasTab() {
        return !((Object) this instanceof MinuitContainerScreen<?>) && !((Object) this instanceof CreativeModeInventoryScreen);
    }

    /** The theme tab on a container screen minuit does not draw, before the labels, where the pose stands at the container's corner. */
    @Inject(method = "extractContents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractLabels(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"))
    private void minuit$themeTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (minuit$hasTab()) {
            ContainerTab.draw(this, new GuiDraw(graphics), this.leftPos, this.topPos, this.leftPos, this.topPos, mouseX, mouseY);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void minuit$themeTabTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (minuit$hasTab()) {
            ContainerTab.drawTip(this, new GuiDraw(graphics), this.leftPos, this.topPos, mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void minuit$clickThemeTab(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (minuit$hasTab() && event.button() == 0 && ContainerTab.click(this, this.leftPos, this.topPos, event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }

    /** Back from the theme page the screen comes again: the menu stays open. */
    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void minuit$backFromThemePage(CallbackInfo ci) {
        if (ContainerTab.leaving(this)) {
            ci.cancel();
        }
    }
}
