package dev.dryaides.minuit.port.mc;

import com.mojang.blaze3d.platform.InputConstants;
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

/** The game's keys, sounds, clipboard and links as core's hooks want them. Client only, not API. */
public final class GameInput {

    /** From this jar's own constants (GLFW codes on 1.20.1 and 1.21.1). */
    static final KeyCodes CODES = new KeyCodes(InputConstants.KEY_ESCAPE, InputConstants.KEY_TAB,
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_BACKSPACE,
            InputConstants.KEY_DELETE, InputConstants.KEY_HOME, InputConstants.KEY_END, InputConstants.KEY_LEFT,
            InputConstants.KEY_RIGHT, InputConstants.KEY_UP, InputConstants.KEY_DOWN, InputConstants.KEY_SPACE,
            InputConstants.KEY_A, InputConstants.KEY_C, InputConstants.KEY_X, InputConstants.KEY_V,
            InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.MOUSE_BUTTON_MIDDLE);

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
