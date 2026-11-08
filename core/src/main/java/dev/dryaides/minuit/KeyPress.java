package dev.dryaides.minuit;

/**
 * A key or mouse button as the host received it, for a widget that binds keys. The codes are GLFW's
 * numbers, opaque to the kit: only the host turns them into a name.
 *
 * @param scancode the key's scancode, 0 for a mouse button
 */
public record KeyPress(Kind kind, int code, int scancode) {

    public enum Kind { KEY, MOUSE }

    public static KeyPress key(int code, int scancode) {
        return new KeyPress(Kind.KEY, code, scancode);
    }

    /** Mouse button {@code button}, 0 left, 1 right, 2 middle, 3 and 4 the side buttons. */
    public static KeyPress mouse(int button) {
        return new KeyPress(Kind.MOUSE, button, 0);
    }
}
