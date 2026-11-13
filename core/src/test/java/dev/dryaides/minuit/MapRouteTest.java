package dev.dryaides.minuit;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MapRouteTest {
    /** Legs 3-4-5, 0-6 and 6-8-10 triangles: 5 + 6 + 10 map pixels. */
    private static final List<MapWaypoint> STOPS = List.of(
            MapWaypoint.at(0, 0), MapWaypoint.at(3, 4), MapWaypoint.at(3, 10), MapWaypoint.at(9, 18));

    @Test
    void lengthIsTheSumOfTheLegsInBlocks() {
        MapSource.MapData data = MapSource.MapData.ofColors(new byte[128 * 128], List.of()).world(1, 0, 0);
        MapComponent map = new MapComponent(new Box("m", 0, 0, 128, 128)).source(MapSource.of(data)).route(STOPS);
        // scale 1: one map pixel is 2 blocks
        assertEquals(42.0, map.routeBlocks(), 1e-6);
        assertEquals(32.0, map.routeReached(1).routeBlocks(), 1e-6);
    }

    @Test
    void legsAreCutAtTheViewEdge() {
        // a row through an 8 px box from x -10 to 10: inside from x 0 (t 0.5) to x 8 (t 0.9)
        assertArrayEquals(new float[] {0.5F, 0.9F}, MapComponent.clip(-10, 5, 10, 5, 0, 0, 8, 8), 1e-6F);
        // the diagonal from (-4, -4) to (12, 12) enters at the corner (0, 0) and leaves at (8, 8)
        assertArrayEquals(new float[] {0.25F, 0.75F}, MapComponent.clip(-4, -4, 12, 12, 0, 0, 8, 8), 1e-6F);
        assertNull(MapComponent.clip(10, 10, 20, 12, 0, 0, 8, 8));
    }
}
