package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.StepsComponent;
import java.util.ArrayList;
import java.util.List;

/** The steps, driven by scripted input. */
public final class StepsDemo implements Demo {
    private static final int W = 260;
    private static final int H = 44;
    private static final List<String> FOUR = List.of("World", "Mods", "Players", "Confirm");

    private static StepsComponent row() {
        return new StepsComponent(new Box("s", 8, 8, W - 16, 30), FOUR);
    }

    private static StepsComponent column(int height) {
        return new StepsComponent(new Box("s", 8, 8, 170, height), FOUR).vertical(true);
    }

    private static List<String> numbered(int count) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add("Step " + (i + 1));
        }
        return out;
    }

    private static void clickStep(bench.api.Scene s, StepsComponent c, int step) {
        s.click(c.markerX(step) + 6, c.markerY(step) + 6);
    }

    @Override
    public void states(Stage stage) {
        stage.state("first step", W, H, s -> {
            StepsComponent c = s.add(row());
        });

        stage.state("mid way", W, H, s -> {
            StepsComponent c = s.add(row().current(2));
        });

        stage.state("all done", W, H, s -> {
            StepsComponent c = s.add(row().current(4));
            s.add(row().current(99)).visible = false;
        });

        stage.state("error with its tooltip", W, 70, s -> {
            StepsComponent c = s.add(row().current(2).error(1, "Two mods clash"));
            s.move(c.markerX(1) + 6, c.markerY(1) + 6);
            s.settle(700);
        });

        stage.state("hover a done step", W, H, s -> {
            StepsComponent c = s.add(row().current(3).onStep(v -> { }));
            s.move(c.markerX(1) + 6, c.markerY(1) + 6);
        });

        stage.state("press held", W, H, s -> {
            int[] heard = {-1};
            StepsComponent c = s.add(row().current(3).onStep(v -> heard[0] = v));
            s.press(c.markerX(0) + 6, c.markerY(0) + 6);
        });

        stage.state("click goes back, keeps what was reached", W, H, s -> {
            List<Integer> heard = new ArrayList<>();
            StepsComponent c = s.add(row().current(3).onStep(heard::add));
            clickStep(s, c, 1);
            clickStep(s, c, 1);
            s.move(-100, -100);
        });

        stage.state("press released elsewhere goes nowhere", W, H, s -> {
            List<Integer> heard = new ArrayList<>();
            StepsComponent c = s.add(row().current(3).onStep(heard::add));
            s.drag(c.markerX(0) + 6, c.markerY(0) + 6, c.markerX(2) + 6, c.markerY(2) + 6);
        });

        stage.state("not allowed past what was reached", W, H, s -> {
            List<Integer> heard = new ArrayList<>();
            StepsComponent c = s.add(row().current(1).onStep(heard::add));
            clickStep(s, c, 3);
        });

        stage.state("own rule", W, H, s -> {
            List<Integer> heard = new ArrayList<>();
            StepsComponent c = s.add(row().current(1).onStep(heard::add).allow(i -> i == 3));
            clickStep(s, c, 0);
            clickStep(s, c, 3);
            s.move(-100, -100);
        });

        stage.state("no callback, not clickable", W, H, s -> {
            StepsComponent c = s.add(row().current(2));
            s.key(InputKey.TAB);
        });

        stage.state("keys walk the allowed steps", W, H, s -> {
            List<Integer> heard = new ArrayList<>();
            StepsComponent c = s.add(row().current(3).onStep(heard::add));
            s.key(InputKey.TAB);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
        });

        stage.state("enter goes there", W, H, s -> {
            List<Integer> heard = new ArrayList<>();
            StepsComponent c = s.add(row().current(3).onStep(heard::add));
            s.focus(c);
            s.key(InputKey.LEFT);
            s.key(InputKey.ENTER);
            s.key(InputKey.LEFT);
            s.key(InputKey.ESCAPE);
        });

        stage.state("column", 190, 92, s -> {
            StepsComponent c = s.add(column(80).current(2).error(1, ""));
        });

        stage.state("column with words", 190, 130, s -> {
            StepsComponent c = s.add(column(118).current(1).onStep(v -> { })
                    .descriptions(List.of("Pick where to play", "Two are out of date, and this line goes on for too long to fit", "", "All set")));
            s.move(c.markerX(0) + 40, c.markerY(0) + 6);
        });

        stage.state("column scrolls by the wheel", 190, 72, s -> {
            StepsComponent c = s.add(new StepsComponent(new Box("s", 8, 8, 170, 58), numbered(12)).vertical(true).current(5));
            int first = c.firstShown();
            s.scroll(40, 30, -1);
            s.move(-100, -100);
        });

        stage.state("long labels cut, tooltip whole", W, 70, s -> {
            String name = "A really very long label for a step";
            StepsComponent c = s.add(new StepsComponent(new Box("s", 8, 8, W - 16, 30), List.of(name, "Two", name + " again")).current(1));
            s.move(c.markerX(0) + 6, c.markerY(0) + 6);
            s.settle(700);
        });

        stage.state("narrow row shows the current label", W, H, s -> {
            StepsComponent c = s.add(new StepsComponent(new Box("s", 8, 8, 170, 30), numbered(9)).current(4));
        });

        stage.state("dots with the current label", W, H, s -> {
            StepsComponent c = s.add(new StepsComponent(new Box("s", 8, 8, 170, 30), numbered(16)).current(6));
        });

        stage.state("tight row has dots", W, 28, s -> {
            StepsComponent c = s.add(new StepsComponent(new Box("s", 8, 8, 170, 12), numbered(24)).current(10));
        });

        stage.state("no room says it in words", W, 28, s -> {
            StepsComponent c = s.add(new StepsComponent(new Box("s", 8, 8, 60, 12), numbered(24)).current(10));
            s.add(new StepsComponent(new Box("t", 90, 8, 8, 8), FOUR));
            s.add(new StepsComponent(new Box("u", 110, 8, 0, 0), FOUR));
            s.add(new StepsComponent(new Box("v", 130, 8, 40, 6), FOUR).vertical(true));
            s.add(new StepsComponent(new Box("w", 180, 8, 12, 16), FOUR).vertical(true));
        });

        stage.state("no steps and one step", W, H, s -> {
            StepsComponent none = s.add(new StepsComponent(new Box("a", 8, 8, 100, 30), List.of()));
            s.add(new StepsComponent(new Box("b", 130, 8, 100, 30), List.of("Only")));
            s.focus(none);
            s.key(InputKey.DOWN);
        });

        stage.state("disabled ignores input", W, H, s -> {
            List<Integer> heard = new ArrayList<>();
            StepsComponent c = s.add(row().current(2).error(0, "Wrong").onStep(heard::add).active(false));
            clickStep(s, c, 1);
        });

        stage.state("beside a hidden one", W, H, s -> {
            List<Integer> heard = new ArrayList<>();
            StepsComponent c = s.add(row().current(2).onStep(heard::add));
            c.visible = false;
            clickStep(s, c, 1);
        });
    }
}
