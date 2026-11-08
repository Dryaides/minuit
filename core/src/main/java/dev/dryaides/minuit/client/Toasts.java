package dev.dryaides.minuit.client;

import dev.dryaides.minuit.ToastSpec;
import dev.dryaides.minuit.internal.port.Platform;

/**
 * Toasts in a mod's theme, stacked with the game's advancement, recipe and system toasts in the top
 * right corner. Client thread only.
 * <pre>{@code
 * Toasts.show("mymod", ToastSpec.of(words.get("mymod.saved"), name).icon(ItemView.of("minecraft:writable_book")));
 * }</pre>
 */
public final class Toasts {

    private Toasts() {
    }

    /**
     * Shows {@code toast} in the theme of {@code modId}. A toast with the {@link ToastSpec#key} of one
     * still on screen replaces its text and restarts its time instead of stacking a second.
     */
    public static void show(String modId, ToastSpec toast) {
        Platform.toasts().show(modId, toast);
    }
}
