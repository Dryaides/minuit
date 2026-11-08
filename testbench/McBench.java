import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.EntityPreviewComponent;
import dev.dryaides.minuit.EntityView;
import dev.dryaides.minuit.ItemPickerComponent;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.KeybindFieldComponent;
import dev.dryaides.minuit.RichTextComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeFamily;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.Words;
import dev.dryaides.minuit.internal.ThemeFiles;

import site.McScenes;
import site.SiteScenes.Scene;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Minecraft components that stand alone: item picker, entity preview, key binding field, formatted text, toasts.
 * Scripted checks first, then shots in two looks, light and dark; run by {@code testbench/run.sh}, into
 * {@code <shots folder>/mc}.
 */
public class McBench {

    public static void main(String[] args) throws IOException {
        Themes.load(ThemeFiles.assemble(ThemeFiles.fromDirectory(WidgetBench.THEMES, "minuit"), WidgetBench.problems::add));
        File out = new File(args[0]);
        out.mkdirs();
        BenchDraw.McFont font = new BenchDraw.McFont();
        BaseComponent.clock = () -> WidgetBench.now;
        McScenes.words = lang(java.nio.file.Path.of("common/src/main/resources/assets/minuit/lang/en_us.json"));
        Theme first = Themes.defaultTheme();
        checks(font, first);
        int shots = 0;
        for (String look : (args.length > 1 ? args[1] : "bedrock/stone,slate/teal").split(",")) {
            String familyId = look.split("/")[0];
            ThemeFamily family = Themes.family(null, familyId);
            ThemeFamily.Chroma chroma = family.chroma(look.split("/")[1]);
            for (ThemeChoice.Variant variant : List.of(ThemeChoice.Variant.LIGHT, ThemeChoice.Variant.DARK)) {
                Theme theme = new Theme(familyId, chroma.palette(variant), family.frame());
                String name = familyId + "-" + variant.id();
                shots += WidgetBench.shot(font, McScenes.pickerScene(theme, ""), 60, 70, out, "picker-" + name);
                shots += WidgetBench.shot(font, McScenes.pickerScene(theme, "zzz"), -1, -1, out, "picker-empty-" + name);
                shots += WidgetBench.shot(font, McScenes.richScene(theme), 190, 120, out, "rich-" + name);
                shots += WidgetBench.shot(font, McScenes.toastScene(theme), -1, -1, out, "toast-" + name);
                shots += WidgetBench.shot(font, McScenes.entityScene(theme), 50, 80, out, "entity-" + name);
                shots += WidgetBench.shot(font, McScenes.keybindScene(theme), 60, 115, out, "keybind-" + name);
            }
        }
        System.out.println(shots + " shots, " + WidgetBench.problems.size() + " problems");
        WidgetBench.problems.stream().limit(30).forEach(System.out::println);
    }

    /** The kit's lang file as the game reads it: {@code %s} filled from the arguments, an unknown key shown as is. */
    static Words lang(java.nio.file.Path file) throws IOException {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
        json.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
        return (key, args) -> {
            String text = map.get(key);
            return text == null ? key : String.format(text, args);
        };
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            WidgetBench.problems.add("check: " + what);
        }
    }

    /** One frame, so widgets lay out and record what they drew. */
    static void frame(BenchDraw.McFont font, Scene scene) {
        BenchDraw.MockDraw d = new BenchDraw.MockDraw(font, WidgetBench.W, WidgetBench.H);
        scene.draw(d, -100, -100, 1F);
    }

    static void checks(BenchDraw.McFont font, Theme theme) {
        // Picker, counts from the fixture: 9 ids hold "ore" (spore, core and forest included), 10 are demo:.
        List<String> picks = new ArrayList<>();
        Scene ps = new Scene(theme, "Items");
        ItemPickerComponent picker = ps.put(new ItemPickerComponent(ps.place(12, 26, 276, 142), (k, a) -> k,
                McScenes.LOOKUP, picks::add));
        picker.search("ore");
        check(picker.shown().size() == 9, "picker \"ore\" keeps 9, got " + picker.shown().size());
        picker.search("@demo");
        check(picker.shown().size() == 10, "picker @demo keeps 10, got " + picker.shown().size());
        picker.search("ore");
        picker.focused = true;
        picker.keyPressed(dev.dryaides.minuit.InputKey.DOWN, false);
        picker.keyPressed(dev.dryaides.minuit.InputKey.RIGHT, false);
        picker.keyPressed(dev.dryaides.minuit.InputKey.ENTER, false);
        check(picks.equals(List.of("minecraft:iron_ore")), "Down Right Enter picks the second match, got " + picks);

        // Entity: 45 px of drag right is 90 degrees, 210 to 300.
        Scene es = new Scene(theme, "Entity");
        EntityPreviewComponent pig = es.put(new EntityPreviewComponent(es.place(12, 26, 80, 120), EntityView.of("minecraft:pig")));
        int ex = pig.x + 40;
        int ey = pig.y + 60;
        es.mouseClicked(ex, ey, 0);
        es.mouseDragged(ex + 45, ey, 0, 45, 0);
        es.mouseReleased(ex + 45, ey, 0);
        check(Math.abs(pig.yaw - 300F) < 0.01F, "drag 45 px sets yaw 300, got " + pig.yaw);

        // Key binding: click, R binds and onChange runs once; Escape while listening unbinds.
        Scene ks = new Scene(theme, "Keys");
        List<McScenes.DemoBinding> all = new ArrayList<>();
        McScenes.DemoBinding bind = new McScenes.DemoBinding("Map", KeyPress.key(77, 0), all);
        int[] changes = {0};
        KeybindFieldComponent field = ks.put(new KeybindFieldComponent(ks.place(12, 30, 130, 20), (k, a) -> k, bind)
                .onChange(() -> changes[0]++));
        frame(font, ks);
        ks.mouseClicked(field.x + 20, field.y + 10, 0);
        check(field.listening && ks.capturing(), "a click starts listening");
        ks.rawKey(KeyPress.key(82, 0));
        check("R".equals(bind.keyName()) && changes[0] == 1 && !field.listening,
                "R binds once: " + bind.keyName() + ", " + changes[0] + " changes");
        ks.mouseClicked(field.x + 20, field.y + 10, 0);
        ks.rawKey(KeyPress.key(KeybindFieldComponent.ESCAPE, 0));
        check(bind.key == null, "Escape while listening unbinds");
        McScenes.DemoBinding other = new McScenes.DemoBinding("Zoom", KeyPress.key(82, 0), all);
        bind.key = KeyPress.key(82, 0);
        KeybindFieldComponent twin = ks.put(new KeybindFieldComponent(ks.place(12, 60, 130, 20), (k, a) -> a.length > 0 ? k + " " + a[0] : k, other));
        List<String> tip = twin.tooltipAt(twin.x + 20, twin.y + 10);
        check(tip != null && tip.get(0).contains("Map"), "the field on the same key names the other: " + tip);

        // A link click opens its URL through the host's opener.
        List<String> opened = new ArrayList<>();
        RichTextComponent.links = opened::add;
        Scene rs = new Scene(theme, "Rich");
        RichTextComponent rich = rs.put(new RichTextComponent(rs.place(12, 26, 276, 20), "[wiki](https://minecraft.wiki)"));
        frame(font, rs);
        rs.mouseClicked(rich.x + 2, rich.y + 4, 0);
        check(opened.equals(List.of("https://minecraft.wiki")), "a link click opens its URL, got " + opened);
        RichTextComponent.links = url -> { };
    }
}
