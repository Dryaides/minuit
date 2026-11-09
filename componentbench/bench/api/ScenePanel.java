package bench.api;

import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.Words;

/** The panel behind a scene: a plain PanelComponent at (0, 0). */
public final class ScenePanel extends PanelComponent {
    private final int width;
    private final int height;
    boolean surface = true;

    ScenePanel(Theme theme, Words words, int width, int height) {
        super(theme, words, 0, 0);
        this.width = width;
        this.height = height;
    }

    <W extends BaseComponent> W put(W widget) {
        return add(widget);
    }

    void showOverlay(BaseComponent widget) {
        overlay = widget;
    }

    @Override public int width() { return width; }
    @Override public int height() { return height; }

    @Override
    protected void drawBackground(Draw d, int mx, int my) {
        if (surface) {
            theme.frame().panel(d, theme.palette(), 0, 0, width, height);
        }
    }
}
