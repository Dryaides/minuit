package dev.dryaides.minuit;

/**
 * One mark on a map, as vanilla's {@code MapDecoration}: a sprite of the {@code minecraft:map_decorations} atlas
 * (its path, {@code "player"}, {@code "red_banner"}, {@code "mansion"}...), where it sits in map pixels (0 to 128,
 * from the top left; vanilla's byte {@code x} is {@code x / 2F + 64}), its turn in sixteenths ({@code rot}, 0 to
 * 15, as {@code MapDecoration.rot}: 8 is the sprite upright, 0 upside down, as the game draws it), and an
 * optional name for the tooltip.
 *
 * @param sprite the decoration's id, e.g. {@code "minecraft:player"}; a bare path means minecraft
 * @param tint   ARGB multiplied into a turned marker's light pixels, 0 for none (white)
 */
@Incubating
public record MapMarker(String sprite, float x, float y, int rot, String name, int tint) {
    public static final int UPRIGHT = 8;

    public MapMarker {
        sprite = sprite == null ? "minecraft:player" : sprite.indexOf(':') < 0 ? "minecraft:" + sprite : sprite;
        rot = rot & 15;
        name = name == null ? "" : name;
    }

    /** An upright mark: a banner, a structure, a target. */
    public static MapMarker at(String sprite, float x, float y, String name) {
        return new MapMarker(sprite, x, y, UPRIGHT, name, 0);
    }

    /** A player arrow facing a yaw in degrees (0 south, 90 west, as {@code Entity.getYRot}). */
    public static MapMarker player(float x, float y, float yaw, String name) {
        return new MapMarker("minecraft:player", x, y, rotOf(yaw), name, 0);
    }

    /** Vanilla's {@code MapItemSavedData.calculateRotation} without the nether spin: a yaw to sixteenths. */
    public static int rotOf(float yaw) {
        double r = yaw < 0 ? yaw - 8D : yaw + 8D;
        return ((int) (r * 16D / 360D)) & 15;
    }

    /** The sprite's path, after the namespace: also the file name under textures/map/decorations. */
    public String path() {
        int c = sprite.indexOf(':');
        return c < 0 ? sprite : sprite.substring(c + 1);
    }

    /** Whether vanilla turns this sprite with the heading (the player arrows and the plain markers). */
    public boolean turns() {
        String p = path();
        return p.equals("player") || p.equals("frame") || p.endsWith("_marker") || p.equals("player_off_map");
    }
}
