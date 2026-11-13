package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.AtlasIcon;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.SideRailComponent;
import dev.dryaides.minuit.SideRailComponent.Edge;

public final class SideRailDemo implements Demo {

    private static final Box PANEL = new Box("panel", 30, 8, 120, 104);

    @Override
    public void states(Stage stage) {
        stage.state("rest", 180, 120, s -> rail(s, Edge.LEFT));
        stage.state("hover", 180, 120, s -> {
            rail(s, Edge.LEFT);
            s.move(22, 14 + 17 * 3 + 8);
        });
        stage.state("locked on", 180, 120, s -> {
            rail(s, Edge.LEFT);
            s.click(20, 14 + 17 * 2);
            s.move(170, 115);
        });
        stage.state("keyboard", 180, 120, s -> {
            rail(s, Edge.LEFT);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
        });
        stage.state("right edge", 180, 120, s -> {
            SideRailComponent r = rail(s, Edge.RIGHT);
            r.selected(1, true).disabled(3, true, "Nothing copied yet");
            s.move(130, 14 + 17 * 3 + 8);
        });
        stage.state("png icons", 180, 120, s -> {
            s.noSurface();
            s.paint((d, theme) -> theme.frame().panel(d, theme.palette(), PANEL.x(), PANEL.y(), PANEL.w(), PANEL.h()));
            SideRailComponent r = new SideRailComponent(PANEL, Edge.LEFT)
                    .action(AtlasIcon.whole("minecraft:textures/item/cake.png", 16), "Home", () -> { })
                    .action(AtlasIcon.whole("minecraft:textures/item/writable_book.png", 16), "Orders", () -> { })
                    .action(AtlasIcon.whole("minecraft:textures/item/iron_pickaxe.png", 16), "Work", () -> { })
                    .action(AtlasIcon.whole("minecraft:textures/item/leather_chestplate.png", 16), "Style", () -> { });
            r.selected(0, true);
            s.add(r);
            s.move(22, 14 + 17 * 2 + 8);
        });
    }

    private static SideRailComponent rail(Scene s, Edge edge) {
        Box panel = edge == Edge.LEFT ? PANEL : PANEL.offset(-28, 0);
        s.noSurface();
        s.paint((d, theme) -> theme.frame().panel(d, theme.palette(), panel.x(), panel.y(), panel.w(), panel.h()));
        SideRailComponent[] self = new SideRailComponent[1];
        self[0] = new SideRailComponent(panel, edge)
                .action(Icon.SORT, "Sort", () -> { })
                .action(Icon.PALETTE, "Theme", () -> { })
                .toggle(Icon.UNLOCK, "Lock layout", false,
                        on -> self[0].look(2, on ? Icon.LOCK : Icon.UNLOCK, on ? "Unlock layout" : "Lock layout"))
                .action(Icon.CLIPBOARD, "Copy layout", () -> { })
                .action(Icon.WRENCH, "Settings", () -> { });
        return s.add(self[0]);
    }
}
