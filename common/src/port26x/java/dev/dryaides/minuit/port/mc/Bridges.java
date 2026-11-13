package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.HeldKeys;
import dev.dryaides.minuit.ItemLookup;
import dev.dryaides.minuit.RichTextComponent;
import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.UiSound;
import dev.dryaides.minuit.Words;
import dev.dryaides.minuit.internal.ImageFamily;
import dev.dryaides.minuit.internal.game.GameItems;
import dev.dryaides.minuit.internal.port.GamePlatform;
import dev.dryaides.minuit.internal.port.KeyCodes;
import dev.dryaides.minuit.internal.port.Platform;
import dev.dryaides.minuit.internal.port.ScreenBridge;
import dev.dryaides.minuit.internal.port.SkinBridge;
import dev.dryaides.minuit.internal.port.TextureBridge;
import dev.dryaides.minuit.internal.port.ToastBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.nio.file.Path;
import java.util.function.ToIntFunction;

/** Hands core the 26.x game: the loader's client entry calls {@link #install} first. Client only, not API. */
public final class Bridges {

    private static final GameTextures TEXTURES = new GameTextures();
    private static final SkinBridge SKINS = new GameSkins();
    private static final ScreenBridge SCREENS = new GameScreenBridge();

    private Bridges() {
    }

    public static void install() {
        dev.dryaides.minuit.ItemTipLook.hook();
        ImageFamily.install(TEXTURES);
        Platform.install(new GamePlatform() {
            @Override
            public Path gameDir() {
                return Minecraft.getInstance().gameDirectory.toPath();
            }

            @Override
            public TextureBridge textures() {
                return TEXTURES;
            }

            @Override
            public SkinBridge skins() {
                return SKINS;
            }

            @Override
            public ToastBridge toasts() {
                return ThemedToast::show;
            }

            @Override
            public ScreenBridge screens() {
                return SCREENS;
            }

            @Override
            public KeyCodes keyCodes() {
                return GameInput.CODES;
            }

            @Override
            public Words words() {
                return I18n::get;
            }

            @Override
            public UiSound sound() {
                return GameInput.SOUNDS;
            }

            @Override
            public HeldKeys keys() {
                return GameInput.HELD_KEYS;
            }

            @Override
            public TextFieldComponent.Clipboard clipboard() {
                return GameInput.CLIPBOARD;
            }

            @Override
            public RichTextComponent.LinkOpener links() {
                return GameInput.LINKS;
            }

            @Override
            public ToIntFunction<String> font() {
                return GameInput::width;
            }

            /** Fresh on each screen init, as the lists were before the cut. */
            @Override
            public ItemLookup items() {
                return new GameItems();
            }
        });
    }
}
