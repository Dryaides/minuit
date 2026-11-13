package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.TreeComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The tree, driven by scripted input. The line under each tree shows what the script actually got. */
public final class TreeDemo implements Demo {
    private static final Map<String, List<String>> KIDS = Map.of(
            "assets", List.of("models", "textures", "lang", "pack.mcmeta"),
            "models", List.of("block", "item"),
            "block", List.of("stone.json", "dirt.json"),
            "textures", List.of("gui", "entity"),
            "gui", List.of("widgets.png"),
            "src", List.of("main", "test"),
            "main", List.of("App.java", "Util.java"));
    private static final List<String> ROOTS = List.of("assets", "src", "README.md");
    private static final Function<String, List<String>> CHILDREN = n -> KIDS.getOrDefault(n, List.of());

    /** Events seen, shown by the state so a picture proves the logic. */
    private static final class Log {
        final List<String> lines = new ArrayList<>();
        void add(String line) { lines.add(line); }
        @Override public String toString() { return String.join(" ", lines); }
    }

    private static TreeComponent<String> tree(Box box) {
        return new TreeComponent<>(box, ROOTS, CHILDREN, n -> n);
    }

    private static TreeComponent<String> logged(Box box, Log log) {
        return tree(box)
                .icon(n -> KIDS.containsKey(n) ? Icon.FOLDER : null)
                .onSelect(n -> log.add("sel:" + n))
                .onActivate(n -> log.add("act:" + n))
                .onToggle((n, open) -> log.add((open ? "open:" : "fold:") + n));
    }

    /** Prints a value under the tree. */
    private static void show(Scene s, int y, java.util.function.Supplier<String> text) {
        s.paint((d, t) -> d.textFit(text.get(), 6, y, s.panel().width() - 12, t.palette().text()));
    }

