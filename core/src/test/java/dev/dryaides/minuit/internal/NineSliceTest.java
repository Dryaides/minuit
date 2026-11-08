package dev.dryaides.minuit.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Expected values worked by hand from 1.20.1's blitNineSliced, blitRepeating, slices and Divisor. */
class NineSliceTest {

    /**
     * AbstractButton's 200 by 20 widgets.png button (slices 20 wide, 4 high) drawn 450 wide at (10, 5): the
     * 410 px middle over a 160 px source is ceil(410 / 160) = 3 parts, Divisor gives 136, 137, 137, each
     * cut from the middle of the source (u 20 + 24 / 2 = 32, then 20 + 23 / 2 = 31); nothing is stretched.
     */
    @Test
    void buttonMiddleRepeatsInThreeCentredParts() {
        PartTable.Region button = new PartTable.Region("minecraft:textures/gui/widgets.png", 0, 66, 200, 20, 256, 256, 20, 4);
        List<NineSlice.Piece> pieces = NineSlice.pieces(button, 10, 5, 450, 20);
        assertEquals(15, pieces.size());
        int area = 0;
        for (NineSlice.Piece p : pieces) {
            area += p.w() * p.h();
            assertEquals(p.w(), p.uw());
            assertEquals(p.h(), p.vh());
        }
        assertEquals(450 * 20, area);
        // centre row: left edge, three middle parts, right edge
        List<NineSlice.Piece> centre = pieces.subList(5, 10);
        int[] x = {10, 30, 166, 303, 440};
        int[] u = {0, 32, 31, 31, 180};
        int[] w = {20, 136, 137, 137, 20};
        for (int i = 0; i < 5; i++) {
            assertEquals(x[i], centre.get(i).x());
            assertEquals(u[i], centre.get(i).u());
            assertEquals(w[i], centre.get(i).w());
            assertEquals(9, centre.get(i).y());
            assertEquals(70, centre.get(i).v());
            assertEquals(12, centre.get(i).h());
        }
    }

    /**
     * TabButton's 130 by 24 tab (borders 2, 2, 2 and 0 at the bottom) drawn 60 wide: no bottom row, and the
     * 56 px middle is one part taken from the centre of the 126 px source, u = 2 + 70 / 2 = 37.
     */
    @Test
    void tabHasNoBottomRowAndCropsItsMiddle() {
        PartTable.Region tab = new PartTable.Region("minecraft:textures/gui/tab_button.png", 0, 48, 130, 24, 256, 256, 2, 2, 2, 0);
        List<NineSlice.Piece> pieces = NineSlice.pieces(tab, 0, 0, 60, 24);
        assertEquals(6, pieces.size());
        NineSlice.Piece middle = pieces.get(4);
        assertEquals(2, middle.x());
        assertEquals(2, middle.y());
        assertEquals(56, middle.w());
        assertEquals(22, middle.h());
        assertEquals(37, middle.u());
        assertEquals(50, middle.v());
        assertEquals(56, middle.uw());
    }

    /**
     * The vanilla panel from inventory.png: in the 1.18.2 to 26.1.2 jars, columns 7 to 170 of rows 7 to 158
     * hold the player box, the crafting grid and the slots. Whatever its size, the panel covers its box once
     * and never reads that zone outside the plain fill.
     */
    @Test
    void panelReadsOnlyTheInventoryBorderAndItsPlainFill() {
        PartTable.Framed panel = (PartTable.Framed) PartTable.ATLAS.resolve(GuiPart.PANEL);
        PartTable.Region fill = panel.fill();
        int[][] sizes = {{176, 166}, {420, 260}, {40, 30}, {12, 9}};
        for (int[] size : sizes) {
            boolean[] covered = new boolean[size[0] * size[1]];
            for (NineSlice.Piece p : NineSlice.pieces(panel, 0, 0, size[0], size[1])) {
                for (int i = 0; i < p.w(); i++) {
                    for (int j = 0; j < p.h(); j++) {
                        int cell = (p.y() + j) * size[0] + p.x() + i;
                        assertEquals(false, covered[cell], "drawn twice at " + size[0] + "x" + size[1]);
                        covered[cell] = true;
                    }
                }
                boolean inFill = p.u() >= fill.u() && p.u() + p.uw() <= fill.u() + fill.w()
                        && p.v() >= fill.v() && p.v() + p.vh() <= fill.v() + fill.h();
                boolean touchesArt = p.u() < 171 && p.u() + p.uw() > 7 && p.v() < 159 && p.v() + p.vh() > 7;
                assertEquals(false, touchesArt && !inFill, "reads slot art at " + size[0] + "x" + size[1] + ": " + p);
            }
            for (boolean c : covered) {
                assertEquals(true, c, "hole at " + size[0] + "x" + size[1]);
            }
        }
    }
}
