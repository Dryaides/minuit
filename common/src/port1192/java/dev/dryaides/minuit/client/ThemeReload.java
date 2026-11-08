package dev.dryaides.minuit.client;

import dev.dryaides.minuit.internal.ThemeFiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the theme files of every mod and resource pack on each resource reload and hands the
 * entries to {@link ThemeFiles#reload}. The loader entry points register it as a client reload listener.
 */
public final class ThemeReload implements ResourceManagerReloadListener {

    /** The listener's id, {@code minuit:themes}. */
    public static final String ID = "themes";

    private static final Logger LOG = LoggerFactory.getLogger("minuit");

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<ResourceLocation, Resource> found = manager.listResources(ThemeFiles.ROOT, id -> id.getPath().endsWith(".json"));
        List<ThemeFiles.Entry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
            String source = e.getKey() + " (" + e.getValue().sourcePackId() + ")";
            String path = e.getKey().getPath().substring(ThemeFiles.ROOT.length() + 1);
            try (InputStream in = e.getValue().open()) {
                entries.add(new ThemeFiles.Entry(source, e.getKey().getNamespace(), path,
                        new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (IOException ex) {
                LOG.warn("minuit theme file {} skipped: {}", source, ex.getMessage());
            }
        }
        LOG.info(ThemeFiles.reload(entries, LOG::warn));
    }
}
