package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.List;

/**
 * The screen rectangles a kit panel covers, so a recipe viewer (JEI, REI, EMI) keeps its item list
 * clear of them, as it does for the vanilla container box.
 */
public final class ScreenAreas {

    private ScreenAreas() {
    }

    /**
     * The panel, its visible windows and its open overlay, each one left out when it lies inside
     * {@code container} (the vanilla container box the viewer already avoids), or every one for null.
     */
    public static List<Box> outside(PanelComponent panel, Box container) {
        List<Box> out = new ArrayList<>();
        add(out, panel.bounds(), container);
        for (BaseComponent widget : panel.widgets()) {
            if (widget.visible && widget instanceof WindowComponent) {
                add(out, widget.box(), container);
            }
        }
        BaseComponent open = panel.overlay();
        if (open != null && open.visible) {
            add(out, open.box(), container);
        }
        return out;
    }

    /** True when the point lies under the panel, one of its visible windows or its open overlay. */
    public static boolean covers(PanelComponent panel, int x, int y) {
        for (Box area : outside(panel, null)) {
            if (area.contains(x, y)) {
                return true;
            }
        }
        return false;
    }

    private static void add(List<Box> out, Box area, Box container) {
        if (container == null || !container.contains(area)) {
            out.add(area);
        }
    }
}
