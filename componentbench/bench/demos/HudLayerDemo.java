package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.HudAnchor;
import dev.dryaides.minuit.HudBar;
import dev.dryaides.minuit.HudWidget;
import dev.dryaides.minuit.HudCounter;
import dev.dryaides.minuit.HudLayer;
import dev.dryaides.minuit.HudPanel;
import dev.dryaides.minuit.ProgressComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.VanillaHud;

/**
 * HUD parts over a fake game frame: the vanilla HUD as grey silhouettes (the boxes {@link VanillaHud} keeps clear),
 * minuit's parts placed around them. One state per GUI size of a 1920 by 1080 window: render the "gui scale N"
 * state with {@code --scale N} to get the window's real pixels.
 */
public final class HudLayerDemo implements Demo {

    private static final int SILHOUETTE = 0x70202020;
    private static final int SILHOUETTE_EDGE = 0x90FFFFFF;

    @Override
    public void states(Stage stage) {
        frame(stage, "gui scale 2", 960, 540, false);
        frame(stage, "gui scale 3", 640, 360, false);
        frame(stage, "gui scale 4", 480, 270, false);
        frame(stage, "busy hud scale 3", 640, 360, true);
    }

    private static void frame(Stage stage, String name, int w, int h, boolean busy) {
        stage.state(name, w, h, s -> {
            s.noSurface();
            VanillaHud hud = new VanillaHud();
            hud.guiWidth = w;
            hud.guiHeight = h;
            hud.offhand = -1;
            if (busy) {
                hud.healthRows = VanillaHud.healthRows(20F, 16F);
                hud.armour = true;
                hud.air = true;
                hud.bossBars = 2;
                hud.goodEffects = 3;
                hud.badEffects = 1;
                hud.subtitles = true;
                hud.sidebar = true;
            }
            HudLayer layer = new HudLayer();
            layer.add(new HudBar("demo", "mana", HudAnchor.TOP_LEFT, 90).label("Mana").text("34/50")
                    .tone(HudBar.Tone.ACCENT).value(0.68F));
            layer.add(new HudBar("demo", "thirst", HudAnchor.BOTTOM_CENTER, 81).label("Thirst")
                    .icon("minecraft:potion").tone(HudBar.Tone.PRIMARY).value(0.45F));
            layer.add(new HudCounter("demo", "arrows", HudAnchor.BOTTOM_RIGHT, "minecraft:arrow").count(23));
            layer.add(new HudCounter("demo", "souls", HudAnchor.TOP_RIGHT, "minecraft:soul_lantern").count(64).max(64));
            layer.add(new HudPanel("demo", "quest", HudAnchor.MIDDLE_LEFT, 3).title("Quest")
                    .line(0, "Gather 12 oak logs").line(1, "7 / 12").line(2, "Back to the village"));
            layer.add(new HudPanel("demo", "kills", HudAnchor.BOTTOM_LEFT, 2).title("Arena")
                    .line(0, "Wave 3 of 5"));
            ProgressComponent raid = new ProgressComponent(new Box("raid", 0, 0, 120, 6));
            raid.fraction = 0.3F;
            layer.add(new HudWidget<>("demo", "raid", HudAnchor.TOP_CENTER, raid));
            s.paint((d, t) -> {
                world(d, w, h);
                layer.themes(id -> t).render(d, hud, 0F);
                silhouettes(d, hud);
            });
        });
    }

    private static void world(Draw d, int w, int h) {
        int horizon = h * 11 / 20;
        d.gradient(0, 0, w, horizon, 0xFF7FA8F0, 0xFF7FA8F0, 0xFFB9D2F7, 0xFFB9D2F7);
        d.fill(0, horizon, w, horizon + 4, 0xFF5E9E3A);
        d.gradient(0, horizon + 4, w, h, 0xFF7A5634, 0xFF7A5634, 0xFF4E3620, 0xFF4E3620);
    }

    /** Drawn after the parts, as outlines, so an overlap would show. */
    private static void silhouettes(Draw d, VanillaHud hud) {
        for (int i = 0; i < hud.count(); i++) {
            int x0 = hud.x0(i);
            int y0 = hud.y0(i);
            int x1 = hud.x1(i);
            int y1 = hud.y1(i);
            d.fill(x0, y0, x1, y1, SILHOUETTE);
            d.fill(x0, y0, x1, y0 + 1, SILHOUETTE_EDGE);
            d.fill(x0, y1 - 1, x1, y1, SILHOUETTE_EDGE);
            d.fill(x0, y0, x0 + 1, y1, SILHOUETTE_EDGE);
            d.fill(x1 - 1, y0, x1, y1, SILHOUETTE_EDGE);
            String label = hud.kind(i).name().toLowerCase();
            if (d.width(label) + 4 <= x1 - x0 && y1 - y0 >= 10) {
                d.text(label, x0 + 2, y0 + (y1 - y0 - 8) / 2 + 1, 0xFFFFFFFF);
            }
        }
    }
}
