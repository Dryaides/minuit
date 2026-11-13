package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.SwatchPickerComponent;

/** Picking a dye colour. */
public final class SwatchPickerDemo implements Demo {
    private static final int W = 190;
    private static final String[] NAMES = {"White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime", "Pink", "Gray",
            "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black"};

    private static SwatchPickerComponent dyes(int picked) {
        return new SwatchPickerComponent(new Box("dye", 8, 8, 0, 0), SwatchPickerComponent.DYES, picked, i -> { }).names(NAMES);
    }

    @Override
    public void states(Stage stage) {
        stage.state("none picked", W, 50, s -> s.add(dyes(-1)));
        stage.state("red picked", W, 50, s -> s.add(dyes(14)));
        stage.state("hover tells the name", W, 70, s -> {
            s.add(dyes(14));
            s.move(8 + 3 * 15 + 6, 8 + 6);
            s.settle(1500);
        });
        stage.state("keyboard: Tab, Down, Right", W, 50, s -> {
            s.add(dyes(2));
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.RIGHT);
        });
        stage.state("wide box fits 11 a row; disabled", W, 70, s -> {
            s.add(new SwatchPickerComponent(new Box("a", 8, 8, 176, 0), SwatchPickerComponent.DYES, 5, i -> { }));
            s.add(new SwatchPickerComponent(new Box("b", 8, 48, 0, 0), new int[]{0xFFB02E26, 0xFF3C44AA, 0xFF5E7C16}, 1, i -> { })
                    .active(false));
        });
    }
}
