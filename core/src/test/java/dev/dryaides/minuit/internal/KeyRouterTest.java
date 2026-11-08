package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.internal.port.KeyCodes;
import dev.dryaides.minuit.internal.port.KeyStroke;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Codes from GLFW's key table: A = 65, C = 67, Home = 268, F1 = 290. */
class KeyRouterTest {

    private static InputKey key(int code, boolean control) {
        return KeyRouter.key(new KeyStroke(code, 0, control, false), KeyCodes.GLFW);
    }

    @Test
    void namesShortcutsAndKeysOnGlfwCodes() {
        assertEquals(InputKey.COPY, key(67, true));
        assertEquals(InputKey.SELECT_ALL, key(65, true));
        assertEquals(InputKey.HOME, key(268, false));
        assertEquals(InputKey.OTHER, key(290, false));
    }
}
