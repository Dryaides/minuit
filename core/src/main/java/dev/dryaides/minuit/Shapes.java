package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.GuiPart;
import dev.dryaides.minuit.internal.Maths;

/** The shapes every frame style is drawn with. */
public final class Shapes {

    private Shapes() {
    }

    /** A sunken area, a slot or a field: a 2 px rounded edge, then the fill one pixel in. */
    public static void well(Draw d, int x, int y, int w, int h, int edge, int fill) {
        rounded(d, x, y, w, h, 2, edge);
        d.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    /** A filled rectangle with rounded corners (radius in pixels, 0 to 6). */
    public static void rounded(Draw d, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        d.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int inset = cornerInset(r, i);
            d.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            d.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /**
     * The 1 px outline of {@link #rounded} with the same box and radius: the pixels of its mask
     * that touch a pixel outside it. Radius 0 gives a plain rectangle outline.
     */
    public static void ring(Draw d, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int row = 0; row < h; row++) {
            int inset = rowInset(r, h, row);
            if (row == 0 || row == h - 1) {
                d.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
                continue;
            }
            int open = Math.max(rowInset(r, h, row - 1), rowInset(r, h, row + 1));
            int run = Math.max(1, open - inset);
            d.fill(x + inset, y + row, x + inset + run, y + row + 1, color);
            d.fill(x + w - inset - run, y + row, x + w - inset, y + row + 1, color);
        }
    }

    /**
     * The pixels of {@link #rounded} at x, y, w, h, r that lie outside the same shape {@code t} pixels in with radius
     * {@code r - t}: a ring {@code t} thick with no gap at its corners, which two {@link #ring}s one pixel apart leave.
     */
    public static void band(Draw d, int x, int y, int w, int h, int r, int t, int color) {
        if (w <= 0 || h <= 0 || t <= 0) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int iw = w - 2 * t;
        int ih = h - 2 * t;
        int ir = Math.max(0, Math.min(r - t, Math.min(iw, ih) / 2));
        for (int row = 0; row < h; row++) {
            int inset = rowInset(r, h, row);
            int inner = row - t;
            if (iw <= 0 || ih <= 0 || inner < 0 || inner >= ih) {
                d.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
                continue;
            }
            int open = Math.max(inset, t + rowInset(ir, ih, inner));
            d.fill(x + inset, y + row, x + open, y + row + 1, color);
            d.fill(x + w - open, y + row, x + w - inset, y + row + 1, color);
        }
    }

    /** Horizontal inset of row {@code row} (0 = top) of a corner of radius r. */
    private static int cornerInset(int r, int row) {
        double dy = r - row - 0.5;
        double dx = Math.sqrt(Math.max(0, r * r - dy * dy));
        return (int) Math.round(r - dx);
    }

    /** The way an {@link #arrow} points. */
    enum Point {
        UP, DOWN, LEFT, RIGHT
    }

    /**
     * A pixel triangle: row {@code i} from the tip is {@code 2i + 1} pixels wide, centred on the tip pixel, so the
     * width is odd, the tip is one pixel and both sides step the same. Every arrow, caret and pointer is drawn here.
     */
    static void arrow(Draw d, int tipX, int tipY, int height, Point way, int color) {
        for (int i = 0; i < height; i++) {
            switch (way) {
                case UP -> d.fill(tipX - i, tipY + i, tipX + i + 1, tipY + i + 1, color);
                case DOWN -> d.fill(tipX - i, tipY - i, tipX + i + 1, tipY - i + 1, color);
                case LEFT -> d.fill(tipX + i, tipY - i, tipX + i + 1, tipY + i + 1, color);
                case RIGHT -> d.fill(tipX - i, tipY - i, tipX - i + 1, tipY + i + 1, color);
            }
        }
    }

    /** How a raised surface catches the light from the top left. */
    public enum Relief {
        /** Lit top and left edges, shaded bottom and right. */
        RAISED,
        /** The same bevel inverted: held down under the mouse. */
        PRESSED,
        /** No bevel: a control that cannot be used. */
        FLAT
    }

    /** A raised surface: drop shadow, face, bevel. {@code highlight} false draws it flat. */
    public static void pill(Draw d, int x, int y, int w, int h, int color, int shade, boolean highlight) {
        pill(d, x, y, w, h, color, shade, highlight ? Relief.RAISED : Relief.FLAT);
    }

    public static void pill(Draw d, int x, int y, int w, int h, int color, int shade, Relief relief) {
        int r = Math.min(3, h / 2);
        rounded(d, x, y + 1, w, h, r, shade);
        rounded(d, x, y, w, h, r, color);
        bevel(d, x, y, w, h, r, color, relief);
    }

    /** The game's slot_highlight_back.png, laid behind the item of a hovered slot. */
    public static final int SLOT_HOVER_BACK = 0x60FFFFFF;
    /** The game's slot_highlight_front.png, laid over the item of a hovered slot. */
    public static final int SLOT_HOVER_FRONT = 0x20FFFFFF;

    /**
     * The game's tooltip box around a text block (tooltip/background.png and frame.png, 26.1.2): a
     * 0xF0100010 fill 4 px out, a 1 px frame 3 px out fading from 0x505000FF at the top to
     * 0x5028007F at the bottom, the corner pixel of each left out. For a Draw that has no game to ask.
     */
    public static void gameTooltip(Draw d, int x, int y, int w, int h) {
        if (d.guiPart(GuiPart.TOOLTIP_BACKGROUND, x - 12, y - 12, w + 24, h + 24, 1F)) {
            d.guiPart(GuiPart.TOOLTIP_FRAME, x - 12, y - 12, w + 24, h + 24, 1F);
            return;
        }
        int x0 = x - 4;
        int y0 = y - 4;
        int x1 = x + w + 4;
        int y1 = y + h + 4;
        d.fill(x0 + 1, y0, x1 - 1, y0 + 1, 0xF0100010);
        d.fill(x0, y0 + 1, x1, y1 - 1, 0xF0100010);
        d.fill(x0 + 1, y1 - 1, x1 - 1, y1, 0xF0100010);
        int top = 0x505000FF;
        int bottom = 0x5028007F;
        d.fill(x0 + 2, y0 + 1, x1 - 2, y0 + 2, top);
        d.fill(x0 + 2, y1 - 2, x1 - 2, y1 - 1, bottom);
        int rows = y1 - y0 - 4;
        for (int i = 0; i < rows; i++) {
            int c = mix(top, bottom, rows == 1 ? 0F : i / (float) (rows - 1));
            d.fill(x0 + 1, y0 + 2 + i, x0 + 2, y0 + 3 + i, c);
            d.fill(x1 - 2, y0 + 2 + i, x1 - 1, y0 + 3 + i, c);
        }
    }

    /** The bevel's lit edge for a face colour, keeping the face's alpha. */
    public static int bevelLight(int face) {
        return mix(face, (face & 0xFF000000) | 0xFFFFFF, 0.40F);
    }

    /** The bevel's shaded edge for a face colour, keeping the face's alpha. */
    public static int bevelDark(int face) {
        return mix(face, face & 0xFF000000, 0.38F);
    }

    /**
     * One pixel along the outline of the rounded rectangle {@link #rounded} fills, lit where the
     * outline faces up or left and shaded where it faces down or right. A pixel that faces both
     * ways (the top-right and bottom-left corners) takes the side of the diagonal it sits on.
     */
    public static void bevel(Draw d, int x, int y, int w, int h, int r, int face, Relief relief) {
        if (relief == Relief.FLAT || w < 3 || h < 3) {
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int light = relief == Relief.PRESSED ? bevelDark(face) : bevelLight(face);
        int dark = relief == Relief.PRESSED ? bevelLight(face) : bevelDark(face);
        for (int row = 0; row < h; row++) {
            int inset = rowInset(r, h, row);
            int runStart = -1;
            int runColor = 0;
            for (int col = inset; col <= w - inset; col++) {
                int color = 0;
                if (col < w - inset) {
                    boolean up = !inside(r, w, h, col, row - 1);
                    boolean left = col == inset;
                    boolean down = !inside(r, w, h, col, row + 1);
                    boolean right = col == w - inset - 1;
                    boolean lit = up || left;
                    boolean shaded = down || right;
                    if (lit && shaded) {
                        lit = col * (h - 1) + row * (w - 1) < (w - 1) * (h - 1);
                        shaded = !lit;
                    }
                    color = lit ? light : shaded ? dark : 0;
                }
                if (color != runColor || color == 0) {
                    if (runStart >= 0) {
                        d.fill(x + runStart, y + row, x + col, y + row + 1, runColor);
                    }
                    runStart = color == 0 ? -1 : col;
                    runColor = color;
                }
            }
        }
    }

    private static int rowInset(int r, int h, int row) {
        if (row < r) {
            return cornerInset(r, row);
        }
        if (row >= h - r) {
            return cornerInset(r, h - 1 - row);
        }
        return 0;
    }

    private static boolean inside(int r, int w, int h, int col, int row) {
        if (row < 0 || row >= h) {
            return false;
        }
        int inset = rowInset(r, h, row);
        return col >= inset && col < w - inset;
    }

    /** Blends two colors (t = 0 gives a, 1 gives b). */
    public static int mix(int a, int b, float t) {
        int aa = a >>> 24;
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int ba = b >>> 24;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** The same colour with alpha {@code a} (0 to 255). */
    public static int alpha(int color, int a) {
        return (Maths.clamp(a, 0, 255) << 24) | (color & 0xFFFFFF);
    }

    /** WCAG relative luminance of an RGB colour, 0 (black) to 1 (white). */
    public static double luminance(int color) {
        double sum = 0;
        double[] weight = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) {
            double v = ((color >> (16 - 8 * i)) & 0xFF) / 255.0;
            sum += weight[i] * (v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4));
        }
        return sum;
    }

    /** WCAG contrast ratio of two colours, 1 to 21; alpha ignored. */
    public static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    static final int NEAR_BLACK = 0xFF141414;
    static final int NEAR_WHITE = 0xFFF5F5F5;

    /**
     * Text for a face that is neither a plain button nor a primary one: the first of raisedText,
     * primaryText and text that reaches 4.5:1 on it, else near-black or near-white, whichever
     * contrasts more, pure black or white when the near tone falls short.
     */
    public static int textOn(Palette p, int face) {
        if (contrast(p.raisedText(), face) >= 4.5) {
            return p.raisedText();
        }
        if (contrast(p.primaryText(), face) >= 4.5) {
            return p.primaryText();
        }
        if (contrast(p.text(), face) >= 4.5) {
            return p.text();
        }
        boolean dark = contrast(0xFF000000, face) >= contrast(0xFFFFFFFF, face);
        int near = dark ? NEAR_BLACK : NEAR_WHITE;
        // A mid-grey face leaves no room for a softened tone; only pure black or white still reads.
        return contrast(near, face) >= 4.5 ? near : dark ? 0xFF000000 : 0xFFFFFFFF;
    }

    /**
     * {@code preferred} when it reaches 4.5:1 on {@code face}, else {@link #textOn}. A translucent
     * face (a plate in the glass mode) is judged as it shows: blended over the palette's surface.
     */
    public static int textFor(Palette p, int preferred, int face) {
        int alpha = face >>> 24;
        int shown = alpha == 0xFF ? face : mix(p.surface() | 0xFF000000, face | 0xFF000000, alpha / 255F);
        return contrast(preferred, shown) >= 4.5 ? preferred : textOn(p, shown);
    }

    /**
     * The face of a toggled-on choice: the hover face held, pushed a little further the same way
     * while {@code hover} (0 to 1) is on it. A chroma whose hover face sits too close to the plate
     * (under 1.3:1) gets a face stepped from the plate toward the text until it reaches that against
     * both the plate and the hover face, so a selected tab or segment never reads as an idle or a
     * hovered one.
     */
    /** The least contrast between a face and the plate it stands for, so a chosen or lit face reads as another state. */
    static final double FACE_STEP = 1.3;

    public static int selectedFace(Palette p, float hover) {
        int off = p.raised() | 0xFF000000;
        int lit = p.raisedHover() | 0xFF000000;
        int on = lit;
        boolean close = contrast(lit, off) < FACE_STEP;
        for (float t = 0.05F; close && (contrast(on, off) < FACE_STEP || contrast(on, lit) < FACE_STEP) && t <= 1F; t += 0.05F) {
            on = mix(off, p.text() | 0xFF000000, t);
        }
        on = (on & 0xFFFFFF) | (p.raisedHover() & 0xFF000000);
        int further = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int a = (off >> shift) & 0xFF;
            int b = (on >> shift) & 0xFF;
            further |= Maths.clamp(b + Math.round((b - a) * 0.6F), 0, 255) << shift;
        }
        return mix(on, further, hover);
    }
}
