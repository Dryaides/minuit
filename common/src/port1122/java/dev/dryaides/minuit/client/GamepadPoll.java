package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Internal;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.internal.GamepadInput;
import org.lwjgl.input.Controller;
import org.lwjgl.input.Controllers;

/**
 * 1.12.2 runs on LWJGL 2, without GLFW: reads the first gamepad LWJGL's {@link Controllers} list (JInput)
 * and hands it to the screen's host. An XInput pad seen through DirectInput numbers A, B, LB, RB, Back
 * and Start as GLFW does (0, 1, 4, 5, 6, 7); its D-pad is the point-of-view hat.
 *
 * <p>Client only.
 */
@Internal
public final class GamepadPoll {

    private static final boolean[] BUTTONS = new boolean[GamepadInput.BUTTON_COUNT];
    private static final float[] AXES = new float[2];
    private static boolean started;
    private static boolean failed;
    private static Controller pad;

    private GamepadPoll() {
    }

    /** From the screen's tick. True when Back or B was pressed: the screen closes as on Escape. */
    public static boolean tick(ScreenHost host, PanelComponent panel) {
        boolean read = poll();
        for (int i = 0; i < BUTTONS.length; i++) {
            BUTTONS[i] = read && i < GamepadInput.BUTTON_DPAD_UP && i < pad.getButtonCount() && pad.isButtonPressed(i);
        }
        if (read) {
            BUTTONS[GamepadInput.BUTTON_DPAD_UP] = pad.getPovY() < -0.5f;
            BUTTONS[GamepadInput.BUTTON_DPAD_DOWN] = pad.getPovY() > 0.5f;
            BUTTONS[GamepadInput.BUTTON_DPAD_LEFT] = pad.getPovX() < -0.5f;
            BUTTONS[GamepadInput.BUTTON_DPAD_RIGHT] = pad.getPovX() > 0.5f;
        }
        AXES[0] = read ? pad.getXAxisValue() : 0f;
        AXES[1] = read ? pad.getYAxisValue() : 0f;
        return host.gamepad(panel, System.currentTimeMillis(), BUTTONS, AXES);
    }

    /** Polls the pad, picking on first use the first controller with a stick and at least eight buttons. */
    private static boolean poll() {
        if (failed) {
            return false;
        }
        try {
            if (!started) {
                started = true;
                if (!Controllers.isCreated()) {
                    Controllers.create();
                }
                for (int i = 0; i < Controllers.getControllerCount() && pad == null; i++) {
                    Controller c = Controllers.getController(i);
                    if (c.getButtonCount() >= 8 && c.getAxisCount() >= 2) {
                        pad = c;
                    }
                }
            }
            if (pad == null) {
                return false;
            }
            Controllers.poll();
            return true;
        } catch (Throwable e) {
            failed = true;
            return false;
        }
    }
}
