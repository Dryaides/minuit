package dev.dryaides.minuit;

/**
 * How a {@link Bubble} looks. Only {@link #CLASSIC} for now, the Polly logo's bubble, the same in
 * every mod and every theme; a themed look comes later through this parameter without an API break.
 */
public final class BubbleStyle {

    /** The Polly logo's bubble: white body, blue rim, navy outline and text. */
    public static final BubbleStyle CLASSIC = new BubbleStyle("classic");

    private final String id;

    private BubbleStyle(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    @Override
    public String toString() {
        return this.id;
    }
}
