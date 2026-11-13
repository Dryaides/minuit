package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.DescriptionListComponent;

/** Terms and values. */
public final class DescriptionListDemo implements Demo {
    private static final int W = 190;

    @Override
    public void states(Stage stage) {
        stage.state("mob stats", W, 60, s -> {
            s.add(new DescriptionListComponent(new Box("a", 8, 8, 174, 0))
                    .row("Health", "20 / 20").row("Armor", "4").row("Owner", "Steve").row("Spawned", "Day 12"));
        });
        stage.state("with rules", W, 60, s -> {
            s.add(new DescriptionListComponent(new Box("a", 8, 8, 174, 0)).lines(true)
                    .row("Address", "play.example.net").row("Version", "1.21.1").row("Players", "12 / 40"));
        });
        stage.state("long value cut, tooltip whole", W, 70, s -> {
            s.add(new DescriptionListComponent(new Box("a", 8, 8, 120, 0))
                    .row("Biome", "Windswept Gravelly Hills").row("Seed", "-4172144997902289642"));
            s.move(100, 12);
            s.settle(1500);
        });
        stage.state("value changed, disabled", W, 50, s -> {
            DescriptionListComponent a = s.add(new DescriptionListComponent(new Box("a", 8, 8, 80, 0))
                    .row("Health", "20").row("Hunger", "18"));
            a.value("Health", "7");
            s.add(new DescriptionListComponent(new Box("b", 100, 8, 82, 0)).row("Mana", "40").active(false));
        });
    }
}
