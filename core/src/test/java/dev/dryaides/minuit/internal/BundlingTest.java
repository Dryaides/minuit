package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.ToastSpec;
import dev.dryaides.minuit.Words;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BundlingTest {

    /** The layout the jarJar task of ModDevGradle and ForgeGradle writes; a mod nesting another library is no parent. */
    @Test
    void jarJarParentIsTheModListingMinuitsJar(@TempDir Path dir) throws IOException {
        Path other = write(dir, "other.json", entry("com.example", "somelib", "META-INF/jarjar/somelib-2.0.jar"));
        Path bundler = write(dir, "bundler.json", entry("dev.dryaides", "minuit-forge", "META-INF/jarjar/minuit-forge-1.20.1-2.0.0.jar"));
        assertEquals(2, Bundling.jarJarParent("minuit-forge-1.20.1-2.0.0.jar", Arrays.asList(other, null, bundler)));
        assertEquals(-1, Bundling.jarJarParent("minuit-forge-1.20.1-2.0.0.jar", Arrays.asList(other, null)));
    }

    /** One toast per game start, and none before the language loads (the game hands the key back). */
    @Test
    void toastOnceAfterLanguageLoads() {
        Map<String, String> lang = Map.of("minuit.bundled", "minuit is bundled inside %s", "minuit.bundled.body", "Install minuit on its own.");
        boolean[] loaded = {false};
        Words words = (key, args) -> loaded[0] && lang.containsKey(key) ? String.format(lang.get(key), args) : key;
        List<ToastSpec> shown = new ArrayList<>();

        Bundling.found("Example Mod");
        Bundling.onScreen(words, (mod, spec) -> shown.add(spec));
        loaded[0] = true;
        Bundling.onScreen(words, (mod, spec) -> shown.add(spec));
        Bundling.onScreen(words, (mod, spec) -> shown.add(spec));

        assertEquals(1, shown.size());
        assertEquals("minuit is bundled inside Example Mod", shown.get(0).title());
    }

    private static String entry(String group, String artifact, String path) {
        return "{\n  \"jars\": [\n    {\n      \"identifier\": {\n        \"group\": \"" + group + "\",\n        \"artifact\": \"" + artifact
                + "\"\n      },\n      \"version\": {\n        \"range\": \"[2.0.0,)\",\n        \"artifactVersion\": \"2.0.0\"\n      },\n"
                + "      \"path\": \"" + path + "\",\n      \"isObfuscated\": false\n    }\n  ]\n}\n";
    }

    private static Path write(Path dir, String name, String text) throws IOException {
        return Files.write(dir.resolve(name), text.getBytes(StandardCharsets.UTF_8));
    }
}
