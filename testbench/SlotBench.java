import dev.dryaides.minuit.internal.HostState;
import java.io.IOException;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.ItemSource;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeFamily;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.internal.ThemeFiles;
import dev.dryaides.minuit.internal.showcase.McScenes;
import dev.dryaides.minuit.internal.showcase.SlotScenes;

import java.io.File;
import java.util.List;

/**
 * The inventory parts: slot, slot grid, player inventory, hotbar, in display mode. One shot per look
 * and variant ({@code slots-<look>}), the mouse over a grid cell; the strings are held to their boxes
 * as in {@link WidgetBench}.
 * Run by {@code testbench/run.sh}, into {@code <shots folder>/slot}.
 */
public class SlotBench {

    public static void main(String[] args) throws IOException {
        Themes.load(ThemeFiles.assemble(ThemeFiles.fromDirectory(WidgetBench.THEMES, "minuit"), WidgetBench.problems::add));
        File out = new File(args.length > 0 ? args[0] : "out/slot");
        out.mkdirs();
        BenchDraw.McFont font = new BenchDraw.McFont();
        HostState.clock(() -> WidgetBench.now);
        dev.dryaides.minuit.ItemLookup.plugIn(McScenes.LOOKUP);
        int shots = 0;
        String looks = args.length > 1 ? args[1] : "bedrock/bedrock,slate/teal";
        for (String look : looks.split(",")) {
            String familyId = look.split("/")[0];
            ThemeFamily family = Themes.family(null, familyId);
            ThemeFamily.Chroma chroma = family.chroma(look.split("/")[1]);
            for (ThemeChoice.Variant variant : List.of(ThemeChoice.Variant.LIGHT, ThemeChoice.Variant.DARK)) {
                Theme theme = new Theme(familyId, chroma.palette(variant), family.frame());
                String name = familyId + (chroma == family.first() ? "" : "-" + chroma.id()) + "-" + variant.id();
                SlotScenes.SlotScene scene = SlotScenes.slotsScene(theme);
                shots += WidgetBench.shot(font, scene, SlotScenes.MX, SlotScenes.MY, out, "slots-" + name);
                int mx = (WidgetBench.W - 300) / 2 + SlotScenes.MX;
                int my = (WidgetBench.H - 180) / 2 + SlotScenes.MY;
                ItemView under = ItemSource.under(scene, mx, my);
                WidgetBench.expect(name + " item under the mouse", "minecraft:oak_log", under == null ? null : under.id());
                shots += WidgetBench.shot(font, SlotScenes.hudScene(theme), -1, -1, out, "hud-" + name);
                shots += WidgetBench.shot(font, SlotScenes.equipmentScene(theme), -1, -1, out, "equipment-" + name);
            }
        }
        System.out.println(shots + " shots, " + WidgetBench.problems.size() + " problems");
        WidgetBench.problems.stream().limit(30).forEach(System.out::println);
    }
}
