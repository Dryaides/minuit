package dev.dryaides.minuit;

import java.util.List;

/** One key binding of the game or a mod, as a key binding field shows and changes it. */
public interface KeyBinding {

    /** The bound key in the player's language, such as "R", "Ctrl + M" or "Mouse Button 4". */
    String keyName();

    boolean isDefault();

    /** Names of the other bindings on the same key and the full modifier mask. */
    List<String> conflicts();

    /**
     * True when {@link #bind} keeps {@link KeyPress#modifier}: NeoForge and Forge bindings. A binding that
     * returns false gets presses without a modifier, so it never shows a combo it does not hold.
     */
    default boolean supportsModifiers() {
        return false;
    }

    /**
     * True when {@link #bind} keeps every modifier of {@link KeyPress#mods}, Meta included. A binding that
     * only {@linkplain #supportsModifiers supports one} (NeoForge, Forge) gets at most one of Ctrl, Shift, Alt.
     */
    default boolean supportsMultipleModifiers() {
        return false;
    }

    void bind(KeyPress key);

    void unbind();

    void reset();
}
