package bench.api;

import java.util.function.Consumer;

public interface Stage {
    /**
     * One picture, drawn once per theme. The script builds the widgets and plays its input from top
     * to bottom; the picture shows the result. Coordinates are from the top left of the picture.
     */
    void state(String name, int width, int height, Consumer<Scene> script);
}
