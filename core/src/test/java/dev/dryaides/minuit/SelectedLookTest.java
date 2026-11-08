package dev.dryaides.minuit;

import dev.dryaides.minuit.internal.ThemeFiles;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedLookTest {

    @BeforeAll
    static void load() throws Exception {
        Themes.load(ThemeFiles.assemble(ThemeFiles.fromDirectory(
                Path.of("src/main/resources/assets/minuit/minuit"), "minuit"), s -> { }));
    }

    private static Palette palette(String family, String chroma, ThemeChoice.Variant variant) {
        return Themes.family("minuit", family).chroma(chroma).palette(variant);
    }

    /** The tiles where no palette text role reached 4.5:1 in _temp/selected_state/contrast.txt. */
    @Test
    void fallbackPassesWhereEveryRoleFailed() {
        record Tile(String family, String chroma, ThemeChoice.Variant variant, float tint) { }
        List<Tile> failing = List.of(
                new Tile("bedrock", "bedrock", ThemeChoice.Variant.LIGHT, 0.20F),
                new Tile("bedrock", "bedrock", ThemeChoice.Variant.LIGHT, 0.35F),
                new Tile("bedrock", "bedrock", ThemeChoice.Variant.LIGHT, 0.50F),
                new Tile("glass", "clear", ThemeChoice.Variant.DARK, 0.35F),
                new Tile("soft", "grey", ThemeChoice.Variant.DARK, 0.50F));
        for (Tile t : failing) {
            Palette p = palette(t.family(), t.chroma(), t.variant());
            int face = Shapes.mix(p.raised(), p.accent(), t.tint());
            double text = Shapes.contrast(Shapes.textOn(p, face), face);
            assertTrue(text >= 4.5, t + " text contrast " + text);
        }
    }

    @Test
    void selectedTextReadsInEveryFamily() {
        for (String famId : List.of("bedrock", "slate", "glass", "bubbly", "soft")) {
            ThemeFamily fam = Themes.family("minuit", famId);
            for (ThemeChoice.Variant v : ThemeChoice.Variant.values()) {
                Palette p = fam.first().palette(v);
                for (float hover : new float[]{0F, 1F}) {
                    int face = Shapes.selectedFace(p, hover);
                    double c = Shapes.contrast(Shapes.textOn(p, face), face);
                    assertTrue(c >= 4.5, famId + " " + v + " hover " + hover + ": " + c);
                }
            }
        }
    }
}
