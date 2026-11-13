package dev.dryaides.minuit;

/**
 * How a {@link Bubble} looks: {@link #CLASSIC} for words said, {@link #THOUGHT} for words thought. Both
 * keep the logo's colours, the same in every mod and every theme; a themed look comes later through this
 * parameter without an API break.
 */
public final class BubbleStyle {

    /** The Polly logo's bubble: white body, blue rim, navy outline and text. */
    public static final BubbleStyle CLASSIC = new BubbleStyle("classic");

    /** A thought cloud: the same white body and navy outline with bumps along its edges, and two small circles instead of a pointed tail. */
    public static final BubbleStyle THOUGHT = new BubbleStyle("thought");

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
