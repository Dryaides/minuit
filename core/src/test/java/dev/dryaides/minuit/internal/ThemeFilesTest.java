package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.FrameStyles;
import dev.dryaides.minuit.ThemeChoice;
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
        assertEquals(FrameStyles.SLATE, ThemeFiles.family(read("themes/slate/family.json")).style());
    }

    /**
     * A family's override sets alpha on its own chromas only: a pack family "tinted" (slate shapes) makes teal's light
     * surface #78 see-through and its glass smoke #99, while the shipped slate keeps teal opaque. The glass smoke is
     * #0B0D10 (owner, 2026-11-11), so the glass override keeps that colour under the new alpha.
     */
    @Test
    void familyOverrideSetsAlphaOnItsChromasOnly() throws Exception {
        String tinted = """
                {"style": "slate", "chromas": ["teal"],
                 "override": {"light": {"surface": "#78"}, "glass": {"surface": "#99"}}}""";
        List<ThemeFiles.Entry> entries = new ArrayList<>(ThemeFiles.fromDirectory(ROOT, "minuit"));
        entries.add(new ThemeFiles.Entry("pack:minuit/themes/tinted/family.json", "pack", "themes/tinted/family.json",
                tinted));
        List<String> log = new ArrayList<>();
        List<ThemeFamily> families = ThemeFiles.assemble(entries, log::add);
        assertEquals(List.of(), log);
        ThemeFamily own = families.stream().filter(f -> f.id().equals("tinted")).findFirst().orElseThrow();
        ThemeFamily slate = families.stream().filter(f -> f.id().equals("slate")).findFirst().orElseThrow();
        assertEquals(0x78E7EBEF, own.chroma("teal").light().surface());
        assertEquals(0xFFE7EBEF, slate.chroma("teal").light().surface());
        assertEquals(0x990B0D10, own.chroma("teal").palette(ThemeChoice.Variant.GLASS).surface());
    }

    @Test
    void shortColourIsOpaque() throws Exception {
        String text = read("chromas/lilac.json").replace("\"#FF281E3A\"", "\"#281E3A\"");
        assertEquals(0xFF281E3A, ThemeFiles.chroma(text).light().text());
    }

    @Test
    void missingRoleSkipsTheFileAndNamesTheField() throws Exception {
        String text = read("chromas/lilac.json").replaceFirst("\"textSoft\": \"#[0-9A-F]+\",\s*", "");
        ThemeFiles.BadFile bad = assertThrows(ThemeFiles.BadFile.class, () -> ThemeFiles.chroma(text));
        assertEquals("variants.light.textSoft: missing", bad.getMessage());

        List<String> log = new ArrayList<>();
        List<ThemeFiles.Entry> entries = List.of(
                new ThemeFiles.Entry("pack:minuit/themes/tinted/family.json", "pack", "themes/tinted/family.json",
                        "{\"style\": \"slate\", \"chromas\": [\"teal\", \"lilac\"]}"),
                new ThemeFiles.Entry("pack:minuit/chromas/teal.json", "pack", "chromas/teal.json", read("chromas/teal.json")),
                new ThemeFiles.Entry("pack:minuit/chromas/lilac.json", "pack", "chromas/lilac.json", text));
        List<ThemeFamily> families = ThemeFiles.assemble(entries, log::add);
        assertEquals(1, families.size());
        assertEquals("teal", families.get(0).first().id());
        assertNull(families.get(0).chroma("lilac"));
        assertEquals(List.of("minuit theme file pack:minuit/chromas/lilac.json skipped: variants.light.textSoft: missing",
                "minuit theme family tinted: recommended chroma lilac not found"), log);
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
