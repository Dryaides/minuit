package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.SliderComponent;

/** The library's slider, moved by scripted input. */
public final class SliderDemo implements Demo {
    @Override
    public void states(Stage stage) {
        stage.state("start value", 180, 36, s ->
                s.add(new SliderComponent(new Box("v", 8, 10, 164, 16), 0, 100, 25, v -> { })));
        stage.state("dragged to 75%", 180, 36, s -> {
            s.add(new SliderComponent(new Box("v", 8, 10, 164, 16), 0, 100, 0, v -> { }));
            s.drag(10, 18, 8 + 164 * 3 / 4, 18);
        });
        stage.state("still dragging", 180, 36, s -> {
            s.add(new SliderComponent(new Box("v", 8, 10, 164, 16), 0, 100, 0, v -> { }));
            s.hold(10, 18, 90, 18);
        });
        stage.state("disabled", 180, 36, s -> {
            SliderComponent slider = s.add(new SliderComponent(new Box("v", 8, 10, 164, 16), 0, 100, 50, v -> { }));
            slider.active = false;
        });
        stage.state("arrow keys", 180, 36, s -> {
            SliderComponent slider = s.add(new SliderComponent(new Box("v", 8, 10, 164, 16), 0, 100, 40, v -> { })
                    .label(v -> v + " dB"));
            s.focus(slider);
            for (int i = 0; i < 5; i++) {
                s.key(InputKey.RIGHT);
            }
        });
    }
}
