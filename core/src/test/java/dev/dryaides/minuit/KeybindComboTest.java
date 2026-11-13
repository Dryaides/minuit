package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The player's three cases in the Controls-style field, with GLFW codes: Ctrl 341, Left Shift 340, M 77. */
class KeybindComboTest {

    private static final int CTRL = 341;
    private static final int LEFT_SHIFT = 340;
    private static final int M = 77;

    private static final class Recorded implements KeyBinding {
        final boolean modifiers;
        KeyPress bound;

        Recorded(boolean modifiers) {
            this.modifiers = modifiers;
        }

        @Override public String keyName() { return ""; }
        @Override public boolean isDefault() { return true; }
        @Override public List<String> conflicts() { return List.of(); }
        @Override public boolean supportsModifiers() { return this.modifiers; }
        @Override public void bind(KeyPress key) { this.bound = key; }
        @Override public void unbind() { this.bound = null; }
        @Override public void reset() { }
    }

    private static KeybindFieldComponent listening(KeyBinding binding) {
        KeybindFieldComponent field = new KeybindFieldComponent(new Box("key", 0, 0, 120, 20), (key, args) -> key, binding);
        field.listening = true;
        return field;
    }

    @Test
    void ctrlThenMBindsCtrlPlusM() {
        Recorded binding = new Recorded(true);
        KeybindFieldComponent field = listening(binding);
        field.keyCaptured(KeyPress.key(CTRL, 0));
        assertEquals(null, binding.bound);
        field.keyCaptured(KeyPress.key(M, 0));
        assertEquals(KeyPress.key(M, 0).with(KeyPress.Modifier.CONTROL), binding.bound);
        assertFalse(field.listening);
    }

    @Test
    void shiftPressedAndLetGoAloneBindsLeftShift() {
        Recorded binding = new Recorded(true);
        KeybindFieldComponent field = listening(binding);
        field.keyCaptured(KeyPress.key(LEFT_SHIFT, 0));
        field.keyReleasedCaptured(KeyPress.key(LEFT_SHIFT, 0));
        assertEquals(KeyPress.key(LEFT_SHIFT, 0), binding.bound);
    }

    @Test
    void aBindingWithoutModifiersGetsTheKeyAlone() {
        Recorded binding = new Recorded(false);
        KeybindFieldComponent field = listening(binding);
        field.keyCaptured(KeyPress.key(CTRL, 0));
        field.keyCaptured(KeyPress.key(M, 0));
        assertEquals(KeyPress.key(M, 0), binding.bound);
    }
}
