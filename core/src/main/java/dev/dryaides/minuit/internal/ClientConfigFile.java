package dev.dryaides.minuit.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.dryaides.minuit.MotionStyle;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeChoices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads and writes {@code config/minuit-client.json}, one file shared by every mod that draws with
 * the kit. Not API: the file's shape may grow within 1.x, {@link ThemeChoices} stays.
 *
 * <pre>{"global": {"family": "slate", "chroma": "teal", "variant": "auto"},
 *  "mods": {"goodboy": {"family": "leather", "chroma": "paper", "variant": "light"}},
 *  "reducedMotion": false, "motion": "bouncy"}</pre>
 *
 * <p>{@code reducedMotion} sets {@link dev.dryaides.minuit.BaseComponent#reducedMotion} for every
 * animated widget. {@code motion} (none, subtle, bouncy) is the player's {@link MotionStyle} over every
 * mod's; absent, each mod's own.
 *
 * <p>The shape before per-mod choices, {@code {"theme": {...}}}, reads as the global choice; its
 * family {@code own} meant no choice.
 */
public final class ClientConfigFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OLD_OWN = "own";

    private ClientConfigFile() {
    }

    /** What the file holds: the theme choices, the reduced-motion flag and the motion style, null for none. */
    public record Settings(ThemeChoices choices, boolean reducedMotion, MotionStyle motion) {
        public static final Settings NONE = new Settings(ThemeChoices.NONE, false, null);

        public Settings(ThemeChoices choices, boolean reducedMotion) {
            this(choices, reducedMotion, null);
        }

        public Settings withChoices(ThemeChoices picked) {
            return new Settings(picked, this.reducedMotion, this.motion);
        }

        public Settings withReducedMotion(boolean value) {
            return new Settings(this.choices, value, this.motion);
        }

        public Settings withMotion(MotionStyle value) {
            return new Settings(this.choices, this.reducedMotion, value);
        }
    }

    /** The settings in {@code text}; a missing or broken part reads as no choice and full motion. */
    public static Settings parse(String text) {
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonElement flag = root.get("reducedMotion");
            boolean reduced = flag != null && flag.isJsonPrimitive()
                    && flag.getAsJsonPrimitive().isBoolean() && flag.getAsBoolean();
            MotionStyle motion = MotionStyle.parse(string(root, "motion"));
            if (!root.has("global") && !root.has("mods")) {
                ThemeChoice old = choice(root.get("theme"));
                return new Settings(new ThemeChoices(old == null || old.family().equals(OLD_OWN) ? null : old, Map.of()),
                        reduced, motion);
            }
            Map<String, ThemeChoice> mods = new LinkedHashMap<>();
            if (root.get("mods") instanceof JsonObject byMod) {
                byMod.entrySet().forEach(e -> mods.put(e.getKey(), choice(e.getValue())));
            }
            return new Settings(new ThemeChoices(choice(root.get("global")), mods), reduced, motion);
        } catch (JsonParseException | IllegalStateException e) {
            return Settings.NONE;
        }
    }

    public static String write(Settings settings) {
        ThemeChoices choices = settings.choices();
        JsonObject root = new JsonObject();
        if (choices.global() != null) {
            root.add("global", json(choices.global()));
        }
        JsonObject mods = new JsonObject();
        choices.mods().forEach((id, choice) -> mods.add(id, json(choice)));
        root.add("mods", mods);
        root.addProperty("reducedMotion", settings.reducedMotion());
        if (settings.motion() != null) {
            root.addProperty("motion", settings.motion().name().toLowerCase(java.util.Locale.ROOT));
        }
        return GSON.toJson(root) + "\n";
    }

    /** The file's settings; a missing file is created empty so players can find it. */
    public static Settings load(Path file) {
        try {
            if (Files.isRegularFile(file)) {
                return parse(Files.readString(file, StandardCharsets.UTF_8));
            }
            save(file, Settings.NONE);
        } catch (IOException ignored) {
            // An unreadable or read-only config keeps every mod's own look.
        }
        return Settings.NONE;
    }

    public static void save(Path file, Settings settings) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, write(settings), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** One choice, or null when it is missing or has no family. */
    private static ThemeChoice choice(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject o = element.getAsJsonObject();
        String family = string(o, "family");
        if (family == null || family.isBlank()) {
            return null;
        }
        return new ThemeChoice(family, string(o, "chroma"), ThemeChoice.Variant.parse(string(o, "variant")),
                accent(string(o, "accent")));
    }

    /** The saved accent id; "dryaides", a preset of the first releases, became "midnight". */
    private static String accent(String id) {
        return "dryaides".equals(id) ? "midnight" : id;
    }

    private static JsonObject json(ThemeChoice choice) {
        JsonObject o = new JsonObject();
        o.addProperty("family", choice.family());
        o.addProperty("chroma", choice.chroma());
        o.addProperty("variant", choice.variant().id());
        if (!choice.accent().isEmpty()) {
            o.addProperty("accent", choice.accent());
        }
        return o;
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }
}
