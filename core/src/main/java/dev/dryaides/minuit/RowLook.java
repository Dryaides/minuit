package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Redraw;
import java.util.List;

/**
 * What {@link TableComponent} and {@link TreeComponent} draw the same way: the row's face and ink, the
 * hover's fade, the text's place in a row, the empty state and the disabled scroll bar. A row's look is
 * decided here only, so the two cannot drift apart.
 */
final class RowLook {
    static final int FONT_H = 8;
    private static final int HOVER_ALPHA = 40;
    // A selection without focus keeps the primary colour at half strength: palettes have no colour for it,
    // and FrameStyle.selectedFace is close to the well in some themes (soft dark, vanilla dark).
    private static final int IDLE_SELECTION_ALPHA = 0x80;
    private static final int IDLE_SELECTION_HOVER_ALPHA = 0xA0;
    private static final float DISABLED_FADE = 0.45F;
    private static final double READABLE = 4.5D;
    private static final int LINE_H = FONT_H + 2;
    /** A popup row: two pixels more than a dropdown's line, so a highlight has room above and below the letters. */
    static final int POPUP_ROW = LINE_H + 4;
    /** Where a popup row's content starts and ends inside its highlight. */
    static final int POPUP_PAD = 4;
    private static final int EMPTY_PAD = 3;

    private RowLook() {
    }

    /** The row's background; its alpha is 0 when the row draws none. */
    static int face(Palette p, boolean active, boolean focused, boolean selected, boolean hovered, float hoverFade) {
        if (!active) {
            return selected ? tint(p, 1F) : 0;
        }
        if (!selected) {
            return hovered ? tint(p, hoverFade) : 0;
        }
        if (focused) {
            return hovered ? p.primaryHover() : p.primary();
        }
        float lit = hovered ? hoverFade : 0F;
        int alpha = Math.round(IDLE_SELECTION_ALPHA + (IDLE_SELECTION_HOVER_ALPHA - IDLE_SELECTION_ALPHA) * lit);
        return Shapes.alpha(p.primary(), alpha);
    }

    /** A light wash of the text's colour: it keeps that text readable in every theme, where raisedHover may not. */
    static int tint(Palette p, float strength) {
        return Shapes.alpha(p.text(), Math.round(HOVER_ALPHA * strength));
    }

    /**
     * The colour of a row's text. It does not follow the hover, so the text never flips between dark and
     * light as the pointer crosses a selected row: {@code primaryText} is the theme's ink for both primary
     * faces, and the other faces are judged at rest.
     */
    static int ink(Palette p, int face, boolean selected, boolean active) {
        if (!active) {
            // The palette's faint text, which reads on the well where the disabled text is made for the disabled button. A selected row's wash takes that
            // down to 1.4:1 in some themes (vanilla dark, soft light), so that row keeps a faded ink instead.
            return selected ? Shapes.mix(inkOn(p, p.well()), p.well() | 0xFF000000, DISABLED_FADE) : Ink.readable(p, p.textFaint(), p.well() | 0xFF000000, Ink.MARK);
        }
        if (!selected) {
            return inkOn(p, p.well());
        }
        if (face == p.primary() || face == p.primaryHover()) {
            return p.primaryText();
        }
        return inkOn(p, Shapes.alpha(p.primary(), IDLE_SELECTION_ALPHA));
    }

    /** The quieter colour on a bare well, for what is not the row's text; the row's ink if that would be too weak. */
    static int softInk(Palette p, int ink) {
        return Shapes.contrast(p.textSoft(), p.well() | 0xFF000000) >= READABLE ? p.textSoft() : ink;
    }

    /**
     * The palette's ink that stands out most on a face, seen over the well when the face is translucent.
     * {@code text} is written for the surface, so it can be weak on a well or on the primary colour.
     */
    private static int inkOn(Palette p, int face) {
        return Shapes.textOn(p, Shapes.mix(p.well() | 0xFF000000, face | 0xFF000000, (face >>> 24) / 255F));
    }

    /** The y of a row's line of text. */
    static int textY(int rowY, int rowHeight) {
        return rowY + (rowHeight - FONT_H) / 2;
    }

    /** True when the view would cut a row's line of text: a row draws no slivers of letters at the view's edges. */
    static boolean textCut(int rowY, int rowHeight, int viewY, int viewH) {
        int textY = textY(rowY, rowHeight);
        return viewH >= FONT_H && (textY < viewY || textY + FONT_H > viewY + viewH);
    }

    /** The text shown with no rows: centred in the body, wrapped, and cut where the body ends. */
    static void drawEmpty(Draw d, Palette p, String text, boolean active, int left, int top, int width, int height) {
        if (text == null || text.isEmpty() || height <= 0) {
            return;
        }
        int room = Math.max(1, width - 2 * EMPTY_PAD);
        List<String> lines = d.wrapAll(text, room);
        int count = Math.min(Math.max(1, height / LINE_H), lines.size());
        int lineY = top + Math.max(0, (height - count * LINE_H) / 2) + 1;
        int ink = ink(p, p.well(), false, active);
        int color = active ? softInk(p, ink) : ink;
        int middle = left + width / 2;
        d.scissor(left, top, left + width, top + height);
        for (int i = 0; i < count; i++) {
            String line = i == count - 1 && count < lines.size() ? d.ellipsize(lines.get(i) + Draw.ELLIPSIS, room) : lines.get(i);
            d.text(line, middle - d.width(line) / 2, lineY + i * LINE_H, color);
        }
        d.endScissor();
    }

    /** False when a box is too small to hold its scroll bar, which would otherwise be drawn outside it. */
    static boolean barFits(int boxW, int boxH, int barW) {
        return boxW >= barW + 2 && boxH >= 4;
    }

    /** Fades a scroll bar already drawn: {@link FrameStyle#scroller} has no disabled look of its own. */
    static void dimBar(Draw d, Palette p, int barX, int barY, int barW, int barH) {
        d.fill(barX, barY, barX + barW, barY + barH, Shapes.alpha(p.well(), Math.round(DISABLED_FADE * 255F)));
    }

    /** The row under the pointer and how far its hover has faded in. */
    static final class HoverFade {
        private int row = -1;
        private float fade;
        private long lastFrame = -1L;

        /** Once a frame, with the row under the pointer, or -1. */
        void step(int rowNow) {
            long now = Redraw.quietClock();
            long elapsed = lastFrame < 0L ? 0L : Math.max(0L, now - lastFrame);
            lastFrame = now;
            if (rowNow != row) {
                row = rowNow;
                fade = 0F;
            }
            fade = BaseComponent.reducedMotion() ? 1F : Math.min(1F, fade + elapsed / (float) BaseComponent.HOVER_IN_MS);
            if (fade < 1F) {
                Redraw.ask();
            }
        }

        int row() {
            return row;
        }

        float fade() {
            return fade;
        }
    }
}
