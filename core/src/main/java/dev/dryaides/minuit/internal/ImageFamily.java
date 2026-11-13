package dev.dryaides.minuit.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.CheckboxComponent;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.FrameStyle;
import dev.dryaides.minuit.internal.ImageSource;
import dev.dryaides.minuit.Insets;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.Shapes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * A family that draws some parts from PNGs in a resource pack, cut in nine, and every other part with its base
 * {@link FrameStyle}: the {@code "sprites"} of a family.json. See THEMES.md, "A family from images". Not API.
 */
public final class ImageFamily implements FrameStyle {

    private static volatile ImageSource source;

    /** The renderer's image source; until one is installed every sprite family draws as its base style. */
    public static void install(ImageSource imageSource) {
        source = imageSource;
    }

    /** Bumped by each reload: a family drawn by a screen opened before it forgets its images and baked ids. */
    private static volatile int generation;

    /**
     * The frame clock: about 67 ms a step. A dropped image is freed two steps later, so never while the frame that
     * last blitted it may still render it (26.x draws the GUI after extraction). Tests set it.
     */
    static LongSupplier clock = () -> System.nanoTime() >>> 26;

    /** The installed image source, or null: the raster cache of every family bakes through it too. */
    public static ImageSource source() {
        return source;
    }

    /** Bumped by each reload, after the source freed every image it baked. */
    public static int generation() {
        return generation;
    }

    /** The current step of the frame {@link #clock}. */
    public static long clockStep() {
        return clock.getAsLong();
    }

    /** After {@link ThemeFiles#reload} loaded fresh families: the source frees the old ones' baked images. */
    static void reloaded() {
        generation++;
        ImageSource s = source;
        if (s != null) {
            s.reload();
        }
    }

    /** How the edges and the centre of a sprite cover a box larger than it. */
    public enum Fill { TILE, STRETCH }

    /** No tint, a multiply by one role, or a gradient map whose stops are roles; {@code face} is the caller's colour. */
    public record Tint(Kind kind, String[] roles) {
        public enum Kind { NONE, MULTIPLY, MAP }

        public static final Tint NONE = new Tint(Kind.NONE, new String[0]);
    }

    /** One sprite: its nine-slice borders and its overhang past the box, in GUI pixels. */
    public record Sprite(Insets inset, Insets outset, Fill fill, Tint tint) {
    }

    /**
     * What {@code "sprites"} says.
     *
     * @param folder  the PNGs' folder under the family's folder
     * @param scale   texels per GUI pixel
     * @param bleed   the button bleed, or null for the base style's
     * @param sprites every sprite by its name, {@code part} or {@code part.role}
     */
    public record Sheet(String folder, int scale, Insets bleed, Map<String, Sprite> sprites) {
    }

    private static final String[] ROLE_NAMES = {
            "frame", "frame_dark", "frame_light", "trim", "surface",
            "surface_edge", "section", "well", "well_edge", "text",
            "text_soft", "text_faint", "raised", "raised_hover", "raised_text",
            "primary", "primary_hover", "primary_text", "accent", "accent_deep",
            "good", "warn", "bad", "destructive", "destructive_hover",
            "destructive_text", "track", "disabled", "disabled_text", "shadow"};

    private static final int[] BUTTON_ROLES = roleIndices("raised", "raised_hover", "primary", "primary_hover",
            "destructive", "destructive_hover", "disabled");
    private static final int[] TRACK_ROLES = roleIndices("track", "accent", "accent_deep", "primary", "primary_hover", "disabled", "well_edge");

    /** Baked images kept per family; grown up to {@link #BAKED_LIMIT} while one frame needs more. */
    static final int BAKED_MIN = 64;
    static final int BAKED_LIMIT = 256;

    private final FrameStyle base;
    private final Sheet sheet;
    private final String root;
    private final Map<String, Part> parts = new HashMap<>();
    private int seen = generation;
    private int bakedCount;
    private int cap = BAKED_MIN;
    private long serial;
    private int[] stops = new int[4];
    private final List<String> dropping = new ArrayList<>();
    private long droppedAt;
    private boolean overLimitLogged;

