package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.NumberFieldComponent;
import dev.dryaides.minuit.StepperComponent;
import dev.dryaides.minuit.TextFieldComponent;
import java.util.ArrayList;
import java.util.List;

/** A typed number with limits, steps and the wheel. */
public final class NumberFieldDemo implements Demo {
    private static final int W = 140;
    private static final int H = 32;
    private static final int X = 8;
    private static final int Y = 8;

    private static NumberFieldComponent field(Scene s, double min, double max, double value, List<Double> heard) {
        return s.add(new NumberFieldComponent(new Box("n", X, Y, 120, 16), min, max, value, heard::add));
    }

    @Override
    public void states(Stage stage) {
        stage.state("default", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 100, 42, new ArrayList<>());
        });
        stage.state("beside the stepper and a field", W, 68, s -> {
            field(s, 0, 100, 42, new ArrayList<>());
            s.add(new StepperComponent(new Box("s", X, 28, 120, 16), 0, 100, 42, v -> { }));
            s.add(new TextFieldComponent(new Box("t", X, 48, 120, 16), "Text field", v -> { }));
        });
        stage.state("hover plus", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 100, 42, new ArrayList<>());
            Box b = BenchAccess.plusBox(n);
            s.move(b.x() + 5, b.y() + 8);
        });
        stage.state("held minus repeats", W, H, s -> {
            List<Double> heard = new ArrayList<>();
            NumberFieldComponent n = field(s, 0, 100, 42, heard);
            Box b = BenchAccess.minusBox(n);
            s.press(b.x() + 5, b.y() + 8);
            s.waitMs(StepperComponent.REPEAT_DELAY_MS + 170);
        });
        stage.state("release stops the repeat", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 100, 42, new ArrayList<>());
            Box b = BenchAccess.plusBox(n);
            s.click(b.x() + 5, b.y() + 8);
            s.waitMs(1000);
        });
        stage.state("at the limit the button is off", W, H, s -> {
            List<Double> heard = new ArrayList<>();
            NumberFieldComponent n = field(s, 0, 100, 100, heard);
            Box b = BenchAccess.plusBox(n);
            s.click(b.x() + 5, b.y() + 8);
            s.move(b.x() + 5, b.y() + 8);
        });
        stage.state("typing settles on enter", W, H, s -> {
            List<Double> heard = new ArrayList<>();
            NumberFieldComponent n = field(s, 0, 100, 42, heard);
            s.click(X + 20, Y + 8);
            s.key(InputKey.SELECT_ALL);
            s.type("77");
            s.key(InputKey.ENTER);
            s.settle(0);
        });
        stage.state("typing settles on leaving", W, H, s -> {
            List<Double> heard = new ArrayList<>();
            NumberFieldComponent n = field(s, 0, 100, 42, heard);
            s.focus(n);
            s.key(InputKey.SELECT_ALL);
            s.type("12");
            s.focus(null);
            s.tick(1);
        });
        stage.state("too high wears a ring", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 100, 42, new ArrayList<>());
            s.focus(n);
            s.key(InputKey.SELECT_ALL);
            s.type("500");
            s.settle(0);
        });
        stage.state("too high comes back inside", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 100, 42, new ArrayList<>());
            s.focus(n);
            s.key(InputKey.SELECT_ALL);
            s.type("500");
            s.key(InputKey.ENTER);
            s.settle(0);
        });
        stage.state("nonsense goes back", W, H, s -> {
            List<Double> heard = new ArrayList<>();
            NumberFieldComponent n = s.add(new NumberFieldComponent(new Box("n", X, Y, 120, 16), -10, 10, 3, heard::add));
            s.focus(n);
            s.key(InputKey.SELECT_ALL);
            s.type("-");
            s.key(InputKey.ENTER);
            s.settle(0);
        });
        stage.state("escape reverts then lets go", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 100, 42, new ArrayList<>());
            s.focus(n);
            s.key(InputKey.SELECT_ALL);
            s.type("7");
            s.key(InputKey.ESCAPE);
            s.key(InputKey.ESCAPE);
        });
        stage.state("up and down step, shift by ten", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 100, 42, new ArrayList<>());
            s.focus(n);
            s.key(InputKey.UP);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.UP, true);
            s.settle(0);
        });
        stage.state("wheel steps only with focus", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 100, 42, new ArrayList<>());
            s.scroll(X + 20, Y + 8, 1);
            s.focus(n);
            s.scroll(X + 20, Y + 8, 1);
            s.scroll(X + 20, Y + 8, -1);
            s.scroll(X + 20, Y + 8, -1);
            s.settle(0);
        });
        stage.state("wheel on hover when asked", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 100, 42, new ArrayList<>()).wheelOnHover(true);
            s.scroll(X + 20, Y + 8, 1);
        });
        stage.state("decimals and a step", W, H, s -> {
            NumberFieldComponent n = s.add(new NumberFieldComponent(new Box("n", X, Y, 120, 16), 0, 2, 0.1, v -> { }).step(0.2).decimals(1));
            s.focus(n);
            s.key(InputKey.UP);
            s.key(InputKey.SELECT_ALL);
            s.type("1,25");
            s.key(InputKey.ENTER);
            s.settle(0);
        });
        stage.state("minus only where it can be", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 100, 5, new ArrayList<>());
            s.focus(n);
            s.key(InputKey.SELECT_ALL);
            s.type("-3.5");
            s.settle(0);
        });
        stage.state("disabled", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 100, 42, new ArrayList<>()).active(false);
            Box b = BenchAccess.plusBox(n);
            s.click(b.x() + 5, b.y() + 8);
            s.scroll(X + 20, Y + 8, 1);
            s.key(InputKey.TAB);
        });
        stage.state("tab shows the ring", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 100, 42, new ArrayList<>());
            s.key(InputKey.TAB);
            s.settle(0);
        });
        stage.state("long number is cut at rest", W, 62, s -> {
            NumberFieldComponent n = s.add(new NumberFieldComponent(new Box("n", X, Y, 70, 16), -Double.MAX_VALUE, Double.MAX_VALUE, 123456789012D, v -> { }));
            s.move(X + 10, Y + 8);
            s.settle(1500);
        });
        stage.state("narrow box drops the buttons", W, H, s -> s.add(new NumberFieldComponent(new Box("n", X, Y, 34, 16), 0, 100, 42, v -> { })));
        stage.state("value out of limits at the start", W, H, s -> {
            NumberFieldComponent n = field(s, 0, 10, 42, new ArrayList<>());
            n.setValue(Double.NaN);
        });
    }
}
