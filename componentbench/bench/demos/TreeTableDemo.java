package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.TableComponent;
import dev.dryaides.minuit.TreeTableComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The folding table, driven by scripted input. */
public final class TreeTableDemo implements Demo {
    private static final int W = 240;
    private static final int H = 110;
    private static final int ROW = 14;

    private record Task(String name, String owner, int days, List<Task> kids) {
        Task(String name, String owner, int days, Task... kids) {
            this(name, owner, days, List.of(kids));
        }
    }

    private static final Task WALLS = new Task("Walls", "Ana", 3);
    private static final Task ROOF = new Task("Roof", "Ben", 2);
    private static final Task BASE = new Task("Base", "Ana", 5, WALLS, ROOF);
    private static final Task FARM = new Task("Farm", "Cleo", 4);
    private static final Task BUILD = new Task("Build the keep", "Ana", 9, BASE, FARM);
    private static final Task MINE = new Task("Dig a mine", "Ben", 6);
    private static final List<Task> ROOTS = List.of(BUILD, MINE);

    private static TreeTableComponent<Task> table(int height) {
        return new TreeTableComponent<Task>(new Box("t", 6, 6, W - 12, height), ROW, ROOTS, Task::kids)
                .treeColumn("Task", Task::name)
                .column("Owner", c -> c.text(Task::owner).fixed(44))
                .column("Days", c -> c.text(t -> Integer.toString(t.days())).fixed(34).align(TableComponent.Align.RIGHT));
    }

    private static TreeTableComponent<Task> table() {
        return table(H - 12);
    }

    private static List<String> names(TreeTableComponent<Task> c) {
        List<String> out = new ArrayList<>();
        for (Task t : c.view()) {
            out.add(t.name());
        }
        return out;
    }

