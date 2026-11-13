package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;
import java.util.ArrayList;
import java.util.List;

/**
 * The speech bubble: the words over an entity that speaks ({@code WorldBubbles} in the client) or
 * a hint in a screen ({@link BubbleComponent}). Pure, so the bench draws the same bubble into an
 * image.
 *
 * <pre>
 *   .-----------------------.
 *   | open the door of the  |
 *   | attic please          |
 *   '----------v------------'
 * </pre>
 *
 * The bubble's tail points at the speaker; the box never leaves the screen, the tail stays on the
 * speaker as long as the box can.
 */
public final class Bubble {

    /** Widest line, in GUI pixels: about seven words of the vanilla font. */
    public static final int MAX_LINE_WIDTH = 150;
    public static final int MAX_LINES = 3;
    public static final int PAD_X = 5;
    public static final int PAD_Y = 4;
    public static final int LINE = 10;
    /** Extra rows under the text, so the logo's blue band stays a rim below the words instead of cutting through them. */
    public static final int RIM = 2;
    /** Rows of tail under the bubble, its tip on the speaker's head. */
    public static final int TAIL = 3;
    /** Shown for a clip that carries no words: the bird talks, nobody knows what it said. */
    public static final String NO_WORDS = "...";

    /**
     * The minuit logo's bubble, cut for a 9-slice by {@code tools/make_bubble.py}: the left and
     * right caps keep their pixels, the column between them and one white and one blue row repeat.
     * All the height goes to the white row: the blue only shows as the logo's rim under the text.
     */
    private static final String TEXTURE = "minuit:textures/gui/bubble.png";
    private static final int ATLAS = 64;
    private static final int BODY_W = 47;
    private static final int BODY_H = 41;
    private static final int CAP_LEFT = 6;
    private static final int CAP_RIGHT = 9;
    private static final int ROWS_TOP = 3;
    private static final int ROWS_MID = 2;
    private static final int ROWS_BOTTOM = 4;
    private static final int COPIES = 16;
    private static final int MID_COPIES = 32;
    private static final int TAIL_U = 48;
    /** The logo's navy outline: dark on the white and pale blue fill. */
    private static final int TEXT = 0xFF1E2434;
    /** The thought cloud's body: the logo bubble's white. */
    private static final int CLOUD = 0xFFFFFFFF;
    private static final int BUMP_STEP = 8;

    private static final int FADE_IN_TICKS = 4;
    private static final int FADE_OUT_TICKS = 10;

    private Bubble() {
    }

    /**
     * How long a bubble stays: as long as the clip plays, plus the time to read it at 15
     * characters a second, between 1.5 s and 15 s.
     */
    public static int lifeTicks(int chars, int audioTicks) {
        int reading = 20 + chars * 20 / 15;
        return Maths.clamp(Math.max(audioTicks + 10, reading), 30, 300);
    }

    /** At most {@code max} characters, cut at a word when there is one, ending in "...". */
    public static String clip(String text, int max) {
        String flat = text.strip().replaceAll("\\s+", " ");
        if (flat.length() <= max) {
            return flat;
        }
        int end = max - Draw.ELLIPSIS.length();
        int space = flat.lastIndexOf(' ', end);
        if (space > end / 2) {
            end = space;
        }
        return flat.substring(0, end).stripTrailing() + Draw.ELLIPSIS;
    }

    /** 0 to 1: up over the first ticks, down over the last. */
    public static float alpha(float age, int life) {
        float in = age / FADE_IN_TICKS;
        float out = (life - age) / FADE_OUT_TICKS;
        return Maths.clamp(Math.min(in, out), 0F, 1F);
    }

