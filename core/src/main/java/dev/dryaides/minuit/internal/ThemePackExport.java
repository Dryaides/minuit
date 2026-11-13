package dev.dryaides.minuit.internal;

import com.google.gson.JsonObject;
import dev.dryaides.minuit.FrameStyle;
import dev.dryaides.minuit.FrameStyles;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeFamily;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A player's theme written as a resource pack, to share: one family named after the export, drawn in the picked
 * family's style, whose only chroma is the picked chroma with the picked accent laid in. A light or dark pick ships
 * that half alone, so {@code auto} lands on it; glass and auto ship both halves (glass derives from the dark one).
 * {@link ThemeFiles} reads the pack back as any pack theme. Not API.
 */
public final class ThemePackExport {

    /** The namespace the exported files sit under, apart from the kit's own {@code minuit}. */
    public static final String NAMESPACE = "minuit_export";
    /**
     * Pack formats the pack declares: the oldest and newest the mod's targets use (gradle/versions/*.properties),
     * so every supported game version lists it without the "made for another version" mark.
     */
    static final int MIN_FORMAT = 6;
    static final int MAX_FORMAT = 121;
    private static final Set<String> OPTIONAL = Set.of("link", "backdrop", "line", "mark");

    private ThemePackExport() {
    }

    /** {@code name} as a resource id: lower case, a to z, 0 to 9 and underscores; "theme" when nothing is left. */
    public static String id(String name) {
        String id = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
        return id.isEmpty() ? "theme" : id;
    }

    /** The name the family's style goes by in a family file, or null for a style no file can name. */
    static String styleName(FrameStyle frame) {
        for (String name : FrameStyles.names()) {
            if (FrameStyles.byName(name) == frame) {
                return name;
            }
        }
        return null;
    }

    /**
     * The pack's files by path inside it.
     *
     * @throws IllegalArgumentException when the family's style has no name a file can use
     */
    public static Map<String, String> files(String name, ThemeFamily family, ThemeFamily.Chroma chroma,
                                            ThemeChoice choice) {
        String style = styleName(family.frame());
        if (style == null) {
            throw new IllegalArgumentException("family " + family.id() + " has no named style");
        }
        String id = id(name);
        ThemeChoice.Variant v = choice.variant();
        Palette light = v == ThemeChoice.Variant.DARK && chroma.dark() != null ? null : chroma.light();
        Palette dark = v == ThemeChoice.Variant.LIGHT && chroma.light() != null ? null : chroma.dark();
        JsonObject halves = new JsonObject();
        if (light != null) {
            halves.add("light", palette(Accent.apply(light, choice.accent())));
        }
        if (dark != null) {
            halves.add("dark", palette(Accent.apply(dark, choice.accent())));
        }
        JsonObject chromaFile = new JsonObject();
        chromaFile.addProperty("order", 0);
        chromaFile.add("variants", halves);
        JsonObject familyFile = new JsonObject();
        familyFile.addProperty("style", style);
        familyFile.addProperty("order", 5);
        com.google.gson.JsonArray chromas = new com.google.gson.JsonArray();
        chromas.add(id);
        familyFile.add("chromas", chromas);
        JsonObject lang = new JsonObject();
        lang.addProperty("minuit.family." + id, name);
        lang.addProperty("minuit.chroma." + id, name);

        Map<String, String> files = new LinkedHashMap<>();
        files.put("pack.mcmeta", mcmeta(name));
        String root = "assets/" + NAMESPACE + "/" + ThemeFiles.ROOT + "/";
        files.put(root + "chromas/" + id + ".json", chromaFile.toString());
        files.put(root + "themes/" + id + "/family.json", familyFile.toString());
        files.put("assets/" + NAMESPACE + "/lang/en_us.json", lang.toString());
        return files;
    }

    private static String mcmeta(String name) {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", "minuit theme: " + name);
        pack.addProperty("pack_format", MIN_FORMAT);
        pack.addProperty("min_format", MIN_FORMAT);
        pack.addProperty("max_format", MAX_FORMAT);
        com.google.gson.JsonArray range = new com.google.gson.JsonArray();
        range.add(MIN_FORMAT);
        range.add(MAX_FORMAT);
        pack.add("supported_formats", range);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root.toString();
    }

    private static JsonObject palette(Palette p) {
        JsonObject out = new JsonObject();
        for (RecordComponent role : Palette.class.getRecordComponents()) {
            int value;
            try {
                value = (int) role.getAccessor().invoke(p);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            if (value == 0 && OPTIONAL.contains(role.getName())) {
                continue;
            }
            out.addProperty(role.getName(), String.format(Locale.ROOT, "#%08X", value));
        }
        return out;
    }

    /**
     * Writes the pack as {@code minuit-<id>.zip} in {@code resourcepacks}, replacing an older export of that name,
     * and returns its path.
     */
    public static Path write(Path resourcepacks, String name, ThemeFamily family, ThemeFamily.Chroma chroma,
                             ThemeChoice choice) throws IOException {
        Map<String, String> files = files(name, family, chroma, choice);
        Files.createDirectories(resourcepacks);
        Path zip = resourcepacks.resolve("minuit-" + id(name) + ".zip");
        try (OutputStream raw = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(raw)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                out.putNextEntry(new ZipEntry(file.getKey()));
                out.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }
}
