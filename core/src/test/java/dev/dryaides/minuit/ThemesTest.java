package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ClientConfigFile;
import dev.dryaides.minuit.internal.ThemeFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The order the decision of 2026-10-26 sets: the mod's choice, else the global one, else the mod's own theme. */
class ThemesTest {

    private static final ThemeChoice GLASS = new ThemeChoice("glass", "clear", ThemeChoice.Variant.LIGHT);
    private static final ThemeChoice SOFT = new ThemeChoice("soft", "sand", ThemeChoice.Variant.DARK);
    private static final ThemeChoice LEATHER = new ThemeChoice("leather", "paper", ThemeChoice.Variant.LIGHT);

    @BeforeEach
    void load() throws Exception {
        List<ThemeFiles.Entry> entries = new ArrayList<>(ThemeFiles.fromDirectory(
                Path.of("src/main/resources/assets/minuit/minuit"), "minuit"));
        entries.add(new ThemeFiles.Entry("goodboy:minuit/themes/leather/family.json", "goodboy",
                "themes/leather/family.json", "{\"style\": \"leather\", \"private\": true, \"chromas\": [\"paper\"]}"));
        Themes.load(ThemeFiles.assemble(entries, s -> { }));
        Themes.register(Themes.SLATE.as("sesame"));
        Themes.register(Themes.LEATHER.as("goodboy"));
    }

    @AfterEach
    void reset() {
        Themes.choose(ThemeChoices.NONE);
    }

    @Test
    void anUnregisteredIdWearsTheDefaultLookUnderItsOwnId() {
        assertEquals("newmod", Themes.byId("newmod").id());
        assertEquals("othermod", Themes.byId("othermod").id());
        assertEquals(Themes.defaultTheme().palette(), Themes.byId("newmod").palette());
        assertEquals(ThemeFiles.builtIn("vanilla", ThemeChoice.Variant.LIGHT), Themes.defaultTheme().palette());
    }

    @Test
    void anEmptyOrMissingConfigWearsVanillaLight() {
        for (String text : new String[] {"", "{}"}) {
            Themes.choose(ClientConfigFile.parse(text).choices());
            assertEquals(0xFFC6C6C6, Themes.byId("newmod").palette().surface(), text);
            assertSame(FrameStyles.VANILLA, Themes.byId("newmod").frame(), text);
        }
        Themes.choose(ClientConfigFile.Settings.NONE.choices());
        assertEquals(0xFFC6C6C6, Themes.byId("newmod").palette().surface());
    }

    @Test
    void noChoiceKeepsTheModsOwnTheme() {
        Themes.choose(ThemeChoices.NONE);
        assertEquals(Themes.SLATE.palette(), Themes.byId("sesame").palette());
        assertSame(FrameStyles.SLATE, Themes.byId("sesame").frame());
    }

    @Test
    void modChoiceBeatsGlobalWhichBeatsOwn() {
        Themes.choose(new ThemeChoices(GLASS, Map.of()).with("goodboy", SOFT));
        assertSame(FrameStyles.GLASS, Themes.byId("sesame").frame());
        assertEquals(0x78ECF6FA, Themes.byId("sesame").palette().surface());
        assertSame(FrameStyles.SOFT, Themes.byId("goodboy").frame());
        assertEquals("goodboy", Themes.byId("goodboy").id());
    }

    @Test
    void everyModReplacesEachModsChoice() {
        ThemeChoices choices = ThemeChoices.NONE.with("goodboy", SOFT).with("sesame", SOFT).everyMod(GLASS);
        assertEquals(GLASS, choices.forMod("goodboy"));
        assertEquals(GLASS, choices.forMod("a mod installed later"));
    }

    /** Leather is Good Boy's private family: offered on its page only, and worn by its screens only. */
    @Test
    void privateFamilyStaysWithItsMod() {
        assertEquals("goodboy", Themes.family("goodboy", "leather").owner());
        assertNull(Themes.family("sesame", "leather"));
        assertEquals(List.of(), Themes.families("sesame").stream().filter(f -> f.id().equals("leather")).toList());
        Themes.choose(ThemeChoices.NONE.everyMod(LEATHER));
        assertSame(FrameStyles.LEATHER, Themes.byId("goodboy").frame());
        assertSame(FrameStyles.SLATE, Themes.byId("sesame").frame());
    }

    /** The owner's rule (2026-10-26): an accent replaces the accent roles only, on any chroma. */
    @Test
    void anAccentRecoloursOnlyTheAccentRoles() {
        ThemeChoice plain = new ThemeChoice("soft", "minimalist", ThemeChoice.Variant.LIGHT);
        Themes.choose(new ThemeChoices(null, Map.of("goodboy", plain.withAccent("blue"))));
        Palette blue = Themes.byId("goodboy").palette();
        Themes.choose(new ThemeChoices(null, Map.of("goodboy", plain)));
        Palette bw = Themes.byId("goodboy").palette();

        assertEquals(0xFF18181B, bw.primary());
        assertEquals(0xFF2563EB, blue.primary());
        assertEquals(0xFF2563EB, blue.accent());
        // shadcn blue-600 carries white text (5.17:1)
        assertEquals(0xFFFFFFFF, blue.primaryText());
        assertEquals(bw.surface(), blue.surface());
        assertEquals(bw.text(), blue.text());
        assertEquals(bw.raised(), blue.raised());
        assertEquals(bw.track(), blue.track());
    }
}
