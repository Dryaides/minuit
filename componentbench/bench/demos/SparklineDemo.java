package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.SparklineComponent;
import java.util.List;

/** The sparkline, driven by scripted input. */
public final class SparklineDemo implements Demo {
    private static final double[] WAVE = {3, 5, 4, 8, 6, 9, 7, 12, 10, 8, 13, 11, 14, 9, 12};
    private static final int W = 150;
    private static final int H = 34;

    private static SparklineComponent spark(Box box) {
        return new SparklineComponent(box);
    }

    @Override
    public void states(Stage stage) {
        stage.state("line", W, H, s -> {
            SparklineComponent c = s.add(spark(new Box("a", 8, 8, 80, 18)).set(WAVE));
        });

        stage.state("area", W, H, s -> {
            SparklineComponent c = s.add(spark(new Box("a", 8, 8, 80, 18)).mode(SparklineComponent.Mode.AREA).set(WAVE));
        });

        stage.state("bars", W, H, s -> {
            SparklineComponent c = s.add(spark(new Box("a", 8, 8, 80, 18)).mode(SparklineComponent.Mode.BAR).set(WAVE));
        });

        stage.state("inline beside text", 190, 44, s -> {
            s.paint((d, t) -> {
                d.text("TPS", 8, 9, t.palette().text());
                d.text("Ping", 8, 27, t.palette().text());
                d.text("19.8", 150, 9, t.palette().text());
                d.text("42 ms", 150, 27, t.palette().text());
            });
            s.add(spark(new Box("a", 40, 7, 100, 12)).fixed(0, 20).set(20, 20, 19, 20, 18, 20, 20, 15, 19, 20, 20, 19.8));
            s.add(spark(new Box("b", 40, 25, 100, 12)).mode(SparklineComponent.Mode.BAR)
                    .set(30, 42, 35, 60, 44, 38, 90, 52, 41, 36, 42, 38, 47, 42));
        });

        stage.state("trend colours", W, H, s -> {
            s.add(spark(new Box("a", 8, 8, 60, 18)).trend(true).set(1, 3, 2, 5, 6));
            s.add(spark(new Box("b", 80, 8, 60, 18)).trend(true).set(6, 5, 3, 3, 1));
        });

        stage.state("own colour", W, H, s -> s.add(spark(new Box("a", 8, 8, 80, 18)).color(0xFFFF8800).set(WAVE)));

        stage.state("gaps", W, H, s -> {
            SparklineComponent c = s.add(spark(new Box("a", 8, 8, 120, 18))
                    .set(1, 4, 2, Double.NaN, Double.NaN, 5, 3, Double.POSITIVE_INFINITY, 2, Double.NaN, 6, 4));
        });

        stage.state("spike not lost when dense", W, H, s -> {
            double[] many = new double[5000];
            for (int i = 0; i < many.length; i++) {
                many[i] = i % 7;
            }
            many[2500] = 100;
            s.add(spark(new Box("a", 8, 8, 120, 18)).capacity(5000).lastDot(false).set(many));
        });

        stage.state("dense bars", W, H, s -> {
            double[] many = new double[900];
            for (int i = 0; i < many.length; i++) {
                many[i] = 50 + 40 * Math.sin(i / 40D);
            }
            s.add(spark(new Box("a", 8, 8, 120, 18)).mode(SparklineComponent.Mode.BAR).capacity(900).set(many));
        });

        stage.state("negative bars and fixed range", W, H, s -> {
            SparklineComponent c = s.add(spark(new Box("a", 8, 8, 80, 18)).mode(SparklineComponent.Mode.BAR).set(3, -2, 5, -4, 2, 1, -1));
            s.add(spark(new Box("b", 96, 8, 46, 18)).fixed(0, 10).set(2, 30, -5, 4));
        });

        stage.state("live window fills from the right", W, H, s -> {
            SparklineComponent c = s.add(spark(new Box("a", 8, 8, 80, 18)).capacity(30).window(30));
            for (int i = 0; i < 12; i++) {
                c.add(i % 5);
            }
            for (int i = 0; i < 1000; i++) {
                c.add(i % 5);
            }
        });

        stage.state("hover lights a sample with its tooltip", W, 56, s -> {
            SparklineComponent c = s.add(spark(new Box("a", 8, 8, 80, 18)).unit(" ms").set(WAVE));
            int mx = 8 + 40;
            s.move(mx, 16);
            s.settle(700);
        });

        stage.state("hover a bar", W, H, s -> {
            SparklineComponent c = s.add(spark(new Box("a", 8, 8, 80, 18)).mode(SparklineComponent.Mode.BAR).set(WAVE));
            s.move(8 + 22, 16);
        });

        stage.state("flat one sample and empty", W, H, s -> {
            SparklineComponent flat = s.add(spark(new Box("a", 8, 8, 40, 18)).set(5, 5, 5, 5));
            SparklineComponent one = s.add(spark(new Box("b", 54, 8, 40, 18)).set(5));
            SparklineComponent none = s.add(spark(new Box("c", 100, 8, 40, 18)));
        });

        stage.state("disabled no hover", W, H, s -> {
            SparklineComponent c = s.add(spark(new Box("a", 8, 8, 80, 18)).active(false).set(WAVE));
            s.move(40, 16);
        });

        stage.state("hidden", W, H, s -> {
            SparklineComponent c = s.add(spark(new Box("a", 8, 8, 80, 18)).set(WAVE));
            c.visible = false;
            s.move(40, 16);
        });

        stage.state("tiny boxes", W, H, s -> {
            s.add(spark(new Box("a", 8, 8, 12, 4)).set(WAVE));
            s.add(spark(new Box("b", 26, 8, 3, 18)).set(WAVE));
            s.add(spark(new Box("c", 36, 8, 1, 1)).set(WAVE));
            s.add(spark(new Box("d", 44, 8, 0, 0)).set(WAVE));
            s.add(spark(new Box("e", 52, 8, 30, 1)).mode(SparklineComponent.Mode.BAR).set(WAVE));
        });
    }
}
