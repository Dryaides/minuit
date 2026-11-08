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
        List<ThemeFiles.Entry> entries = new ArrayList<>();
        for (ResourceLocation id : manager.listResources(ThemeFiles.ROOT, path -> path.endsWith(".json"))) {
            String path = id.getPath().substring(ThemeFiles.ROOT.length() + 1);
            String source = id.toString();
            try (Resource resource = manager.getResource(id); InputStream in = resource.getInputStream()) {
                source = id + " (" + resource.getSourceName() + ")";
                entries.add(new ThemeFiles.Entry(source, id.getNamespace(), path,
                        new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (IOException ex) {
                LOG.warn("minuit theme file {} skipped: {}", source, ex.getMessage());
            }
        }
        LOG.info(ThemeFiles.reload(entries, LOG::warn));
    }
}
