package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.GaugeComponent;
import java.util.List;

/** The gauge, driven by scripted input. */
public final class GaugeDemo implements Demo {
    private static final int W = 110;
    private static final int H = 86;

    private static GaugeComponent load(double value) {
        return new GaugeComponent(new Box("g", 6, 6, W - 12, H - 12), 0, 100, value).zones(60, 85).label("Load").unit("%");
    }

    @Override
    public void states(Stage stage) {
        stage.state("good zone", W, H, s -> {
            GaugeComponent c = s.add(load(42));
        });

        stage.state("warn zone", W, H, s -> {
            GaugeComponent c = s.add(load(72));
        });

        stage.state("bad zone", W, H, s -> {
            GaugeComponent c = s.add(load(96));
        });

        stage.state("no zones takes primary", W, H, s -> {
            GaugeComponent c = s.add(new GaugeComponent(new Box("g", 6, 6, W - 12, H - 12), 0, 20, 13).label("Render").unit(" ch"));
        });

        stage.state("half dial", W, 70, s -> s.add(new GaugeComponent(new Box("g", 6, 6, W - 12, 58), 0, 100, 64)
                .sweep(180).zones(30, 70).label("Hunger").unit("%")));

        stage.state("full circle", W, H, s -> s.add(new GaugeComponent(new Box("g", 6, 6, W - 12, H - 12), 0, 1, 0.7)
                .sweep(360).zones(0.5).valueFormat(v -> Math.round(v * 100) + "%").label("Share")));

        stage.state("thick own colours", W, H, s -> s.add(new GaugeComponent(new Box("g", 6, 6, W - 12, H - 12), 0, 10, 8)
                .thickness(10).zones(3, 6, 9).zoneColors(0xFF3366FF, 0xFF33CCCC, 0xFFFFCC33, 0xFFFF3366).label("Heat")));

        stage.state("empty and full", 170, 60, s -> {
            s.add(new GaugeComponent(new Box("a", 6, 6, 74, 48), 0, 100, 0).zones(60, 85).unit("%"));
            s.add(new GaugeComponent(new Box("b", 90, 6, 74, 48), 0, 100, 100).zones(60, 85).unit("%"));
        });

        stage.state("outside the range says the truth", W, H, s -> {
            GaugeComponent c = s.add(load(250));
        });

        stage.state("not a number shows a dash", W, H, s -> {
            GaugeComponent c = s.add(load(Double.NaN));
            s.waitMs(50);
        });

        stage.state("glide half way", W, H, s -> {
            GaugeComponent c = s.add(load(10));
            s.waitMs(20);
            c.setValue(90);
            s.waitMs(48);
            s.settle(0);
        });

        stage.state("glide ends", W, H, s -> {
            GaugeComponent c = s.add(load(10));
            s.waitMs(20);
            c.setValue(90);
            s.waitMs(900);
        });

        stage.state("reduced motion jumps", W, H, s -> {
            GaugeComponent c = s.add(load(10));
            s.settle(0);
            s.waitMs(20);
            BaseComponent.reducedMotion = true;
            try {
                c.setValue(77);
                s.waitMs(16);
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });

        stage.state("very long words", 220, H, s -> {
            GaugeComponent c = s.add(new GaugeComponent(new Box("g", 6, 6, W - 12, H - 12), 0, 1000000, 987654)
                    .label("A really very long label for it").unit(" blocks per hour"));
            s.move(50, 40);
            List<String> tip = s.panel().tooltipAt(50, 40);
            s.settle(700);
        });

        stage.state("disabled", W, H, s -> s.add(load(72).active(false)));

        stage.state("small boxes drop parts", 150, 44, s -> {
            GaugeComponent mid = s.add(new GaugeComponent(new Box("a", 4, 4, 38, 36), 0, 100, 72).zones(60, 85).label("Load").unit("%"));
            GaugeComponent small = s.add(new GaugeComponent(new Box("b", 48, 4, 22, 22), 0, 100, 72).zones(60, 85).label("Load"));
            GaugeComponent tiny = s.add(new GaugeComponent(new Box("c", 76, 4, 10, 10), 0, 100, 72).zones(60, 85));
            GaugeComponent none = s.add(new GaugeComponent(new Box("d", 92, 4, 1, 1), 0, 100, 72));
            s.move(58, 14);
        });

        stage.state("zero range and hidden", 150, 60, s -> {
            GaugeComponent flat = s.add(new GaugeComponent(new Box("a", 6, 6, 60, 48), 5, 5, 5));
            GaugeComponent gone = s.add(new GaugeComponent(new Box("b", 84, 6, 60, 48), 0, 10, 5));
            gone.visible = false;
        });
    }
}
