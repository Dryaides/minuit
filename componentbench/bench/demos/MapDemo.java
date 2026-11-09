package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.MapComponent;
import dev.dryaides.minuit.MapMarker;
import dev.dryaides.minuit.MapSource;
import java.util.List;

/** A generated map (fake terrain in the vanilla palette, a few markers): sizes, paper, zoom, states. */
public final class MapDemo implements Demo {
    private static final int W = 160;
    private static final int H = 160;

    // The game's MapColor ids and brightnesses: a map stores base * 4 + brightness per pixel.
    private static final int NONE = 0;
    private static final int GRASS = 1;
    private static final int SAND = 2;
    private static final int PLANT = 7;
    private static final int SNOW = 8;
    private static final int DIRT = 10;
    private static final int STONE = 11;
    private static final int WATER = 12;
    private static final int WOOD = 13;
    private static final int LOW = 0;
    private static final int NORMAL = 1;
    private static final int HIGH = 2;

    private static byte packed(int base, int brightness) {
        return (byte) (base << 2 | brightness);
    }

    /** Value noise terrain: water, sand, grass, forest, stone, snow, with vanilla's shading by height. */
    static byte[] terrain(boolean unexplored) {
        byte[] out = new byte[128 * 128];
        double[] hgt = new double[128 * 128];
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                double h = noise(x / 32D, y / 32D) * 0.6 + noise(x / 12D, y / 12D) * 0.3 + noise(x / 5D, y / 5D) * 0.1;
                hgt[y * 128 + x] = h;
            }
        }
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                double h = hgt[y * 128 + x];
                double above = y > 0 ? hgt[(y - 1) * 128 + x] : h;
                int base;
                if (h < 0.42) base = WATER;
                else if (h < 0.46) base = SAND;
                else if (h < 0.62) base = GRASS;
                else if (h < 0.72) base = PLANT;
                else if (h < 0.82) base = STONE;
                else base = SNOW;
                int shade;
                if (base == WATER) {
                    shade = h < 0.30 ? LOW : h < 0.38 ? NORMAL : HIGH;
                } else {
                    double d = (h - above) * 60;
                    shade = d > 0.6 ? HIGH : d < -0.6 ? LOW : NORMAL;
                }
                if (unexplored && (x - 40) * (x - 40) + (y - 50) * (y - 50) > 46 * 46) {
                    base = NONE;
                }
                out[y * 128 + x] = packed(base, shade);
            }
        }
        // a road and a village
        for (int x = 20; x < 110; x++) out[70 * 128 + x] = packed(DIRT, NORMAL);
        for (int y = 60; y < 68; y++) for (int x = 70; x < 80; x++) out[y * 128 + x] = packed(WOOD, HIGH);
        return out;
    }

    private static double rnd(int x, int y) {
        long s = x * 374761393L + y * 668265263L;
        s = (s ^ (s >>> 13)) * 1274126177L;
        return ((s ^ (s >>> 16)) & 0xFFFF) / 65535D;
    }

    private static double noise(double x, double y) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double fx = x - ix;
        double fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = rnd(ix, iy) + (rnd(ix + 1, iy) - rnd(ix, iy)) * fx;
        double b = rnd(ix, iy + 1) + (rnd(ix + 1, iy + 1) - rnd(ix, iy + 1)) * fx;
        return a + (b - a) * fy;
    }

    static List<MapMarker> markers() {
        return List.of(
                MapMarker.player(64, 64, 30, "Steve"),
                new MapMarker("minecraft:player", 30, 90, 3, "Alex", 0xFF8AB4FF),
                MapMarker.at("red_banner", 96, 40, "Base"),
                MapMarker.at("plains_village", 75, 58, "Village"),
                MapMarker.at("red_x", 20, 20, "Treasure"));
    }

    private static final byte[] MAP = terrain(false);
    private static final byte[] PART = terrain(true);

    private static MapComponent map(Box box) {
        MapSource.MapData data = MapSource.MapData.ofColors(MAP, markers()).world(1, 200, -300);
        return new MapComponent(box).source(MapSource.of(data)).markerFiles(true).coordinates(true);
    }

    private static Box full() {
        return new Box("m", 8, 8, W - 16, H - 16);
    }

    @Override
    public void states(Stage stage) {
        stage.state("colours fit the box", W, H, s -> s.add(map(full())));
        stage.state("on vanilla paper", W, H, s -> s.add(map(full()).paper(true)));
        stage.state("small box", 80, 80, s -> s.add(map(new Box("m", 6, 6, 68, 68)).paper(true)));
        stage.state("unexplored and locked", W, H, s -> s.add(new MapComponent(full()).markerFiles(true)
                .source(MapSource.of(MapSource.MapData.ofColors(PART, markers()).locked(true)))));
        stage.state("wheel zoom at a point", W, H, s -> {
            MapComponent c = s.add(map(full()));
            s.scroll(100, 60, 1);
            s.scroll(100, 60, 1);
        });
        stage.state("zoomed and dragged", W, H, s -> {
            MapComponent c = s.add(map(full()));
            s.scroll(80, 80, 1);
            s.drag(80, 80, 40, 60);
        });
        stage.state("marker tooltip", W, H + 16, s -> {
            MapComponent c = s.add(map(full()));
            s.waitMs(16);
            float unit = (W - 16) / 128F;
            int px = Math.round(8 + 96 * unit);
            int py = Math.round(8 + 40 * unit);
            s.move(px, py);
            s.settle(700);
        });
        stage.state("coordinates tooltip", W, H + 16, s -> {
            s.add(map(full()));
            s.move(50, 110);
            s.settle(700);
        });
        stage.state("loading", W, H, s -> s.add(new MapComponent(full()).source(MapSource.of(MapSource.MapData.LOADING)).paper(true)));
        stage.state("no map", W, H, s -> s.add(new MapComponent(full()).source(id -> null)));
        stage.state("arrows in 16 turns", W, 60, s -> {
            List<MapMarker> ms = new java.util.ArrayList<>();
            for (int r = 0; r < 16; r++) ms.add(new MapMarker("minecraft:player", 8 + r * 7.5F, 64, r, "rot " + r, 0));
            byte[] flat = new byte[128 * 128];
            java.util.Arrays.fill(flat, packed(GRASS, NORMAL));
            s.add(new MapComponent(new Box("m", 0, 0, W, 60)).colors(flat, ms).view(1, 64, 64));
        });
    }
}
