package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Picture;
import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.CardComponent;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.ItemView;
import java.util.List;

/** Cards, alone and in a grid, driven by scripted input. */
public final class CardDemo implements Demo {
    private static final int W = 200;
    private static final int H = 112;
    private static final String TEXT = "A world of tall forests, a long river and a village nobody has found yet.";

    private static CardComponent full(Box box) {
        return new CardComponent(box).title("Tall Forests").subtitle("By Steve").text(TEXT)
                .media(ItemView.of("minecraft:oak_sapling"), 24)
                .footerText("12 MB").footerButton("Play", ButtonComponent.Role.PRIMARY, () -> { });
    }

    @Override
    public void states(Stage stage) {
        stage.state("picture: screenshot cover", W, 120, s -> s.add(new CardComponent(new Box("c", 8, 8, 184, 104))
                .title("Spawn").subtitle("Day 1").picture(Shots.day().fit(Picture.Fit.COVER), 56)));
        stage.state("picture: item grows crisp", W, 96, s -> s.add(new CardComponent(new Box("c", 8, 8, 184, 80))
                .title("Diamond sword").subtitle("Sharpness V").media(ItemView.of("minecraft:diamond_sword"), 44)));
        stage.state("picture: item hover shows its tooltip", W, 96, s -> {
            s.add(new CardComponent(new Box("c", 8, 8, 184, 80)).title("Apple").media(ItemView.of("minecraft:apple"), 44));
            s.move(100, 30);
            s.waitMs(700);
        });
        stage.state("picture: loading, disabled", W, 96, s -> s.add(new CardComponent(new Box("c", 8, 8, 184, 80))
                .title("Pending").picture(Picture.of(AsyncImage.pending()), 44).active(false)));
        stage.state("full card", W, 134, s -> {
            CardComponent c = s.add(full(new Box("c", 8, 8, 184, 118)));
        });

        stage.state("header and text only", W, 62, s -> {
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 8, 184, 0)).title("Settings").subtitle("Saved a minute ago").icon(s.theme().icon(Icon.FOLDER)).fit());
        });

        stage.state("fit grows with the text", W, 76, s -> {
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 8, 184, 0)).title("About").text(TEXT).fit());
        });

        stage.state("grid of three", 280, 160, s -> {
            Box[] places = CardComponent.grid(new Box("g", 8, 8, 264, 0), 3, 6, 70, 6);
            String[] names = {"Oak", "Spruce", "Birch", "Jungle", "Acacia", "Dark oak"};
            for (int i = 0; i < places.length; i++) {
                s.add(new CardComponent(places[i]).title(names[i]).subtitle("Sapling")
                        .media(ItemView.of("minecraft:" + names[i].toLowerCase().replace(' ', '_') + "_sapling"), 28).onClick(() -> { }));
            }
            s.move(places[1].x() + 20, places[1].y() + 20);
        });

        stage.state("hover lifts", W, 62, s -> {
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 10, 184, 44)).title("Hover me").text("It rises two pixels."));
            s.move(40, 30);
            s.waitMs(200);
            s.move(40, 8);
            s.waitMs(16);
        });

        stage.state("lift half way", W, 62, s -> {
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 10, 184, 44)).title("Rising").text("Half way there."));
            s.move(40, 30);
            s.settle(16);
        });

        stage.state("lift off", W, 62, s -> {
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 10, 184, 44)).lift(false).title("Stays still").text("No lift."));
            s.move(40, 30);
            s.waitMs(200);
        });

        stage.state("reduced motion lifts at once", W, 62, s -> {
            s.paint((d, t) -> HostState.reducedMotion(true));
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 10, 184, 44)).title("No easing").text("Up in one frame."));
            s.paint((d, t) -> HostState.reducedMotion(false));
            HostState.reducedMotion(true);
            try {
                s.move(40, 30);
                s.waitMs(16);
            } finally {
                HostState.reducedMotion(false);
            }
        });

        stage.state("click card", W, 62, s -> {
            int[] clicks = new int[1];
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 10, 184, 44)).title("Click me").text("The whole card is a button.").onClick(() -> clicks[0]++));
            s.click(40, 30);
            s.drag(40, 30, 120, 58 + 40);
            s.press(40, 30);
            s.waitMs(200);
        });

        stage.state("footer button is not the card", W, 80, s -> {
            int[] card = new int[1];
            int[] button = new int[1];
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 10, 184, 62)).title("Two targets").onClick(() -> card[0]++)
                    .footerButton("Open", ButtonComponent.Role.PRIMARY, () -> button[0]++));
            s.move(40, 30);
            s.waitMs(200);
            ButtonComponent b = c.buttons().get(0);
            s.click(b.x + 4, b.y + 4);
        });

        stage.state("tab goes card then button", W, 80, s -> {
            int[] card = new int[1];
            int[] button = new int[1];
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 10, 184, 62)).title("Two stops").onClick(() -> card[0]++)
                    .footerButton("Open", ButtonComponent.Role.PRIMARY, () -> button[0]++));
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
            s.key(InputKey.TAB);
            s.settle(0);
        });

        stage.state("tab ring on a card", W, 62, s -> {
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 10, 184, 44)).title("Keyboard").text("The ring goes round the card.").onClick(() -> { }));
            s.key(InputKey.TAB);
            s.settle(0);
        });

        stage.state("selected", W, 62, s -> {
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 10, 184, 44)).title("Chosen").text("With a ring.").selected(true));
        });

        stage.state("disabled", W, 112, s -> {
            int[] clicks = new int[1];
            CardComponent c = s.add(full(new Box("c", 8, 8, 184, 96)).onClick(() -> clicks[0]++).active(false).selected(true));
            s.click(40, 50);
            s.move(40, 50);
            s.waitMs(200);
        });

        stage.state("image pending", W, H, s -> s.add(new CardComponent(new Box("c", 8, 8, 184, 96)).title("Loading picture").text("The picture is still on its way.").image(AsyncImage.pending(), 40)));

        stage.state("very long title and text", W, 72, s -> {
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 8, 184, 56)).title("A very very long title that cannot possibly fit on this card").subtitle("And a subtitle that is too long as well, by far").text(TEXT + " " + TEXT));
            s.move(40, 16);
            s.settle(1500);
            s.waitMs(16);
        });

        stage.state("empty", W, 40, s -> s.add(new CardComponent(new Box("c", 8, 8, 184, 24))));

        stage.state("tiny", W, 50, s -> {
            s.add(full(new Box("a", 8, 8, 20, 20)));
            s.add(full(new Box("b", 36, 8, 60, 34)));
            s.add(new CardComponent(new Box("c", 104, 8, 88, 12)).title("One line box"));
        });

        stage.state("hidden", W, 40, s -> {
            int[] clicks = new int[1];
            CardComponent c = s.add(new CardComponent(new Box("c", 8, 8, 184, 24)).title("Hidden").onClick(() -> clicks[0]++));
            c.visible = false;
            s.click(40, 16);
            s.key(InputKey.TAB);
        });
    }
}
