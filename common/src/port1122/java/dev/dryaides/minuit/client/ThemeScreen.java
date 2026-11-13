package dev.dryaides.minuit.client;

import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemePickerPanel;
import dev.dryaides.minuit.Themes;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

import javax.annotation.Nullable;

/**
 * The theme page over a mod's screen, setting the look of that mod's screens. A pick is saved and the page is drawn again in it at once;
 * Escape or the back arrow goes back to {@code parent}, which the game builds again, so it shows
 * the pick too. The cross closes everything, except a container screen under the page: the player
 * goes back to it.
 *
 * <p>Client only.
 */
public final class ThemeScreen extends MinuitScreen {

    private final @Nullable GuiScreen parent;
    private final String ownThemeId;

    /** @param ownThemeId the id the opening mod's screens use with {@code Themes.byId}: the mod whose look the page sets */
    public ThemeScreen(@Nullable GuiScreen parent, String ownThemeId) {
        super("minuit.picker.title");
        this.parent = parent;
        this.ownThemeId = ownThemeId;
    }

    @Override
    protected boolean showsThemeTab() {
        return false;
    }

    @Override
    protected PanelComponent build(int screenWidth, int screenHeight) {
        Theme own = Themes.registered(this.ownThemeId);
        return new ThemePickerPanel(words(), ThemePickerPanel.left(screenWidth), ThemePickerPanel.top(screenHeight),
                own, MinuitClient.themeChoices().forMod(own.id()), picked -> {
            MinuitClient.themeChoice(own.id(), picked);
            this.panel = build(this.width, this.height);
        }, picked -> {
            MinuitClient.themeChoiceEveryMod(picked);
            this.panel = build(this.width, this.height);
        }, this::closeAll, this.parent == null ? null : this::onClose, () -> show(new DemoScreen(this, this.ownThemeId)));
    }

    private void closeAll() {
        show(this.parent instanceof GuiContainer ? this.parent : null);
    }

    @Override
    public void onClose() {
        if (this.panel != null && this.panel.closeOverlay()) {
            return;
        }
        show(this.parent);
    }
}
