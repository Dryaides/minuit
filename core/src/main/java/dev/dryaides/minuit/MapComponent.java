package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A Minecraft map in a box: the 128x128 picture, its markers and, if asked, the paper it is drawn on.
 *
 * <pre>{@code
 * MapComponent map = add(new MapComponent(at(new Box("map", 10, 10, 140, 140)))
 *         .source(VanillaMaps.INSTANCE).mapId(heldMapId)
 *         .paper(true).coordinates(true)
 *         .onMarker(m -> say(m.name())));
 * }</pre>
 *
 * <p><b>Two ways to paint.</b> When the {@link MapSource} gives a texture (in game: the one the game already keeps
 * for that map, {@code "minecraft:map/<id>"}), the map is one {@code Draw.image} call. When it only gives colours
 * (custom maps, the bench), the component merges equal pixels into runs and fills them; past
 * about a thousand rectangles it samples coarser blocks, since every rectangle costs in game.
 *
 * <p><b>Crisp.</b> One map pixel is a whole number of screen pixels: n / guiScale GUI units, the largest n that
 * fits (below one screen pixel a pixel, it fits the box as it can). The wheel zooms in by doubling (to 8x) at the
 * pointer, dragging pans, a right click resets. Markers come from the {@code minecraft:map_decorations} atlas
 * (or from the loose files in {@code textures/map/decorations}, see {@link #markerFiles(boolean)}); since Draw
 * cannot turn a sprite, turned arrows are drawn from the arrow's pixels, turned in 16 steps as vanilla does.
 * Hovering a named marker tells its name; with {@link #coordinates(boolean)} the tooltip tells the block under
 * the pointer.
 */
@Incubating
public class MapComponent extends BaseComponent {
    public static final String DECORATIONS_ATLAS = "minecraft:map_decorations";
    public static final String BACKGROUND = "minecraft:textures/map/map_background.png";
    private static final int PAPER = 0xFFDBCDA4;
    private static final int PAPER_EDGE = 0xFF8C7354;
    private static final int MAX_ZOOM = 8;

    private MapSource source;
    private int mapId;
    private boolean paper;
    private boolean markerFiles;
    private boolean coordinates;
    private boolean useTexture = true;
    private static final int RECT_BUDGET = 1024;
    private String loadingText;
    /** Null until the mod sets one: the lang entry then. */
    private String emptyText;
    private Words words;
    private final Map<String, String> spriteFiles = new HashMap<>();
    private int labelledZoom;
    private String zoomLabel = "";
    private Consumer<MapMarker> onMarker;
    private List<MapWaypoint> route = List.of();
    private List<MapMarker> routeSprites = List.of();
    private int reached;
    private String routeText;
    private int routeColor;
    private int labelledBlocks = -1;
    private String routeLabel = "";

    private int zoom = 1;
    private float cx = 64F;
    private float cy = 64F;
    private boolean pressed;

    // cache of the colour runs
    private byte[] runsFor;
    private int runsVersion = -1;
    private int[] runs = new int[0];
    private int runCount;
    private int runBlock = 1;

    // layout of the last frame
    private float originX;
    private float originY;
    private float unit = 1F;
    private int viewX;
    private int viewY;
    private int viewW;
    private int viewH;

    public MapComponent(Box box) {
        super(box);
    }

    // ---- setup ----

    public MapComponent source(MapSource source) {
        this.source = source;
        return this;
    }

    public MapComponent mapId(int mapId) {
        if (mapId != this.mapId) {
            this.mapId = mapId;
            resetView();
        }
        return this;
    }

    /** Raw colours (128*128 packed ids) and markers: a fixed source. */
    public MapComponent colors(byte[] colors, List<MapMarker> markers) {
        return source(MapSource.ofColors(colors, markers));
    }

    /** The vanilla map paper ({@code textures/map/map_background.png}) around the map, as a held map shows it. */
    public MapComponent paper(boolean paper) {
        this.paper = paper;
        return this;
    }

    /** Markers from {@code textures/map/decorations/<name>.png} instead of the atlas (the bench has no atlas). */
    public MapComponent markerFiles(boolean files) {
        this.markerFiles = files;
        return this;
    }

    /** The tooltip tells the block X and Z under the pointer (needs the source's centre and scale). */
    public MapComponent coordinates(boolean coordinates) {
        this.coordinates = coordinates;
        return this;
    }

    /** False paints colours even when a texture is given: for a Draw without textures. */
    public MapComponent useTexture(boolean useTexture) {
        this.useTexture = useTexture;
        return this;
    }

    public MapComponent loadingText(String text) {
        loadingText = text == null ? "" : text;
        return this;
    }

    @Override
    void words(Words words) {
        this.words = words;
    }

    public MapComponent emptyText(String text) {
        emptyText = text == null ? "" : text;
        return this;
    }

    /** Called when a marker is clicked. */
    public MapComponent onMarker(Consumer<MapMarker> onMarker) {
        this.onMarker = onMarker;
        return this;
    }

    /**
     * An itinerary drawn over the map: a line through the stops in order, in the theme's accent with a dark edge so
     * it reads on any terrain, travelled legs solid and the legs still to go dashed with a chevron pointing the way,
     * and the distance still to go in blocks in the bottom left corner. The first stop is usually the player
     * ({@link MapWaypoint#of(MapMarker)}), the last the destination. It follows zoom and pan and is cut at the
     * map's edge. Null or fewer than two stops clears it.
     */
    public MapComponent route(List<MapWaypoint> stops) {
        route = stops == null || stops.size() < 2 ? List.of() : List.copyOf(stops);
        List<MapMarker> sprites = new ArrayList<>();
        for (MapWaypoint w : route) {
            if (w.sprite() != null) {
                sprites.add(MapMarker.at(w.sprite(), w.x(), w.y(), w.name()));
            }
        }
        routeSprites = List.copyOf(sprites);
        reached = Math.min(reached, Math.max(0, route.size() - 1));
        labelledBlocks = -1;
        return this;
    }

    /**
     * How many legs are travelled: legs before stop {@code index} are drawn solid, the rest dashed, and the distance
     * label counts only the rest. 0 (the default) shows the whole route as still to go.
     */
    public MapComponent routeReached(int index) {
        reached = Math.max(0, Math.min(index, Math.max(0, route.size() - 1)));
        labelledBlocks = -1;
        return this;
    }

    /** The route line's colour (ARGB); 0 (the default) picks the theme's accent, or white when the accent is a terrain hue. */
    public MapComponent routeColor(int argb) {
        routeColor = argb;
        return this;
    }

    /** The distance label's text with {@code %s} for the number of blocks; null (the default) takes the lang entry. */
    public MapComponent routeText(String format) {
        routeText = format;
        labelledBlocks = -1;
        return this;
    }

    /** Zoom 1, 2, 4 or 8, centred on a map pixel. */
    public MapComponent view(int zoom, float centerX, float centerY) {
        this.zoom = clampZoom(zoom);
        cx = centerX;
        cy = centerY;
        keepIn();
        return this;
    }

    public void resetView() {
        zoom = 1;
        cx = 64F;
        cy = 64F;
    }

    // ---- reading ----

    public int zoom() { return zoom; }
    public float centerX() { return cx; }
    public float centerY() { return cy; }
    /** GUI units per map pixel at the last frame, zoom included. */
    float unit() { return unit; }
    /** Rectangles the colour path filled for the whole map (before culling), and the block size it sampled. */
    int runCount() { return runCount; }
    int runBlock() { return runBlock; }

    /** The route's stops, empty when none is set. */
    public List<MapWaypoint> route() { return route; }

    /** The length of the legs still to go, in blocks: map pixels times 2^scale of the current map (1 without one). */
    public double routeBlocks() {
        MapSource.MapData data = data();
        int scale = data == null ? 0 : data.scale();
        return pathLength(route, reached) * (1 << scale);
    }

    public MapSource.MapData data() {
        return source == null ? null : source.picture(mapId);
    }

    /** The map pixel under a GUI point at the last frame. */
    public float mapX(double gx) { return (float) ((gx - originX) / unit); }
    public float mapY(double gy) { return (float) ((gy - originY) / unit); }

    // ---- layout ----

    private static int clampZoom(int z) {
        int out = 1;
        while (out < MAX_ZOOM && out * 2 <= z) {
            out *= 2;
        }
        return out;
    }

    private void keepIn() {
        float half = 64F / zoom;
        cx = Math.max(half, Math.min(128F - half, cx));
        cy = Math.max(half, Math.min(128F - half, cy));
    }

    private int border(int side) {
        return paper ? Math.max(2, Math.round(side * 7F / 142F)) : 0;
    }

    private void layout(int gs) {
        int side = Math.min(w, h);
        int area = side - 2 * border(side);
        int whole = area / MapColors.SIZE;
        int n = area * gs / MapColors.SIZE;
        // whole GUI pixels per map pixel when that costs at most an eighth of the side; otherwise whole device pixels
        boolean snapped = whole >= 1 && whole * MapColors.SIZE * 8 >= area * 7;
        float base = snapped ? whole : n >= 1 ? n / (float) gs : area / (float) MapColors.SIZE;
        float drawn = base * MapColors.SIZE;
        viewW = Math.max(1, (int) Math.floor(drawn));
        viewH = viewW;
        viewX = x + (w - viewW) / 2;
        viewY = y + (h - viewH) / 2;
        unit = base * zoom;
        originX = snap(viewX + viewW / 2F - cx * unit, gs);
        originY = snap(viewY + viewH / 2F - cy * unit, gs);
    }

    private float snap(float v, int gs) {
        return unit == (int) unit ? Math.round(v) : Math.round(v * gs) / (float) gs;
    }

    // ---- drawing ----

    @Override
    protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
        int gs = Math.max(1, d.guiScale());
        layout(gs);
        Palette p = t.palette();
        if (paper) {
            int b = border(Math.min(w, h));
            d.image(BACKGROUND, viewX - b, viewY - b, viewW + 2 * b, viewH + 2 * b, 64, 64, 1F);
        } else {
            d.fill(viewX - 1, viewY - 1, viewX + viewW + 1, viewY + viewH + 1, PAPER_EDGE);
            d.fill(viewX, viewY, viewX + viewW, viewY + viewH, PAPER);
        }
        MapSource.MapData data = data();
        if (data == null || data.loading()) {
            String text = data == null ? text(emptyText, words, "minuit.map.none", "No map") : text(loadingText, words, "minuit.map.loading", "Loading map");
            if (data != null) {
                drawLoading(d, gs);
            }
            int ty = viewY + viewH / 2 - 4;
            d.fill(viewX, ty - 3, viewX + viewW, ty + 11, 0x66FFFFFF & PAPER | 0x66000000);
            d.textFit(text, viewX + 4, ty, viewW - 8, 0xFF3F3222);
            return;
        }
        d.scissor(viewX, viewY, viewX + viewW, viewY + viewH);
        d.push(originX, originY, unit);
        if (useTexture && data.texture() != null) {
            d.image(data.texture(), 0, 0, MapColors.SIZE, MapColors.SIZE, MapColors.SIZE, MapColors.SIZE, 1F);
        } else if (data.colors() != null) {
            drawRuns(d, data);
        }
        d.pop();
        if (!route.isEmpty()) {
            drawRoute(d, ROUTE_EDGE, 2);
            drawRoute(d, routeColor != 0 ? routeColor : routeInk(p.accent()), 0);
        }
        MapMarker hot = markerAt(data, mx, my);
        for (MapMarker m : data.markers()) {
            drawMarker(d, m, m == hot);
        }
        for (MapMarker m : routeSprites) {
            drawMarker(d, m, false);
        }
        d.endScissor();
        if (data.locked() && viewW >= 40) {
            drawLock(d, viewX + viewW - 9, viewY + 2);
        }
        if (zoom > 1 && viewW >= 40) {
            if (labelledZoom != zoom) {
                labelledZoom = zoom;
                zoomLabel = zoom + "x";
            }
            String z = zoomLabel;
            int tw = d.width(z);
            d.fill(viewX + 2, viewY + 2, viewX + tw + 6, viewY + 12, 0xAA000000);
            d.text(z, viewX + 4, viewY + 3, 0xFFFFFFFF);
        }
        if (!route.isEmpty() && viewW >= 40) {
            int blocks = (int) Math.round(routeBlocks());
            if (labelledBlocks != blocks) {
                labelledBlocks = blocks;
                String n = Integer.toString(blocks);
                // the lang entry goes through Words.get(key, args), which formats it; a mod's own text is only filled in
                routeLabel = routeText != null ? routeText.replace("%s", n)
                        : words != null ? words.get("minuit.map.route.blocks", n) : n + " blocks";
            }
            int tw = d.width(routeLabel);
            int ly = viewY + viewH - 12;
            d.fill(viewX + 2, ly, viewX + tw + 6, ly + 10, 0xAA000000);
            d.text(routeLabel, viewX + 4, ly + 1, 0xFFFFFFFF);
        }
        if (!active) {
            d.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0x88808080);
        }
        if (focused) {
            int c = p.primary();
            d.fill(viewX - 1, viewY - 1, viewX + viewW + 1, viewY, c);
            d.fill(viewX - 1, viewY + viewH, viewX + viewW + 1, viewY + viewH + 1, c);
        }
    }

    // ---- route ----

    private static final int ROUTE_EDGE = 0xC0000000;
    private static final int DASH_ON = 4;
    private static final int DASH_PERIOD = 7;

    /**
     * The accent, or white when it is a saturated green, cyan or blue (hue 70 to 250 degrees), the hues of grass,
     * forest, water and ice on a map, where it would read only through its dark edge.
     */
    static int routeInk(int accent) {
        int r = accent >> 16 & 0xFF;
        int g = accent >> 8 & 0xFF;
        int b = accent & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int span = max - Math.min(r, Math.min(g, b));
        float hue = span == 0 ? 0F : max == r ? 60F * (((g - b) / (float) span + 6F) % 6F)
                : max == g ? 60F * ((b - r) / (float) span + 2F) : 60F * ((r - g) / (float) span + 4F);
        boolean terrain = max > 0 && span >= max * 0.25F && hue >= 70F && hue <= 250F;
        return terrain ? 0xFFFFFFFF : 0xFF000000 | accent;
    }

    /** Sum of the straight legs from stop {@code from} to the last, in map pixels. */
    static double pathLength(List<MapWaypoint> stops, int from) {
        double sum = 0;
        for (int i = Math.max(0, from); i + 1 < stops.size(); i++) {
            sum += Math.hypot(stops.get(i + 1).x() - stops.get(i).x(), stops.get(i + 1).y() - stops.get(i).y());
        }
        return sum;
    }

    /**
     * The part of the segment inside the box (Liang-Barsky), as {t0, t1} along it, or null when none is. The box
     * bounds are inclusive pixel centres.
     */
    static float[] clip(float x0, float y0, float x1, float y1, float minX, float minY, float maxX, float maxY) {
        float t0 = 0F;
        float t1 = 1F;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float[] p = {-dx, dx, -dy, dy};
        float[] q = {x0 - minX, maxX - x0, y0 - minY, maxY - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0F) {
                if (q[i] < 0F) {
                    return null;
                }
                continue;
            }
            float r = q[i] / p[i];
            if (p[i] < 0F) {
                t0 = Math.max(t0, r);
            } else {
                t1 = Math.min(t1, r);
            }
            if (t0 > t1) {
                return null;
            }
        }
        return new float[] {t0, t1};
    }

    /** One pass of the route: {@code grow} 2 draws the dark edge under the line, 0 the line itself. */
    private void drawRoute(Draw d, int color, int grow) {
        float minX = viewX;
        float minY = viewY;
        float maxX = viewX + viewW - 1;
        float maxY = viewY + viewH - 1;
        float phase = 0F;
        for (int i = 0; i + 1 < route.size(); i++) {
            float ax = originX + route.get(i).x() * unit;
            float ay = originY + route.get(i).y() * unit;
            float bx = originX + route.get(i + 1).x() * unit;
            float by = originY + route.get(i + 1).y() * unit;
            float len = (float) Math.hypot(bx - ax, by - ay);
            boolean ahead = i >= reached;
            float[] t = clip(ax, ay, bx, by, minX, minY, maxX, maxY);
            if (t != null) {
                stroke(d, Math.round(ax + (bx - ax) * t[0]), Math.round(ay + (by - ay) * t[0]),
                        Math.round(ax + (bx - ax) * t[1]), Math.round(ay + (by - ay) * t[1]),
                        phase + t[0] * len, ahead, 2 + grow, color);
            }
            if (ahead && len >= 20F) {
                float ux = (bx - ax) / len;
                float uy = (by - ay) / len;
                int tx = Math.round((ax + bx) / 2F + ux * 2F);
                int ty = Math.round((ay + by) / 2F + uy * 2F);
                if (tx >= minX && ty >= minY && tx <= maxX && ty <= maxY) {
                    for (int side = -1; side <= 1; side += 2) {
                        int ex = Math.round(tx - ux * 4F - uy * 4F * side);
                        int ey = Math.round(ty - uy * 4F + ux * 4F * side);
                        stroke(d, tx, ty, ex, ey, 0F, false, 2 + grow, color);
                    }
                }
            }
            phase = ahead ? phase + len : 0F;
        }
        int s = 4 + grow;
        for (int i = 1; i < route.size(); i++) {
            MapWaypoint w = route.get(i);
            if (w.sprite() != null) {
                continue;
            }
            int px = Math.round(originX + w.x() * unit);
            int py = Math.round(originY + w.y() * unit);
            if (px >= minX && py >= minY && px <= maxX && py <= maxY) {
                d.fill(px - s / 2, py - s / 2, px - s / 2 + s, py - s / 2 + s, color);
            }
        }
    }

    /**
     * A line of whole GUI pixels from one pixel to another (Bresenham), {@code width} thick, pixels on the same row
     * (or column, for a steep line) merged into one fill. Dashed lines start {@code phase} pixels into the pattern,
     * so dashes run on across legs and across the view's edge.
     */
    private static void stroke(Draw d, int x0, int y0, int x1, int y1, float phase, boolean dashed, int width, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        boolean xMajor = dx >= dy;
        int steps = Math.max(dx, dy);
        float per = steps == 0 ? 0F : (float) Math.hypot(dx, dy) / steps;
        int off = width / 2;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        boolean open = false;
        int rx0 = 0;
        int ry0 = 0;
        int rx1 = 0;
        int ry1 = 0;
        for (int i = 0; i <= steps; i++) {
            boolean on = !dashed || (phase + i * per) % DASH_PERIOD < DASH_ON;
            if (open && (!on || (xMajor ? y != ry1 : x != rx1))) {
                d.fill(Math.min(rx0, rx1) - off, Math.min(ry0, ry1) - off,
                        Math.max(rx0, rx1) - off + width, Math.max(ry0, ry1) - off + width, color);
                open = false;
            }
            if (on) {
                if (!open) {
                    rx0 = x;
                    ry0 = y;
                    open = true;
                }
                rx1 = x;
                ry1 = y;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
        if (open) {
            d.fill(Math.min(rx0, rx1) - off, Math.min(ry0, ry1) - off,
                    Math.max(rx0, rx1) - off + width, Math.max(ry0, ry1) - off + width, color);
        }
    }

    /** Loading: faint stripes moving across the paper. */
    private void drawLoading(Draw d, int gs) {
        long now = clock() == null ? 0L : clock().getAsLong();
        int step = 12;
        int shift = (int) ((now / 60L) % step);
        for (int i = -step; i < viewW; i += step) {
            int x0 = Math.max(viewX, viewX + i + shift);
            int x1 = Math.min(viewX + viewW, viewX + i + shift + step / 2);
            if (x1 > x0) {
                d.fill(x0, viewY, x1, viewY + viewH, 0x14000000);
            }
        }
    }

    private void drawRuns(Draw d, MapSource.MapData data) {
        if (runsFor != data.colors() || runsVersion != data.version()) {
            buildRuns(data.colors());
            runsFor = data.colors();
            runsVersion = data.version();
        }
        // cull to the view, in map pixels
        float minX = (viewX - originX) / unit - 1;
        float minY = (viewY - originY) / unit - 1;
        float maxX = minX + viewW / unit + 2;
        float maxY = minY + viewH / unit + 2;
        int[] r = runs;
        for (int i = 0; i < runCount * 5; i += 5) {
            int rx = r[i];
            int ry = r[i + 1];
            int rw = r[i + 2];
            int rh = r[i + 3];
            if (rx > maxX || ry > maxY || rx + rw < minX || ry + rh < minY) {
                continue;
            }
            d.fill(rx, ry, rx + rw, ry + rh, r[i + 4]);
        }
    }

    /** Equal pixels to runs, merged down when a run repeats the one above; coarser blocks past the budget. */
    private void buildRuns(byte[] colors) {
        for (int block = 1; block <= 16; block *= 2) {
            int count = runsAt(colors, block, null);
            if (count <= RECT_BUDGET || block == 16) {
                runs = new int[count * 5];
                runCount = runsAt(colors, block, runs);
                runBlock = block;
                return;
            }
        }
    }

    private static int colorAt(byte[] c, int px, int py) {
        int i = py * MapColors.SIZE + px;
        return i < c.length ? MapColors.argb(c[i]) : 0;
    }

    /** Counts (out null) or writes runs: x, y, w, h, colour. Rows of blocks, a run merged with the same one above. */
    private static int runsAt(byte[] colors, int block, int[] out) {
        int size = MapColors.SIZE;
        int[] prevStart = new int[size];
        int[] prevIndex = new int[size];
        int prevCount = 0;
        int[] curStart = new int[size];
        int[] curIndex = new int[size];
        // with out == null, track runs in a scratch array to merge the same way
        int[] scratch = out != null ? out : new int[size * size / block / block * 5 + 5];
        int count = 0;
        for (int by = 0; by < size; by += block) {
            int curCount = 0;
            int p = 0;
            int bx = 0;
            while (bx < size) {
                int c = colorAt(colors, bx, by);
                int start = bx;
                bx += block;
                while (bx < size && colorAt(colors, bx, by) == c) {
                    bx += block;
                }
                if ((c >>> 24) == 0) {
                    continue;
                }
                int len = bx - start;
                while (p < prevCount && scratch[prevIndex[p] * 5] < start) {
                    p++;
                }
                int idx;
                int pi = p < prevCount ? prevIndex[p] * 5 : -1;
                if (pi >= 0 && scratch[pi] == start && scratch[pi + 2] == len && scratch[pi + 4] == c
                        && scratch[pi + 1] + scratch[pi + 3] == by) {
                    scratch[pi + 3] += block;
                    idx = prevIndex[p];
                } else {
                    idx = count++;
                    int o = idx * 5;
                    scratch[o] = start;
                    scratch[o + 1] = by;
                    scratch[o + 2] = len;
                    scratch[o + 3] = block;
                    scratch[o + 4] = c;
                }
                curStart[curCount] = start;
                curIndex[curCount++] = idx;
            }
            int[] ts = prevStart;
            prevStart = curStart;
            curStart = ts;
            int[] ti = prevIndex;
            prevIndex = curIndex;
            curIndex = ti;
            prevCount = curCount;
        }
        return count;
    }

    private int markerSize() {
        if (unit == (int) unit && unit >= 1F) {
            return unit >= 2F ? 16 : 8;
        }
        return Math.max(5, Math.round(8F * unit));
    }

    private void drawMarker(Draw d, MapMarker m, boolean hot) {
        int s = markerSize();
        float fx = originX + m.x() * unit;
        float fy = originY + m.y() * unit;
        int px = Math.round(fx) - s / 2;
        int py = Math.round(fy) - s / 2;
        if (px + s < viewX || py + s < viewY || px > viewX + viewW || py > viewY + viewH) {
            return;
        }
        if (hot) {
            d.fill(px - 1, py - 1, px + s + 1, py + s + 1, 0x55FFFFFF);
        }
        if (m.turns() && m.rot() != MapMarker.UPRIGHT) {
            Arrow.draw(d, m.rot(), px, py, s, tintOf(m));
            return;
        }
        if (markerFiles) {
            d.image(spriteFiles.computeIfAbsent(m.sprite(), MapComponent::spriteFile), px, py, s, s, 8, 8, 1F);
        } else {
            d.sprite(DECORATIONS_ATLAS, m.sprite(), px, py, s, s, 1F);
        }
    }

    private static String spriteFile(String id) {
        int c = id.indexOf(':');
        return id.substring(0, c) + ":textures/map/decorations/" + id.substring(c + 1) + ".png";
    }

    private static int tintOf(MapMarker m) {
        if (m.tint() != 0) {
            return m.tint();
        }
        String p = m.path();
        if (p.equals("red_marker")) return 0xFFFF2119;
        if (p.equals("blue_marker")) return 0xFF3F66FF;
        if (p.equals("frame")) return 0xFF22B14C;
        return 0xFFFFFFFF;
    }

    private static void drawLock(Draw d, int x, int y) {
        d.fill(x - 1, y - 1, x + 8, y + 10, 0x99000000);
        d.fill(x + 2, y, x + 5, y + 1, 0xFFC8C8C8);
        d.fill(x + 1, y + 1, x + 2, y + 4, 0xFFC8C8C8);
        d.fill(x + 5, y + 1, x + 6, y + 4, 0xFFC8C8C8);
        d.fill(x, y + 4, x + 7, y + 9, 0xFFE0B040);
        d.fill(x + 3, y + 5, x + 4, y + 7, 0xFF5A3A10);
    }

    // ---- input ----

    private MapMarker markerAt(MapSource.MapData data, double mx, double my) {
        if (data == null || mx < viewX || my < viewY || mx >= viewX + viewW || my >= viewY + viewH) {
            return null;
        }
        int half = markerSize() / 2 + 1;
        MapMarker best = null;
        for (MapMarker m : data.markers()) {
            float fx = originX + m.x() * unit;
            float fy = originY + m.y() * unit;
            if (Math.abs(mx - fx) <= half && Math.abs(my - fy) <= half) {
                best = m;
            }
        }
        return best;
    }

    private MapWaypoint waypointAt(double mx, double my) {
        if (mx < viewX || my < viewY || mx >= viewX + viewW || my >= viewY + viewH) {
            return null;
        }
        int half = markerSize() / 2 + 1;
        MapWaypoint best = null;
        for (MapWaypoint w : route) {
            if (Math.abs(mx - (originX + w.x() * unit)) <= half && Math.abs(my - (originY + w.y() * unit)) <= half) {
                best = w;
            }
        }
        return best;
    }

    @Override
    public List<String> tooltipAt(double mx, double my) {
        MapSource.MapData data = data();
        if (data == null || data.loading()) {
            return super.tooltipAt(mx, my);
        }
        MapMarker m = markerAt(data, mx, my);
        List<String> out = new ArrayList<>(2);
        if (m != null && !m.name().isEmpty()) {
            out.add(m.name());
        } else if (m == null) {
            MapWaypoint w = waypointAt(mx, my);
            if (w != null && !w.name().isEmpty()) {
                out.add(w.name());
            }
        }
        if (coordinates && mx >= viewX && my >= viewY && mx < viewX + viewW && my < viewY + viewH) {
            out.add("X " + data.blockX(mapX(mx)) + "  Z " + data.blockZ(mapY(my)));
        }
        return out.isEmpty() ? super.tooltipAt(mx, my) : out;
    }

    private boolean live() {
        return visible && active;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!live() || !isOver(mx, my) || amount == 0) {
            return false;
        }
        int next = amount > 0 ? Math.min(MAX_ZOOM, zoom * 2) : Math.max(1, zoom / 2);
        if (next == zoom) {
            return true;
        }
        // keep the map pixel under the pointer where it is
        float px = mapX(mx);
        float py = mapY(my);
        float dx = (float) (mx - (viewX + viewW / 2F));
        float dy = (float) (my - (viewY + viewH / 2F));
        zoom = next;
        float u = unit / (amount > 0 ? 0.5F : 2F);
        cx = px - dx / u;
        cy = py - dy / u;
        keepIn();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!live() || !isOver(mx, my)) {
            return false;
        }
        if (button == 1) {
            resetView();
            return true;
        }
        pressed = button == 0;
        MapMarker m = markerAt(data(), mx, my);
        if (m != null && onMarker != null) {
            click();
            onMarker.accept(m);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!live() || !pressed || zoom == 1) {
            return false;
        }
        cx -= (float) (dx / unit);
        cy -= (float) (dy / unit);
        keepIn();
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean was = pressed;
        pressed = false;
        return was;
    }

    /** The player arrow ({@code textures/map/decorations/player.png}), turned in 16 steps and drawn as runs. */
    static final class Arrow {
        // 0 clear, 1 black edge, 2 0xDF, 3 0xF1, 4 white: the sprite's pixels, row by row
        private static final String PIXELS =
                "....1..."
              + "...121.."
              + "...131.."
              + "..13431."
              + "..14441."
              + "..13431."
              + "..12321."
              + "...111..";
        private static final int[] LIGHT = {0, 0, 0xDF, 0xF1, 0xFF};
        private static final int CELLS = 16;
        private static final int[][] CACHE = new int[16][];

        private Arrow() {
        }

        static void draw(Draw d, int rot, int x, int y, int size, int tint) {
            int[] r = runs(rot);
            d.push(x, y, size / (float) CELLS);
            for (int i = 0; i < r.length; i += 4) {
                int shade = r[i + 3];
                int c = shade == 1 ? 0xFF000000 : shadeOf(tint, LIGHT[shade]);
                d.fill(r[i], r[i + 1], r[i] + r[i + 2], r[i + 1] + 1, c);
            }
            d.pop();
        }

        private static int shadeOf(int tint, int light) {
            int r = ((tint >> 16) & 0xFF) * light / 255;
            int g = ((tint >> 8) & 0xFF) * light / 255;
            int b = (tint & 0xFF) * light / 255;
            return 0xFF000000 | r << 16 | g << 8 | b;
        }

        /** Runs (x, y, len, shade) on a 16x16 grid: the 8x8 sprite sampled at two cells a pixel, turned. */
        static synchronized int[] runs(int rot) {
            int[] cached = CACHE[rot & 15];
            if (cached != null) {
                return cached;
            }
            // vanilla turns by rot * 22.5 degrees from upside down; 8 is upright
            double a = Math.toRadians(((rot & 15) - 8) * 22.5D);
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            int[] tmp = new int[CELLS * CELLS * 4];
            int n = 0;
            for (int j = 0; j < CELLS; j++) {
                int runStart = -1;
                int runShade = 0;
                for (int i = 0; i <= CELLS; i++) {
                    int shade = 0;
                    if (i < CELLS) {
                        double dx = (i + 0.5D) / 2D - 4D;
                        double dy = (j + 0.5D) / 2D - 4D;
                        // inverse turn: screen to sprite
                        double sx = dx * cos + dy * sin + 4D;
                        double sy = -dx * sin + dy * cos + 4D;
                        int px = (int) Math.floor(sx);
                        int py = (int) Math.floor(sy);
                        if (px >= 0 && py >= 0 && px < 8 && py < 8) {
                            char ch = PIXELS.charAt(py * 8 + px);
                            shade = ch == '.' ? 0 : ch - '0';
                        }
                    }
                    if (shade != runShade) {
                        if (runShade != 0) {
                            tmp[n++] = runStart;
                            tmp[n++] = j;
                            tmp[n++] = i - runStart;
                            tmp[n++] = runShade;
                        }
                        runStart = i;
                        runShade = shade;
                    }
                }
            }
            int[] out = java.util.Arrays.copyOf(tmp, n);
            CACHE[rot & 15] = out;
            return out;
        }
    }
}
