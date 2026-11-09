package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.RangeSliderComponent;
import dev.dryaides.minuit.RangeSliderComponent.Handle;

/**
 * The two-handled slider, moved by scripted input. Each state prints what happened under the widget.
 */
public final class RangeSliderDemo implements Demo {
    private static final int W = 196;
    private static final int H = 42;

    /** A widget, its callback counts, and a line printed under it. */
    private static final class Rig {
        final Scene scene;
        RangeSliderComponent r;
        int changes;
        int commits;

        Rig(Scene scene) {
            this.scene = scene;
            scene.paint((d, t) -> {
                if (r != null) {
                    d.text(line(), 8, 30, t.palette().textSoft());
                }
            });
        }

        RangeSliderComponent make(Box box, int min, int max, int low, int high) {
            r = scene.add(new RangeSliderComponent(box, min, max, low, high, (l, h) -> changes++)
                    .onCommit((l, h) -> commits++));
            scene.waitMs(16); // one frame: the widget has seen the theme, so xOf is right
            return r;
        }

        RangeSliderComponent make(int min, int max, int low, int high) {
            return make(new Box("r", 8, 10, 180, 16), min, max, low, high);
        }

        String line() {
            return r.low + ".." + r.high + " ch" + changes + " co" + commits
                    + (r.chosen() == null ? "" : " " + r.chosen());
        }

        /** Presses a value's spot and drags to another's. */
        void drag(int from, int to) {
            scene.drag(r.xOf(from), 18, r.xOf(to), 18);
        }
    }

    @Override
    public void states(Stage stage) {
        looks(stage);
        mouse(stage);
        keys(stage);
        edges(stage);
    }