    @Override
    public void states(Stage stage) {

        stage.state("items", 220, 100, s -> {
            dev.dryaides.minuit.TreeTableComponent<String> t = s.add(new dev.dryaides.minuit.TreeTableComponent<String>(new Box("t", 6, 6, 208, 88), 18,
                    java.util.List.of("minecraft:chest", "minecraft:barrel"), n -> n.equals("minecraft:chest") ? java.util.List.of("minecraft:diamond", "minecraft:iron_pickaxe", "minecraft:oak_log", "minecraft:redstone", "minecraft:apple") : java.util.List.of())
                    .treeColumn("Name", n -> n.substring(n.indexOf(':') + 1).replace('_', ' '), n -> dev.dryaides.minuit.ItemView.of(n, n.endsWith("log") ? 12 : 1))
                    .textColumn("Mod", n -> n.substring(0, n.indexOf(':'))));
            t.expand("minecraft:chest");
        });
        stage.state("folded", W, H, s -> {
            TreeTableComponent<Task> c = s.add(table());
        });

        stage.state("open two levels", W, H, s -> {
            TreeTableComponent<Task> c = s.add(table());
            c.expand(BUILD);
            c.expand(BASE);
        });

        stage.state("click the arrow opens", W, H, s -> {
            List<String> heard = new ArrayList<>();
            TreeTableComponent<Task> c = s.add(table().onToggle((t, open) -> heard.add(t.name() + "=" + open)));
            s.waitMs(16);
            Box arrow = BenchAccess.arrowBox(c, 0);
            s.click(arrow.x() + 4, arrow.y() + 6);
            s.click(arrow.x() + 4, arrow.y() + 6);
            s.click(arrow.x() + 4, arrow.y() + 6);
            s.move(-100, -100);
        });

        stage.state("click the row selects", W, H, s -> {
            TreeTableComponent<Task> c = s.add(table());
            c.expand(BUILD);
            s.waitMs(16);
            Box arrow = BenchAccess.arrowBox(c, 1);
            s.click(arrow.x() + 40, arrow.y() + 6);
            s.move(-100, -100);
        });

        stage.state("hover row", W, H, s -> {
            TreeTableComponent<Task> c = s.add(table());
            c.expand(BUILD);
            s.waitMs(16);
            Box arrow = BenchAccess.arrowBox(c, 1);
            s.move(arrow.x() + 40, arrow.y() + 6);
        });

        stage.state("double click folds with no callback", W, H, s -> {
            TreeTableComponent<Task> c = s.add(table());
            s.waitMs(16);
            Box arrow = BenchAccess.arrowBox(c, 0);
            s.click(arrow.x() + 40, arrow.y() + 6);
            s.click(arrow.x() + 40, arrow.y() + 6);
            s.move(-100, -100);
        });

        stage.state("double click calls back when there is one", W, H, s -> {
            List<Task> heard = new ArrayList<>();
            TreeTableComponent<Task> c = s.add(table().onActivate(heard::add));
            s.waitMs(16);
            Box arrow = BenchAccess.arrowBox(c, 0);
            s.click(arrow.x() + 40, arrow.y() + 6);
            s.click(arrow.x() + 40, arrow.y() + 6);
            s.move(-100, -100);
        });

        stage.state("keys open, walk and fold", W, H, s -> {
            TreeTableComponent<Task> c = s.add(table());
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
        });

        stage.state("a fold hands the selection up", W, H, s -> {
            TreeTableComponent<Task> c = s.add(table());
            c.expandAll();
            c.select(ROOF);
            c.collapse(BUILD);
        });

        stage.state("expand all overflows and scrolls", W, 74, s -> {
            TreeTableComponent<Task> c = s.add(table(62));
            c.expandAll();
            s.scroll(60, 40, -1);
            s.move(-100, -100);
        });

        stage.state("deep and long, tooltip whole", W, 90, s -> {
            Task leaf = new Task("A really very long name for a task this far down", "Ana", 1);
            Task mid = new Task("Middle", "Ben", 2, leaf);
            TreeTableComponent<Task> c = new TreeTableComponent<Task>(new Box("t", 6, 6, W - 12, 78), ROW, List.of(mid), Task::kids)
                    .treeColumn("Task", Task::name)
                    .column("Owner", o -> o.text(Task::owner).fixed(44));
            s.add(c);
            c.expandAll();
            s.waitMs(16);
            Box arrow = BenchAccess.arrowBox(c, 1);
            s.move(arrow.x() + 40, arrow.y() + 6);
            s.settle(700);
        });

        stage.state("a loop in the data shows once", W, 60, s -> {
            List<Task> ring = new ArrayList<>();
            Task loop = new Task("Loop", "Ana", 1, ring);
            ring.add(loop);
            TreeTableComponent<Task> c = new TreeTableComponent<Task>(new Box("t", 6, 6, W - 12, 48), ROW, List.of(loop), Task::kids)
                    .treeColumn("Task", Task::name);
            s.add(c);
            c.expandAll();
        });

        stage.state("a sortable column is refused", W, 40, s -> {
            boolean refused = false;
            try {
                s.add(table(28)).column("Bad", o -> o.text(Task::name).comparator(Comparator.comparing(Task::name)));
            } catch (IllegalArgumentException e) {
                refused = true;
            }
        });

        stage.state("no rows", W, 50, s -> {
            TreeTableComponent<Task> c = new TreeTableComponent<Task>(new Box("t", 6, 6, W - 12, 38), ROW, List.of(), Task::kids)
                    .treeColumn("Task", Task::name).emptyText("Nothing to do");
            s.add(c);
            s.focus(c);
            s.key(InputKey.RIGHT);
            s.key(InputKey.DOWN);
        });

        stage.state("disabled ignores input", W, H, s -> {
            TreeTableComponent<Task> c = s.add(table());
            c.expand(BUILD);
            c.select(BASE);
            c.active(false);
            s.waitMs(16);
            Box arrow = BenchAccess.arrowBox(c, 0);
            s.click(arrow.x() + 4, arrow.y() + 6);
            s.focus(c);
            s.key(InputKey.RIGHT);
        });

        stage.state("hidden and tiny", W, 40, s -> {
            TreeTableComponent<Task> gone = s.add(new TreeTableComponent<Task>(new Box("a", 4, 4, 60, 30), ROW, ROOTS, Task::kids).treeColumn("Task", Task::name));
            gone.visible = false;
            s.add(new TreeTableComponent<Task>(new Box("b", 70, 4, 40, 30), ROW, ROOTS, Task::kids).treeColumn("Task", Task::name).column("Owner", o -> o.text(Task::owner).fixed(44)));
            s.add(new TreeTableComponent<Task>(new Box("c", 116, 4, 6, 6), ROW, ROOTS, Task::kids).treeColumn("Task", Task::name));
        });
    }
}
