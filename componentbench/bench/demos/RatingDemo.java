package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.RatingComponent;
import dev.dryaides.minuit.RatingComponent.Mark;

/** Stars and hearts. */
public final class RatingDemo implements Demo {
    private static final int W = 190;
    private static final int H = RatingComponent.HEIGHT;

    @Override
    public void states(Stage stage) {
        stage.state("values", W, 46, s -> {
            s.add(new RatingComponent(new Box("a", 8, 8, 0, H), 5, 0, v -> { }));
            s.add(new RatingComponent(new Box("b", 8, 26, 0, H), 5, 3, v -> { }));
            s.add(new RatingComponent(new Box("c", 100, 8, 0, H), 5, 3.5, v -> { }).halves(true).readOnly(true));
            s.add(new RatingComponent(new Box("d", 100, 26, 0, H), 5, 5, v -> { }));
        });
        stage.state("hover shows what a click gives", W, 30, s -> {
            s.add(new RatingComponent(new Box("a", 8, 8, 0, H), 5, 2, v -> { }));
            s.move(8 + 3 * 13 + 5, 12);
        });
        stage.state("half under the mouse", W, 30, s -> {
            s.add(new RatingComponent(new Box("a", 8, 8, 0, H), 5, 1, v -> { }).halves(true));
            s.move(8 + 3 * 13 + 2, 12);
        });
        stage.state("clicked, mid fade", W, 30, s -> {
            s.add(new RatingComponent(new Box("a", 8, 8, 0, H), 5, 1, v -> { }));
            s.click(8 + 3 * 13 + 5, 12);
            s.move(180, 28);
            s.settle(70);
        });
        stage.state("keyboard: right twice from 2", W, 30, s -> {
            RatingComponent r = s.add(new RatingComponent(new Box("a", 8, 8, 0, H), 5, 2, v -> { }));
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
        });
        stage.state("hearts", W, 30, s -> {
            s.add(new RatingComponent(new Box("a", 8, 8, 0, H), 10, 6.5, v -> { }).mark(Mark.HEART).halves(true).readOnly(true));
        });
        stage.state("disabled", W, 30, s -> {
            s.add(new RatingComponent(new Box("a", 8, 8, 0, H), 5, 3, v -> { }).active(false));
            s.add(new RatingComponent(new Box("b", 100, 8, 0, H), 5, 2, v -> { }).mark(Mark.HEART).active(false));
        });
    }
}
