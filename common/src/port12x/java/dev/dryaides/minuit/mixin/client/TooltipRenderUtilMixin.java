package dev.dryaides.minuit.mixin.client;

import dev.dryaides.minuit.client.GameTooltips;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game's tooltip box in the player's theme when they chose so ({@link GameTooltips#frame}). Fabric's tooltips
 * come here; on Forge and NeoForge the game calls the colour overload instead, themed from the colour event, and
 * this one serves mods that draw the game's box themselves.
 */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipRenderUtilMixin {

    @Inject(method = "renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V", at = @At("HEAD"),
            cancellable = true)
    private static void minuit$themedBox(GuiGraphics g, int x, int y, int w, int h, int z, CallbackInfo ci) {
        if (GameTooltips.frame(g, x, y, w, h, z, true)) {
            ci.cancel();
        }
    }
}
