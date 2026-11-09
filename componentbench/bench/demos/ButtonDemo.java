package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;

/** The library's own button: proves the renderer, and shows the shape of a demo. */
public final class ButtonDemo implements Demo {
    @Override
    public void states(Stage stage) {
        stage.state("roles", 196, 62, s -> {
            s.add(new ButtonComponent(new Box("hero", 8, 8, 86, 20), "Hero", () -> { }).role(ButtonComponent.Role.HERO));
            s.add(new ButtonComponent(new Box("primary", 102, 8, 86, 20), "Primary", () -> { }).role(ButtonComponent.Role.PRIMARY));
            s.add(new ButtonComponent(new Box("secondary", 8, 34, 86, 20), "Secondary", () -> { }).role(ButtonComponent.Role.SECONDARY));
            s.add(new ButtonComponent(new Box("destructive", 102, 34, 86, 20), "Destroy", () -> { }).role(ButtonComponent.Role.DESTRUCTIVE));
        });
        stage.state("hover", 110, 36, s -> {
            s.add(new ButtonComponent(new Box("b", 8, 8, 94, 20), "Hover me", () -> { }).role(ButtonComponent.Role.PRIMARY));
            s.move(50, 18);
        });
        stage.state("pressed", 110, 36, s -> {
            s.add(new ButtonComponent(new Box("b", 8, 8, 94, 20), "Held down", () -> { }).role(ButtonComponent.Role.PRIMARY));
            s.press(50, 18);
        });
        stage.state("disabled", 110, 36, s ->
                s.add(new ButtonComponent(new Box("b", 8, 8, 94, 20), "Disabled", () -> { }).active(false)));
        stage.state("tooltip", 260, 70, s -> {
            s.add(new ButtonComponent(new Box("b", 8, 42, 94, 20), "Save", () -> { })
                    .tooltip("Save", "Writes the settings to disk"));
            s.move(50, 50);
            s.settle(1500);
        });
    }
}
