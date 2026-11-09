package dev.dryaides.minuit;

/**
 * The small tab that opens the theme page, sticking out of a kit screen's top left edge like the
 * creative inventory's tabs. The host draws it before the panel, so the panel's frame covers its
 * foot. When the panel leaves no room above it, the tab sticks out of the left edge instead.
 */
public final class ThemeTab {

    /** Across and out of the edge, foot included. */
    public static final int ACROSS = 37;
    public static final int OUT = 16;
    /** The part under the panel's frame. */
    static final int FOOT = 3;
    /** From the panel's corner to the tab. */
    static final int INSET = 6;
    /** The word on a tab above the panel: a proper name, the same in every language, so the tab keeps one width. */
    static final String WORD = "minuit";
    /** minuit's clock, on a tab beside the panel where the word does not fit. */
    private static final IconArt LOGO = new AtlasIcon("minuit:textures/gui/theme_tab.png", 0, 0, 11, 11, 11, 11);

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
        IconArt themed = theme.icon(Icon.PALETTE);
        boolean over = isOver(panel, mx, my);
        // Face inside the frame's 1 px outline; the panel's own frame closes the foot side.
        int faceX = tab.x() + 1;
        int faceY = tab.y() + 1;
        int faceW = above ? shownW - 2 : shownW - 1;
        int faceH = above ? shownH - 1 : shownH - 2;
        if (themed != Icon.PALETTE.glyph()) {
            d.icon(themed, faceX + (faceW - themed.width()) / 2, faceY + (faceH - themed.height()) / 2,
                    over ? p.accent() : p.text());
        } else if (above) {
            d.text(WORD, faceX + (faceW - d.width(WORD) + 1) / 2, faceY + (faceH - 7) / 2, over ? p.accent() : p.text());
        } else {
            // A texture takes no tint: the clock dims at rest and lights up under the pointer instead.
            LOGO.draw(d, faceX + (faceW - LOGO.width()) / 2, faceY + (faceH - LOGO.height()) / 2, 0, over ? 1F : 0.8F);
        }
    }
}
