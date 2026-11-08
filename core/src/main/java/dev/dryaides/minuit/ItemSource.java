package dev.dryaides.minuit;

import java.util.List;

/**
 * A widget that shows items: a slot, a picker cell, an item icon. A recipe viewer asks the screen
 * which item is under the mouse ({@code MinuitScreen.itemUnderMouse}) for its R and U keys and
 * bookmarks; the screen asks its widgets through this.
 */
public interface ItemSource {

    /** The item drawn at the mouse, screen coordinates, or null. */
    ItemView itemAt(double mx, double my);

    /** The item under the mouse among the panel's widgets, the open overlay and the topmost widget first; or null. */
    static ItemView under(PanelComponent panel, double mx, double my) {
        if (panel.overlay() instanceof ItemSource open && panel.overlay().isOver(mx, my)) {
            return open.itemAt(mx, my);
        }
        List<BaseComponent> widgets = panel.widgets();
        for (int i = widgets.size() - 1; i >= 0; i--) {
            BaseComponent widget = widgets.get(i);
            if (!widget.isOver(mx, my)) {
                continue;
            }
            // the topmost widget under the mouse hides the ones under it
            return widget instanceof ItemSource source ? source.itemAt(mx, my) : null;
        }
        return null;
    }
}
