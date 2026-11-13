package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.CountdownComponent;

/** Round timers and cooldowns. */
public final class CountdownDemo implements Demo {
    private static final int W = 190;

    @Override
    public void states(Stage stage) {
        stage.state("formats", W, 30, s -> {
            s.add(new CountdownComponent(new Box("a", 8, 8, 50, 9), 90_000));
            s.add(new CountdownComponent(new Box("b", 66, 8, 50, 9), 3_725_000));
            s.add(new CountdownComponent(new Box("c", 124, 8, 50, 9), 0));
            s.settle(0);
        });
        stage.state("rounds up, warns in the last 10 s", W, 30, s -> {
            s.add(new CountdownComponent(new Box("a", 8, 8, 50, 9), 9_200));
            s.add(new CountdownComponent(new Box("b", 66, 8, 50, 9), 30_000).warnAt(0));
            s.settle(0);
        });
        stage.state("bar after 3.5 s of 10", W, 34, s -> {
            s.add(new CountdownComponent(new Box("a", 8, 8, 80, CountdownComponent.BAR_HEIGHT), 10_000).bar(true).warnAt(3));
            s.add(new CountdownComponent(new Box("b", 100, 8, 80, CountdownComponent.BAR_HEIGHT), 20_000).bar(true).warnAt(3));
            s.waitMs(3_500);
            s.settle(0);
        });
        stage.state("cooldown in its last seconds", W, 34, s -> {
            s.add(new CountdownComponent(new Box("a", 8, 8, 80, CountdownComponent.BAR_HEIGHT), 5_000).bar(true).warnAt(3));
            s.waitMs(3_000);
            s.settle(0);
        });
        stage.state("paused at 1:00, done says so", W, 34, s -> {
            CountdownComponent a = s.add(new CountdownComponent(new Box("a", 8, 8, 50, 9), 60_000).pause());
            int[] runs = {0};
            CountdownComponent b = s.add(new CountdownComponent(new Box("b", 66, 8, 50, 9), 1_000).onDone(() -> runs[0]++));
            s.waitMs(2_000);
            s.tick(3);
            s.caption(() -> "onDone ran " + runs[0] + "x");
            s.settle(0);
        });
        stage.state("disabled", W, 34, s -> {
            s.add(new CountdownComponent(new Box("a", 8, 8, 80, CountdownComponent.BAR_HEIGHT), 10_000).bar(true).active(false));
            s.waitMs(4_000);
            s.settle(0);
        });
    }
}
