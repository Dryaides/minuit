package dev.dryaides.minuit.internal.port;

import dev.dryaides.minuit.ToastSpec;

/** The game's toast stack, for {@code client.Toasts}. Not API. */
public interface ToastBridge {

    /** No game: nowhere to show it. */
    ToastBridge NONE = (modId, spec) -> {
    };

    /** On the client thread: adds {@code spec}, or replaces the text of the one on screen with its key. */
    void show(String modId, ToastSpec spec);
}