    /** One sprite as drawn: its pixels (null when absent), the untinted region and the tinted ones baked so far. */
    private static final class Part {
        final String name;
        final Sprite sprite;
        final ImageSource.Pixels img;
        final PartTable.Region plain;
        final int[] tintRoles;
        Bake[] bakes = new Bake[0];
        int bakeCount;
        String[] dotted;

        Part(String name, Sprite sprite, ImageSource.Pixels img, PartTable.Region plain) {
            this.name = name;
            this.sprite = sprite;
            this.img = img;
            this.plain = plain;
            String[] roles = sprite == null ? new String[0] : sprite.tint().roles();
            this.tintRoles = new int[roles.length];
            for (int i = 0; i < roles.length; i++) {
                this.tintRoles[i] = roles[i].equals("face") ? -1 : roleIndex(roles[i]);
            }
        }
    }

    private static final class Bake {
        final int[] stops;
        final PartTable.Region region;
        long used;

        Bake(int[] stops, PartTable.Region region, long used) {
            this.stops = stops;
            this.region = region;
            this.used = used;
        }
    }

    /** @param root the id prefix of the PNGs, {@code namespace:minuit/themes/<family>/<folder>/} */
    public ImageFamily(FrameStyle base, Sheet sheet, String root) {
        this.base = base;
        this.sheet = sheet;
        this.root = root;
    }

    public FrameStyle base() {
        return this.base;
    }

    // ---- the sheet as family.json writes it ----

