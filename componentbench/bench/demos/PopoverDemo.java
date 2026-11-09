package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.PopoverComponent;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.SurfaceAlign;
import dev.dryaides.minuit.SurfaceSide;
import dev.dryaides.minuit.ToggleComponent;

/** The popover, driven by scripted input. */
public final class PopoverDemo implements Demo {
    private static final int W = 220;
    private static final int H = 110;
    private static final int OPENED = 200;
    private static final String BODY = "Pick how far the fog starts, and whether it follows the sky.";

    private static final class Rig {
        final Scene s;
        final int[] behind = new int[1];
        PopoverComponent pop;
        ButtonComponent anchor;

        Rig(Scene s, Box place) {
            this.s = s;
            s.add(new ButtonComponent(new Box("other", W - 70, H - 28, 62, 20), "Behind", () -> behind[0]++));
            anchor = s.add(new ButtonComponent(place, "Fog", () -> pop.toggle()));
            pop = s.add(new PopoverComponent(anchor, s.panel()).title("Fog").text(BODY));
        }

        Rig opened() {
            pop.open();
            s.waitMs(OPENED);
            return this;
        }
    }

    private static Box near(int x, int y) {
        return new Box("a", x, y, 50, 20);
    }

    private static void inside(PopoverComponent p, int w, int h) {
        Box c = BenchAccess.cardBox(p);
    }

