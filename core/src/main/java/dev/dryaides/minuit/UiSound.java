package dev.dryaides.minuit;

/**
 * The kit's sound cues. Widgets call {@link BaseComponent#sound}; the client host plugs the game's sounds
 * in, a bench leaves it silent or records the calls.
 */
public interface UiSound {

    UiSound NONE = new UiSound() {
    };

    /** The button click: a button, a toggle, a list row. */
    default void click() {
    }

    /** A soft tap: a wheel notch, a tab change. */
    default void tap() {
    }

    /** A tick whose pitch rises with {@code frac} (0 to 1): a reel card passing, a slider step. */
    default void tick(float frac) {
    }

    /** The one dry tick when a reel stops. */
    default void land() {
    }

    /** A short bright confirmation. */
    default void confirm() {
    }

    /**
     * A button press in its role: the host may give the hero, a secondary and a destructive button a
     * sound of their own. Plays {@link #click} by default, and for a null role.
     */
    @Incubating
    default void press(ButtonComponent.Role role) {
        click();
    }
}
