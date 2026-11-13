package dev.dryaides.minuit.client;

import dev.dryaides.minuit.internal.HostState;
import dev.dryaides.minuit.ScreenBackground;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.MotionStyle;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeChoices;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.LayoutStore;
import dev.dryaides.minuit.internal.ClientConfigFile;
import dev.dryaides.minuit.internal.ContainerTab;
import dev.dryaides.minuit.internal.LayoutFile;
import dev.dryaides.minuit.internal.port.Platform;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The player's side of the kit, kept in {@code config/minuit-client.json}: the theme choices, handed
 * to {@link Themes#choose} so every {@code Themes.byId} wears them, and the reduced-motion flag, set
 * on {@link BaseComponent#reducedMotion} for every animated widget.
 */
public final class MinuitClient {

    private static volatile ClientConfigFile.Settings settings;
    private static volatile LayoutStore layouts;

    private MinuitClient() {
    }

    /** The player's choices, read once on first call; the loader entry points ask at client start. */
    public static ThemeChoices themeChoices() {
        return settings().choices();
    }

    /** The player's reduced-motion flag, read with the theme choices. */
    public static boolean reducedMotion() {
        return settings().reducedMotion();
    }

    /** Sets the reduced-motion flag for every widget from the next frame on and saves it. */
    public static void reducedMotion(boolean value) {
        save(settings().withReducedMotion(value));
    }

    /** The player's motion style over every mod's, or null when they chose none. */
    public static MotionStyle motion() {
        return settings().motion();
    }

    /** Sets the player's motion style for every widget from the next frame on and saves it; null gives each mod its own. */
    public static void motion(MotionStyle value) {
        save(settings().withMotion(value));
    }

    /** Where the theme tab sits on the game's container screens, read with the theme choices. */
    public static ClientConfigFile.TabPlace containerTab() {
        return settings().containerTab();
    }

    /** True when the player chose to draw every tooltip of the game in minuit's theme; off by default. */
    public static boolean themedItemTips() {
        return settings().themedItemTips();
    }

    /** Draws every tooltip of the game in minuit's theme from the next frame on, or the game's box again, and saves it. */
    public static void themedItemTips(boolean value) {
        save(settings().withThemedItemTips(value));
    }

    /** What minuit screens draw behind their panel, the player's pick for the whole game; {@link ScreenBackground#NONE}'s by default. */
    public static ScreenBackground background() {
        return ScreenBackground.byId(settings().background());
    }

    /** Draws background {@code id} behind every minuit screen from the next frame on, and saves it. */
    public static void background(String id) {
        save(settings().withBackground(id));
    }

    /**
     * Writes {@code choice}, the look the player sees on {@code modId}'s screens, as a resource pack zip in the game's
     * resourcepacks folder, to share or to enable on another install. Returns the zip, or null when the family's
     * shapes cannot be named in a pack (an image family).
     */
    public static java.nio.file.Path exportTheme(String modId, dev.dryaides.minuit.ThemeChoice choice)
            throws java.io.IOException {
        dev.dryaides.minuit.ThemeFamily family = Themes.family(modId, choice.family());
        if (family == null) {
            return null;
        }
        dev.dryaides.minuit.ThemeFamily.Chroma chroma = choice.ownChroma() && Themes.isRegistered(modId)
                ? Themes.ownChroma(Themes.registered(modId)) : family.chroma(choice.chroma());
        if (chroma == null) {
            chroma = family.first();
        }
        String name = family.id() + " " + chroma.id() + " " + choice.variant().name().toLowerCase(java.util.Locale.ROOT)
                + (choice.accent().isEmpty() ? "" : " " + choice.accent());
        try {
            return dev.dryaides.minuit.internal.ThemePackExport.write(Platform.gameDir().resolve("resourcepacks"),
                    name, family, chroma, choice);
        } catch (IllegalArgumentException unnamed) {
            return null;
        }
    }

    /**
     * Keeps minuit's theme tab off the container screens of {@code screenClass} and its subclasses. A mod
     * calls it at client start for its own screens that have no room for the tab or draw their own.
     */
    public static void hideThemeTab(Class<?> screenClass) {
        ContainerTab.hideOn(screenClass);
    }

    /** Keeps the theme tab off every container screen whose class sits in {@code packagePrefix}, such as a mod's root package. */
    public static void hideThemeTabIn(String packagePrefix) {
        ContainerTab.hideIn(packagePrefix);
    }

    private static ClientConfigFile.Settings settings() {
        ClientConfigFile.Settings now = settings;
        if (now == null) {
            now = ClientConfigFile.load(file());
            settings = now;
            Themes.choose(now.choices());
            HostState.reducedMotion(now.reducedMotion());
            HostState.playerMotion(now.motion());
        }
        return now;
    }

    /** Applies {@code picked} to {@code modId}'s screens built from now on and saves it; null clears the mod's choice. */
    public static void themeChoice(String modId, ThemeChoice picked) {
        save(settings().withChoices(picked == null ? themeChoices().without(modId) : themeChoices().with(modId, picked)));
    }

    /** Applies {@code picked} to every mod's screens built from now on and saves it; null clears every choice. */
    public static void themeChoiceEveryMod(ThemeChoice picked) {
        save(settings().withChoices(themeChoices().everyMod(picked)));
    }

    private static void save(ClientConfigFile.Settings picked) {
        settings = picked;
        Themes.choose(picked.choices());
        HostState.reducedMotion(picked.reducedMotion());
        HostState.playerMotion(picked.motion());
        try {
            ClientConfigFile.save(file(), picked);
        } catch (IOException ignored) {
            // A read-only config keeps the pick for this session.
        }
    }

    /**
     * The windows' saved places, {@code config/minuit-layouts.json}. The file is opened on the first
     * get or put: the platform is installed while mods construct, before the game knows its folder.
     */
    public static LayoutStore layouts() {
        return LAZY_LAYOUTS;
    }

    private static final LayoutStore LAZY_LAYOUTS = new LayoutStore() {
        @Override
        public Layout get(String key) {
            return file().get(key);
        }

        @Override
        public void put(String key, Layout layout) {
            file().put(key, layout);
        }

        private LayoutStore file() {
            LayoutStore now = layouts;
            if (now == null) {
                now = new LayoutFile(Platform.gameDir().resolve("config")
                        .resolve("minuit-layouts.json"));
                layouts = now;
            }
            return now;
        }
    };

    private static Path file() {
        return Platform.gameDir().resolve("config").resolve("minuit-client.json");
    }
}
