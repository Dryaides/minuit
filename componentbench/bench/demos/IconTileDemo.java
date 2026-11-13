package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.IconTileComponent;

/** Icon tiles at rest, under the mouse and held, and buttons with a face of their own. */
public final class IconTileDemo implements Demo {
    @Override
    public void states(Stage stage) {
        stage.state("rest", 150, 56, s -> tiles(s));
        stage.state("hover", 150, 56, s -> {
            tiles(s);
            s.move(70, 20);
        });
        stage.state("pressed", 150, 56, s -> {
            tiles(s);
            s.press(70, 20);
        });
        stage.state("tint", 196, 36, s -> {
            s.add(new ButtonComponent(new Box("blue", 8, 8, 86, 20), "Discord", () -> { }).tint(0xFF5865F2));
            s.add(new ButtonComponent(new Box("sky", 102, 8, 86, 20), "Posts", () -> { }).tint(0xFF1DA1F2));
        });
    }

    private static void tiles(bench.api.Scene s) {
        s.add(new IconTileComponent(new Box("a", 8, 8, 40, 40), "Mic", () -> { }).icon(Icon.MIC));
        s.add(new IconTileComponent(new Box("b", 54, 8, 40, 40), "Done", () -> { }).icon(Icon.CHECK));
        s.add(new IconTileComponent(new Box("c", 100, 8, 40, 40), "Close", () -> { }).icon(Icon.CLOSE));
    }
}
