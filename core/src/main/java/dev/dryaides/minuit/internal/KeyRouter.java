package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.internal.port.KeyCodes;
import dev.dryaides.minuit.internal.port.KeyStroke;

/** Game keys and mouse buttons to the kit's names, with the codes of the running version. Not API. */
public final class KeyRouter {

    private KeyRouter() {
    }

    /** Hands {@code stroke} to {@code panel}: a capturing widget takes it raw first. False lets the screen use it. */
    public static boolean press(PanelComponent panel, KeyStroke stroke, KeyCodes codes) {
        if (panel.capturing() && panel.rawKey(KeyPress.key(stroke.code(), 0))) {
            return true;
        }
        return panel.keyPressed(key(stroke, codes), stroke.shift());
    }

    /** The kit's name for a key, Ctrl shortcuts included. */
    public static InputKey key(KeyStroke stroke, KeyCodes codes) {
        int code = stroke.code();
        if (stroke.control() && !stroke.shift() && code == codes.a()) {
            return InputKey.SELECT_ALL;
        }
        if (stroke.control()) {
            if (code == codes.c()) {
                return InputKey.COPY;
            }
            if (code == codes.x()) {
                return InputKey.CUT;
            }
            if (code == codes.v()) {
                return InputKey.PASTE;
            }
        }
        if (code == codes.escape()) {
            return InputKey.ESCAPE;
        }
        if (code == codes.left()) {
            return InputKey.LEFT;
        }
        if (code == codes.right()) {
            return InputKey.RIGHT;
        }
        if (code == codes.tab()) {
            return InputKey.TAB;
        }
        if (code == codes.home()) {
            return InputKey.HOME;
        }
        if (code == codes.end()) {
            return InputKey.END;
        }
        if (code == codes.backspace()) {
            return InputKey.BACKSPACE;
        }
        if (code == codes.delete()) {
            return InputKey.DELETE;
        }
        if (code == codes.enter() || code == codes.numpadEnter()) {
            return InputKey.ENTER;
        }
        if (code == codes.up()) {
            return InputKey.UP;
        }
        if (code == codes.down()) {
            return InputKey.DOWN;
        }
        if (code == codes.space()) {
            return InputKey.SPACE;
        }
        return InputKey.OTHER;
    }

    /** The kit's button number (0 left, 1 right, 2 middle) for a game mouse button, -1 for any other. */
    public static int button(int gameButton, KeyCodes codes) {
        if (gameButton == codes.mouseLeft()) {
            return 0;
        }
        if (gameButton == codes.mouseRight()) {
            return 1;
        }
        return gameButton == codes.mouseMiddle() ? 2 : -1;
    }
}
