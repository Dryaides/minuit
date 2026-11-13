package dev.dryaides.minuit.mixin.client;

import dev.dryaides.minuit.ItemTipLook;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.client.MinuitClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game's tooltip box in the player's theme when they chose so ({@link ItemTipLook}), on Fabric and NeoForge
 * alike. A tooltip with a style of its own (an item's {@code tooltip_style}, or a texture a mod set through
 * NeoForge's tooltip texture event) keeps it.
 */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipRenderUtilMixin {

    @Inject(method = "extractTooltipBackground", at = @At("HEAD"), cancellable = true)
    private static void minuit$themedBox(GuiGraphicsExtractor g, int x, int y, int w, int h, Identifier style,
                                         CallbackInfo ci) {
        ItemTipLook look = ItemTipLook.begin(style == null && MinuitClient.themedItemTips());
        if (look != null) {
            look.frame(new GuiDraw(g), x, y, w, h);
            ci.cancel();
        }
    }
}
