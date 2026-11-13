package dev.dryaides.minuit.mixin.client;

import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether {@code GuiGraphics} holds its fills for one flush ({@code drawManaged}), so the themed tooltip box can draw unheld. */
@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {

    @Accessor("managed")
    boolean minuit$managed();

    @Accessor("managed")
    void minuit$managed(boolean managed);
}
