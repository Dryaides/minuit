package dev.dryaides.minuit.internal.formui;

import dev.dryaides.minuit.FrameStyles;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeChoices;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.internal.ThemeFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A form wears the theme the player picked for the mod that sent it, as that mod's own screens do. */
class FormClientThemeTest {

    private static final ThemeChoice SOFT = new ThemeChoice("soft", "sand", ThemeChoice.Variant.DARK);

    @BeforeEach
    void load() throws Exception {
        Themes.load(ThemeFiles.assemble(ThemeFiles.fromDirectory(
                Path.of("src/main/resources/assets/minuit/minuit"), "minuit"), s -> { }));
        Themes.register(Themes.SLATE.as("sesame"));
    }

    @AfterEach
    void reset() {
        Themes.choose(ThemeChoices.NONE);
    }

    @Test
    void aFormTakesItsModsThemeAndItsModsChoice() {
        Themes.choose(ThemeChoices.NONE.with("example", SOFT));

        assertSame(FrameStyles.SLATE, FormClient.theme("sesame:shop").frame());
        assertSame(FrameStyles.SOFT, FormClient.theme("example:shop").frame());
        assertEquals("example", FormClient.theme("example:shop").id());
        // THEMES.md: no choice and no global, the mod wears what it registered; unregistered, the kit default (Vanilla).
        assertSame(FrameStyles.VANILLA, FormClient.theme("other:shop").frame());
    }
}
