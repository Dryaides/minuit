package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Picture;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.CarouselComponent;
import dev.dryaides.minuit.InputKey;
import java.util.ArrayList;
import java.util.List;

/** The carousel, driven by scripted input. */
public final class CarouselDemo implements Demo {
    private static final int W = 220;
    private static final int H = 80;
    private static final int AH = H - 12;
    private static final int LANDED = 300;
    private static final List<String> TIPS = List.of("Sneak to place a block against a chest.", "Boats are faster on ice.", "Bees follow flowers in your hand.", "Maps grow when placed on a cartography table.");

    private static CarouselComponent.SlideRenderer<String> words() {
        return (d, t, tip, b, hovered) -> {
            d.wrapped(tip, b.x() + 3, b.y() + 4, b.w() - 6, Math.max(1, Math.min(4, (b.h() - 4) / 10)), 10, t.palette().text());
        };
    }

    private static CarouselComponent<String> make(Scene s) {
        return s.add(new CarouselComponent<>(new Box("c", 6, 6, W - 12, AH), TIPS, words()));
    }

    private static List<Integer> heard(CarouselComponent<String> c) {
        List<Integer> out = new ArrayList<>();
        c.onChange(out::add);
        return out;
    }

    private static void clickBox(Scene s, Box b) {
        s.click(b.x() + b.w() / 2, b.y() + b.h() / 2);
    }

