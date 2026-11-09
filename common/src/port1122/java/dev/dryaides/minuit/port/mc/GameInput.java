package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.HeldKeys;
import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.UiSound;
import dev.dryaides.minuit.internal.SoundCues;
import dev.dryaides.minuit.internal.port.KeyCodes;
import dev.dryaides.minuit.internal.port.KeyStroke;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The game's keys, sounds and clipboard as core's hooks want them. 1.12.2 reads keys through LWJGL 2,
 * whose codes are not GLFW's; core stays on GLFW codes (its {@link KeyCodes#GLFW} and the raw codes some
 * components test), so every key crosses {@link #glfw} on the way in and {@link #lwjgl} on the way back
 * to {@code Keyboard} or a vanilla key binding. Client only, not API.
 */
public final class GameInput {

    /** One row per key both libraries name: the LWJGL 2 code, the GLFW name (after {@code GLFW_KEY_}) and code. */
    public static final class Key {
        public final int lwjgl;
        public final String glfwName;
        public final int glfw;

        Key(int lwjgl, String glfwName, int glfw) {
            this.lwjgl = lwjgl;
            this.glfwName = glfwName;
            this.glfw = glfw;
        }
    }

    public static final List<Key> KEYS;
    private static final int[] TO_GLFW = new int[256];
    private static final int[] TO_LWJGL = new int[349];

    static {
        List<Key> k = new ArrayList<>();
        k.add(new Key(Keyboard.KEY_ESCAPE, "ESCAPE", 256));
        int[] digits = {Keyboard.KEY_0, Keyboard.KEY_1, Keyboard.KEY_2, Keyboard.KEY_3, Keyboard.KEY_4,
                Keyboard.KEY_5, Keyboard.KEY_6, Keyboard.KEY_7, Keyboard.KEY_8, Keyboard.KEY_9};
        for (int i = 0; i < 10; i++) {
            k.add(new Key(digits[i], String.valueOf(i), 48 + i));
        }
        int[] letters = {Keyboard.KEY_A, Keyboard.KEY_B, Keyboard.KEY_C, Keyboard.KEY_D, Keyboard.KEY_E,
                Keyboard.KEY_F, Keyboard.KEY_G, Keyboard.KEY_H, Keyboard.KEY_I, Keyboard.KEY_J, Keyboard.KEY_K,
                Keyboard.KEY_L, Keyboard.KEY_M, Keyboard.KEY_N, Keyboard.KEY_O, Keyboard.KEY_P, Keyboard.KEY_Q,
                Keyboard.KEY_R, Keyboard.KEY_S, Keyboard.KEY_T, Keyboard.KEY_U, Keyboard.KEY_V, Keyboard.KEY_W,
                Keyboard.KEY_X, Keyboard.KEY_Y, Keyboard.KEY_Z};
        for (int i = 0; i < 26; i++) {
            k.add(new Key(letters[i], String.valueOf((char) ('A' + i)), 65 + i));
        }
        int[] functions = {Keyboard.KEY_F1, Keyboard.KEY_F2, Keyboard.KEY_F3, Keyboard.KEY_F4, Keyboard.KEY_F5,
                Keyboard.KEY_F6, Keyboard.KEY_F7, Keyboard.KEY_F8, Keyboard.KEY_F9, Keyboard.KEY_F10,
                Keyboard.KEY_F11, Keyboard.KEY_F12, Keyboard.KEY_F13, Keyboard.KEY_F14, Keyboard.KEY_F15,
                Keyboard.KEY_F16, Keyboard.KEY_F17, Keyboard.KEY_F18, Keyboard.KEY_F19};
        for (int i = 0; i < functions.length; i++) {
            k.add(new Key(functions[i], "F" + (i + 1), 290 + i));
        }
        int[] numpad = {Keyboard.KEY_NUMPAD0, Keyboard.KEY_NUMPAD1, Keyboard.KEY_NUMPAD2, Keyboard.KEY_NUMPAD3,
                Keyboard.KEY_NUMPAD4, Keyboard.KEY_NUMPAD5, Keyboard.KEY_NUMPAD6, Keyboard.KEY_NUMPAD7,
                Keyboard.KEY_NUMPAD8, Keyboard.KEY_NUMPAD9};
        for (int i = 0; i < 10; i++) {
            k.add(new Key(numpad[i], "KP_" + i, 320 + i));
        }
        k.add(new Key(Keyboard.KEY_MINUS, "MINUS", 45));
        k.add(new Key(Keyboard.KEY_EQUALS, "EQUAL", 61));
        k.add(new Key(Keyboard.KEY_BACK, "BACKSPACE", 259));
        k.add(new Key(Keyboard.KEY_TAB, "TAB", 258));
        k.add(new Key(Keyboard.KEY_LBRACKET, "LEFT_BRACKET", 91));
        k.add(new Key(Keyboard.KEY_RBRACKET, "RIGHT_BRACKET", 93));
        k.add(new Key(Keyboard.KEY_RETURN, "ENTER", 257));
        k.add(new Key(Keyboard.KEY_LCONTROL, "LEFT_CONTROL", 341));
        k.add(new Key(Keyboard.KEY_SEMICOLON, "SEMICOLON", 59));
        k.add(new Key(Keyboard.KEY_APOSTROPHE, "APOSTROPHE", 39));
        k.add(new Key(Keyboard.KEY_GRAVE, "GRAVE_ACCENT", 96));
        k.add(new Key(Keyboard.KEY_LSHIFT, "LEFT_SHIFT", 340));
        k.add(new Key(Keyboard.KEY_BACKSLASH, "BACKSLASH", 92));
        k.add(new Key(Keyboard.KEY_COMMA, "COMMA", 44));
        k.add(new Key(Keyboard.KEY_PERIOD, "PERIOD", 46));
        k.add(new Key(Keyboard.KEY_SLASH, "SLASH", 47));
        k.add(new Key(Keyboard.KEY_RSHIFT, "RIGHT_SHIFT", 344));
        k.add(new Key(Keyboard.KEY_MULTIPLY, "KP_MULTIPLY", 332));
        k.add(new Key(Keyboard.KEY_LMENU, "LEFT_ALT", 342));
        k.add(new Key(Keyboard.KEY_SPACE, "SPACE", 32));
        k.add(new Key(Keyboard.KEY_CAPITAL, "CAPS_LOCK", 280));
        k.add(new Key(Keyboard.KEY_NUMLOCK, "NUM_LOCK", 282));
        k.add(new Key(Keyboard.KEY_SCROLL, "SCROLL_LOCK", 281));
        k.add(new Key(Keyboard.KEY_SUBTRACT, "KP_SUBTRACT", 333));
        k.add(new Key(Keyboard.KEY_ADD, "KP_ADD", 334));
        k.add(new Key(Keyboard.KEY_DECIMAL, "KP_DECIMAL", 330));
        k.add(new Key(Keyboard.KEY_NUMPADEQUALS, "KP_EQUAL", 336));
        k.add(new Key(Keyboard.KEY_NUMPADENTER, "KP_ENTER", 335));
        k.add(new Key(Keyboard.KEY_RCONTROL, "RIGHT_CONTROL", 345));
        k.add(new Key(Keyboard.KEY_DIVIDE, "KP_DIVIDE", 331));
        k.add(new Key(Keyboard.KEY_SYSRQ, "PRINT_SCREEN", 283));
        k.add(new Key(Keyboard.KEY_RMENU, "RIGHT_ALT", 346));
        k.add(new Key(Keyboard.KEY_PAUSE, "PAUSE", 284));
        k.add(new Key(Keyboard.KEY_HOME, "HOME", 268));
        k.add(new Key(Keyboard.KEY_UP, "UP", 265));
        k.add(new Key(Keyboard.KEY_PRIOR, "PAGE_UP", 266));
        k.add(new Key(Keyboard.KEY_LEFT, "LEFT", 263));
        k.add(new Key(Keyboard.KEY_RIGHT, "RIGHT", 262));
        k.add(new Key(Keyboard.KEY_END, "END", 269));
        k.add(new Key(Keyboard.KEY_DOWN, "DOWN", 264));
        k.add(new Key(Keyboard.KEY_NEXT, "PAGE_DOWN", 267));
        k.add(new Key(Keyboard.KEY_INSERT, "INSERT", 260));
        k.add(new Key(Keyboard.KEY_DELETE, "DELETE", 261));
        k.add(new Key(Keyboard.KEY_LMETA, "LEFT_SUPER", 343));
        k.add(new Key(Keyboard.KEY_RMETA, "RIGHT_SUPER", 347));
        k.add(new Key(Keyboard.KEY_APPS, "MENU", 348));
        KEYS = Collections.unmodifiableList(k);
        for (Key key : KEYS) {
            TO_GLFW[key.lwjgl] = key.glfw;
            TO_LWJGL[key.glfw] = key.lwjgl;
        }
    }

    /** Core's codes are GLFW's; mouse buttons 0 to 2 are the same in both libraries. */
    static final KeyCodes CODES = KeyCodes.GLFW;

    static final UiSound SOUNDS = SoundCues.over(GameInput::play);

    static final HeldKeys HELD_KEYS = new HeldKeys() {
        @Override
        public boolean ctrl() {
            return GuiScreen.isCtrlKeyDown();
        }

        @Override
        public boolean shift() {
            return GuiScreen.isShiftKeyDown();
        }
    };

    static final TextFieldComponent.Clipboard CLIPBOARD = new TextFieldComponent.Clipboard() {
        @Override
        public String get() {
            return GuiScreen.getClipboardString();
        }

        @Override
        public void set(String text) {
            GuiScreen.setClipboardString(text);
        }
    };

    private GameInput() {
    }

    /** The GLFW code of an LWJGL 2 key, -1 for a key GLFW does not name (Kana, Yen, Power...). */
    public static int glfw(int lwjgl) {
        int code = lwjgl >= 0 && lwjgl < TO_GLFW.length ? TO_GLFW[lwjgl] : 0;
        return code == 0 ? -1 : code;
    }

    /** The LWJGL 2 code of a GLFW key, {@code Keyboard.KEY_NONE} for one LWJGL 2 lacks. */
    public static int lwjgl(int glfw) {
        return glfw >= 0 && glfw < TO_LWJGL.length ? TO_LWJGL[glfw] : Keyboard.KEY_NONE;
    }

    /** A key press of {@code GuiScreen.keyTyped}, the LWJGL 2 code kept as the scancode. */
    public static KeyStroke stroke(int lwjglKey) {
        return new KeyStroke(glfw(lwjglKey), lwjglKey, GuiScreen.isCtrlKeyDown(), GuiScreen.isShiftKeyDown());
    }

    /**
     * Whether the char of a {@code keyTyped} call goes on to core as typed text: 1.12.2 hands key and
     * char in one call, core takes them as a key press then a typed char, as GLFW sends them.
     */
    public static boolean typed(char c) {
        return c != 0 && ChatAllowedCharacters.isAllowedCharacter(c);
    }

    /** A wheel turn of {@code Mouse.getEventDWheel} in notches: LWJGL 2 counts 120 per notch. */
    public static double wheel(int dWheel) {
        return dWheel / 120D;
    }

    static int width(String text) {
        return Minecraft.getMinecraft().fontRenderer.getStringWidth(text);
    }

    private static void play(String soundId, float pitch, float volume) {
        SoundEvent sound = GameIds.sound(soundId);
        if (sound != null) {
            Minecraft.getMinecraft().getSoundHandler().playSound(new PositionedSoundRecord(sound.getSoundName(),
                    SoundCategory.MASTER, volume, pitch, false, 0, ISound.AttenuationType.NONE, 0F, 0F, 0F));
        }
    }
}
