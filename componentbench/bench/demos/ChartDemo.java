package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ChartComponent;
import dev.dryaides.minuit.ChartComponent.Mode;
import dev.dryaides.minuit.ChartComponent.Scale;
import dev.dryaides.minuit.ChartComponent.Series;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.TextMeasure;
import java.util.ArrayList;
import java.util.List;

/**
 * The chart, driven by scripted input.
 */
public final class ChartDemo implements Demo {
    private static final int W = 220;
    private static final int H = 110;
    private static final double[] WAVE = {12, 18, 15, 27, 34, 30, 22, 41, 38, 26, 31, 44};
    private static final double[] OTHER = {30, 25, 28, 20, 14, 19, 26, 12, 16, 24, 21, 9};
    private static final double NA = Double.NaN;
    private static final List<String> DAYS = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

    private static ChartComponent chart(Scene s) {
        return s.add(new ChartComponent(new Box("c", 6, 6, W - 12, H - 12)));
    }

    private static ChartComponent two(Scene s, Mode mode) {
        ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, W - 12, H - 12)).mode(mode));
        c.addSeries("Iron").set(WAVE);
        c.addSeries("Gold").set(OTHER);
        return c;
    }

    /** A row half way down the chart, inside the plot. */
    private static int mid(ChartComponent c) {
        return c.y + c.h / 2;
    }

    /** Near the column of sample {@code n} of the first series: the plot spans the box less the axis labels. */
    private static int at(ChartComponent c, int n) {
        int left = c.x + 26;
        int right = c.x + c.w - 5;
        int size = c.seriesCount() == 0 ? 1 : Math.max(1, c.series(0).size());
        if (c.mode() == Mode.BAR) {
            return left + (2 * n + 1) * (right - left) / (2 * size);
        }
        return left + n * (right - left) / Math.max(1, size - 1);
    }

    /** The swatch of a legend entry: entries sit in order at the right end of the header. */
    private static int legendX(ChartComponent c, int index) {
        int at = c.x + c.w - 4;
        for (int i = c.seriesCount() - 1; i >= index; i--) {
            at -= 7 + TextMeasure.width(c.series(i).name()) + (i > index ? 6 : 0);
        }
        return at + 2;
    }

    private static int legendY(ChartComponent c) {
        return c.y + 7;
    }

    /** Every tick a round multiple of one step, on the axis, in order. */
    /** No two tick labels the same: the axis would then say nothing. */
    @Override
    public void states(Stage stage) {
        looks(stage);
        hover(stage);
        keys(stage);
        legend(stage);
        live(stage);
        gaps(stage);
        ranges(stage);
        edges(stage);
    }

    private void looks(Stage stage) {
        stage.state("line", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("Iron").set(WAVE);
        });

        stage.state("area", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.AREA);
            c.addSeries("Iron").set(WAVE);
        });

        stage.state("bar", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.BAR).categories(DAYS);
            c.addSeries("Iron").set(12, 18, 15, 27, 34, 30, 22);
        });

        stage.state("two lines", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
        });

        stage.state("two areas", W, H, s -> two(s, Mode.AREA));

        stage.state("two bars", W, H, s -> {
            ChartComponent c = two(s, Mode.BAR);
        });

        stage.state("title and legend", W, H, s -> {
            ChartComponent c = chart(s).title("Ore mined");
            c.addSeries("Iron").color(DonutChartDemo.IRON).set(WAVE);
            c.addSeries("Gold").color(DonutChartDemo.GOLD).set(OTHER);
        });

        stage.state("title only", W, H, s -> {
            ChartComponent c = chart(s).title("Frame time");
            c.addSeries("Frame time").set(WAVE);
        });

        stage.state("points", W, H, s -> {
            ChartComponent c = chart(s).points(true);
            c.addSeries("Iron").set(WAVE);
        });

        stage.state("six series", W, H, s -> {
            ChartComponent c = chart(s);
            for (int i = 0; i < 6; i++) {
                double[] values = new double[12];
                for (int k = 0; k < 12; k++) {
                    values[k] = 10 + i * 6 + WAVE[k] / 5D;
                }
                c.addSeries("S" + (i + 1)).set(values);
            }
            for (int i = 0; i < 6; i++) {
                for (int k = i + 1; k < 6; k++) {
                }
            }
        });

        stage.state("x labels thinned", W, H, s -> {
            ChartComponent c = chart(s).xLabel(n -> "t" + n);
            double[] values = new double[120];
            for (int i = 0; i < 120; i++) {
                values[i] = 20 + 12 * Math.sin(i / 8D);
            }
            c.addSeries("Wave").set(values);
        });

        stage.state("own colour", W, H, s -> {
            ChartComponent c = chart(s);
            Series own = c.addSeries("Own").color(0xFF3399FF).set(WAVE);
        });

        stage.state("custom format", W, H, s -> {
            ChartComponent c = chart(s).valueFormat(v -> Math.round(v) + "%").unit(" ignored");
            c.addSeries("Load").set(WAVE);
            s.move(c.x + c.w / 2, mid(c));
        });
    }

    private void hover(Stage stage) {
        stage.state("hover line", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE).title("Ore");
            int at = at(c, 5);
            s.move(at, mid(c));
        });

        stage.state("hover area", W, H, s -> {
            ChartComponent c = two(s, Mode.AREA);
            s.move(at(c, 3), mid(c));
        });

        stage.state("hover bar", W, H, s -> {
            ChartComponent c = two(s, Mode.BAR).categories(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"));
            s.move(at(c, 4), mid(c));
        });

        stage.state("hover near the right flips", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            int at = at(c, 11);
            s.move(at, mid(c));
        });

        stage.state("hover near the top stays in", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            s.move(at(c, 2), c.y + 16);
        });

        stage.state("hover over a gap", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("Iron").set(12, 18, NA, NA, 34, 30);
            s.move(at(c, 2), mid(c));
        });

        stage.state("hover with the x label", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE).xLabel(n -> "Minute " + n);
            s.move(at(c, 6), mid(c));
        });

        stage.state("pointer outside the plot", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            s.move(2, 2);
        });

        stage.state("pointer leaves", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            s.move(at(c, 6), mid(c));
            s.move(-100, -100);
        });

        stage.state("crosshair half way in", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            s.move(at(c, 6), mid(c));
            s.waitMs(16);
            s.settle(0);
        });

        stage.state("reduced motion crosshair", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            HostState.reducedMotion(true);
            s.move(at(c, 6), mid(c));
            HostState.reducedMotion(false);
            s.settle(0);
        });

        stage.state("click picks a sample", W, H, s -> {
            List<Long> picks = new ArrayList<>();
            ChartComponent c = two(s, Mode.LINE).onSelect(picks::add);
            s.click(at(c, 4), mid(c));
        });

        stage.state("tooltip beside the chart", W, H, s -> {
            ChartComponent c = chart(s).tooltip("A chart", "With its own tooltip");
            c.addSeries("Iron").set(WAVE);
            s.move(2 + c.x, 2 + c.y);
            s.settle(1500);
        });
    }

    private void keys(Stage stage) {
        stage.state("focus by tab", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            s.key(InputKey.TAB);
        });

        stage.state("focus by click", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            s.click(2 + c.x, 2 + c.y);
            s.move(-100, -100);
        });

        stage.state("left and right walk", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            s.focus(c);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.RIGHT);
        });

        stage.state("home and end stop", W, H, s -> {
            ChartComponent c = two(s, Mode.BAR);
            s.focus(c);
            s.key(InputKey.HOME);
            s.key(InputKey.LEFT);
            s.key(InputKey.END);
            s.key(InputKey.RIGHT);
        });

        stage.state("enter picks", W, H, s -> {
            List<Long> picks = new ArrayList<>();
            ChartComponent c = two(s, Mode.LINE).onSelect(picks::add);
            s.focus(c);
            s.key(InputKey.HOME);
            s.key(InputKey.RIGHT);
            s.key(InputKey.ENTER);
        });

        stage.state("escape puts the keys away", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            s.focus(c);
            s.key(InputKey.HOME);
            s.key(InputKey.ESCAPE);
        });

        stage.state("legend by keys", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            s.focus(c);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.UP);
        });

        stage.state("legend ring by tab", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE).title("Ore");
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
        });

        stage.state("space toggles the lit entry", W, H, s -> {
            List<String> log = new ArrayList<>();
            ChartComponent c = two(s, Mode.LINE).onToggle((v, on) -> log.add(v.name() + "=" + on));
            s.focus(c);
            s.key(InputKey.DOWN);
            s.key(InputKey.SPACE);
        });

        stage.state("keys pass on an empty chart", W, H, s -> {
            ChartComponent c = chart(s).emptyText("No data");
            c.addSeries("Iron");
            s.focus(c);
            c.keyPressed(InputKey.LEFT, false);
            c.keyPressed(InputKey.DOWN, false);
            c.keyPressed(InputKey.ENTER, false);
        });

        stage.state("cursor follows a scrolling ring", W, H, s -> {
            ChartComponent c = chart(s).live(20);
            Series v = c.addSeries("TPS");
            for (int i = 0; i < 20; i++) {
                v.add(20 - (i % 3));
            }
            s.focus(c);
            s.key(InputKey.HOME);
            for (int i = 0; i < 5; i++) {
                v.add(19);
            }
            s.waitMs(16);
        });
    }

    private void legend(Stage stage) {
        stage.state("hover legend", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            s.move(legendX(c, 1), legendY(c));
        });

        stage.state("click hides a series", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            int gold = c.seriesColor(s.theme(), c.series(1));
            s.click(legendX(c, 0), legendY(c));
            s.move(-100, -100);
        });

        stage.state("click twice shows it again", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            s.click(legendX(c, 0), legendY(c));
            s.click(legendX(c, 0), legendY(c));
            s.move(-100, -100);
        });

        stage.state("all hidden", W, H, s -> {
            ChartComponent c = two(s, Mode.BAR).emptyText("Nothing shown");
            c.series(0).shown(false);
            c.series(1).shown(false);
            s.click(legendX(c, 1), legendY(c));
            s.move(-100, -100);
            c.series(1).shown(false);
        });

        stage.state("hidden bars close the group", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.BAR).categories(DAYS);
            c.addSeries("A").set(4, 9, 6, 2, 8, 5, 3);
            c.addSeries("B").set(5, 3, 7, 6, 2, 4, 6).shown(false);
            c.addSeries("C").set(2, 6, 4, 8, 3, 7, 5);
        });

        stage.state("removed series", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE);
            Series third = c.addSeries("Coal").set(OTHER);
            int third0 = c.seriesColor(s.theme(), third);
            c.removeSeries(c.series(0));
        });
    }

    private void live(Stage stage) {
        stage.state("live filling up", W, 60, s -> {
            ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, W - 12, 48)).live(100).unit(" fps"));
            Series v = c.addSeries("FPS");
            for (int i = 0; i < 30; i++) {
                v.add(60 + 8 * Math.sin(i / 3D));
            }
        });

        stage.state("live ring wrapped", W, 60, s -> {
            ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, W - 12, 48)).live(100).unit(" fps"));
            Series v = c.addSeries("FPS");
            for (int i = 0; i < 1234; i++) {
                v.add(60 + 8 * Math.sin(i / 3D));
            }
        });

        stage.state("live two series", W, 70, s -> {
            ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, W - 12, 58)).live(120).title("Tick"));
            Series a = c.addSeries("TPS");
            Series b = c.addSeries("MSPT");
            for (int i = 0; i < 150; i++) {
                a.add(20 - (i % 40 == 0 ? 6 : 0));
                b.add(8 + 4 * Math.sin(i / 9D));
            }
        });

        stage.state("live bars", W, 60, s -> {
            ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, W - 12, 48)).mode(Mode.BAR).live(40));
            Series v = c.addSeries("Ms");
            for (int i = 0; i < 70; i++) {
                v.add(6 + (i * 13 % 17));
            }
        });

        stage.state("series out of step", W, 60, s -> {
            ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, W - 12, 48)).live(60));
            Series a = c.addSeries("A");
            Series b = c.addSeries("B");
            for (int i = 0; i < 60; i++) {
                a.add(10 + i % 7);
                if (i < 40) {
                    b.add(20 + i % 5);
                }
            }
        });

        stage.state("spike kept by the columns", W, 60, s -> {
            ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, W - 12, 48)).capacity(5000));
            Series v = c.addSeries("Ms");
            double[] values = new double[5000];
            for (int i = 0; i < 5000; i++) {
                values[i] = 10;
            }
            values[2501] = 90;
            v.set(values);
        });

        stage.state("capacity shrinks keeping the newest", W, H, s -> {
            ChartComponent c = chart(s);
            Series v = c.addSeries("A").set(WAVE);
            c.capacity(5);
        });

        stage.state("set grows the capacity", W, H, s -> {
            ChartComponent c = chart(s).capacity(4);
            Series v = c.addSeries("A").set(WAVE);
        });

        stage.state("range glides on a jump", W, H, s -> {
            ChartComponent c = chart(s);
            Series v = c.addSeries("A").set(10, 20, 15, 18);
            s.waitMs(16);
            v.add(400);
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("range jumps with reduced motion", W, H, s -> {
            ChartComponent c = chart(s);
            Series v = c.addSeries("A").set(10, 20, 15, 18);
            s.waitMs(16);
            HostState.reducedMotion(true);
            v.add(400);
            s.waitMs(16);
            HostState.reducedMotion(false);
            s.settle(0);
        });

        stage.state("range glide ends", W, H, s -> {
            ChartComponent c = chart(s);
            Series v = c.addSeries("A").set(10, 20, 15, 18);
            s.waitMs(16);
            v.add(400);
            s.waitMs(1000);
        });
    }

    private void gaps(Stage stage) {
        stage.state("line with gaps", W, H, s -> {
            ChartComponent c = chart(s);
            Series v = c.addSeries("A").set(12, 18, 15, NA, NA, 30, 22, 41, NA, 26, 31, 44);
        });

        stage.state("area with gaps", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.AREA);
            c.addSeries("A").set(12, 18, 15, NA, NA, 30, 22, 41, NA, 26, 31, 44);
        });

        stage.state("bars with gaps", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.BAR);
            c.addSeries("A").set(12, 18, 15, NA, NA, 30, 22, 41, NA, 26, 31, 44);
        });

        stage.state("a lone sample between gaps", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(NA, NA, 20, NA, NA, 30, 35, NA, 10, NA);
        });

        stage.state("gaps at both ends", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(NA, NA, 20, 25, 22, NA, NA);
        });

        stage.state("infinities are gaps", W, H, s -> {
            ChartComponent c = chart(s);
            Series v = c.addSeries("A").set(10, Double.POSITIVE_INFINITY, 20, Double.NEGATIVE_INFINITY, 30, 28);
        });

        stage.state("gap added live", W, 60, s -> {
            ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, W - 12, 48)).live(50));
            Series v = c.addSeries("A");
            for (int i = 0; i < 50; i++) {
                if (i > 20 && i < 28) {
                    v.gap();
                } else {
                    v.add(20 + i % 6);
                }
            }
        });
    }

    private void ranges(Stage stage) {
        stage.state("auto range", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(1003, 1007, 1005, 1011);
        });

        stage.state("zero range", W, H, s -> {
            ChartComponent c = chart(s).scale(Scale.ZERO);
            c.addSeries("A").set(1003, 1007, 1005, 1011);
        });

        stage.state("hold range", W, H, s -> {
            ChartComponent c = chart(s).scale(Scale.HOLD).live(5);
            Series v = c.addSeries("A");
            for (double value : new double[] {10, 90, 20, 30, 40, 50, 40, 30}) {
                v.add(value);
            }
            c.resetHold();
        });

        stage.state("fixed range cuts", W, H, s -> {
            ChartComponent c = chart(s).fixed(20, 30);
            c.addSeries("A").set(WAVE);
        });

        stage.state("fixed range with bars", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.BAR).fixed(0, 30);
            c.addSeries("A").set(WAVE);
        });

        stage.state("fixed swapped ends", W, H, s -> {
            ChartComponent c = chart(s).fixed(50, 10);
            c.addSeries("A").set(WAVE);
        });

        stage.state("floor pinned", W, H, s -> {
            ChartComponent c = chart(s).floor(0);
            c.addSeries("A").set(40, 50, 45);
        });

        stage.state("ceiling pinned", W, H, s -> {
            ChartComponent c = chart(s).ceiling(60);
            c.addSeries("A").set(40, 50, 45);
        });

        stage.state("floor above ceiling", W, H, s -> {
            ChartComponent c = chart(s).floor(50).ceiling(10);
            c.addSeries("A").set(WAVE);
        });

        stage.state("negative values", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(-20, 35, -8, 14, 42, -31, 6);
        });

        stage.state("negative area", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.AREA);
            c.addSeries("A").set(-20, 35, -8, 14, 42, -31, 6);
        });

        stage.state("negative bars", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.BAR);
            c.addSeries("A").set(-20, 35, -8, 14, 42, -31, 6);
        });

        stage.state("fractions", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(0.12, 0.31, 0.27, 0.44);
        });

        stage.state("big values", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(1_200_000, 3_400_000, 2_100_000, 4_800_000);
        });

        stage.state("huge and tiny", W, H, s -> {
            ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, 100, H - 12)));
            c.addSeries("A").set(1e300, -1e300, 5e299);
            ChartComponent t = s.add(new ChartComponent(new Box("t", 112, 6, 102, H - 12)));
            t.addSeries("A").set(1e-9, 3e-9, 2e-9);
        });

        stage.state("tiny span far from zero", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(1_000_000.001, 1_000_000.002, 1_000_000.0015);
        });
    }

    private void edges(Stage stage) {
        stage.state("empty", W, H, s -> {
            ChartComponent c = chart(s).emptyText("No data yet");
            s.move(100, 50);
            s.focus(c);
            s.key(InputKey.LEFT);
            s.key(InputKey.ENTER);
        });

        stage.state("empty no text", W, H, s -> {
            ChartComponent c = chart(s);
        });

        stage.state("empty series and legend", W, H, s -> {
            ChartComponent c = chart(s).emptyText("Waiting").title("Tick");
            c.addSeries("TPS");
            c.addSeries("MSPT");
        });

        stage.state("empty with a fixed range", W, H, s -> {
            ChartComponent c = chart(s).fixed(0, 100).emptyText("Waiting");
            c.addSeries("A");
        });

        stage.state("only gaps", W, H, s -> {
            ChartComponent c = chart(s).emptyText("All samples missing");
            c.addSeries("A").set(NA, NA, NA);
            s.move(100, 50);
        });

        stage.state("one point line", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(42);
            s.move(at(c, 0), mid(c));
        });

        stage.state("one point area", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.AREA);
            c.addSeries("A").set(42);
        });

        stage.state("one point bar", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.BAR).categories(List.of("Only"));
            c.addSeries("A").set(42);
        });

        stage.state("two points", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(10, 20);
        });

        stage.state("all equal", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(20, 20, 20, 20, 20);
        });

        stage.state("all zero", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(0, 0, 0, 0);
        });

        stage.state("all equal negative", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(-7, -7, -7);
        });

        stage.state("all equal bars", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.BAR);
            c.addSeries("A").set(5, 5, 5, 5);
        });

        stage.state("all zero bars", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.BAR);
            c.addSeries("A").set(0, 0, 0, 0);
        });

        stage.state("cleared", W, H, s -> {
            ChartComponent c = two(s, Mode.LINE).emptyText("Cleared");
            c.clear();
            c.series(0).add(3).latest();
            c.series(0).clear();
        });

        stage.state("disabled", W, H, s -> {
            List<Long> picks = new ArrayList<>();
            ChartComponent c = two(s, Mode.LINE).title("Ore").onSelect(picks::add).active(false);
            s.move(at(c, 5), mid(c));
            s.click(at(c, 5), mid(c));
            s.click(legendX(c, 0), legendY(c));
            s.focus(c);
            s.key(InputKey.LEFT);
        });

        stage.state("disabled bars", W, H, s -> {
            ChartComponent c = two(s, Mode.BAR).title("Ore").active(false);
        });

        stage.state("disabled area", W, H, s -> two(s, Mode.AREA).active(false));

        stage.state("hidden", W, 40, s -> {
            List<Long> picks = new ArrayList<>();
            ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, W - 12, 28)).onSelect(picks::add));
            c.addSeries("A").set(WAVE);
            c.visible = false;
            s.click(100, 20);
            c.keyPressed(InputKey.LEFT, false);
            s.paint((d, t) -> d.text("hidden: nothing above", 6, 14, t.palette().text()));
        });

        stage.state("tiny boxes", W, 134, s -> {
            int[][] sizes = {{1, 1}, {4, 4}, {10, 8}, {24, 12}, {40, 16}, {62, 28}, {70, 24}, {64, 30}, {70, 44}};
            int bx = 6;
            int by = 6;
            for (int[] size : sizes) {
                if (bx + size[0] > W - 6) {
                    bx = 6;
                    by += 38;
                }
                ChartComponent c = s.add(new ChartComponent(new Box("c", bx, by, size[0], size[1])));
                c.addSeries("A").set(WAVE);
                c.addSeries("B").set(OTHER);
                bx += size[0] + 4;
            }
        });

        stage.state("tiny bars and area", W, H, s -> {
            ChartComponent a = s.add(new ChartComponent(new Box("a", 6, 6, 40, 16)).mode(Mode.BAR));
            a.addSeries("A").set(WAVE);
            ChartComponent b = s.add(new ChartComponent(new Box("b", 52, 6, 40, 16)).mode(Mode.AREA));
            b.addSeries("A").set(WAVE);
            ChartComponent e = s.add(new ChartComponent(new Box("e", 98, 6, 40, 16)).emptyText("Nothing at all here"));
            e.addSeries("A");
        });

        stage.state("tiny readout becomes a tooltip", W, H, s -> {
            ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, 40, 16)));
            c.addSeries("Iron").set(WAVE);
            int at = at(c, 6);
            s.move(at, c.y + 2);
            s.settle(1500);
        });

        stage.state("no axes", W, H, s -> {
            ChartComponent c = chart(s).axes(false);
            c.addSeries("A").set(WAVE);
        });

        stage.state("long labels", W, H, s -> {
            ChartComponent c = chart(s).title("A very long title that cannot possibly fit in the chart at all")
                    .categories(List.of("A very long first category name", "Another far too long name", "Third"));
            c.addSeries("A very long series name that will not fit").set(10, 20, 15);
            c.addSeries("Another extremely long series name here").set(5, 12, 18);
            int at = at(c, 1);
            s.move(at, mid(c));
        });

        stage.state("long labels bars", W, H, s -> {
            ChartComponent c = chart(s).mode(Mode.BAR)
                    .categories(List.of("A very long first category name", "Another far too long name", "Third"));
            c.addSeries("Iron").set(10, 20, 15);
        });

        stage.state("long format narrow box", 120, H, s -> {
            ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, 108, H - 12))
                    .valueFormat(v -> "about " + Math.round(v) + " units of something"));
            c.addSeries("A").set(WAVE);
        });

        stage.state("twelve series readout", W, H, s -> {
            ChartComponent c = chart(s);
            for (int i = 0; i < 12; i++) {
                c.addSeries("Series " + (i + 1)).set(i + 2, i + 5, i + 3, i + 7);
            }
            s.move(at(c, 1), mid(c));
        });

        stage.state("beside a button", W, 74, s -> {
            ChartComponent c = s.add(new ChartComponent(new Box("c", 6, 6, 128, 62)).mode(Mode.BAR));
            c.addSeries("A").set(3, 7, 5, 9);
            s.add(new dev.dryaides.minuit.ButtonComponent(new Box("b", 140, 6, 74, 20), "Button", () -> { }));
            s.add(new dev.dryaides.minuit.SliderComponent(new Box("v", 140, 32, 74, 16), 0, 100, 40, v -> { }));
        });

        stage.state("moved box follows", W, H, s -> {
            ChartComponent c = chart(s);
            c.addSeries("A").set(WAVE);
            int before = at(c, 3);
            c.x += 0;
            c.w -= 40;
        });
    }
}
