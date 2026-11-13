import dev.dryaides.minuit.internal.HostState;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.GridComponent;
import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.ReelComponent;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.UiSound;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ReelComponent and AsyncImage drawn offline: a spin frame by frame per family, light and dark, with one thumbnail
 * pending until mid-spin, a thumbnail grid with pending, ready and failed cells, and a 500 ms start delay.
 *   java ReelBench <out folder>   (frames for the GIF go to out/gif)
 */
public class ReelBench {

    static final int W = 427;
    static final int H = 240;
    static long now = 10_000;
    static int fails;
    static final List<String> MAPS = dev.dryaides.minuit.internal.showcase.SiteScenes.MAPS;

    public static void main(String[] args) {
        File out = new File(args.length > 0 ? args[0] : "out");
        BenchMock.McFont font = new BenchMock.McFont();
        HostState.clock(() -> now);
        List<String> sounds = new ArrayList<>();
        HostState.sound(new UiSound() {
            @Override
            public void tick(float frac) {
                sounds.add("tick");
            }

            @Override
            public void land() {
                sounds.add("land");
            }
        });
        for (int i = 0; i < MAPS.size(); i++) {
            BenchMock.DYNAMIC.put("bench:thumb/" + i, thumb(i));
        }
        for (String family : List.of("slate", "leather")) {
            for (ThemeChoice.Variant v : List.of(ThemeChoice.Variant.LIGHT, ThemeChoice.Variant.DARK)) {
                Theme theme = Themes.resolve(Themes.SLATE, new ThemeChoice(family, "default", v));
                String tag = family + "-" + v.id();
                sounds.clear();
                spin(font, theme, out, tag, sounds, tag.equals("slate-dark"));
                grid(font, theme, out, tag);
            }
        }
        System.out.println(fails == 0 ? "ReelBench ok" : "ReelBench " + fails + " FAIL");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void spin(BenchMock.McFont font, Theme theme, File out, String tag, List<String> sounds, boolean gif) {
        Map<String, AsyncImage> images = new java.util.HashMap<>();
        for (int i = 0; i < MAPS.size(); i++) {
            images.put(MAPS.get(i), i == 2 ? AsyncImage.pending() : AsyncImage.of("bench:thumb/" + i, 320, 180));
        }
        String[] landed = {null};
        ReelComponent<String> reel = new ReelComponent<>(new Box("reel", 13, 80, 401, 80), MAPS, s -> s)
                .image(images::get).delay(500).onLand(s -> landed[0] = s);
        long start = now;
        reel.spin(2, 1234L);
        for (long t = 0; t <= 4000; t += 10) {
            now = start + t;
            if (t == 1800) {
                images.get(MAPS.get(2)).ready("bench:thumb/2", 320, 180);
            }
            boolean still = t % 250 == 0;
            boolean frame = gif && t % 30 == 0;
            BenchMock.MockDraw d = draw(font);
            reel.draw(d, theme, 0, 0, 1F);
            if (still) {
                d.save(new File(out, String.format("reel-%s-%04dms.png", tag, t)));
            }
            if (frame) {
                d.save(new File(out, String.format("gif/f%04d.png", t / 30)));
            }
            if (t == 400) {
                check(sounds.isEmpty(), tag + " no tick during the 500 ms delay");
            }
        }
        check(MAPS.get(2).equals(landed[0]), tag + " lands on " + MAPS.get(2) + ", got " + landed[0]);
        check(sounds.indexOf("land") == sounds.size() - 1 && sounds.stream().filter("land"::equals).count() == 1,
                tag + " one landing, last");
        check(sounds.size() > 10, tag + " ticks heard: " + sounds.size());
    }

    static void grid(BenchMock.McFont font, Theme theme, File out, String tag) {
        List<AsyncImage> images = List.of(AsyncImage.of("bench:thumb/0", 320, 180), AsyncImage.pending(), AsyncImage.pending(),
                AsyncImage.of("bench:thumb/3", 320, 180));
        images.get(2).failed();
        List<Integer> cells = List.of(0, 1, 2, 3);
        GridComponent<Integer> grid = new GridComponent<>(new Box("grid", 20, 40, 387, 0), 4, 70, 6, 6, cells,
                GridComponent.thumbnails(images::get, i -> List.of("Ready", "Pending", "Failed", "Ready").get(i)), i -> {
                }).selected(3);
        BenchMock.MockDraw d = draw(font);
        grid.draw(d, theme, 0, 0, 1F);
        d.save(new File(out, "grid-thumbs-pending-" + tag + ".png"));
        images.get(1).ready("bench:thumb/1", 320, 180);
        BenchMock.MockDraw r = draw(font);
        grid.draw(r, theme, 0, 0, 1F);
        r.save(new File(out, "grid-thumbs-ready-" + tag + ".png"));
    }

    /** A 320 by 180 stand-in for a map screenshot: sky, ground and a block, coloured per map. */
    static BufferedImage thumb(int i) {
        BufferedImage img = new BufferedImage(320, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Color hue = Color.getHSBColor(i / 6F, 0.55F, 0.85F);
        g.setPaint(new GradientPaint(0, 0, new Color(0x87B6E8), 0, 110, hue.brighter()));
        g.fillRect(0, 0, 320, 110);
        g.setColor(hue.darker());
        g.fillRect(0, 110, 320, 70);
        g.setColor(hue);
        g.fillRect(60 + i * 30, 60, 70, 50);
        g.dispose();
        return img;
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    static BenchMock.MockDraw draw(BenchMock.McFont font) {
        BenchMock.MockDraw d = new BenchMock.MockDraw(font, W, H);
        d.backdrop();
        return d;
    }
}
