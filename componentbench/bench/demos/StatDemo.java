package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.StatComponent;

/** Numbers under their labels. */
public final class StatDemo implements Demo {
    private static final int W = 190;
    private static final int H = StatComponent.HEIGHT;

    @Override
    public void states(Stage stage) {
        stage.state("a row of stats", W, 44, s -> {
            s.add(new StatComponent(new Box("a", 8, 8, 54, H), "Coins", 1250).centred(true));
            s.add(new StatComponent(new Box("b", 68, 8, 54, H), "Kills", 37).centred(true));
            s.add(new StatComponent(new Box("c", 128, 8, 54, H), "Best lap", 84_200)
                    .format(ms -> ms / 1000 + "." + ms / 100 % 10 + "s").centred(true));
        });
        stage.state("counting 120 to 345, 150 ms in", W, 44, s -> {
            StatComponent st = s.add(new StatComponent(new Box("a", 8, 8, 80, H), "Coins", 120));
            st.value(345);
            s.settle(150);
        });
        stage.state("counting done", W, 44, s -> {
            StatComponent st = s.add(new StatComponent(new Box("a", 8, 8, 80, H), "Coins", 120));
            st.value(345);
            s.settle(StatComponent.ROLL_MS + 50);
        });
        stage.state("small, too long for big, disabled", W, 44, s -> {
            s.add(new StatComponent(new Box("a", 8, 8, 50, StatComponent.SMALL_HEIGHT), "Deaths", 3).large(false));
            s.add(new StatComponent(new Box("b", 64, 8, 60, H), "Blocks mined", 1_234_567));
            s.add(new StatComponent(new Box("c", 134, 8, 50, H), "Level", 12).active(false));
        });
    }
}
