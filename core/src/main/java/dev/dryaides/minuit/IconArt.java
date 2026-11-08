package dev.dryaides.minuit;

/** Something the kit can draw as an icon: a pixel glyph, or a cell of a texture atlas. */
public interface IconArt {

    int width();

    int height();

    /**
     * @param color tint for a glyph, ignored by a texture
     * @param alpha 0 to 1
     */
    void draw(Draw d, int x, int y, int color, float alpha);
}
