package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.BadgeComponent;
import dev.dryaides.minuit.BadgeComponent.Variant;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.InputKey;
import java.util.List;

/** Labels, counts and dots. */
public final class BadgeDemo implements Demo {
    private static final int W = 190;
    private static final int H = BadgeComponent.HEIGHT;

    @Override
    public void states(Stage stage) {
        stage.state("variants", W, 46, s -> {
            Variant[] all = Variant.values();
            String[] names = {"Primary", "Second", "Outline", "Good", "Warn", "Bad"};
            for (int i = 0; i < all.length; i++) {
                s.add(new BadgeComponent(new Box("b" + i, 8 + (i % 3) * 58, 8 + (i / 3) * 16, 0, H), names[i]).variant(all[i]));
            }
        });
        stage.state("counts", W, 30, s -> {
            BadgeComponent a = s.add(new BadgeComponent(new Box("a", 8, 8, 0, H)).count(1));
            BadgeComponent b = s.add(new BadgeComponent(new Box("b", 30, 8, 0, H)).count(42).variant(Variant.BAD));
            BadgeComponent c = s.add(new BadgeComponent(new Box("c", 60, 8, 0, H)).count(99));
            BadgeComponent d = s.add(new BadgeComponent(new Box("d", 90, 8, 0, H)).count(100).variant(Variant.BAD));
            BadgeComponent e = s.add(new BadgeComponent(new Box("e", 125, 8, 0, H)).count(12000).variant(Variant.SECONDARY));
        });
        stage.state("count of zero goes", W, 30, s -> {
            BadgeComponent a = s.add(new BadgeComponent(new Box("a", 8, 8, 0, H)).count(0));
            BadgeComponent b = s.add(new BadgeComponent(new Box("b", 30, 8, 0, H)).count(0).showZero(true).variant(Variant.SECONDARY));
        });
        stage.state("own cap", W, 30, s -> {
            BadgeComponent a = s.add(new BadgeComponent(new Box("a", 8, 8, 0, H)).max(9).count(10));
            a.max(999);
        });
        stage.state("capped tooltip tells the number", W, 60, s -> {
            s.add(new BadgeComponent(new Box("a", 8, 8, 0, H)).count(1234));
            s.move(14, 12);
            s.settle(1500);
        });
        stage.state("dots", W, 46, s -> {
            s.add(new BadgeComponent(new Box("a", 8, 8, 0, H), "Online").dot(true).variant(Variant.GOOD));
            s.add(new BadgeComponent(new Box("b", 70, 8, 0, H), "Busy").dot(true).variant(Variant.BAD));
            s.add(new BadgeComponent(new Box("c", 120, 8, 0, H), "").dot(true));
            s.add(new BadgeComponent(new Box("d", 8, 26, 0, H), "Online").dot(true).bare(true).variant(Variant.GOOD));
            s.add(new BadgeComponent(new Box("e", 70, 26, 0, H), "Away").dot(true).bare(true).variant(Variant.WARN));
            s.add(new BadgeComponent(new Box("f", 120, 26, 0, H), "Busy").bare(true).variant(Variant.BAD));
        });
        stage.state("disabled", W, 30, s -> {
            s.add(new BadgeComponent(new Box("a", 8, 8, 0, H), "Beta").active(false));
            s.add(new BadgeComponent(new Box("b", 45, 8, 0, H), "Beta").variant(Variant.OUTLINE).active(false));
            s.add(new BadgeComponent(new Box("c", 85, 8, 0, H), "Off").dot(true).active(false));
            s.add(new BadgeComponent(new Box("d", 130, 8, 0, H)).count(7).active(false));
        });
        stage.state("long text in a box", W, 60, s -> {
            String text = "A badge with a very long label";
            BadgeComponent b = s.add(new BadgeComponent(new Box("a", 8, 8, 70, H), text));
            s.move(20, 12);
            s.settle(1500);
        });
        stage.state("align right grows left", W, 30, s -> {
            BadgeComponent b = s.add(new BadgeComponent(new Box("a", 182, 8, 0, H), "Stays right").alignRight(true));
            b.isOver(0, 0);
        });
        stage.state("heights", W, 40, s -> {
            s.add(new BadgeComponent(new Box("a", 8, 8, 0, 11), "11"));
            s.add(new BadgeComponent(new Box("b", 34, 8, 0, 12), "12"));
            s.add(new BadgeComponent(new Box("c", 60, 8, 0, 14), "14"));
            s.add(new BadgeComponent(new Box("d", 86, 8, 0, 18), "18"));
            s.add(new BadgeComponent(new Box("e", 112, 8, 0, 6), "no room"));
            s.add(new BadgeComponent(new Box("f", 160, 8, 6, 6)).dot(true));
        });
        stage.state("empty draws nothing", W, 30, s -> {
            BadgeComponent b = s.add(new BadgeComponent(new Box("a", 8, 8, 0, H), ""));
            b.text(null);
        });
        stage.state("takes no keyboard stop", W, 40, s -> {
            s.add(new BadgeComponent(new Box("a", 8, 4, 0, H), "New"));
            ButtonComponent b = s.add(new ButtonComponent(new Box("b", 8, 18, 60, 18), "Next", () -> { }));
            s.key(InputKey.TAB);
        });
        stage.state("count on a button's corner", W, 40, s -> {
            s.add(new ButtonComponent(new Box("b", 8, 12, 70, 20), "Inbox", () -> { }));
            s.add(new ButtonComponent(new Box("c", 100, 12, 70, 20), "Quests", () -> { }));
            s.paint((d, t) -> {
                BadgeComponent.drawCount(d, t, 3, 82, 5);
                BadgeComponent.drawCount(d, t, 250, 174, 5);
            });
        });
        stage.state("hidden", W, 30, s -> {
            BadgeComponent b = s.add(new BadgeComponent(new Box("a", 8, 8, 0, H), "Hidden"));
            b.visible = false;
            s.move(12, 12);
        });
        stage.state("in a row of text", W, 30, s -> {
            s.paint((d, t) -> d.text("Version", 8, 10, t.palette().text()));
            s.add(new BadgeComponent(new Box("a", 52, 8, 0, H), "Beta").variant(Variant.WARN));
            s.add(new BadgeComponent(new Box("b", 84, 8, 0, H), "1.2.0").variant(Variant.OUTLINE));
        });
    }
}
