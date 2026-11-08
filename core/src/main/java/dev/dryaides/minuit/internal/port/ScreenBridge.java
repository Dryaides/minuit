package dev.dryaides.minuit.internal.port;

/** The game's screen manager, for the screens' logic in core. Not API. */
public interface ScreenBridge {

    ScreenBridge NONE = new ScreenBridge() {
    };

    /** Opens the theme page over {@code parent}, a game screen, which it goes back to. */
    default void openThemePage(Object parent, String themeId) {
    }

    default void onGameThread(Runnable task) {
        task.run();
    }

    /** Closes the current screen. */
    default void close() {
    }

    default boolean singleplayer() {
        return false;
    }

    default int guiWidth() {
        return 0;
    }

    default int guiHeight() {
        return 0;
    }

    /** The game's key event as a {@link KeyStroke}. */
    default KeyStroke stroke(Object gameKeyEvent) {
        return (KeyStroke) gameKeyEvent;
    }
}
