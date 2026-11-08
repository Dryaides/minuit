package dev.dryaides.minuit.internal.port;

/**
 * The key and mouse button codes of the running game. The variant fills it from its own
 * {@code InputConstants}, so 26.3's SDL codes come from 26.3. Not API.
 */
public record KeyCodes(int escape, int tab, int enter, int numpadEnter, int backspace, int delete, int home, int end,
                       int left, int right, int up, int down, int space, int a, int c, int x, int v,
                       int mouseLeft, int mouseRight, int mouseMiddle) {

    /** GLFW's table (the game up to 26.2), until a platform is installed: benches and tests. */
    public static final KeyCodes GLFW = new KeyCodes(256, 258, 257, 335, 259, 261, 268, 269,
            263, 262, 265, 264, 32, 65, 67, 88, 86, 0, 1, 2);
}
