package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.ComboBoxComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.TextFieldComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The combo box, driven by scripted input.
 */
public final class ComboBoxDemo implements Demo {
    private static final int W = 200;
    private static final int H = 156;
    private static final int FX = 8;
    private static final int FY = 8;
    private static final int FW = 150;
    private static final int FH = 16;
    /** Where the button behind the list sits: wholly covered by a list of 3 rows or more, clear of one of 1. */
    private static final int BEHIND_Y = 46;
    private static final int OPENED = 150;
    private static final int ROW = ComboBoxComponent.ROW;
    private static final List<String> BLOCKS = List.of("Stone", "Cobblestone", "Sandstone", "Stone Bricks", "Dirt",
            "Diamond Ore", "Diamond Block", "Oak Planks", "Spruce Planks", "Birch Planks", "Glass", "Tinted Glass");
    private static final String LONG = "A very long option whose name cannot possibly fit in the list at all";

    /** A scene with widgets the list can cover, the combo box and the line of values beneath. */
    private static final class Rig {
        final Scene s;
        final ButtonComponent button;
        final List<String> events = new ArrayList<>();
        int behind;
        String note = "";

        Rig(Scene s) {
            this(s, BEHIND_Y);
        }

        Rig(Scene s, int buttonY) {
            this.s = s;
            s.caption(() -> note.isEmpty() ? String.join(" ", events) : note);
            button = s.add(new ButtonComponent(new Box("behind", FX, buttonY, 90, 20), "Behind", () -> behind++));
        }

        ComboBoxComponent<String> combo() {
            return combo(new Box("c", FX, FY, FW, FH), BLOCKS);
        }

        /** Added first, with its popup last: the normal way. */
        ComboBoxComponent<String> combo(Box box, List<String> options) {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(box, s.panel(), options, o -> o)
                    .placeholder("Pick a block")
                    .emptyText("Nothing matches")
                    .onSelect(v -> events.add("sel:" + (v == null ? "none" : v)))
                    .onCommit(v -> events.add("commit:" + v)));
            return c;
        }

        ComboBoxComponent<String> placed(ComboBoxComponent<String> c) {
            return c;
        }

        /** Types into a focused combo box and lets the list finish opening. */
        void typed(ComboBoxComponent<String> c, String text) {
            s.focus(c);
            s.type(text);
            s.waitMs(OPENED);
        }

