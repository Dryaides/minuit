package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.DatePickerComponent;
import dev.dryaides.minuit.InputKey;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The field with its calendar in a popup. */
public final class DatePickerDemo implements Demo {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final int W = 170;
    private static final int TALL = 136;
    private static final int SHORT = 32;
    private static final int X = 8;
    private static final int Y = 8;
    private static final int OPENED = 150;

    private static LocalDate nov(int day) {
        return LocalDate.of(2026, 11, day);
    }

    private static DatePickerComponent picker(Scene s) {
        return picker(s, new Box("d", X, Y, 150, 16));
    }

    private static DatePickerComponent picker(Scene s, Box box) {
        DatePickerComponent d = s.add(new DatePickerComponent(box, s.panel()).placeholder("Pick a day"));
        d.calendar().today(TODAY);
        return d;
    }

    private static void clickField(Scene s, DatePickerComponent d) {
        s.click(d.x + 20, d.y + d.h / 2);
    }

    /**
     * The calendar of the field at X, Y under it: the popup sits 2 pixels below the field, its frame takes the inset
     * (3 to 4), and the grid starts under the title (14) and the weekday line (11), in cells of 18 by 12.
     */
    private static int dayX(Scene s, LocalDate day, DayOfWeek weekStart) {
        int first = nov(1).getDayOfWeek().getValue();
        LocalDate start = nov(1).minusDays(Math.floorMod(first - weekStart.getValue(), 7));
        return X + inset(s) + (int) (day.toEpochDay() - start.toEpochDay()) % 7 * 18 + 9;
    }

    private static int dayY(Scene s, LocalDate day, DayOfWeek weekStart) {
        int first = nov(1).getDayOfWeek().getValue();
        LocalDate start = nov(1).minusDays(Math.floorMod(first - weekStart.getValue(), 7));
        return Y + 16 + 2 + inset(s) + 14 + 11 + (int) (day.toEpochDay() - start.toEpochDay()) / 7 * 12 + 6;
    }

    private static int inset(Scene s) {
        return Math.max(3, Math.min(4, s.theme().frame().corner()));
    }

    private static void clickDay(Scene s, DatePickerComponent d, LocalDate day) {
        clickDay(s, d, day, DayOfWeek.MONDAY);
    }

    private static void clickDay(Scene s, DatePickerComponent d, LocalDate day, DayOfWeek weekStart) {
        s.click(dayX(s, day, weekStart), dayY(s, day, weekStart));
    }

