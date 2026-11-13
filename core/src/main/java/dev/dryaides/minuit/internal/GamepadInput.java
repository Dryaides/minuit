package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.InputKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-frame gamepad state to the kit's keys, on GLFW's standard gamepad layout
 * (https://www.glfw.org/docs/latest/group__gamepad__buttons.html and group__gamepad__axes.html).
 * A and Start activate (Enter), B and Back go back (Escape), RB and LB move focus (Tab, Shift+Tab),
 * the D-pad and the left stick are the arrows. Only the arrows repeat while held, as a held keyboard
 * arrow does; the game reads key repeat from the OS, so the delay and rate here are the common
 * desktop defaults. Not API: a port polls {@code glfwGetGamepadState} each frame and feeds {@link #update}.
 */
public final class GamepadInput {

    public static final int BUTTON_A = 0;
    public static final int BUTTON_B = 1;
    public static final int BUTTON_LEFT_BUMPER = 4;
    public static final int BUTTON_RIGHT_BUMPER = 5;
    public static final int BUTTON_BACK = 6;
    public static final int BUTTON_START = 7;
    public static final int BUTTON_DPAD_UP = 11;
    public static final int BUTTON_DPAD_RIGHT = 12;
    public static final int BUTTON_DPAD_DOWN = 13;
    public static final int BUTTON_DPAD_LEFT = 14;
    public static final int BUTTON_COUNT = 15;
    public static final int AXIS_LEFT_X = 0;
    /** GLFW reports down as positive. */
    public static final int AXIS_LEFT_Y = 1;

    public static final float DEAD_ZONE = 0.25f;
    public static final long REPEAT_DELAY_MS = 500;
    public static final long REPEAT_INTERVAL_MS = 50;

    /** One key for the focused panel; {@code shift} is set for Shift+Tab. */
    public record Event(InputKey key, boolean shift) {
    }

    private static final InputKey[] ARROWS = {InputKey.UP, InputKey.RIGHT, InputKey.DOWN, InputKey.LEFT};

    private final boolean[] held = new boolean[BUTTON_COUNT];
    private InputKey arrow;
    private long nextRepeat;

    /** The key a single button press gives, or null for a button the kit leaves to the game. */
    public static Event button(int id) {
        return switch (id) {
            case BUTTON_A, BUTTON_START -> new Event(InputKey.ENTER, false);
            case BUTTON_B, BUTTON_BACK -> new Event(InputKey.ESCAPE, false);
            case BUTTON_RIGHT_BUMPER -> new Event(InputKey.TAB, false);
            case BUTTON_LEFT_BUMPER -> new Event(InputKey.TAB, true);
            case BUTTON_DPAD_UP -> new Event(InputKey.UP, false);
            case BUTTON_DPAD_RIGHT -> new Event(InputKey.RIGHT, false);
            case BUTTON_DPAD_DOWN -> new Event(InputKey.DOWN, false);
            case BUTTON_DPAD_LEFT -> new Event(InputKey.LEFT, false);
            default -> null;
        };
    }

    /** The arrow the left stick points at, the larger axis winning, or null inside the dead zone. */
    public static InputKey stick(float x, float y) {
        if (Math.abs(x) < DEAD_ZONE && Math.abs(y) < DEAD_ZONE) {
            return null;
        }
        if (Math.abs(x) >= Math.abs(y)) {
            return x > 0 ? InputKey.RIGHT : InputKey.LEFT;
        }
        return y > 0 ? InputKey.DOWN : InputKey.UP;
    }

    /**
     * The keys this frame gives: one per newly pressed button, and the held arrow (D-pad first, then
     * stick) when it starts and at each repeat. {@code buttons} and {@code axes} are GLFWgamepadstate's arrays.
     */
    public List<Event> update(long nowMs, boolean[] buttons, float[] axes) {
        List<Event> out = new ArrayList<>();
        for (int id = 0; id < BUTTON_COUNT; id++) {
            boolean down = id < buttons.length && buttons[id];
            if (down && !held[id] && id < BUTTON_DPAD_UP) {
                Event e = button(id);
                if (e != null) {
                    out.add(e);
                }
            }
            held[id] = down;
        }
        InputKey now = null;
        for (int i = 0; i < ARROWS.length && now == null; i++) {
            if (held[BUTTON_DPAD_UP + i]) {
                now = ARROWS[i];
            }
        }
        if (now == null && axes.length > AXIS_LEFT_Y) {
            now = stick(axes[AXIS_LEFT_X], axes[AXIS_LEFT_Y]);
        }
        if (now != arrow) {
            arrow = now;
            if (now != null) {
                out.add(new Event(now, false));
                nextRepeat = nowMs + REPEAT_DELAY_MS;
            }
        } else if (now != null && nowMs >= nextRepeat) {
            out.add(new Event(now, false));
            nextRepeat = Math.max(nextRepeat + REPEAT_INTERVAL_MS, nowMs - REPEAT_INTERVAL_MS + 1);
        }
        return out;
    }
}
