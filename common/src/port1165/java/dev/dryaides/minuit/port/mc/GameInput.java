package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.HeldKeys;
import dev.dryaides.minuit.RichTextComponent;
import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.UiSound;
import dev.dryaides.minuit.internal.SoundCues;
import dev.dryaides.minuit.internal.port.KeyCodes;
import dev.dryaides.minuit.internal.port.KeyStroke;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;

/** The game's keys, sounds, clipboard and links as core's hooks want them. Client only, not API. */
public final class GameInput {

    /** GLFW's own constants: 1.16.5's {@code InputConstants} maps them but has no {@code KEY_} fields. */
    static final KeyCodes CODES = new KeyCodes(GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_TAB,
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_BACKSPACE,
            GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END, GLFW.GLFW_KEY_LEFT,
            GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_SPACE,
            GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_V,
            GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);

    static final UiSound SOUNDS = SoundCues.over(GameInput::play);

    /** Ctrl (Command on macOS) and Shift as the game reads them, for a wheel turn or a click. */
    static final HeldKeys HELD_KEYS = new HeldKeys() {
        @Override
        public boolean ctrl() {
            return Screen.hasControlDown();
        }

        @Override
        public boolean shift() {
            return Screen.hasShiftDown();
        }
    };

    static final TextFieldComponent.Clipboard CLIPBOARD = new TextFieldComponent.Clipboard() {
        @Override
        public String get() {
            return Minecraft.getInstance().keyboardHandler.getClipboard();
        }

        @Override
        public void set(String text) {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
        }
    };

    /** The player answers, then comes back to the screen. */
    static final RichTextComponent.LinkOpener LINKS = GameScreens::confirmLink;

    private GameInput() {
    }

    /**
     * A key press of the int-coded screen events: Ctrl and Shift read as the game reads them for its
     * own shortcuts (Command for Ctrl on macOS), the modifier bits being the same keys.
     */
    public static KeyStroke stroke(int key, int scancode) {
        return new KeyStroke(key, scancode, Screen.hasControlDown(), Screen.hasShiftDown());
    }

    static int width(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    private static void play(String soundId, float pitch, float volume) {
        SoundEvent sound = GameIds.sound(soundId);
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }
}