    @Override
    public void states(Stage stage) {
        stage.state("below the anchor", W, H, s -> {
            Rig r = new Rig(s, near(8, 8)).opened();
            inside(r.pop, W, H);
        });

        stage.state("flips above", W, H, s -> {
            Rig r = new Rig(s, near(8, 82)).opened();
            inside(r.pop, W, H);
        });

        stage.state("pushed in from the right", W, H, s -> {
            Rig r = new Rig(s, near(162, 8)).opened();
            inside(r.pop, W, H);
        });

        stage.state("beside on the right", W, H, s -> {
            Rig r = new Rig(s, near(8, 30));
            r.pop.side(SurfaceSide.RIGHT).open();
            s.waitMs(OPENED);
            inside(r.pop, W, H);
        });

        stage.state("beside flips left", W, H, s -> {
            Rig r = new Rig(s, near(162, 30));
            r.pop.side(SurfaceSide.RIGHT).open();
            s.waitMs(OPENED);
            inside(r.pop, W, H);
        });

        stage.state("centred and at the end", W, H, s -> {
            Rig r = new Rig(s, near(120, 8));
            r.pop.align(SurfaceAlign.CENTER).open();
            s.waitMs(OPENED);
            Box c = BenchAccess.cardBox(r.pop);
            r.pop.align(SurfaceAlign.END);
            r.pop.align(SurfaceAlign.CENTER);
        });

        stage.state("no room on either side covers the anchor", W, 70, s -> {
            ButtonComponent a = s.add(new ButtonComponent(new Box("a", 8, 25, 50, 20), "Fog", () -> { }));
            PopoverComponent p = s.add(new PopoverComponent(a, s.panel()).title("Fog").text(BODY));
            p.open();
            s.waitMs(OPENED);
            inside(p, W, 70);
        });

        stage.state("opening half way", W, H, s -> {
            Rig r = new Rig(s, near(8, 8));
            r.pop.open();
            s.waitMs(16);
            s.settle(0);
        });

        stage.state("reduced motion opens at once", W, H, s -> {
            Rig r = new Rig(s, near(8, 8));
            BaseComponent.reducedMotion = true;
            try {
                r.pop.open();
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });

        stage.state("anchor toggles", W, H, s -> {
            Rig r = new Rig(s, near(8, 8));
            s.click(20, 16);
            s.waitMs(OPENED);
            s.click(20, 16);
            s.waitMs(OPENED);
        });

        stage.state("first click outside only closes", W, H, s -> {
            Rig r = new Rig(s, near(8, 8)).opened();
            s.click(W - 30, H - 16);
            s.waitMs(OPENED);
            s.click(W - 30, H - 16);
        });

        stage.state("passClicks reaches beneath", W, H, s -> {
            Rig r = new Rig(s, near(8, 8));
            r.pop.passClicks(true);
            r.opened();
            s.click(W - 30, H - 16);
        });

        stage.state("click inside stays open", W, H, s -> {
            Rig r = new Rig(s, near(8, 8)).opened();
            Box c = BenchAccess.cardBox(r.pop);
            s.click(c.x() + 20, c.y() + 12);
        });

        stage.state("escape closes, gives the focus back", W, H, s -> {
            int[] closed = new int[1];
            Rig r = new Rig(s, near(8, 8));
            r.pop.onClose(() -> closed[0]++);
            r.opened();
            s.key(InputKey.ESCAPE);
        });

        stage.state("widgets inside, tab rings one", W, H, s -> {
            ButtonComponent a = s.add(new ButtonComponent(near(8, 8), "Fog", () -> { }));
            PopoverComponent p = s.add(new PopoverComponent(a, s.panel()).title("Fog").width(150));
            ToggleComponent t = p.add(new ToggleComponent(new Box("t", 0, 0, 130, 12), "Follow the sky", true, on -> { }), 8, 19);
            SliderComponent v = p.add(new SliderComponent(new Box("v", 0, 0, 130, 12), 0, 100, 40, x -> { }), 8, 34);
            p.open();
            s.waitMs(OPENED);
            s.key(InputKey.TAB);
            s.settle(0);
        });

        stage.state("hidden anchor closes", W, H, s -> {
            Rig r = new Rig(s, near(8, 8)).opened();
            r.anchor.visible = false;
            s.waitMs(OPENED);
        });

        stage.state("wheel does not reach beneath", W, H, s -> {
            Rig r = new Rig(s, near(8, 8)).opened();
            Box c = BenchAccess.cardBox(r.pop);
            s.scroll(c.x() + 10, c.y() + 10, -1);
        });

        stage.state("very long text in a short panel", W, 66, s -> {
            ButtonComponent a = s.add(new ButtonComponent(new Box("a", 4, 4, 40, 12), "Fog", () -> { }));
            PopoverComponent p = s.add(new PopoverComponent(a, s.panel()).title("A title that goes on and on and on and on and on").text(BODY + " " + BODY + " " + BODY));
            p.open();
            s.waitMs(OPENED);
            inside(p, W, 66);
        });

        stage.state("tiny area", 60, 40, s -> {
            ButtonComponent a = s.add(new ButtonComponent(new Box("a", 2, 2, 20, 10), "F", () -> { }));
            PopoverComponent p = s.add(new PopoverComponent(a, s.panel()).title("Fog").text(BODY));
            p.open();
            s.waitMs(OPENED);
            inside(p, 60, 40);
        });

        stage.state("empty", W, 50, s -> {
            ButtonComponent a = s.add(new ButtonComponent(new Box("a", 8, 4, 50, 16), "Fog", () -> { }));
            PopoverComponent p = s.add(new PopoverComponent(a, s.panel()));
            p.open();
            s.waitMs(OPENED);
        });

        stage.state("disabled keeps open, widgets off", W, H, s -> {
            ButtonComponent a = s.add(new ButtonComponent(near(8, 8), "Fog", () -> { }));
            PopoverComponent p = s.add(new PopoverComponent(a, s.panel()).title("Fog").text("Busy for a moment."));
            int[] pressed = new int[1];
            ButtonComponent inner = p.add(new ButtonComponent(new Box("i", 0, 0, 60, 16), "Inner", () -> pressed[0]++), 8, 34);
            p.open();
            s.waitMs(OPENED);
            p.active(false);
            s.waitMs(16);
            s.click(inner.x + 4, inner.y + 4);
            p.active(true);
            s.waitMs(16);
            p.active(false);
        });
    }
}
