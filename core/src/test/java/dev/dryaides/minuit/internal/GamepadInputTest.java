package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.InputKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ids from GLFW's gamepad tables: button A = 0, axis LEFT_X = 0, LEFT_Y = 1; 15 buttons, 6 axes. */
class GamepadInputTest {

    private static final GamepadInput.Event ENTER = new GamepadInput.Event(InputKey.ENTER, false);
    private static final GamepadInput.Event RIGHT = new GamepadInput.Event(InputKey.RIGHT, false);

    private static boolean[] pressed(int... ids) {
        boolean[] b = new boolean[15];
        for (int id : ids) {
            b[id] = true;
        }
        return b;
    }

    private static float[] leftStick(float x, float y) {
        return new float[] {x, y, 0, 0, -1, -1};
    }

    @Test
    void aHeldActivatesOnce() {
        GamepadInput pad = new GamepadInput();
        assertEquals(List.of(ENTER), pad.update(0, pressed(0), leftStick(0, 0)));
        assertEquals(List.of(), pad.update(1000, pressed(0), leftStick(0, 0)));
    }

    @Test
    void stickHeldRightRepeatsAfterTheDelay() {
        GamepadInput pad = new GamepadInput();
        assertEquals(List.of(RIGHT), pad.update(0, pressed(), leftStick(1, 0)));
        assertEquals(List.of(), pad.update(400, pressed(), leftStick(1, 0)));
        assertEquals(List.of(RIGHT), pad.update(500, pressed(), leftStick(1, 0)));
        assertEquals(List.of(RIGHT), pad.update(550, pressed(), leftStick(1, 0)));
    }

    @Test
    void deadZoneIgnoresAFifthOfTheThrow() {
        assertEquals(List.of(), new GamepadInput().update(0, pressed(), leftStick(0.2f, -0.2f)));
    }
}
