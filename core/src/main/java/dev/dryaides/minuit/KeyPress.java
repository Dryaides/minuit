package dev.dryaides.minuit;

/**
 * A key or mouse button as the host received it, for a widget that binds keys. The codes are GLFW's
 * numbers, opaque to the kit: only the host turns them into a name.
 *
 * @param scancode the key's scancode, 0 for a mouse button
 * @param mods     the modifiers held with it as a bit mask, see {@link Modifier#bit}; 0 from the host. A host whose
 *                 bindings hold one modifier (NeoForge, Forge) reads {@link #modifier()}
 */
public record KeyPress(Kind kind, int code, int scancode, int mods) {

    public enum Kind { KEY, MOUSE }

    /** A modifier key. CONTROL is Command on macOS for the game; META is the Windows or Command key as GLFW reports it. */
    public enum Modifier {
        NONE, CONTROL, SHIFT, ALT, META;

        /** The bit this modifier takes in {@link KeyPress#mods}, 0 for NONE. The enum order is the display order. */
        public int bit() {
            return this == NONE ? 0 : 1 << (ordinal() - 1);
        }
    }

    /** Ctrl, Shift and Alt: the modifiers a one-modifier host such as NeoForge can store. */
    public static final int ONE_MODIFIER_HOSTS = Modifier.CONTROL.bit() | Modifier.SHIFT.bit() | Modifier.ALT.bit();

    public KeyPress(Kind kind, int code, int scancode) {
        this(kind, code, scancode, 0);
    }

    public KeyPress(Kind kind, int code, int scancode, Modifier modifier) {
        this(kind, code, scancode, modifier.bit());
    }

    public static KeyPress key(int code, int scancode) {
        return new KeyPress(Kind.KEY, code, scancode);
    }

    /** Mouse button {@code button}, 0 left, 1 right, 2 middle, 3 and 4 the side buttons. */
    public static KeyPress mouse(int button) {
        return new KeyPress(Kind.MOUSE, button, 0);
    }

    /** This key with {@code held} held down, such as Ctrl + M. */
    public KeyPress with(Modifier held) {
        return new KeyPress(this.kind, this.code, this.scancode, held.bit());
    }

    /** This key with the modifier mask {@code held}, such as Ctrl + Shift + M. */
    public KeyPress with(int held) {
        return new KeyPress(this.kind, this.code, this.scancode, held);
    }

    public boolean has(Modifier m) {
        return m != Modifier.NONE && (this.mods & m.bit()) != 0;
    }

    /** The first modifier of the mask in display order, NONE when none: the whole answer for a one-modifier host. */
    public Modifier modifier() {
        for (Modifier m : Modifier.values()) {
            if (has(m)) {
                return m;
            }
        }
        return Modifier.NONE;
    }

    /** The mask as the player reads it, in the fixed order Ctrl, Shift, Alt, Meta: {@code "Ctrl + Shift + "}, empty for none. */
    public static String prefix(int mods) {
        StringBuilder sb = new StringBuilder();
        for (Modifier m : Modifier.values()) {
            if (m != Modifier.NONE && (mods & m.bit()) != 0) {
                sb.append(switch (m) {
                    case CONTROL -> "Ctrl";
                    case SHIFT -> "Shift";
                    case ALT -> "Alt";
                    default -> "Meta";
                }).append(" + ");
            }
        }
        return sb.toString();
    }
}
