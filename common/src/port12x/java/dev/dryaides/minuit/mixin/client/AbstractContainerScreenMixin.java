package dev.dryaides.minuit.mixin.client;

import dev.dryaides.minuit.SlotPlacement;
import dev.dryaides.minuit.SpriteIcon;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.client.MinuitContainerScreen;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.internal.ContainerTab;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
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
 * Drops vanilla's white slot hover on a {@link MinuitContainerScreen}: it vanishes on a light theme, and
 * the screen's slot components draw the theme's hover instead. The highlight is a static method called
 * while the open screen renders, so the open screen tells which one it is. On NeoForge the render calls
 * a hook the screen overrides instead, and this one goes unused there.
 *
 * <p>The slots the screen hides ({@link MinuitContainerScreen#showsSlot}) are neither drawn nor found
 * under the pointer, a slot is drawn and found at the screen's offset
 * ({@link MinuitContainerScreen#slotOffsetX}), and the carried stack is not drawn while the screen asks so
 * ({@link MinuitContainerScreen#drawsCarried}); the snapback item still flies home.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow
    @Final
    protected AbstractContainerMenu menu;

    @Shadow
    protected Slot hoveredSlot;

    @Shadow
    private ItemStack snapbackItem;

    @Unique
    private boolean minuit$slotMoved;

    @Shadow
    private boolean isHovering(Slot slot, double x, double y) {
        throw new AssertionError();
    }

    @Shadow
    private Slot findSlot(double x, double y) {
        throw new AssertionError();
    }

    @Inject(method = "renderSlotHighlight(Lnet/minecraft/client/gui/GuiGraphics;III)V", at = @At("HEAD"), cancellable = true)
    private static void minuit$themeHover(GuiGraphics graphics, int x, int y, int z, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof MinuitContainerScreen<?>) {
            ci.cancel();
        }
    }

    @Inject(method = "findSlot", at = @At("HEAD"), cancellable = true)
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

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void minuit$hiddenSlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        this.minuit$slotMoved = false;
        if ((Object) this instanceof MinuitContainerScreen<?> screen) {
            if (!screen.showsSlot(slot)) {
                ci.cancel();
                return;
            }
            float ox = screen.slotOffsetX(slot) + screen.placement().shiftX(slot.index);
            float oy = screen.slotOffsetY(slot) + screen.placement().shiftY(slot.index);
            if (ox != 0F || oy != 0F) {
                graphics.pose().pushPose();
                graphics.pose().translate(ox, oy, 0F);
                this.minuit$slotMoved = true;
            }
        }
    }

    /** The game's empty-slot outline of the slot being drawn, kept for the theme to draw at its end; the game's is never drawn. */
    @Unique
    private Pair<ResourceLocation, ResourceLocation> minuit$ghost;

    @Redirect(method = "renderSlot", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getNoItemIcon()Lcom/mojang/datafixers/util/Pair;"))
    private Pair<ResourceLocation, ResourceLocation> minuit$themeGhost(Slot slot) {
        if ((Object) this instanceof MinuitContainerScreen<?> screen && screen.placement() != SlotPlacement.NONE) {
            this.minuit$ghost = slot.getNoItemIcon();
            return null;
        }
        return slot.getNoItemIcon();
    }

    @Inject(method = "renderSlot", at = @At("RETURN"))
    private void minuit$movedSlotBack(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        Pair<ResourceLocation, ResourceLocation> ghost = this.minuit$ghost;
        this.minuit$ghost = null;
        if (ghost != null && (Object) this instanceof MinuitContainerScreen<?>) {
            ((MinuitContainerScreen<?>) (Object) this).placement().drawGhost(new GuiDraw(graphics), slot.index,
                    new SpriteIcon(ghost.getFirst().toString(), ghost.getSecond().toString(), 16, 16), slot.x, slot.y);
        }
        if (this.minuit$slotMoved) {
            this.minuit$slotMoved = false;
            graphics.pose().popPose();
        }
    }

    /** The render loop picks the hovered slot inline, at the menu's places; the screen's lookup replaces it before labels, tooltip and keys read it. */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void minuit$hiddenHover(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if ((Object) this instanceof MinuitContainerScreen<?>) {
            this.hoveredSlot = findSlot(mouseX, mouseY);
        }
    }

    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void minuit$hiddenCarried(GuiGraphics graphics, ItemStack stack, int x, int y, String count, CallbackInfo ci) {
        if ((Object) this instanceof MinuitContainerScreen<?> screen && stack != this.snapbackItem && !screen.drawsCarried()) {
            ci.cancel();
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
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;renderLabels(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void minuit$themeTab(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (minuit$hasTab()) {
            ContainerTab.draw(this, new GuiDraw(graphics), this.leftPos, this.topPos, this.leftPos, this.topPos, mouseX, mouseY);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void minuit$themeTabTip(GuiGraphics graphics, int mouseX, int mouseY, float partial, CallbackInfo ci) {
        if (minuit$hasTab()) {
            ContainerTab.drawTip(this, new GuiDraw(graphics), this.leftPos, this.topPos, mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void minuit$clickThemeTab(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (minuit$hasTab() && button == 0 && ContainerTab.click(this, this.leftPos, this.topPos, mouseX, mouseY)) {
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
