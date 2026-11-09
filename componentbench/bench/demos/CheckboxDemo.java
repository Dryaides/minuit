package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.CheckboxComponent;
import dev.dryaides.minuit.CheckboxComponent.State;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.ToggleComponent;
import java.util.ArrayList;
import java.util.List;

/** The check box and its third state. */
public final class CheckboxDemo implements Demo {
    private static final int W = 170;
    private static final int X = 8;

    @Override
    public void states(Stage stage) {
        stage.state("all states", W, 58, s -> {
            s.add(new CheckboxComponent(new Box("off", X, 6, 150, 12), "Off", false, v -> { }));
            s.add(new CheckboxComponent(new Box("on", X, 22, 150, 12), "On", true, v -> { }));
            s.add(new CheckboxComponent(new Box("mixed", X, 38, 150, 12), "Mixed", false, v -> { }).state(State.MIXED));
        });
        stage.state("disabled states", W, 58, s -> {
            s.add(new CheckboxComponent(new Box("off", X, 6, 150, 12), "Off", false, v -> { }).active(false));
            s.add(new CheckboxComponent(new Box("on", X, 22, 150, 12), "On", true, v -> { }).active(false));
            CheckboxComponent m = s.add(new CheckboxComponent(new Box("mixed", X, 38, 150, 12), "Mixed", false, v -> { }).state(State.MIXED).active(false));
            s.click(X + 4, 44);
        });
        stage.state("hover off", W, 26, s -> {
            s.add(new CheckboxComponent(new Box("c", X, 7, 150, 12), "Hover me", false, v -> { }));
            s.move(X + 30, 12);
        });
        stage.state("hover on", W, 26, s -> {
            s.add(new CheckboxComponent(new Box("c", X, 7, 150, 12), "Hover me", true, v -> { }));
            s.move(X + 30, 12);
        });
        stage.state("hover mixed", W, 26, s -> {
            s.add(new CheckboxComponent(new Box("c", X, 7, 150, 12), "Hover me", false, v -> { }).state(State.MIXED));
            s.move(X + 3, 12);
        });
        stage.state("click on the label ticks", W, 26, s -> {
            List<Boolean> heard = new ArrayList<>();
            CheckboxComponent c = s.add(new CheckboxComponent(new Box("c", X, 7, 150, 12), "Click the words", false, heard::add));
            s.click(X + 60, 12);
            s.click(X + 60, 12);
            s.click(X + 60, 12);
        });
        stage.state("mixed goes on then off", W, 26, s -> {
            List<Boolean> heard = new ArrayList<>();
            CheckboxComponent c = s.add(new CheckboxComponent(new Box("c", X, 7, 150, 12), "Mixed", false, heard::add).state(State.MIXED));
            s.click(X + 3, 12);
            s.click(X + 3, 12);
        });
        stage.state("tab shows the ring, space ticks", W, 42, s -> {
            int[] calls = new int[1];
            CheckboxComponent c = s.add(new CheckboxComponent(new Box("c", X, 7, 150, 12), "With the keys", false, v -> calls[0]++));
            s.add(new CheckboxComponent(new Box("d", X, 23, 150, 12), "Second stop", false, v -> { }));
            s.key(InputKey.TAB);
            s.key(InputKey.SPACE);
            s.key(InputKey.ENTER);
            s.key(InputKey.SPACE);
        });
        stage.state("disabled is no stop", W, 42, s -> {
            s.add(new CheckboxComponent(new Box("c", X, 7, 150, 12), "Disabled", false, v -> { }).active(false));
            CheckboxComponent d = s.add(new CheckboxComponent(new Box("d", X, 23, 150, 12), "The stop", false, v -> { }));
            s.key(InputKey.TAB);
        });
        stage.state("long label is cut and tooltip", 170, 60, s -> {
            String text = "A very long label that does not fit the width of this box";
            s.add(new CheckboxComponent(new Box("c", X, 7, 150, 12), text, true, v -> { }));
            s.move(X + 40, 12);
            s.settle(1500);
        });
        stage.state("sizes", W, 66, s -> {
            s.add(new CheckboxComponent(new Box("a", X, 4, 150, 12), "12 high", true, v -> { }));
            s.add(new CheckboxComponent(new Box("b", X, 18, 150, 16), "16 high", true, v -> { }));
            s.add(new CheckboxComponent(new Box("c", X, 36, 150, 24), "24 high", false, v -> { }).state(State.MIXED));
        });
        stage.state("tiny and hidden", W, 34, s -> {
            s.add(new CheckboxComponent(new Box("a", X, 6, 4, 4), "No room", true, v -> { }));
            s.add(new CheckboxComponent(new Box("b", 30, 6, 10, 9), "Cut", true, v -> { }));
            s.add(new CheckboxComponent(new Box("c", 50, 6, 60, 12), "", true, v -> { }));
            CheckboxComponent h = s.add(new CheckboxComponent(new Box("d", 120, 6, 40, 12), "Hidden", true, v -> { }));
            h.visible = false;
            s.click(125, 10);
        });
        stage.state("beside a toggle", W, 42, s -> {
            s.add(new ToggleComponent(new Box("t", X, 6, 150, 12), "The library's toggle", true, v -> { }));
            s.add(new CheckboxComponent(new Box("c", X, 22, 150, 12), "This check box", true, v -> { }));
        });
        stage.state("select all above a list", W, 74, s -> {
            CheckboxComponent all = s.add(new CheckboxComponent(new Box("all", X, 6, 150, 12), "Every biome", false, v -> { }));
            CheckboxComponent[] items = new CheckboxComponent[3];
            Runnable sum = () -> {
                int on = 0;
                for (CheckboxComponent i : items) {
                    on += i.checked() ? 1 : 0;
                }
                all.state(on == 0 ? State.OFF : on == items.length ? State.ON : State.MIXED);
            };
            String[] names = {"Plains", "Desert", "Forest"};
            for (int i = 0; i < 3; i++) {
                items[i] = s.add(new CheckboxComponent(new Box("i" + i, X + 12, 22 + i * 16, 138, 12), names[i], i == 1, v -> sum.run()));
            }
            s.click(X + 16, 28);
        });
    }
}
