package dev.dryaides.minuit.form;

/** The three form kinds; {@link #wire()} is the kind byte of an open. */
public enum Kind {
    NOTICE, MENU, CUSTOM;

    public int wire() {
        return ordinal();
    }
}
