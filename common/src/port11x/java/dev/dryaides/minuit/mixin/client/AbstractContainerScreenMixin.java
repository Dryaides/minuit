package dev.dryaides.minuit.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dryaides.minuit.client.MinuitContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops vanilla's white slot hover on a {@link MinuitContainerScreen}: it vanishes on a light theme, and
 * the screen's slot components draw the theme's hover instead. The highlight is a static method called
 * while the open screen renders, so the open screen tells which one it is. On NeoForge the render calls
 * a hook the screen overrides instead, and this one goes unused there.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Inject(method = "renderSlotHighlight(Lcom/mojang/blaze3d/vertex/PoseStack;III)V", at = @At("HEAD"), cancellable = true)
    private static void minuit$themeHover(PoseStack pose, int x, int y, int z, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof MinuitContainerScreen<?>) {
            ci.cancel();
        }
    }
}
