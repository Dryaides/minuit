package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Picture;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.HoverCardComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.SurfaceSide;
import java.util.List;

/** The hover card, driven by a scripted pointer on simulated time. */
public final class HoverCardDemo implements Demo {
    private static final int W = 220;
    private static final int H = 110;
    private static final int REST = 430;
    private static final String BODY = "Found deep below the surface. Smelts into a gem you can trade with any villager.";

    private static final class Rig {
        final Scene s;
        final ButtonComponent anchor;
        final HoverCardComponent card;

        Rig(Scene s, Box place) {
            this.s = s;
            anchor = s.add(new ButtonComponent(place, "Ore", () -> { }));
            card = s.add(new HoverCardComponent(anchor, s.panel()).media(ItemView.of("minecraft:diamond")).title("Diamond ore").subtitle("Common in caves").text(BODY));
        }

        void rest() {
            s.move(anchor.x + 5, anchor.y + 5);
            s.waitMs(REST);
        }
    }

    private static Box near(int x, int y) {
        return new Box("a", x, y, 50, 20);
    }

    private static void inside(HoverCardComponent c, int w, int h) {
        Box b = BenchAccess.cardBox(c);
    }

    @Override
    public void states(Stage stage) {
        stage.state("not before the delay", W, H, s -> {
            Rig r = new Rig(s, near(8, 8));
            s.move(12, 12);
            s.waitMs(300);
            s.settle(0);
        });

        stage.state("open after the delay", W, H, s -> {
            int[] opened = new int[1];
            Rig r = new Rig(s, near(8, 8));
            r.card.onOpen(() -> opened[0]++);
            r.rest();
            inside(r.card, W, H);
        });

        stage.state("flips above", W, H, s -> {
            Rig r = new Rig(s, near(8, 84));
            r.rest();
            inside(r.card, W, H);
        });

        stage.state("pushed in from the right", W, H, s -> {
            Rig r = new Rig(s, near(162, 8));
            r.rest();
            inside(r.card, W, H);
        });

        stage.state("beside", W, H, s -> {
            Rig r = new Rig(s, near(8, 40));
            r.card.side(SurfaceSide.RIGHT);
            r.rest();
            inside(r.card, W, H);
        });

        stage.state("gliding in", W, H, s -> {
            Rig r = new Rig(s, near(8, 8));
            s.move(12, 12);
            s.waitMs(410);
            s.settle(0);
        });

        stage.state("reduced motion keeps the delay, not the glide", W, H, s -> {
            s.paint((d, t) -> BaseComponent.reducedMotion = true);
            Rig r = new Rig(s, near(8, 8));
            s.paint((d, t) -> BaseComponent.reducedMotion = false);
            BaseComponent.reducedMotion = true;
            try {
                s.move(12, 12);
                s.waitMs(300);
                s.waitMs(120);
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });

        stage.state("moving onto the card keeps it", W, H, s -> {
            Rig r = new Rig(s, near(8, 8));
            r.rest();
            Box c = BenchAccess.cardBox(r.card);
            s.move(c.x() + 20, c.y() + 10);
            s.waitMs(400);
        });

        stage.state("leaving closes after its delay", W, H, s -> {
            int[] closed = new int[1];
            Rig r = new Rig(s, near(8, 8));
            r.card.onClose(() -> closed[0]++);
            r.rest();
            s.move(200, 100);
            s.waitMs(80);
            s.waitMs(120);
            s.waitMs(200);
        });

        stage.state("a pass across does nothing", W, H, s -> {
            Rig r = new Rig(s, near(8, 8));
            s.move(12, 12);
            s.waitMs(100);
            s.move(200, 100);
            s.waitMs(500);
        });

        stage.state("click closes until the pointer leaves", W, H, s -> {
            int[] pressed = new int[1];
            ButtonComponent a = s.add(new ButtonComponent(near(8, 8), "Ore", () -> pressed[0]++));
            HoverCardComponent c = s.add(new HoverCardComponent(a, s.panel()).title("Diamond ore").text("Text"));
            s.move(12, 12);
            s.waitMs(REST);
            s.click(12, 12);
            s.waitMs(REST);
            s.move(200, 100);
            s.waitMs(16);
            s.move(12, 12);
            s.waitMs(REST);
        });

        stage.state("anchor tooltip stays away", W, H, s -> {
            ButtonComponent a = s.add(new ButtonComponent(near(8, 8), "Ore", () -> { }));
            a.tooltip("The anchor's own");
            HoverCardComponent c = s.add(new HoverCardComponent(a, s.panel()).title("Diamond ore"));
            s.move(12, 12);
            s.waitMs(REST);
        });

        stage.state("keyboard ring opens it", W, H, s -> {
            Rig r = new Rig(s, near(8, 8));
            s.key(InputKey.TAB);
            s.waitMs(REST);
            s.settle(0);
        });

        stage.state("own delays", W, H, s -> {
            Rig r = new Rig(s, near(8, 8));
            r.card.delays(0, 0);
            s.move(12, 12);
            s.waitMs(16);
            s.move(200, 100);
            s.waitMs(16);
        });

        stage.state("disabled does not open", W, H, s -> {
            Rig r = new Rig(s, near(8, 8));
            r.card.active(false);
            r.rest();
        });

        stage.state("hidden anchor closes", W, H, s -> {
            Rig r = new Rig(s, near(8, 8));
            r.rest();
            r.anchor.visible = false;
            s.waitMs(16);
        });

        stage.state("box anchor, words only", W, H, s -> {
            HoverCardComponent c = s.add(new HoverCardComponent(new Box("place", 8, 8, 60, 12), s.panel()).title("A place").text("Just a spot on the panel."));
            s.paint((d, t) -> d.text("Rest here", 10, 10, t.palette().text()));
            s.move(12, 12);
            s.waitMs(REST);
        });

        stage.state("very long text in a short panel", W, 60, s -> {
            ButtonComponent a = s.add(new ButtonComponent(new Box("a", 4, 4, 40, 12), "Ore", () -> { }));
            HoverCardComponent c = s.add(new HoverCardComponent(a, s.panel()).title("A title that goes on and on and on and on").subtitle("A subtitle that goes on as well, and on, and on").text(BODY + " " + BODY));
            s.move(8, 8);
            s.waitMs(REST);
            inside(c, W, 60);
        });

        stage.state("tiny area", 60, 40, s -> {
            ButtonComponent a = s.add(new ButtonComponent(new Box("a", 2, 2, 20, 10), "O", () -> { }));
            HoverCardComponent c = s.add(new HoverCardComponent(a, s.panel()).title("Diamond ore").text(BODY));
            s.move(6, 6);
            s.waitMs(REST);
            inside(c, 60, 40);
        });

        stage.state("empty", W, 50, s -> {
            ButtonComponent a = s.add(new ButtonComponent(new Box("a", 8, 4, 50, 16), "Ore", () -> { }));
            HoverCardComponent c = s.add(new HoverCardComponent(a, s.panel()));
            s.move(12, 8);
            s.waitMs(REST);
        });
    }
}
