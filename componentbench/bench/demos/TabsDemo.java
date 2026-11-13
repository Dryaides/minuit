package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.TabsComponent;
import java.util.List;

/** A row of tabs: at rest, hovered, and after a click picked another tab through its hit box. */
public final class TabsDemo implements Demo {
    private static final int W = 260;
    private static final int H = 40;
    private static final List<String> THREE = List.of("Mods", "Packs", "Shaders");

    private static TabsComponent row(int index) {
        return new TabsComponent(new Box("t", 8, 8, W - 16, 20), THREE, index, i -> { });
    }

    @Override
    public void states(Stage stage) {
        stage.state("first shown", W, H, s -> s.add(row(0)));

        stage.state("middle shown, last hovered", W, H, s -> {
            s.add(row(1));
            s.move(W - 30, 18);
        });

        stage.state("click on the last tab's left edge", W, H, s -> {
            TabsComponent c = s.add(row(0));
            s.click(8 + 2 * ((W - 16 - 2 * 4) / 3 + 4) + 1, 18);
            s.move(-100, -100);
            s.settle(400);
        });

        stage.state("one disabled", W, H, s -> s.add(row(0).disabled(1, true)));
    }
}
