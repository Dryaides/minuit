package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.DonutChartComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.ItemView;
import java.util.ArrayList;
import java.util.List;

/** The pie and the donut, driven by scripted input. */
public final class DonutChartDemo implements Demo {
    private static final int W = 200;
    private static final int H = 96;
    /** The ores in their own colours, as the game draws the ingot or the gem. */
    static final int IRON = 0xFFE2C9B5;
    static final int GOLD = 0xFFFFC21A;
    static final int COPPER = 0xFFE77C56;
    static final int DIAMOND = 0xFF4AEDD9;
    static final int REDSTONE = 0xFFE0281C;
    static final int EMERALD = 0xFF41DC7A;
    static final int LAPIS = 0xFF4A6FE0;

    private static DonutChartComponent ores(Box box) {
        return new DonutChartComponent(box).centerLabel("Blocks")
                .slice("Iron", 120, IRON).slice("Gold", 40, GOLD).slice("Copper", 80, COPPER).slice("Diamond", 12, DIAMOND);
    }

    /** A storage: a few big piles and a tail of rare things. */
    private static DonutChartComponent storage(Box box) {
        return new DonutChartComponent(box).centerLabel("Items")
                .slice("Cobblestone", 1234).slice("Dirt", 640).slice("Andesite", 300).slice("Iron ingot", 64, IRON)
                .slice("Gold ingot", 12, GOLD).slice("Coal", 9).slice("Diamond", 5, DIAMOND).slice("Redstone", 3, REDSTONE)
                .slice("Emerald", 2, EMERALD).slice("Lapis", 1, LAPIS);
    }

    /** Each slice about half the one before: how much goes into Other depends on the size alone. */
    private static DonutChartComponent halving(Box box) {
        DonutChartComponent c = new DonutChartComponent(box);
        int[] values = {600, 300, 150, 60, 30, 16, 9, 5, 3};
        for (int i = 0; i < values.length; i++) {
            c.slice(String.valueOf((char) ('A' + i)), values[i]);
        }
        return c;
    }

    private static final String[][] ITEMS = {
        {"Cobblestone", "cobblestone", "1234"}, {"Dirt", "dirt", "640"}, {"Andesite", "andesite", "300"},
        {"Iron ingot", "iron_ingot", "64"}, {"Gold ingot", "gold_ingot", "12"}, {"Coal", "coal", "9"},
        {"Diamond", "diamond", "5"}, {"Redstone", "redstone", "3"}, {"Emerald", "emerald", "2"},
    };

    private static DonutChartComponent items(Box box) {
        DonutChartComponent c = new DonutChartComponent(box).centerLabel("Items");
        for (String[] item : ITEMS) {
            c.slice(item[0], Integer.parseInt(item[2]), 0, ItemView.of("minecraft:" + item[1]));
        }
        return c;
    }

    private static Box big() {
        return new Box("d", 6, 6, W - 12, H - 12);
    }

    /** About how wide the legend is beside the disc: what a pointer needs, not the exact layout. */
    private static int legendW(DonutChartComponent c) {
        return Math.min(c.w - c.h - 6, 80);
    }

    /** A point in the middle of a slice's arc, on a disc centred in the room the legend leaves. */
    private static int[] onSlice(DonutChartComponent c, int index) {
        int room = c.w - legendW(c) - 6;
        double turn = (c.arcFrom(index) + c.arcTo(index)) / 2D * 2D * Math.PI;
        double r = Math.min(room, c.h) / 2D - 6;
        return new int[] {(int) (c.x + room / 2D + Math.sin(turn) * r), (int) (c.y + c.h / 2D - Math.cos(turn) * r)};
    }

    /** A point on a legend row: rows of 10, centred down the box, at its right. */
    private static int[] onRow(DonutChartComponent c, int row) {
        int shown = Math.min(c.rowCount(), c.h / 10);
        return new int[] {c.x + c.w - legendW(c) + 10, c.y + (c.h - shown * 10) / 2 + row * 10 + 4};
    }

