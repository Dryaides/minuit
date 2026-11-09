package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.client.ThemeScreen;
import dev.dryaides.minuit.internal.port.ScreenBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

/**
 * The game's screen manager for core. The key events become a {@code KeyStroke} in the screens themselves
 * ({@link GameInput#stroke}), so {@code stroke} keeps its default. Client only, not API.
 */
final class GameScreenBridge implements ScreenBridge {

    @Override
    public void openThemePage(Object parent, String themeId) {
        Minecraft.getMinecraft().displayGuiScreen(new ThemeScreen((GuiScreen) parent, themeId));
    }

    @Override
    public void onGameThread(Runnable task) {
        Minecraft.getMinecraft().addScheduledTask(task);
    }

    @Override
    public void close() {
        Minecraft.getMinecraft().displayGuiScreen(null);
    }

    @Override
    public boolean singleplayer() {
        return Minecraft.getMinecraft().isIntegratedServerRunning();
    }

    @Override
    public int guiWidth() {
        return new ScaledResolution(Minecraft.getMinecraft()).getScaledWidth();
    }

    @Override
    public int guiHeight() {
        return new ScaledResolution(Minecraft.getMinecraft()).getScaledHeight();
    }
}
