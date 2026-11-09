package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.CalendarComponent;
import dev.dryaides.minuit.InputKey;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** The month grid, driven by scripted input. */
public final class CalendarDemo implements Demo {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final int W = CalendarComponent.WIDTH + 16;
    private static final int H = CalendarComponent.HEIGHT + 14;
    private static final int X = 8;
    private static final int Y = 7;

    private static LocalDate nov(int day) {
        return LocalDate.of(2026, 11, day);
    }

    private static CalendarComponent cal(Scene s, List<LocalDate> heard) {
        return s.add(new CalendarComponent(new Box("c", X, Y, CalendarComponent.WIDTH, CalendarComponent.HEIGHT), heard::add).today(TODAY));
    }

    private static final int CELL_W = CalendarComponent.WIDTH / 7;
    private static final int CELL_H = 12;
    private static final int GRID_TOP = Y + 14 + 11;
    private static final int NAV_W = 14;
    private static final LocalDate GRID_START = nov(1).minusDays(6);

    /** The cell of a day in the November grid, Monday first, as the demo laid the calendar out. */
    private static int dayX(LocalDate day) {
        return X + (int) (day.toEpochDay() - GRID_START.toEpochDay()) % 7 * CELL_W + CELL_W / 2;
    }

    private static int dayY(LocalDate day) {
        return GRID_TOP + (int) (day.toEpochDay() - GRID_START.toEpochDay()) / 7 * CELL_H + CELL_H / 2;
    }

    private static void clickDay(Scene s, CalendarComponent c, LocalDate day) {
        s.click(dayX(day), dayY(day));
    }

    private static void moveDay(Scene s, CalendarComponent c, LocalDate day) {
        s.move(dayX(day), dayY(day));
    }