    @Override
    public void states(Stage stage) {
        stage.state("donut", W, H, s -> {
            DonutChartComponent c = s.add(ores(big()));
        });

        for (dev.dryaides.minuit.SeriesPalette palette : new dev.dryaides.minuit.SeriesPalette[] {dev.dryaides.minuit.SeriesPalette.THEME,
                dev.dryaides.minuit.SeriesPalette.CONTRAST, dev.dryaides.minuit.SeriesPalette.VIVID,
                dev.dryaides.minuit.SeriesPalette.PASTEL, dev.dryaides.minuit.SeriesPalette.SHADES}) {
            stage.state("series palette " + palette.id().substring("minuit:".length()), W, H, s -> {
                DonutChartComponent c = s.add(ores(big()).seriesPalette(palette));
            });
        }

        stage.state("pie", W, H, s -> {
            DonutChartComponent c = s.add(ores(big()).pie(true));
        });

        stage.state("hover slice", W, H, s -> {
            DonutChartComponent c = s.add(ores(big()));
            int[] at = onSlice(c, 0);
            s.move(at[0], at[1]);
        });

        stage.state("hover small slice", W, H, s -> {
            DonutChartComponent c = s.add(ores(big()));
            int[] at = onSlice(c, 3);
            s.move(at[0], at[1]);
        });

        stage.state("hover legend row lights the slice", W, H, s -> {
            DonutChartComponent c = s.add(ores(big()));
            int[] row = onRow(c, 2);
            s.move(row[0], row[1]);
        });

        stage.state("pie tooltip", W, 110, s -> {
            DonutChartComponent c = s.add(ores(new Box("d", 6, 6, W - 12, H - 12)).pie(true).unit(" kg"));
            int[] at = onSlice(c, 2);
            s.move(at[0], at[1]);
            s.settle(700);
        });

        stage.state("click and keys pick", W, H, s -> {
            List<Integer> picks = new ArrayList<>();
            DonutChartComponent c = s.add(ores(big()).onSelect(picks::add));
            int[] at = onSlice(c, 1);
            s.click(at[0], at[1]);
            s.move(-100, -100);
            s.key(InputKey.RIGHT);
            s.key(InputKey.ENTER);
            s.key(InputKey.END);
            s.key(InputKey.HOME);
            s.key(InputKey.LEFT);
            s.key(InputKey.DOWN);
        });

        stage.state("tab focus shows the ring", W, H, s -> {
            DonutChartComponent c = s.add(ores(big()));
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
        });

        stage.state("escape lets go of the slice", W, H, s -> {
            DonutChartComponent c = s.add(ores(big()));
            s.focus(c);
            s.key(InputKey.DOWN);
            s.key(InputKey.ESCAPE);
        });

        stage.state("legend below in a tall box", 110, 150, s -> {
            DonutChartComponent c = s.add(ores(new Box("d", 6, 6, 98, 138)));
        });

        stage.state("thin ring and own colours", W, H, s -> s.add(new DonutChartComponent(big()).thickness(5)
                .centerValue("3 of 4").centerLabel("Done")
                .slice("Done", 3, 0xFF3DB46D).slice("Open", 1, 0xFF808080)));

        stage.state("tiny slices stay visible", W, H, s -> {
            DonutChartComponent c = s.add(new DonutChartComponent(big()).groupSmall(false)
                    .slice("Most", 100000).slice("Tiny", 1).slice("Tiny too", 1).slice("Rest", 900));
        });

        stage.state("zero negative and NaN keep their row", W, H, s -> {
            DonutChartComponent c = s.add(new DonutChartComponent(big())
                    .slice("Real", 5).slice("Zero", 0).slice("Below", -3).slice("Not a number", Double.NaN).slice("Also real", 5));
        });

        stage.state("one slice is a whole ring", W, H, s -> {
            DonutChartComponent c = s.add(new DonutChartComponent(big()).centerLabel("All").slice("Only", 7));
        });

        stage.state("long names cut, tooltip whole", W, 110, s -> {
            String name = "A really very long name for a slice of this chart";
            DonutChartComponent c = s.add(new DonutChartComponent(new Box("d", 6, 6, W - 12, 84))
                    .slice(name, 3).slice("Short", 2));
            int[] row = onRow(c, 0);
            s.move(row[0], row[1]);
            s.settle(700);
        });

        stage.state("many slices, legend wheel", W, H, s -> {
            DonutChartComponent c = new DonutChartComponent(big());
            for (int i = 0; i < 14; i++) {
                c.slice("Slice " + (i + 1), 14 - i);
            }
            s.add(c);
            int[] row = onRow(c, 1);
            s.scroll(row[0], row[1], -2);
            s.key(InputKey.TAB);
            s.key(InputKey.END);
            s.move(-100, -100);
        });

        stage.state("small slices go into Other", W, H, s -> {
            DonutChartComponent c = s.add(storage(big()));
            double sum = 0D;
            for (int i = 0; i < c.sliceCount(); i++) {
                sum += c.inOther(i) ? c.share(i) : 0D;
            }
        });

        stage.state("a bigger chart groups less", 360, 170, s -> {
            DonutChartComponent small = s.add(halving(new Box("a", 4, 4, 140, 64)));
            DonutChartComponent large = s.add(halving(new Box("b", 148, 4, 208, 162)));
        });

        stage.state("one small slice stays alone", W, H, s -> {
            DonutChartComponent c = s.add(ores(big()).slice("Emerald", 1));
        });

        stage.state("max slices", W, H, s -> {
            DonutChartComponent c = s.add(storage(big()).maxSlices(3));
        });

        stage.state("grouping off", W, H, s -> {
            DonutChartComponent c = s.add(storage(big()).groupSmall(false));
        });

        stage.state("hover Other lists its slices", W, 120, s -> {
            DonutChartComponent c = s.add(storage(big()));
            int[] at = onSlice(c, DonutChartComponent.OTHER);
            s.move(at[0], at[1]);
            List<String> tip = s.panel().tooltipAt(at[0], at[1]);
            s.settle(700);
        });

        stage.state("Other tooltip cut", W, 120, s -> {
            DonutChartComponent c = new DonutChartComponent(big()).slice("Most", 5000);
            for (int i = 0; i < 12; i++) {
                c.slice("Bit " + (i + 1), 1 + i % 3);
            }
            s.add(c);
            int[] row = onRow(c, c.rowCount() - 1);
            s.move(row[0], row[1]);
            s.settle(700);
        });

        stage.state("click opens Other", W, H, s -> {
            DonutChartComponent c = s.add(storage(big()));
            int inOther = c.otherCount();
            int[] at = onSlice(c, DonutChartComponent.OTHER);
            s.click(at[0], at[1]);
            s.move(-100, -100);
        });

        stage.state("back comes up", W, H, s -> {
            DonutChartComponent c = s.add(storage(big()));
            s.focus(c);
            s.key(InputKey.END);
            s.key(InputKey.ENTER);
            s.key(InputKey.ESCAPE);
        });

        stage.state("items in the legend", 260, 170, s -> {
            DonutChartComponent c = s.add(items(new Box("d", 6, 6, 248, 158)));
        });

        stage.state("items disabled", 260, 170, s -> s.add(items(new Box("d", 6, 6, 248, 158)).active(false)));

        stage.state("empty data", W, H, s -> {
            DonutChartComponent c = s.add(new DonutChartComponent(big()).emptyText("No data").slice("Nothing", 0));
        });

        stage.state("no slices at all", W, H, s -> {
            DonutChartComponent c = s.add(new DonutChartComponent(big()).emptyText("No data"));
            s.focus(c);
            s.key(InputKey.DOWN);
        });

        stage.state("disabled ignores input", W, H, s -> {
            List<Integer> picks = new ArrayList<>();
            DonutChartComponent c = s.add(ores(big()).onSelect(picks::add).active(false));
            int[] at = onSlice(c, 0);
            s.click(at[0], at[1]);
        });

        stage.state("hidden takes nothing", W, H, s -> {
            List<Integer> picks = new ArrayList<>();
            DonutChartComponent c = s.add(ores(big()).onSelect(picks::add));
            c.visible = false;
            int[] at = onSlice(c, 0);
            s.click(at[0], at[1]);
        });

        stage.state("reduced motion lights at once", W, H, s -> {
            DonutChartComponent c = s.add(ores(big()));
            int[] at = onSlice(c, 0);
            s.settle(0);
            HostState.reducedMotion(true);
            try {
                s.move(at[0], at[1]);
            } finally {
                HostState.reducedMotion(false);
            }
        });

        stage.state("small boxes drop parts", W, 60, s -> {
            DonutChartComponent mid = s.add(ores(new Box("a", 4, 4, 64, 52)));
            DonutChartComponent small = s.add(ores(new Box("b", 74, 4, 30, 30)));
            DonutChartComponent tiny = s.add(ores(new Box("c", 110, 4, 10, 10)));
            DonutChartComponent none = s.add(ores(new Box("d", 126, 4, 2, 2)));
        });
    }
}
