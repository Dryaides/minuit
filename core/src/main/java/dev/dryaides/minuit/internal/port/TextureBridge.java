package dev.dryaides.minuit.internal.port;

import dev.dryaides.minuit.AsyncImage;

/** PNG bytes to a game texture, for {@code client.AsyncImages}. Not API. */
public interface TextureBridge {

    /** No game: every upload fails, so widgets keep their placeholder. */
    TextureBridge NONE = new TextureBridge() {
        @Override
        public void upload(AsyncImage target, String path, byte[] png) {
            target.failed();
        }

        @Override
        public void release(String texture) {
        }
    };

    /** Uploads {@code png} as {@code minuit:dynamic/<path>}, then marks {@code target} ready, or failed when the bytes are not a PNG. */
    void upload(AsyncImage target, String path, byte[] png);

    void release(String texture);
}
