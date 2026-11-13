package dev.dryaides.minuit.port.mc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.scores.DisplaySlot;

import java.net.URI;

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
        ConfirmLinkScreen.confirmLinkNow(current(), URI.create(url), false);
    }

    /** The scoreboard objective shown in the sidebar slot, which the game draws on the right. */
    public static boolean sidebarShown(Minecraft client) {
        return client.level != null && client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR) != null;
    }

    public static boolean beneficial(MobEffectInstance effect) {
        return effect.getEffect().value().isBeneficial();
    }
}
