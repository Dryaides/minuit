package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ChartComponent;
import dev.dryaides.minuit.GaugeComponent;
import dev.dryaides.minuit.SparklineComponent;
import dev.dryaides.minuit.ModelPreviewComponent;
import dev.dryaides.minuit.Scene3DComponent;

/** smooth(false) on the left, smooth(true) on the right, for the 3D views and the charts. */
public final class SmoothDemo implements Demo {
    private static final double[] WAVE = {3, 8, 5, 12, 9, 14, 6, 10, 4, 11, 13, 7};
    private static final double[] SLOPE = {2, 6, 3, 9, 7, 12, 10, 4, 8, 13, 5, 9};

    @Override
    public void states(Stage stage) {
        stage.state("scene3d off | on", 250, 120, s -> {
            Scene3DComponent[] v = new Scene3DComponent[2];
            for (int i = 0; i < 2; i++) {
                v[i] = s.add(new Scene3DComponent(new Box("v" + i, 4 + i * 124, 4, 118, 102))
                        .model(Scene3DDemo.house()).floor(Scene3DComponent.Floor.GRID).smooth(i == 1));
            }
        });
        stage.state("crate off | on", 250, 120, s -> {
            Scene3DComponent[] v = new Scene3DComponent[2];
            for (int i = 0; i < 2; i++) {
                v[i] = s.add(new Scene3DComponent(new Box("v" + i, 4 + i * 124, 4, 118, 102))
                        .model(Scene3DDemo.crate()).smooth(i == 1));
            }
        });
        stage.state("preview off | on, framed(false)", 200, 110, s -> {
            for (int i = 0; i < 2; i++) {
                s.add(new ModelPreviewComponent(new Box("p" + i, 4 + i * 98, 4, 92, 102), Scene3DDemo.crate(), "Oak crate")
                        .subtitle(i == 0 ? "smooth off" : "smooth on").smooth(i == 1).framed(i == 0));
            }
        });
        stage.state("chart line off | on", 250, 90, s -> {
            for (int i = 0; i < 2; i++) {
                ChartComponent c = s.add(new ChartComponent(new Box("c" + i, 4 + i * 124, 4, 118, 82)).smooth(i == 1));
                c.addSeries("a").set(WAVE);
                c.addSeries("b").set(SLOPE);
            }
        });
        stage.state("chart area off | on", 250, 90, s -> {
            for (int i = 0; i < 2; i++) {
                ChartComponent c = s.add(new ChartComponent(new Box("c" + i, 4 + i * 124, 4, 118, 82))
                        .mode(ChartComponent.Mode.AREA).points(true).smooth(i == 1));
                c.addSeries("a").set(WAVE);
            }
        });
        stage.state("sparkline off | on", 200, 50, s -> {
            for (int i = 0; i < 2; i++) {
                s.add(new SparklineComponent(new Box("a" + i, 6 + i * 98, 6, 86, 16)).set(WAVE).smooth(i == 1));
                s.add(new SparklineComponent(new Box("b" + i, 6 + i * 98, 28, 86, 16))
                        .mode(SparklineComponent.Mode.AREA).set(SLOPE).smooth(i == 1));
            }
        });
        stage.state("gauge off | on", 200, 80, s -> {
            for (int i = 0; i < 2; i++) {
                s.add(new GaugeComponent(new Box("g" + i, 6 + i * 98, 6, 86, 70), 0, 100, 72)
                        .zones(60, 85).label("Load").unit("%").smooth(i == 1));
            }
        });
    }
}
