package dev.dryaides.minuit;

/**
 * The small tab that opens the theme page, sticking out of a kit screen's top left edge like the
 * creative inventory's tabs. The host draws it before the panel, so the panel's frame covers its
 * foot. When the panel leaves no room above it, the tab sticks out of the left edge instead.
 */
public final class ThemeTab {

    /** Across and out of the edge, foot included. */
    public static final int ACROSS = 20;
    public static final int OUT = 16;
    /** The part under the panel's frame. */
    static final int FOOT = 3;
    /** From the panel's corner to the tab. */
    static final int INSET = 6;

    private ThemeTab() {
    }

    /** Where the tab sits for a panel at {@code panel}. */
    public static Box at(Box panel) {
        if (panel.y() >= OUT - FOOT) {
            return new Box("theme tab", panel.x() + INSET, panel.y() - OUT + FOOT, ACROSS, OUT);
        }
        return new Box("theme tab", panel.x() - OUT + FOOT, panel.y() + INSET, OUT, ACROSS);
    }

    /** The part of the tab that takes a click: what the panel does not cover. */
    public static boolean isOver(Box panel, double mx, double my) {
        return at(panel).contains(mx, my) && !panel.contains(mx, my);
    }

    public static void draw(Draw d, Theme theme, Box panel, int mx, int my) {
        Box tab = at(panel);
        Palette p = theme.palette();
        theme.frame().panel(d, p, tab.x(), tab.y(), tab.w(), tab.h());
        boolean above = tab.y() < panel.y();
        int shownW = above ? tab.w() : tab.w() - FOOT;
        int shownH = above ? tab.h() - FOOT : tab.h();
        IconArt icon = theme.icon(Icon.PALETTE);
        d.icon(icon, tab.x() + (shownW - icon.width()) / 2, tab.y() + (shownH - icon.height()) / 2,
                isOver(panel, mx, my) ? p.accent() : p.text());
    }
}
