package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.TransferComponent;
import java.util.List;

/** Two lists, moving rows across. */
public final class TransferDemo implements Demo {
    private static final int W = 220;
    private static final List<String> MOBS = List.of("Zombie", "Skeleton", "Creeper", "Spider", "Enderman",
            "Witch", "Slime", "Phantom", "Blaze", "Ghast");

    private static TransferComponent<String> make(int h) {
        return new TransferComponent<String>(new Box("t", 8, 8, 204, h), MOBS, List.of("Creeper", "Phantom"), m -> m,
                l -> { }).titles("Allowed", "Blocked").emptyText("Nothing here");
    }

    @Override
    public void states(Stage stage) {
        stage.state("at rest, left list scrolls", W, 104, s -> {
            s.add(make(88));
        });
        stage.state("two checked, hover on the button", W, 104, s -> {
            TransferComponent<String> t = s.add(make(88));
            s.click(20, 8 + 12 + 2 + 6);
            s.click(20, 8 + 12 + 2 + 13 + 13 + 6);
            s.move(110, 8 + 12 + 38 - 9);
        });
        stage.state("moved right, kept in order", W, 104, s -> {
            TransferComponent<String> t = s.add(make(88));
            s.click(20, 8 + 12 + 2 + 6);
            s.click(20, 8 + 12 + 2 + 13 + 13 + 6);
            s.click(110, 8 + 12 + 38 - 9);
            s.move(215, 100);
        });
        stage.state("keyboard: Tab, Down, Space", W, 104, s -> {
            s.add(make(88));
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.SPACE);
        });
        stage.state("scrolled to the end", W, 104, s -> {
            s.add(make(88));
            s.scroll(40, 60, -10);
        });
        stage.state("empty side, disabled", W, 104, s -> {
            TransferComponent<String> t = s.add(new TransferComponent<String>(new Box("t", 8, 8, 204, 88),
                    List.of("Zombie", "Skeleton"), List.of(), m -> m, l -> { }).titles("Allowed", "Blocked")
                    .emptyText("Nothing here").active(false));
        });
    }
}
