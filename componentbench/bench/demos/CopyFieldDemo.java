package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.CopyFieldComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.TextFieldComponent;

/** Text to copy: a seed, coordinates. */
public final class CopyFieldDemo implements Demo {
    private static final int W = 190;

    @Override
    public void states(Stage stage) {
        stage.state("at rest", W, 30, s -> {
            s.add(new CopyFieldComponent(new Box("a", 8, 8, 174, 16), "-4172144997902289642"));
        });
        stage.state("clicked: copied", W, 44, s -> {
            TextFieldComponent.clipboard.set("");
            CopyFieldComponent c = s.add(new CopyFieldComponent(new Box("a", 8, 8, 174, 16), "/tp 120 64 -300"));
            s.click(150, 15);
            s.move(60, 15);
            s.caption(() -> "clipboard: " + TextFieldComponent.clipboard.get());
        });
        stage.state("copied label goes back after 1.5 s", W, 30, s -> {
            CopyFieldComponent c = s.add(new CopyFieldComponent(new Box("a", 8, 8, 174, 16), "/tp 120 64 -300"));
            s.click(150, 15);
            s.move(180, 28);
            s.settle(CopyFieldComponent.DONE_MS + 100);
        });
        stage.state("keyboard: Tab then Enter", W, 30, s -> {
            s.add(new CopyFieldComponent(new Box("a", 8, 8, 174, 16), "play.example.net"));
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
        });
        stage.state("long text cut, tooltip whole", W, 60, s -> {
            s.add(new CopyFieldComponent(new Box("a", 8, 8, 120, 16), "a very long invite code that does not fit"));
            s.move(40, 15);
            s.settle(1500);
        });
        stage.state("disabled", W, 30, s -> {
            s.add(new CopyFieldComponent(new Box("a", 8, 8, 174, 16), "1234").active(false));
        });
    }
}
