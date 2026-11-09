package dev.dryaides.minuit.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The protected gradient fill, for {@link AbstractContainerScreenMixin} to draw the hover it did not drop. */
@Mixin(GuiComponent.class)
public interface GuiComponentInvoker {

    @Invoker("fillGradient")
    void minuit$fillGradient(PoseStack pose, int x0, int y0, int x1, int y1, int from, int to);
}
