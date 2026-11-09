package dev.dryaides.minuit.forge;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each generated .lang file, read the way Forge 1.12.2's LanguageMap reads one (split at the first '=',
 * and with a {@code #PARSE_ESCAPES} first line each value unescaped as Java text), gives back exactly the
 * entries of the json it came from.
 */
class LegacyLangTest {

    @Test
    void everyLangFileReadsBackAsItsJson() throws IOException {
        File jsonDir = new File(System.getProperty("minuit.langJson"));
        File langDir = new File(System.getProperty("minuit.langLegacy"));
        File[] jsons = jsonDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertTrue(jsons != null && jsons.length > 0, "no json in " + jsonDir);
        for (File json : jsons) {
            Map<String, String> expected = new Gson().fromJson(
                    new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8),
                    new TypeToken<Map<String, String>>() { }.getType());
            File lang = new File(langDir, json.getName().replace(".json", ".lang"));
            assertEquals(expected, readLikeTheGame(lang), lang.getName());
        }
    }

    private static Map<String, String> readLikeTheGame(File lang) throws IOException {
        List<String> lines = Files.readAllLines(lang.toPath(), StandardCharsets.UTF_8);
        boolean escapes = !lines.isEmpty() && lines.get(0).equals("#PARSE_ESCAPES");
        Map<String, String> read = new HashMap<>();
        for (String line : lines) {
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String value = line.substring(eq + 1);
            read.put(line.substring(0, eq), escapes ? StringEscapeUtils.unescapeJava(value) : value);
        }
        return read;
    }
}
