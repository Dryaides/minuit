package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.ReorderListComponent;
import dev.dryaides.minuit.ScrollComponent;
import dev.dryaides.minuit.SliderComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The reorderable list, driven by scripted input.
 */
public final class ReorderListDemo implements Demo {
    private static final int W = 200;
    private static final int LX = 6;
    private static final int LY = 6;
    private static final int LW = 188;
    private static final int ROW = 14;
    private static final int SLID = 150;
    private static final int RAW_ALT = 342;
    private static final int RAW_RIGHT_ALT = 346;
    private static final int RAW_ESCAPE = 256;
    private static final int RAW_PAGE_UP = 266;
    private static final int RAW_PAGE_DOWN = 267;
    private static final int RAW_A = 65;
    private static final List<String> BLOCKS = List.of("Stone", "Dirt", "Oak Planks", "Glass", "Sand");
    private static final List<String> MANY = List.of("Stone", "Dirt", "Oak Planks", "Glass", "Sand", "Gravel",
            "Obsidian", "Iron Ore", "Gold Ore", "Clay", "Netherrack", "Basalt");
    private static final String LONG = "A very long block name that cannot possibly fit in this row at all";

    /** A scene with a line of values beneath, and every callback written down. */
    private static final class Rig {
        final Scene s;
        final List<String> events = new ArrayList<>();
        ReorderListComponent<String> c;
        Supplier<String> note;

        Rig(Scene s, int lineY) {
            this.s = s;
            s.paint((d, t) -> d.textFit(note != null ? note.get() : String.join(" ", events), LX, lineY, W - 2 * LX, t.palette().text()));
        }

        ReorderListComponent<String> list(int shownRows, List<String> items) {
            c = new ReorderListComponent<>(new Box("r", LX, LY, LW, shownRows * ROW + 2), items, n -> n);
            c.onReorder((order, moved, from, to) -> events.add(moved + ":" + from + ">" + to))
                    .onDelete((item, at) -> events.add("del:" + item + "@" + at))
                    .onSelect(n -> events.add("sel:" + n))
                    .onActivate(n -> events.add("act:" + n));
            return s.add(c);
        }

        int scrolled;

        void scrollTo(int offset) {
            c.setScroll(offset);
            scrolled = offset;
        }

        int hx() { return handleX(c) + 5; }
        int mid(int row) { return LY + 1 + row * ROW - scrolled + ROW / 2; }
        int dx() { return deleteX(c) + 5; }

        /** Presses a row's handle and moves by that many pixels, still held. */
        void lift(int row, int by) {
            s.hold(hx(), mid(row), hx(), mid(row) + by);
        }

        void drop() {
            s.release(s.mouseX(), s.mouseY());
        }

        boolean raw(int code) {
            return s.panel().rawKey(KeyPress.key(code, 0));
        }

        /** Alt is pressed and the key follows, as the game sends them. */
        void alt(InputKey key) {
            raw(RAW_ALT);
            s.key(key);
        }

        long reorders() {
            return events.stream().filter(e -> e.contains(">")).count();
        }
    }

    /** Left edge of the handle: one pixel of border, then one of padding. */
    private static int handleX(ReorderListComponent<?> c) {
        return c.x + 1 + 1;
    }

    /** Left edge of the delete button: at the right of the rows, left of the scroll bar when the rows overflow. */
    private static int deleteX(ReorderListComponent<?> c) {
        boolean overflows = c.items().size() * ROW > c.h - 2;
        int viewW = Math.max(0, c.w - 2 - (overflows ? ScrollComponent.BAR : 0));
        return c.x + 1 + viewW - ReorderListComponent.DELETE - 1;
    }

    private static int lineY(int shownRows) {
        return LY + shownRows * ROW + 2 + 4;
    }

    private static int height(int shownRows) {
        return lineY(shownRows) + 13;
    }

    private static void state(Stage stage, String name, int shownRows, java.util.function.Consumer<Rig> script) {
        stage.state(name, W, height(shownRows), s -> script.accept(new Rig(s, lineY(shownRows))));
    }

