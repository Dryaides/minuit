package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ThemeFiles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldInkTest {

    @BeforeAll
    static void load() throws Exception {
        Themes.load(ThemeFiles.assemble(ThemeFiles.fromDirectory(
                Path.of("src/main/resources/assets/minuit/minuit"), "minuit"), s -> { }));
    }

    /**
     * Owner's rule from the in-game review: field text is light on every family and brightness, on a
     * face dark enough for WCAG AA (4.5:1), so the game's drop shadow under a vanilla edit box sharpens it.
     */
    @Test
    void fieldTextIsLightAndReadsInEveryChroma() {
        for (String famId : List.of("bedrock", "slate", "glass", "bubbly", "soft")) {
            ThemeFamily fam = Themes.family("minuit", famId);
            for (ThemeFamily.Chroma chroma : fam.chromas()) {
                for (ThemeChoice.Variant v : List.of(ThemeChoice.Variant.LIGHT, ThemeChoice.Variant.DARK)) {
                    if (!chroma.has(v)) {
                        continue;
                    }
                    Palette p = chroma.palette(v);
                    int text = fam.frame().fieldText(p);
                    int face = FieldInk.face(p);
                    String at = famId + "/" + chroma.id() + " " + v;
                    assertTrue(Shapes.luminance(text) > 0.5, at + ": text not light");
                    assertTrue(face >>> 24 == 0xFF, at + ": face not opaque");
                    assertTrue(Shapes.contrast(text, face) >= 4.5, at + ": " + Shapes.contrast(text, face));
                }
            }
        }
    }
}
