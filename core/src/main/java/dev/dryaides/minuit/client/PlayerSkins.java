package dev.dryaides.minuit.client;

import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.internal.port.Platform;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The skin of a player as an {@link AsyncImage}, for {@code PlayerHeadComponent} and its list row and
 * grid card. One image per player, kept for the session: ready at once with the player's default
 * skin, then the real one. Client only.
 */
public final class PlayerSkins {

    private static final Map<UUID, AsyncImage> SKINS = new ConcurrentHashMap<>();

    private PlayerSkins() {
    }

    /** The skin of {@code id}; {@code name}, when known, lets the skin service find a player who is not online here. */
    public static AsyncImage of(UUID id, String name) {
        return SKINS.computeIfAbsent(id, key -> {
            AsyncImage image = AsyncImage.pending();
            Platform.skins().fill(image, key, name);
            return image;
        });
    }

    public static AsyncImage of(UUID id) {
        return of(id, null);
    }

    /** Looks every skin up again on next use: after a server change, a skin may have changed. */
    public static void forget() {
        SKINS.clear();
    }
}