    @Override
    public void states(Stage stage) {
        stage.state("empty", W, SHORT, s -> {
            DatePickerComponent d = picker(s);
        });
        stage.state("filled", W, SHORT, s -> {
            DatePickerComponent d = picker(s);
            d.setValue(nov(12));
        });
        stage.state("own format", W, SHORT, s -> {
            DatePickerComponent d = picker(s).format(day -> day.getDayOfMonth() + " / " + day.getMonthValue() + " / " + day.getYear());
            d.setValue(nov(12));
        });
        stage.state("hover clear", W, SHORT, s -> {
            DatePickerComponent d = picker(s);
            d.setValue(nov(12));
            s.move(X + 150 - 20, Y + 8);
        });
        stage.state("click opens below", W, TALL, s -> {
            DatePickerComponent d = picker(s);
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(OPENED);
            s.move(1, 1);
        });
        stage.state("opening half way", W, TALL, s -> {
            DatePickerComponent d = picker(s);
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(48);
            s.settle(0);
        });
        stage.state("reduced motion opens at once", W, TALL, s -> {
            DatePickerComponent d = picker(s);
            s.add(d.popup());
            BaseComponent.reducedMotion = true;
            try {
                clickField(s, d);
                s.settle(0);
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });
        stage.state("a pick closes and tells", W, TALL, s -> {
            List<LocalDate> heard = new ArrayList<>();
            DatePickerComponent d = picker(s).onChange(heard::add);
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(OPENED);
            clickDay(s, d, nov(19));
            s.move(1, 1);
        });
        stage.state("opens on the value's month", W, TALL, s -> {
            DatePickerComponent d = picker(s);
            d.setValue(LocalDate.of(2027, 3, 14));
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(OPENED);
            s.move(1, 1);
        });
        stage.state("opens above at the bottom of the panel", W, TALL, s -> {
            DatePickerComponent d = picker(s, new Box("d", X, TALL - 24, 150, 16));
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(OPENED);
            s.move(1, 1);
        });
        stage.state("stays in a short panel", W, 90, s -> {
            DatePickerComponent d = picker(s, new Box("d", X, 37, 150, 16));
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(OPENED);
            s.move(1, 1);
        });
        stage.state("right edge keeps it inside", W, TALL, s -> {
            DatePickerComponent d = picker(s, new Box("d", 120, Y, 44, 16));
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(OPENED);
            s.move(1, 1);
        });
        stage.state("covers what is beneath", W, TALL, s -> {
            DatePickerComponent d = picker(s);
            int[] pressed = new int[1];
            s.add(new ButtonComponent(new Box("b", X, 40, 150, 20), "Behind the calendar", () -> pressed[0]++));
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(OPENED);
            s.move(dayX(s, nov(11), DayOfWeek.MONDAY) - 4, dayY(s, nov(11), DayOfWeek.MONDAY) - 1);
            s.waitMs(200);
        });
        stage.state("first click outside only closes", W, TALL + 30, s -> {
            int[] pressed = new int[1];
            DatePickerComponent d = picker(s);
            s.add(new ButtonComponent(new Box("b", X, TALL + 6, 150, 20), "Beneath", () -> pressed[0]++));
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(OPENED);
            s.click(X + 20, TALL + 14);
            s.click(X + 20, TALL + 14);
            s.move(1, 1);
        });
        stage.state("keys open walk and pick", W, TALL, s -> {
            List<LocalDate> heard = new ArrayList<>();
            DatePickerComponent d = picker(s).onChange(heard::add);
            s.add(d.popup());
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.waitMs(OPENED);
            s.key(InputKey.RIGHT);
            s.key(InputKey.DOWN);
            s.settle(0);
        });
        stage.state("enter picks and closes", W, SHORT, s -> {
            DatePickerComponent d = picker(s);
            s.add(d.popup());
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
            s.waitMs(OPENED);
            s.key(InputKey.RIGHT);
            s.key(InputKey.ENTER);
        });
        stage.state("escape closes then lets go", W, SHORT, s -> {
            DatePickerComponent d = picker(s);
            s.add(d.popup());
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.waitMs(OPENED);
            s.key(InputKey.ESCAPE);
            s.key(InputKey.ESCAPE);
        });
        stage.state("tab closes and moves on", W, 52, s -> {
            DatePickerComponent d = picker(s);
            ButtonComponent b = s.add(new ButtonComponent(new Box("b", X, 28, 80, 18), "Next", () -> { }));
            s.add(d.popup());
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.waitMs(OPENED);
            s.key(InputKey.TAB);
        });
        stage.state("range mid way", W, TALL, s -> {
            List<String> heard = new ArrayList<>();
            DatePickerComponent d = picker(s).range(true).onRange((a, b) -> heard.add(a + ".." + b));
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(OPENED);
            clickDay(s, d, nov(10));
            s.move(dayX(s, nov(20), DayOfWeek.MONDAY) - 5, dayY(s, nov(20), DayOfWeek.MONDAY) - 2);
        });
        stage.state("range done", W, TALL, s -> {
            List<String> heard = new ArrayList<>();
            DatePickerComponent d = picker(s).range(true).onRange((a, b) -> heard.add(a + ".." + b));
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(OPENED);
            clickDay(s, d, nov(10));
            clickDay(s, d, nov(20));
            s.move(1, 1);
        });
        stage.state("closing drops a started range", W, TALL, s -> {
            DatePickerComponent d = picker(s).range(true);
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(OPENED);
            clickDay(s, d, nov(10));
            s.key(InputKey.ESCAPE);
            s.key(InputKey.ESCAPE);
            s.move(1, 1);
        });
        stage.state("clear empties and tells", W, SHORT, s -> {
            List<LocalDate> heard = new ArrayList<>();
            DatePickerComponent d = picker(s).onChange(heard::add);
            d.setValue(nov(12));
            s.click(X + 150 - 20, Y + 8);
            s.move(1, 1);
        });
        stage.state("the calendar's rules reach the popup", W, TALL, s -> {
            DatePickerComponent d = picker(s);
            d.calendar().weekStart(DayOfWeek.SUNDAY).disabled(day -> day.getDayOfWeek() == DayOfWeek.SATURDAY);
            s.add(d.popup());
            clickField(s, d);
            s.waitMs(OPENED);
            clickDay(s, d, nov(7), DayOfWeek.SUNDAY);
            s.move(1, 1);
        });
        stage.state("disabled", W, SHORT, s -> {
            DatePickerComponent d = picker(s).active(false);
            d.setValue(nov(12));
            clickField(s, d);
            s.key(InputKey.TAB);
        });
        stage.state("long text in a narrow box", W, 62, s -> {
            DatePickerComponent d = picker(s, new Box("d", X, Y, 80, 16)).format(day -> "The twelfth of November 2026");
            d.setValue(nov(12));
            s.move(X + 10, Y + 8);
            s.settle(1500);
        });
        stage.state("tiny", W, SHORT, s -> {
            DatePickerComponent d = picker(s, new Box("d", X, Y, 24, 10));
            d.setValue(nov(12));
        });
    }
}
