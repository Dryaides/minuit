package dev.dryaides.minuit.internal;

/**
 * Where a family drawn from PNGs gets its pixels: a renderer (the game per port, the bench, the site) implements it
 * and installs it with {@code ImageFamily.install}. See THEMES.md, "A family from images".
 */
public interface ImageSource {

    /** An image as 0xAARRGGBB, row by row. */
    record Pixels(int w, int h, int[] argb) {
    }

    /** The pixels of {@code namespace:path/to/file.png}, or null when it is absent or not loaded yet. */
    Pixels read(String id);

    /**
     * Makes {@code image} drawable by {@link Draw#blit}, replacing what an earlier call with the same {@code key} made.
     *
     * @return the texture id to blit
     */
    String bake(String key, Pixels image);

    /** Frees one image {@link #bake} made, at once; the family calls it frames after the last blit of that image. */
    default void drop(String texture) {
    }

    /** A resource reload swapped the families in: frees what {@link #bake} made for the old ones. */
    default void reload() {
    }
}
