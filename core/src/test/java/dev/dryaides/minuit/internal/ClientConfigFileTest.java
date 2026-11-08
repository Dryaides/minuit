package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeChoices;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigFileTest {

    private static final ThemeChoice GREEN_GLASS = new ThemeChoice("glass", "green", ThemeChoice.Variant.DARK);

    /** The file before per-mod choices held one choice for every screen: it becomes the global preference. */
    @Test
    void oldSingleChoiceReadsAsGlobal() {
        ThemeChoices read = ClientConfigFile.parse(
                "{\"theme\": {\"family\": \"glass\", \"chroma\": \"green\", \"variant\": \"dark\"}}").choices();
        assertEquals(new ThemeChoices(GREEN_GLASS, Map.of()), read);
    }

    /** The old "Per mod" card ({@code own}) meant no choice: every mod keeps its own default theme. */
    @Test
    void oldOwnReadsAsNoChoice() {
        assertEquals(ThemeChoices.NONE, ClientConfigFile.parse(
                "{\"theme\": {\"family\": \"own\", \"chroma\": \"default\", \"variant\": \"auto\"}}").choices());
        assertEquals(ThemeChoices.NONE, ClientConfigFile.parse("{\"theme\": 3").choices());
    }

    @Test
    void newShapeKeepsGlobalAndEachMod() {
        ThemeChoices read = ClientConfigFile.parse("""
                {"global": {"family": "glass", "chroma": "green", "variant": "dark"},
                 "mods": {"goodboy": {"family": "leather", "chroma": "paper", "variant": "light"}, "broken": 4}}""").choices();
        assertEquals(GREEN_GLASS, read.global());
        assertEquals(Map.of("goodboy", new ThemeChoice("leather", "paper", ThemeChoice.Variant.LIGHT)), read.mods());
    }

    /**
     * A player who wrote {@code "reducedMotion": true} by hand keeps it after the theme page saves a
     * pick; a file without the key, or with a non-boolean, means full motion.
     */
    @Test
    void reducedMotionFlagReadAndKeptOnSave() {
        ClientConfigFile.Settings read = ClientConfigFile.parse("""
                {"global": {"family": "glass", "chroma": "green", "variant": "dark"}, "reducedMotion": true}""");
        assertTrue(read.reducedMotion());
        ClientConfigFile.Settings saved = ClientConfigFile.parse(ClientConfigFile.write(
                read.withChoices(read.choices().with("goodboy", GREEN_GLASS))));
        assertTrue(saved.reducedMotion());
        assertFalse(ClientConfigFile.parse("{\"global\": null}").reducedMotion());
        assertFalse(ClientConfigFile.parse("{\"mods\": {}, \"reducedMotion\": \"yes\"}").reducedMotion());
    }

    /** Each mod keeps its own accent through a save; an accent no preset has reads as none. */
    @Test
    void accentIsKeptPerModAndUnknownReadsAsNone() {
        ThemeChoices picked = new ThemeChoices(GREEN_GLASS.withAccent("violet"),
                Map.of("goodboy", new ThemeChoice("leather", "paper", ThemeChoice.Variant.LIGHT, "rose")));
        ThemeChoices back = ClientConfigFile.parse(ClientConfigFile.write(
                new ClientConfigFile.Settings(picked, false))).choices();
        assertEquals("violet", back.global().accent());
        assertEquals("rose", back.mods().get("goodboy").accent());
        assertEquals("", ClientConfigFile.parse(
                "{\"global\": {\"family\": \"glass\", \"accent\": \"pink\"}}").choices().global().accent());
    }

    /** The owner's call (2026-10-26): the "dryaides" preset is gone, a config that saved it gets midnight. */
    @Test
    void aSavedDryaidesAccentReadsAsMidnight() {
        assertEquals("midnight", ClientConfigFile.parse(
                "{\"global\": {\"family\": \"soft\", \"accent\": \"dryaides\"}}").choices().global().accent());
    }
}
