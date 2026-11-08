package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.FrameStyles;
import dev.dryaides.minuit.ThemeFamily;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeFilesTest {

    private static final Path ROOT = Path.of("src/main/resources/assets/minuit/minuit");

    private static String read(String file) throws IOException {
        return Files.readString(ROOT.resolve(file));
    }

    /** Slate's dark half is the old Java preset SLATE; its values are the ones the file spells out. */
    @Test
    void validFileGivesTheColoursItSpells() throws Exception {
        ThemeFiles.ChromaFile slate = ThemeFiles.chroma(read("chromas/teal.json"));
        assertEquals(0xFF2A3038, slate.dark().frame());
        assertEquals(0xFFE6E9EC, slate.dark().text());
        assertEquals(0x66000000, slate.dark().shadow());
        assertEquals(0xFF8A96A3, slate.light().frame());
        assertEquals(FrameStyles.GLASS, ThemeFiles.family(read("themes/glass/family.json")).style());
    }

    /**
     * Glass's translucency is its family's override, not the chroma's: clear glass keeps the
     * #78ECF6FA surface (owner review: more see-through), and the same chroma on slate is opaque.
     */
    @Test
    void familyOverrideSetsAlphaOnItsChromasOnly() throws Exception {
        List<String> log = new ArrayList<>();
        List<ThemeFamily> families = ThemeFiles.assemble(ThemeFiles.fromDirectory(ROOT, "minuit"), log::add);
        assertEquals(List.of(), log);
        ThemeFamily glass = families.stream().filter(f -> f.id().equals("glass")).findFirst().orElseThrow();
        ThemeFamily slate = families.stream().filter(f -> f.id().equals("slate")).findFirst().orElseThrow();
        assertEquals(List.of("clear", "green"), glass.recommended().stream().map(ThemeFamily.Chroma::id).toList());
        assertEquals(0x78ECF6FA, glass.chroma("clear").light().surface());
        assertEquals(0x7A0C161C, glass.chroma("clear").dark().surface());
        assertEquals(0xFFECF6FA, slate.chroma("clear").light().surface());
        assertEquals(0x26000000, slate.chroma("clear").light().shadow());
    }

    @Test
    void shortColourIsOpaque() throws Exception {
        String text = read("chromas/lilac.json").replace("\"#FF281E3A\"", "\"#281E3A\"");
        assertEquals(0xFF281E3A, ThemeFiles.chroma(text).light().text());
    }

    @Test
    void missingRoleSkipsTheFileAndNamesTheField() throws Exception {
        String text = read("chromas/green.json").replaceFirst("\"textSoft\": \"#[0-9A-F]+\",\s*", "");
        ThemeFiles.BadFile bad = assertThrows(ThemeFiles.BadFile.class, () -> ThemeFiles.chroma(text));
        assertEquals("variants.light.textSoft: missing", bad.getMessage());

        List<String> log = new ArrayList<>();
        List<ThemeFiles.Entry> entries = List.of(
                new ThemeFiles.Entry("pack:minuit/themes/glass/family.json", "pack", "themes/glass/family.json",
                        read("themes/glass/family.json")),
                new ThemeFiles.Entry("pack:minuit/chromas/clear.json", "pack", "chromas/clear.json", read("chromas/clear.json")),
                new ThemeFiles.Entry("pack:minuit/chromas/green.json", "pack", "chromas/green.json", text));
        List<ThemeFamily> families = ThemeFiles.assemble(entries, log::add);
        assertEquals(1, families.size());
        assertEquals("clear", families.get(0).first().id());
        assertNull(families.get(0).chroma("green"));
        assertEquals(List.of("minuit theme file pack:minuit/chromas/green.json skipped: variants.light.textSoft: missing",
                "minuit theme family glass: recommended chroma green not found"), log);
    }

    /** A chroma file in a family folder, the layout before shared chromas, still joins that family. */
    @Test
    void oldFamilyFolderChromaStillLoads() throws Exception {
        String moss = read("chromas/lilac.json");
        List<ThemeFiles.Entry> entries = List.of(
                new ThemeFiles.Entry("pack:minuit/themes/moss/family.json", "pack", "themes/moss/family.json",
                        "{\"style\": \"slate\"}"),
                new ThemeFiles.Entry("pack:minuit/themes/moss/default.json", "pack", "themes/moss/default.json", moss),
                new ThemeFiles.Entry("pack:minuit/chromas/teal.json", "pack", "chromas/teal.json", read("chromas/teal.json")));
        ThemeFamily family = ThemeFiles.assemble(entries, s -> { }).get(0);
        assertEquals("default", family.first().id());
        assertEquals("minuit.chroma.moss.default", family.first().nameKey());
        assertEquals(List.of("teal"), family.others().stream().map(ThemeFamily.Chroma::id).toList());
    }

    @Test
    void badColourNamesTheField() throws Exception {
        String text = read("chromas/cream.json").replace("\"accent\": \"#FFF0A04B\"", "\"accent\": \"#F0A04G\"");
        ThemeFiles.BadFile bad = assertThrows(ThemeFiles.BadFile.class, () -> ThemeFiles.chroma(text));
        assertTrue(bad.getMessage().startsWith("variants.dark.accent: "), bad.getMessage());
    }
}
