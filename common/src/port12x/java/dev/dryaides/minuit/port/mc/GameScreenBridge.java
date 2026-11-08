package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.client.ThemeScreen;
import dev.dryaides.minuit.internal.port.ScreenBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The game's screen manager for core. The int-coded key events become a {@code KeyStroke} in the
 * screens themselves ({@link GameInput#stroke}), so {@code stroke} keeps its default. Client only, not API.
 */
final class GameScreenBridge implements ScreenBridge {

    @Override
    public void openThemePage(Object parent, String themeId) {
        Minecraft.getInstance().setScreen(new ThemeScreen((Screen) parent, themeId));
    }

    @Override
    public void onGameThread(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    @Override
    public void close() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean singleplayer() {
        return Minecraft.getInstance().hasSingleplayerServer();
    }

    @Override
    public int guiWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    @Override
    public int guiHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
