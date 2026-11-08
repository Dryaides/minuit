package dev.dryaides.minuit.client;

import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.internal.port.Platform;

import java.util.Locale;

/**
 * Pictures that arrive while a screen is open, as PNG bytes: a map thumbnail from the server, a
 * screenshot. Each one becomes a {@link AsyncImage} that widgets draw as a placeholder until the upload
 * is done. Client only.
 */
public final class AsyncImages {

    private AsyncImages() {
    }

    /** A pending image for pictures still on their way; hand it to {@link #fill} when the bytes come. */
    public static AsyncImage pending() {
        return AsyncImage.pending();
    }

    /**
     * Uploads {@code png} under {@code key} and marks {@code target} ready, or failed when the bytes
     * are empty or unreadable. A key used again replaces the earlier picture.
     */
    public static void fill(AsyncImage target, String key, byte[] png) {
        if (png == null || png.length == 0) {
            target.failed();
            return;
        }
        Platform.textures().upload(target, path(key), png);
    }

    /** Frees the texture of {@code image}; it goes back to a placeholder. */
    public static void release(AsyncImage image) {
        if (image.state() == AsyncImage.State.READY && image.texture() != null
                && image.texture().startsWith("minuit:dynamic/")) {
            Platform.textures().release(image.texture());
            image.failed();
        }
    }

    /** A resource-path-safe form of {@code key}; the hash keeps two folded keys apart. */
    static String path(String key) {
        String low = key.toLowerCase(Locale.ROOT);
        String safe = low.replaceAll("[^a-z0-9_./-]", "_");
        return safe.equals(key) ? safe : safe + "_" + Integer.toHexString(key.hashCode());
    }
}
