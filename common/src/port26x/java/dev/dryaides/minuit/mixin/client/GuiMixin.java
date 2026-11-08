package dev.dryaides.minuit.mixin.client;

import dev.dryaides.minuit.client.MinuitMenuScreens;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** {@code MinecraftMixin} for 26.2 and later, where {@code setScreen} lives on {@code Gui}; absent on 26.1.2. */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen minuit$kitOverMenu(Screen screen) {
        return MinuitMenuScreens.swap(screen);
    }
}
