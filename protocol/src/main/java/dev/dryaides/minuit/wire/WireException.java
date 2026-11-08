package dev.dryaides.minuit.wire;

/** A message that breaks the wire format or one of its caps; the reader drops the whole message. */
public final class WireException extends RuntimeException {

    public WireException(String message) {
        super(message);
    }
}