    @Override
    public void states(Stage stage) {
        stage.state("pictures: screenshots", W, 120, s -> s.add(CarouselComponent.ofPictures(new Box("c", 6, 6, W - 12, 108), Shots.gallery())));
        stage.state("pictures: items and portrait", W, 120, s -> {
            CarouselComponent<Picture> c = s.add(CarouselComponent.ofPictures(new Box("c", 6, 6, W - 12, 108),
                    java.util.List.of(Picture.item("minecraft:diamond_sword").caption("Sword"), Shots.portrait())));
            c.show(1);
            s.waitMs(400);
        });
        stage.state("framed for standalone use", W, H, s -> s.add(new CarouselComponent<>(new Box("c", 6, 6, W - 12, AH), TIPS, words()).framed(true)));
        stage.state("first slide", W, H, s -> {
            CarouselComponent<String> c = make(s);
        });

        stage.state("hover next arrow", W, H, s -> {
            CarouselComponent<String> c = make(s);
            Box a = BenchAccess.arrowBox(c, true);
            s.move(a.x() + 5, a.y() + 5);
        });

        stage.state("hover a dot", W, H, s -> {
            CarouselComponent<String> c = make(s);
            Box a = BenchAccess.dotBox(c, 2);
            s.move(a.x() + 2, a.y() + 2);
        });

        stage.state("arrows move", W, H, s -> {
            CarouselComponent<String> c = make(s);
            List<Integer> seen = heard(c);
            clickBox(s, BenchAccess.arrowBox(c, true));
            s.waitMs(LANDED);
            clickBox(s, BenchAccess.arrowBox(c, true));
            s.waitMs(LANDED);
            clickBox(s, BenchAccess.arrowBox(c, false));
            s.waitMs(LANDED);
        });

        stage.state("moving half way", W, H, s -> {
            CarouselComponent<String> c = make(s);
            clickBox(s, BenchAccess.arrowBox(c, true));
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("reduced motion jumps", W, H, s -> {
            CarouselComponent<String> c = make(s);
            BaseComponent.reducedMotion = true;
            try {
                clickBox(s, BenchAccess.arrowBox(c, true));
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });

        stage.state("dots jump", W, H, s -> {
            CarouselComponent<String> c = make(s);
            clickBox(s, BenchAccess.dotBox(c, 3));
            s.waitMs(LANDED);
            Box a = BenchAccess.arrowBox(c, true);
            s.move(a.x() + 5, a.y() + 5);
        });

        stage.state("no loop stops at the ends", W, H, s -> {
            CarouselComponent<String> c = make(s);
            s.key(InputKey.TAB);
            s.key(InputKey.LEFT);
            s.key(InputKey.END);
            s.key(InputKey.RIGHT);
            s.waitMs(LANDED);
            s.settle(0);
        });

        stage.state("loop goes round", W, H, s -> {
            CarouselComponent<String> c = make(s).loop(true);
            clickBox(s, BenchAccess.arrowBox(c, false));
            s.waitMs(LANDED);
            clickBox(s, BenchAccess.arrowBox(c, true));
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("wheel moves one", W, H, s -> {
            CarouselComponent<String> c = make(s);
            s.scroll(100, 30, -1);
            s.waitMs(LANDED);
            s.scroll(100, 30, 1);
            s.waitMs(LANDED);
            s.scroll(100, 30, 1);
        });

        stage.state("dragging follows", W, H, s -> {
            CarouselComponent<String> c = make(s);
            s.hold(150, 30, 90, 30);
            s.settle(0);
        });

        stage.state("drag lands on the next", W, H, s -> {
            CarouselComponent<String> c = make(s);
            List<Integer> seen = heard(c);
            s.drag(150, 30, 90, 30);
            s.waitMs(LANDED);
        });

        stage.state("short drag falls back", W, H, s -> {
            CarouselComponent<String> c = make(s);
            s.hold(150, 30, 135, 30);
            s.waitMs(200);
            s.release(135, 30);
            s.waitMs(LANDED);
        });

        stage.state("drag past the end resists", W, H, s -> {
            CarouselComponent<String> c = make(s);
            s.hold(40, 30, 140, 30);
            s.settle(0);
        });

        stage.state("click is not a drag", W, H, s -> {
            int[] clicked = new int[] {-1};
            CarouselComponent<String> c = make(s).onClick(i -> clicked[0] = i);
            s.click(100, 30);
            s.key(InputKey.RIGHT);
            s.key(InputKey.ENTER);
            s.waitMs(LANDED);
        });

        stage.state("auto play moves", W, H, s -> {
            CarouselComponent<String> c = make(s).autoPlay(500);
            List<Integer> seen = heard(c);
            s.waitMs(560);
            s.waitMs(LANDED);
        });

        stage.state("auto play rests under the pointer", W, H, s -> {
            CarouselComponent<String> c = make(s).autoPlay(300);
            s.move(100, 30);
            s.waitMs(700);
            s.move(-50, -50);
            s.waitMs(350);
            s.waitMs(LANDED);
        });

        stage.state("auto play never under reduced motion", W, H, s -> {
            s.paint((d, t) -> BaseComponent.reducedMotion = true);
            CarouselComponent<String> c = make(s).autoPlay(100);
            s.paint((d, t) -> BaseComponent.reducedMotion = false);
            BaseComponent.reducedMotion = true;
            try {
                s.waitMs(500);
                BaseComponent.reducedMotion = true;
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });

        stage.state("tab rings it", W, H, s -> {
            CarouselComponent<String> c = make(s);
            s.key(InputKey.TAB);
            s.key(InputKey.HOME);
            s.settle(0);
        });

        stage.state("disabled", W, H, s -> {
            CarouselComponent<String> c = make(s).active(false);
            clickBox(s, BenchAccess.arrowBox(c, true));
            s.scroll(100, 30, -1);
            s.drag(150, 30, 90, 30);
        });

        stage.state("many slides show a counter", W, H, s -> {
            List<String> many = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                many.add("Slide number " + (i + 1));
            }
            CarouselComponent<String> c = s.add(new CarouselComponent<>(new Box("c", 6, 6, W - 12, AH), many, words()));
            c.show(11);
            s.waitMs(LANDED);
        });

        stage.state("one slide has no arrows", W, H, s -> {
            CarouselComponent<String> c = s.add(new CarouselComponent<>(new Box("c", 6, 6, W - 12, AH), List.of("Only this one."), words()));
        });

        stage.state("empty", W, H, s -> {
            CarouselComponent<String> c = s.add(new CarouselComponent<>(new Box("c", 6, 6, W - 12, AH), List.of(), words()).emptyText("No tips yet"));
            s.click(100, 30);
            s.key(InputKey.TAB);
        });

        stage.state("tiny", W, 44, s -> {
            CarouselComponent<String> a = s.add(new CarouselComponent<>(new Box("a", 6, 6, 30, 16), TIPS, words()));
            CarouselComponent<String> b = s.add(new CarouselComponent<>(new Box("b", 44, 6, 70, 20), TIPS, words()));
            CarouselComponent<String> c = s.add(new CarouselComponent<>(new Box("c", 122, 6, 90, 32), TIPS, words()));
        });

        stage.state("hidden", W, H, s -> {
            CarouselComponent<String> c = make(s);
            c.visible = false;
            s.click(100, 30);
            s.scroll(100, 30, -1);
        });
    }
}
