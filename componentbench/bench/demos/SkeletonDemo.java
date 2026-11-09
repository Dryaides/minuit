package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.SkeletonComponent;

/** Loading placeholders. The clock is simulated, so the band sits in the same place in every picture. */
public final class SkeletonDemo implements Demo {
    private static final int W = 200;

    @Override
    public void states(Stage stage) {
        stage.state("lines", W, 62, s -> {
            SkeletonComponent c = s.add(SkeletonComponent.lines(new Box("l", 8, 8, 184, 46), 4));
        });

        stage.state("all kinds", W, 120, s -> {
            s.add(SkeletonComponent.head(new Box("h", 8, 8, 24, 24)));
            SkeletonComponent r = s.add(SkeletonComponent.row(new Box("r", 38, 8, 154, 24)));
            s.add(SkeletonComponent.block(new Box("b", 8, 38, 60, 74)));
            SkeletonComponent card = s.add(SkeletonComponent.card(new Box("c", 76, 38, 116, 74)));
        });

        stage.state("band further on", W, 62, s -> {
            SkeletonComponent c = s.add(SkeletonComponent.lines(new Box("l", 8, 8, 184, 46), 4));
            int before = c.bandAt();
            s.waitMs(320);
            int after = c.bandAt();
            s.settle(0);
        });

        stage.state("reduced motion rests", W, 62, s -> {
            s.paint((d, t) -> BaseComponent.reducedMotion = true);
            SkeletonComponent c = s.add(SkeletonComponent.lines(new Box("l", 8, 8, 184, 46), 4));
            s.paint((d, t) -> BaseComponent.reducedMotion = false);
            BaseComponent.reducedMotion = true;
            try {
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });

        stage.state("shimmer off", W, 34, s -> {
            SkeletonComponent c = s.add(SkeletonComponent.block(new Box("b", 8, 8, 184, 18)).shimmer(false));
        });

        stage.state("disabled", W, 44, s -> {
            SkeletonComponent c = s.add(SkeletonComponent.row(new Box("r", 8, 8, 184, 28)).active(false));
        });

        stage.state("hidden", W, 34, s -> {
            SkeletonComponent c = s.add(SkeletonComponent.block(new Box("b", 8, 8, 184, 18)));
            c.visible = false;
        });

        stage.state("fewer lines fit", W, 40, s -> {
            SkeletonComponent c = s.add(SkeletonComponent.lines(new Box("l", 8, 8, 184, 24), 9));
        });

        stage.state("tiny and empty boxes", W, 40, s -> {
            SkeletonComponent a = s.add(SkeletonComponent.lines(new Box("a", 8, 8, 30, 4), 3));
            SkeletonComponent b = s.add(SkeletonComponent.row(new Box("b", 46, 8, 14, 14)));
            SkeletonComponent c = s.add(SkeletonComponent.card(new Box("c", 68, 8, 30, 20)));
            SkeletonComponent e = s.add(SkeletonComponent.lines(new Box("e", 106, 8, 40, 20), 0));
            SkeletonComponent z = s.add(SkeletonComponent.head(new Box("z", 150, 8, 0, 0)));
        });

        stage.state("a list of rows", W, 96, s -> {
            for (int i = 0; i < 4; i++) {
                SkeletonComponent r = s.add(SkeletonComponent.row(new Box("r" + i, 8, 8 + i * 20, 184, 16)));
            }
        });

        stage.state("in a well", W, 62, s -> {
            s.paint((d, t) -> t.frame().well(d, t.palette(), 6, 6, 188, 50, false));
            SkeletonComponent c = s.add(SkeletonComponent.lines(new Box("l", 12, 11, 176, 40), 4));
        });

        stage.state("a grid of cards", W, 112, s -> {
            for (int i = 0; i < 6; i++) {
                s.add(SkeletonComponent.card(new Box("c" + i, 8 + (i % 3) * 62, 8 + (i / 3) * 50, 58, 46)));
            }
        });

        stage.state("the same band on every shape", W, 44, s -> {
            SkeletonComponent a = s.add(SkeletonComponent.block(new Box("a", 8, 8, 184, 12)));
            SkeletonComponent b = s.add(SkeletonComponent.block(new Box("b", 8, 24, 184, 12)));
            s.waitMs(160);
        });

        stage.state("the thing arrived", W, 44, s -> {
            SkeletonComponent c = s.add(SkeletonComponent.lines(new Box("l", 8, 8, 184, 28), 3));
            s.paint((d, t) -> {
                if (!c.visible) {
                    d.text("Loaded words", 8, 9, t.palette().text());
                }
            });
            s.waitMs(160);
            c.visible = false;
        });

        stage.state("takes no input", W, 34, s -> {
            SkeletonComponent c = s.add(SkeletonComponent.block(new Box("b", 8, 8, 184, 18)));
            s.click(40, 16);
        });
    }
}
