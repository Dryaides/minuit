package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.Picture;
import dev.dryaides.minuit.PictureComponent;
import dev.dryaides.minuit.SpriteIcon;

/** PictureComponent: fits, sampling, items, states, zoom. Pictures are bench/images, all made by MakeImages. */
public final class PictureDemo implements Demo {
    private static PictureComponent add(Scene s, int x, int y, int w, int h, Picture p) {
        return s.add(new PictureComponent(new Box("p", x, y, w, h), p));
    }

    private static PictureComponent zoomable(Scene s) {
        return add(s, 8, 8, 184, 104, Shots.day()).caption(false).zoomable(true);
    }

    @Override
    public void states(Stage stage) {
        // ---- fits ----
        stage.state("contain 16:9 in a square", 136, 136, s -> {
            add(s, 8, 8, 120, 120, Shots.pattern());
        });
        stage.state("cover around the middle", 136, 136, s -> add(s, 8, 8, 120, 120, Shots.pattern().fit(Picture.Fit.COVER)));
        stage.state("cover with focus on the left", 136, 136, s -> add(s, 8, 8, 120, 120, Shots.pattern().fit(Picture.Fit.COVER).focus(0F, 0.5F)));
        stage.state("stretch distorts", 200, 76, s -> add(s, 8, 8, 184, 60, Shots.pattern().fit(Picture.Fit.STRETCH)));
        stage.state("natural, one pixel per screen pixel", 200, 116, s -> add(s, 8, 8, 184, 100, Shots.pattern().fit(Picture.Fit.NATURAL)));
        stage.state("portrait in a wide box", 200, 116, s -> add(s, 8, 8, 184, 100, Shots.portrait()).caption(false));
        stage.state("ultra-wide in a 16:9 box", 200, 124, s -> add(s, 8, 8, 184, 108, Shots.sunset()));

        // ---- sampling: the same chart three ways, 120 x 68 units, so 240 x 136 screen pixels at scale 2 ----
        stage.state("sampling: raw, smoothed, shrunk first", 392, 92, s -> {
            add(s, 8, 8, 120, 68, Shots.pattern().smooth(false).caption("raw, nearest"));
            add(s, 136, 8, 120, 68, Shots.pattern().caption("smoothed 3 x 3"));
            add(s, 264, 8, 120, 68, Shots.patternShrunk().caption("shrunk first"));
        });
        stage.state("screenshot: raw, smoothed, shrunk first", 392, 92, s -> {
            add(s, 8, 8, 120, 68, Shots.day().smooth(false).caption("raw, nearest"));
            add(s, 136, 8, 120, 68, Shots.day().caption("smoothed 3 x 3"));
            add(s, 264, 8, 120, 68, Shots.dayShrunk().caption("shrunk first"));
        });

        // ---- pixel art, items, sprites ----
        stage.state("pixel art: contain, integer", 200, 84, s -> {
            add(s, 8, 8, 88, 68, Shots.gem().fit(Picture.Fit.CONTAIN));
            add(s, 104, 8, 88, 68, Shots.gem());
        });
        stage.state("pixel art: natural and scale down", 200, 84, s -> {
            add(s, 8, 8, 88, 68, Shots.gem().fit(Picture.Fit.NATURAL));
            add(s, 104, 8, 88, 68, Shots.gem().fit(Picture.Fit.SCALE_DOWN));
        });
        stage.state("items grow by whole pixels", 200, 76, s -> {
            add(s, 8, 8, 60, 60, Picture.item("minecraft:diamond_sword"));
            add(s, 76, 8, 36, 36, Picture.item("minecraft:apple"));
            add(s, 120, 8, 20, 20, Picture.item("minecraft:oak_sapling"));
            add(s, 148, 8, 12, 12, Picture.item("minecraft:stone"));
        });
        stage.state("sprite and texture", 200, 76, s -> {
            add(s, 8, 8, 60, 60, Picture.of(SpriteIcon.item("minecraft:item/apple")));
            add(s, 76, 8, 60, 60, Shots.face());
        });

        // ---- states ----
        stage.state("loading", 200, 124, s -> add(s, 8, 8, 184, 108, Picture.of(AsyncImage.pending()).caption("Loading the screenshot")));
        stage.state("failed with alt words", 200, 124, s -> {
            AsyncImage gone = AsyncImage.pending();
            gone.failed();
            add(s, 8, 8, 184, 108, Picture.of(gone).alt("screenshot_2026-11-08.png"));
        });
        stage.state("failed, tiny box", 60, 40, s -> {
            AsyncImage gone = AsyncImage.pending();
            gone.failed();
            add(s, 8, 8, 40, 24, Picture.of(gone).alt("too small for words"));
        });
        stage.state("loaded later", 200, 124, s -> {
            AsyncImage later = AsyncImage.pending();
            add(s, 8, 8, 184, 108, Picture.of(later).caption("Arrived"));
            s.waitMs(200);
            later.ready(Shots.DIR + "shot-night.png", 1920, 1080);
        });
        stage.state("hover with a click", 200, 124, s -> {
            int[] clicks = {0};
            add(s, 8, 8, 184, 108, Shots.day()).onClick(() -> clicks[0]++);
            s.click(100, 50);
            s.waitMs(400);
            s.move(100, 50);
        });
        stage.state("disabled", 200, 124, s -> add(s, 8, 8, 184, 108, Shots.day()).active(false));
        stage.state("keyboard focus", 200, 124, s -> {
            PictureComponent p = add(s, 8, 8, 184, 108, Shots.day()).onClick(() -> { });
            s.focus(p);
        });
        stage.state("alt words as tooltip", 200, 124, s -> {
            add(s, 8, 8, 184, 108, Shots.day());
            s.move(60, 40);
            s.waitMs(700);
        });
        stage.state("long caption cut", 200, 124, s -> add(s, 8, 8, 184, 108,
                Shots.day().caption("A very long caption that cannot fit on one line under the picture")));
        stage.state("tiny box", 40, 30, s -> add(s, 8, 8, 20, 12, Shots.day()));

        // ---- zoom ----
        stage.state("wheel zooms at the pointer", 200, 120, s -> {
            PictureComponent p = zoomable(s);
            s.scroll(60, 40, 3);
            s.waitMs(300);
        });
        stage.state("zoomed then dragged", 200, 120, s -> {
            PictureComponent p = zoomable(s);
            s.scroll(100, 60, 5);
            s.waitMs(300);
            float before = p.centerX();
            s.drag(100, 60, 140, 60);
        });
        stage.state("double click: 1 to 1", 200, 120, s -> {
            PictureComponent p = zoomable(s);
            s.click(150, 40);
            s.waitMs(100);
            s.click(150, 40);
            s.waitMs(300);
        });
        stage.state("double click again resets", 200, 120, s -> {
            PictureComponent p = zoomable(s);
            s.click(150, 40);
            s.waitMs(100);
            s.click(150, 40);
            s.waitMs(500);
            s.click(150, 40);
            s.waitMs(100);
            s.click(150, 40);
            s.waitMs(300);
        });
        stage.state("keys: plus twice, then right", 200, 120, s -> {
            PictureComponent p = zoomable(s);
            s.focus(p);
            s.type("++");
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.waitMs(300);
        });
        stage.state("zoom easing, half way", 200, 120, s -> {
            PictureComponent p = zoomable(s);
            s.scroll(100, 60, 4);
            s.settle(50);
        });
        stage.state("reduced motion snaps", 200, 120, s -> {
            BaseComponent.reducedMotion = true;
            try {
                PictureComponent p = zoomable(s);
                s.scroll(100, 60, 4);
                s.settle(16);
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });
        stage.state("zoomed pixel art", 120, 120, s -> {
            PictureComponent p = s.add(new PictureComponent(new Box("p", 8, 8, 104, 104), Shots.gem()).zoomable(true));
            s.scroll(40, 40, 6);
            s.waitMs(300);
        });
    }
}
