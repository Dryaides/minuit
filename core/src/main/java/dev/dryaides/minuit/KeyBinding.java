package dev.dryaides.minuit;

import java.util.List;

/** One key binding of the game or a mod, as a key binding field shows and changes it. */
public interface KeyBinding {

    /** The bound key in the player's language, such as "R" or "Mouse Button 4". */
    String keyName();

    boolean isDefault();

    /** Names of the other bindings on the same key. */
    List<String> conflicts();

    void bind(KeyPress key);

    void unbind();

    void reset();
}