    private static void looks(Stage stage) {
        stage.state("default", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
        });
        stage.state("hover the nearest", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            s.move(g.r.xOf(80), 18);
            s.settle(600);
        });
        stage.state("tooltip", W, 70, s -> {
            Rig g = new Rig(s);
            g.make(new Box("r", 8, 46, 180, 16), 0, 100, 25, 75).tooltip("Price range", "Items outside it are hidden");
            s.move(g.r.xOf(50), 54);
            s.settle(1500);
        });
        stage.state("disabled ignores input", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            int x = g.r.xOf(25);
            g.r.active = false;
            s.drag(x, 18, x + 40, 18);
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
        });
        stage.state("hidden ignores input", W, H, s -> {
            Rig g = new Rig(s);
            g.make(new Box("r", 8, 10, 180, 16), 0, 100, 25, 75);
            int x = g.r.xOf(25);
            g.r.visible = false;
            s.drag(x, 18, x + 40, 18);
        });
        stage.state("small range 0..10", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 10, 2, 7).label((l, h) -> l + "-" + h);
        });
        stage.state("custom label", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 20, 60).label(v -> v + "%");
        });
        stage.state("whole label, no label", W, 60, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 20, 60).label((l, h) -> h - l + " wide");
            s.add(new RangeSliderComponent(new Box("none", 8, 42, 180, 12), 0, 100, 20, 60, null)
                    .label((l, h) -> null));
        });
    }

    private static void mouse(Stage stage) {
        stage.state("drag low: change, commit", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            g.drag(25, 40);
        });
        stage.state("still dragging: no commit", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            s.hold(g.r.xOf(75), 18, g.r.xOf(50), 18);
        });
        stage.state("click track: nearest moves", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            s.click(g.r.xOf(5), 18);
            s.click(g.r.xOf(95), 18);
        });
        stage.state("no crossing", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 50);
            g.drag(25, 90);
        });
        stage.state("minGap 20 holds", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 10, 50).minGap(20);
            g.drag(10, 90);
            g.drag(50, 0);
        });
        stage.state("same spot, drag right: high", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 50, 50);
            g.drag(50, 80);
        });
        stage.state("same spot, drag left: low", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 50, 50);
            g.drag(50, 20);
        });
        stage.state("same spot, click: no change", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 50, 50);
            s.click(g.r.xOf(50), 18);
        });
        stage.state("both at min, left then right", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 0, 0);
            int x = g.r.xOf(0);
            s.press(x, 18);
            s.panel().mouseDragged(x - 3, 18, 0, -3, 0);
            s.panel().mouseDragged(g.r.xOf(40), 18, 0, 40, 0);
            s.release(g.r.xOf(40), 18);
        });
        stage.state("both at max, drag left", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 100, 100);
            g.drag(100, 60);
        });
    }

    private static void keys(Stage stage) {
        stage.state("tab arrives: choosing", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            s.key(InputKey.TAB);
        });
        stage.state("tab: low chosen", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
        });
        stage.state("tab tab: high, arrows move", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT, true);
        });
        stage.state("shift tab: high first", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB, true);
            s.key(InputKey.TAB, true);
        });
        stage.state("right chooses, then moves", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT, true);
        });
        stage.state("left chooses, enter rechooses", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            s.key(InputKey.TAB);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.ENTER);
            s.key(InputKey.RIGHT);
        });
        stage.state("keys stop at the other handle", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 48, 50).minGap(1);
            s.key(InputKey.TAB);
            s.key(InputKey.LEFT);
            for (int i = 0; i < 4; i++) {
                s.key(InputKey.RIGHT);
            }
            s.key(InputKey.END);
            s.key(InputKey.HOME);
        });
        stage.state("focus by panel.focus", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            s.focus(g.r);
            s.key(InputKey.LEFT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.TAB);
        });
        stage.state("tab leaves the widget", W, 60, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 25, 75);
            RangeSliderComponent next = s.add(
                    new RangeSliderComponent(new Box("next", 8, 42, 180, 12), 0, 10, 2, 8, null));
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
        });
    }

    private static void edges(Stage stage) {
        stage.state("step 7 of 100: max is a stop", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 100, 10, 50).step(7);
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
            s.key(InputKey.END);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
        });
        stage.state("step 7, gap 3, offset min", W, H, s -> {
            Rig g = new Rig(s);
            g.make(3, 100, 3, 100).step(7).minGap(3);
            g.drag(3, 100);
        });
        stage.state("min == max", W, H, s -> {
            Rig g = new Rig(s);
            g.make(5, 5, 5, 5);
            s.drag(g.r.x + 10, 18, g.r.x + 100, 18);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
        });
        stage.state("minGap wider than range", W, H, s -> {
            Rig g = new Rig(s);
            g.make(0, 10, 4, 6).minGap(50);
            g.drag(10, 5);
        });
        stage.state("arguments out of order", W, H, s -> {
            Rig g = new Rig(s);
            g.make(new Box("r", 8, 10, 180, 16), 100, 0, 900, -5);
            g.r.low = 500;
            g.r.high = -500;
            s.waitMs(16);
        });
        stage.state("tiny: 40, 16, 8, 3 wide", W, H, s -> {
            Rig g = new Rig(s);
            g.make(new Box("a", 8, 10, 40, 16), 0, 100, 25, 75);
            s.add(new RangeSliderComponent(new Box("b", 56, 10, 16, 16), 0, 100, 25, 75, null));
            s.add(new RangeSliderComponent(new Box("c", 80, 10, 8, 16), 0, 100, 25, 75, null));
            s.add(new RangeSliderComponent(new Box("d", 96, 10, 3, 16), 0, 100, 25, 75, null));
            RangeSliderComponent tiny = s.add(new RangeSliderComponent(new Box("e", 110, 10, 3, 16), 0, 100, 25, 75, null));
            s.press(111, 18);
            s.release(111, 18);
        });
        stage.state("whole int range", W, H, s -> {
            Rig g = new Rig(s);
            g.make(new Box("r", 8, 10, 180, 16), Integer.MIN_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MAX_VALUE);
            g.r.label((l, h) -> "");
            s.waitMs(16);
            s.key(InputKey.TAB);
            s.key(InputKey.LEFT);
            s.key(InputKey.RIGHT, true);
            long tenth = (Integer.MAX_VALUE - (long) Integer.MIN_VALUE) / 10;
            s.click(g.r.xOf(Integer.MAX_VALUE), 18);
            int mid = g.r.x + g.r.w / 2;
            s.click(mid, 18);
        });
        stage.state("very long label", W, H, s -> {
            Rig g = new Rig(s);
            g.make(new Box("r", 8, 10, 110, 16), 0, 100, 25, 75)
                    .label((l, h) -> "from " + l + " up to and including " + h + " blocks away");
            g.drag(25, 50);
        });
    }
}
