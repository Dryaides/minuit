package dev.dryaides.minuit.form;

/** The width class of a screen; {@link #wire()} is the size byte of an open. */
public enum Size {
    NARROW, NORMAL, WIDE;

    public int wire() {
        return ordinal();
    }
}
