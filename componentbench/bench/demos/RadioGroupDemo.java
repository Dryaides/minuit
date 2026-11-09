package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.RadioGroupComponent;
import java.util.ArrayList;
import java.util.List;

/** One choice out of a few, with scripted input. */
public final class RadioGroupDemo implements Demo {
    private static final int W = 170;
    private static final int X = 8;
    private static final int Y = 6;
    private static final List<String> THREE = List.of("Peaceful", "Normal", "Hard");

    private static RadioGroupComponent three(Scene s, int selected, List<Integer> heard) {
        return s.add(new RadioGroupComponent(new Box("r", X, Y, 150, 42), THREE, selected, heard::add));
    }

    @Override
    public void states(Stage stage) {
        stage.state("default", W, 54, s -> {
            RadioGroupComponent r = three(s, 1, new ArrayList<>());
        });
        stage.state("none chosen", W, 54, s -> three(s, -1, new ArrayList<>()));
        stage.state("hover a row", W, 54, s -> {
            three(s, 1, new ArrayList<>());
            s.move(X + 30, Y + 7);
        });
        stage.state("click selects once", W, 54, s -> {
            List<Integer> heard = new ArrayList<>();
            RadioGroupComponent r = three(s, 1, heard);
            s.click(X + 30, Y + 35);
            s.click(X + 3, Y + 35);
            s.move(150, 50);
        });
        stage.state("tab comes in on the chosen", W, 54, s -> {
            RadioGroupComponent r = three(s, 2, new ArrayList<>());
            s.key(InputKey.TAB);
        });
        stage.state("arrows choose and wrap", W, 54, s -> {
            List<Integer> heard = new ArrayList<>();
            RadioGroupComponent r = three(s, 2, heard);
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.UP);
            s.key(InputKey.LEFT);
        });
        stage.state("home and end", W, 54, s -> {
            RadioGroupComponent r = three(s, 1, new ArrayList<>());
            s.key(InputKey.TAB);
            s.key(InputKey.END);
            s.key(InputKey.HOME);
        });
        stage.state("space chooses with none chosen", W, 54, s -> {
            RadioGroupComponent r = three(s, -1, new ArrayList<>());
            s.key(InputKey.TAB);
            s.key(InputKey.SPACE);
        });
        stage.state("disabled row", W, 54, s -> {
            List<Integer> heard = new ArrayList<>();
            RadioGroupComponent r = three(s, 0, heard).disabled(1, true, "Unlock it first");
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.click(X + 30, Y + 21);
        });
        stage.state("disabled chosen row stays", W, 54, s -> {
            RadioGroupComponent r = three(s, 1, new ArrayList<>()).disabled(1, true);
        });
        stage.state("disabled group", W, 54, s -> {
            RadioGroupComponent r = three(s, 1, new ArrayList<>()).active(false);
            s.click(X + 30, Y + 7);
            s.key(InputKey.TAB);
        });
        stage.state("horizontal wraps", W, 40, s -> {
            RadioGroupComponent r = s.add(new RadioGroupComponent(new Box("r", X, Y, 150, 28), List.of("Small", "Medium", "Large", "Enormous", "Huge"), 3, v -> { }).horizontal(true));
        });
        stage.state("horizontal arrows", W, 30, s -> {
            RadioGroupComponent r = s.add(new RadioGroupComponent(new Box("r", X, Y, 150, 14), List.of("One", "Two", "Three"), 0, v -> { }).horizontal(true));
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
        });
        stage.state("long label is cut and tooltip", W, 70, s -> {
            String text = "A choice with a label far too long for this box";
            s.add(new RadioGroupComponent(new Box("r", X, Y, 150, 28), List.of(text, "Short"), 0, v -> { }));
            s.move(X + 40, Y + 7);
            s.settle(1500);
        });
        stage.state("box too short draws whole rows", W, 34, s -> {
            RadioGroupComponent r = s.add(new RadioGroupComponent(new Box("r", X, Y, 150, 20), THREE, 2, v -> { }));
            s.click(X + 30, Y + 21);
        });
        stage.state("empty options", W, 30, s -> {
            RadioGroupComponent r = s.add(new RadioGroupComponent(new Box("r", X, Y, 150, 14), List.of(), 0, v -> { }));
            s.key(InputKey.TAB);
        });
        stage.state("beside a button", W, 74, s -> {
            three(s, 0, new ArrayList<>());
            s.add(new ButtonComponent(new Box("b", X, 50, 70, 18), "Next", () -> { }));
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
        });
    }
}
