package dev.dryaides.minuit.form;

/** An image the client already has: an item, or a GUI sprite drawn at {@code w} by {@code h}. */
public sealed interface Image {

    record Item(String id) implements Image {
    }

    record Sprite(String id, int w, int h) implements Image {
    }
}
