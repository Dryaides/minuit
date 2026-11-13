package dev.dryaides.minuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Vanilla EditBox: Ctrl+A then Backspace empties the box; Shift+arrow selects; typing replaces the selection. */
class TextFieldEditTest {

    private static TextFieldComponent field(String text) {
        return new TextFieldComponent(new Box("f", 0, 0, 200, 16), text, t -> { });
    }

    @Test
    void selectAllThenBackspaceClearsEverything() {
        TextFieldComponent f = field("Hello world");
        f.keyPressed(InputKey.SELECT_ALL, false);
        assertArrayEquals(new int[]{0, 11}, f.selection());
        f.keyPressed(InputKey.BACKSPACE, false);
        assertEquals("", f.text());
    }

    @Test
    void shiftLeftSelectsAndTypingReplacesIt() {
        TextFieldComponent f = field("Hello world");
        f.moveCursor(11, false);
        for (int i = 0; i < 5; i++) {
            f.keyPressed(InputKey.LEFT, true);
        }
        assertArrayEquals(new int[]{6, 11}, f.selection());
        f.charTyped('x');
        assertEquals("Hello x", f.text());
    }
}
