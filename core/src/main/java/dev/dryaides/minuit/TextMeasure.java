package dev.dryaides.minuit;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Text widths without a frame to draw in: a panel sizes itself to wrapped text while it is built,
 * before its first draw. The host sets {@link #font} to the game's font; a bench sets its own.
 * {@link #draw} is a {@link Draw} that draws nothing, so {@link Draw#wrapAll} and the other text
 * helpers run at layout time.
 */
public final class TextMeasure {

    /** Pixel width of a string in the font the screens draw with; 6 per character until a host sets it. */
    public static volatile ToIntFunction<String> font = text -> 6 * text.length();

    /** Bumped whenever the font changes (a resource reload, a screen re-reading the game's font); measured-text caches key on it. */
    private static volatile int fontGeneration;

    private TextMeasure() {
    }

    public static int fontGeneration() {
        return fontGeneration;
    }

    public static void fontChanged() {
        fontGeneration++;
    }

    public static int width(String text) {
        return font.applyAsInt(text);
    }

    /** {@code text} wrapped at {@code maxWidth}, nothing lost, as {@link Draw#wrapAll}. */
    public static List<String> wrapAll(String text, int maxWidth) {
        return draw().wrapAll(text, maxWidth);
    }

    /** A Draw that measures with {@link #font} and draws nothing. */
    public static Draw draw() {
        ToIntFunction<String> f = font;
        return new Draw() {
            @Override
            protected void rect(int x0, int y0, int x1, int y1, int argb) {
            }

            @Override
            protected void string(String text, int x, int y, int argb) {
            }

            @Override
            public int width(String text) {
                return f.applyAsInt(text);
            }

            @Override
            public String cut(String text, int maxWidth) {
                int end = 0;
                while (end < text.length()) {
                    int next = end + Character.charCount(text.codePointAt(end));
                    if (f.applyAsInt(text.substring(0, next)) > maxWidth) {
                        break;
                    }
                    end = next;
                }
                return text.substring(0, end);
            }

            @Override
            public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH,
                             float alpha) {
            }

            @Override
            protected void pushPose(float x, float y, float scale) {
            }

            @Override
            protected void popPose() {
            }

            @Override
            public void scissor(int x0, int y0, int x1, int y1) {
            }

            @Override
            public void endScissor() {
            }

            @Override
            public void tooltip(List<String> lines, int x, int y) {
            }
        };
    }
}