    @Override
    public void states(Stage stage) {

        stage.state("items", 180, 88, s -> {
            dev.dryaides.minuit.TreeComponent<String> t = s.add(new dev.dryaides.minuit.TreeComponent<String>(new Box("t", 6, 6, 168, 76),
                    java.util.List.of("minecraft:bundle"), n -> n.equals("minecraft:bundle") ? java.util.List.of("minecraft:diamond", "minecraft:iron_pickaxe", "minecraft:oak_log", "minecraft:redstone", "minecraft:apple") : java.util.List.of(), Mc::name)
                    .iconArt(n -> dev.dryaides.minuit.ItemView.of(n)));
            t.expand("minecraft:bundle");
            s.move(80, 26);
        });
        stage.state("items disabled", 180, 64, s -> {
            dev.dryaides.minuit.TreeComponent<String> t = s.add(new dev.dryaides.minuit.TreeComponent<String>(new Box("t", 6, 6, 168, 52),
                    java.util.List.of("minecraft:bundle"), n -> n.equals("minecraft:bundle") ? java.util.List.of("minecraft:diamond", "minecraft:iron_pickaxe", "minecraft:oak_log", "minecraft:redstone", "minecraft:apple") : java.util.List.of(), Mc::name)
                    .iconArt(n -> dev.dryaides.minuit.ItemView.of(n)).active(false));
            t.expand("minecraft:bundle");
        });
        stage.state("default", 150, 76, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 138, 64)));
            t.expand("assets");
        });

        stage.state("hover row", 150, 64, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 138, 52)).icon(n -> KIDS.containsKey(n) ? Icon.FOLDER : null));
            t.expand("assets");
            s.move(70, 25);
        });

        stage.state("focused by keys", 150, 64, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 138, 52)));
            t.expand("assets");
            s.focus(t);
            s.key(InputKey.DOWN);
        });

        stage.state("tab focus, nothing selected", 150, 64, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 138, 52)));
            t.expand("assets");
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            t.reveal("textures");
        });

        stage.state("disabled", 170, 64, s -> {
            Log log = new Log();
            TreeComponent<String> t = s.add(logged(new Box("t", 6, 6, 158, 40), log));
            t.expand("assets");
            t.select("models");
            t.active(false);
            s.click(40, 25);
            s.click(11, 25);
            s.focus(t);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
            s.scroll(40, 25, -1);
            show(s, 50, () -> "ignored: [" + log + "]");
        });

        stage.state("click arrow folds", 280, 88, s -> {
            Log log = new Log();
            TreeComponent<String> t = s.add(logged(new Box("t", 6, 6, 268, 64), log));
            s.click(13, 13);
            s.click(13, 13);
            s.click(13, 13);
            show(s, 74, () -> log.toString());
        });

        stage.state("click row selects", 190, 88, s -> {
            Log log = new Log();
            TreeComponent<String> t = s.add(logged(new Box("t", 6, 6, 178, 64), log));
            t.expand("assets");
            s.click(60, 25);
            s.waitMs(1000);
            s.click(60, 25);
            show(s, 74, () -> log + " sel=" + t.selected());
        });

        stage.state("double click activates", 190, 88, s -> {
            Log log = new Log();
            TreeComponent<String> t = s.add(logged(new Box("t", 6, 6, 178, 64), log));
            t.expand("assets");
            s.click(60, 37);
            s.waitMs(100);
            s.click(60, 37);
            show(s, 74, () -> log.toString());
        });

        stage.state("right expands then enters", 320, 88, s -> {
            Log log = new Log();
            TreeComponent<String> t = s.add(logged(new Box("t", 6, 6, 308, 64), log));
            s.focus(t);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            show(s, 74, () -> log.toString());
        });

        stage.state("left folds then parent", 320, 88, s -> {
            Log log = new Log();
            TreeComponent<String> t = s.add(logged(new Box("t", 6, 6, 308, 64), log));
            t.expandAll();
            t.select("block");
            s.focus(t);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            show(s, 74, () -> log.toString());
        });

        stage.state("enter activates once", 300, 88, s -> {
            Log log = new Log();
            TreeComponent<String> t = s.add(logged(new Box("t", 6, 6, 288, 64), log));
            s.focus(t);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
            s.key(InputKey.HOME);
            s.key(InputKey.END);
            show(s, 74, () -> log.toString());
        });

        stage.state("kept across setRoots", 220, 88, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 208, 64)));
            t.expand("src");
            t.select("main");
            t.setRoots(List.of("src", "new.txt"));
        });

        stage.state("expandAll and scroll", 190, 88, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 178, 64)));
            t.expandAll();
            s.scroll(60, 30, -2);
            s.click(60, 30);
        });

        stage.state("drag scroll bar", 190, 88, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 178, 64)));
            t.expandAll();
            s.hold(180, 12, 180, 60);
            show(s, 74, () -> "thumb dragged to the end");
        });

        stage.state("collapseAll", 190, 64, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 178, 40)));
            t.expandAll();
            t.collapseAll();
        });

        stage.state("reveal", 230, 88, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 218, 64)));
            boolean hit = t.reveal("widgets.png");
            boolean miss = t.reveal("nothing");
            s.focus(t);
            show(s, 74, () -> "found=" + hit + " missing=" + miss + " cur=" + t.cursor());
        });

        stage.state("fold half way", 190, 88, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 178, 76)));
            s.click(13, 13);
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("fold closing half way", 190, 88, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 178, 76)));
            t.expand("assets");
            s.click(13, 13);
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("reduced motion snaps", 190, 88, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 178, 76)));
            HostState.reducedMotion(true);
            s.click(13, 13);
            HostState.reducedMotion(false);
            s.settle(0);
        });

        stage.state("fold hides selection", 230, 88, s -> {
            Log log = new Log();
            TreeComponent<String> t = s.add(logged(new Box("t", 6, 6, 218, 64), log));
            t.expandAll();
            t.select("block");
            s.click(22, 25);
            show(s, 74, () -> log + " sel=" + t.selected() + " cur=" + t.cursor());
        });

        stage.state("row tooltip", 210, 70, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 34, 120, 30))
                    .rowTooltip(n -> List.of(n, "Row tooltip")));
            s.move(50, 40);
            s.settle(1500);
        });

        stage.state("hidden takes no input", 190, 40, s -> {
            Log log = new Log();
            TreeComponent<String> t = s.add(logged(new Box("t", 6, 6, 178, 12), log));
            t.visible = false;
            s.click(13, 12);
            s.click(60, 12);
        });

        stage.state("draws only visible rows", 190, 88, s -> {
            int[] calls = new int[1];
            s.paint((d, th) -> calls[0] = 0);
            TreeComponent<Integer> t = s.add(new TreeComponent<Integer>(new Box("t", 6, 6, 178, 64), List.of(0),
                    n -> n == 0 ? wide() : List.of(), n -> "node " + n)
                    .renderer((d, th, n, row, depth, open, hovered, selected) -> {
                        calls[0]++;
                        d.textFit("node " + n, row.x(), row.y() + 2, row.w(), th.palette().text());
                    }));
            t.expand(0);
            t.setScroll(600_000);
        });

        stage.state("empty", 150, 40, s -> {
            TreeComponent<String> t = s.add(new TreeComponent<String>(new Box("t", 6, 6, 138, 28), List.of(), CHILDREN, n -> n)
                    .emptyText("Nothing here"));
            s.focus(t);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
            s.click(40, 18);
        });

        stage.state("long text", 290, 84, s -> {
            TreeComponent<String> t = s.add(new TreeComponent<>(new Box("t", 6, 42, 138, 40),
                    List.of("a very long folder name that cannot possibly fit"),
                    n -> n.length() > 20 ? List.of("short", "another name that is also far too long") : List.of(), n -> n));
            t.expandAll();
            s.move(70, 49);
            s.settle(1500);
        });

        stage.state("10k deep, 100k wide", 190, 88, s -> {
            TreeComponent<Integer> t = s.add(new TreeComponent<Integer>(new Box("t", 6, 6, 178, 64), List.of(0),
                    n -> n < 10_000 ? List.of(n + 1) : n == 10_000 ? wide() : List.of(), n -> "node " + n));
            t.expandAll();
            s.key(InputKey.END);
            t.setScroll((10_001 + 100_000) * 12d);
        });

        stage.state("cycle", 190, 88, s -> {
            TreeComponent<String> t = s.add(new TreeComponent<String>(new Box("t", 6, 6, 178, 64), List.of("a"),
                    n -> n.equals("a") ? List.of("b") : n.equals("b") ? List.of("a", "c") : List.of(), n -> n));
            t.expandAll();
        });

        stage.state("custom row", 190, 64, s -> {
            TreeComponent<String> t = s.add(tree(new Box("t", 6, 6, 178, 52))
                    .rowHeight(16)
                    .renderer((d, theme, node, row, depth, open, hovered, selected) -> {
                        int ink = selected ? theme.palette().primaryText() : theme.palette().text();
                        d.textFit(node, row.x(), row.y() + 4, row.w() - 22, ink);
                        d.textFitRight("d" + depth, row.right() - 3, row.y() + 4, 18, ink);
                    }));
            t.expand("assets");
            s.click(70, 25);
        });
    }

    private static List<Integer> wide() {
        List<Integer> out = new ArrayList<>(100_000);
        for (int i = 0; i < 100_000; i++) {
            out.add(20_000 + i);
        }
        return out;
    }
}
