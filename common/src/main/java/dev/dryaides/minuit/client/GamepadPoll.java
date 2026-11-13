package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Internal;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.internal.GamepadInput;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * Reads the first connected gamepad (GLFW standard layout) for a kit screen's tick and hands it to the
 * screen's host. 1.12.2 (LWJGL 2) and 26.3 (SDL3) have their own copy in their port dir.
 *
 * <p>Client only.
 */
@Internal
public final class GamepadPoll {

    private static final boolean[] BUTTONS = new boolean[GamepadInput.BUTTON_COUNT];
    private static final float[] AXES = new float[2];
    private static GLFWGamepadState state;

    private GamepadPoll() {
    }

    /** From the screen's tick. True when Back or B was pressed: the screen calls its {@code onClose}. */
    public static boolean tick(ScreenHost host, PanelComponent panel) {
        if (state == null) {
            state = GLFWGamepadState.malloc();
        }
        boolean read = false;
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST && !read; jid++) {
            read = GLFW.glfwJoystickIsGamepad(jid) && GLFW.glfwGetGamepadState(jid, state);
        }
        for (int i = 0; i < BUTTONS.length; i++) {
            BUTTONS[i] = read && state.buttons(i) != 0;
        }
        AXES[0] = read ? state.axes(GamepadInput.AXIS_LEFT_X) : 0f;
        AXES[1] = read ? state.axes(GamepadInput.AXIS_LEFT_Y) : 0f;
        return host.gamepad(panel, System.currentTimeMillis(), BUTTONS, AXES);
    }
}
