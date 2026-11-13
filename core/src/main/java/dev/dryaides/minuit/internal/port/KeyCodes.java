package dev.dryaides.minuit.internal.port;

import dev.dryaides.minuit.KeyPress;

/**
 * The key and mouse button codes of the running game. The variant fills it from its own
 * {@code InputConstants}, so 26.3's SDL codes come from 26.3. Not API.
 */
public record KeyCodes(int escape, int tab, int enter, int numpadEnter, int backspace, int delete, int home, int end,
                       int left, int right, int up, int down, int space, int a, int c, int x, int v,
                       int mouseLeft, int mouseRight, int mouseMiddle,
                       int leftShift, int rightShift, int leftControl, int rightControl, int leftAlt, int rightAlt) {

    /** GLFW's table (the game up to 26.2), until a platform is installed: benches and tests. */
    public static final KeyCodes GLFW = new KeyCodes(256, 258, 257, 335, 259, 261, 268, 269,
            263, 262, 265, 264, 32, 65, 67, 88, 86, 0, 1, 2, 340, 344, 341, 345, 342, 346);

    /** The modifier key {@code code} is, {@link KeyPress.Modifier#NONE} for any other key. */
    public KeyPress.Modifier modifier(int code) {
        if (code == this.leftControl || code == this.rightControl) {
            return KeyPress.Modifier.CONTROL;
        }
        if (code == this.leftShift || code == this.rightShift) {
            return KeyPress.Modifier.SHIFT;
        }
        if (code == this.leftAlt || code == this.rightAlt) {
            return KeyPress.Modifier.ALT;
        }
        if (code == GLFW_LEFT_SUPER || code == GLFW_RIGHT_SUPER) {
            return KeyPress.Modifier.META;
        }
        return KeyPress.Modifier.NONE;
    }

    /** Windows or Command keys, GLFW's numbers; the variants' record does not carry them, so 26.3's SDL table is not followed. */
    private static final int GLFW_LEFT_SUPER = 343;
    private static final int GLFW_RIGHT_SUPER = 347;
}
