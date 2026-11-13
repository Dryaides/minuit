package dev.dryaides.minuit.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.dryaides.minuit.MotionStyle;
import dev.dryaides.minuit.ScreenBackground;
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
import java.util.Set;

/**
 * Reads and writes {@code config/minuit-client.json}, one file shared by every mod that draws with
 * the kit. Not API: the file's shape may grow within 1.x, {@link ThemeChoices} stays.
 *
 * <pre>{"global": {"family": "slate", "chroma": "teal", "variant": "auto"},
 *  "mods": {"goodboy": {"family": "leather", "chroma": "paper", "variant": "light"}},
 *  "ownLook": ["sesame"],
 *  "reducedMotion": false, "motion": "bouncy",
 *  "containerTab": {"shown": true, "offsetX": 0, "offsetY": 0},
 *  "themedItemTips": false}</pre>
 *
 * <p>{@code reducedMotion} sets {@link dev.dryaides.minuit.BaseComponent#reducedMotion} for every
 * animated widget. {@code motion} (none, subtle, bouncy) is the player's {@link MotionStyle} over every
 * mod's; absent, each mod's own. {@code containerTab} places the theme tab on the game's container
 * screens ({@link ContainerTab}): hidden, or moved by an offset when another mod's buttons sit there.
 * {@code themedItemTips} draws every tooltip of the game in minuit's theme ({@link dev.dryaides.minuit.ItemTipLook});
 * absent, off.
 *
 * <p>The shape before per-mod choices, {@code {"theme": {...}}}, reads as the global choice; its
 * family {@code own} meant no choice. A family merged into another ({@code wiki}) reads as that family.
 */
public final class ClientConfigFile {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String OLD_OWN = "own";
    /**
     * Families merged into another, by saved id: the family it became, the chroma that keeps its colours when the
     * file names none, the variant it forces (null keeps the saved one) and the chromas that left with it. Wiki
     * joined Bedrock on 2026-11-10 and lives on as Bedrock's wiki chroma. Glass became a mode on 2026-11-11: a glass
     * choice reads as Vanilla in glass, its own chromas clear and green gone to the family's first.
     */
    private static final Map<String, Merged> MERGED = Map.of(
            "wiki", new Merged("bedrock", "wiki", null, Set.of()),
            "glass", new Merged("vanilla", "", ThemeChoice.Variant.GLASS, Set.of("clear", "green")));

    private record Merged(String family, String chroma, ThemeChoice.Variant variant, Set<String> gone) {
    }

    private ClientConfigFile() {
    }

    /** The theme tab on the game's container screens: shown or not, and moved by an offset in GUI pixels. */
    public record TabPlace(boolean shown, int offsetX, int offsetY) {
        public static final TabPlace DEFAULT = new TabPlace(true, 0, 0);
    }

    /**
     * What the file holds: the theme choices, the reduced-motion flag, the motion style (null for none), the container
     * tab, the themed game tooltips and the screen background ({@link ScreenBackground} id, null for the game's own).
     */
    public record Settings(ThemeChoices choices, boolean reducedMotion, MotionStyle motion, TabPlace containerTab,
                           boolean themedItemTips, String background) {
        public static final Settings NONE = new Settings(ThemeChoices.NONE, false, null, TabPlace.DEFAULT, false, null);

        public Settings(ThemeChoices choices, boolean reducedMotion, MotionStyle motion, TabPlace containerTab,
                        boolean themedItemTips) {
            this(choices, reducedMotion, motion, containerTab, themedItemTips, null);
        }

        public Settings(ThemeChoices choices, boolean reducedMotion) {
            this(choices, reducedMotion, null, TabPlace.DEFAULT, false);
        }

        public Settings(ThemeChoices choices, boolean reducedMotion, MotionStyle motion) {
            this(choices, reducedMotion, motion, TabPlace.DEFAULT, false);
        }

        public Settings withChoices(ThemeChoices picked) {
            return new Settings(picked, this.reducedMotion, this.motion, this.containerTab, this.themedItemTips, this.background);
        }

        public Settings withReducedMotion(boolean value) {
            return new Settings(this.choices, value, this.motion, this.containerTab, this.themedItemTips, this.background);
        }

        public Settings withMotion(MotionStyle value) {
            return new Settings(this.choices, this.reducedMotion, value, this.containerTab, this.themedItemTips, this.background);
        }

        public Settings withThemedItemTips(boolean value) {
            return new Settings(this.choices, this.reducedMotion, this.motion, this.containerTab, value, this.background);
        }

        public Settings withBackground(String id) {
            return new Settings(this.choices, this.reducedMotion, this.motion, this.containerTab, this.themedItemTips,
                    id == null || id.equals(ScreenBackground.NONE) ? null : id);
        }
    }

