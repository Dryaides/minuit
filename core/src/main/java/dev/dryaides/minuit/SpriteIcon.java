package dev.dryaides.minuit;

/**
 * A sprite of one of the game's texture atlases. The game maps the sprite onto the box whatever its
 * pixel size, so a resource pack at 32 or 128 pixels still fills the same 16 GUI pixels, and an
 * animated texture animates. An unknown sprite shows the game's missing texture.
 *
 * @param atlas  {@code minecraft:items}, {@code minecraft:blocks} or {@code minecraft:gui}
 * @param sprite {@code namespace:path} inside the atlas, such as {@code minecraft:item/diamond}
 */
public record SpriteIcon(String atlas, String sprite, int width, int height) implements IconArt {

    public static final String ITEMS = "minecraft:items";
    public static final String BLOCKS = "minecraft:blocks";
    public static final String GUI = "minecraft:gui";

    public static SpriteIcon item(String sprite) {
        return new SpriteIcon(ITEMS, sprite, 16, 16);
    }

    public static SpriteIcon block(String sprite) {
        return new SpriteIcon(BLOCKS, sprite, 16, 16);
    }

    /** A GUI sprite, such as {@code minecraft:icon/checkmark}, at its own size. */
    public static SpriteIcon gui(String sprite, int width, int height) {
        return new SpriteIcon(GUI, sprite, width, height);
    }

    @Override
    public void draw(Draw d, int x, int y, int color, float alpha) {
        d.sprite(this.atlas, this.sprite, x, y, this.width, this.height, alpha);
    }
}
