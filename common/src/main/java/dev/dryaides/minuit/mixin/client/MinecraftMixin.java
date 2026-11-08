package dev.dryaides.minuit.mixin.client;

import dev.dryaides.minuit.client.MinuitMenuScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps a menu's vanilla screen for a kit screen before anything sees it, the loader's opening
 * event included, so the kit holds the menu from the first frame. Both loaders, one hook. From 26.2
 * the game moved {@code setScreen} to {@code Gui} ({@code GuiMixin} in port26x), so this one is optional there.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen minuit$kitOverMenu(Screen screen) {
        return MinuitMenuScreens.swap(screen);
    }
}
