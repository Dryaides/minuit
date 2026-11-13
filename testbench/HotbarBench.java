import dev.dryaides.minuit.internal.HostState;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.ExpandableHotbarComponent;
import dev.dryaides.minuit.ItemDropTarget;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.ThemeFamily;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.internal.ThemeFiles;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The expandable hotbar over a world backdrop: a bar of wells at the bottom with three rows, a HUD bar at
 * the top with one row held open, and a drop target that takes emeralds only. Shots per look and variant:
 * folded, mid-way (60 ms into the 150 ms unfold), unfolded, and an emerald carried onto the target (cue).
 *   java ... HotbarBench <shots folder> [looks, e.g. vanilla/vanilla,bedrock/bedrock,slate/teal]
 */
public class HotbarBench {

    static final int W = 427;
    static final int H = 240;
    static long now = 10_000;
    static final List<String> problems = new ArrayList<>();

    static final class Scene extends PanelComponent {
        final ExpandableHotbarComponent bottom;
        final ExpandableHotbarComponent top;
        final ItemDropTarget.Drop target;
        ItemView carried;

        Scene(Theme theme) {
            super(theme, (key, args) -> key, 0, 0);
            Box area = new Box("target", 300, 70, 60, 50);
            this.target = new ItemDropTarget.Drop(area, null, item -> item.id().equals("minecraft:emerald"), item -> true);
            this.bottom = add(ExpandableHotbarComponent.bottom(W, H, 3, false))
                    .items(inventory()).selected(2)
                    .targets(() -> List.of(this.target)).carried(() -> this.carried);
            this.top = add(ExpandableHotbarComponent.top(W, 1, true)).items(inventory()).selected(0);
        }

        @Override
        public int width() {
            return W;
        }

        @Override
        public int height() {
            return H;
        }

        @Override
        protected void drawBackground(Draw d, int mx, int my) {
            Palette p = this.theme.palette();
            Box a = this.target.area();
            this.theme.frame().panel(d, p, a.x(), a.y(), a.w(), a.h());
            if (this.bottom.cued(mx, my) == this.target) {
                int c = p.accent();
                d.fill(a.x() - 2, a.y() - 2, a.right() + 2, a.y(), c);
                d.fill(a.x() - 2, a.bottom(), a.right() + 2, a.bottom() + 2, c);
                d.fill(a.x() - 2, a.y(), a.x(), a.bottom(), c);
                d.fill(a.right(), a.y(), a.right() + 2, a.bottom(), c);
            }
        }

        @Override
        protected void drawForeground(Draw d, int mx, int my) {
            if (this.carried != null) {
                d.item(this.carried, mx - 8, my - 8);
            }
        }
    }

    static List<ItemView> inventory() {
        List<ItemView> all = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            all.add(null);
        }
        all.set(0, ItemView.of("minecraft:iron_sword"));
        all.set(1, ItemView.of("minecraft:bread", 12));
        all.set(2, ItemView.of("minecraft:torch", 64));
        all.set(4, ItemView.of("minecraft:emerald", 9));
        all.set(9, ItemView.of("minecraft:diamond"));
        all.set(13, ItemView.of("minecraft:oak_log", 32));
        all.set(20, ItemView.of("minecraft:redstone", 7));
        all.set(27, ItemView.of("minecraft:cobblestone", 64));
        all.set(31, ItemView.of("minecraft:apple", 3));
        all.set(35, ItemView.of("minecraft:iron_ingot", 16));
        return all;
    }

    public static void main(String[] args) throws IOException {
        Themes.load(ThemeFiles.assemble(ThemeFiles.fromDirectory(WidgetBench.THEMES, "minuit"), problems::add));
        File out = new File(args.length > 0 ? args[0] : "out/hotbar");
        out.mkdirs();
        BenchDraw.McFont font = new BenchDraw.McFont();
        HostState.clock(() -> now);
        String looks = args.length > 1 ? args[1] : "vanilla/vanilla,bedrock/bedrock,slate/teal";
        int shots = 0;
        for (String look : looks.split(",")) {
            String familyId = look.split("/")[0];
            ThemeFamily family = Themes.family(null, familyId);
            ThemeFamily.Chroma chroma = family.chroma(look.split("/")[1]);
            for (ThemeChoice.Variant variant : List.of(ThemeChoice.Variant.LIGHT, ThemeChoice.Variant.DARK)) {
                Theme theme = new Theme(familyId, chroma.palette(variant), family.frame());
                String name = familyId + "-" + variant.id();
                int barX = W / 2;
                int barY = H - 9;

                Scene s = new Scene(theme);
                shots += shot(font, s, W / 2, H / 2, 0, out, "hotbar-" + name + "-folded");
                expect(name + " folded: rows away", false, s.bottom.unfolded());

                s = new Scene(theme);
                s.top.hold(true);
                frame(font, s, barX, barY);
                shots += shot(font, s, barX, barY, 60, out, "hotbar-" + name + "-mid");
                expect(name + " mid: moving", true, s.bottom.animating());

                s = new Scene(theme);
                s.top.hold(true);
                frame(font, s, barX, barY);
                shots += shot(font, s, barX, barY, 400, out, "hotbar-" + name + "-unfolded");
                expect(name + " unfolded: at rest", 0, s.bottom.offset());

                s = new Scene(theme);
                s.top.hold(true);
                s.bottom.hold(true);
                s.carried = ItemView.of("minecraft:emerald", 9);
                frame(font, s, 330, 95);
                shots += shot(font, s, 330, 95, 400, out, "hotbar-" + name + "-drop");
                expect(name + " drop: target cued", s.target, s.bottom.cued(330, 95));
            }
        }
        System.out.println(shots + " shots, " + problems.size() + " problems");
        problems.stream().limit(30).forEach(System.out::println);
    }

    private static BenchDraw.MockDraw frame(BenchDraw.McFont font, PanelComponent panel, int mx, int my) {
        BenchDraw.MockDraw d = new BenchDraw.MockDraw(font, W, H);
        d.backdrop();
        panel.draw(d, mx, my, 1F);
        return d;
    }

    private static int shot(BenchDraw.McFont font, PanelComponent panel, int mx, int my, long afterMs, File dir, String name) {
        now += afterMs;
        BenchDraw.MockDraw d = frame(font, panel, mx, my);
        d.save(new File(dir, name + ".png"));
        return 1;
    }

    static void expect(String what, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            problems.add(what + ": expected " + expected + ", got " + actual);
        }
    }
}
