package dev.dryaides.minuit;

/**
 * An item as an icon, 16 by 16: by id from core, or a game stack from {@code client.MinuitItems.view},
 * which keeps its glint, damage and components.
 *
 * @param id    {@code namespace:path}
 * @param stack null, or the game's {@code ItemStack}, opaque to core
 */
public record ItemView(String id, int count, Object stack) implements IconArt {

    public static ItemView of(String id) {
        return new ItemView(id, 1, null);
    }

    public static ItemView of(String id, int count) {
        return new ItemView(id, count, null);
    }

    @Override
    public int width() {
        return 16;
    }

    @Override
    public int height() {
        return 16;
    }

    /** The game cannot fade an item, so {@code alpha} is ignored; a disabled button veils it instead. */
    @Override
    public void draw(Draw d, int x, int y, int color, float alpha) {
        d.item(this, x, y);
    }
}
