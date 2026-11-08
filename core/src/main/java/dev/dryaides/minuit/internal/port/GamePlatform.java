package dev.dryaides.minuit.internal.port;

import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.HeldKeys;
import dev.dryaides.minuit.ItemLookup;
import dev.dryaides.minuit.RichTextComponent;
import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.TextMeasure;
import dev.dryaides.minuit.UiSound;
import dev.dryaides.minuit.Words;

import java.nio.file.Path;
import java.util.function.ToIntFunction;

/**
 * What the game gives core, built by the variant's {@code port/mc/Bridges} and handed to
 * {@link Platform#install}. Each method defaults to the {@code NONE} of its bridge, or for a public
 * hook to the value it holds now, so a bench or the site runs with no game. Not API.
 */
public interface GamePlatform {

    /** The game folder; {@code config/} sits in it. */
    default Path gameDir() {
        return Path.of(".");
    }

    default TextureBridge textures() {
        return TextureBridge.NONE;
    }

    default SkinBridge skins() {
        return SkinBridge.NONE;
    }

    default ToastBridge toasts() {
        return ToastBridge.NONE;
    }

    default ScreenBridge screens() {
        return ScreenBridge.NONE;
    }

    default KeyCodes keyCodes() {
        return KeyCodes.GLFW;
    }

    /** The player's language. */
    default Words words() {
        return (key, args) -> key;
    }

    default UiSound sound() {
        return BaseComponent.sound;
    }

    default HeldKeys keys() {
        return BaseComponent.keys;
    }

    default TextFieldComponent.Clipboard clipboard() {
        return TextFieldComponent.clipboard;
    }

    default RichTextComponent.LinkOpener links() {
        return RichTextComponent.links;
    }

    default ToIntFunction<String> font() {
        return TextMeasure.font;
    }

    /** Null keeps the lookup plugged in now. */
    default ItemLookup items() {
        return null;
    }
}
