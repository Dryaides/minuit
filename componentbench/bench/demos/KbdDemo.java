package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.KbdComponent;
import dev.dryaides.minuit.KeyBinding;
import dev.dryaides.minuit.KeyPress;
import java.util.List;

/** Key caps in a help line. */
public final class KbdDemo implements Demo {
    private static final int W = 190;
    private static final int H = KbdComponent.HEIGHT;

    @Override
    public void states(Stage stage) {
        stage.state("single keys", W, 30, s -> {
            String[] keys = {"E", "W", "Esc", "Shift", "Tab", "F3"};
            int x = 8;
            for (String key : keys) {
                KbdComponent k = s.add(new KbdComponent(new Box(key, x, 8, 0, H), key));
                x += k.naturalWidth() + 4;
            }
        });
        stage.state("combinations", W, 30, s -> {
            KbdComponent a = s.add(new KbdComponent(new Box("a", 8, 8, 0, H), "Ctrl", "F"));
            s.add(new KbdComponent(new Box("b", 8 + a.naturalWidth() + 10, 8, 0, H), "F3", "Shift", "H"));
        });
        stage.state("in a help line", W, 30, s -> {
            s.paint((d, t) -> d.text("Press", 8, 11, t.palette().text()));
            s.add(new KbdComponent(new Box("k", 39, 8, 0, H), "E"));
            s.paint((d, t) -> d.text("to open the bag", 56, 11, t.palette().text()));
        });
        stage.state("pressed", W, 30, s -> {
            s.add(new KbdComponent(new Box("a", 8, 8, 0, H), "Space"));
            s.add(new KbdComponent(new Box("b", 50, 8, 0, H), "Space").pressed(true));
        });
        stage.state("follows a binding", W, 30, s -> {
            Binding zoom = new Binding("C");
            s.add(new KbdComponent(new Box("a", 8, 8, 0, H), zoom));
            Binding rebound = new Binding("C");
            s.add(new KbdComponent(new Box("b", 40, 8, 0, H), rebound));
            rebound.name = "Mouse Button 4";
            s.add(new KbdComponent(new Box("c", 150, 8, 0, H), new Binding("")));
        });
        stage.state("wide box centres, disabled", W, 30, s -> {
            s.add(new KbdComponent(new Box("a", 8, 8, 80, H), "Alt", "R"));
            s.add(new KbdComponent(new Box("b", 100, 8, 0, H), "Q").active(false));
        });
    }

    private static final class Binding implements KeyBinding {
        String name;

        Binding(String name) {
            this.name = name;
        }

        @Override public String keyName() { return name; }
        @Override public boolean isDefault() { return true; }
        @Override public List<String> conflicts() { return List.of(); }
        @Override public void bind(KeyPress key) { }
        @Override public void unbind() { }
        @Override public void reset() { }
    }
}
