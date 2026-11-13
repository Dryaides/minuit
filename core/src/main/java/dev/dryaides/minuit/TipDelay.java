package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Redraw;
import dev.dryaides.minuit.internal.port.Platform;
import java.util.ArrayList;
import java.util.List;

/** Holds a tooltip back until the same tip has stayed under the mouse for {@link #MS}, on {@link BaseComponent#clock()}. */
@Internal
public final class TipDelay {

    /** How long a tip waits under the mouse before it shows; 0 by default (owner 2026-11-11), a mod may set its own. */
    public static long MS = 0;

    private static final int PAD = ItemTipLook.PAD;

    private List<String> lines;
    private long since;

    /** Draws {@code shown} (null for none) in the game's tooltip once it has been the same tip for {@link #MS}. */
    public void draw(Draw d, List<String> shown, int mx, int my) {
        draw(d, null, shown, mx, my);
    }

    /**
     * Draws {@code shown} (null for none) once it has been the same tip for {@link #MS}: in {@code theme}'s popup
     * frame and inks, or in the game's tooltip for a null theme. Blank lines are dropped; a tip with none left
     * draws nothing.
     */
    public void draw(Draw d, Theme theme, List<String> shown, int mx, int my) {
        draw(d, theme, shown, mx, my, null);
    }

    /**
     * As {@link #draw(Draw, Theme, List, int, int)}, a themed popup kept inside {@code area} (the canvas a host
     * without a screen size gives) when it is not null, else inside the screen when its size is known.
     */
    public void draw(Draw d, Theme theme, List<String> shown, int mx, int my, Box area) {
        long now = Redraw.quietClock();
        if (shown == null || !shown.equals(this.lines)) {
            this.lines = shown;
            this.since = now;
        }
        List<String> kept = new ArrayList<>();
        if (shown != null) {
            for (String line : shown) {
                if (line != null && !line.isBlank()) {
                    kept.add(line);
                }
            }
        }
        if (kept.isEmpty()) {
            return;
        }
        if (now - this.since < MS) {
            Redraw.ask();
        } else if (theme == null) {
            d.tooltip(kept, mx, my);
        } else {
            themed(d, theme, kept, mx, my, area);
        }
    }

    /** The game's sixteen format colours, by code 0-9 a-f. */
    private static final int[] CODES = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    /**
     * {@code row} with every colour code that falls under 4.5:1 on {@code face} turned into a reset to the line's
     * base ink.
     * Item lines carry the game's colours, chosen for its dark tooltip (white names, grey lore); on a pale popup
     * they vanish, while a rarity colour that still reads keeps its meaning.
     */
    static String legible(String row, int face) {
        if (row.indexOf('\u00a7') < 0) {
            return row;
        }
        StringBuilder out = new StringBuilder(row.length());
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c == '\u00a7' && i + 1 < row.length()) {
                int code = Character.digit(row.charAt(i + 1), 16);
                if (code >= 0 && !ItemTipLook.reads(CODES[code], face)) {
                    out.append('\u00a7').append('r');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /** The game's placement (12 px right of the pointer, 12 px up, kept on screen) in the theme's popup. */
    private static void themed(Draw d, Theme theme, List<String> kept, int mx, int my, Box area) {
        List<String> rows = new ArrayList<>();
        List<Boolean> head = new ArrayList<>();
        for (int i = 0; i < kept.size(); i++) {
            for (String row : d.wrapAll(kept.get(i), Draw.TOOLTIP_WIDTH)) {
                rows.add(row);
                head.add(i == 0);
            }
        }
        int textW = 0;
        for (String row : rows) {
            textW = Math.max(textW, d.width(row));
        }
        int w = textW + 2 * PAD;
        int h = rows.size() * 10 - 2 + 2 * PAD;
        int[] at = place(mx, my, w, h, area, Platform.screens().guiWidth(), Platform.screens().guiHeight());
        int x = at[0];
        int y = at[1];
        ItemTipLook look = new ItemTipLook(theme);
        d.nextLayer();
        look.frame(d, x + PAD, y + PAD, textW, h - 2 * PAD);
        int ty = y + PAD;
        for (int i = 0; i < rows.size(); i++) {
            d.text(legible(rows.get(i), look.face()), x + PAD, ty, head.get(i) ? look.ink() : look.soft());
            ty += 10;
        }
    }

    /**
     * Top-left of a {@code w} x {@code h} popup for a pointer at {@code mx},{@code my}: 12 px right and up, flipped
     * left of the pointer when it would cross the right edge, lifted when it would cross the bottom. The edges are
     * {@code area}'s when given, else the screen's; a size of 0 (no screen known) never clamps.
     */
    static int[] place(int mx, int my, int w, int h, Box area, int screenW, int screenH) {
        int left = 0;
        int top = 0;
        int right = screenW;
        int bottom = screenH;
        if (area != null) {
            left = area.x();
            top = area.y();
            right = area.right();
            bottom = area.bottom();
        }
        int x = mx + 12;
        int y = my - 12 - PAD;
        if (right > 0 && x + w > right - 2) {
            x = Math.max(left + 2, mx - 12 - w);
        }
        if (bottom > 0 && y + h > bottom - 2) {
            y = bottom - 2 - h;
        }
        y = Math.max(top + 2, y);
        return new int[] {x, y};
    }
}
