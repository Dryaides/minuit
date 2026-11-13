package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.DropdownComponent;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.ScrollComponent;
import dev.dryaides.minuit.Shapes;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.SplitPaneComponent;
import dev.dryaides.minuit.SplitPaneComponent.Orientation;
import dev.dryaides.minuit.SplitPaneComponent.Side;
import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.TreeComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The split, driven by scripted input. The line under
 * a split, when there is one, shows what the script actually got.
 */
public final class SplitPaneDemo implements Demo {
    private static final int W = 200;
    private static final int H = 72;
    private static final Map<String, List<String>> KIDS = Map.of(
            "assets", List.of("models", "textures", "lang", "pack.mcmeta"),
            "models", List.of("block", "item"),
            "src", List.of("main", "test"));

    /** A pane that shows its exact box and keeps what it was given. */
    private static final class Tile extends BaseComponent {
        final String name;
        final List<String> keys = new ArrayList<>();
        final StringBuilder typed = new StringBuilder();
        boolean grabs = true;
        boolean hoveredLast;
        int clicks;
        int releases;
        int drags;
        int ticks;
        int wheel;

        Tile(String name) {
            super(new Box(name, 0, 0, 10, 10));
            this.name = name;
        }

        @Override
        protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
            hoveredLast = hovered;
            Palette p = t.palette();
            t.frame().section(d, p, x, y, w, h);
            if (hovered) {
                d.fill(x, y, x + w, y + h, Shapes.alpha(p.text(), 24));
            }
            d.textFit(name, x + 3, y + (h - 8) / 2, w - 6, active ? p.text() : p.disabledText());
        }

        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (!isOver(mx, my) || !active) {
                return false;
            }
            clicks++;
            return true;
        }

        @Override public boolean mouseReleased(double mx, double my, int button) { releases++; return false; }
        @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { drags++; return false; }
        @Override public boolean mouseScrolled(double mx, double my, double amount) { wheel++; return isOver(mx, my); }
        @Override public void tick() { ticks++; }
        @Override protected boolean takesFocus() { return grabs && active; }

        @Override public boolean keyPressed(InputKey key, boolean shift) {
            keys.add(key.name());
            return key == InputKey.ENTER;
        }

        @Override public boolean charTyped(int codePoint) {
            typed.appendCodePoint(codePoint);
            return true;
        }
    }

    /** Draws past its own box on every side, which only the pane's clipping can stop. */
    private static final class Bleeder extends BaseComponent {
        Bleeder() {
            super(0, 0, 10, 10);
        }

        @Override
        protected void render(Draw d, Theme t, int mx, int my, boolean hovered) {
            d.fill(x - 40, y - 40, x + w + 40, y + h + 40, Shapes.alpha(t.palette().primary(), 0x80));
            d.text("drawn far past its own box on both sides", x - 40, y + 2, t.palette().text());
        }
    }

    /** A line under the split, painted first: a widget added after the split would take the panel's focus away. */
    private static final class Rig {
        final Scene s;
        final List<String> log = new ArrayList<>();
        Supplier<String> line = () -> "";

        Rig(Scene s, int y) {
            this.s = s;
            s.paint((d, t) -> d.textFit(line.get(), 6, y, s.panel().width() - 12, t.palette().text()));
        }

        SplitPaneComponent logged(SplitPaneComponent split) {
            return split
                    .onChange(v -> log.add("c" + v))
                    .onCommit(v -> log.add("C" + v))
                    .onCollapse((side, on) -> log.add((on ? "fold:" : "open:") + side));
        }

        String logText() {
            return String.join(" ", log);
        }
    }

    private static SplitPaneComponent tiles(Box box, Orientation o) {
        return new SplitPaneComponent(box, o, new Tile("First"), new Tile("Second"));
    }

    private static Tile first(SplitPaneComponent split) {
        return (Tile) split.first();
    }

    private static Tile second(SplitPaneComponent split) {
        return (Tile) split.second();
    }

    private static int mx(Box b) {
        return b.x() + b.w() / 2;
    }

    private static int my(Box b) {
        return b.y() + b.h() / 2;
    }

    private static void grabBy(Scene s, SplitPaneComponent split, int by) {
        Box g = BenchAccess.dividerBox(split);
        boolean across = split.orientation() == Orientation.HORIZONTAL;
        s.hold(mx(g), my(g), mx(g) + (across ? by : 0), my(g) + (across ? 0 : by));
    }

    private static void dragBy(Scene s, SplitPaneComponent split, int by) {
        grabBy(s, split, by);
        s.release(s.mouseX(), s.mouseY());
    }

    /** Clicks a side's arrow: the first cell is at the divider's start, the second next to it. */
    private static void clickArrow(Scene s, SplitPaneComponent split, int cell) {
        Box g = BenchAccess.dividerBox(split);
        int along = 1 + cell * 9 + 4;
        if (split.orientation() == Orientation.HORIZONTAL) {
            s.click(mx(g), g.y() + along);
        } else {
            s.click(g.x() + along, my(g));
        }
    }

    private static void doubleClick(Scene s, SplitPaneComponent split) {
        Box g = BenchAccess.dividerBox(split);
        s.click(mx(g), my(g));
        s.waitMs(100);
        s.click(mx(g), my(g));
    }

    private static List<String> stopNames(Scene s) {
        List<String> out = new ArrayList<>();
        for (BaseComponent stop : s.panel().stops()) {
            out.add(stop instanceof Tile tile ? tile.name : stop.getClass().getSimpleName());
        }
        return out;
    }

    private static TreeComponent<String> tree() {
        TreeComponent<String> tree = new TreeComponent<>(new Box("tree", 0, 0, 10, 10), List.of("assets", "src", "README.md"),
                n -> KIDS.getOrDefault(n, List.of()), n -> n);
        tree.icon(n -> KIDS.containsKey(n) ? Icon.FOLDER : null);
        tree.expand("assets");
        return tree;
    }

    private static ScrollComponent scrollOfButtons(int count, List<String> pressed) {
        ScrollComponent scroll = new ScrollComponent(new Box("scroll", 0, 0, 90, 56));
        for (int i = 0; i < count; i++) {
            String name = "Button " + (i + 1);
            scroll.add(new ButtonComponent(new Box("b" + i, 2, 2 + i * 22, 76, 20), name, () -> pressed.add(name)));
        }
        return scroll;
    }

    @Override
    public void states(Stage stage) {
        looks(stage);
        dragging(stage);
        limits(stage);
        keyboard(stage);
        resetAndFold(stage);
        resizing(stage);
        nesting(stage);
        forwarding(stage);
        edges(stage);
    }

    // ---- at rest and under the pointer ----

    private void looks(Stage stage) {
        stage.state("default", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
        });

        stage.state("vertical", 120, 110, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 108, 98), Orientation.VERTICAL));
        });

        stage.state("framed", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).framed(true));
        });

        stage.state("framed focused", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).framed(true));
            s.focus(p);
        });

        stage.state("real widgets", 270, 100, s -> {
            TreeComponent<String> t = tree();
            List<String> pressed = new ArrayList<>();
            SplitPaneComponent right = new SplitPaneComponent(new Box("r", 0, 0, 10, 10), Orientation.VERTICAL,
                    new TextFieldComponent(new Box("f", 0, 0, 10, 16), "A note", v -> { }),
                    new ButtonComponent(new Box("b", 0, 0, 10, 10), "Apply", () -> pressed.add("apply")));
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 258, 88), Orientation.HORIZONTAL, t, right)
                    .fraction(0.45).collapsible(Side.FIRST));
            s.click(mx(BenchAccess.paneBox(p, Side.SECOND)), BenchAccess.paneBox(p, Side.SECOND).bottom() - 8);
        });

        stage.state("hover divider", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            Box g = BenchAccess.dividerBox(p);
            s.move(mx(g), my(g));
        });

        stage.state("hover divider vertical", 120, 80, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 108, 68), Orientation.VERTICAL));
            Box g = BenchAccess.dividerBox(p);
            s.move(mx(g), my(g));
        });

        stage.state("hover pane", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            s.move(40, 30);
        });

        stage.state("arrows at rest", W, 72, s ->
                s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST, Side.SECOND)));

        stage.state("arrows at rest vertical", 120, 80, s ->
                s.add(tiles(new Box("s", 6, 6, 108, 68), Orientation.VERTICAL).collapsible(Side.FIRST, Side.SECOND)));

        stage.state("hover arrow", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST));
            Box g = BenchAccess.dividerBox(p);
            s.move(mx(g), g.y() + 5);
        });

        stage.state("thin divider", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).dividerSize(1));
            int before = p.position();
            s.drag(BenchAccess.dividerBox(p).x() + 2, 30, BenchAccess.dividerBox(p).x() + 22, 30);
        });

        stage.state("wide divider and no padding", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL)
                    .dividerSize(11).padding(0).collapsible(Side.SECOND));
        });
    }

    // ---- the mouse ----

    private void dragging(Stage stage) {
        stage.state("held", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL)));
            grabBy(s, p, -30);
            r.line = () -> r.log.size() + " changes, held";
        });

        stage.state("held vertical", 120, 96, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 108, 84), Orientation.VERTICAL));
            grabBy(s, p, 16);
        });

        stage.state("released", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL)));
            dragBy(s, p, 30);
            s.waitMs(500);
            r.line = () -> r.log.get(r.log.size() - 1);
        });

        stage.state("press without moving", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL)));
            Box g = BenchAccess.dividerBox(p);
            s.click(mx(g), my(g));
            r.line = () -> "fired: [" + r.logText() + "]";
        });

        stage.state("grab keeps its offset", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            Box g = BenchAccess.dividerBox(p);
            s.drag(g.x(), my(g), g.x() + 1, my(g));
            g = BenchAccess.dividerBox(p);
            s.drag(g.right() - 1, my(g), g.right() - 2, my(g));
        });

        stage.state("past the box clamps", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            Box g = BenchAccess.dividerBox(p);
            s.drag(mx(g), my(g), 900, my(g));
        });

        stage.state("to the left edge", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            Box g = BenchAccess.dividerBox(p);
            s.drag(mx(g), my(g), -300, my(g));
            s.click(10, 30);
        });

        stage.state("right button does nothing", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST)));
            Box g = BenchAccess.dividerBox(p);
            s.rightClick(mx(g), my(g));
            s.rightClick(mx(g), g.y() + 5);
            r.line = () -> "fired: [" + r.logText() + "]";
        });

        stage.state("escape drops a drag", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL)));
            grabBy(s, p, 40);
            s.key(InputKey.ESCAPE);
            s.release(s.mouseX(), s.mouseY());
            r.line = () -> "pos=" + p.position() + " no commit";
        });

        stage.state("turned off while held", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL)));
            grabBy(s, p, 20);
            p.active = false;
            s.move(150, 30);
            int at = p.position();
            s.drag(150, 30, 170, 30);
            r.line = () -> "pos=" + p.position() + " " + r.log.get(r.log.size() - 1);
        });

        stage.state("hidden while held", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            grabBy(s, p, 20);
            p.visible = false;
            s.hold(120, 30, 170, 30);
            p.visible = true;
            s.release(170, 30);
        });
    }

    // ---- limits ----

    private void limits(Stage stage) {
        stage.state("at the minimum", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).limits(Side.FIRST, 40, 120));
            dragBy(s, p, -200);
            r.line = () -> "first=" + p.position() + ", min 40";
        });

        stage.state("at the maximum", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).limits(Side.FIRST, 40, 120));
            dragBy(s, p, 200);
            r.line = () -> "first=" + p.position() + ", max 120";
        });

        stage.state("second's minimum", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).limits(Side.SECOND, 50, 100));
            dragBy(s, p, 200);
            dragBy(s, p, -200);
            r.line = () -> "second=" + p.secondSize() + ", max 100";
        });

        stage.state("box too small for both minimums", 80, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 60, 60), Orientation.HORIZONTAL)
                    .limits(Side.FIRST, 40, 999).limits(Side.SECOND, 40, 999));
            dragBy(s, p, 10);
        });

        stage.state("uneven minimums in a small box", 80, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 60, 60), Orientation.HORIZONTAL)
                    .limits(Side.FIRST, 90, 999).limits(Side.SECOND, 30, 999));
        });

        stage.state("more room than both maximums", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL)
                    .limits(Side.FIRST, 20, 50).limits(Side.SECOND, 20, 50));
        });

        stage.state("limit set under the start", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL)
                    .fraction(0.1).limits(Side.FIRST, 60, 999));
        });
    }

    // ---- keys ----

    private void keyboard(Stage stage) {
        stage.state("tab reaches the divider", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
        });

        stage.state("tab ring vertical", 120, 80, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 108, 68), Orientation.VERTICAL).collapsible(Side.SECOND));
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
        });

        stage.state("tab ring with arrows", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST, Side.SECOND));
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
        });

        stage.state("tab ring framed", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).framed(true));
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
        });

        stage.state("tab ring on a child", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            s.key(InputKey.TAB);
        });

        stage.state("tab walks and wraps", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            StringBuilder seen = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                s.key(InputKey.TAB);
                seen.append(p.keyboardAt() == p.first() ? "1" : p.keyboardAt() == p.second() ? "2" : "D");
            }
            s.key(InputKey.TAB, true);
            int flags = (p.first().focused ? 1 : 0) + (p.second().focused ? 1 : 0);
        });

        stage.state("arrow keys", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL)));
            s.focus(p);
            s.key(InputKey.RIGHT);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.RIGHT, true);
            r.line = () -> "pos=" + p.position() + " after keys";
        });

        stage.state("arrow keys vertical", 120, 80, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 108, 68), Orientation.VERTICAL).keyStep(3));
            s.focus(p);
            int start = p.position();
            s.key(InputKey.DOWN);
            s.key(InputKey.UP);
            s.key(InputKey.UP);
        });

        stage.state("home and end", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).limits(Side.FIRST, 30, 150)));
            s.focus(p);
            s.key(InputKey.HOME);
            s.key(InputKey.HOME);
            s.key(InputKey.END);
            r.line = () -> r.logText();
        });

        stage.state("cross arrows walk the stops", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            int at = p.position();
            s.key(InputKey.DOWN);
            s.key(InputKey.UP);
        });

        stage.state("clicked divider takes keys", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            Box g = BenchAccess.dividerBox(p);
            s.click(mx(g), my(g));
            s.key(InputKey.RIGHT);
        });

        stage.state("escape lets go of the keys", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            s.focus(p);
            s.key(InputKey.ESCAPE);
            int at = p.position();
            s.key(InputKey.RIGHT);
        });

        stage.state("enter resets when nothing folds", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL)));
            s.focus(p);
            s.key(InputKey.END);
            s.key(InputKey.ENTER);
            s.waitMs(200);
            r.line = () -> r.logText();
        });

        stage.state("selection stays without focus", 240, 84, s -> {
            TreeComponent<String> t = tree();
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 228, 72), Orientation.HORIZONTAL, t, new Tile("Second")));
            s.move(0, 0);
            s.click(t.x + 40, t.y + 20);
            Box g = BenchAccess.dividerBox(p);
            s.click(mx(g), my(g));
            s.waitMs(500);
        });

        stage.state("selection with focus", 240, 84, s -> {
            TreeComponent<String> t = tree();
            s.add(new SplitPaneComponent(new Box("s", 6, 6, 228, 72), Orientation.HORIZONTAL, t, new Tile("Second")));
            s.move(0, 0);
            s.click(t.x + 40, t.y + 20);
            s.key(InputKey.DOWN);
        });
    }

    // ---- double click and folding ----

    private void resetAndFold(Stage stage) {
        stage.state("double click resets", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).fraction(0.25)));
            int home = p.position();
            dragBy(s, p, 60);
            s.waitMs(400);
            doubleClick(s, p);
            r.line = () -> "home=" + p.position() + " " + r.log.get(r.log.size() - 1);
        });

        stage.state("reset half way", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).position(30));
            dragBy(s, p, 120);
            s.waitMs(400);
            doubleClick(s, p);
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("two slow clicks do not reset", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            s.focus(p);
            s.key(InputKey.RIGHT);
            Box g = BenchAccess.dividerBox(p);
            s.click(mx(g), my(g));
            s.waitMs(1000);
            s.click(mx(g), my(g));
        });

        stage.state("reset under keepSize", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).position(50).keepSize(Side.FIRST));
            dragBy(s, p, 40);
            s.waitMs(400);
            doubleClick(s, p);
        });

        stage.state("arrow folds first", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST)));
            clickArrow(s, p, 0);
            r.line = () -> r.logText();
        });

        stage.state("arrow folds second", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.SECOND)));
            clickArrow(s, p, 0);
            r.line = () -> r.logText();
        });

        stage.state("second arrow of two", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST, Side.SECOND));
            clickArrow(s, p, 1);
        });

        stage.state("folded vertical", 120, 80, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 108, 68), Orientation.VERTICAL).collapsible(Side.FIRST, Side.SECOND));
            clickArrow(s, p, 1);
            s.waitMs(200);
        });

        stage.state("fold half way", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST));
            clickArrow(s, p, 0);
            s.waitMs(32);
            int mid = BenchAccess.paneArea(p, Side.FIRST).w();
            s.settle(0);
        });

        stage.state("reduced motion snaps", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST));
            HostState.reducedMotion(true);
            clickArrow(s, p, 0);
            HostState.reducedMotion(false);
            s.settle(0);
        });

        stage.state("arrow unfolds at the old size", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST)));
            dragBy(s, p, -41);
            r.log.clear();
            s.waitMs(400);
            clickArrow(s, p, 0);
            s.waitMs(200);
            clickArrow(s, p, 0);
            r.line = () -> r.logText();
        });

        stage.state("enter folds and unfolds", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.SECOND)));
            s.focus(p);
            s.key(InputKey.ENTER);
            s.key(InputKey.SPACE);
            s.key(InputKey.ENTER);
            r.line = () -> r.logText();
        });

        stage.state("keys on a folded divider", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST));
            s.focus(p);
            s.key(InputKey.ENTER);
            s.key(InputKey.LEFT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.ENTER);
            s.key(InputKey.END);
        });

        stage.state("drag out of a fold", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST)));
            clickArrow(s, p, 0);
            s.waitMs(400);
            Box g = BenchAccess.dividerBox(p);
            s.press(mx(g), my(g));
            s.panel().mouseDragged(mx(g) + 2, my(g), 0, 2, 0);
            s.panel().mouseDragged(mx(g) + 60, my(g), 0, 58, 0);
            s.release(mx(g) + 60, my(g));
            r.line = () -> r.logText();
        });

        stage.state("double click unfolds", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST));
            clickArrow(s, p, 0);
            s.waitMs(400);
            doubleClick(s, p);
        });

        stage.state("focus leaves a folded pane", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            s.key(InputKey.TAB);
            p.collapse(Side.FIRST);
            s.waitMs(200);
            s.type("x");
        });

        stage.state("arrow hidden when the divider is short", W, 40, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 8), Orientation.HORIZONTAL).collapsible(Side.FIRST, Side.SECOND));
            Box g = BenchAccess.dividerBox(p);
            s.click(mx(g), my(g));
            s.key(InputKey.ENTER);
        });
    }

    // ---- the box changes size ----

    private void resizing(Stage stage) {
        stage.state("box resized keeps the share", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).fraction(0.25));
            p.w = 100;
            p.w = 188;
            p.h = 40;
            s.move(0, 0);
            r.line = () -> "share=" + Math.round(p.share() * 100) + "%";
        });

        stage.state("box moved", W, 90, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            p.x = 2;
            p.y = 24;
            s.move(0, 0);
            s.click(40, 40);
        });

        stage.state("keepSize first", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).position(60).keepSize(Side.FIRST));
            p.w = 120;
            dragBy(s, p, 10);
            p.w = 188;
            r.line = () -> "first=" + p.position();
        });

        stage.state("keepSize second", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).position(141).keepSize(Side.SECOND));
            p.w = 100;
            p.w = 188;
            r.line = () -> "second=" + p.secondSize();
        });

        stage.state("shrunk under a limit and back", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL)
                    .position(120).limits(Side.SECOND, 40, 999));
            p.w = 100;
            p.w = 188;
            r.line = () -> "first=" + p.position();
        });

        stage.state("resized while folded", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.SECOND));
            clickArrow(s, p, 0);
            s.waitMs(200);
            p.w = 120;
            s.move(0, 0);
        });

        stage.state("resized while sliding", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.SECOND));
            clickArrow(s, p, 0);
            s.waitMs(32);
            p.w = 120;
            s.waitMs(200);
        });

        stage.state("setPosition and setShare", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).limits(Side.FIRST, 30, 999)));
            p.setPosition(5);
            p.setShare(0.5);
            r.line = () -> "fired: [" + r.logText() + "]";
        });

        stage.state("onLayout places a widget's parts", 230, 84, s -> {
            Rig r = new Rig(s, 70);
            ScrollComponent scroll = new ScrollComponent(new Box("scroll", 0, 0, 10, 10));
            ButtonComponent inside = scroll.add(new ButtonComponent(new Box("b", 0, 0, 10, 20), "Fits the pane", () -> { }));
            int[] calls = new int[1];
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 218, 60), Orientation.HORIZONTAL, new Tile("First"), scroll)
                    .onLayout(split -> {
                        calls[0]++;
                        inside.w = BenchAccess.paneBox(split, Side.SECOND).w();
                    }));
            dragBy(s, p, -40);
            r.line = () -> "button w=" + inside.w;
        });
    }

    // ---- splits in splits ----

    private void nesting(Stage stage) {
        stage.state("nested", W, 110, s -> {
            SplitPaneComponent inner = new SplitPaneComponent(new Box("i", 0, 0, 10, 10), Orientation.VERTICAL, new Tile("Top"), new Tile("Bottom"));
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 98), Orientation.HORIZONTAL, new Tile("Left"), inner));
        });

        stage.state("inner divider dragged", W, 110, s -> {
            SplitPaneComponent inner = new SplitPaneComponent(new Box("i", 0, 0, 10, 10), Orientation.VERTICAL, new Tile("Top"), new Tile("Bottom"));
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 98), Orientation.HORIZONTAL, new Tile("Left"), inner));
            s.move(0, 0);
            int outerAt = p.position();
            int innerAt = inner.position();
            grabBy(s, inner, 20);
            s.release(s.mouseX(), s.mouseY());
        });

        stage.state("outer drag resizes the inner", W, 110, s -> {
            SplitPaneComponent inner = new SplitPaneComponent(new Box("i", 0, 0, 10, 10), Orientation.HORIZONTAL, new Tile("A"), new Tile("B"));
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 98), Orientation.VERTICAL, new Tile("Top"), inner));
            s.move(0, 0);
            int share = inner.position();
            dragBy(s, p, -20);
        });

        stage.state("keys reach the inner divider", W, 110, s -> {
            SplitPaneComponent inner = new SplitPaneComponent(new Box("i", 0, 0, 10, 10), Orientation.VERTICAL, new Tile("Top"), new Tile("Bottom"));
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 98), Orientation.HORIZONTAL, new Tile("Left"), inner));
            for (int i = 0; i < 4; i++) {
                s.key(InputKey.TAB);
            }
            int at = inner.position();
            int outerAt = p.position();
            s.key(InputKey.DOWN);
            s.key(InputKey.RIGHT);
        });

        stage.state("tab through three levels", 240, 110, s -> {
            SplitPaneComponent deep = new SplitPaneComponent(new Box("d", 0, 0, 10, 10), Orientation.HORIZONTAL, new Tile("X"), new Tile("Y"));
            SplitPaneComponent mid = new SplitPaneComponent(new Box("m", 0, 0, 10, 10), Orientation.VERTICAL, new Tile("Top"), deep);
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 228, 98), Orientation.HORIZONTAL, new Tile("Left"), mid));
            for (int i = 0; i < 5; i++) {
                s.key(InputKey.TAB);
            }
            s.type("k");
            s.key(InputKey.TAB);
            s.type("n");
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
        });

        stage.state("click moves focus across levels", W, 110, s -> {
            SplitPaneComponent inner = new SplitPaneComponent(new Box("i", 0, 0, 10, 10), Orientation.VERTICAL, new Tile("Top"), new Tile("Bottom"));
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 98), Orientation.HORIZONTAL, new Tile("Left"), inner));
            s.move(0, 0);
            Box top = first(inner).box();
            s.click(mx(top), my(top));
            s.click(30, 50);
            Box g = BenchAccess.dividerBox(inner);
            s.click(mx(g), my(g));
        });

        stage.state("folded inner hides its stops", W, 110, s -> {
            SplitPaneComponent inner = new SplitPaneComponent(new Box("i", 0, 0, 10, 10), Orientation.VERTICAL, new Tile("Top"), new Tile("Bottom"));
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 98), Orientation.HORIZONTAL, new Tile("Left"), inner).collapsible(Side.SECOND));
            clickArrow(s, p, 0);
            s.waitMs(200);
        });

        stage.state("inner disabled with the outer", W, 110, s -> {
            SplitPaneComponent inner = new SplitPaneComponent(new Box("i", 0, 0, 10, 10), Orientation.VERTICAL, new Tile("Top"), new Tile("Bottom"));
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 98), Orientation.HORIZONTAL, new Tile("Left"), inner));
            p.active(false);
            s.move(100, 40);
            p.active(true);
            s.move(100, 40);
        });
    }

    // ---- input and clipping for the panes ----

    private void forwarding(Stage stage) {
        stage.state("clicks reach the right pane", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            s.click(30, 30);
            s.click(150, 30);
            s.click(160, 40);
            r.line = () -> "first=" + first(p).clicks + " second=" + second(p).clicks;
        });

        stage.state("press on the padding", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            s.click(7, 7);
        });

        stage.state("drags, releases and ticks", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            s.drag(30, 30, 40, 30);
            s.tick(3);
            dragBy(s, p, 10);
            int drags = first(p).drags;
            dragBy(s, p, 10);
            r.line = () -> "drags=" + first(p).drags + " rel=" + first(p).releases + " ticks=" + first(p).ticks;
        });

        stage.state("slider in a pane", W, 84, s -> {
            Rig r = new Rig(s, 70);
            int[] value = {0};
            SliderComponent slider = new SliderComponent(new Box("v", 0, 0, 10, 16), 0, 100, 0, v -> value[0] = v);
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.VERTICAL, slider, new Tile("Second")).position(22));
            s.drag(10, 18, 130, 18);
            r.line = () -> "value=" + value[0];
        });

        stage.state("typing into a field", W, 84, s -> {
            Rig r = new Rig(s, 70);
            TextFieldComponent field = new TextFieldComponent(new Box("f", 0, 0, 10, 16), "", v -> { }).placeholder("Name");
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.VERTICAL, field, new Tile("Second")).position(20));
            s.click(field.x + 10, field.y + 8);
            s.type("abc");
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.TAB);
            s.type("z");
            r.line = () -> "field=" + field.text();
        });

        stage.state("tree keys in a pane", 240, 84, s -> {
            TreeComponent<String> t = tree();
            s.add(new SplitPaneComponent(new Box("s", 6, 6, 228, 72), Orientation.HORIZONTAL, t, new Tile("Second")));
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.RIGHT);
        });

        stage.state("button by keyboard", W, 84, s -> {
            Rig r = new Rig(s, 70);
            List<String> pressed = new ArrayList<>();
            ButtonComponent b = new ButtonComponent(new Box("b", 0, 0, 10, 10), "Go", () -> pressed.add("go"));
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL, b, new Tile("Second")));
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
            s.key(InputKey.TAB);
            r.line = () -> "pressed=" + pressed;
        });

        stage.state("button clicked then enter", W, 84, s -> {
            Rig r = new Rig(s, 70);
            List<String> pressed = new ArrayList<>();
            ButtonComponent b = new ButtonComponent(new Box("b", 0, 0, 10, 10), "Go", () -> pressed.add("go"));
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL, b, new Tile("Second")));
            s.click(40, 30);
            r.line = () -> "pressed=" + pressed.size();
        });

        stage.state("wheel over a pane and the divider", 240, 96, s -> {
            Rig r = new Rig(s, 82);
            TreeComponent<String> t = tree();
            t.expandAll();
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 228, 72), Orientation.HORIZONTAL, t, new Tile("Second")));
            s.scroll(t.x + 30, t.y + 20, -1);
            int at = p.position();
            Box g = BenchAccess.dividerBox(p);
            s.scroll(mx(g), my(g), 1);
            g = BenchAccess.dividerBox(p);
            s.scroll(mx(g), my(g), -2);
            g = BenchAccess.dividerBox(p);
            s.scroll(mx(g), my(g), -0.01);
            r.line = () -> "pos=" + p.position();
        });

        stage.state("wheel on a folded divider", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST));
            clickArrow(s, p, 0);
            s.waitMs(200);
            Box g = BenchAccess.dividerBox(p);
            s.scroll(mx(g), my(g), -1);
        });

        stage.state("clipped at the pane", W, 72, s -> {
            Bleeder b = new Bleeder();
            s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL, b, new Tile("Second")));
        });

        stage.state("clipped at the second pane", W, 72, s -> {
            Bleeder b = new Bleeder();
            s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL, new Tile("First"), b));
        });

        stage.state("clipped while nested", W, 110, s -> {
            SplitPaneComponent inner = new SplitPaneComponent(new Box("i", 0, 0, 10, 10), Orientation.VERTICAL, new Bleeder(), new Tile("Bottom"));
            s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 98), Orientation.HORIZONTAL, new Tile("Left"), inner));
        });

        stage.state("dropdown popup leaves the pane", 220, 120, s -> {
            int[] picked = {0};
            DropdownComponent drop = new DropdownComponent(new Box("pick", 0, 0, 10, 16), "Difficulty", 105, 107, s::overlay);
            drop.lines = List.of(
                    new DropdownComponent.Entry("Easy", "", "", "", DropdownComponent.State.SELECTABLE, -1F, List.of(), () -> picked[0] = 1),
                    new DropdownComponent.Entry("Hard", "", "", "", DropdownComponent.State.SELECTABLE, -1F, List.of(), () -> picked[0] = 2));
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 208, 40), Orientation.HORIZONTAL, new Tile("First"), drop).position(90).popupHost(s.panel()));
            s.move(0, 0);
            s.click(drop.x + 30, drop.y + 8);
            Box list = s.panel().overlay().box();
        });

        stage.state("popup over the next pane", 220, 120, s -> {
            DropdownComponent drop = new DropdownComponent(new Box("pick", 0, 0, 10, 16), "Difficulty", 8, 204, s::overlay);
            drop.lines = List.of(
                    new DropdownComponent.Entry("Easy", "", "", "", DropdownComponent.State.SELECTABLE, -1F, List.of(), () -> { }),
                    new DropdownComponent.Entry("Hard", "", "", "", DropdownComponent.State.SELECTABLE, -1F, List.of(), () -> { }));
            s.add(new SplitPaneComponent(new Box("s", 6, 6, 208, 108), Orientation.VERTICAL, drop, new Tile("Text under the popup, on the left")).position(20).popupHost(s.panel()));
            s.move(0, 0);
            s.click(drop.x + 30, drop.y + 8);
            s.move(210, 110);
            s.waitMs(32);
        });

        stage.state("scroll box pane", W, 72, s -> {
            List<String> pressed = new ArrayList<>();
            ScrollComponent scroll = scrollOfButtons(6, pressed);
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL, new Tile("First"), scroll));
            s.move(0, 0);
            ButtonComponent top = (ButtonComponent) scroll.children().get(0);
            s.click(top.x + 10, top.y + 8);
            s.scroll(top.x + 10, top.y + 8, -1);
            int pos = p.position();
            dragBy(s, p, -20);
        });

        stage.state("tab reveals inside a scroll box pane", W, 72, s -> {
            ScrollComponent scroll = scrollOfButtons(6, new ArrayList<>());
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL, new Tile("First"), scroll));
            TextFieldComponent field = scroll.add(new TextFieldComponent(new Box("f", 2, 134, 70, 16), "", v -> { }));
            s.move(0, 0);
            for (int i = 0; i < 9; i++) {
                s.key(InputKey.TAB);
            }
            s.type("q");
        });

        stage.state("inside a scroll box", W, 90, s -> {
            ScrollComponent outer = s.add(new ScrollComponent(new Box("o", 6, 6, 188, 78)));
            SplitPaneComponent p = outer.add(tiles(new Box("s", 6, 6, 170, 60), Orientation.HORIZONTAL));
            outer.add(new ButtonComponent(new Box("b", 8, 70, 100, 30), "Under the split", () -> { }));
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            int at = p.position();
            s.key(InputKey.RIGHT);
        });

        stage.state("setPane swaps a widget", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            s.click(30, 30);
            Tile old = first(p);
            Tile fresh = new Tile("New");
            p.setPane(Side.FIRST, fresh);
            s.move(0, 0);
            s.click(30, 30);
        });
    }

    // ---- what should not happen ----

    private void edges(Stage stage) {
        stage.state("disabled", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL).collapsible(Side.FIRST))).active(false);
            Box g = BenchAccess.dividerBox(p);
            s.drag(mx(g), my(g), mx(g) + 30, my(g));
            s.click(mx(g), g.y() + 5);
            s.scroll(mx(g), my(g), 1);
            s.click(30, 30);
            s.type("x");
            s.focus(p);
            s.key(InputKey.RIGHT);
            r.line = () -> "ignored: [" + r.logText() + "]";
        });

        stage.state("disabled vertical framed", 120, 80, s ->
                s.add(tiles(new Box("s", 6, 6, 108, 68), Orientation.VERTICAL).framed(true).collapsible(Side.FIRST)).active(false));

        stage.state("disabled wheel still scrolls a view", 240, 84, s -> {
            TreeComponent<String> t = tree();
            t.expandAll();
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 228, 72), Orientation.HORIZONTAL, t, new Tile("Second")));
            p.active = false;
            s.scroll(t.x + 30, t.y + 20, -1);
        });

        stage.state("active gives back what it took", W, 72, s -> {
            Tile off = new Tile("Was off");
            off.active = false;
            Tile on = new Tile("Was on");
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL, off, on));
            p.active = false;
            s.move(0, 0);
            p.active = true;
            s.move(0, 0);
        });

        stage.state("hidden takes no input", W, 84, s -> {
            Rig r = new Rig(s, 70);
            SplitPaneComponent p = s.add(r.logged(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL)));
            p.visible = false;
            Box g = BenchAccess.dividerBox(p);
            s.drag(mx(g), my(g), mx(g) + 30, my(g));
            s.click(30, 30);
            s.scroll(mx(g), my(g), 1);
            s.key(InputKey.TAB);
            r.line = () -> "hidden: [" + r.logText() + "] pos=" + p.position();
        });

        stage.state("widget in a pane hidden", W, 72, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL));
            p.first().visible = false;
            s.click(30, 30);
        });

        stage.state("tiny boxes", W, 72, s -> {
            int[][] sizes = {{0, 0}, {1, 1}, {3, 3}, {7, 7}, {8, 20}, {12, 8}, {20, 12}, {30, 30}};
            int left = 4;
            for (int[] size : sizes) {
                SplitPaneComponent p = s.add(tiles(new Box("s", left, 6, size[0], size[1]), Orientation.HORIZONTAL).collapsible(Side.FIRST));
                left += size[0] + 4;
                s.move(left, 6);
            }
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
            s.click(5, 7);
            s.move(50, 10);
        });

        stage.state("tiny boxes vertical and framed", W, 72, s -> {
            int[][] sizes = {{1, 1}, {3, 3}, {7, 7}, {8, 9}, {20, 12}, {30, 30}};
            int left = 4;
            for (int[] size : sizes) {
                SplitPaneComponent p = s.add(tiles(new Box("s", left, 6, size[0], size[1]), Orientation.VERTICAL).framed(true));
                left += size[0] + 4;
            }
        });

        stage.state("collapse in a tiny box", 60, 30, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 5, 14), Orientation.HORIZONTAL).collapsible(Side.FIRST));
            p.collapse(Side.FIRST);
            s.waitMs(200);
        });

        stage.state("empty panes", W, 72, s -> {
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL, null, null).emptyText("Nothing here"));
            s.click(30, 30);
            s.scroll(30, 30, 1);
            s.key(InputKey.TAB);
        });

        stage.state("empty and framed", W, 72, s ->
                s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL, new Tile("First"), null)
                        .framed(true).emptyText("Pick something on the left to see it here")));

        stage.state("empty with too much text", 150, 50, s ->
                s.add(new SplitPaneComponent(new Box("s", 6, 6, 138, 38), Orientation.HORIZONTAL, null, null)
                        .emptyText("A very long empty text that cannot possibly fit in such a small pane at all")));

        stage.state("empty disabled", W, 72, s ->
                s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.VERTICAL, null, null)
                        .emptyText("Nothing here")).active(false));

        stage.state("no empty text", W, 72, s -> {
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL, null, null));
            s.tick(2);
        });

        stage.state("long text in the panes", W, 72, s -> {
            List<String> pressed = new ArrayList<>();
            s.add(new SplitPaneComponent(new Box("s", 6, 6, 188, 60), Orientation.HORIZONTAL,
                    new ButtonComponent(new Box("b", 0, 0, 10, 10), "A button with far too much to say for its pane", () -> pressed.add("b")),
                    new Tile("A tile whose name cannot possibly fit in this pane")).position(40));
        });

        stage.state("divider tooltip", 210, 90, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 108, 78), Orientation.HORIZONTAL)
                    .dividerTooltip("Drag to resize", "Double-click to reset"));
            Box g = BenchAccess.dividerBox(p);
            s.move(mx(g), my(g));
            s.settle(1500);
        });

        stage.state("arrow tooltip", 210, 90, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 108, 78), Orientation.HORIZONTAL)
                    .collapsible(Side.FIRST).dividerTooltip("Drag to resize").arrowTooltip("Fold this pane"));
            Box g = BenchAccess.dividerBox(p);
            s.move(mx(g), g.y() + 5);
            s.settle(1500);
        });

        stage.state("child tooltip", 210, 90, s -> {
            Tile a = new Tile("First");
            a.tooltip("The first pane's own tooltip");
            SplitPaneComponent p = s.add(new SplitPaneComponent(new Box("s", 6, 6, 108, 78), Orientation.HORIZONTAL, a, new Tile("Second"))
                    .tooltip("The split's tooltip"));
            s.move(20, 30);
            s.settle(1500);
        });

        stage.state("no tooltip while held", 210, 90, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 6, 6, 108, 78), Orientation.HORIZONTAL).dividerTooltip("Drag to resize"));
            grabBy(s, p, 10);
            Box g = BenchAccess.dividerBox(p);
            s.settle(1500);
        });

        stage.state("tooltip at the screen's edge", 170, 60, s -> {
            SplitPaneComponent p = s.add(tiles(new Box("s", 2, 2, 166, 56), Orientation.HORIZONTAL).position(148)
                    .dividerTooltip("A rather long tooltip line, seen from close to the edge"));
            Box g = BenchAccess.dividerBox(p);
            s.move(mx(g), my(g));
            s.settle(1500);
        });
    }
}
