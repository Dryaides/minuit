package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.Themes;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.dryaides.minuit.FrameStyle;
import dev.dryaides.minuit.FrameStyles;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeFamily;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The theme files under {@code assets/<namespace>/minuit/}: the shared chromas,
 * {@code chromas/<chroma>.json}, and one folder per family, {@code themes/<family>/family.json}.
 * A family folder may still hold chroma files of its own, the layout before shared chromas. See
 * THEMES.md for the format. A file that does not parse is skipped with one log line naming it and
 * the field; it never stops the others.
 */
public final class ThemeFiles {

    /** The folder under a namespace's assets, as the game's resource manager lists it. */
    public static final String ROOT = "minuit";

    private static final String THEMES = "themes";
    private static final String CHROMAS = "chromas";
    private static final String SERIES = "series";
    private static final Map<Palette, int[]> SERIES_OF = new java.util.concurrent.ConcurrentHashMap<>();
    private static final String PALETTES = "palettes";
    private static final String ACCENTS = "accents";
    private static volatile Map<String, int[]> seriesPalettes = Map.of();
    private static final String FAMILY_FILE = "family.json";
    private static final int DEFAULT_ORDER = 100;
    /** Palette's roles, read on first use, so that series() runs where records have no reflection (the site's TeaVM build). */
    private static final class Roles {
        static final RecordComponent[] ALL = Palette.class.getRecordComponents();
    }

    private ThemeFiles() {
    }

    /**
     * One file found under {@link #ROOT}.
     *
     * @param source    where it came from, for log lines, e.g. {@code minuit:minuit/chromas/teal.json}
     * @param namespace the namespace it sits in: the owner of a private family
     * @param path      its path under {@link #ROOT}: {@code chromas/teal.json}, {@code themes/slate/family.json}
     */
    public record Entry(String source, String namespace, String path, String text) {
    }

    /** A file that is skipped, with the field at fault. */
    public static final class BadFile extends Exception {
        public BadFile(String field, String message) {
            super(field.isEmpty() ? message : field + ": " + message);
        }
    }

    /**
     * What {@code family.json} says.
     *
     * @param style       the drawing style
     * @param order       where the family sits on the theme page
     * @param privateOnly true when only the theme page of the namespace's mod offers it
     * @param chromas     the shared chromas it recommends, in order
     * @param override    the roles it draws its own way in every chroma, by variant
     */
    public record FamilyFile(FrameStyle style, int order, boolean privateOnly, List<String> chromas,
                             Map<ThemeChoice.Variant, List<Override>> override) {
    }

    /** One role of {@link Palette}, by index, that a family replaces, or whose alpha alone it sets. */
    public record Override(int role, boolean alphaOnly, int value) {
    }

    /** One chroma file: its place among the chromas and its halves, one of them maybe null. */
    public record ChromaFile(int order, Palette light, Palette dark) {
    }

