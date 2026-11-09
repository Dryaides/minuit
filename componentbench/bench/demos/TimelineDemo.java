package bench.demos;

import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.TimelineComponent;
import java.util.ArrayList;
import java.util.List;

/** The timeline, driven by scripted input. */
public final class TimelineDemo implements Demo {
    private static final int W = 220;
    private static final int H = 110;

    private record Event(String title, String when, String text, TimelineComponent.Marker kind) { }

    private static final List<Event> FIVE = List.of(
            new Event("Server started", "09:12", "All worlds loaded in four seconds.", TimelineComponent.Marker.GOOD),
            new Event("Backup finished", "09:30", "Saved 2.4 GB to the second disk, keeping the last five copies of each world.", TimelineComponent.Marker.PRIMARY),
            new Event("Slow tick", "10:02", "One tick took 240 ms.", TimelineComponent.Marker.WARN),
            new Event("Chunk error", "10:41", "A chunk failed to load.", TimelineComponent.Marker.BAD),
            new Event("Quiet night", "23:00", "", TimelineComponent.Marker.MUTED));

    private static TimelineComponent<Event> plain(Box box, List<Event> onClick) {
        TimelineComponent<Event> c = new TimelineComponent<Event>(box, Event::title, onClick == null ? null : onClick::add)
                .time(Event::when).marker(Event::kind);
        c.setItems(FIVE);
        return c;
    }

    private static Box box() {
        return new Box("t", 6, 6, W - 12, H - 12);
    }

    @Override
    public void states(Stage stage) {
        stage.state("title and time", W, H, s -> {
            TimelineComponent<Event> c = s.add(plain(box(), null));
        });

        stage.state("hover row", W, H, s -> {
            TimelineComponent<Event> c = s.add(plain(box(), null));
            s.move(60, 30);
        });

        stage.state("item markers, rows of 18", W, H, s -> {
            List<dev.dryaides.minuit.ItemView> items = List.of(dev.dryaides.minuit.ItemView.of("minecraft:diamond"),
                    dev.dryaides.minuit.ItemView.of("minecraft:iron_ingot", 12), dev.dryaides.minuit.ItemView.of("minecraft:bread"),
                    dev.dryaides.minuit.ItemView.of("minecraft:torch", 64), dev.dryaides.minuit.ItemView.of("minecraft:stone"));
            TimelineComponent<Event> c = s.add(new TimelineComponent<Event>(box(), Event::title, null, 0, true)
                    .time(Event::when).marker(Event::kind).iconArt(e -> items.get(FIVE.indexOf(e))));
            c.setItems(FIVE);
            s.move(60, 30);
        });

        stage.state("item markers in plain rows (shrunk)", W, H, s -> {
            TimelineComponent<Event> c = s.add(plain(box(), null)
                    .iconArt(e -> e.kind() == TimelineComponent.Marker.BAD ? null : dev.dryaides.minuit.ItemView.of("minecraft:diamond")));
            s.move(60, 30);
        });

        stage.state("click selects and calls back", W, H, s -> {
            List<Event> heard = new ArrayList<>();
            TimelineComponent<Event> c = s.add(plain(box(), heard));
            s.click(60, 30);
            s.move(-100, -100);
        });

        stage.state("keys walk the rows", W, H, s -> {
            TimelineComponent<Event> c = s.add(plain(box(), new ArrayList<>()));
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.END);
            s.key(InputKey.HOME);
            s.key(InputKey.UP);
        });

        stage.state("enter calls back, escape clears", W, H, s -> {
            List<Event> heard = new ArrayList<>();
            TimelineComponent<Event> c = s.add(plain(box(), heard));
            s.focus(c);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
            s.key(InputKey.ESCAPE);
        });

        stage.state("body wrapped to two lines", W, 150, s -> {
            TimelineComponent<Event> c = new TimelineComponent<Event>(new Box("t", 6, 6, W - 12, 138), Event::title, null, 2)
                    .time(Event::when).body(Event::text).marker(Event::kind);
            c.setItems(FIVE);
            s.add(c);
            s.waitMs(16);
            s.move(60, 60);
            s.move(-100, -100);
        });

        stage.state("overflow scrolls with the wheel", W, 70, s -> {
            TimelineComponent<Event> c = s.add(plain(new Box("t", 6, 6, W - 12, 58), null));
            s.scroll(60, 30, -1);
            s.move(-100, -100);
        });

        stage.state("own colours and icons", W, H, s -> {
            TimelineComponent<Event> c = new TimelineComponent<Event>(box(), Event::title, (java.util.function.Consumer<Event>) null)
                    .time(Event::when)
                    .markerColor(e -> e == FIVE.get(1) ? 0xFFFF8800 : null)
                    .icon(e -> e == FIVE.get(3) ? Icon.WARN : e == FIVE.get(0) ? Icon.CHECK : null);
            c.setItems(FIVE);
            s.add(c);
        });

        stage.state("one event and two", W, 50, s -> {
            TimelineComponent<Event> a = new TimelineComponent<Event>(new Box("a", 6, 6, 100, 38), Event::title, (java.util.function.Consumer<Event>) null);
            a.setItems(FIVE.subList(0, 1));
            s.add(a);
            TimelineComponent<Event> b = new TimelineComponent<Event>(new Box("b", 112, 6, 102, 38), Event::title, (java.util.function.Consumer<Event>) null);
            b.setItems(FIVE.subList(0, 2));
            s.add(b);
        });

        stage.state("no events", W, 50, s -> {
            TimelineComponent<Event> c = s.add(new TimelineComponent<Event>(new Box("a", 6, 6, W - 12, 38), Event::title, (java.util.function.Consumer<Event>) null).emptyText("Nothing yet"));
            c.setItems(List.of());
            s.focus(c);
            s.key(InputKey.DOWN);
        });

        stage.state("long words cut, tooltip whole", W, 90, s -> {
            Event e = new Event("A really very long title for an event in this timeline", "yesterday", "", TimelineComponent.Marker.WARN);
            TimelineComponent<Event> c = new TimelineComponent<Event>(new Box("t", 6, 6, W - 12, 40), Event::title, (java.util.function.Consumer<Event>) null).time(Event::when).marker(Event::kind);
            c.setItems(List.of(e));
            s.add(c);
            s.move(60, 12);
            s.settle(700);
        });

        stage.state("hundred thousand rows ask only for those in view", W, H, s -> {
            List<Event> many = new ArrayList<>(100_000);
            for (int i = 0; i < 100_000; i++) {
                many.add(new Event("Event " + i, "t" + i, "", TimelineComponent.Marker.PRIMARY));
            }
            TimelineComponent<Event> c = new TimelineComponent<Event>(box(), Event::title, (java.util.function.Consumer<Event>) null).time(Event::when);
            c.setItems(many);
            s.add(c);
            s.waitMs(16);
            c.setScroll(700_000D);
            s.waitMs(32);
        });

        stage.state("disabled ignores input", W, H, s -> {
            List<Event> heard = new ArrayList<>();
            TimelineComponent<Event> c = s.add(plain(box(), heard).active(false));
            s.click(60, 30);
        });

        stage.state("hidden and tiny", W, 40, s -> {
            TimelineComponent<Event> gone = s.add(plain(new Box("a", 4, 4, 60, 30), null));
            gone.visible = false;
            s.add(plain(new Box("b", 70, 4, 18, 30), null));
            s.add(plain(new Box("c", 94, 4, 60, 10), null));
            s.add(plain(new Box("d", 160, 4, 3, 3), null));
        });
    }
}
