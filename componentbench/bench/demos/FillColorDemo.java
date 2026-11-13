package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.FillColor;
import dev.dryaides.minuit.HudAnchor;
import dev.dryaides.minuit.HudBar;
import dev.dryaides.minuit.HudLayer;
import dev.dryaides.minuit.ProgressComponent;
import dev.dryaides.minuit.VanillaHud;

/** Bars whose fill colour follows the value: bad under a quarter, warning under a half, the accent above. */
public final class FillColorDemo implements Demo {

    private static final FillColor ZONES = FillColor.zones(new float[] {0.25F, 0.5F},
            HudBar.Tone.BAD, HudBar.Tone.WARN, HudBar.Tone.ACCENT);
    private static final float[] VALUES = {0.15F, 0.4F, 0.85F};

    @Override
    public void states(Stage stage) {
        stage.state("progress by value", 160, 60, s -> {
            for (int i = 0; i < VALUES.length; i++) {
                ProgressComponent bar = new ProgressComponent(new Box("bar" + i, 10, 10 + 16 * i, 140, 8));
                bar.fraction = VALUES[i];
                bar.fillColor = ZONES;
                s.add(bar);
            }
        });
        stage.state("hud bars by value", 240, 120, s -> {
            s.noSurface();
            VanillaHud hud = new VanillaHud();
            hud.guiWidth = 240;
            hud.guiHeight = 120;
            hud.offhand = -1;
            HudLayer layer = new HudLayer();
            String[] items = {"minecraft:cooked_beef", "minecraft:potion", "minecraft:cake"};
            String[] names = {"Hunger", "Thirst", "Mood"};
            for (int i = 0; i < VALUES.length; i++) {
                layer.add(new HudBar("demo", "need" + i, HudAnchor.TOP_LEFT, 110).label(names[i])
                        .text(Math.round(VALUES[i] * 20) + "/20").icon(items[i]).fillColor(ZONES).value(VALUES[i]));
            }
            s.paint((d, t) -> {
                d.fill(0, 0, 240, 120, 0xFF7FA8F0);
                layer.themes(id -> t).render(d, hud, 0F);
            });
        });
    }
}
