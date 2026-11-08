package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.internal.HostCore;
import dev.dryaides.minuit.internal.port.KeyStroke;
import dev.dryaides.minuit.internal.port.Platform;

/**
 * What a game screen drawing the kit takes from the game: the sound cues and the clipboard, the
 * frame time, and the theme tab. {@link MinuitScreen} holds one; a container screen that draws a kit
 * panel holds its own and calls it from the same places.
 *
 * <p>Client only.
 */
public final class ScreenHost extends HostCore {

    /**
     * @param screen the game screen holding this host, which the theme page goes back to. Typed
     *               as Object so this class names no Minecraft type (see {@code common/fence.tsv}).
     */
    public ScreenHost(Object screen) {
        super(screen);
    }

    /** Draws the theme tab of a panel at {@code panel}; before the panel, which covers its foot. */
    public void drawTab(GuiDraw d, Theme theme, Box panel, int mx, int my) {
        super.drawTab((Draw) d, theme, panel, mx, my);
    }

    /** Draws the tab's tip; after everything, where the panel's own tip goes. */
    public void drawTabTip(GuiDraw d, Box panel, int mx, int my) {
        super.drawTabTip((Draw) d, panel, mx, my);
    }

    /**
     * Gives a key to the panel: the focused field, then moving between the widgets with Tab, the
     * arrows, Enter, Space and Escape ({@link PanelComponent#keyPressed}). From the screen's
     * {@code keyPressed}, with the game's {@code KeyEvent}; false lets the screen use the key.
     */
    public boolean keyPressed(PanelComponent panel, Object event) {
        return keyPressed(panel, Platform.screens().stroke(event));
    }

    /** As {@link #keyPressed(PanelComponent, Object)}, for a screen that converts the key itself. */
    @Override
    public boolean keyPressed(PanelComponent panel, KeyStroke stroke) {
        return super.keyPressed(panel, stroke);
    }
}
