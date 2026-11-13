package bench.render;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Finds "namespace:path" textures in the library's assets, then in the cached game jar. The bench's own test
 * pictures, {@code minuit_components_bench:textures/bench/<file>}, come from {@code bench/images}: the in-game
 * dev mod ships the same files under that id.
 */
final class Textures {
    private final Map<String, Optional<BufferedImage>> cache = new HashMap<>();
    private final Path assets;
    private final String mcJar;
    final Set<String> missing = new TreeSet<>();

    Textures(Path assets, String mcJar) {
        this.assets = assets;
        this.mcJar = mcJar;
    }

    /** How many blits asked for a texture: the image-family proof checks its sheet drew sprites. */
    int gets;

    BufferedImage get(String id) {
        gets++;
        BufferedImage img = cache.computeIfAbsent(id, this::read).orElse(null);
        if (img == null) {
            missing.add(id);
        }
        return img;
    }

    /** Serves {@code img} under {@code id} from now on: a baked or generated texture. */
    void put(String id, BufferedImage img) {
        cache.put(id, Optional.of(img));
        missing.remove(id);
    }

    /** Like {@link #get}, without counting a miss: for probing where a texture might be. */
    BufferedImage peek(String id) {
        return cache.computeIfAbsent(id, this::read).orElse(null);
    }

    static final String BENCH_IMAGES = "minuit_components_bench:textures/bench/";

    /** bench/images, found upwards from the compiled bench classes (build/run/<pid>/bench). */
    private static Path benchImages() {
        String classes = System.getProperty("bench.classes", "");
        for (Path p = classes.isEmpty() ? Path.of("").toAbsolutePath() : Path.of(classes).toAbsolutePath(); p != null; p = p.getParent()) {
            Path images = p.resolve("bench").resolve("images");
            if (Files.isRegularFile(images.resolve("MakeImages.java"))) {
                return images;
            }
        }
        return null;
    }

    private Optional<BufferedImage> read(String id) {
        int colon = id.indexOf(':');
        String ns = colon < 0 ? "minecraft" : id.substring(0, colon);
        String entry = ns + "/" + id.substring(colon + 1);
        try {
            if (id.startsWith(BENCH_IMAGES)) {
                Path own = benchImages();
                Path file = own == null ? null : own.resolve(id.substring(BENCH_IMAGES.length()));
                return file != null && Files.isRegularFile(file) ? Optional.ofNullable(ImageIO.read(file.toFile())) : Optional.empty();
            }
            Path file = assets.resolve(entry);
            if (Files.isRegularFile(file)) {
                return Optional.ofNullable(ImageIO.read(file.toFile()));
            }
            if (mcJar != null && !mcJar.isEmpty()) {
                try (ZipFile zip = new ZipFile(mcJar)) {
                    ZipEntry e = zip.getEntry("assets/" + entry);
                    if (e != null) {
                        try (InputStream in = zip.getInputStream(e)) {
                            return Optional.ofNullable(ImageIO.read(in));
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("bench: cannot read " + id + ": " + e);
        }
        return Optional.empty();
    }
}
