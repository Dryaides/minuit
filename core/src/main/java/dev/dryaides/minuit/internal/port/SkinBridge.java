package dev.dryaides.minuit.internal.port;

import dev.dryaides.minuit.AsyncImage;

import java.util.UUID;

/** Player skins as {@link AsyncImage}s, for {@code client.PlayerSkins}. Not API. */
public interface SkinBridge {

    /** No game: the image stays a placeholder. */
    SkinBridge NONE = (target, id, name) -> {
    };

    /**
     * Points {@code target} at the skin of {@code id}; {@code name}, when known, lets the skin service
     * find a player who is not online here.
     */
    void fill(AsyncImage target, UUID id, String name);
}