    @Override
    public void states(Stage stage) {
        stage.state("default today marked", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>());
        });
        stage.state("week from sunday", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>()).weekStart(DayOfWeek.SUNDAY);
        });
        stage.state("a leap february", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>());
            c.show(YearMonth.of(2028, 2));
        });
        stage.state("names passed in", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>())
                    .monthNames(List.of("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"))
                    .dayNames(List.of("Lu", "Ma", "Me", "Je", "Ve", "Sa", "Di"));
        });
        stage.state("names from words keep what is missing", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>()).labels((key, args) -> key.endsWith(".month.11") ? "Brumaire" : key, "test");
        });
        stage.state("hover a day", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>());
            moveDay(s, c, nov(18));
        });
        stage.state("hover the header arrow", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>());
            s.move(X + CalendarComponent.WIDTH - NAV_W + 6, Y + 6);
        });
        stage.state("click picks a day", W, H, s -> {
            List<LocalDate> heard = new ArrayList<>();
            CalendarComponent c = cal(s, heard);
            clickDay(s, c, nov(12));
            s.move(1, 1);
        });
        stage.state("picked on today", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>());
            c.setValue(TODAY);
        });
        stage.state("outside day moves the month", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>());
            clickDay(s, c, LocalDate.of(2026, 12, 2));
        });
        stage.state("outside days blank", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>()).showOutsideDays(false);
            clickDay(s, c, LocalDate.of(2026, 10, 30));
        });
        stage.state("disabled days", W, H, s -> {
            List<LocalDate> heard = new ArrayList<>();
            CalendarComponent c = cal(s, heard).disabled(d -> d.getDayOfWeek() == DayOfWeek.SUNDAY || d.getDayOfMonth() == 18);
            clickDay(s, c, nov(18));
            moveDay(s, c, nov(18));
        });
        stage.state("limits stop the arrows", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>()).limits(nov(4), nov(20));
            s.click(X + 5, Y + 5);
            s.move(X + 5, Y + 5);
        });
        stage.state("range first pick and preview", W, H, s -> {
            List<String> heard = new ArrayList<>();
            CalendarComponent c = cal(s, new ArrayList<>()).range(true).onRange((a, b) -> heard.add(a + ".." + b));
            clickDay(s, c, nov(10));
            moveDay(s, c, nov(24));
        });
        stage.state("range complete", W, H, s -> {
            List<String> heard = new ArrayList<>();
            CalendarComponent c = cal(s, new ArrayList<>()).range(true).onRange((a, b) -> heard.add(a + ".." + b));
            clickDay(s, c, nov(10));
            clickDay(s, c, nov(19));
            s.move(1, 1);
        });
        stage.state("range backwards", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>()).range(true);
            clickDay(s, c, nov(19));
            clickDay(s, c, nov(10));
            s.move(1, 1);
        });
        stage.state("range across months", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>()).range(true);
            clickDay(s, c, nov(27));
            clickDay(s, c, LocalDate.of(2026, 12, 3));
            s.move(1, 1);
        });
        stage.state("range one day", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>()).range(true);
            clickDay(s, c, nov(11));
            clickDay(s, c, nov(11));
            s.move(1, 1);
        });
        stage.state("range cannot cross a disabled day", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>()).range(true).disabled(d -> d.getDayOfMonth() == 15);
            clickDay(s, c, nov(10));
            clickDay(s, c, nov(20));
            s.move(1, 1);
        });
        stage.state("escape drops a started range", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>()).range(true);
            clickDay(s, c, nov(10));
            s.key(InputKey.ESCAPE);
            s.key(InputKey.ESCAPE);
        });
        stage.state("keys from tab show a ring on the day", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>());
            s.key(InputKey.TAB);
        });
        stage.state("arrows move and enter picks", W, H, s -> {
            List<LocalDate> heard = new ArrayList<>();
            CalendarComponent c = cal(s, heard);
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
            s.key(InputKey.DOWN);
            s.key(InputKey.LEFT);
            s.key(InputKey.UP);
            s.key(InputKey.ENTER);
            s.key(InputKey.RIGHT);
            s.key(InputKey.SPACE);
        });
        stage.state("keys cross the month", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>());
            s.key(InputKey.TAB);
            for (int i = 0; i < 4; i++) {
                s.key(InputKey.DOWN);
            }
        });
        stage.state("shift keys jump", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>());
            c.setValue(LocalDate.of(2026, 1, 31));
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT, true);
            s.key(InputKey.UP, true);
            s.key(InputKey.HOME, true);
            s.key(InputKey.END, true);
            s.key(InputKey.HOME);
            s.key(InputKey.END);
        });
        stage.state("keys step over disabled days", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>()).disabled(d -> d.getDayOfMonth() == 7 || d.getDayOfMonth() == 8);
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
        });
        stage.state("wheel changes the month", W, H, s -> {
            List<String> months = new ArrayList<>();
            CalendarComponent c = cal(s, new ArrayList<>()).onMonth(() -> months.add("m"));
            s.scroll(X + 40, Y + 50, -1);
            s.scroll(X + 40, Y + 50, 1);
            s.scroll(X + 40, Y + 50, 1);
        });
        stage.state("month sliding half way", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>());
            s.click(X + CalendarComponent.WIDTH - NAV_W + 5, Y + 5);
            s.waitMs(32);
            s.settle(0);
        });
        stage.state("reduced motion has no slide", W, H, s -> {
            CalendarComponent c = cal(s, new ArrayList<>());
            BaseComponent.reducedMotion = true;
            try {
                s.click(X + CalendarComponent.WIDTH - NAV_W + 5, Y + 5);
                s.settle(0);
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });
        stage.state("disabled calendar", W, H, s -> {
            List<LocalDate> heard = new ArrayList<>();
            CalendarComponent c = cal(s, heard);
            c.setValue(nov(12));
            c.active(false);
            clickDay(s, c, nov(20));
            s.scroll(X + 40, Y + 50, -1);
            s.key(InputKey.TAB);
        });
        stage.state("day tooltip", 200, H + 20, s -> {
            CalendarComponent c = cal(s, new ArrayList<>()).disabled(d -> d.getDayOfMonth() == 18)
                    .dayTooltip(d -> d.getDayOfMonth() == 18 ? List.of("The server is shut") : null);
            moveDay(s, c, nov(18));
            s.settle(1500);
        });
        stage.state("too small draws the title only", W, 40, s -> {
            CalendarComponent c = s.add(new CalendarComponent(new Box("c", X, Y, 60, 20), d -> { }).today(TODAY));
            s.click(X + 30, Y + 10);
        });
        stage.state("a larger box", 240, 150, s -> s.add(new CalendarComponent(new Box("c", X, Y, 224, 136), d -> { }).today(TODAY)));
    }
}
