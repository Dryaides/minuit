package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Oracle: the formatting code table of minecraft.wiki, and the owner's escape rules. */
class RichTextTest {

    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;

    /** Every glyph 6 px wide, the advance of most of the game's capitals. */
    private static final Draw FIXED = new Draw() {
        @Override
        protected void rect(int x0, int y0, int x1, int y1, int argb) {
        }

        @Override
        protected void string(String text, int x, int y, int argb) {
        }

        @Override
        public int width(String text) {
            return 6 * text.codePointCount(0, text.length());
        }

        @Override
        protected String cut(String text, int maxWidth) {
            return text.substring(0, Math.min(text.length(), maxWidth / 6));
        }

        @Override
        public void blit(String texture, int x, int y, int u, int v, int w, int h, int texW, int texH, float alpha) {
        }

        @Override
        public void push(float x, float y, float scale) {
        }

        @Override
        public void pop() {
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

    private static RichText.Run run(String text, int color, int style) {
        return new RichText.Run(text, color, style, null);
    }

    @Test
    void aStyleCarriesOverAColourCodeUntilReset() {
        assertEquals(List.of(run("A", RED, 0), run("B", RED, RichText.BOLD), run("C", 0, 0)),
                RichText.parse("\u00A7cA\u00A7lB\u00A7rC").runs());
    }

    @Test
    void aColourCodeClearsTheStyle() {
        assertEquals(List.of(run("B", 0, RichText.BOLD), run("C", RED, 0)),
                RichText.parse("\u00A7lB\u00A7cC").runs());
    }

    @Test
    void anUnknownCodeStaysAsWritten() {
        assertEquals("\u00A7zA", RichText.parse("\u00A7zA").plain());
    }

    @Test
    void ampersandIsACodeSignAndBackslashEscapes() {
        assertEquals(List.of(run("A", RED, 0)), RichText.parse("&cA").runs());
        assertEquals("&cA", RichText.parse("\\&cA").plain());
        assertEquals("[x](https://a.b)", RichText.parse("\\[x](https://a.b)").plain());
        assertEquals("a\\b", RichText.parse("a\\\\b").plain());
    }

    @Test
    void aWebLinkKeepsItsLabelAndUrl() {
        List<RichText.Run> runs = RichText.parse("see [docs](https://minuit.dev) now").runs();
        assertEquals(new RichText.Run("docs", 0, 0, "https://minuit.dev"), runs.get(1));
        assertEquals("[x](file:///etc)", RichText.parse("[x](file:///etc)").plain());
    }

    @Test
    void boldIsOnePixelWiderPerGlyph() {
        assertEquals(RichText.parse("AB").width(FIXED) + 2, RichText.parse("\u00A7lAB").width(FIXED));
    }

    @Test
    void aLineBrokenInsideARunKeepsItsColour() {
        List<RichText> lines = RichText.parse("\u00A7cred \u00A7aaa bbb \u00A79blue").wrap(FIXED, 36);
        assertEquals("red aa", lines.get(0).plain());
        assertEquals(run("bbb", GREEN, 0), lines.get(1).runs().get(0));
    }
}
