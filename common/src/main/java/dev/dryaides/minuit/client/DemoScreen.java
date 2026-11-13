package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Internal;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.internal.showcase.ShowcasePanel;
import net.minecraft.client.gui.screens.Screen;

/**
 * The kit's components to try, in the look the theme page under it sets ({@link ShowcasePanel}). Escape or the
 * back arrow goes back to that page.
 *
 * <p>Client only.
 */
@Internal
public final class DemoScreen extends MinuitScreen {

    private final Screen parent;
    private final String ownThemeId;
    private String place = ShowcasePanel.HOME;

    public DemoScreen(Screen parent, String ownThemeId) {
        super("minuit.demo.title");
        this.parent = parent;
        this.ownThemeId = ownThemeId;
    }

    @Override
    protected boolean showsThemeTab() {
        return false;
    }

    @Override
    protected PanelComponent build(int screenWidth, int screenHeight) {
        return new ShowcasePanel(words(), Themes.byId(this.ownThemeId), screenWidth, screenHeight, this.place, next -> {
            this.place = next;
            this.panel = build(this.width, this.height);
        }, this::onClose);
    }

    @Override
    public void onClose() {
        if (this.panel != null && this.panel.closeOverlay()) {
            return;
        }
        show(this.parent);
    }
}
