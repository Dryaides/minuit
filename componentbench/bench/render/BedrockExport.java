package bench.render;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.dryaides.minuit.FrameStyle;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.Shapes;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeFamily;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.ToggleComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * The form pieces of every family x chroma x variant as nine-slice PNGs for minuit's Bedrock resource pack, drawn by
 * the same FrameStyle code the Java screens use, at GUI scale 1 (one Bedrock UI unit per pixel). Writes
 * {@code <out>/<family>.<chroma>.<variant>/<part>.png} and {@code <out>/index.json}: per look and part, the sprite
 * size, its nine-slice insets (left, top, right, bottom) and its outset, what it draws outside its box.
 * mod-bedrock/tools/gen_theme.py --art reads them. Args: outDir.
 */
public final class BedrockExport {

    private record Part(String name, int w, int h, boolean fixed, Painter painter) {
    }

    private interface Painter {
        void paint(FrameStyle s, Palette p, dev.dryaides.minuit.Draw d, int x, int y, int w, int h);
    }

    // Box sizes follow the Bedrock templates (gen_theme.py): a 26-high form button, a 10-high toggle track, a 4-high
    // slider groove; a rounded part exported at another height would carry the wrong corners.
    private static List<Part> parts(Palette p) {
        List<Part> out = new ArrayList<>();
        out.add(new Part("panel", 60, 60, false, (s, pp, d, x, y, w, h) -> s.panel(d, pp, x, y, w, h)));
        out.add(new Part("field", 60, 20, false, (s, pp, d, x, y, w, h) -> s.field(d, pp, x, y, w, h, false)));
        out.add(new Part("field_focused", 60, 20, false, (s, pp, d, x, y, w, h) -> s.field(d, pp, x, y, w, h, true)));
        String[] roles = {"raised", "raised_hover", "primary", "primary_hover", "destructive", "destructive_hover"};
        int[] faces = {p.raised(), p.raisedHover(), p.primary(), p.primaryHover(), p.destructive(), p.destructiveHover()};
        for (int i = 0; i < roles.length; i++) {
            int c = faces[i];
            out.add(new Part("button." + roles[i], 60, 26, false,
                    (s, pp, d, x, y, w, h) -> s.raised(d, pp, x, y, w, h, c, Shapes.Relief.RAISED)));
            if (!roles[i].endsWith("_hover")) {
                out.add(new Part("button_pressed." + roles[i], 60, 26, false,
                        (s, pp, d, x, y, w, h) -> s.raised(d, pp, x, y, w, h, c, Shapes.Relief.PRESSED)));
            }
        }
        // ToggleComponent's track colours at rest and fully hovered, its knob face and size.
        int[][] tracks = {{0, p.wellEdge()}, {1, p.raisedHover()}, {2, Shapes.mix(p.accentDeep(), p.accent(), 0.5F)}, {3, p.accent()}};
        String[] trackNames = {"toggle.off", "toggle.off_hover", "toggle.on", "toggle.on_hover"};
        for (int[] t : tracks) {
            int c = t[1];
            out.add(new Part(trackNames[t[0]], ToggleComponent.SWITCH, 10, false,
                    (s, pp, d, x, y, w, h) -> s.track(d, pp, x, y, w, h, c)));
        }
        out.add(new Part("toggle.knob", 8, 8, true,
                (s, pp, d, x, y, w, h) -> s.toggleKnob(d, pp, x, y, 0xFFF7F7F7, true, false)));
        out.add(new Part("toggle.knob_lit", 8, 8, true,
                (s, pp, d, x, y, w, h) -> s.toggleKnob(d, pp, x, y, 0xFFF7F7F7, true, true)));
        // SliderComponent's groove: the well edge under, the accent fill over, the knob.
        out.add(new Part("slider.track", 60, 4, false, (s, pp, d, x, y, w, h) -> s.track(d, pp, x, y, w, h, pp.wellEdge())));
        out.add(new Part("slider.fill", 60, 4, false, (s, pp, d, x, y, w, h) -> s.track(d, pp, x, y, w, h, pp.accent())));
        out.add(new Part("slider.knob", 7, 8, true, (s, pp, d, x, y, w, h) -> s.knob(d, pp, x, y, false)));
        out.add(new Part("slider.knob_lit", 7, 8, true, (s, pp, d, x, y, w, h) -> s.knob(d, pp, x, y, true)));
        return out;
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Path assets = Path.of(System.getProperty("bench.assets"));
        String mcJar = System.getProperty("bench.mcjar", "");
        dev.dryaides.minuit.internal.ImageFamily.install(new BenchImages(new Textures(assets, mcJar)));
        Main.loadThemes(assets);

        JsonObject looks = new JsonObject();
        int sprites = 0;
        for (ThemeFamily family : Themes.families("bench")) {
            for (ThemeFamily.Chroma chroma : family.chromas()) {
                for (ThemeChoice.Variant v : List.of(ThemeChoice.Variant.LIGHT, ThemeChoice.Variant.DARK,
                        ThemeChoice.Variant.GLASS)) {
                    if (!chroma.has(v)) {
                        continue;
                    }
                    String look = family.id() + "." + chroma.id() + "." + v.id();
                    Theme theme = Themes.resolve(Themes.defaultTheme(), new ThemeChoice(family.id(), chroma.id(), v));
                    Path dir = out.resolve(look);
                    Files.createDirectories(dir);
                    JsonObject entry = new JsonObject();
                    for (Part part : parts(theme.palette())) {
                        ImageFamilyProof.Cut c = ImageFamilyProof.cut(look + " " + part.name,
                                (d, x, y) -> part.painter.paint(theme.frame(), theme.palette(), d, x, y, part.w, part.h),
                                part.w, part.h, part.fixed);
                        ImageIO.write(c.sprite(), "png", dir.resolve(part.name + ".png").toFile());
                        JsonObject o = new JsonObject();
                        o.add("size", ints(c.sprite().getWidth(), c.sprite().getHeight()));
                        o.add("nineslice", ints(c.left(), c.top(), c.right(), c.bottom()));
                        o.add("outset", ints(c.ol(), c.ot(), c.or(), c.ob()));
                        entry.add(part.name, o);
                        sprites++;
                    }
                    looks.add(look, entry);
                }
            }
        }
        JsonObject index = new JsonObject();
        index.addProperty("scale", 1);
        index.add("looks", looks);
        Files.writeString(out.resolve("index.json"), new GsonBuilder().setPrettyPrinting().create().toJson(index),
                StandardCharsets.UTF_8);
        System.out.println("bedrock art: " + looks.size() + " looks, " + sprites + " sprites in " + out);
    }

    private static JsonArray ints(int... v) {
        JsonArray a = new JsonArray();
        for (int i : v) {
            a.add(i);
        }
        return a;
    }
}
