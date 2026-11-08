package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.GuiPart;

import java.util.ArrayList;
import java.util.List;

/**
 * Every drawing call of the kit. {@code client.GuiDraw} is the one implementation that names
 * Minecraft's renderer, so a Minecraft version change touches that class only, and a bench draws
 * the same widgets into an image through its own implementation.
 *
 * <p>Text is a plain string, already translated: that is what lets this class and every widget
 * built on it load without the game.
 */
public abstract class Draw {

    /** The width a {@link #tooltip} wraps its lines at, as the game's item tooltips. */
    protected static final int TOOLTIP_WIDTH = 190;

    public static final String ELLIPSIS = "...";

    protected abstract void rect(int x0, int y0, int x1, int y1, int argb);

    protected abstract void string(String text, int x, int y, int argb);

    public abstract int width(String text);

    /**
     * The icon of item {@code itemId} ({@code namespace:path}), 16 by 16 at {@code x, y}. Drawn by the
     * game's host; a bench without items draws nothing.
     */
    public void item(String itemId, int x, int y) {
    }

    /**
     * An item icon, 16 by 16 at {@code x, y}: the game draws the stack or the id; the base draws a
     * stand-in token, so a renderer without the item's texture never leaves the slot blank.
     */
    public void item(ItemView item, int x, int y) {
        ItemStandIn.draw(this, item.id(), x, y);
    }

    /** What the game draws over an item: here the count when above one, white with the game's shadow. */
    public void itemDecorations(ItemView item, int x, int y) {
        itemDecorations(item, x, y, item.count() <= 1 ? null : Integer.toString(item.count()));
    }

    /**
     * The decorations with {@code count} drawn in place of the stack's own count, or no count for null:
     * a storage slot's {@code 1.2k}. The game keeps the damage bar and cooldown.
     */
    public void itemDecorations(ItemView item, int x, int y, String count) {
        if (count == null || count.isEmpty()) {
            return;
        }
        int left = x + 17 - width(count);
        string(count, left + 1, y + 10, 0xFF3F3F3F);
        string(count, left, y + 9, 0xFFFFFFFF);
    }

    /**
     * The game's own tooltip for the item at the mouse, queued for the end of the frame.
     *
     * @return false when this renderer has none; the caller then shows the item's name
     */
    public boolean itemTooltip(ItemView item, int mx, int my) {
        return false;
    }

    /**
     * A sprite of the game's atlas {@code atlas} ({@link SpriteIcon}) stretched to {@code w} by {@code h}.
     * The base reads the sprite's own file, {@code namespace:textures/path.png}.
     *
     * @param alpha 0 to 1
     */
    public void sprite(String atlas, String sprite, int x, int y, int w, int h, float alpha) {
        int colon = sprite.indexOf(':');
        String ns = colon < 0 ? "minecraft" : sprite.substring(0, colon);
        String path = sprite.substring(colon + 1);
        String folder = SpriteIcon.GUI.equals(atlas) ? "textures/gui/sprites/" : "textures/";
        image(ns + ":" + folder + path + ".png", x, y, w, h, w, h, alpha);
    }

    /**
     * An entity turned by {@code yaw} and {@code pitch} (degrees) in the box from {@code x0, y0} to
     * {@code x1, y1}.
     *
     * @return false when this renderer draws no entity; the caller then draws its stand-in
     */
    public boolean entity(EntityView view, int x0, int y0, int x1, int y1, float yaw, float pitch) {
        return false;
    }

    /**
     * One run of formatted text in {@code style} ({@link RichText#BOLD} and the rest). The base draws what
     * the game's font does: bold twice, 1 px apart; underline and strikethrough as lines; obfuscated as
     * random glyphs of the same width; italic upright, since the shear needs the font.
     */
    protected void styledString(String text, int x, int y, int argb, int style) {
        String shown = (style & RichText.OBFUSCATED) != 0 ? obfuscate(text) : text;
        boolean bold = (style & RichText.BOLD) != 0;
        if (!bold) {
            string(shown, x, y, argb);
        } else {
            int pen = x;
            for (int i = 0; i < shown.length(); ) {
                int cp = shown.codePointAt(i);
                String glyph = new String(Character.toChars(cp));
                string(glyph, pen, y, argb);
                string(glyph, pen + 1, y, argb);
                pen += width(glyph) + 1;
                i += Character.charCount(cp);
            }
        }
        int w = styledWidth(text, style);
        if ((style & RichText.UNDERLINE) != 0) {
            rect(x, y + 9, x + w, y + 10, argb);
        }
        if ((style & RichText.STRIKE) != 0) {
            rect(x, y + 4, x + w, y + 5, argb);
        }
    }

