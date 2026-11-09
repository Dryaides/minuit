package dev.dryaides.minuit.port.mc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.Sys;

import java.awt.Desktop;
import java.net.URI;

/** The game calls that moved between Minecraft versions: one copy per port variant. */
public final class GameScreens {

    private GameScreens() {
    }

    public static GuiScreen current() {
        return Minecraft.getMinecraft().currentScreen;
    }

    /** The player answers, then comes back to the open screen. */
    public static void confirmLink(String url) {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen back = current();
        mc.displayGuiScreen(new GuiConfirmOpenLink((open, id) -> {
            if (open) {
                openUri(url);
            }
            mc.displayGuiScreen(back);
        }, url, 0, false));
    }

    /** GuiScreen's own opener is private in 1.12.2; LWJGL 2's is the fallback where AWT has no desktop. */
    private static void openUri(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception e) {
            LogManager.getLogger("minuit").warn("Could not open {}: {}", url, e.toString());
        }
        Sys.openURL(url);
    }

    /** The player's field of view setting, in degrees. */
    public static double fov() {
        return Minecraft.getMinecraft().gameSettings.fovSetting;
    }
}
