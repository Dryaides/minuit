package bench.ingame;

import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.Picture;
import dev.dryaides.minuit.PictureBytes;
import dev.dryaides.minuit.client.AsyncImages;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;

/**
 * The recipe for showing the player's screenshots, as a mod would write it: list the files, read and shrink
 * each one off the render thread, then let {@link AsyncImages#fill} make the texture. Each picture gets a
 * small file for thumbnails and a screen-sized one for the full-screen viewer ({@link Picture#large}).
 */
public final class Screenshots {
    private Screenshots() {
    }

    /** The newest {@code count} screenshots, loading in the background. Call {@link #release} when the screen closes. */
    public static List<Picture> newest(int count, int thumbW, int thumbH) {
        Minecraft mc = Minecraft.getInstance();
        Path dir = mc.gameDirectory.toPath().resolve("screenshots");
        int screenW = mc.getWindow().getWidth();
        int screenH = mc.getWindow().getHeight();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> all = Files.list(dir)) {
            all.filter(p -> p.toString().endsWith(".png"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .limit(count)
                    .forEach(files::add);
        } catch (IOException e) {
            return List.of();
        }
        List<Picture> out = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            AsyncImage thumb = AsyncImage.pending();
            AsyncImage full = AsyncImage.pending();
            CompletableFuture.runAsync(() -> {
                try {
                    byte[] png = Files.readAllBytes(file);
                    AsyncImages.fill(thumb, "minuit_components_bench:shots/thumb/" + name, PictureBytes.shrink(png, thumbW, thumbH));
                    AsyncImages.fill(full, "minuit_components_bench:shots/full/" + name, PictureBytes.shrink(png, screenW, screenH));
                } catch (IOException | RuntimeException e) {
                    thumb.failed();
                    full.failed();
                }
            });
            out.add(Picture.of(thumb).large(Picture.of(full)).caption(name.replace(".png", "")).alt(name));
        }
        return out;
    }

    /** Frees the textures of these pictures. */
    public static void release(List<Picture> pictures) {
        for (Picture p : pictures) {
            AsyncImages.release(p.image());
            if (p.large() != null) {
                AsyncImages.release(p.large().image());
            }
        }
    }
}
