package dev.dryaides.minuit;

/**
 * A paper page and the fold of a book, drawn from a theme's palette.
 */
public final class Paper {

    private Paper() {
    }

    /**
     * One page: an edge, ruled lines, a red margin line on the outer side.
     *
     * @param firstRule y of the first rule below the page's top
     * @param ruleStep  pixels between two rules
     */
    public static void page(Draw d, Palette p, Box box, int rules, int firstRule, int ruleStep,
                            boolean marginOnLeft) {
        Shapes.rounded(d, box.x(), box.y(), box.w(), box.h(), 3, p.surfaceEdge());
        Shapes.rounded(d, box.x() + 1, box.y() + 1, box.w() - 2, box.h() - 3, 2, p.surface());
        int rule = Shapes.alpha(p.textSoft(), 0x30);
        for (int i = 0; i < rules; i++) {
            int ry = box.y() + firstRule + i * ruleStep;
            d.fill(box.x() + 4, ry, box.right() - 4, ry + 1, rule);
        }
        int mx = marginOnLeft ? box.x() + 4 : box.right() - 5;
        d.fill(mx, box.y() + 4, mx + 1, box.bottom() - 6, Shapes.alpha(p.bad(), 0x55));
    }

    /** The fold between two pages, with a ribbon hanging from the top. */
    public static void spine(Draw d, Palette p, Box box) {
        d.fill(box.x(), box.y(), box.right(), box.bottom(), p.frameDark());
        d.fill(box.x() + box.w() / 2 - 1, box.y(), box.x() + box.w() / 2 + 1, box.bottom(), 0x40000000);
        int rx = box.x() + box.w() / 2 - 2;
        int ribbonDark = Shapes.mix(p.bad(), p.frameDark(), 0.4F);
        d.fill(rx, box.y() - 3, rx + 4, box.y() + 30, p.bad());
        d.fill(rx + 3, box.y() - 3, rx + 4, box.y() + 30, ribbonDark);
        d.fill(rx, box.y() + 30, rx + 1, box.y() + 32, p.bad());
        d.fill(rx + 3, box.y() + 30, rx + 4, box.y() + 32, ribbonDark);
    }
}
