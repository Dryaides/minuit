package dev.dryaides.minuit.form;

/** The role of a button in a data-driven screen; {@link #wire()} is its role byte on the wire. */
public enum Role {
    PRIMARY(0),
    SECONDARY(1),
    DESTRUCTIVE(2),
    HERO(3);

    private final int wire;

    Role(int wire) {
        this.wire = wire;
    }

    public int wire() {
        return wire;
    }
}
