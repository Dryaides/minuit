package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.FrameStyles;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeFamily;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ThemePackExportTest {

    private static final Path KIT = Path.of("src/main/resources/assets/minuit/minuit");

    /**
     * The owner's case: a player on slate, teal, dark, rose accent exports "Night Rose" and a friend enables the pack.
     * The friend's picker then offers a family "night_rose" in slate's shapes whose one chroma wears exactly the
     * colours the player saw, dark half only.
     */
    @Test
    void exportedPackLoadsBackAsTheThemeThePlayerSaw(@TempDir Path dir) throws IOException {
        List<ThemeFamily> kit = ThemeFiles.assemble(ThemeFiles.fromDirectory(KIT, "minuit"), line -> { });
        ThemeFamily slate = kit.stream().filter(f -> f.id().equals("slate")).findFirst().orElseThrow();
        ThemeFamily.Chroma teal = slate.chroma("teal");
        ThemeChoice choice = new ThemeChoice("slate", "teal", ThemeChoice.Variant.DARK, "rose");
        Palette seen = Accent.apply(teal.palette(ThemeChoice.Variant.DARK), "rose");

        Map<String, String> files = ThemePackExport.files("Night Rose", slate, teal, choice);
        Path assets = dir.resolve("assets/" + ThemePackExport.NAMESPACE + "/" + ThemeFiles.ROOT);
        for (Map.Entry<String, String> f : files.entrySet()) {
            Path out = dir.resolve(f.getKey());
            Files.createDirectories(out.getParent());
            Files.writeString(out, f.getValue());
        }

        List<ThemeFiles.Entry> entries = new ArrayList<>(ThemeFiles.fromDirectory(KIT, "minuit"));
        entries.addAll(ThemeFiles.fromDirectory(assets, ThemePackExport.NAMESPACE));
        List<String> log = new ArrayList<>();
        ThemeFamily back = ThemeFiles.assemble(entries, log::add).stream()
                .filter(f -> f.id().equals("night_rose")).findFirst().orElseThrow();
        assertEquals(List.of(), log);
        assertEquals(FrameStyles.SLATE, back.frame());
        ThemeFamily.Chroma chroma = back.first();
        assertEquals(seen, chroma.dark());
        assertNull(chroma.light());
    }
}
