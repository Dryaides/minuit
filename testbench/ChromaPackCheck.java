import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dryaides.minuit.ThemeFamily;
import dev.dryaides.minuit.internal.ThemeFiles;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Loads a resource pack the site's theme composer exported the way the game does (kit files first,
 * then the pack's) and fails unless every family offers its chroma with both halves, no file is
 * skipped, and pack.mcmeta covers the resource formats of 26.1.2 to 26.3 (gradle/versions).
 *   java ChromaPackCheck <pack.zip>    from the repository root, classpath as in run.sh
 */
public final class ChromaPackCheck {
    public static void main(String[] args) throws Exception {
        List<ThemeFiles.Entry> entries = new ArrayList<>(ThemeFiles.fromDirectory(Path.of("core/src/main/resources/assets/minuit/minuit"), "minuit"));
        String mcmeta = null;
        String chromaId = null;
        String chromaText = null;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(Path.of(args[0])))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                String text = read(zip);
                String[] p = e.getName().split("/", 4);
                if (e.getName().equals("pack.mcmeta")) {
                    mcmeta = text;
                } else if (p.length == 4 && p[0].equals("assets") && p[2].equals(ThemeFiles.ROOT)) {
                    entries.add(new ThemeFiles.Entry(p[1] + ":" + ThemeFiles.ROOT + "/" + p[3] + " (composer pack)", p[1], p[3], text));
                    if (p[3].startsWith("chromas/")) {
                        chromaId = p[3].substring("chromas/".length(), p[3].length() - ".json".length());
                        chromaText = text;
                    }
                }
            }
        }
        List<String> log = new ArrayList<>();
        List<ThemeFamily> families = ThemeFiles.assemble(entries, log::add);
        int fails = 0;
        for (String line : log) {
            System.out.println("FAIL log: " + line);
            fails++;
        }
        ThemeFiles.ChromaFile file = ThemeFiles.chroma(chromaText);
        int accent = Integer.parseUnsignedInt(JsonParser.parseString(chromaText).getAsJsonObject()
                .getAsJsonObject("variants").getAsJsonObject("light").get("accent").getAsString().substring(1), 16);
        if (file.light().accent() != accent) {
            System.out.println("FAIL light accent read as " + Integer.toHexString(file.light().accent()));
            fails++;
        }
        String wanted = chromaId;
        for (ThemeFamily f : families) {
            ThemeFamily.Chroma c = f.chromas().stream().filter(x -> x.id().equals(wanted)).findFirst().orElse(null);
            if (c == null || c.light() == null || c.dark() == null) {
                System.out.println("FAIL family " + f.id() + " does not offer " + chromaId + " with both halves");
                fails++;
            }
        }
        int max = 0;
        try (var versions = Files.list(Path.of("gradle/versions"))) {
            for (Path v : versions.toList()) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(v)) {
                    props.load(in);
                }
                int top = Integer.parseInt(props.getProperty("pack_max_format"));
                // A version whose jar format is the data one notes its resources format: "resources 97".
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("resources ([0-9]+)").matcher(Files.readString(v));
                max = Math.max(max, m.find() ? Integer.parseInt(m.group(1)) : top);
            }
        }
        JsonObject jar = JsonParser.parseString(Files.readString(Path.of("common/src/main/resources/pack.mcmeta"))
                .replace("${pack_max_format}", "0").replace("${mod_name}", "x")).getAsJsonObject().getAsJsonObject("pack");
        JsonObject pack = JsonParser.parseString(mcmeta).getAsJsonObject().getAsJsonObject("pack");
        if (pack.get("min_format").getAsInt() != jar.get("min_format").getAsInt() || pack.get("max_format").getAsInt() != max) {
            System.out.println("FAIL pack.mcmeta formats " + pack + ", jar declares min " + jar.get("min_format") + " max " + max);
            fails++;
        }
        System.out.println(fails == 0 ? "OK " + chromaId + " offered by " + families.size() + " families, formats "
                + pack.get("min_format") + " to " + max : fails + " failures");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        in.transferTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
