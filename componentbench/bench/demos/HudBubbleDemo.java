package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.BubbleStyle;
import dev.dryaides.minuit.HudBubble;

/** A bubble with no panel and no entity, as on a mod's HUD. */
public final class HudBubbleDemo implements Demo {
    private static final int W = 220;
    private static final int H = 70;

    private static void show(bench.api.Scene s, HudBubble b, int x, int y) {
        s.paint((d, t) -> b.draw(d, x, y, W, H, 0));
    }

    @Override
    public void states(Stage stage) {
        stage.state("short", W, H, s -> {
            HudBubble b = new HudBubble();
            b.say("Only 10% left!", 100);
            for (int i = 0; i < 10; i++) {
                b.tick();
            }
            show(s, b, 110, 50);
        });
        stage.state("long text wraps", W, H, s -> {
            HudBubble b = new HudBubble();
            b.say("Your pickaxe is about to break and you are a long way from home", 100);
            for (int i = 0; i < 10; i++) {
                b.tick();
            }
            show(s, b, 110, 60);
        });
        stage.state("stays on screen at the right", W, H, s -> {
            HudBubble b = new HudBubble();
            b.say("Inventory full", 100);
            for (int i = 0; i < 10; i++) {
                b.tick();
            }
            show(s, b, 214, 50);
        });
        stage.state("thought", W, H, s -> {
            HudBubble b = new HudBubble().style(BubbleStyle.THOUGHT);
            b.say("I could use a cake right now", 100);
            for (int i = 0; i < 10; i++) {
                b.tick();
            }
            show(s, b, 90, 56);
        });
        stage.state("fading in", W, H, s -> {
            HudBubble b = new HudBubble();
            b.say("Hello", 100);
            b.tick();
            b.tick();
            show(s, b, 110, 50);
        });
        stage.state("fading out", W, H, s -> {
            HudBubble b = new HudBubble();
            b.say("Goodbye", 20);
            for (int i = 0; i < 15; i++) {
                b.tick();
            }
            show(s, b, 110, 50);
        });
        stage.state("reduced motion stays solid", W, H, s -> {
            HudBubble b = new HudBubble().reducedMotion(true);
            b.say("No fade", 20);
            show(s, b, 110, 50);
        });
        stage.state("logic", W, 24, s -> {
            HudBubble b = new HudBubble();
            b.say("One", 3);
            b.tick();
            b.tick();
            b.tick();
            b.say("A", 100);
            for (int i = 0; i < 50; i++) {
                b.tick();
            }
            b.say("A", 100);
            for (int i = 0; i < 95; i++) {
                b.tick();
            }
            b.say("B", 100);
            b.say(" ");
            b.say(null);
            b.say("Reads for a while");
            s.paint((d, t) -> d.text("all logic checks passed", 4, 8, t.palette().text()));
        });
    }
}
