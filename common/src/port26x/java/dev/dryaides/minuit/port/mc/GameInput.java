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
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import java.net.URI;

/** The game's keys, sounds, clipboard and links as core's hooks want them. Client only, not API. */
final class GameInput {

    /** From this jar's own constants, so each version carries its own codes (SDL on 26.3). */
    static final KeyCodes CODES = new KeyCodes(InputConstants.KEY_ESCAPE, InputConstants.KEY_TAB,
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_BACKSPACE,
            InputConstants.KEY_DELETE, InputConstants.KEY_HOME, InputConstants.KEY_END, InputConstants.KEY_LEFT,
            InputConstants.KEY_RIGHT, InputConstants.KEY_UP, InputConstants.KEY_DOWN, InputConstants.KEY_SPACE,
            InputConstants.KEY_A, InputConstants.KEY_C, InputConstants.KEY_X, InputConstants.KEY_V,
            InputConstants.MOUSE_BUTTON_LEFT, InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.MOUSE_BUTTON_MIDDLE,
            InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT, InputConstants.KEY_LCONTROL,
            InputConstants.KEY_RCONTROL, InputConstants.KEY_LALT, InputConstants.KEY_RALT);

    static final UiSound SOUNDS = SoundCues.over(GameInput::play);

    /** Ctrl (Command on macOS) and Shift as the game reads them, for a wheel turn or a click. */
    static final HeldKeys HELD_KEYS = new HeldKeys() {
        @Override
        public boolean ctrl() {
            return Minecraft.getInstance().hasControlDown();
        }

        @Override
        public boolean shift() {
            return Minecraft.getInstance().hasShiftDown();
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

    /** The URI overload: the String one is gone in 26.3. The player answers, then comes back to the screen. */
    static final RichTextComponent.LinkOpener LINKS =
            url -> ConfirmLinkScreen.confirmLinkNow(GameScreens.current(), URI.create(url), false);

    private GameInput() {
    }

    static KeyStroke stroke(KeyEvent event) {
        return new KeyStroke(event.key(), 0, event.hasControlDownWithQuirk(), event.hasShiftDown());
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
