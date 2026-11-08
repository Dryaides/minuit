package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.ThemeFamily;
import dev.dryaides.minuit.Themes;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeReloadOrderTest {

    private static final Path ROOT = Path.of("src/main/resources/assets/minuit/minuit");

    /**
     * A pack's {@code chromas/teal.json} in its own namespace replaces minuit's, whatever order the game
     * lists the files in: the {@code minuit} namespace goes first.
     */
    @Test
    void otherNamespaceFileComesAfterMinuitAndWins() throws Exception {
        String teal = Files.readString(ROOT.resolve("chromas/teal.json"));
        String packTeal = teal.replaceFirst("\"frame\": \"#FF8A96A3\"", "\"frame\": \"#FF123456\"");
        List<ThemeFiles.Entry> listed = List.of(
                new ThemeFiles.Entry("pack:minuit/chromas/teal.json", "pack", "chromas/teal.json", packTeal),
                new ThemeFiles.Entry("minuit:minuit/chromas/teal.json", "minuit", "chromas/teal.json", teal),
                new ThemeFiles.Entry("minuit:minuit/themes/slate/family.json", "minuit", "themes/slate/family.json",
                        Files.readString(ROOT.resolve("themes/slate/family.json"))));
        ThemeFiles.reload(listed, s -> { });
        ThemeFamily slate = Themes.families(null).stream().filter(f -> f.id().equals("slate")).findFirst().orElseThrow();
        assertEquals(0xFF123456, slate.chroma("teal").light().frame());
    }
}
