package dev.dryaides.minuit.internal.port;

import dev.dryaides.minuit.internal.HostState;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.ItemLookup;
import dev.dryaides.minuit.RichTextComponent;
import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.TextMeasure;
import dev.dryaides.minuit.Words;
import dev.dryaides.minuit.WindowComponent;
import dev.dryaides.minuit.client.MinuitClient;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The installed {@link GamePlatform}, one static slot per bridge. Before {@link #install} every slot
 * holds its {@code NONE}. Not API.
 */
public final class Platform {

    private static volatile GamePlatform game = new GamePlatform() {
    };
    private static volatile boolean installed;
    private static volatile TextureBridge textures = TextureBridge.NONE;
    private static volatile SkinBridge skins = SkinBridge.NONE;
    private static volatile ToastBridge toasts = ToastBridge.NONE;
    private static volatile ScreenBridge screens = ScreenBridge.NONE;
    private static volatile KeyCodes keyCodes = KeyCodes.GLFW;
    private static volatile Words words = game.words();
    private static volatile boolean development;

    private Platform() {
    }

    /** The loader's client entry calls it, through the variant's {@code Bridges.install()}, before anything reads a slot. */
    public static void install(GamePlatform platform) {
        game = Objects.requireNonNull(platform);
        textures = platform.textures();
        skins = platform.skins();
        toasts = platform.toasts();
        screens = platform.screens();
        keyCodes = platform.keyCodes();
        words = platform.words();
        installed = true;
        plugIn();
    }

    /**
     * Sets the public hooks on the components (sound cues, held keys, clipboard, links, font, item
     * lookup, window places) from the installed platform. Idempotent; a screen calls it on each init.
     */
    public static void plugIn() {
        if (!installed) {
            return;
        }
        GamePlatform now = game;
        HostState.sound(now.sound());
        HostState.keys(now.keys());
        TextFieldComponent.clipboard = now.clipboard();
        HostState.links(now.links());
        TextMeasure.font = now.font();
        TextMeasure.fontChanged();
        ItemLookup items = now.items();
        if (items != null) {
            ItemLookup.plugIn(items);
        }
        WindowComponent.layouts = MinuitClient.layouts();
    }

    public static Path gameDir() {
        return game.gameDir();
    }

    public static TextureBridge textures() {
        return textures;
    }

    public static SkinBridge skins() {
        return skins;
    }

    public static ToastBridge toasts() {
        return toasts;
    }

    public static ScreenBridge screens() {
        return screens;
    }

    public static KeyCodes keyCodes() {
        return keyCodes;
    }

    public static Words words() {
        return words;
    }

    /** The loader's client entry calls it once: true in a development environment, where the kit writes notes for the modder. */
    public static void development(boolean on) {
        development = on;
    }

    /** False until the loader says otherwise, so a released game never gets the modder's notes. */
    public static boolean development() {
        return development;
    }

    /** One line on {@code System.err}, which the game copies into its log on every version; core has no logger. */
    public static void note(String line) {
        System.err.println(line);
    }
}
