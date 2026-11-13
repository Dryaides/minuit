package dev.dryaides.minuit.mixin.client;

import dev.dryaides.minuit.ItemTipLook;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ends a themed tooltip once the game has drawn it, a cancelled one included: later text keeps its own colours. */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    @Inject(method = "renderTooltipInternal", at = @At("RETURN"))
    private void minuit$endThemedTip(CallbackInfo ci) {
        ItemTipLook.end();
    }
}
