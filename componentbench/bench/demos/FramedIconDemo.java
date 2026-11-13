package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.AtlasIcon;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.FramedIcon;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.IconTileComponent;
import dev.dryaides.minuit.SideRailComponent;
import dev.dryaides.minuit.SideRailComponent.Edge;

/** Framed icons in the Bedrock settings-list style: game textures and glyphs, in tiles and on a side rail. */
public final class FramedIconDemo implements Demo {

    private static final Box PANEL = new Box("panel", 34, 8, 150, 104);

    private static FramedIcon tex(String path) {
        return FramedIcon.of(AtlasIcon.whole("minecraft:textures/" + path + ".png", 16));
    }

    @Override
    public void states(Stage stage) {
        stage.state("tiles", 196, 56, s -> {
            s.add(new IconTileComponent(new Box("a", 8, 8, 40, 40), "General", () -> { })
                    .icon(FramedIcon.of(Icon.SETTINGS)));
            s.add(new IconTileComponent(new Box("b", 54, 8, 40, 40), "Furnace", () -> { })
                    .icon(tex("block/furnace_front")));
            s.add(new IconTileComponent(new Box("c", 100, 8, 40, 40), "Chest", () -> { })
                    .icon(tex("item/chest_minecart")));
            s.add(new IconTileComponent(new Box("d", 146, 8, 40, 40), "Cheats", () -> { })
                    .icon(tex("block/command_block_front")));
        });
        stage.state("side rail", 200, 120, s -> {
            s.noSurface();
            s.paint((d, theme) -> theme.frame().panel(d, theme.palette(), PANEL.x(), PANEL.y(), PANEL.w(), PANEL.h()));
            SideRailComponent r = new SideRailComponent(PANEL, Edge.LEFT)
                    .action(FramedIcon.of(Icon.SETTINGS), "General", () -> { })
                    .action(tex("block/furnace_front"), "Game", () -> { })
                    .action(tex("block/structure_block"), "Structures", () -> { })
                    .action(tex("block/command_block_front"), "Cheats", () -> { });
            r.selected(1, true);
            s.add(r);
        });
    }
}