    public static FamilyFile family(String text) throws BadFile {
        JsonObject root = object(text);
        String name = string(root, "style");
        FrameStyle style = FrameStyles.byName(name);
        if (style == null) {
            throw new BadFile("style", "no drawing style named \"" + name + "\", known: " + FrameStyles.names());
        }
        boolean privateOnly = false;
        JsonElement flag = root.get("private");
        if (flag != null) {
            if (!flag.isJsonPrimitive() || !flag.getAsJsonPrimitive().isBoolean()) {
                throw new BadFile("private", "not true or false");
            }
            privateOnly = flag.getAsBoolean();
        }
        List<String> chromas = new ArrayList<>();
        JsonElement list = root.get("chromas");
        if (list != null) {
            if (!list.isJsonArray()) {
                throw new BadFile("chromas", "not a list of chroma ids");
            }
            for (JsonElement id : list.getAsJsonArray()) {
                if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) {
                    throw new BadFile("chromas", id + " is not a chroma id");
                }
                chromas.add(id.getAsString().trim().toLowerCase(Locale.ROOT));
            }
        }
        return new FamilyFile(style, order(root), privateOnly, chromas, override(root.get("override")));
    }

    /** {@code "override": {"light": {"surface": "#9E", "trim": "#FFD9B26A"}}}: an alpha alone, or a colour. */
    private static Map<ThemeChoice.Variant, List<Override>> override(JsonElement element) throws BadFile {
        Map<ThemeChoice.Variant, List<Override>> out = new EnumMap<>(ThemeChoice.Variant.class);
        if (element == null) {
            return out;
        }
        if (!element.isJsonObject()) {
            throw new BadFile("override", "not an object");
        }
        Map<String, Integer> index = roleIndex();
        for (Map.Entry<String, JsonElement> half : element.getAsJsonObject().entrySet()) {
            String where = "override." + half.getKey();
            ThemeChoice.Variant variant = switch (half.getKey()) {
                case "light" -> ThemeChoice.Variant.LIGHT;
                case "dark" -> ThemeChoice.Variant.DARK;
                default -> throw new BadFile(where, "unknown variant, use light or dark");
            };
            if (!half.getValue().isJsonObject()) {
                throw new BadFile(where, "not an object");
            }
            List<Override> roles = new ArrayList<>();
            for (Map.Entry<String, JsonElement> role : half.getValue().getAsJsonObject().entrySet()) {
                String field = where + "." + role.getKey();
                Integer at = index.get(role.getKey());
                if (at == null) {
                    throw new BadFile(field, "unknown role");
                }
                JsonElement value = role.getValue();
                String text = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
                if (text.length() == 3 && hex(text)) {
                    roles.add(new Override(at, true, Integer.parseInt(text.substring(1), 16)));
                } else {
                    roles.add(new Override(at, false, colour(value, field)));
                }
            }
            out.put(variant, roles);
        }
        return out;
    }

    public static ChromaFile chroma(String text) throws BadFile {
        JsonObject root = object(text);
        JsonElement variants = root.get("variants");
        if (variants == null || !variants.isJsonObject()) {
            throw new BadFile("variants", "missing, or not an object");
        }
        JsonObject halves = variants.getAsJsonObject();
        for (String key : halves.keySet()) {
            if (!key.equals("light") && !key.equals("dark")) {
                throw new BadFile("variants." + key, "unknown variant, use light or dark");
            }
        }
        Palette light = halves.has("light") ? palette(halves.get("light"), "variants.light") : null;
        Palette dark = halves.has("dark") ? palette(halves.get("dark"), "variants.dark") : null;
        if (light == null && dark == null) {
            throw new BadFile("variants", "needs light, dark or both");
        }
        return new ChromaFile(order(root), light, dark);
    }

    /** The 30 roles of {@link Palette}, every one required, by their record names. */
    public static Palette palette(JsonElement element, String where) throws BadFile {
        if (!element.isJsonObject()) {
            throw new BadFile(where, "not an object");
        }
        JsonObject o = element.getAsJsonObject();
        Map<String, Integer> index = roleIndex();
        for (String key : o.keySet()) {
            if (!index.containsKey(key) && !SERIES.equals(key)) {
                throw new BadFile(where + "." + key, "unknown role");
            }
        }
        int[] colours = new int[Roles.ALL.length];
        for (int i = 0; i < Roles.ALL.length; i++) {
            String field = where + "." + Roles.ALL[i].getName();
            JsonElement value = o.get(Roles.ALL[i].getName());
            if (value == null) {
                throw new BadFile(field, "missing");
            }
            colours[i] = colour(value, field);
        }
        Palette palette = build(colours);
        JsonElement series = o.get(SERIES);
        if (series != null) {
            if (!series.isJsonArray() || series.getAsJsonArray().isEmpty()) {
                throw new BadFile(where + "." + SERIES, "not a list of colours");
            }
            int[] list = new int[series.getAsJsonArray().size()];
            for (int i = 0; i < list.length; i++) {
                list[i] = colour(series.getAsJsonArray().get(i), where + "." + SERIES + "[" + i + "]");
            }
            SERIES_OF.put(palette, list);
        }
        return palette;
    }

    /** One accent file, {@code {"order": 10, "light": "#FF2563EB", "dark": "#FF3B82F6"}}; order is optional. */
    private static Accent accent(String id, JsonObject root) throws BadFile {
        for (String key : root.keySet()) {
            if (!key.equals("order") && !key.equals("light") && !key.equals("dark")) {
                throw new BadFile(key, "unknown field, use order, light and dark");
            }
        }
        for (String key : new String[] {"light", "dark"}) {
            if (!root.has(key)) {
                throw new BadFile(key, "missing");
            }
        }
        return new Accent(id, colour(root.get("light"), "light") | 0xFF000000,
                colour(root.get("dark"), "dark") | 0xFF000000);
    }

    /** The kit's own accents, read from its jar, in {@link Accent#BUILT_IN} order. */
    public static List<Accent> builtInAccents() {
        List<Accent> out = new ArrayList<>();
        for (String id : Accent.BUILT_IN) {
            String path = "/assets/minuit/" + ROOT + "/" + ACCENTS + "/" + id + ".json";
            try (InputStream in = ThemeFiles.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("minuit built-in theme file missing: " + path);
                }
                out.add(accent(id, object(new String(in.readAllBytes(), StandardCharsets.UTF_8))));
            } catch (IOException | BadFile e) {
                throw new IllegalStateException("minuit built-in theme file " + path + ": " + e.getMessage(), e);
            }
        }
        return out;
    }

    /** A series palette file's colours by its {@code <namespace>:<id>}; null when none was loaded. */
    public static int[] seriesPalette(String id) {
        return seriesPalettes.get(id);
    }

    /** One series palette file, {@code {"colors": ["#RRGGBB", ...]}}. */
    public static int[] seriesPaletteFile(String text) throws BadFile {
        JsonObject root;
        try {
            root = JsonParser.parseString(text).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new BadFile("file", "not a JSON object");
        }
        JsonElement colors = root.get("colors");
        if (colors == null || !colors.isJsonArray() || colors.getAsJsonArray().isEmpty()) {
            throw new BadFile("colors", "missing, or not a list of colours");
        }
        int[] out = new int[colors.getAsJsonArray().size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = colour(colors.getAsJsonArray().get(i), "colors[" + i + "]") | 0xFF000000;
        }
        return out;
    }

    /** The chart series colours a chroma file lists for this palette, in order; null when it lists none. */
    public static int[] series(Palette palette) {
        return SERIES_OF.get(palette);
    }

    /** {@code palette} with one variant's overrides of a family applied; null stays null. */
    public static Palette apply(Palette palette, List<Override> overrides) {
        if (palette == null || overrides == null || overrides.isEmpty()) {
            return palette;
        }
        int[] colours = new int[Roles.ALL.length];
        try {
            for (int i = 0; i < Roles.ALL.length; i++) {
                colours[i] = (int) Roles.ALL[i].getAccessor().invoke(palette);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
        for (Override o : overrides) {
            colours[o.role()] = o.alphaOnly() ? o.value() << 24 | colours[o.role()] & 0xFFFFFF : o.value();
        }
        Palette applied = build(colours);
        int[] series = SERIES_OF.get(palette);
        if (series != null) {
            SERIES_OF.put(applied, series);
        }
        return applied;
    }

    /** {@code #RRGGBB} (opaque) or {@code #AARRGGBB}. */
    static int colour(JsonElement value, String field) throws BadFile {
        String text = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : "";
        if (!(text.length() == 7 || text.length() == 9) || !hex(text)) {
            throw new BadFile(field, "\"" + value + "\" is not a colour, use #RRGGBB or #AARRGGBB");
        }
        long rgb = Long.parseLong(text.substring(1), 16);
        return (int) (text.length() == 7 ? 0xFF000000L | rgb : rgb);
    }

    private static boolean hex(String text) {
        return text.startsWith("#") && text.substring(1).chars().allMatch(c -> Character.digit(c, 16) >= 0);
    }

    private static Map<String, Integer> roleIndex() {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < Roles.ALL.length; i++) {
            index.put(Roles.ALL[i].getName(), i);
        }
        return index;
    }

    private static Palette build(int[] colours) {
        Object[] args = Arrays.stream(colours).boxed().toArray();
        try {
            Class<?>[] types = new Class<?>[Roles.ALL.length];
            Arrays.fill(types, int.class);
            return Palette.class.getDeclaredConstructor(types).newInstance(args);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A chroma file as read, before a family draws it. */
    private record Found(String id, String nameKey, ChromaFile file) {
    }

    /**
     * The families the entries make, ordered as the theme page lists them. A later entry at the
     * same path replaces an earlier one; a chroma file in a family folder joins that family from
     * any namespace. Every skipped file or family gets one line on {@code log}.
     */
    public static List<ThemeFamily> assemble(List<Entry> entries, Consumer<String> log) {
        Map<String, FamilyFile> families = new LinkedHashMap<>();
        Map<String, String> owners = new LinkedHashMap<>();
        Map<String, Found> shared = new LinkedHashMap<>();
        Map<String, Map<String, Found>> local = new LinkedHashMap<>();
        Map<String, int[]> palettes = new LinkedHashMap<>();
        Map<String, Accent> accents = new LinkedHashMap<>();
        Map<String, Integer> accentOrder = new LinkedHashMap<>();
        for (Entry entry : entries) {
            String[] parts = entry.path().toLowerCase(Locale.ROOT).split("/");
            try {
                if (parts.length == 2 && parts[0].equals(CHROMAS) && parts[1].endsWith(".json")) {
                    String id = stem(parts[1]);
                    shared.put(id, new Found(id, "minuit.chroma." + id, chroma(entry.text())));
                } else if (parts.length == 2 && parts[0].equals(ACCENTS) && parts[1].endsWith(".json")) {
                    String id = stem(parts[1]);
                    JsonObject root = object(entry.text());
                    accents.put(id, accent(id, root));
                    accentOrder.put(id, order(root));
                } else if (parts.length == 2 && parts[0].equals(PALETTES) && parts[1].endsWith(".json")) {
                    palettes.put(entry.namespace() + ":" + stem(parts[1]), seriesPaletteFile(entry.text()));
                } else if (parts.length == 3 && parts[0].equals(THEMES) && parts[2].equals(FAMILY_FILE)) {
                    families.put(parts[1], family(entry.text()));
                    owners.put(parts[1], entry.namespace());
                } else if (parts.length == 3 && parts[0].equals(THEMES) && parts[2].endsWith(".json")) {
                    String id = stem(parts[2]);
                    local.computeIfAbsent(parts[1], k -> new LinkedHashMap<>())
                            .put(id, new Found(id, "minuit.chroma." + parts[1] + "." + id, chroma(entry.text())));
                } else {
                    log.accept("minuit theme file " + entry.source()
                            + " skipped: not at chromas/<chroma>.json, accents/<id>.json, palettes/<id>.json or themes/<family>/<file>.json");
                }
            } catch (BadFile e) {
                log.accept("minuit theme file " + entry.source() + " skipped: " + e.getMessage());
            }
        }
        seriesPalettes = Map.copyOf(palettes);
        if (!accents.isEmpty()) {
            Accent.load(accents.values().stream()
                    .sorted(Comparator.comparingInt((Accent a) -> accentOrder.get(a.id())).thenComparing(Accent::id))
                    .toList());
        }
        Comparator<Found> byOrder = Comparator.<Found>comparingInt(f -> f.file().order()).thenComparing(Found::id);
        List<Found> sharedOrdered = shared.values().stream().sorted(byOrder).toList();
        record Ordered(int order, ThemeFamily family) {
        }
        List<Ordered> out = new ArrayList<>();
        families.forEach((id, file) -> {
            List<Found> recommended = new ArrayList<>();
            for (String chromaId : file.chromas()) {
                Found found = shared.get(chromaId);
                if (found == null) {
                    log.accept("minuit theme family " + id + ": recommended chroma " + chromaId + " not found");
                } else if (!recommended.contains(found)) {
                    recommended.add(found);
                }
            }
            local.getOrDefault(id, Map.of()).values().stream().sorted(byOrder).forEach(recommended::add);
            List<Found> others = sharedOrdered.stream()
                    .filter(f -> recommended.stream().noneMatch(r -> r.id().equals(f.id())))
                    .toList();
            if (recommended.isEmpty() && others.isEmpty()) {
                log.accept("minuit theme family " + id + " skipped: no chroma");
                return;
            }
            String owner = file.privateOnly() ? owners.get(id) : null;
            out.add(new Ordered(file.order(), new ThemeFamily(id, owner, file.style(),
                    dress(recommended, file), dress(others, file))));
        });
        local.keySet().stream().filter(id -> !families.containsKey(id)).forEach(id ->
                log.accept("minuit theme family " + id + " skipped: chroma files but no family.json"));
        return out.stream()
                .sorted(Comparator.comparingInt(Ordered::order).thenComparing(o -> o.family().id()))
                .map(Ordered::family)
                .toList();
    }

    /** The chromas as {@code family} draws them, its overrides applied. */
    private static List<ThemeFamily.Chroma> dress(List<Found> chromas, FamilyFile family) {
        List<Override> light = family.override().get(ThemeChoice.Variant.LIGHT);
        List<Override> dark = family.override().get(ThemeChoice.Variant.DARK);
        return chromas.stream()
                .map(f -> new ThemeFamily.Chroma(f.id(), f.nameKey(),
                        apply(f.file().light(), light), apply(f.file().dark(), dark)))
                .toList();
    }

    private static String stem(String file) {
        return file.substring(0, file.length() - ".json".length());
    }

    /**
     * The theme files of a resource reload, in any order, to {@link Themes#load}: the {@code minuit}
     * namespace first, then by id, so another mod's or a pack's file at the same path wins. Returns
     * the count line for the log.
     */
    public static String reload(List<Entry> found, Consumer<String> warn) {
        List<Entry> entries = found.stream()
                .sorted(Comparator.comparing((Entry e) -> !e.namespace().equals("minuit"))
                        .thenComparing(e -> e.namespace() + ":" + ROOT + "/" + e.path()))
                .toList();
        Themes.load(assemble(entries, warn));
        return "minuit: " + Themes.families(null).size() + " theme families from " + entries.size() + " files";
    }

    /** The entries of a namespace's {@code minuit} assets folder on disk, for a bench or a test without the game. */
    public static List<Entry> fromDirectory(Path root, String namespace) throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root, 3)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                String rel = root.relativize(file).toString().replace('\\', '/');
                entries.add(new Entry(namespace + ":" + ROOT + "/" + rel, namespace, rel,
                        Files.readString(file, StandardCharsets.UTF_8)));
            }
        }
        return entries;
    }

    /** A half of one of the kit's own shared chroma files, read from its jar, for the presets. */
    public static Palette builtIn(String chroma, ThemeChoice.Variant variant) {
        String path = "/assets/minuit/" + ROOT + "/" + CHROMAS + "/" + chroma + ".json";
        try (InputStream in = ThemeFiles.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("minuit built-in theme file missing: " + path);
            }
            ChromaFile file = chroma(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            Palette half = variant == ThemeChoice.Variant.DARK
                    || (variant == ThemeChoice.Variant.AUTO && file.dark() != null) ? file.dark() : file.light();
            if (half == null) {
                throw new IllegalStateException("minuit built-in theme file " + path + " has no " + variant.id());
            }
            return half;
        } catch (IOException | BadFile e) {
            throw new IllegalStateException("minuit built-in theme file " + path + ": " + e.getMessage(), e);
        }
    }

    private static JsonObject object(String text) throws BadFile {
        try {
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                throw new BadFile("", "not a JSON object");
            }
            return root.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new BadFile("", "not valid JSON (" + e.getMessage() + ")");
        }
    }

    private static String string(JsonObject o, String key) throws BadFile {
        JsonElement value = o.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new BadFile(key, "missing, or not a string");
        }
        return value.getAsString();
    }

    private static int order(JsonObject o) throws BadFile {
        JsonElement value = o.get("order");
        if (value == null) {
            return DEFAULT_ORDER;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new BadFile("order", "not a number");
        }
        return value.getAsInt();
    }
}
