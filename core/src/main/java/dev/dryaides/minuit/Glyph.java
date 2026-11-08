package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.Maths;

/**
 * An icon drawn pixel by pixel in the text colour, so it needs no texture atlas. 'X' is a pixel, 'h' a
 * half-tone pixel: the same colour at half its alpha.
 */
public record Glyph(String... rows) implements IconArt {

    @Override
    public int width() {
        return this.rows[0].length();
    }

    @Override
    public int height() {
        return this.rows.length;
    }

    @Override
    public void draw(Draw d, int x, int y, int color, float alpha) {
        int a = Math.round(((color >>> 24) == 0 ? 255 : color >>> 24) * Maths.clamp(alpha, 0F, 1F));
        int tinted = (a << 24) | (color & 0xFFFFFF);
        fillRuns(d, x, y, 'X', tinted);
        fillRuns(d, x, y, 'h', ((a / 2) << 24) | (color & 0xFFFFFF));
    }

    private void fillRuns(Draw d, int x, int y, char mark, int tinted) {
        for (int row = 0; row < this.rows.length; row++) {
            String line = this.rows[row];
            int start = -1;
            for (int col = 0; col <= line.length(); col++) {
                boolean on = col < line.length() && line.charAt(col) == mark;
                if (on && start < 0) {
                    start = col;
                } else if (!on && start >= 0) {
                    d.fill(x + start, y + row, x + col, y + row + 1, tinted);
                    start = -1;
                }
            }
        }
    }
}
