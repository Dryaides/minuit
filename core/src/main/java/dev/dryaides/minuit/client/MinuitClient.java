package dev.dryaides.minuit.client;

import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.MotionStyle;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeChoices;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.LayoutStore;
import dev.dryaides.minuit.internal.ClientConfigFile;
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

    private static ClientConfigFile.Settings settings() {
        ClientConfigFile.Settings now = settings;
        if (now == null) {
            now = ClientConfigFile.load(file());
            settings = now;
            Themes.choose(now.choices());
            BaseComponent.reducedMotion = now.reducedMotion();
            BaseComponent.playerMotion = now.motion();
        }
        return now;
    }

    /** Applies {@code picked} to {@code modId}'s screens built from now on and saves it. */
    public static void themeChoice(String modId, ThemeChoice picked) {
        save(settings().withChoices(themeChoices().with(modId, picked)));
    }

    /** Applies {@code picked} to every mod's screens built from now on and saves it. */
    public static void themeChoiceEveryMod(ThemeChoice picked) {
        save(settings().withChoices(themeChoices().everyMod(picked)));
    }

    private static void save(ClientConfigFile.Settings picked) {
        settings = picked;
        Themes.choose(picked.choices());
        BaseComponent.reducedMotion = picked.reducedMotion();
        BaseComponent.playerMotion = picked.motion();
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
