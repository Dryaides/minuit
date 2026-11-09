package dev.dryaides.minuit;

/**
 * The inside of a floating surface: a panel of its own, as a dialog holds one, because a panel already does
 * Tab that wraps, the arrows, the focus ring, typing and popups. Draws no background; its owner does.
 */
final class SurfaceBody extends PanelComponent {
    private int bodyW;
    private int bodyH;

    /** @param words the host panel's, so default texts inside translate as they do on the panel */
    SurfaceBody(Theme theme, Words words) {
        super(theme, words, 0, 0);
    }

    void use(Theme current) {
        theme = current;
    }

    void showOverlay(BaseComponent widget) {
        overlay = widget;
    }

    void place(int x, int y, int w, int h) {
        left = x;
        top = y;
        bodyW = w;
        bodyH = h;
    }

    <W extends BaseComponent> W put(W widget) {
        return add(widget);
    }

    void clearWidgets() {
        focus(null);
        widgets.clear();
    }

    /** True when Tab or Shift-Tab would leave this panel's last or first stop. */
    boolean atEnd(boolean backwards) {
        java.util.List<BaseComponent> all = stops();
        if (all.isEmpty()) {
            return true;
        }
        BaseComponent at = focus() != null ? focus() : cursor();
        return at == null ? false : all.get(backwards ? 0 : all.size() - 1) == at;
    }

    @Override public int width() { return bodyW; }
    @Override public int height() { return bodyH; }

    @Override
    protected void drawBackground(Draw d, int mx, int my) {
        // the owner draws the card
    }
}