    /** The settings in {@code text}; a missing or broken part reads as no choice and full motion. */
    public static Settings parse(String text) {
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            boolean reduced = flag(root, "reducedMotion");
            boolean itemTips = flag(root, "themedItemTips");
            MotionStyle motion = MotionStyle.parse(string(root, "motion"));
            TabPlace tab = tabPlace(root.get("containerTab"));
            String background = string(root, "background");
            if (!root.has("global") && !root.has("mods")) {
                ThemeChoice old = choice(root.get("theme"));
                return new Settings(new ThemeChoices(old == null || old.family().equals(OLD_OWN) ? null : old, Map.of()),
                        reduced, motion, tab, itemTips, background);
            }
            Map<String, ThemeChoice> mods = new LinkedHashMap<>();
            if (root.get("mods") instanceof JsonObject byMod) {
                byMod.entrySet().forEach(e -> mods.put(e.getKey(), choice(e.getValue())));
            }
            Set<String> ownLook = new java.util.TreeSet<>();
            if (root.get("ownLook") instanceof com.google.gson.JsonArray marks) {
                marks.forEach(m -> {
                    if (m.isJsonPrimitive() && m.getAsJsonPrimitive().isString()) {
                        ownLook.add(m.getAsString());
                    }
                });
            }
            return new Settings(new ThemeChoices(choice(root.get("global")), mods, ownLook), reduced, motion, tab,
                    itemTips, background);
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
        if (!choices.ownLook().isEmpty()) {
            com.google.gson.JsonArray marks = new com.google.gson.JsonArray();
            choices.ownLook().forEach(marks::add);
            root.add("ownLook", marks);
        }
        root.addProperty("reducedMotion", settings.reducedMotion());
        if (settings.motion() != null) {
            root.addProperty("motion", settings.motion().name().toLowerCase(java.util.Locale.ROOT));
        }
        JsonObject tab = new JsonObject();
        tab.addProperty("shown", settings.containerTab().shown());
        tab.addProperty("offsetX", settings.containerTab().offsetX());
        tab.addProperty("offsetY", settings.containerTab().offsetY());
        root.add("containerTab", tab);
        root.addProperty("themedItemTips", settings.themedItemTips());
        if (settings.background() != null) {
            root.addProperty("background", settings.background());
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
        String chroma = string(o, "chroma");
        ThemeChoice.Variant variant = ThemeChoice.Variant.parse(string(o, "variant"));
        Merged merged = MERGED.get(family);
        if (merged != null) {
            family = merged.family();
            chroma = chroma == null || chroma.isBlank() || merged.gone().contains(chroma) ? merged.chroma() : chroma;
            variant = merged.variant() == null ? variant : merged.variant();
        }
        return new ThemeChoice(family, chroma, variant, accent(string(o, "accent")));
    }

    /** The container tab's place; a missing or broken part keeps the default. */
    private static boolean flag(JsonObject o, String key) {
        JsonElement flag = o.get(key);
        return flag != null && flag.isJsonPrimitive() && flag.getAsJsonPrimitive().isBoolean() && flag.getAsBoolean();
    }

    private static TabPlace tabPlace(JsonElement element) {
        if (!(element instanceof JsonObject o)) {
            return TabPlace.DEFAULT;
        }
        JsonElement shown = o.get("shown");
        return new TabPlace(!(shown != null && shown.isJsonPrimitive() && shown.getAsJsonPrimitive().isBoolean())
                || shown.getAsBoolean(), integer(o, "offsetX"), integer(o, "offsetY"));
    }

    private static int integer(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : 0;
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
