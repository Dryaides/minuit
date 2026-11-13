package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Internal;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.internal.GamepadInput;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLStdinc;

import java.nio.IntBuffer;

/**
 * 26.3 runs its window on SDL3 instead of GLFW: reads the first connected gamepad through SDL's gamepad
 * API and hands it to the screen's host in GLFW's button and axis order, which {@link GamepadInput} uses.
 *
 * <p>Client only.
 */
@Internal
public final class GamepadPoll {

    private static final boolean[] BUTTONS = new boolean[GamepadInput.BUTTON_COUNT];
    private static final float[] AXES = new float[2];
    /** SDL button for each GLFW index {@link GamepadInput} reads; -1 where the kit reads none. */
    private static final int[] SDL_BUTTON = new int[GamepadInput.BUTTON_COUNT];
    private static boolean started;
    private static boolean failed;
    private static long pad;

    static {
        java.util.Arrays.fill(SDL_BUTTON, -1);
        SDL_BUTTON[GamepadInput.BUTTON_A] = SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH;
        SDL_BUTTON[GamepadInput.BUTTON_B] = SDLGamepad.SDL_GAMEPAD_BUTTON_EAST;
        SDL_BUTTON[GamepadInput.BUTTON_LEFT_BUMPER] = SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER;
        SDL_BUTTON[GamepadInput.BUTTON_RIGHT_BUMPER] = SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER;
        SDL_BUTTON[GamepadInput.BUTTON_BACK] = SDLGamepad.SDL_GAMEPAD_BUTTON_BACK;
        SDL_BUTTON[GamepadInput.BUTTON_START] = SDLGamepad.SDL_GAMEPAD_BUTTON_START;
        SDL_BUTTON[GamepadInput.BUTTON_DPAD_UP] = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP;
        SDL_BUTTON[GamepadInput.BUTTON_DPAD_RIGHT] = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT;
        SDL_BUTTON[GamepadInput.BUTTON_DPAD_DOWN] = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN;
        SDL_BUTTON[GamepadInput.BUTTON_DPAD_LEFT] = SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT;
    }

    private GamepadPoll() {
    }

    /** From the screen's tick. True when Back or B was pressed: the screen calls its {@code onClose}. */
    public static boolean tick(ScreenHost host, PanelComponent panel) {
        boolean read = open();
        if (read) {
            SDLGamepad.SDL_UpdateGamepads();
        }
        for (int i = 0; i < BUTTONS.length; i++) {
            BUTTONS[i] = read && SDL_BUTTON[i] >= 0 && SDLGamepad.SDL_GetGamepadButton(pad, SDL_BUTTON[i]);
        }
        AXES[0] = read ? SDLGamepad.SDL_GetGamepadAxis(pad, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTX) / 32767f : 0f;
        AXES[1] = read ? SDLGamepad.SDL_GetGamepadAxis(pad, SDLGamepad.SDL_GAMEPAD_AXIS_LEFTY) / 32767f : 0f;
        return host.gamepad(panel, System.currentTimeMillis(), BUTTONS, AXES);
    }

    /** The open gamepad, opening the first one SDL lists; the game starts SDL without its gamepad subsystem. */
    private static boolean open() {
        if (failed) {
            return false;
        }
        try {
            if (!started) {
                started = true;
                if (!SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_GAMEPAD)) {
                    failed = true;
                    return false;
                }
            }
            if (pad != 0 && !SDLGamepad.SDL_GamepadConnected(pad)) {
                SDLGamepad.SDL_CloseGamepad(pad);
                pad = 0;
            }
            if (pad == 0) {
                IntBuffer ids = SDLGamepad.SDL_GetGamepads();
                if (ids != null) {
                    if (ids.remaining() > 0) {
                        pad = SDLGamepad.SDL_OpenGamepad(ids.get(ids.position()));
                    }
                    SDLStdinc.SDL_free(ids);
                }
            }
            return pad != 0;
        } catch (LinkageError e) {
            failed = true;
            return false;
        }
    }
}
