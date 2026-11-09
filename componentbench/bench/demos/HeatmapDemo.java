package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.HeatmapComponent;
import dev.dryaides.minuit.InputKey;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The heatmap, driven by scripted input. */
public final class HeatmapDemo implements Demo {
    private static final int W = 280;
    private static final int H = 92;
    private static final LocalDate START = LocalDate.of(2026, 1, 1); // a Thursday

    private static double[] days(int count) {
        double[] out = new double[count];
        long seed = 12345L;
        for (int i = 0; i < count; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int r = (int) (seed >>> 58);
            out[i] = r < 20 ? 0 : (r % 16) * (1 + (i % 90) / 45D);
        }
        return out;
    }

    private static double[] table() {
        double[] out = new double[6 * 12];
        for (int i = 0; i < out.length; i++) {
            out[i] = (i % 12) * (i / 12 + 1) % 17;
        }
        return out;
    }

    private static HeatmapComponent calendar(Box box, int count) {
        return new HeatmapComponent(box).calendar(START, days(count)).legendWords("Less", "More").unit(" blocks");
    }

    private static HeatmapComponent grid(Box box) {
        return new HeatmapComponent(box).grid(6, 12, table())
                .rowLabels(List.of("Iron", "Gold", "Coal", "Lapis", "Quartz", "Emerald"))
                .colLabels(List.of("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"))
                .legendWords("Less", "More");
    }

    /** A point at a share of the box across and down: on the cells, past the labels, above the footer. */
    private static Box at(HeatmapComponent c, double across, double down) {
        return new Box("at", c.x + (int) (c.w * across), c.y + (int) (c.h * down), 1, 1);
    }

    private static Box full() {
        return new Box("h", 6, 6, W - 12, H - 12);
    }

    @Override
    public void states(Stage stage) {
        stage.state("calendar year scrolled to the newest", W, H, s -> {
            HeatmapComponent c = s.add(calendar(full(), 365));
        });

        stage.state("calendar three months fits", W, H, s -> {
            HeatmapComponent c = s.add(calendar(full(), 90));
        });

        stage.state("calendar wheel goes along", W, H, s -> {
            HeatmapComponent c = s.add(calendar(new Box("h", 6, 6, 170, H - 12), 365));
            s.scroll(50, 30, 3);
            s.move(-100, -100);
        });

        stage.state("grid with labels", W, H, s -> {
            HeatmapComponent c = s.add(grid(full()));
        });

        stage.state("hover cell tells its value", W, H, s -> {
            HeatmapComponent c = s.add(calendar(full(), 90));
            Box b = at(c, 0.5, 0.45);
            s.move(b.x(), b.y());
            s.settle(0);
        });

        stage.state("tooltip after the delay", W, H + 20, s -> {
            HeatmapComponent c = s.add(grid(full()));
            Box b = at(c, 0.45, 0.45);
            s.move(b.x(), b.y());
            s.settle(700);
        });

        stage.state("click and keys pick", W, H, s -> {
            List<Integer> picks = new ArrayList<>();
            HeatmapComponent c = s.add(grid(full()).onSelect(picks::add));
            Box b = at(c, 0.35, 0.3);
            s.click(b.x(), b.y());
            s.move(-100, -100);
            s.key(InputKey.RIGHT);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
            s.key(InputKey.HOME);
            s.key(InputKey.END);
        });

        stage.state("tab focus shows the ring on the cell", W, H, s -> {
            HeatmapComponent c = s.add(grid(full()));
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.LEFT);
            s.key(InputKey.UP);
        });

        stage.state("keys scroll to the cell", W, H, s -> {
            HeatmapComponent c = s.add(calendar(new Box("h", 6, 6, 170, H - 12), 365));
            s.focus(c);
            s.key(InputKey.DOWN);
            s.key(InputKey.HOME);
            s.key(InputKey.ESCAPE);
        });

        stage.state("calendar keys stop at the blanks", W, H, s -> {
            HeatmapComponent c = s.add(calendar(full(), 60));
            s.focus(c);
            s.key(InputKey.DOWN);
            s.key(InputKey.HOME);
            s.key(InputKey.UP);
            s.key(InputKey.UP);
            s.key(InputKey.UP);
            s.key(InputKey.UP);
            s.key(InputKey.UP);
            s.key(InputKey.UP);
        });

        stage.state("no data cells", W, H, s -> {
            double[] v = table();
            for (int i = 0; i < v.length; i += 5) {
                v[i] = Double.NaN;
            }
            HeatmapComponent c = s.add(new HeatmapComponent(full()).grid(6, 12, v).noDataText("no data"));
            Box b = at(c, 0.3, 0.2);
            s.move(b.x(), b.y());
        });

        stage.state("all the same and all zero", W, H, s -> {
            HeatmapComponent same = s.add(new HeatmapComponent(new Box("a", 6, 6, 120, H - 12)).grid(3, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7));
            HeatmapComponent zero = s.add(new HeatmapComponent(new Box("b", 140, 6, 120, H - 12)).grid(3, 6, new double[18]));
        });

        stage.state("fixed range and levels", W, H, s -> {
            HeatmapComponent c = s.add(new HeatmapComponent(full()).grid(6, 12, table()).range(0, 8).levels(3));
        });

        stage.state("fixed cell small", W, H, s -> s.add(new HeatmapComponent(full()).grid(6, 12, table()).cell(4).footer(false)));

        stage.state("thousand cells", W, H, s -> {
            double[] v = new double[20 * 100];
            for (int i = 0; i < v.length; i++) {
                v[i] = Math.sin(i * 0.02) + Math.cos(i * 0.31);
            }
            HeatmapComponent c = s.add(new HeatmapComponent(full()).grid(20, 100, v));
        });

        stage.state("long words cut", W, H, s -> {
            HeatmapComponent c = s.add(new HeatmapComponent(new Box("h", 6, 6, 170, H - 12)).grid(6, 12, table())
                    .rowLabels(List.of("A very long row name that cannot fit", "Two", "Three", "Four", "Five", "Six"))
                    .cellName(i -> "A really very long name for this very cell, number " + i));
            Box b = at(c, 0.45, 0.2);
            s.move(b.x(), b.y());
            s.settle(0);
        });

        stage.state("disabled ignores input", W, H, s -> {
            List<Integer> picks = new ArrayList<>();
            HeatmapComponent c = s.add(grid(full()).onSelect(picks::add).active(false));
            Box b = at(c, 0.35, 0.3);
            s.click(b.x(), b.y());
        });

        stage.state("hidden and empty", W, H, s -> {
            HeatmapComponent gone = s.add(grid(new Box("a", 6, 6, 120, H - 12)));
            gone.visible = false;
            HeatmapComponent none = s.add(new HeatmapComponent(new Box("b", 140, 6, 120, H - 12)).emptyText("No data yet").grid(0, 0));
            s.focus(none);
            s.key(InputKey.DOWN);
        });

        stage.state("small boxes drop parts", W, 40, s -> {
            HeatmapComponent small = s.add(calendar(new Box("a", 4, 4, 60, 30), 365));
            HeatmapComponent tiny = s.add(grid(new Box("b", 70, 4, 16, 16)));
            HeatmapComponent none = s.add(grid(new Box("c", 92, 4, 2, 2)));
        });
    }
}
