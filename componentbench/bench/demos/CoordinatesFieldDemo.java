package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.CoordinatesFieldComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.TextFieldComponent;

/** Three number fields for a position: paste, nudge, relative, Here, a Y range. */
public final class CoordinatesFieldDemo implements Demo {
    private static final int W = 250;

    private static CoordinatesFieldComponent field() {
        return new CoordinatesFieldComponent(new Box("pos", 8, 8, 234, 16), v -> { })
                .yRange(-64, 320)
                .relativeToggle(true)
                .here(() -> new CoordinatesFieldComponent.Value(120.6, 71, -304.2, false));
    }

    @Override
    public void states(Stage stage) {
        stage.state("at rest", W, 32, s -> {
            s.add(field());
        });
        stage.state("typing in Y", W, 32, s -> {
            s.add(field());
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            s.key(InputKey.SELECT_ALL);
            s.type("-12");
        });
        stage.state("Here clicked", W, 32, s -> {
            s.add(field());
            s.click(215, 16);
            s.move(60, 30);
        });
        stage.state("relative on, Y nudged by 10", W, 32, s -> {
            s.add(field());
            s.click(181, 16);
            s.click(110, 16);
            s.key(InputKey.UP, true);
        });
        stage.state("pasted \"10, 500, -20\": Y held at 320", W, 32, s -> {
            TextFieldComponent.clipboard.set("10, 500, -20");
            s.add(field());
            s.key(InputKey.TAB);
            s.key(InputKey.PASTE);
        });
        stage.state("two decimals", W, 32, s -> {
            s.add(new CoordinatesFieldComponent(new Box("pos", 8, 8, 234, 16), v -> { }).decimals(2));
            s.key(InputKey.TAB);
            s.type("12.345");
        });
        stage.state("disabled", W, 32, s -> {
            s.add(field().active(false));
        });
    }
}
