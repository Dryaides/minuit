package dev.dryaides.minuit.wire;

/** The three channel ids of minuit, {@code minuit:<path>}, registered as optional by every 1.x build. */
public final class Channels {

    public static final String NAMESPACE = "minuit";

    /** Both directions: the handshake. */
    public static final String HELLO = "hello";
    /** Server to client: open, patch, close. */
    public static final String SCREEN = "screen";
    /** Client to server: change, press, closed. */
    public static final String INPUT = "input";

    private Channels() {
    }
}
