package dev.dryaides.minuit.port.mc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;

/** The game calls that moved between Minecraft versions: one copy per port variant. */
public final class GameScreens {

    private GameScreens() {
    }

    public static Screen current() {
        return Minecraft.getInstance().screen;
    }

    public static ToastManager toasts() {
        return Minecraft.getInstance().getToastManager();
    }

    public static InputConstants.Type keyboard() {
        return InputConstants.Type.KEYSYM;
    }

    /** F1: the game draws no HUD. */
    public static boolean hudHidden() {
        return Minecraft.getInstance().options.hideGui;
    }

    public static BossHealthOverlay bossOverlay() {
        return Minecraft.getInstance().gui.getBossOverlay();
    }
}