    private static List<String> many(int count) {
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add("Row " + i);
        }
        return out;
    }

    @Override
    public void states(Stage stage) {

        stage.state("items", 180, 100, s -> {
            dev.dryaides.minuit.ReorderListComponent<String> c = s.add(new dev.dryaides.minuit.ReorderListComponent<String>(new Box("r", 6, 6, 168, 92),
                    java.util.List.of("minecraft:diamond", "minecraft:iron_pickaxe", "minecraft:oak_log", "minecraft:redstone", "minecraft:apple"), n -> n.substring(n.indexOf(':') + 1).replace('_', ' ')).iconArt(n -> dev.dryaides.minuit.ItemView.of(n, n.endsWith("log") ? 64 : 1)).deletable(true));
            s.move(80, 30);
        });
        looks(stage);
        drag(stage);
        duplicates(stage);
        locks(stage);
        edges(stage);
        keyboard(stage);
        deleting(stage);
        scrolling(stage);
        tooltips(stage);
        limits(stage);
    }

    private void looks(Stage stage) {
        state(stage, "default", 5, r -> {
            r.list(5, BLOCKS);
        });
        state(stage, "hover row", 5, r -> {
            r.list(5, BLOCKS);
            r.s.move(90, r.mid(2));
        });
        state(stage, "hover handle", 5, r -> {
            r.list(5, BLOCKS);
            r.s.move(r.hx(), r.mid(1));
        });
        state(stage, "hover delete", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            r.s.move(r.dx(), r.mid(1));
        });
        state(stage, "delete buttons at rest", 5, r -> r.list(5, BLOCKS).deletable(true));
        state(stage, "selected and focused", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            r.s.click(90, r.mid(1));
            r.s.move(-100, -100);
        });
        state(stage, "hover over selected", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            r.s.click(90, r.mid(1));
            r.s.move(r.dx(), r.mid(1));
        });
        stage.state("selected, focus elsewhere", W, height(5) + 24, s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, BLOCKS);
            ButtonComponent other = s.add(new ButtonComponent(new Box("b", LX, height(5), 90, 20), "Other", () -> { }));
            s.click(90, r.mid(2));
            s.focus(other);
        });
        stage.state("beside Button and Slider", W, 118, s -> {
            ReorderListComponent<String> c = s.add(new ReorderListComponent<>(new Box("r", LX, LY, LW, 58), BLOCKS.subList(0, 4), n -> n).deletable(true));
            s.add(new ButtonComponent(new Box("b", LX, 68, 90, 20), "Button", () -> { }));
            s.add(new SliderComponent(new Box("v", LX, 94, LW, 16), 0, 100, 40, v -> { }));
            c.select("Dirt");
            s.focus(c);
        });
        state(stage, "tab focus, nothing selected", 5, r -> {
            r.list(5, BLOCKS);
            r.s.key(InputKey.TAB);
        });
        state(stage, "tab focus on selected row", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            r.s.key(InputKey.TAB);
            r.s.key(InputKey.DOWN);
            r.s.key(InputKey.DOWN);
        });
        state(stage, "disabled", 5, r -> {
            r.list(5, BLOCKS).locked("Glass"::equals).deletable(true);
            r.c.select("Dirt");
            r.c.active(false);
            r.lift(1, 28);
            r.drop();
            r.s.click(90, r.mid(2));
            r.s.click(r.dx(), r.mid(2));
            r.s.focus(r.c);
            r.s.key(InputKey.DOWN);
            r.s.key(InputKey.DELETE);
            r.s.key(InputKey.ENTER);
        });
        stage.state("disabled scrolled", W, height(3), s -> {
            Rig r = new Rig(s, lineY(3));
            r.list(3, MANY);
            r.c.select("Oak Planks");
            r.c.active(false);
            s.scroll(90, r.mid(1), -1);
        });
        state(stage, "icons", 5, r -> r.list(5, BLOCKS).icon(n -> n.length() < 5 ? Icon.FOLDER : Icon.CHIP).deletable(true));
        stage.state("custom row", W, 76, s -> {
            ReorderListComponent<String> c = s.add(new ReorderListComponent<>(new Box("r", LX, LY, LW, 66), BLOCKS.subList(0, 4), n -> n));
            c.rowHeight(16).deletable(true).renderer((d, t, n, row, hovered, selected) -> {
                int ink = c.cellInk(t, selected, hovered);
                d.textFit(n, row.x(), row.y() + 4, row.w() - 26, ink);
                d.textFitRight("x" + n.length(), row.right() - 3, row.y() + 4, 22, ink);
            });
            s.click(90, 7 + 16 + 8);
        });
        state(stage, "long text", 3, r -> {
            r.list(3, List.of(LONG, "Short", LONG + " again")).deletable(true);
            r.s.click(90, r.mid(2));
            r.s.move(-100, -100);
        });
        state(stage, "empty", 2, r -> {
            r.list(2, List.of()).emptyText("Nothing to order yet");
            r.s.focus(r.c);
            r.s.key(InputKey.DOWN);
            r.s.key(InputKey.ENTER);
            r.s.key(InputKey.DELETE);
            r.s.click(90, r.mid(0));
        });
        state(stage, "empty, no text", 2, r -> r.list(2, null));
        stage.state("hidden takes no input", W, 40, s -> {
            Rig r = new Rig(s, 16);
            r.list(1, BLOCKS);
            r.c.visible = false;
            s.click(90, r.mid(0));
            r.lift(0, 10);
            r.drop();
            s.scroll(90, r.mid(0), -1);
        });
    }

    private void drag(Stage stage) {
        state(stage, "handle pressed, not moved", 5, r -> {
            r.list(5, BLOCKS);
            r.s.press(r.hx(), r.mid(1));
        });
        state(stage, "lifted, still at home", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(1, 3);
        });
        state(stage, "dragged down, held", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            r.lift(1, 2 * ROW);
        });
        state(stage, "dragged up, held", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(3, -2 * ROW - 4);
        });
        state(stage, "dragged between rows", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(0, ROW + ROW / 2 + 1);
        });
        stage.state("neighbours half way", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, BLOCKS);
            r.lift(0, 2 * ROW);
            s.settle(0);
        });
        state(stage, "neighbours done sliding", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(0, 2 * ROW);
            r.s.waitMs(SLID);
        });
        state(stage, "drop fires once", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(1, 2 * ROW);
            r.drop();
        });
        stage.state("drop half way", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, BLOCKS);
            s.hold(r.hx(), r.mid(1), r.hx(), r.mid(1) + 2 * ROW + 5);
            s.waitMs(SLID);
            r.drop();
            s.settle(0);
        });
        state(stage, "dropped at home, no callback", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(2, 3);
            r.drop();
        });
        state(stage, "there and back, no callback", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(0, 3 * ROW);
            r.s.hold(r.s.mouseX(), r.s.mouseY(), r.hx(), r.mid(0));
            r.s.release(r.hx(), r.mid(0));
        });
        state(stage, "click on handle only selects", 5, r -> {
            r.list(5, BLOCKS);
            r.s.click(r.hx(), r.mid(2));
            r.s.waitMs(100);
            r.s.click(r.hx(), r.mid(2));
        });
        state(stage, "slip of 1 px does not lift", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(1, 1);
            r.drop();
        });
        state(stage, "row body does not drag", 5, r -> {
            r.list(5, BLOCKS);
            r.s.hold(90, r.mid(1), 90, r.mid(4));
            r.s.release(90, r.mid(4));
        });
        state(stage, "right button does nothing", 5, r -> {
            r.list(5, BLOCKS);
            r.s.rightClick(r.hx(), r.mid(1));
            r.lift(0, ROW);
            r.s.rightClick(r.s.mouseX(), r.s.mouseY());
        });
        state(stage, "released outside the list", 5, r -> {
            r.list(5, BLOCKS);
            r.s.hold(r.hx(), r.mid(0), W - 2, r.mid(3));
            r.s.release(W - 2, r.mid(3));
        });
        state(stage, "pointer left the panel", 5, r -> {
            r.list(5, BLOCKS);
            r.s.hold(r.hx(), r.mid(1), r.hx() - 60, height(5) + 40);
        });
        state(stage, "escape cancels", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            r.lift(1, 2 * ROW);
            r.s.key(InputKey.ESCAPE);
            r.drop();
        });
        stage.state("escape half way back", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, BLOCKS);
            r.lift(0, 3 * ROW);
            s.waitMs(SLID);
            s.settle(0);
            s.waitMs(16);
        });
        state(stage, "escape with nothing dragged", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
        });
        state(stage, "escape before the lift", 5, r -> {
            r.list(5, BLOCKS);
            r.s.press(r.hx(), r.mid(1));
            r.s.key(InputKey.ESCAPE);
            r.s.panel().mouseDragged(r.hx(), r.mid(3), 0, 0, 2 * ROW);
            r.drop();
        });
        state(stage, "cancelDrag from code", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(1, ROW);
            r.c.cancelDrag();
        });
        state(stage, "keys wait during a drag", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            r.lift(1, ROW);
            BaseComponent focus = r.s.panel().focus();
            r.s.key(InputKey.TAB);
            r.s.key(InputKey.DELETE);
            r.s.key(InputKey.DOWN);
            r.s.key(InputKey.ENTER);
        });
        state(stage, "reduced motion snaps", 5, r -> {
            r.list(5, BLOCKS);
            BaseComponent.reducedMotion = true;
            r.lift(0, 2 * ROW);
            r.drop();
            BaseComponent.reducedMotion = false;
            r.s.settle(0);
        });
        stage.state("reduced motion, held", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, BLOCKS);
            BaseComponent.reducedMotion = true;
            r.lift(0, 2 * ROW);
            BaseComponent.reducedMotion = false;
            s.settle(0);
        });
        state(stage, "reduced motion, keys and delete", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            BaseComponent.reducedMotion = true;
            r.s.focus(r.c);
            r.c.select("Dirt");
            r.alt(InputKey.DOWN);
            r.s.click(r.dx(), r.mid(0));
            BaseComponent.reducedMotion = false;
            r.s.move(-100, -100);
        });
        state(stage, "duplicates stay apart", 5, r -> {
            r.list(5, List.of("Same", "Same", "Other", "Same", "Last"));
            r.lift(3, -3 * ROW);
            r.drop();
        });
    }

    private void duplicates(Stage stage) {
        state(stage, "twin past twin, no callback", 5, r -> {
            r.list(5, List.of("Same", "Same", "Other", "Last", "End"));
            r.lift(0, ROW);
            r.drop();
            r.s.focus(r.c);
            r.alt(InputKey.UP);
            r.alt(InputKey.DOWN);
            r.alt(InputKey.DOWN);
        });
    }

    private void locks(Stage stage) {
        state(stage, "locked rows", 5, r -> r.list(5, BLOCKS).locked(n -> n.equals("Stone") || n.equals("Glass")).deletable(true));
        state(stage, "locked row selected", 5, r -> {
            r.list(5, BLOCKS).locked("Dirt"::equals);
            r.s.click(r.hx(), r.mid(1));
            r.s.move(-100, -100);
        });
        state(stage, "locked row does not lift", 5, r -> {
            r.list(5, BLOCKS).locked("Dirt"::equals);
            r.lift(1, 2 * ROW);
            r.drop();
        });
        state(stage, "drag passes over a lock, held", 5, r -> {
            r.list(5, BLOCKS).locked("Oak Planks"::equals);
            r.lift(1, 2 * ROW);
        });
        state(stage, "lock keeps its index", 5, r -> {
            r.list(5, BLOCKS).locked("Oak Planks"::equals);
            r.lift(0, 4 * ROW);
            r.drop();
        });
        state(stage, "held right over a lock", 5, r -> {
            r.list(5, BLOCKS).locked("Oak Planks"::equals);
            r.lift(0, 2 * ROW);
        });
        stage.state("pressing a padlock refuses", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, BLOCKS).locked("Dirt"::equals);
            s.press(r.hx(), r.mid(1));
            s.settle(100);
        });
        stage.state("refusal stays under reduced motion", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, BLOCKS).locked("Dirt"::equals);
            BaseComponent.reducedMotion = true;
            s.focus(r.c);
            r.c.select("Dirt");
            r.alt(InputKey.DOWN);
            BaseComponent.reducedMotion = false;
            s.settle(100);
        });
        state(stage, "only one movable row", 5, r -> {
            r.list(5, BLOCKS).locked(n -> !n.equals("Glass"));
            r.lift(3, -ROW);
            r.drop();
        });
        state(stage, "all locked", 5, r -> {
            r.list(5, BLOCKS).locked(n -> true);
            r.s.focus(r.c);
            r.alt(InputKey.DOWN);
        });
    }

    private void edges(Stage stage) {
        stage.state("autoscroll down, held", W, height(4), s -> {
            Rig r = new Rig(s, lineY(4));
            r.list(4, MANY);
            s.hold(r.hx(), r.mid(0), r.hx(), r.mid(3) + 3);
            s.waitMs(300);
            s.settle(0);
        });
        stage.state("autoscroll up, held", W, height(4), s -> {
            Rig r = new Rig(s, lineY(4));
            r.list(4, MANY);
            r.scrollTo(8 * ROW);
            s.hold(r.hx(), r.mid(11), r.hx(), LY + 2);
            s.waitMs(300);
            s.settle(0);
        });
        stage.state("autoscroll reaches the end", W, height(4), s -> {
            Rig r = new Rig(s, lineY(4));
            r.list(4, MANY);
            s.hold(r.hx(), r.mid(0), r.hx(), height(4) + 10);
            s.waitMs(4000);
            r.drop();
        });
        stage.state("grabbed at the edge stays still", W, height(4), s -> {
            Rig r = new Rig(s, lineY(4));
            r.list(4, MANY);
            s.press(r.hx(), r.mid(3));
            s.waitMs(200);
            s.hold(r.hx(), r.mid(3), r.hx(), r.mid(3) - 3);
            s.waitMs(300);
            s.settle(0);
        });
        stage.state("wheel during a drag", W, height(4), s -> {
            Rig r = new Rig(s, lineY(4));
            r.list(4, MANY);
            r.lift(1, 10);
            s.scroll(r.hx(), s.mouseY(), -2);
            s.settle(0);
        });
        stage.state("escape restores the scroll", W, height(4), s -> {
            Rig r = new Rig(s, lineY(4));
            r.list(4, MANY);
            s.hold(r.hx(), r.mid(0), r.hx(), height(4));
            s.waitMs(800);
            s.key(InputKey.ESCAPE);
            r.drop();
        });
        stage.state("drag in 5000 rows", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, many(5000));
            r.scrollTo(2500 * ROW);
            int row = 2502;
            r.lift(row, 2 * ROW);
            s.waitMs(SLID);
            s.settle(0);
        });
        stage.state("hold to the end of 5000 rows", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, many(5000));
            r.scrollTo(4800 * ROW);
            s.hold(r.hx(), r.mid(4801), r.hx(), height(5) + 10);
            s.waitMs(3200);
            r.drop();
        });
    }

    private void keyboard(Stage stage) {
        state(stage, "up, down, home, end", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
            r.s.key(InputKey.DOWN);
            r.s.key(InputKey.UP);
            r.s.key(InputKey.UP);
            r.s.key(InputKey.END);
            r.s.key(InputKey.DOWN);
            r.s.key(InputKey.HOME);
        });
        state(stage, "alt down moves the row", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
            r.c.select("Dirt");
            r.alt(InputKey.DOWN);
        });
        state(stage, "alt up moves the row", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
            r.c.select("Glass");
            r.alt(InputKey.UP);
            r.s.key(InputKey.UP);
        });
        stage.state("alt move half way", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, BLOCKS);
            s.focus(r.c);
            r.c.select("Dirt");
            r.alt(InputKey.DOWN);
            s.waitMs(32);
            s.settle(0);
        });
        state(stage, "right alt works too", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
            r.c.select("Dirt");
            r.raw(RAW_RIGHT_ALT);
            r.s.key(InputKey.DOWN);
        });
        state(stage, "alt then a wait is a plain arrow", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
            r.c.select("Dirt");
            r.raw(RAW_ALT);
            r.s.waitMs(1100);
            r.s.key(InputKey.DOWN);
        });
        state(stage, "alt then another key is a plain arrow", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
            r.c.select("Dirt");
            r.raw(RAW_ALT);
            r.s.key(InputKey.DOWN);
        });
        state(stage, "alt then a click is a plain arrow", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
            r.raw(RAW_ALT);
            r.s.click(90, r.mid(1));
            r.s.key(InputKey.DOWN);
            r.s.panel().rawKey(KeyPress.mouse(0));
        });
        state(stage, "altHeld is exact", 5, r -> {
            boolean[] down = {false};
            r.list(5, BLOCKS).altHeld(() -> down[0]);
            r.s.focus(r.c);
            r.c.select("Dirt");
            r.raw(RAW_ALT);
            r.s.key(InputKey.DOWN);
            down[0] = true;
            r.s.key(InputKey.DOWN);
        });
        state(stage, "ctrl moves the row", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
            r.c.select("Dirt");
            r.s.held(true, false);
            r.s.key(InputKey.DOWN);
            r.s.key(InputKey.DOWN);
            r.s.key(InputKey.UP);
            r.s.held(false, false);
        });
        state(stage, "ctrl end and home", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
            r.c.select("Dirt");
            r.s.held(true, false);
            r.s.key(InputKey.END);
            r.s.key(InputKey.END);
            r.s.key(InputKey.HOME);
            r.s.held(false, false);
        });
        state(stage, "alt at the ends, no callback", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
            r.c.select("Stone");
            r.alt(InputKey.UP);
            r.c.select("Sand");
            r.alt(InputKey.DOWN);
        });
        state(stage, "alt hops over a lock", 5, r -> {
            r.list(5, BLOCKS).locked("Oak Planks"::equals);
            r.s.focus(r.c);
            r.c.select("Dirt");
            r.alt(InputKey.DOWN);
        });
        state(stage, "alt on a locked row refuses", 5, r -> {
            r.list(5, BLOCKS).locked("Dirt"::equals);
            r.s.focus(r.c);
            r.c.select("Dirt");
            r.alt(InputKey.DOWN);
            r.alt(InputKey.UP);
            r.s.settle(100);
        });
        stage.state("alt keeps the row in view", W, height(3), s -> {
            Rig r = new Rig(s, lineY(3));
            r.list(3, MANY);
            s.focus(r.c);
            r.c.select("Oak Planks");
            r.alt(InputKey.DOWN);
            r.alt(InputKey.DOWN);
        });
        state(stage, "enter and space activate", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
            r.s.key(InputKey.DOWN);
            r.s.key(InputKey.ENTER);
            r.s.key(InputKey.SPACE);
        });
        state(stage, "double click activates", 5, r -> {
            r.list(5, BLOCKS);
            r.s.click(90, r.mid(2));
            r.s.waitMs(100);
            r.s.click(90, r.mid(2));
        });
        state(stage, "two slow clicks do not", 5, r -> {
            r.list(5, BLOCKS);
            r.s.click(90, r.mid(2));
            r.s.waitMs(1000);
            r.s.click(90, r.mid(2));
        });
        stage.state("page keys", W, height(4), s -> {
            Rig r = new Rig(s, lineY(4));
            r.list(4, MANY);
            s.focus(r.c);
            s.key(InputKey.HOME);
            r.raw(RAW_PAGE_DOWN);
            r.raw(RAW_PAGE_UP);
        });
        state(stage, "tab leaves the list", 5, r -> {
            r.list(5, BLOCKS);
            r.s.focus(r.c);
            r.s.key(InputKey.TAB);
            r.c.keyPressed(InputKey.COPY, false);
        });
    }

    private void deleting(Stage stage) {
        state(stage, "click delete", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            r.s.click(90, r.mid(2));
            r.s.click(r.dx(), r.mid(1));
            r.s.move(-100, -100);
        });
        stage.state("delete half way", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, BLOCKS).deletable(true);
            s.click(r.dx(), r.mid(1));
            s.settle(0);
            s.move(-100, -100);
        });
        state(stage, "press delete", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            r.s.press(r.dx(), r.mid(1));
        });
        state(stage, "delete released elsewhere", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            r.s.hold(r.dx(), r.mid(1), r.dx(), r.mid(3));
            r.s.release(r.dx(), r.mid(3));
        });
        state(stage, "delete selected row", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            r.s.focus(r.c);
            r.s.key(InputKey.DOWN);
            r.s.key(InputKey.DELETE);
        });
        state(stage, "delete last row goes up", 5, r -> {
            r.list(5, BLOCKS).deletable(true);
            r.s.focus(r.c);
            r.s.key(InputKey.END);
            r.s.key(InputKey.DELETE);
        });
        state(stage, "delete everything", 2, r -> {
            r.list(2, List.of("Only", "Other")).deletable(true).emptyText("All gone");
            r.s.focus(r.c);
            r.s.key(InputKey.HOME);
            r.s.key(InputKey.DELETE);
            r.s.key(InputKey.DELETE);
        });
        state(stage, "locked rows keep no button", 5, r -> {
            r.list(5, BLOCKS).locked("Dirt"::equals).deletable(true);
            r.s.click(r.dx(), r.mid(1));
            r.s.key(InputKey.DELETE);
            r.s.move(-100, -100);
        });
        state(stage, "per row delete", 5, r -> {
            r.list(5, BLOCKS).deletable(n -> n.startsWith("S"));
            r.s.click(r.dx(), r.mid(1));
            r.s.click(r.dx(), r.mid(0));
            r.s.move(-100, -100);
        });
        stage.state("delete in a scrolled list", W, height(3), s -> {
            Rig r = new Rig(s, lineY(3));
            r.list(3, List.of("A", "B", "C", "D", "E")).deletable(true);
            r.scrollTo(2 * ROW);
            s.click(r.dx(), r.mid(4));
            s.move(-100, -100);
        });
    }

    private void scrolling(Stage stage) {
        stage.state("overflow", W, height(4), s -> {
            Rig r = new Rig(s, lineY(4));
            r.list(4, MANY).deletable(true);
        });
        stage.state("wheel", W, height(4), s -> {
            Rig r = new Rig(s, lineY(4));
            r.list(4, MANY);
            int over = LY + 20;
            s.scroll(90, over, -1);
            s.scroll(90, over, 5);
            s.scroll(90, over, -50);
        });
        stage.state("wheel with room to spare", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, BLOCKS);
            s.scroll(90, r.mid(1), -1);
        });
        stage.state("half a row scrolled", W, height(4), s -> {
            Rig r = new Rig(s, lineY(4));
            r.list(4, MANY).deletable(true);
            r.scrollTo(ROW + 5);
        });
        stage.state("scroll bar dragged", W, height(4), s -> {
            Rig r = new Rig(s, lineY(4));
            r.list(4, MANY);
            s.hold(LX + LW - 4, LY + 4, LX + LW - 4, LY + 4 * ROW);
        });
        stage.state("scroll bar hover", W, height(4), s -> {
            Rig r = new Rig(s, lineY(4));
            r.list(4, MANY);
            s.move(LX + LW - 4, LY + 8);
        });
        stage.state("draws only visible rows", W, height(5), s -> {
            int[] calls = new int[1];
            Rig r = new Rig(s, lineY(5));
            s.paint((d, t) -> calls[0] = 0);
            ReorderListComponent<Integer> c = s.add(new ReorderListComponent<Integer>(new Box("r", LX, LY, LW, 5 * ROW + 2),
                    numbers(5000), n -> "row " + n));
            c.renderer((d, t, n, row, hovered, selected) -> {
                calls[0]++;
                d.textFit("row " + n, row.x(), row.y() + 3, row.w(), c.cellInk(t, selected, hovered));
            });
            c.setScroll(2500 * ROW);
            s.move(-100, -100);
        });
        stage.state("100k rows, end of the list", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            ReorderListComponent<Integer> c = s.add(new ReorderListComponent<Integer>(new Box("r", LX, LY, LW, 5 * ROW + 2),
                    numbers(100_000), n -> "row " + n));
            s.focus(c);
            s.key(InputKey.END);
        });
    }

    private void tooltips(Stage stage) {
        stage.state("row tooltip", W, 90, s -> {
            ReorderListComponent<String> c = s.add(new ReorderListComponent<>(new Box("r", LX, 40, LW, 44), BLOCKS, n -> n)
                    .rowTooltip(n -> List.of(n, "Row tooltip")));
            s.move(70, 47);
            s.settle(1500);
        });
        stage.state("delete tooltip", W, 90, s -> {
            ReorderListComponent<String> c = s.add(new ReorderListComponent<>(new Box("r", LX, 40, LW, 44), BLOCKS, n -> n)
                    .deletable(true).deleteTooltip("Remove from the list")
                    .rowTooltip(n -> List.of(n)));
            int dx = deleteX(c) + 5;
            s.move(dx, 47);
            s.settle(1500);
        });
        stage.state("locked tooltip", W, 90, s -> {
            ReorderListComponent<String> c = s.add(new ReorderListComponent<>(new Box("r", LX, 40, LW, 44), BLOCKS, n -> n)
                    .locked("Stone"::equals).lockedTooltip("This row stays first"));
            int hx = handleX(c) + 5;
            s.move(hx, 47);
            s.settle(1500);
        });
        stage.state("cut text tooltip", 290, 84, s -> {
            ReorderListComponent<String> c = s.add(new ReorderListComponent<>(new Box("r", LX, 42, 138, 30), List.of(LONG, "Short"), n -> n));
            s.move(70, 49);
            s.settle(1500);
        });
        stage.state("no tooltip during a drag", W, 90, s -> {
            ReorderListComponent<String> c = s.add(new ReorderListComponent<>(new Box("r", LX, 40, LW, 44), BLOCKS, n -> n)
                    .rowTooltip(n -> List.of(n)));
            int hx = handleX(c) + 5;
            s.hold(hx, 47, hx, 55);
            s.waitMs(800);
            s.settle(0);
        });
        stage.state("disabled shows the list's tooltip", W, 90, s -> {
            ReorderListComponent<String> c = s.add(new ReorderListComponent<>(new Box("r", LX, 40, LW, 44), BLOCKS, n -> n)
                    .rowTooltip(n -> List.of(n)).tooltip("About this list"));
            c.active(false);
            s.move(70, 47);
            s.settle(1500);
        });
    }

    private void limits(Stage stage) {
        stage.state("tiny box", 80, 30, s -> {
            ReorderListComponent<String> c = s.add(new ReorderListComponent<>(new Box("r", 4, 4, 34, 18), BLOCKS, n -> n).deletable(true));
            s.add(new ReorderListComponent<>(new Box("r2", 44, 4, 10, 8), BLOCKS, n -> n).deletable(true));
            s.add(new ReorderListComponent<>(new Box("r3", 60, 4, 2, 2), BLOCKS, n -> n));
            s.hold(handleX(c) + 5, 9, handleX(c) + 5, 16);
            s.release(handleX(c) + 5, 16);
            s.move(-100, -100);
        });
        stage.state("narrow drops the delete button", 110, height(3), s -> {
            ReorderListComponent<String> c = s.add(new ReorderListComponent<>(new Box("r", 4, LY, 30, 3 * ROW + 2), BLOCKS, n -> n)
                    .deletable(true));
            ReorderListComponent<String> wider = s.add(new ReorderListComponent<>(new Box("w", 40, LY, 64, 3 * ROW + 2), BLOCKS, n -> n)
                    .deletable(true));
            s.click(deleteX(c) + 5, c.y + 1 + 7);
            s.click(deleteX(wider) + 5, wider.y + 1 + 7);
            s.move(-100, -100);
        });
        stage.state("row taller than the box", W, 24, s -> {
            ReorderListComponent<String> c = s.add(new ReorderListComponent<>(new Box("r", LX, 4, LW, 10), BLOCKS, n -> n));
            s.focus(c);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.hold(handleX(c) + 5, 8, handleX(c) + 5, 14);
            s.release(handleX(c) + 5, 14);
            s.move(-100, -100);
        });
        state(stage, "setItems keeps the selection", 5, r -> {
            r.list(5, BLOCKS);
            r.c.select("Glass");
            r.c.setItems(List.of("Glass", "Stone", "New"));
            r.c.setItems(List.of("Stone", "New"));
        });
        state(stage, "setItems ends a drag", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(1, 2 * ROW);
            r.c.setItems(List.of("One", "Two"));
            r.drop();
        });
        state(stage, "move from code", 5, r -> {
            r.list(5, BLOCKS).locked("Glass"::equals);
            r.c.move(0, 2);
            r.c.move(0, 3);
            r.c.move(3, 0);
            r.c.move(1, 1);
            r.c.move(0, 9);
        });
        state(stage, "disabled during a drag", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(1, 2 * ROW);
            r.c.active(false);
            r.drop();
        });
        state(stage, "active field off during a drag", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(1, 2 * ROW);
            r.c.active = false;
            r.s.waitMs(60);
            r.drop();
        });
        state(stage, "hidden during a drag", 5, r -> {
            r.list(5, BLOCKS);
            r.lift(1, 2 * ROW);
            r.c.visible = false;
            r.s.waitMs(60);
            r.drop();
            r.c.visible = true;
        });
        stage.state("inside a scrolled ScrollComponent", W, 100, s -> {
            List<String> events = new ArrayList<>();
            ScrollComponent scroll = s.add(new ScrollComponent(new Box("s", LX, LY, LW, 86)));
            scroll.add(new ButtonComponent(new Box("above", LX + 4, LY + 4, 90, 20), "Above", () -> { }));
            ReorderListComponent<String> c = scroll.add(new ReorderListComponent<>(new Box("r", LX + 4, LY + 28, LW - 16, 72), BLOCKS, n -> n)
                    .onReorder((order, moved, from, to) -> events.add(moved + ":" + from + ">" + to)));
            scroll.add(new ButtonComponent(new Box("below", LX + 4, LY + 104, 90, 20), "Below", () -> { }));
            scroll.setScroll(20);
            s.waitMs(16);
            int hx = handleX(c) + 5;
            int y0 = LY + 28 - 20 + 1 + 7;
            s.hold(hx, y0, hx, y0 + 2 * ROW);
            s.release(hx, y0 + 2 * ROW);
        });
        stage.state("glass shows nothing through the float", W, height(5), s -> {
            Rig r = new Rig(s, lineY(5));
            r.list(5, List.of("Row with text behind it", "Lifted", "More text on this row", "Text again", "Last"));
            r.lift(1, ROW + ROW / 2 - 1);
            s.waitMs(SLID);
            s.settle(0);
        });
    }

    private static List<Integer> numbers(int count) {
        List<Integer> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(i);
        }
        return out;
    }
}
