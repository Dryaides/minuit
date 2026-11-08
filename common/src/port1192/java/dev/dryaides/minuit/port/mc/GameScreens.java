package dev.dryaides.minuit.port.mc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** The game calls that moved between Minecraft versions: one copy per port variant. */
public final class GameScreens {

    private GameScreens() {
    }

    public static Screen current() {
        return Minecraft.getInstance().screen;
    }

    public static ToastComponent toasts() {
        return Minecraft.getInstance().getToasts();
    }

    public static InputConstants.Type keyboard() {
        return InputConstants.Type.KEYSYM;
    }

    /** The player answers, then comes back to the open screen. */
    public static void confirmLink(String url) {
        Minecraft mc = Minecraft.getInstance();
        Screen back = current();
        mc.setScreen(new ConfirmLinkScreen(open -> {
            if (open) {
                Util.getPlatform().openUri(url);
            }
            mc.setScreen(back);
        }, url, false));
    }

    public static MutableComponent literal(String text) {
        return Component.literal(text);
    }

    public static MutableComponent translatable(String key) {
        return Component.translatable(key);
    }

    /** The player's field of view setting, in degrees. */
    public static double fov() {
        return Minecraft.getInstance().options.fov().get();
    }
}
