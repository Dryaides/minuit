package dev.dryaides.minuit;

/**
 * One cell of a texture atlas, for a mod that draws its icons rather than using the kit's glyphs.
 * Not HD-safe: {@code u, v} count pixels of a fixed-size sheet, so a resource pack that doubles the
 * sheet breaks the cells. For the game's item, block and GUI textures use {@link SpriteIcon}.
 *
 * @param texture {@code namespace:textures/gui/icons.png}
 */
public record AtlasIcon(String texture, int u, int v, int width, int height, int texW, int texH) implements IconArt {

    /** Cell {@code index} of a 256 by 256 atlas of 16 pixel cells, filled row by row. */
    public static AtlasIcon cell16(String texture, int index) {
        return new AtlasIcon(texture, (index % 16) * 16, (index / 16) * 16, 16, 16, 256, 256);
    }

    /** A whole PNG of {@code size} by {@code size} pixels as one icon, such as {@code mymod:textures/gui/tab_home.png}. */
    public static AtlasIcon whole(String texture, int size) {
        return new AtlasIcon(texture, 0, 0, size, size, size, size);
    }

    @Override
    public void draw(Draw d, int x, int y, int color, float alpha) {
        d.blit(this.texture, x, y, this.u, this.v, this.width, this.height, this.texW, this.texH, alpha);
    }
}
