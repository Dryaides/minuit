package dev.dryaides.minuit;

/**
 * One stop of a {@link MapComponent#route(java.util.List) route}: where it sits in map pixels (0 to 128 from the
 * top left, as {@link MapMarker}), an optional decoration drawn there and an optional name for the tooltip.
 *
 * @param sprite a {@code map_decorations} id drawn upright at the stop ({@code "red_x"}, {@code "red_banner"}...),
 *               null for a plain dot
 * @param name   told by the tooltip when the pointer is over the stop; empty for none
 */
@Incubating
public record MapWaypoint(float x, float y, String sprite, String name) {
    public MapWaypoint {
        sprite = sprite == null || sprite.isEmpty() ? null : sprite.indexOf(':') < 0 ? "minecraft:" + sprite : sprite;
        name = name == null ? "" : name;
    }

    /** A plain stop: a dot on the path, no name. */
    public static MapWaypoint at(float x, float y) {
        return new MapWaypoint(x, y, null, "");
    }

    /** A named stop with a dot. */
    public static MapWaypoint at(float x, float y, String name) {
        return new MapWaypoint(x, y, null, name);
    }

    /**
     * A stop on a marker already on the map (the player, a banner): same place and name, no second sprite, since
     * the map draws the marker itself.
     */
    public static MapWaypoint of(MapMarker marker) {
        return new MapWaypoint(marker.x(), marker.y(), null, marker.name());
    }

    /** A destination drawn with a decoration of its own, e.g. {@code to(96, 40, "red_x", "Treasure")}. */
    public static MapWaypoint to(float x, float y, String sprite, String name) {
        return new MapWaypoint(x, y, sprite, name);
    }
}
