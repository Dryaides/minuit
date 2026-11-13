package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.ToastSpec;
import dev.dryaides.minuit.Words;
import dev.dryaides.minuit.internal.port.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * minuit loaded from inside another mod's jar (Fabric {@code include}, Forge and NeoForge jarJar, 1.12.2
 * {@code ContainedDeps}). Every mod should depend on minuit as its own download, so the loader glue reports
 * the bundling mod here: one warning in the log at once, one toast on the first screen after the language
 * loads. Not API.
 */
public final class Bundling {

    static final String TOAST_KEY = "minuit.bundled";
    private static final int TOAST_MS = 15000;

    private static volatile String bundler;
    private static volatile boolean shown = true;

    private Bundling() {
    }

    /**
     * The loader found minuit nested in another mod's jar. {@code name} is the bundling mod's display name,
     * null when the loader cannot tell which mod it is.
     */
    public static void found(String name) {
        String who = name == null || name.isBlank() ? null : name;
        System.getLogger("minuit").log(System.Logger.Level.WARNING, (who == null ? "minuit is bundled inside another mod's jar"
                : "minuit is bundled inside the jar of " + who)
                + ". Install minuit as its own download in the mods folder; a bundled copy can clash with the minuit other mods expect.");
        bundler = who == null ? "" : who;
        shown = false;
    }

    /** From the screen-opening hook, client thread. */
    public static void onScreen() {
        if (!shown) {
            onScreen(Platform.words(), (modId, spec) -> Platform.toasts().show(modId, spec));
        }
    }

    /** Shows the toast once; false while the language is not loaded yet (the key comes back unchanged). */
    static boolean onScreen(Words words, BiConsumer<String, ToastSpec> toasts) {
        if (shown) {
            return false;
        }
        String who = bundler;
        String titleKey = who == null || who.isEmpty() ? TOAST_KEY + ".unknown" : TOAST_KEY;
        String title = words.get(titleKey, who);
        if (title.equals(titleKey)) {
            return false;
        }
        shown = true;
        toasts.accept("minuit", new ToastSpec(title, words.get(TOAST_KEY + ".body"), null, TOAST_MS, TOAST_KEY));
        return true;
    }

    /**
     * Forge jarJar: the index in {@code metadataFiles} of the {@code META-INF/jarjar/metadata.json} that lists
     * {@code nestedName} (minuit's own jar file name), or -1. A null entry stands for a mod with no such file.
     */
    public static int jarJarParent(String nestedName, List<Path> metadataFiles) {
        String entry = "META-INF/jarjar/" + nestedName;
        for (int i = 0; i < metadataFiles.size(); i++) {
            Path file = metadataFiles.get(i);
            if (file == null) {
                continue;
            }
            try {
                if (new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\\/", "/").contains(entry)) {
                    return i;
                }
            } catch (IOException | RuntimeException e) {
                // an unreadable metadata file names no parent
            }
        }
        return -1;
    }

    /**
     * 1.12.2: the index in {@code jars} of the mod jar whose manifest {@code ContainedDeps} lists a jar named
     * after minuit, or -1. Forge renames the extracted jar after its {@code Maven-Artifact}, so the match is
     * on the name, not the file.
     */
    public static int containedDepsParent(List<Path> jars) {
        for (int i = 0; i < jars.size(); i++) {
            Path jar = jars.get(i);
            if (jar == null || !Files.isRegularFile(jar)) {
                continue;
            }
            try (JarFile file = new JarFile(jar.toFile())) {
                Manifest manifest = file.getManifest();
                String deps = manifest == null ? null : manifest.getMainAttributes().getValue(new Attributes.Name("ContainedDeps"));
                if (deps == null) {
                    continue;
                }
                for (String dep : deps.trim().split("\\s+")) {
                    String name = dep.substring(dep.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                    if (name.startsWith("minuit") && name.endsWith(".jar")) {
                        return i;
                    }
                }
            } catch (IOException | RuntimeException e) {
                // an unreadable jar names no parent
            }
        }
        return -1;
    }

    /** 1.12.2: minuit's source is a jar outside the mods folder and its version subfolder, where Forge puts extracted deps. */
    public static boolean extractedDep(Path source, Path modsDir, String mcVersion) {
        if (source == null || !Files.isRegularFile(source)) {
            return false;
        }
        Path dir = source.toAbsolutePath().normalize().getParent();
        Path mods = modsDir.toAbsolutePath().normalize();
        return !dir.equals(mods) && !dir.equals(mods.resolve(mcVersion));
    }
}
