package dev.dryaides.minuit.form;

/**
 * Why a screen closed; {@link #wire()} is the reason byte of a close or a closed.
 * {@link #UNSUPPORTED} never goes on the wire: the player's client draws no forms and the server
 * required them ({@link FormServer#requireForms}).
 */
public enum CloseReason {
    PLAYER, INVALID, REPLACED, SERVER, UNSUPPORTED;

    public int wire() {
        return ordinal();
    }
}