    /** The width of {@code text} drawn in {@code style}: one more pixel per glyph when bold. */
    public int styledWidth(String text, int style) {
        int w = width(text);
        return (style & RichText.BOLD) != 0 ? w + text.codePointCount(0, text.length()) : w;
    }

    private String obfuscate(String text) {
        StringBuilder out = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            String glyph = new String(Character.toChars(cp));
            int target = width(glyph);
            String pick = glyph;
            if (cp != ' ') {
                for (int tries = 0; tries < 16; tries++) {
                    String candidate = String.valueOf((char) ('!' + random.nextInt(94)));
                    if (width(candidate) == target) {
                        pick = candidate;
                        break;
                    }
                }
            }
            out.append(pick);
        }
        return out.toString();
    }

    /**
     * Formatted text on one line from {@code x, y}; a run with colour 0 takes {@code defaultColor}, and a
     * link takes the accent the caller passes as {@code linkColor}.
     */
    public final void rich(RichText text, int x, int y, int defaultColor) {
        rich(text, x, y, defaultColor, defaultColor);
    }

    /** Same, links in {@code linkColor} and underlined. */
    public final void rich(RichText text, int x, int y, int defaultColor, int linkColor) {
        int pen = x;
        for (RichText.Run run : text.runs()) {
            int color = run.link() != null ? linkColor : run.color() == 0 ? defaultColor : run.color();
            int style = run.link() != null ? run.style() | RichText.UNDERLINE : run.style();
            styledString(run.text(), pen, y, textColor(color), style);
            pen += styledWidth(run.text(), run.style());
        }
    }

    /** The longest prefix of {@code text} that is at most {@code maxWidth} pixels wide. */
    protected abstract String cut(String text, int maxWidth);

    /** The longest prefix of {@code text} that is at most {@code maxWidth} pixels wide, no ellipsis. */
    public final String prefix(String text, int maxWidth) {
        return cut(text, maxWidth);
    }

    /**
     * Part of a texture, {@code texture} written {@code namespace:path/to/file.png}.
     *
     * @param alpha 0 to 1
     */
    public abstract void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH,
                              float alpha);

    /**
     * A whole texture of {@code texW} by {@code texH} stretched to {@code w} by {@code h}: a thumbnail.
     * The default draws it unscaled, cropped to the box; a renderer that can scale overrides this.
     */
    public void image(String texture, int x, int y, int w, int h, int texW, int texH, float alpha) {
        blit(texture, x, y, 0, 0, Math.min(w, texW), Math.min(h, texH), texW, texH, alpha);
    }

    /**
     * The {@code regionW} by {@code regionH} part of a texture at ({@code u}, {@code v}) drawn into a
     * {@code w} by {@code h} box: a face of a skin, a cropped thumbnail. The default scales the 1:1
     * blit by the width ratio; a renderer that can stretch overrides this.
     */
    public void blitRegion(String texture, int x, int y, int w, int h, int u, int v, int regionW, int regionH,
                           int texW, int texH, float alpha) {
        push(x, y, w / (float) Math.max(1, regionW));
        blit(texture, 0, 0, u, v, regionW, regionH, texW, texH, alpha);
        pop();
    }

    /** Screen pixels per GUI pixel, for {@link #crispScale}; 0 when this renderer does not know it. */
    public int guiScale() {
        return 0;
    }

    /**
     * The game's own art for {@code part} stretched to {@code w} by {@code h}, so a resource pack restyles it.
     *
     * @param alpha 0 to 1
     * @return false when this renderer has no game art: the caller draws its pixel copy
     */
    public boolean guiPart(GuiPart part, int x, int y, int w, int h, float alpha) {
        return false;
    }

    /**
     * Starts a new layer over everything drawn so far, so what comes next covers the game's items
     * drawn in between. No-op where draws already land in call order.
     */
    public void nextLayer() {
    }

    public abstract void push(float x, float y, float scale);

    public abstract void pop();

    public abstract void scissor(int x0, int y0, int x1, int y1);

    public abstract void endScissor();

    /** Queued and drawn by the game at the end of the frame, above everything else. */
    public abstract void tooltip(List<String> lines, int x, int y);

    public final void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x1 > x0 && y1 > y0) {
            rect(x0, y0, x1, y1, argb);
        }
    }

    /**
     * A rectangle shaded between four corner colours, each channel blended linearly across and
     * down. The kit's only gradient: one fill per pixel row of each column, so keep it small (a
     * colour square, a bar), never a whole screen.
     */
    public final void gradient(int x0, int y0, int x1, int y1, int topLeft, int topRight, int bottomLeft,
                               int bottomRight) {
        int w = x1 - x0;
        int h = y1 - y0;
        for (int col = 0; col < w; col++) {
            float u = w == 1 ? 0F : col / (float) (w - 1);
            int top = Shapes.mix(topLeft, topRight, u);
            int bottom = Shapes.mix(bottomLeft, bottomRight, u);
            for (int row = 0; row < h; row++) {
                float v = h == 1 ? 0F : row / (float) (h - 1);
                rect(x0 + col, y0 + row, x0 + col + 1, y0 + row + 1, Shapes.mix(top, bottom, v));
            }
        }
    }

    /** Colors written without alpha (0xRRGGBB) are opaque. */
    private static int textColor(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    public final void text(String text, int x, int y, int color) {
        string(text, x, y, textColor(color));
    }

    public final void centered(String text, int cx, int y, int color) {
        text(text, cx - width(text) / 2, y, color);
    }

    /**
     * The top of an icon set beside a line of text drawn at {@code textTop}: the icon's middle row on the
     * middle row of the font's 7-px capitals, so a 9 x 9 glyph overhangs the capitals by one pixel each way.
     */
    public static int iconTop(int textTop, int iconHeight) {
        return textTop + 3 - (iconHeight - 1) / 2;
    }

    public final void icon(IconArt icon, int x, int y, int color) {
        icon.draw(this, x, y, color, 1F);
    }

    /** Text cut with "..." so it never goes past {@code maxWidth} pixels. */
    public final void textFit(String text, int x, int y, int maxWidth, int color) {
        text(ellipsize(text, maxWidth), x, y, color);
    }

    /** Same, ending at {@code right} instead of starting at a left edge. */
    public final void textFitRight(String text, int right, int y, int maxWidth, int color) {
        String cut = ellipsize(text, maxWidth);
        text(cut, right - width(cut), y, color);
    }

    /** The text in {@code box}: one line, vertically centred, cut with "..." when too long. */
    public final void textIn(String text, Box box, int color) {
        textFit(text, box.x(), box.y() + (box.h() - 8) / 2 + (box.h() > 8 ? 1 : 0), box.w(), color);
    }

    /** Same, right-aligned in {@code box}. */
    public final void textInRight(String text, Box box, int color) {
        textFitRight(text, box.right(), box.y() + (box.h() - 8) / 2 + (box.h() > 8 ? 1 : 0), box.w(), color);
    }

    /** The text cut to fit {@code maxWidth} pixels, with "..." when it was too long. */
    public final String ellipsize(String text, int maxWidth) {
        if (width(text) <= maxWidth) {
            return text;
        }
        if (width(ELLIPSIS) > maxWidth) {
            return "";
        }
        return cut(text, Math.max(0, maxWidth - width(ELLIPSIS))).stripTrailing() + ELLIPSIS;
    }

    /**
     * Text that shrinks before it is cut, so a long translation stays whole when it can.
     *
     * @param floor smallest scale allowed; below it the text is cut instead
     */
    public final void textShrink(String text, int x, int y, int maxWidth, int height, float floor,
                                 int color) {
        float scale = Math.min(1F, maxWidth / (float) Math.max(1, width(text)));
        String shown = text;
        if (scale < floor) {
            scale = floor;
            shown = ellipsize(text, (int) (maxWidth / scale));
        }
        if (scale >= 1F) {
            text(shown, x, y + (height - 8) / 2, color);
            return;
        }
        push(x, y + (height - 8 * scale) / 2F, scale);
        text(shown, 0, 0, color);
        pop();
    }

    /** Small text (three quarters) in {@code box}, cut when even that is too wide. */
    public final void small(String text, Box box, int color) {
        small(text, box, color, 0.75F, 0);
    }

    /**
     * {@code scale} rounded so that one font pixel covers a whole number of screen pixels: 0.75
     * gives 2/3 at GUI scale 3, 3/4 at 4, 4/5 at 5. At 1 and 2 no size between a half (unreadable)
     * and the full font is whole, so it stays as asked.
     */
    public static float crispScale(int guiScale, float scale) {
        if (guiScale <= 2) {
            return scale;
        }
        return Math.round(guiScale * scale) / (float) guiScale;
    }

    /** A position on a whole screen pixel, so scaled text does not straddle two; 0 leaves it as is. */
    public static float snap(float v, int guiScale) {
        return guiScale <= 0 ? v : Math.round(v * guiScale) / (float) guiScale;
    }

    /** Text at {@code scale} in {@code box}, vertically centred on a whole screen pixel, cut when too wide. */
    public final void small(String text, Box box, int color, float scale, int guiScale) {
        String shown = ellipsize(text, (int) (box.w() / scale));
        push(box.x(), snap(box.y() + (box.h() - 8 * scale) / 2F, guiScale), scale);
        text(shown, 0, 0, color);
        pop();
    }

    /** Text at {@code scale} ending at {@code right}, vertically centred in a row of {@code h}. */
    public final void smallRight(String text, int right, int y, int maxWidth, int h, int color, float scale,
                                 int guiScale) {
        String shown = ellipsize(text, (int) (maxWidth / scale));
        float width = width(shown) * scale;
        push(snap(right - width, guiScale), snap(y + (h - 8 * scale) / 2F, guiScale), scale);
        text(shown, 0, 0, color);
        pop();
    }

    /** Text at {@code scale} wrapped in {@code box}, at most {@code lines} lines of 8 pixels, the last one cut. */
    public final void smallWrapped(String text, Box box, int lines, int color, float scale) {
        push(box.x(), box.y(), scale);
        wrapped(text, 0, 0, (int) (box.w() / scale), lines, (int) (8 / scale), color);
        pop();
    }

    /** Word wrap at {@code maxWidth}; a word longer than the line is cut, never left to overflow. */
    public final List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(width(word) <= maxWidth ? word : ellipsize(word, maxWidth));
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Word wrap at {@code maxWidth} that loses nothing: a word longer than the line is broken
     * across lines instead of cut.
     */
    public final List<String> wrapAll(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (maxWidth <= 0) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(candidate) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            String rest = word;
            while (width(rest) > maxWidth) {
                String head = cut(rest, maxWidth);
                if (head.isEmpty()) {
                    head = rest.substring(0, Character.charCount(rest.codePointAt(0)));
                }
                lines.add(head);
                rest = rest.substring(head.length());
            }
            line.append(rest);
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Wrapped over at most {@code maxLines} lines of {@code lineHeight}, the last one ending in
     * "..." when the text did not fit.
     *
     * @return the number of lines drawn
     */
    public final int wrapped(String text, int x, int y, int maxWidth, int maxLines, int lineHeight, int color) {
        List<String> lines = wrap(text, maxWidth);
        int drawn = Math.min(lines.size(), maxLines);
        for (int i = 0; i < drawn; i++) {
            String line = lines.get(i);
            if (i == drawn - 1 && lines.size() > maxLines) {
                line = ellipsize(line + ELLIPSIS, maxWidth);
            }
            text(line, x, y + i * lineHeight, color);
        }
        return drawn;
    }
}
