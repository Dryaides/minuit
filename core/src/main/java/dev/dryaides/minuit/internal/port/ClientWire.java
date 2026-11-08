package dev.dryaides.minuit.internal.port;

/** How the loader sends a minuit message to the server, for the forms in core. Not API. */
public interface ClientWire {

    /** No connection: the message goes nowhere. */
    ClientWire NONE = (channel, bytes) -> {
    };

    /** Sends {@code bytes} on {@code channel}, one of {@code wire.Channels}' paths. */
    void send(String channel, byte[] bytes);
}
