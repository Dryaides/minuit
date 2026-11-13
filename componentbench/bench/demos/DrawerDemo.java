package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.DrawerComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.SurfaceSide;
import dev.dryaides.minuit.TextFieldComponent;

/** The drawer, driven by scripted input. */
public final class DrawerDemo implements Demo {
    private static final int W = 240;
    private static final int H = 130;
    private static final int OPENED = 300;
    private static final String BODY = "The world was last played an hour ago, on a server with four other players. It holds three dimensions and one very long river.";

    private static final class Rig {
        final Scene s;
        final int[] behind = new int[1];
        final ButtonComponent button;
        Rig(Scene s) {
            this.s = s;
            button = s.fixture(new ButtonComponent(new Box("b", 8, 8, 70, 20), "Settings", () -> behind[0]++));
            s.add(new TextFieldComponent(new Box("f", 8, 34, 70, 16), "Words", v -> { }));
        }
        DrawerComponent drawer(SurfaceSide side, int size) {
            return s.add(new DrawerComponent(new Box("area", 0, 0, W, H), side, size).title("World details").message(BODY));
        }
        DrawerComponent opened(SurfaceSide side, int size) {
            DrawerComponent d = drawer(side, size);
            d.open(s.panel());
            s.waitMs(OPENED);
            return d;
        }
    }