        void clickRow(int row) {
            s.click(rowX(s) + 10, rowY(s, row));
        }
    }

    /** The list's frame inset, from the theme's corner, 3 to 4 pixels. */
    private static int inset(Scene s) {
        return Math.max(3, Math.min(4, s.theme().frame().corner()));
    }

    /** The list opens 2 pixels under the field at FY. */
    private static int listTop() {
        return FY + FH + 2;
    }

    private static int rowX(Scene s) {
        return FX + inset(s);
    }

    /** A row's line in the list under the field at FY, with no scroll. */
    private static int rowY(Scene s, int row) {
        return listTop() + inset(s) + row * ROW + 4;
    }

    /** The list's height for a number of rows. */
    private static int listH(Scene s, int rows) {
        return rows * ROW + 2 * inset(s);
    }

    private static List<String> many(int count) {
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add("Option " + i);
        }
        return out;
    }

    @Override
    public void states(Stage stage) {

        stage.state("items", 200, 150, s -> {
            dev.dryaides.minuit.ComboBoxComponent<String> c = s.add(new dev.dryaides.minuit.ComboBoxComponent<String>(new Box("c", 8, 8, 150, 18), s.panel(),
                    java.util.List.of("minecraft:diamond", "minecraft:iron_pickaxe", "minecraft:oak_log", "minecraft:redstone", "minecraft:apple"), Mc::name).iconArt(o -> dev.dryaides.minuit.ItemView.of(o)));
            c.select("minecraft:diamond");
            c.open();
            s.keepHeight();
            s.waitMs(300);
        });
        stage.state("items field", 200, 40, s -> {
            dev.dryaides.minuit.ComboBoxComponent<String> c = s.add(new dev.dryaides.minuit.ComboBoxComponent<String>(new Box("c", 8, 8, 150, 18), s.panel(),
                    java.util.List.of("minecraft:diamond", "minecraft:iron_pickaxe", "minecraft:oak_log", "minecraft:redstone", "minecraft:apple"), o -> "").iconArt(o -> dev.dryaides.minuit.ItemView.of(o)));
            c.select("minecraft:iron_pickaxe");
        });
        looks(stage);
        filtering(stage);
        keyboard(stage);
        mouse(stage);
        value(stage);
        placing(stage);
        edges(stage);
    }

    private void looks(Stage stage) {
        stage.state("default", W, 50, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, FY, FW, FH), s.panel(), BLOCKS, o -> o)
                    .placeholder("Pick a block"));
        });

        stage.state("beside button and slider", W, 80, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, 8, 110, 16), s.panel(), BLOCKS, o -> o));
            c.select("Stone");
            s.add(new ButtonComponent(new Box("b", FX, 30, 110, 16), "Button", () -> { }));
            s.add(new SliderComponent(new Box("s", FX, 52, 110, 16), 0, 100, 40, v -> { }));
            s.add(new TextFieldComponent(new Box("t", 124, 8, 68, 16), "Field", v -> { }));
        });

        stage.state("with a value", W, 50, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, FY, FW, FH), s.panel(), BLOCKS, o -> o));
            c.select("Oak Planks");
        });

        stage.state("focused by click", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
        });

        stage.state("focused by tab shows the ring", W, 50, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, FY, FW, FH), s.panel(), BLOCKS, o -> o)
                    .placeholder("Pick a block"));
            s.key(InputKey.TAB);
        });

        stage.state("ring beside an open list", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.waitMs(OPENED);
        });

        stage.state("hover arrow", W, 50, s -> {
            s.add(new ComboBoxComponent<>(new Box("c", FX, FY, FW, FH), s.panel(), BLOCKS, o -> o));
            s.move(FX + FW - 6, FY + 8);
        });

        stage.state("hover clear", W, 50, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, FY, FW, FH), s.panel(), BLOCKS, o -> o));
            c.select("Stone");
            s.move(FX + FW - 18, FY + 8);
        });

        stage.state("arrow held", W, 50, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, FY, FW, FH), s.panel(), BLOCKS, o -> o));
            s.press(FX + FW - 6, FY + 8);
            s.settle(0);
            c.close();
        });

        stage.state("hover row quieter than the lit row", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "st");
            s.move(rowX(s) + 30, rowY(s, 2));
            s.waitMs(OPENED);
        });

        stage.state("value in the list", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            c.select("Dirt");
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.key(InputKey.DOWN);
        });

        stage.state("value without focus in the list", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            c.select("Sandstone");
            c.open();
            s.waitMs(OPENED);
            s.panel().focus(null);
            // Without focus the list closes on the next frame; the value is still there.
            s.waitMs(20);
        });

        stage.state("disabled", W, 70, s -> {
            List<String> log = new ArrayList<>();
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, 8, FW, FH), s.panel(), BLOCKS, o -> o)
                    .onSelect(v -> log.add("sel")));
            c.select("Stone");
            c.active(false);
            ComboBoxComponent<String> e = s.add(new ComboBoxComponent<>(new Box("e", FX, 30, FW, FH), s.panel(), BLOCKS, o -> o)
                    .placeholder("Nothing to pick"));
            e.active(false);
            s.click(FX + 20, 14);
            s.focus(c);
            s.key(InputKey.DOWN);
            s.type("x");
            s.key(InputKey.ENTER);
            s.scroll(FX + 20, 14, -1);
            s.panel().focus(null);
            s.paint((d, t) -> d.textFit("ignored, text=" + c.text(), 6, 52, W - 12, t.palette().text()));
        });

        stage.state("disabled beside a text field", W, 80, s -> {
            ComboBoxComponent<String> on = s.add(new ComboBoxComponent<>(new Box("c", FX, 8, 90, FH), s.panel(), BLOCKS, o -> o));
            on.select("Stone");
            s.add(new TextFieldComponent(new Box("t", 104, 8, 88, FH), "Stone", v -> { }));
            ComboBoxComponent<String> off = s.add(new ComboBoxComponent<>(new Box("o", FX, 30, 90, FH), s.panel(), BLOCKS, o -> o));
            off.select("Stone");
            off.active(false);
            TextFieldComponent field = s.add(new TextFieldComponent(new Box("f", 104, 30, 88, FH), "Stone", v -> { }));
            field.active = false;
            ButtonComponent button = s.add(new ButtonComponent(new Box("b", FX, 52, 90, FH), "Button", () -> { }));
            button.active = false;
        });

        stage.state("disabling an open list closes it", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "st");
            c.active(false);
            s.waitMs(20);
        });
    }

    private void filtering(Stage stage) {
        stage.state("typing prefix first", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "st");
        });

        stage.state("fuzzy after prefix", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "dmb");
        });

        stage.state("tightest fuzzy first", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "pl");
        });

        stage.state("fuzzy off", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo().fuzzy(false));
            r.typed(c, "dmb");
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.BACKSPACE);
            s.type("one");
            s.waitMs(OPENED);
        });

        stage.state("accents and case", W, H, s -> {
            Rig r = new Rig(s);
            List<String> names = List.of("Éditer", "Effacer", "Noël", "ÉLAN");
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, FY, FW, FH), names));
            r.typed(c, "E");
            s.key(InputKey.BACKSPACE);
            s.type("noel");
            s.waitMs(OPENED);
        });

        stage.state("empty result line", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "zzz");
            s.key(InputKey.ENTER);
            s.key(InputKey.DOWN);
        });

        stage.state("backspace widens again", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "zz");
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.BACKSPACE);
            s.waitMs(OPENED);
        });

        stage.state("no data", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, FY, FW, FH), List.of()));
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.key(InputKey.DOWN);
            s.key(InputKey.TAB);
        });

        stage.state("10000 options draws only its rows", W, H, s -> {
            Rig r = new Rig(s);
            List<String> options = many(10_000);
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, FY, FW, FH), options));
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.scroll(FX + 20, listTop() + 20, -100_000);
        });

        stage.state("10000 options filtered", W, H, s -> {
            Rig r = new Rig(s);
            List<String> options = many(10_000);
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, FY, FW, FH), options));
            r.typed(c, "n 99");
            // Letters in a row first, in list order: "Option 99", then 990 to 999, then 9900 to 9999.
        });
    }

    private void keyboard(Stage stage) {
        stage.state("down up wrap", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.focus(c);
            s.key(InputKey.UP);
            s.key(InputKey.DOWN);
            s.key(InputKey.UP);
            s.waitMs(OPENED);
        });

        stage.state("keys keep the row in view", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.focus(c);
            s.key(InputKey.DOWN);
            for (int i = 0; i < 9; i++) {
                s.key(InputKey.DOWN);
            }
            s.waitMs(OPENED);
        });

        stage.state("enter chooses", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "dia");
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
        });

        stage.state("tab completes then moves on", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.key(InputKey.TAB);
            r.typed(c, "oak");
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            s.waitMs(20);
        });

        stage.state("tab completes the lit row", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "st");
            s.key(InputKey.DOWN);
            s.key(InputKey.TAB);
        });

        stage.state("shift tab goes back without completing", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.key(InputKey.TAB);
            r.typed(c, "oak");
            s.key(InputKey.TAB, true);
            s.waitMs(20);
        });

        stage.state("escape closes then lets go", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "oak");
            s.key(InputKey.ESCAPE);
            s.key(InputKey.ESCAPE);
            s.waitMs(20);
        });

        stage.state("left right edit in the middle", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "sone");
            s.key(InputKey.HOME);
            s.key(InputKey.RIGHT);
            s.type("t");
            s.waitMs(OPENED);
            s.key(InputKey.SELECT_ALL);
            s.key(InputKey.BACKSPACE);
        });

        stage.state("selection in the field", W, 50, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, FY, FW, FH), s.panel(), BLOCKS, o -> o));
            s.focus(c);
            s.type("stone");
            s.key(InputKey.ESCAPE);
            s.key(InputKey.HOME);
            s.key(InputKey.END, true);
        });

        stage.state("space is typed not pressed", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "stone");
            s.key(InputKey.SPACE);
            s.type(" b");
            s.waitMs(OPENED);
        });

        stage.state("keys go to the combo not behind", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
        });
    }

    private void mouse(Stage stage) {
        stage.state("click row chooses", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            r.clickRow(4);
        });

        stage.state("click row over a widget", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.click(FX + 20, BEHIND_Y + 6);
        });

        stage.state("click outside only closes", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.click(FX + FW + 10, 60);
            s.click(FX + 20, BEHIND_Y + 6);
        });

        stage.state("arrow toggles", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            int ax = FX + FW - 6;
            s.click(ax, FY + 8);
            s.waitMs(OPENED);
            s.click(ax, FY + 8);
            s.click(ax, FY + 8);
            s.waitMs(OPENED);
        });

        stage.state("clear button", W, H, s -> {
            Rig r = new Rig(s);
            List<String> texts = new ArrayList<>();
            ComboBoxComponent<String> c = r.placed(r.combo().onText(texts::add));
            c.select("Stone");
            s.click(FX + FW - 18, FY + 8);
            s.move(100, 100);
        });

        stage.state("no clear button", W, 50, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, FY, FW, FH), s.panel(), BLOCKS, o -> o).clearable(false));
            c.select("Stone");
            s.click(FX + FW - 18, FY + 8);
            c.close();
        });

        stage.state("click in the field keeps the list open", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "stone");
            s.click(FX + 12, FY + 6);
            s.type("x");
            s.waitMs(OPENED);
        });

        stage.state("drag selects in the field", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "stone bricks");
            s.key(InputKey.ESCAPE);
            s.drag(FX + 12, FY + 8, FX + 40, FY + 8);
            s.waitMs(OPENED);
        });

        stage.state("wheel scrolls", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.scroll(FX + 10, listTop() + 10, -1);
            s.scroll(FX + 10, listTop() + 10, 1);
            s.scroll(FX + 10, listTop() + 10, 50);
            s.scroll(FX + 10, listTop() + 10, -50);
        });

        stage.state("wheel outside does nothing beneath", W, H, s -> {
            Rig r = new Rig(s);
            List<String> values = new ArrayList<>();
            s.add(new SliderComponent(new Box("s", 116, 8, 76, 16), 0, 100, 40, v -> values.add("v" + v)));
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, FY, 100, FH), BLOCKS));
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.scroll(150, 16, -1);
        });

        stage.state("wheel closed is not taken", W, 50, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, FY, FW, FH), s.panel(), BLOCKS, o -> o));
            c.mouseScrolled(FX + 20, FY + 6, -1);
        });

        stage.state("bar dragged", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, FY, FW, FH), many(500)));
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            int bx = FX + FW - 6;
            s.hold(bx, listTop() + 6, bx, listTop() + listH(s, 8) + 40);
        });

        stage.state("right click on a row picks nothing", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.rightClick(rowX(s) + 10, rowY(s, 1));
        });

        stage.state("list covers the next combo", W, H, s -> {
            Rig r = new Rig(s, 112);
            ComboBoxComponent<String> a = r.combo(new Box("a", FX, FY, FW, FH), BLOCKS).maxRows(5);
            ComboBoxComponent<String> b = r.combo(new Box("b", FX, FY + 22, FW, FH), BLOCKS);
            b.select("Stone Bricks");
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
        });

        stage.state("two combos over each other", W, H, s -> {
            Rig r = new Rig(s, 110);
            ComboBoxComponent<String> a = r.combo(new Box("a", FX, FY, FW, FH), BLOCKS);
            ComboBoxComponent<String> b = r.combo(new Box("b", FX, FY + 22, FW, FH), BLOCKS).maxRows(7); // clear of the line of values
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.click(rowX(s) + 10, FY + 26);
            s.click(FX + 20, FY + 22 + 6);
            s.waitMs(OPENED);
        });
    }

    private void value(Stage stage) {
        stage.state("strict goes back to the value", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            c.select("Dirt");
            s.focus(c);
            s.key(InputKey.SELECT_ALL);
            s.type("zzz");
            s.panel().focus(null);
            s.waitMs(20);
        });

        stage.state("emptied then enter clears", W, H, s -> {
            Rig r = new Rig(s);
            List<String> texts = new ArrayList<>();
            ComboBoxComponent<String> c = r.placed(r.combo().onText(texts::add));
            c.select("Dirt");
            s.focus(c);
            s.key(InputKey.SELECT_ALL);
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.ENTER);
            s.type("gl");
            s.key(InputKey.ENTER);
        });

        stage.state("strict takes an exact label", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.focus(c);
            s.type("STONE bricks");
            s.panel().focus(null);
            s.waitMs(20);
        });

        stage.state("strict emptied clears", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            c.select("Dirt");
            s.focus(c);
            s.key(InputKey.SELECT_ALL);
            s.key(InputKey.BACKSPACE);
            s.panel().focus(null);
            s.waitMs(20);
        });

        stage.state("free text kept", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo().freeText(true));
            r.typed(c, "my block");
            s.key(InputKey.ENTER);
        });

        stage.state("free text with suggestions", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo().freeText(true));
            r.typed(c, "st");
            s.key(InputKey.DOWN);
            s.key(InputKey.UP);
        });

        stage.state("free text on leaving", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo().freeText(true));
            s.focus(c);
            s.type("dirt");
            s.panel().focus(null);
            s.waitMs(20);
        });

        stage.state("select calls nothing", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            c.select("Glass");
            c.select(null);
            c.setText("abc");
        });

        stage.state("value kept across setOptions", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            c.select("Dirt");
            c.setOptions(List.of("Dirt", "Grass"));
            c.setOptions(List.of("Grass"));
            c.setOptions(null);
        });

        stage.state("max length", W, 50, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, FY, FW, FH), s.panel(), BLOCKS, o -> o).maxLength(4));
            s.focus(c);
            s.type("stone");
            s.key(InputKey.ESCAPE);
        });
    }

    private void placing(Stage stage) {
        stage.state("flips above", W, H, s -> {
            Rig r = new Rig(s, 8);
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, 110, FW, FH), BLOCKS));
            s.click(FX + 20, 116);
            s.waitMs(OPENED);
        });

        stage.state("short list stays below when it fits", W, H, s -> {
            Rig r = new Rig(s, 8);
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, 60, FW, FH), List.of("One", "Two", "Three")));
            s.click(FX + 20, 66);
            s.waitMs(OPENED);
        });

        stage.state("keeps its side as it narrows", W, H, s -> {
            Rig r = new Rig(s, 8);
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, 90, FW, FH), BLOCKS));
            s.click(FX + 20, 96);
            s.waitMs(OPENED);
            s.type("dirt");
            s.waitMs(OPENED);
        });

        stage.state("too tall for both sides", W, 80, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, 30, FW, FH), s.panel(), BLOCKS, o -> o));
            s.click(FX + 20, 36);
            s.waitMs(OPENED);
            s.settle(0);
        });

        stage.state("wider than the panel side", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", 130, FY, 60, FH), BLOCKS).popupWidth(140));
            s.click(136, FY + 6);
            s.waitMs(OPENED);
        });

        stage.state("area box limits it", W, H, s -> {
            Rig r = new Rig(s);
            Box area = new Box("area", 0, 0, W, 90);
            ComboBoxComponent<String> c = r.placed(r.combo().area(area));
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
        });

        stage.state("max rows", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo().maxRows(4));
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
        });

        stage.state("list opening half way", W, H, s -> {
            Rig r = new Rig(s);
            r.placed(r.combo());
            s.click(FX + 20, FY + 6);
            s.waitMs(48);
            s.settle(0);
        });

        stage.state("list opening above half way", W, H, s -> {
            Rig r = new Rig(s, 8);
            r.placed(r.combo(new Box("c", FX, 110, FW, FH), BLOCKS));
            s.click(FX + 20, 116);
            s.waitMs(48);
            s.settle(0);
        });

        stage.state("reduced motion snaps", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            HostState.reducedMotion(true);
            try {
                s.click(FX + 20, FY + 6);
            } finally {
                HostState.reducedMotion(false);
            }
            s.settle(0);
        });

        stage.state("drawing itself when added last", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.combo();
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.scroll(FX + 20, listTop() + 10, -1);
            r.clickRow(3);
        });
    }

    private void edges(Stage stage) {
        stage.state("hidden takes no input", W, 50, s -> {
            List<String> log = new ArrayList<>();
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, FY, FW, FH), s.panel(), BLOCKS, o -> o)
                    .onSelect(v -> log.add("sel")));
            c.visible = false;
            s.click(FX + 20, FY + 6);
            c.open();
            s.paint((d, t) -> d.textFit("hidden, nothing drawn", 6, 20, W - 12, t.palette().text()));
        });

        stage.state("hiding an open list closes it", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo());
            r.typed(c, "st");
            c.visible = false;
            s.waitMs(60);
        });

        stage.state("tiny box", W, 70, s -> {
            ComboBoxComponent<String> small = s.add(new ComboBoxComponent<>(new Box("c", FX, 8, 30, 10), s.panel(), BLOCKS, o -> o));
            small.select("Stone");
            ComboBoxComponent<String> thin = s.add(new ComboBoxComponent<>(new Box("t", 50, 8, 60, 6), s.panel(), BLOCKS, o -> o));
            thin.select("Stone");
            ComboBoxComponent<String> noBar = s.add(new ComboBoxComponent<>(new Box("n", FX, 26, 45, 12), s.panel(), BLOCKS, o -> o));
            noBar.select("Stone");
            s.focus(small);
            s.key(InputKey.DOWN);
            s.waitMs(OPENED);
        });

        stage.state("zero size", W, 50, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, FY, 0, 0), s.panel(), BLOCKS, o -> o));
            c.select("Stone");
        });

        stage.state("long text at rest", W, 70, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, 34, FW, FH), s.panel(), List.of(LONG), o -> o));
            c.select(LONG);
            s.move(FX + 30, 40);
            s.settle(1500);
        });

        stage.state("long text typed", W, 50, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, FY, FW, FH), s.panel(), List.of(LONG), o -> o).clearable(true));
            s.focus(c);
            s.type(LONG.substring(0, 50));
            s.key(InputKey.ESCAPE);
        });

        stage.state("long rows cut with marks", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, FY, FW, FH), List.of(LONG, "A second long option that goes on and on and on", "Short")));
            r.typed(c, "long");
            s.key(InputKey.ENTER);
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
        });

        stage.state("match past the cut", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, FY, FW, FH), List.of(LONG)));
            r.typed(c, "at all");
        });

        stage.state("row tooltip for a cut label", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, FY, FW, FH), List.of(LONG, "Short")));
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.move(rowX(s) + 20, rowY(s, 0));
            s.settle(1500);
        });

        stage.state("custom row tooltip", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo().rowTooltip(o -> List.of(o, "A block")));
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.move(rowX(s) + 20, rowY(s, 1));
            s.settle(1500);
        });

        stage.state("field tooltip", W, 70, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, 34, FW, FH), s.panel(), BLOCKS, o -> o)
                    .tooltip("Which block to use"));
            s.move(FX + 30, 40);
            s.settle(1500);
        });

        stage.state("clear tooltip", W, 70, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, 34, FW, FH), s.panel(), BLOCKS, o -> o)
                    .clearTooltip("Empty the field"));
            c.select("Stone");
            s.move(FX + FW - 18, 40);
            s.settle(1500);
        });

        stage.state("tooltip beneath the list stays quiet", W, H, s -> {
            Rig r = new Rig(s);
            r.button.tooltip("Must not show");
            ComboBoxComponent<String> c = r.placed(r.combo());
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
            s.move(FX + 40, BEHIND_Y + 6);
            s.settle(1500);
        });

        stage.state("tall field", W, 60, s -> {
            ComboBoxComponent<String> c = s.add(new ComboBoxComponent<>(new Box("c", FX, 8, FW, 30), s.panel(), BLOCKS, o -> o));
            c.select("Stone");
            s.focus(c);
            s.key(InputKey.END);
        });

        stage.state("twenty first of a hundred", W, H, s -> {
            Rig r = new Rig(s);
            ComboBoxComponent<String> c = r.placed(r.combo(new Box("c", FX, FY, FW, FH), many(100)));
            c.select("Option 20");
            s.click(FX + 20, FY + 6);
            s.waitMs(OPENED);
        });
    }
}
