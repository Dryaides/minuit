package bench.ingame;

import dev.dryaides.minuit.MapMarker;
import dev.dryaides.minuit.MapSource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Recipe: vanilla map items as a {@link MapSource}, client side, against 26.3. The picture is the game's own
 * texture: {@code MapTextureManager.prepareMapTexture(MapId, MapItemSavedData)} uploads the colours when they
 * changed and returns the registered id ({@code "minecraft:map/<id>"}), so the component draws it with one
 * {@code Draw.image}. Markers come from {@code MapItemSavedData.getDecorations()}: vanilla byte x/y (-128..127)
 * to map pixels (x / 2 + 64), {@code rot} kept, sprite = {@code MapDecorationType.assetId()} in the
 * {@code minecraft:map_decorations} atlas (what {@code MapDecoration.getSpriteLocation()} gives).
 * Untested in game (written without a compile against the real jar in this session).
 */
public final class VanillaMaps implements MapSource {
    public static final VanillaMaps INSTANCE = new VanillaMaps();

    private MapItemSavedData lastData;
    private MapData last;
    private int lastId = Integer.MIN_VALUE;
    private int lastHash;

    @Override
    public MapData picture(int mapId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        MapId id = new MapId(mapId);
        MapItemSavedData data = mc.level.getMapData(id);
        if (data == null) {
            return MapData.LOADING; // the server sends it once a player holds the map
        }
        Identifier tex = mc.getMapTextureManager().prepareMapTexture(id, data);
        int hash = 1;
        for (MapDecoration d : data.getDecorations()) {
            hash = hash * 31 + d.hashCode();
        }
        if (data == lastData && mapId == lastId && hash == lastHash && last != null) {
            return last;
        }
        List<MapMarker> markers = new ArrayList<>();
        for (MapDecoration d : data.getDecorations()) {
            String name = d.name().map(c -> c.getString()).orElse("");
            markers.add(new MapMarker(d.getSpriteLocation().toString(), d.x() / 2F + 64F, d.y() / 2F + 64F, d.rot(), name, 0));
        }
        last = new MapData(tex.toString(), data.colors, markers, data.locked, data.scale, data.centerX, data.centerZ,
                0, false);
        lastData = data;
        lastId = mapId;
        lastHash = hash;
        return last;
    }
}
