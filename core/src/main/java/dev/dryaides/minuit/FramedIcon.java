package dev.dryaides.minuit;

/**
 * One icon in a small dark square frame, as Bedrock marks the entries of its settings list. It is an
 * {@link IconArt}, so it goes wherever an icon does: nav lists, tabs, the side rail, settings rows, icon tiles. The
 * frame comes from the theme being drawn ({@link FrameStyle#iconFrame}); the art inside is a game item or block
 * texture read at runtime, a minuit glyph, or any other art. Art taller than the room inside shrinks by whole screen
 * pixels, never resampled; smaller art sits centred.
 *
 * @param art   what sits inside; null with {@code glyph} set
 * @param glyph a glyph looked up in the theme at draw time, so a theme's own art wins; null with {@code art} set
 * @param size  the frame's side in GUI pixels
 */
public record FramedIcon(IconArt art, Icon glyph, int size) implements IconArt {

    /** Frame side around a 16 px item: 2 px of air and a 1 px outline each side. */
    public static final int ITEM_SIZE = 22;
    /** Frame side around a 12 px glyph. */
    public static final int GLYPH_SIZE = 18;

    public FramedIcon {
        if ((art == null) == (glyph == null)) {
            throw new IllegalArgumentException("one of art or glyph");
        }
        size = Math.max(6, size);
    }

    /** A game item by id ({@code minecraft:furnace}), drawn by the game as in a slot. */
    public static FramedIcon item(String itemId) {
        return new FramedIcon(ItemView.of(itemId), null, ITEM_SIZE);
    }

    /** A block texture of the game's atlas, such as {@code minecraft:block/command_block_front}. */
    public static FramedIcon block(String sprite) {
        return new FramedIcon(SpriteIcon.block(sprite), null, ITEM_SIZE);
    }

    /** One of the kit's glyphs, or the theme's replacement for it. */
    public static FramedIcon of(Icon glyph) {
        return new FramedIcon(null, glyph, GLYPH_SIZE);
    }

    /** Any art, framed with 2 px of air around its larger side. */
    public static FramedIcon of(IconArt art) {
        return new FramedIcon(art, null, Math.max(art.width(), art.height()) + 6);
    }

    /** The same art in a frame {@code side} square. */
    public FramedIcon sized(int side) {
        return new FramedIcon(this.art, this.glyph, side);
    }

    @Override
    public int width() {
        return this.size;
    }

    @Override
    public int height() {
        return this.size;
    }

    @Override
    public void draw(Draw d, int x, int y, int color, float alpha) {
        Theme theme = d.theme();
        FrameStyle frame = theme.frame();
        Palette p = theme.palette();
        frame.iconFrame(d, p, x, y, this.size);
        IconArt inner = this.art != null ? this.art : theme.icon(this.glyph);
        int room = this.size - 4;
        ItemArt.fit(d, p, inner, x + 2, y + 2, room, frame.iconFrameInk(p), alpha >= 0.99F);
        if (alpha < 0.99F && !ItemArt.isItem(inner)) {
            int veil = Math.round((1F - alpha) * 255F);
            d.fill(x + 1, y + 1, x + this.size - 1, y + this.size - 1, (veil << 24) | (p.surface() & 0xFFFFFF));
        }
    }
}
