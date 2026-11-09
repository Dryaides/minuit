package dev.dryaides.minuit;

import java.util.List;

/**
 * Where a {@link MapComponent} gets its map: give it a map id, it answers what to draw right now. Core code has
 * no game class, so the game side lives in the mod: the in-game bench's {@code bench.ingame.VanillaMaps} is the
 * recipe for vanilla map items (it asks {@code MapTextureManager} for the game's own texture, so a map costs one
 * draw call). {@link #ofColors(byte[], List)} wraps raw colours for custom maps and the bench.
 *
 * <p>Called every frame: return the same {@link MapData} while nothing changed, so the component keeps its cache.
 *
 * <h2>Later: minimap mods</h2>
 * Xaero's World Map and JourneyMap keep their own textures per region or tile, not per map id. The plan is a
 * second source kind, {@link Tiles}, that the component would ask for the tiles under its view (world blocks in,
 * texture ids out) and draw with one {@code Draw.blitRegion} per tile; a mod would implement it against the other
 * mod's API (JourneyMap's client API image of a region; Xaero's region textures through its public classes) in
 * its own optional compat class, so no dependency reaches core code. Not wired yet.
 */
@Incubating
public interface MapSource {
    /** What to show for a map id; {@link MapData#LOADING} while the data is on its way, null for no such map. */
    MapData picture(int mapId);

    /**
     * A map to draw. Either {@code texture} (a registered 128x128 texture: in game {@code "minecraft:map/<id>"},
     * as {@code MapTextureManager} names them) or {@code colors} (128*128 packed ids, row by row, see
     * {@link MapColors}); both may be there, the texture wins where the Draw has textures.
     *
     * @param scale   the map's zoom level 0 to 4 ({@code MapItemSavedData.scale}): one pixel is 2^scale blocks
     * @param centerX world X at the map's centre, for the coordinate readout
     * @param version bump when the colours change, so the component rebuilds its cache
     */
    record MapData(String texture, byte[] colors, List<MapMarker> markers, boolean locked, int scale,
                   int centerX, int centerZ, int version, boolean loading) {
        public static final MapData LOADING = new MapData(null, null, List.of(), false, 0, 0, 0, 0, true);

        public MapData {
            markers = markers == null ? List.of() : List.copyOf(markers);
        }

        public static MapData ofTexture(String texture, List<MapMarker> markers) {
            return new MapData(texture, null, markers, false, 0, 0, 0, 0, false);
        }

        public static MapData ofColors(byte[] colors, List<MapMarker> markers) {
            return new MapData(null, colors, markers, false, 0, 0, 0, 0, false);
        }

        public MapData locked(boolean locked) {
            return new MapData(texture, colors, markers, locked, scale, centerX, centerZ, version, loading);
        }

        public MapData world(int scale, int centerX, int centerZ) {
            return new MapData(texture, colors, markers, locked, scale, centerX, centerZ, version, loading);
        }

        public MapData version(int version) {
            return new MapData(texture, colors, markers, locked, scale, centerX, centerZ, version, loading);
        }

        /** The block a map pixel stands for, as vanilla places them: centre minus 64 pixels, 2^scale blocks each. */
        public int blockX(float px) {
            return centerX + (int) Math.floor((px - 64F) * (1 << scale));
        }

        public int blockZ(float py) {
            return centerZ + (int) Math.floor((py - 64F) * (1 << scale));
        }
    }

    /** A fixed picture: for custom maps, demos, a map already read. */
    static MapSource of(MapData picture) {
        return id -> picture;
    }

    /** Raw colours and markers as a source. */
    static MapSource ofColors(byte[] colors, List<MapMarker> markers) {
        return of(MapData.ofColors(colors, markers));
    }

    /**
     * Reserved for tile maps (Xaero's World Map, JourneyMap, a mod's own renderer): square tiles of world blocks,
     * each a registered texture. Declared so the shape is settled; {@link MapComponent} does not draw it yet.
     */
    interface Tiles {
        /** Blocks per tile side (512 for a Xaero region, 512 for a JourneyMap region image). */
        int tileBlocks();

        /** Pixels per tile side in its texture. */
        int tilePixels();

        /** The texture of the tile holding these tile coordinates, null while missing; must not block. */
        String tile(String dimension, int tileX, int tileZ);

        /** Marks in this block area (waypoints, players), in block coordinates as x and y. */
        default List<MapMarker> markers(String dimension, int minX, int minZ, int maxX, int maxZ) {
            return List.of();
        }
    }
}
