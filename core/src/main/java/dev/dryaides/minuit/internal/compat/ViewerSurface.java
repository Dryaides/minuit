package dev.dryaides.minuit.internal.compat;

/**
 * What a kit screen tells the recipe viewer plugins (JEI, REI), read on every frame. Not API: a container
 * screen sets it through {@code showsContainerAddons}. Later it can also hand out exclusion zones for kit
 * panels that stick out of the container box.
 */
@FunctionalInterface
public interface ViewerSurface {

    /** False keeps the viewers' overlays off the screen. */
    boolean showsAddons();
}
