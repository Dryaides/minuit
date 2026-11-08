package dev.dryaides.minuit.port.mc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;

/** The game calls that moved between Minecraft versions: one copy per port variant. */
public final class GameScreens {

    private GameScreens() {
    }

    public static Screen current() {
        return Minecraft.getInstance().gui.screen();
    }

    public static ToastManager toasts() {
        return Minecraft.getInstance().gui.toastManager();
    }

    public static InputConstants.Type keyboard() {
        return InputConstants.Type.KEYSYM;
    }
}
