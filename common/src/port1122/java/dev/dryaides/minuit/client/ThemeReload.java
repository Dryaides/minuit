package dev.dryaides.minuit.client;

import dev.dryaides.minuit.internal.ThemeFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads the theme files of every mod and selected resource pack on each resource reload and hands the
 * entries to {@link ThemeFiles#reload}. 1.12.2's resource manager cannot list a folder, so the files are
 * found in the packs' own jars, zips and folders: mods first, then the selected packs in the order the game stacks
 * them, so a later entry (a higher pack) wins as it does with the game's listing. Client only.
 */
public final class ThemeReload implements IResourceManagerReloadListener {

    private static final Logger LOG = LogManager.getLogger("minuit");

    /** {@code assets/<namespace>/minuit/<path>.json}. */
    private static final Pattern FILE = Pattern.compile("assets/([a-z0-9_.-]+)/" + ThemeFiles.ROOT + "/(.+\\.json)");

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        List<ThemeFiles.Entry> entries = new ArrayList<>();
        for (File source : sources()) {
            try {
                if (source.isDirectory()) {
                    folder(source.toPath(), entries);
                } else if (source.isFile()) {
                    zip(source, entries);
                }
            } catch (IOException ex) {
                LOG.warn("minUIt theme files of {} skipped: {}", source.getName(), ex.getMessage());
            }
        }
        LOG.info(ThemeFiles.reload(entries, LOG::warn));
    }

    private static Set<File> sources() {
        Set<File> out = new LinkedHashSet<>();
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            if (mod.getSource() != null) {
                out.add(mod.getSource());
            }
        }
        ResourcePackRepository packs = Minecraft.getMinecraft().getResourcePackRepository();
        for (ResourcePackRepository.Entry pack : packs.getRepositoryEntries()) {
            out.add(new File(packs.getDirResourcepacks(), pack.getResourcePackName()));
        }
        return out;
    }

    private static void folder(Path root, List<ThemeFiles.Entry> entries) throws IOException {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(assets)) {
            walk.filter(Files::isRegularFile).sorted().forEach(files::add);
        }
        for (Path file : files) {
            String name = root.relativize(file).toString().replace(File.separatorChar, '/');
            Matcher m = FILE.matcher(name);
            if (m.matches()) {
                entries.add(new ThemeFiles.Entry(name + " (" + root.getFileName() + ")", m.group(1), m.group(2),
                        new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
            }
        }
    }

    private static void zip(File source, List<ThemeFiles.Entry> entries) throws IOException {
        try (ZipFile zip = new ZipFile(source)) {
            List<ZipEntry> found = new ArrayList<>();
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry e = all.nextElement();
                if (!e.isDirectory() && FILE.matcher(e.getName()).matches()) {
                    found.add(e);
                }
            }
            found.sort((a, b) -> a.getName().compareTo(b.getName()));
            for (ZipEntry e : found) {
                Matcher m = FILE.matcher(e.getName());
                m.matches();
                try (InputStream in = zip.getInputStream(e)) {
                    entries.add(new ThemeFiles.Entry(e.getName() + " (" + source.getName() + ")", m.group(1),
                            m.group(2), IOUtils.toString(in, StandardCharsets.UTF_8)));
                }
            }
        }
    }
}
