package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemeTab;
import dev.dryaides.minuit.TipDelay;
import dev.dryaides.minuit.internal.compat.ViewerSurface;
import dev.dryaides.minuit.internal.port.KeyStroke;
import dev.dryaides.minuit.internal.port.Platform;

import java.util.List;
import java.util.function.BooleanSupplier;

/** The body of {@code client.ScreenHost}, on core types. Not API: mods use {@code ScreenHost}. */
public class HostCore {

    private final Object screen;
    private final TipDelay tip = new TipDelay();
    private long lastFrame;
    private final GamepadInput pad = new GamepadInput();
    private boolean away;
    private BooleanSupplier themeTab = () -> true;
    private ViewerSurface viewers = () -> true;

    /** @param screen the game screen holding this host, which the theme page goes back to. */
    public HostCore(Object screen) {
        this.screen = screen;
    }

    /** Whether the screen shows the theme tab, read on every frame and click: the screen's {@code showsThemeTab}. */
    public void themeTab(BooleanSupplier shows) {
        this.themeTab = shows;
    }

    /** What the recipe viewer plugins read from this screen: the container screen's {@code showsContainerAddons}. */
    public void viewers(ViewerSurface surface) {
        this.viewers = surface;
    }

    public ViewerSurface viewers() {
        return this.viewers;
    }

    /** From the screen's {@code init}, before the panel is built. */
    public void init() {
        Platform.plugIn();
        this.away = false;
    }

    /** The time since the last frame in ticks, for {@code PanelComponent.draw}; once per frame. */
    public float frame() {
        long now = BaseComponent.clock().getAsLong();
        float delta = this.lastFrame == 0 ? 1F : Math.min(5F, (now - this.lastFrame) / 50F);
        this.lastFrame = now;
        return delta;
    }

    /** Draws the theme tab of a panel at {@code panel}; before the panel, which covers its foot. */
    public void drawTab(Draw d, Theme theme, Box panel, int mx, int my) {
        if (this.themeTab.getAsBoolean()) {
            ThemeTab.draw(d, theme, panel, mx, my);
        }
    }

    /** Draws the tab's tip; after everything, where the panel's own tip goes. */
    public void drawTabTip(Draw d, Box panel, int mx, int my) {
        drawTabTip(d, null, panel, mx, my);
    }

    /** {@link #drawTabTip(Draw, Box, int, int)} in {@code theme}'s popup, or the game's tooltip for null. */
    public void drawTabTip(Draw d, Theme theme, Box panel, int mx, int my) {
        boolean over = this.themeTab.getAsBoolean() && ThemeTab.isOver(panel, mx, my);
        this.tip.draw(d, theme, over ? List.of(Platform.words().get("minuit.picker.title")) : null, mx, my);
    }

    /**
     * Gives a key to the panel: the focused field, then moving between the widgets with Tab, the
     * arrows, Enter, Space and Escape ({@link PanelComponent#keyPressed}); false lets the screen use the key.
     */
    public boolean keyPressed(PanelComponent panel, KeyStroke stroke) {
        return KeyRouter.press(panel, stroke, Platform.keyCodes());
    }

    /**
     * One controller poll from the screen's tick: new button presses and stick or d-pad repeats go to the panel as
     * arrows, Tab, Enter and Space-like keys. True when Back or B was pressed, which the screen answers with its
     * {@code onClose} (that closes an open overlay first).
     */
    public boolean gamepad(PanelComponent panel, long nowMs, boolean[] buttons, float[] axes) {
        boolean close = false;
        for (GamepadInput.Event e : this.pad.update(nowMs, buttons, axes)) {
            if (e.key() == InputKey.ESCAPE) {
                close = true;
            } else if (panel != null) {
                panel.keyPressed(e.key(), e.shift());
            }
        }
        return close;
    }

    /** Gives a let-go key to the panel's capturing widget; false lets the screen use it. */
    public boolean keyReleased(PanelComponent panel, KeyStroke stroke) {
        return KeyRouter.release(panel, stroke);
    }

    /** Opens the theme page on a click on the tab; back from it, the game builds the screen again. */
    public boolean clickTab(Theme theme, Box panel, double mx, double my) {
        if (!this.themeTab.getAsBoolean() || !ThemeTab.isOver(panel, mx, my)) {
            return false;
        }
        BaseComponent.click();
        this.away = true;
        Platform.screens().openThemePage(this.screen, theme.id());
        return true;
    }

    /**
     * True from a click on the tab until the next {@link #init}: a {@code removed} then means the
     * screen comes back, so a container screen keeps its menu and does not send its closing save.
     */
    public boolean away() {
        return this.away;
    }
}