    public static Sheet parse(JsonElement element) throws ThemeFiles.BadFile {
        if (!element.isJsonObject()) {
            throw new ThemeFiles.BadFile("sprites", "not an object");
        }
        JsonObject o = element.getAsJsonObject();
        String folder = "sprites";
        if (o.has("folder")) {
            JsonElement f = o.get("folder");
            if (!f.isJsonPrimitive() || !f.getAsString().matches("[a-z0-9_./-]+")) {
                throw new ThemeFiles.BadFile("sprites.folder", "not a lowercase path");
            }
            folder = f.getAsString();
        }
        int scale = o.has("scale") ? number(o.get("scale"), "sprites.scale") : 1;
        if (scale < 1) {
            throw new ThemeFiles.BadFile("sprites.scale", "below 1");
        }
        Insets bleed = o.has("bleed") ? insets(o.get("bleed"), "sprites.bleed") : null;
        JsonElement list = o.get("parts");
        if (list == null || !list.isJsonObject()) {
            throw new ThemeFiles.BadFile("sprites.parts", "missing, or not an object");
        }
        Map<String, JsonObject> raw = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : list.getAsJsonObject().entrySet()) {
            String where = "sprites.parts." + e.getKey();
            if (!e.getKey().matches("[a-z0-9_]+(\\.[a-z0-9_]+)?")) {
                throw new ThemeFiles.BadFile(where, "not a sprite name, part or part.role");
            }
            if (!e.getValue().isJsonObject()) {
                throw new ThemeFiles.BadFile(where, "not an object");
            }
            raw.put(e.getKey(), e.getValue().getAsJsonObject());
        }
        Map<String, Sprite> sprites = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> e : raw.entrySet()) {
            int dot = e.getKey().indexOf('.');
            JsonObject parent = dot < 0 ? null : raw.get(e.getKey().substring(0, dot));
            sprites.put(e.getKey(), sprite(e.getValue(), parent, "sprites.parts." + e.getKey()));
        }
        return new Sheet(folder, scale, bleed, sprites);
    }

    /** A {@code part.role} entry takes each field it leaves out from its {@code part} entry. */
    private static Sprite sprite(JsonObject o, JsonObject parent, String where) throws ThemeFiles.BadFile {
        JsonElement inset = field(o, parent, "inset");
        JsonElement outset = field(o, parent, "outset");
        JsonElement fill = field(o, parent, "fill");
        JsonElement tint = field(o, parent, "tint");
        Fill f = Fill.TILE;
        if (fill != null) {
            String text = fill.isJsonPrimitive() ? fill.getAsString() : "";
            f = switch (text) {
                case "tile" -> Fill.TILE;
                case "stretch" -> Fill.STRETCH;
                default -> throw new ThemeFiles.BadFile(where + ".fill", "use tile or stretch");
            };
        }
        return new Sprite(inset == null ? Insets.NONE : insets(inset, where + ".inset"),
                outset == null ? Insets.NONE : insets(outset, where + ".outset"), f,
                tint == null ? Tint.NONE : tint(tint, where + ".tint"));
    }

    private static JsonElement field(JsonObject o, JsonObject parent, String key) {
        return o.has(key) ? o.get(key) : parent != null ? parent.get(key) : null;
    }

    private static Tint tint(JsonElement e, String where) throws ThemeFiles.BadFile {
        if (e.isJsonPrimitive() && "none".equals(e.getAsString())) {
            return Tint.NONE;
        }
        if (e.isJsonObject() && e.getAsJsonObject().has("multiply")) {
            String role = e.getAsJsonObject().get("multiply").getAsString();
            checkRole(role, where + ".multiply");
            return new Tint(Tint.Kind.MULTIPLY, new String[]{role});
        }
        if (e.isJsonObject() && e.getAsJsonObject().get("map") instanceof JsonArray stops && stops.size() >= 2) {
            String[] roles = new String[stops.size()];
            for (int i = 0; i < roles.length; i++) {
                roles[i] = stops.get(i).getAsString();
                checkRole(roles[i], where + ".map[" + i + "]");
            }
            return new Tint(Tint.Kind.MAP, roles);
        }
        throw new ThemeFiles.BadFile(where, "use \"none\", {\"multiply\": role} or {\"map\": [role, role, ...]}");
    }

    private static void checkRole(String role, String where) throws ThemeFiles.BadFile {
        if (!role.equals("face") && !isRole(role)) {
            throw new ThemeFiles.BadFile(where, "unknown role " + role);
        }
    }

    private static Insets insets(JsonElement e, String where) throws ThemeFiles.BadFile {
        if (e.isJsonPrimitive()) {
            int n = number(e, where);
            return new Insets(n, n, n, n);
        }
        if (!e.isJsonObject()) {
            throw new ThemeFiles.BadFile(where, "a number or {top, left, right, bottom}");
        }
        JsonObject o = e.getAsJsonObject();
        for (String key : o.keySet()) {
            if (!key.equals("top") && !key.equals("left") && !key.equals("right") && !key.equals("bottom")) {
                throw new ThemeFiles.BadFile(where + "." + key, "use top, left, right or bottom");
            }
        }
        return new Insets(side(o, "top", where), side(o, "left", where), side(o, "right", where), side(o, "bottom", where));
    }

    private static int side(JsonObject o, String key, String where) throws ThemeFiles.BadFile {
        return o.has(key) ? number(o.get(key), where + "." + key) : 0;
    }

    private static int number(JsonElement e, String where) throws ThemeFiles.BadFile {
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || e.getAsDouble() != Math.floor(e.getAsDouble())
                || e.getAsInt() < 0) {
            throw new ThemeFiles.BadFile(where, "not a whole number of pixels");
        }
        return e.getAsInt();
    }

    // ---- roles by their snake-case names, without reflection (the site's TeaVM build has none for records) ----

    private static boolean isRole(String role) {
        return roleIndex(role) >= 0;
    }

    private static int roleIndex(String role) {
        for (int i = 0; i < ROLE_NAMES.length; i++) {
            if (ROLE_NAMES[i].equals(role)) {
                return i;
            }
        }
        return -1;
    }

    private static int[] roleIndices(String... roles) {
        int[] out = new int[roles.length];
        for (int i = 0; i < roles.length; i++) {
            out[i] = roleIndex(roles[i]);
        }
        return out;
    }

    /** The colour of {@code role} in {@code p}, or null for no such role. */
    static Integer role(Palette p, String role) {
        int i = roleIndex(role);
        return i < 0 ? null : color(p, i);
    }

    /** The colour of the role at {@code index} of the role names. */
    private static int color(Palette p, int index) {
        return switch (index) {
            case 0 -> p.frame();
            case 1 -> p.frameDark();
            case 2 -> p.frameLight();
            case 3 -> p.trim();
            case 4 -> p.surface();
            case 5 -> p.surfaceEdge();
            case 6 -> p.section();
            case 7 -> p.well();
            case 8 -> p.wellEdge();
            case 9 -> p.text();
            case 10 -> p.textSoft();
            case 11 -> p.textFaint();
            case 12 -> p.raised();
            case 13 -> p.raisedHover();
            case 14 -> p.raisedText();
            case 15 -> p.primary();
            case 16 -> p.primaryHover();
            case 17 -> p.primaryText();
            case 18 -> p.accent();
            case 19 -> p.accentDeep();
            case 20 -> p.good();
            case 21 -> p.warn();
            case 22 -> p.bad();
            case 23 -> p.destructive();
            case 24 -> p.destructiveHover();
            case 25 -> p.destructiveText();
            case 26 -> p.track();
            case 27 -> p.disabled();
            case 28 -> p.disabledText();
            case 29 -> p.shadow();
            default -> throw new IllegalArgumentException("role " + index);
        };
    }

    /** Of {@code roles}, the one whose colour in {@code p} is {@code color}, else the nearest in RGB. */
    static String nearest(Palette p, int color, String[] roles) {
        return ROLE_NAMES[nearest(p, color, roleIndices(roles))];
    }

    private static int nearest(Palette p, int color, int[] roles) {
        int best = roles[0];
        int bestDist = Integer.MAX_VALUE;
        for (int r : roles) {
            int c = color(p, r);
            int dist = Math.abs((c >>> 24) - (color >>> 24));
            for (int sh = 0; sh <= 16; sh += 8) {
                dist += Math.abs((c >> sh & 0xFF) - (color >> sh & 0xFF));
            }
            if (dist < bestDist) {
                best = r;
                bestDist = dist;
            }
        }
        return best;
    }

    // ---- tinting, the same on every renderer ----

    /** {@code argb} tinted: multiplied by {@code stops[0]}, or its grey run along the stops; the alpha multiplied in. */
    static int tint(int argb, Tint.Kind kind, int[] stops) {
        int a = argb >>> 24;
        if (kind == Tint.Kind.NONE || a == 0) {
            return argb;
        }
        int c;
        if (kind == Tint.Kind.MULTIPLY) {
            int t = stops[0];
            c = 0;
            for (int sh = 0; sh <= 16; sh += 8) {
                c |= mul(argb >> sh & 0xFF, t >> sh & 0xFF) << sh;
            }
            c |= (t >>> 24) << 24;
        } else {
            int grey = ((argb >> 16 & 0xFF) + (argb >> 8 & 0xFF) + (argb & 0xFF) + 1) / 3;
            int span = stops.length - 1;
            int at = grey * span;
            int i = Math.min(span - 1, at / 255);
            int frac = at - i * 255;
            int lo = stops[i];
            int hi = stops[i + 1];
            c = 0;
            for (int sh = 0; sh <= 24; sh += 8) {
                int l = lo >>> sh & 0xFF;
                int h = hi >>> sh & 0xFF;
                c |= (l + ((h - l) * frac + (h > l ? 127 : -127)) / 255) << sh;
            }
        }
        return (mul(a, c >>> 24) << 24) | (c & 0xFFFFFF);
    }

    private static int mul(int a, int b) {
        return (a * b + 127) / 255;
    }

    // ---- drawing ----

    /**
     * At the start of each sprite draw: after a reload the family forgets its pixels and its baked ids (the source
     * freed them), and drops queued two clock steps ago are freed.
     */
    private long begin() {
        long now = clock.getAsLong();
        if (this.seen != generation) {
            this.seen = generation;
            this.parts.clear();
            this.bakedCount = 0;
            this.cap = BAKED_MIN;
            this.dropping.clear();
        }
        if (!this.dropping.isEmpty() && now >= this.droppedAt + 2) {
            ImageSource s = source;
            if (s != null) {
                for (int i = 0; i < this.dropping.size(); i++) {
                    s.drop(this.dropping.get(i));
                }
            }
            this.dropping.clear();
        }
        return now;
    }

    private static final Part NO_SOURCE = new Part("", null, null, null);

    /** The part {@code name}, read once; a listed PNG that is missing or off the scale is remembered as absent. */
    private Part part(String name) {
        Part got = this.parts.get(name);
        if (got == null) {
            if (source == null) {
                return NO_SOURCE;
            }
            got = load(name);
            this.parts.put(name, got);
        }
        return got;
    }

    private Part load(String name) {
        Sprite sprite = this.sheet.sprites().get(name);
        if (sprite == null) {
            return new Part(name, null, null, null);
        }
        String file = this.root + name + ".png";
        ImageSource.Pixels img = source.read(file);
        int n = this.sheet.scale();
        if (img == null) {
            warn(file + " is listed but missing: the part draws in the base style until the next reload");
            return new Part(name, sprite, null, null);
        }
        if (img.w() % n != 0 || img.h() % n != 0) {
            warn(file + " is " + img.w() + "x" + img.h() + ", not a multiple of the scale " + n
                    + ": the part draws in the base style until the next reload");
            return new Part(name, sprite, null, null);
        }
        return new Part(name, sprite, img, region(file, img, sprite));
    }

    private PartTable.Region region(String texture, ImageSource.Pixels img, Sprite sprite) {
        int n = this.sheet.scale();
        Insets in = sprite.inset();
        return new PartTable.Region(texture, 0, 0, img.w() / n, img.h() / n, img.w() / n, img.h() / n,
                in.left(), in.top(), in.right(), in.bottom());
    }

    private static void warn(String line) {
        System.err.println("[minuit] " + line);
    }

    private boolean has(String name) {
        begin();
        return part(name).img != null;
    }

    /** The first of {@code a} (may be null) and {@code b} that the sheet lists and the source has. */
    private Part pick(String a, String b) {
        if (a != null) {
            Part pa = part(a);
            if (pa.img != null) {
                return pa;
            }
        }
        if (b != null) {
            Part pb = part(b);
            if (pb.img != null) {
                return pb;
            }
        }
        return null;
    }

    /** {@code base + "." + role}, built once per part and role. */
    private String dotted(String base, int role) {
        Part bp = part(base);
        if (bp == NO_SOURCE) {
            return null;
        }
        if (bp.dotted == null) {
            bp.dotted = new String[ROLE_NAMES.length];
        }
        String got = bp.dotted[role];
        if (got == null) {
            got = base + "." + ROLE_NAMES[role];
            bp.dotted[role] = got;
        }
        return got;
    }

    /** The part over the box, tinted with {@code face} as the role {@code face} stands for; false when it cannot. */
    private boolean draw(Draw d, Palette p, Part pt, int face, long now, int x, int y, int w, int h) {
        Sprite sprite = pt.sprite;
        PartTable.Region r = sprite.tint().kind() == Tint.Kind.NONE ? pt.plain : baked(p, pt, face, now);
        if (r == null) {
            return false;
        }
        Insets o = sprite.outset();
        x -= o.left();
        y -= o.top();
        w += o.left() + o.right();
        h += o.top() + o.bottom();
        if (sprite.fill() == Fill.TILE) {
            NineSlice.tile(d, r, x, y, w, h, 1F);
        } else {
            stretch(d, r, x, y, w, h);
        }
        return true;
    }

    /** Corners at their size, edges stretched along their length only, the centre both ways. */
    static void stretch(Draw d, PartTable.Region r, int x, int y, int w, int h) {
        int left = Math.min(r.left(), w / 2);
        int right = Math.min(r.right(), w / 2);
        int top = Math.min(r.top(), h / 2);
        int bottom = Math.min(r.bottom(), h / 2);
        for (int row = 0; row < 3; row++) {
            int ty = NineSlice.cut(row, y, top, bottom, h);
            int th = NineSlice.cut(row + 1, y, top, bottom, h) - ty;
            int sv = NineSlice.cut(row, 0, top, bottom, r.h());
            int vh = NineSlice.cut(row + 1, 0, top, bottom, r.h()) - sv;
            for (int col = 0; col < 3; col++) {
                int tx = NineSlice.cut(col, x, left, right, w);
                int tw = NineSlice.cut(col + 1, x, left, right, w) - tx;
                int su = NineSlice.cut(col, 0, left, right, r.w());
                int uw = NineSlice.cut(col + 1, 0, left, right, r.w()) - su;
                if (tw > 0 && th > 0 && uw > 0 && vh > 0) {
                    d.blitRegion(r.texture(), tx, ty, tw, th, su, sv, uw, vh, r.texW(), r.texH(), 1F);
                }
            }
        }
    }

    /**
     * The part tinted with the palette's stops, baked on the first draw that needs it. When the family holds
     * {@link #cap} images, those no draw used this clock step are dropped (freed two steps later); when every one was
     * used, the cap doubles up to {@link #BAKED_LIMIT}, past which the part falls back to the base style.
     */
    private PartTable.Region baked(Palette p, Part pt, int face, long now) {
        int[] roles = pt.tintRoles;
        int n = roles.length;
        if (this.stops.length < n) {
            this.stops = new int[n];
        }
        int[] s = this.stops;
        for (int i = 0; i < n; i++) {
            s[i] = roles[i] < 0 ? face : color(p, roles[i]);
        }
        for (int b = 0; b < pt.bakeCount; b++) {
            Bake k = pt.bakes[b];
            if (same(k.stops, s, n)) {
                k.used = now;
                return k.region;
            }
        }
        ImageSource src = source;
        if (src == null) {
            return null;
        }
        if (this.bakedCount >= this.cap) {
            evict(now);
            if (this.bakedCount >= this.cap) {
                if (this.cap >= BAKED_LIMIT) {
                    if (!this.overLimitLogged) {
                        this.overLimitLogged = true;
                        warn(this.root + ": one frame needs more than " + BAKED_LIMIT
                                + " tinted images; the rest draw in the base style");
                    }
                    return null;
                }
                this.cap = Math.min(this.cap * 2, BAKED_LIMIT);
            }
        }
        int[] own = Arrays.copyOf(s, n);
        int[] in = pt.img.argb();
        int[] out = new int[in.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = tint(in[i], pt.sprite.tint().kind(), own);
        }
        StringBuilder key = new StringBuilder(this.root.replace(':', '/')).append(pt.name.toLowerCase(Locale.ROOT));
        for (int stop : own) {
            key.append('_').append(Integer.toHexString(stop));
        }
        // A fresh id per bake: an id dropped earlier may still be queued for this frame's render.
        key.append('_').append(this.serial++);
        String id = src.bake(key.toString(), new ImageSource.Pixels(pt.img.w(), pt.img.h(), out));
        Bake made = new Bake(own, region(id, pt.img, pt.sprite), now);
        if (pt.bakeCount == pt.bakes.length) {
            pt.bakes = Arrays.copyOf(pt.bakes, Math.max(4, pt.bakeCount * 2));
        }
        pt.bakes[pt.bakeCount++] = made;
        this.bakedCount++;
        return made.region;
    }

    private static boolean same(int[] a, int[] b, int n) {
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    /** Queues for freeing every baked image no draw used at {@code now}. */
    private void evict(long now) {
        for (Part pt : this.parts.values()) {
            int kept = 0;
            for (int b = 0; b < pt.bakeCount; b++) {
                Bake k = pt.bakes[b];
                if (k.used == now) {
                    pt.bakes[kept++] = k;
                } else {
                    this.dropping.add(k.region.texture());
                    this.droppedAt = now;
                    this.bakedCount--;
                }
            }
            for (int b = kept; b < pt.bakeCount; b++) {
                pt.bakes[b] = null;
            }
            pt.bakeCount = kept;
        }
    }

    /** A part that takes a colour: {@code part.role}, then {@code part}, the face being the matched role's colour. */
    private boolean faced(Draw d, Palette p, int[] roles, int color, int x, int y, int w, int h, String a, String b) {
        long now = begin();
        int role = nearest(p, color, roles);
        Part pt = a == null ? null : pick(dotted(a, role), a);
        if (pt == null) {
            pt = pick(dotted(b, role), b);
        }
        return pt != null && draw(d, p, pt, color(p, role), now, x, y, w, h);
    }

    private boolean plain(Draw d, Palette p, int x, int y, int w, int h, String a, String b) {
        long now = begin();
        Part pt = pick(a, b);
        return pt != null && draw(d, p, pt, p.surface(), now, x, y, w, h);
    }

    // ---- the parts sprites can draw ----

    @Override
    public void panel(Draw d, Palette p, int x, int y, int w, int h) {
        if (!plain(d, p, x, y, w, h, null, "panel")) {
            this.base.panel(d, p, x, y, w, h);
        }
    }

    @Override
    public void header(Draw d, Palette p, int x, int y, int w, int h) {
        if (!plain(d, p, x, y, w, h, null, "header")) {
            this.base.header(d, p, x, y, w, h);
        }
    }

    @Override
    public void section(Draw d, Palette p, int x, int y, int w, int h) {
        if (!plain(d, p, x, y, w, h, null, "section")) {
            this.base.section(d, p, x, y, w, h);
        }
    }

    @Override
    public void well(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
        if (!plain(d, p, x, y, w, h, focused ? "well_focused" : null, "well")) {
            this.base.well(d, p, x, y, w, h, focused);
        }
    }

    @Override
    public void field(Draw d, Palette p, int x, int y, int w, int h, boolean focused) {
        if (!plain(d, p, x, y, w, h, focused ? "field_focused" : null, "field")) {
            this.base.field(d, p, x, y, w, h, focused);
        }
    }

    @Override
    public void raised(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief) {
        if (!buttonSprite(d, p, x, y, w, h, color, relief)) {
            this.base.raised(d, p, x, y, w, h, color, relief);
        }
    }

    private boolean buttonSprite(Draw d, Palette p, int x, int y, int w, int h, int color, Shapes.Relief relief) {
        return relief == Shapes.Relief.PRESSED
                ? faced(d, p, BUTTON_ROLES, color, x, y, w, h, "button_pressed", "button")
                : faced(d, p, BUTTON_ROLES, color, x, y, w, h, null, "button");
    }

    /** The button sprite over the box grown by the bleed; the role shows through its colour. */
    @Override
    public void button(Draw d, Palette p, int x, int y, int w, int h, ButtonComponent.Role role, int color,
                       Shapes.Relief relief) {
        Insets b = FrameStyle.bleedOf(this);
        if (!buttonSprite(d, p, x - b.left(), y - b.top(), w + b.left() + b.right(), h + b.top() + b.bottom(), color,
                relief)) {
            this.base.button(d, p, x, y, w, h, role, color, relief);
        }
    }

    @Override
    public void track(Draw d, Palette p, int x, int y, int w, int h, int color) {
        if (!faced(d, p, TRACK_ROLES, color, x, y, w, h, null, "track")) {
            this.base.track(d, p, x, y, w, h, color);
        }
    }

    @Override
    public void knob(Draw d, Palette p, int x, int y, boolean lit) {
        if (!plain(d, p, x, y, 7, 8, lit ? "knob_lit" : null, "knob")) {
            this.base.knob(d, p, x, y, lit);
        }
    }

    @Override
    public void slot(Draw d, Palette p, int x, int y, boolean hovered, boolean marked) {
        if (!plain(d, p, x, y, 18, 18, null, "slot")) {
            this.base.slot(d, p, x, y, hovered, marked);
            return;
        }
        if (hovered) {
            d.fill(x + 1, y + 1, x + 17, y + 17, Shapes.SLOT_HOVER_BACK);
        }
        if (marked) {
            Shapes.ring(d, x, y, 18, 18, 0, p.accent());
        }
    }

    /** The popup sprite; without one but with a panel sprite, the base's drop shadow under the panel sprite. */
    @Override
    public void popup(Draw d, Palette p, int x, int y, int w, int h) {
        if (plain(d, p, x, y, w, h, null, "popup")) {
            return;
        }
        if (has("panel")) {
            this.base.dropShadow(d, p, x, y, w, h, 2);
            panel(d, p, x, y, w, h);
        } else {
            this.base.popup(d, p, x, y, w, h);
        }
    }

    @Override
    public void toast(Draw d, Palette p, int x, int y, int w, int h) {
        if (has("panel")) {
            panel(d, p, x, y, w, h);
        } else {
            this.base.toast(d, p, x, y, w, h);
        }
    }

    @Override
    public Insets bleed() {
        return this.sheet.bleed() != null ? this.sheet.bleed() : this.base.bleed();
    }

    // ---- everything else is the base style's ----

    @Override public int corner() { return this.base.corner(); }
    @Override public void shadow(Draw d, Palette p, int x, int y, int w, int h, int color) { this.base.shadow(d, p, x, y, w, h, color); }
    @Override public void dropShadow(Draw d, Palette p, int x, int y, int w, int h, int depth) { this.base.dropShadow(d, p, x, y, w, h, depth); }
    @Override public Insets panelInsets() { return this.base.panelInsets(); }
    @Override public int panelShadow() { return this.base.panelShadow(); }
    @Override public int selectedFace(Palette p, float hover) { return this.base.selectedFace(p, hover); }
    @Override public Shapes.Relief selectedRelief() { return this.base.selectedRelief(); }
    @Override public void bar(Draw d, Palette p, int x, int y, int w, int h, float value, int color) { this.base.bar(d, p, x, y, w, h, value, color); }
    @Override public void trackEdge(Draw d, Palette p, int x, int y, int w, int h, int color) { this.base.trackEdge(d, p, x, y, w, h, color); }
    @Override public void toggleKnob(Draw d, Palette p, int x, int y, int face, boolean active) { this.base.toggleKnob(d, p, x, y, face, active); }
    @Override public void groove(Draw d, Palette p, int x, int y, int w, int h, int filled, int steps, boolean active) { this.base.groove(d, p, x, y, w, h, filled, steps, active); }
    @Override public void toggleKnob(Draw d, Palette p, int x, int y, int face, boolean active, boolean lit) { this.base.toggleKnob(d, p, x, y, face, active, lit); }
    @Override public int fieldText(Palette p) { return this.base.fieldText(p); }
    @Override public int fieldHint(Palette p) { return this.base.fieldHint(p); }
    @Override public void hotbar(Draw d, Palette p, int x, int y) { this.base.hotbar(d, p, x, y); }
    @Override public void hotbarSelection(Draw d, Palette p, int x, int y) { this.base.hotbarSelection(d, p, x, y); }
    @Override public boolean gameWidgets() { return this.base.gameWidgets(); }
    @Override public void checkbox(Draw d, Palette p, int x, int y, int side, boolean on, boolean lit, boolean active) { this.base.checkbox(d, p, x, y, side, on, lit, active); }
    @Override public void sliderBar(Draw d, Palette p, int x, int y, int w, int h, boolean lit, boolean active) { this.base.sliderBar(d, p, x, y, w, h, lit, active); }
    @Override public void sliderHandle(Draw d, Palette p, int x, int y, int h, boolean lit) { this.base.sliderHandle(d, p, x, y, h, lit); }
    @Override public void tab(Draw d, Palette p, int x, int y, int w, int h, boolean selected, boolean lit) { this.base.tab(d, p, x, y, w, h, selected, lit); }
    @Override public int tabInset(int h, boolean selected) { return this.base.tabInset(h, selected); }
    @Override public void tabRule(Draw d, Palette p, int x, int y, int w, int h) { this.base.tabRule(d, p, x, y, w, h); }
    @Override public int tabText(Palette p, boolean selected, boolean active) { return this.base.tabText(p, selected, active); }
    @Override public int scrollerGap() { return this.base.scrollerGap(); }
    @Override public void scroller(Draw d, Palette p, int x, int y, int h, int thumbY, int thumbH, boolean lit) { this.base.scroller(d, p, x, y, h, thumbY, thumbH, lit); }
    @Override public void plate(Draw d, Palette p, int x, int y, int w, int h, int fill, int edge) { this.base.plate(d, p, x, y, w, h, fill, edge); }
    @Override public void ringAround(Draw d, Palette p, int x, int y, int w, int h, int outset, int color) { this.base.ringAround(d, p, x, y, w, h, outset, color); }
    @Override public int buttonCorner() { return this.base.buttonCorner(); }
    @Override public void panelEdge(Draw d, Palette p, int x, int y, int w, int h, int thickness, int color) { this.base.panelEdge(d, p, x, y, w, h, thickness, color); }
    @Override public void plateEdge(Draw d, Palette p, int x, int y, int w, int h, int thickness, int color) { this.base.plateEdge(d, p, x, y, w, h, thickness, color); }
    @Override public int plateCorner(int w, int h) { return this.base.plateCorner(w, h); }
    @Override public void dot(Draw d, Palette p, int x, int y, int side, int fill, int edge) { this.base.dot(d, p, x, y, side, fill, edge); }
    @Override public void row(Draw d, Palette p, int x, int y, int w, int h, int face) { this.base.row(d, p, x, y, w, h, face); }
    @Override public void checkbox(Draw d, Palette p, int x, int y, int side, CheckboxComponent.State state, boolean lit, boolean active) { this.base.checkbox(d, p, x, y, side, state, lit, active); }
    @Override public void radio(Draw d, Palette p, int x, int y, int side, boolean on, boolean lit, boolean active) { this.base.radio(d, p, x, y, side, on, lit, active); }
}
