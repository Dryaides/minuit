package dev.dryaides.minuit.mixin.client;

import dev.dryaides.minuit.ItemTipLook;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A text line of a themed tooltip ({@link ItemTipLook#current}) in the theme's inks, without the game's shadow: each
 * run keeps its own colour where it reads on the popup, the rest takes the line's ink. Lines of other tooltips, and
 * other kinds of tooltip parts, draw as the game or their mod draws them.
 */
@Mixin(ClientTextTooltip.class)
public abstract class ClientTextTooltipMixin {

    @Shadow
    @Final
    private FormattedCharSequence text;

    @Inject(method = "renderText", at = @At("HEAD"), cancellable = true)
    private void minuit$themedInk(Font font, int x, int y, Matrix4f pose, MultiBufferSource.BufferSource buffers,
                                  CallbackInfo ci) {
        ItemTipLook look = ItemTipLook.current();
        if (look == null) {
            return;
        }
        font.drawInBatch(minuit$legible(this.text, look), (float) x, (float) y, look.nextInk() | 0xFF000000, false, pose,
                buffers, Font.DisplayMode.NORMAL, 0, 15728880);
        ci.cancel();
    }

    @Unique
    private static FormattedCharSequence minuit$legible(FormattedCharSequence text, ItemTipLook look) {
        return sink -> text.accept((index, style, codepoint) -> {
            TextColor color = style.getColor();
            return sink.accept(index, color == null || look.keeps(color.getValue()) ? style
                    : style.withColor((TextColor) null), codepoint);
        });
    }
}