    @Override
    public void states(Stage stage) {
        stage.state("right modal", W, H, s -> {
            DrawerComponent d = new Rig(s).opened(SurfaceSide.RIGHT, 140);
        });

        stage.state("left", W, H, s -> new Rig(s).opened(SurfaceSide.LEFT, 140));

        stage.state("bottom", W, H, s -> {
            DrawerComponent d = new Rig(s).opened(SurfaceSide.BOTTOM, 76);
        });

        stage.state("top", W, H, s -> new Rig(s).opened(SurfaceSide.TOP, 76));

        stage.state("half way in", W, H, s -> {
            DrawerComponent d = new Rig(s).drawer(SurfaceSide.RIGHT, 140);
            d.open(s.panel());
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("reduced motion arrives at once", W, H, s -> {
            DrawerComponent d = new Rig(s).drawer(SurfaceSide.RIGHT, 140);
            HostState.reducedMotion(true);
            try {
                d.open(s.panel());
                d.close();
                d.open(s.panel());
            } finally {
                HostState.reducedMotion(false);
            }
        });

        stage.state("dragging the handle", W, H, s -> {
            DrawerComponent d = new Rig(s).opened(SurfaceSide.RIGHT, 140);
            Box g = BenchAccess.handleBox(d);
            s.hold(g.x() + 4, 60, g.x() + 34, 60);
            s.settle(0);
        });

        stage.state("short drag comes back", W, H, s -> {
            DrawerComponent d = new Rig(s).opened(SurfaceSide.RIGHT, 140);
            Box g = BenchAccess.handleBox(d);
            s.hold(g.x() + 4, 60, g.x() + 24, 60);
            s.waitMs(200);
            s.release(g.x() + 24, 60);
            s.waitMs(OPENED);
        });

        stage.state("far drag closes", W, H, s -> {
            int[] closed = new int[1];
            DrawerComponent d = new Rig(s).opened(SurfaceSide.BOTTOM, 76).onClose(() -> closed[0]++);
            Box g = BenchAccess.handleBox(d);
            s.drag(120, g.y() + 4, 120, g.y() + 44);
            s.waitMs(OPENED);
        });

        stage.state("flick closes from a short drag", W, H, s -> {
            DrawerComponent d = new Rig(s).opened(SurfaceSide.LEFT, 140);
            Box g = BenchAccess.handleBox(d);
            s.press(g.x() + 4, 60);
            s.waitMs(16);
            s.panel().mouseDragged(g.x() - 26, 60, 0, -30, 0);
            s.release(g.x() - 26, 60);
        });

        stage.state("drag the wrong way does nothing", W, H, s -> {
            DrawerComponent d = new Rig(s).opened(SurfaceSide.RIGHT, 140);
            Box g = BenchAccess.handleBox(d);
            s.hold(g.x() + 4, 60, g.x() - 30, 60);
            s.release(g.x() - 30, 60);
        });

        stage.state("hover handle", W, H, s -> {
            DrawerComponent d = new Rig(s).opened(SurfaceSide.BOTTOM, 76);
            Box g = BenchAccess.handleBox(d);
            s.move(120, g.y() + 4);
        });

        stage.state("hover close", W, H, s -> {
            DrawerComponent d = new Rig(s).opened(SurfaceSide.RIGHT, 140);
            Box c = BenchAccess.closeBox(d);
            s.move(c.x() + 5, c.y() + 5);
        });

        stage.state("close button closes", W, H, s -> {
            DrawerComponent d = new Rig(s).opened(SurfaceSide.RIGHT, 140);
            Box c = BenchAccess.closeBox(d);
            s.click(c.x() + 5, c.y() + 5);
        });

        stage.state("modal click on the dim closes, presses nothing", W, H, s -> {
            Rig r = new Rig(s);
            DrawerComponent d = r.opened(SurfaceSide.RIGHT, 140);
            s.click(20, 16);
        });

        stage.state("modal keeps open with closeOnBackdrop off", W, H, s -> {
            Rig r = new Rig(s);
            DrawerComponent d = r.drawer(SurfaceSide.RIGHT, 140).closeOnBackdrop(false);
            d.open(s.panel());
            s.waitMs(OPENED);
            s.click(20, 16);
        });

        stage.state("escape closes and gives the focus back", W, H, s -> {
            Rig r = new Rig(s);
            TextFieldComponent field = s.add(new TextFieldComponent(new Box("g", 8, 56, 70, 16), "", v -> { }));
            DrawerComponent d = r.drawer(SurfaceSide.RIGHT, 140);
            s.focus(field);
            d.open(s.panel());
            s.waitMs(OPENED);
            s.key(InputKey.ESCAPE);
        });

        stage.state("actions with the keyboard", W, H, s -> {
            int[] saved = new int[1];
            Rig r = new Rig(s);
            DrawerComponent d = r.drawer(SurfaceSide.RIGHT, 140).message("Save this world?")
                    .action("Cancel", ButtonComponent.Role.SECONDARY, null)
                    .action("Save", ButtonComponent.Role.PRIMARY, () -> saved[0]++);
            d.open(s.panel());
            s.waitMs(OPENED);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            s.settle(0);
        });

        stage.state("action runs and closes", W, H, s -> {
            int[] saved = new int[1];
            Rig r = new Rig(s);
            DrawerComponent d = r.drawer(SurfaceSide.BOTTOM, 76).message("Save this world?")
                    .action("Save", ButtonComponent.Role.PRIMARY, () -> saved[0]++);
            d.open(s.panel());
            s.waitMs(OPENED);
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
        });

        stage.state("not modal leaves the rest working", W, H, s -> {
            Rig r = new Rig(s);
            DrawerComponent d = r.drawer(SurfaceSide.RIGHT, 140).modal(false);
            d.open(s.panel());
            s.waitMs(OPENED);
            s.click(20, 16);
            s.move(20, 16);
        });

        stage.state("not modal tab walks out", W, H, s -> {
            Rig r = new Rig(s);
            DrawerComponent d = r.drawer(SurfaceSide.RIGHT, 140).modal(false).message("Nothing to press in here.");
            d.open(s.panel());
            s.waitMs(OPENED);
            s.key(InputKey.TAB);
            s.settle(0);
        });

        stage.state("long text scrolls by keys and wheel", W, 80, s -> {
            DrawerComponent d = s.add(new DrawerComponent(new Box("area", 0, 0, W, 80), SurfaceSide.RIGHT, 120).title("Long").message(BODY + " " + BODY));
            d.open(s.panel());
            s.waitMs(OPENED);
            s.scroll(180, 40, -1);
            s.key(InputKey.DOWN);
            s.waitMs(16);
        });

        stage.state("content child", W, H, s -> {
            SliderComponent slider = new SliderComponent(new Box("s", 0, 0, 110, 14), 0, 100, 40, v -> { });
            DrawerComponent d = s.add(new DrawerComponent(new Box("area", 0, 0, W, H), SurfaceSide.RIGHT, 140).title("Volume").message("Applies at once.").content(slider));
            d.open(s.panel());
            s.waitMs(OPENED);
            s.click(slider.x + 100, slider.y + 6);
        });

        stage.state("disabled blocks", W, H, s -> {
            Rig r = new Rig(s);
            DrawerComponent d = r.drawer(SurfaceSide.RIGHT, 140).action("Save", ButtonComponent.Role.PRIMARY, () -> { }).active(false);
            d.open(s.panel());
            s.waitMs(OPENED);
            Box g = BenchAccess.handleBox(d);
            s.drag(g.x() + 4, 60, g.x() + 90, 60);
            s.key(InputKey.ESCAPE);
            s.click(20, 16);
        });

        stage.state("no handle, no close button, long title", W, H, s -> {
            DrawerComponent d = s.add(new DrawerComponent(new Box("area", 0, 0, W, H), SurfaceSide.LEFT, 140).handle(false).closeButton(false)
                    .title("A very very very long title to cut short").message("Short."));
            d.open(s.panel());
            s.waitMs(OPENED);
        });

        stage.state("bigger than its area, tiny area", W, 60, s -> {
            DrawerComponent a = s.add(new DrawerComponent(new Box("a", 4, 4, 110, 52), SurfaceSide.RIGHT, 400).title("Bigger").message(BODY));
            DrawerComponent b = s.add(new DrawerComponent(new Box("b", 124, 4, 40, 30), SurfaceSide.BOTTOM, 20).title("Tiny").message(BODY));
            DrawerComponent c = s.add(new DrawerComponent(new Box("c", 174, 4, 60, 52), SurfaceSide.LEFT, 60));
            a.open(s.panel());
            b.open(s.panel());
            c.open(s.panel());
            s.waitMs(OPENED);
        });
    }
}
