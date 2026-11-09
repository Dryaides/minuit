package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.GridComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.LightboxComponent;
import dev.dryaides.minuit.Picture;
import java.util.List;

/** The full-screen viewer over a gallery of screenshots in a GridComponent. */
public final class LightboxDemo implements Demo {
    private static final int W = 320;
    private static final int H = 200;

    private static LightboxComponent gallery(Scene s, int w, int h) {
        List<Picture> shots = Shots.gallery();
        LightboxComponent[] viewer = new LightboxComponent[1];
        s.add(new GridComponent<>(new Box("grid", 8, 8, w - 16, 0), 4, 40, 4, 4, shots,
                Picture.cells(p -> p, Picture::caption), p -> viewer[0].open(s.panel(), shots.indexOf(p))));
        viewer[0] = s.add(new LightboxComponent(new Box("viewer", 0, 0, w, h)).pictures(shots));
        return viewer[0];
    }

    @Override
    public void states(Stage stage) {
        stage.state("thumbnail grid", W, H, s -> gallery(s, W, H));
        stage.state("grid hover", W, H, s -> {
            gallery(s, W, H);
            s.move(100, 30);
            s.waitMs(200);
        });
        stage.state("opened from the grid", W, H, s -> {
            LightboxComponent v = gallery(s, W, H);
            s.click(30, 25);
            s.waitMs(300);
        });
        stage.state("next by key, ultra-wide", W, H, s -> {
            LightboxComponent v = gallery(s, W, H);
            s.click(30, 25);
            s.key(InputKey.RIGHT);
            s.waitMs(300);
        });
        stage.state("portrait, arrow hovered", W, H, s -> {
            LightboxComponent v = gallery(s, W, H);
            v.open(s.panel(), 5);
            Box next = BenchAccess.arrowBox(v, true);
            s.move(next.x() + 5, next.y() + 5);
            s.waitMs(300);
        });
        stage.state("last: no next arrow", W, H, s -> {
            LightboxComponent v = gallery(s, W, H);
            v.open(s.panel(), 0);
            s.key(InputKey.END);
            s.waitMs(300);
        });
        stage.state("cross-fade half way", W, H, s -> {
            LightboxComponent v = gallery(s, W, H);
            v.open(s.panel(), 0);
            s.waitMs(300);
            v.next();
            s.settle(50);
        });
        stage.state("zoomed with the wheel", W, H, s -> {
            LightboxComponent v = gallery(s, W, H);
            v.open(s.panel(), 0);
            s.waitMs(300);
            s.scroll(200, 80, 4);
            s.waitMs(300);
        });
        stage.state("escape closes", W, H, s -> {
            LightboxComponent v = gallery(s, W, H);
            v.open(s.panel(), 2);
            s.key(InputKey.ESCAPE);
        });
        stage.state("small screen", 180, 120, s -> {
            LightboxComponent v = gallery(s, 180, 120);
            v.open(s.panel(), 1);
            s.waitMs(300);
        });
        stage.state("opening, reduced motion", W, H, s -> {
            BaseComponent.reducedMotion = true;
            try {
                LightboxComponent v = gallery(s, W, H);
                v.open(s.panel(), 3);
                s.settle(16);
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });
        stage.state("item in the viewer", W, H, s -> {
            LightboxComponent v = s.add(new LightboxComponent(new Box("viewer", 0, 0, W, H))
                    .pictures(List.of(Picture.item("minecraft:diamond_sword").caption("Diamond sword"), Shots.gem().caption("Gem, 16 x 16"))));
            v.open(s.panel(), 0);
            s.waitMs(300);
        });
    }
}