    /**
     * Words wrapped to {@code maxWidth}, at most {@code maxLines}; a word wider than a line is cut
     * across lines, and what does not fit ends the last line with "...". Lines that fit are evened
     * out: the narrowest width that keeps the same number of lines, so no word sits alone below.
     */
    public static List<String> wrap(Draw d, String text, int maxWidth, int maxLines) {
        String flat = text.strip().replaceAll("\\s+", " ");
        if (flat.isEmpty()) {
            return List.of(NO_WORDS);
        }
        List<String> lines = greedy(d, flat, maxWidth);
        if (lines.size() > maxLines) {
            List<String> kept = new ArrayList<>(lines.subList(0, maxLines));
            String last = kept.get(maxLines - 1);
            String marked = last + Draw.ELLIPSIS;
            kept.set(maxLines - 1, d.width(marked) <= maxWidth ? marked
                    : d.ellipsize(last + " " + lines.get(maxLines), maxWidth));
            return kept;
        }
        int low = 1;
        int high = maxWidth;
        while (low < high) {
            int mid = (low + high) / 2;
            if (greedy(d, flat, mid).size() <= lines.size()) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return greedy(d, flat, high);
    }

    private static List<String> greedy(Draw d, String flat, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : flat.split(" ")) {
            String joined = line.isEmpty() ? word : line + " " + word;
            if (d.width(joined) <= maxWidth) {
                line.setLength(0);
                line.append(joined);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            while (d.width(word) > maxWidth) {
                String head = d.cut(word, maxWidth);
                if (head.isEmpty()) {
                    head = word.substring(0, 1);
                }
                lines.add(head);
                word = word.substring(head.length());
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** The box for these lines, its tail touching {@code anchorX, anchorY}, kept inside the screen. */
    public static Box box(Draw d, List<String> lines, int anchorX, int anchorY, int screenW, int screenH) {
        int textW = 0;
        for (String line : lines) {
            textW = Math.max(textW, d.width(line));
        }
        int w = textW + PAD_X * 2;
        int h = lines.size() * LINE - 2 + PAD_Y * 2 + RIM;
        int x = Maths.clamp(anchorX - w / 2, 2, Math.max(2, screenW - w - 2));
        int y = Maths.clamp(anchorY - TAIL - h, 2, Math.max(2, screenH - h - TAIL - 2));
        return new Box("bubble", x, y, w, h);
    }

    /** Draws the bubble in {@code style}; {@code alpha} 0 draws nothing. */
    public static void draw(Draw d, BubbleStyle style, List<String> lines, Box box, int anchorX, float alpha) {
        float a = Maths.clamp(alpha, 0F, 1F);
        if (a * 255 <= 8) {
            return;
        }
        int x = box.x();
        int y = box.y();
        int w = Math.max(box.w(), CAP_LEFT + 1 + CAP_RIGHT);
        int h = Math.max(box.h(), ROWS_TOP + 1 + ROWS_MID + 1 + ROWS_BOTTOM);
        if (style == BubbleStyle.THOUGHT) {
            cloud(d, x, y, w, h, anchorX, a);
            words(d, lines, x, y, w, a);
            return;
        }
        int extra = h - (ROWS_TOP + ROWS_MID + ROWS_BOTTOM);
        int[] rowSrc = {0, ROWS_TOP, ROWS_TOP + COPIES, ROWS_TOP + COPIES + ROWS_MID, BODY_H - ROWS_BOTTOM};
        int[] rowH = {ROWS_TOP, extra, ROWS_MID, 0, ROWS_BOTTOM};
        int[] rowMax = {ROWS_TOP, COPIES, ROWS_MID, COPIES, ROWS_BOTTOM};
        int[] colSrc = {0, CAP_LEFT, BODY_W - CAP_RIGHT};
        int[] colW = {CAP_LEFT, w - CAP_LEFT - CAP_RIGHT, CAP_RIGHT};
        int[] colMax = {CAP_LEFT, MID_COPIES, CAP_RIGHT};
        int dy = y;
        for (int r = 0; r < rowH.length; r++) {
            int dx = x;
            for (int c = 0; c < colW.length; c++) {
                tile(d, dx, dy, colW[c], rowH[r], colSrc[c], rowSrc[r], colMax[c], rowMax[r], a);
                dx += colW[c];
            }
            dy += rowH[r];
        }
        int tipX = Maths.clamp(anchorX, x + 5, x + w - 8);
        d.blit(TEXTURE, tipX - 1, y + h - 1, TAIL_U, 0, 4, 4, ATLAS, ATLAS, a);
        words(d, lines, x, y, w, a);
    }

    private static void words(Draw d, List<String> lines, int x, int y, int w, float a) {
        int color = Shapes.alpha(TEXT, Math.round(255 * a));
        if (lines.size() == 1 && lines.get(0).equals(NO_WORDS)) {
            int dotX = x + (w - 8) / 2;
            int dotY = y + PAD_Y + 3;
            for (int i = 0; i < 3; i++) {
                d.fill(dotX + 3 * i, dotY, dotX + 3 * i + 2, dotY + 2, color);
            }
            return;
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            d.text(line, x + (w - d.width(line)) / 2, y + PAD_Y + i * LINE, color);
        }
    }

    /**
     * The thought cloud, drawn in pixels rather than from the atlas: a rounded body, three-pixel bumps along the top
     * and bottom edges every {@link #BUMP_STEP} pixels, and two circles stepping down to the speaker, the larger by
     * the body.
     */
    private static void cloud(Draw d, int x, int y, int w, int h, int anchorX, float a) {
        int ink = Shapes.alpha(TEXT, Math.round(255 * a));
        int body = Shapes.alpha(CLOUD, Math.round(255 * a));
        Shapes.rounded(d, x, y, w, h, 3, ink);
        Shapes.rounded(d, x + 1, y + 1, w - 2, h - 2, 2, body);
        int tipX = Maths.clamp(anchorX, x + 5, x + w - 8);
        int bumps = Math.max(1, (w - 8) / BUMP_STEP);
        int first = x + (w - (bumps - 1) * BUMP_STEP) / 2;
        for (int i = 0; i < bumps; i++) {
            int cx = first + i * BUMP_STEP;
            bump(d, cx, y, -1, ink, body);
            // The trail leaves the bottom edge at tipX: a bump there would merge with its first circle.
            if (cx + 3 < tipX - 1 || cx - 3 > tipX + 5) {
                bump(d, cx, y + h - 1, 1, ink, body);
            }
        }
        int ty = y + h;
        d.fill(tipX + 1, ty, tipX + 4, ty + 1, ink);
        d.fill(tipX, ty + 1, tipX + 5, ty + 3, ink);
        d.fill(tipX + 1, ty + 1, tipX + 4, ty + 3, body);
        d.fill(tipX + 1, ty + 3, tipX + 4, ty + 4, ink);
        d.fill(tipX - 2, ty + 4, tipX - 1, ty + 5, ink);
        d.fill(tipX - 3, ty + 5, tipX, ty + 6, ink);
        d.fill(tipX - 2, ty + 5, tipX - 1, ty + 6, body);
        d.fill(tipX - 2, ty + 6, tipX - 1, ty + 7, ink);
    }

    /** One bump centred on {@code cx}, standing out of the edge row {@code edgeY} upward ({@code dir} -1) or downward. */
    private static void bump(Draw d, int cx, int edgeY, int dir, int ink, int body) {
        int outer = edgeY + 2 * dir;
        int inner = edgeY + dir;
        d.fill(cx - 1, outer, cx + 2, outer + 1, ink);
        d.fill(cx - 2, inner, cx - 1, inner + 1, ink);
        d.fill(cx + 2, inner, cx + 3, inner + 1, ink);
        d.fill(cx - 1, inner, cx + 2, inner + 1, body);
        d.fill(cx - 1, edgeY, cx + 2, edgeY + 1, body);
    }

    /** {@code w x h} at {@code x, y} filled with the atlas piece at {@code u, v}, repeated when it is smaller. */
    private static void tile(Draw d, int x, int y, int w, int h, int u, int v, int maxW, int maxH, float alpha) {
        for (int oy = 0; oy < h; oy += maxH) {
            for (int ox = 0; ox < w; ox += maxW) {
                d.blit(TEXTURE, x + ox, y + oy, u, v, Math.min(maxW, w - ox), Math.min(maxH, h - oy), ATLAS, ATLAS,
                        alpha);
            }
        }
    }
}
