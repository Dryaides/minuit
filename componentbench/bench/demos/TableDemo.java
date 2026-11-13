package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.TableComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * The data table, driven by scripted input; the line under each table shows the values.
 */
public final class TableDemo implements Demo {
    private record Pet(String name, String home, int level, float mood) { }

    private static final int W = 300;
    private static final int H = 100;
    private static final int TABLE_X = 8;
    private static final int TABLE_Y = 6;
    private static final int TABLE_W = 284;
    private static final int TABLE_H = 76;
    private static final int ROW = 14;
    private static final int READOUT_Y = 87;
    /** The y of the middle of the header, and of the first row. */
    private static final int HEADER_MID = TABLE_Y + 1 + 7;
    private static final int ROW0_MID = TABLE_Y + 1 + 14 + 7;
    /** The x inside the columns: Name, Home and Level. */
    private static final int NAME_X = 30;
    private static final int LEVEL_X = 205;

    private static final List<Pet> PETS = List.of(
            new Pet("Biscuit", "Taiga", 3, 0.8F),
            new Pet("Mochi", "Plains", 7, 0.35F),
            new Pet("Ash", "Nether", 3, 0.1F),
            new Pet("Clover", "Meadow", 5, 0.95F),
            new Pet("Dune", "Desert", 3, 0.5F),
            new Pet("Echo", "Deep dark", 9, 0.2F),
            new Pet("Fern", "Jungle", 5, 0.6F),
            new Pet("Gale", "Peaks", 1, 0.7F));

    private static TableComponent<Pet> table() {
        TableComponent<Pet>[] self = holder();
        self[0] = new TableComponent<Pet>(new Box("pets", TABLE_X, TABLE_Y, TABLE_W, TABLE_H), ROW)
                .column("Name", c -> c.text(Pet::name).weight(2F)
                        .comparator(Comparator.comparing(Pet::name, String.CASE_INSENSITIVE_ORDER)))
                .column("Home", c -> c.text(Pet::home).weight(2F))
                .column("Level", c -> c.text(p -> Integer.toString(p.level())).fixed(50)
                        .align(TableComponent.Align.RIGHT).comparator(Comparator.comparingInt(Pet::level)))
                .column("Mood", c -> c.fixed(60).renderer((d, theme, pet, cell, hovered, selected) -> {
                    int left = cell.x() + 3;
                    int right = cell.right() - 3;
                    int top = cell.y() + 4;
                    int bottom = cell.bottom() - 4;
                    int ink;
                    if (selected || !self[0].active) {
                        // The colours below may vanish on the selection's face: use the row's own ink.
                        ink = self[0].cellInk(theme, selected, hovered);
                    } else {
                        d.fill(left, top, right, bottom, theme.palette().track());
                        ink = pet.mood() < 0.3F ? theme.palette().bad()
                                : pet.mood() < 0.6F ? theme.palette().warn() : theme.palette().good();
                    }
                    d.fill(left, top, left + Math.round((right - left) * pet.mood()), bottom, ink);
                }).comparator(Comparator.comparingDouble(Pet::mood)));
        return self[0];
    }

    private static TableComponent<Pet> filled(Scene s) {
        TableComponent<Pet> table = s.add(table());
        table.setItems(PETS);
        return table;
    }

    /** Draws a line of values under the table. Added first, so it lies behind the table and takes no click. */
    private static void readout(Scene s, Supplier<String> text) {
        s.paint((d, theme) -> d.textFit(text.get(), TABLE_X, READOUT_Y, TABLE_W, theme.palette().textSoft()));
    }

    private static String names(TableComponent<Pet> table) {
        StringBuilder out = new StringBuilder();
        for (Pet pet : table.view()) {
            out.append(pet.name().charAt(0));
        }
        return out.toString();
    }

    /** The selected row's place in the view, or -1. */
    private static int pos(TableComponent<Pet> table) {
        return table.selected() == null ? -1 : table.view().indexOf(table.selected());
    }

    private static String name(Pet pet) {
        return pet == null ? "none" : pet.name();
    }

    private static int rowMid(int row) {
        return ROW0_MID + row * ROW;
    }

    /** A chest's contents for the item table; the pickaxe does not stack. */
    private static final java.util.Map<String, Integer> CHEST = java.util.Map.of("minecraft:diamond", 12,
            "minecraft:iron_pickaxe", 1, "minecraft:oak_log", 64, "minecraft:redstone", 37, "minecraft:apple", 5);

    @Override
    public void states(Stage stage) {

        stage.state("items", 220, 100, s -> {
            dev.dryaides.minuit.TableComponent<String> t = s.add(new dev.dryaides.minuit.TableComponent<String>(new Box("t", 6, 6, 208, 88),
                    dev.dryaides.minuit.TableComponent.ITEM_ROW)
                    .itemColumn("Item", n -> dev.dryaides.minuit.ItemView.of(n))
                    .column("Count", c -> c.text(n -> String.valueOf(CHEST.get(n))).fixed(44).align(dev.dryaides.minuit.TableComponent.Align.RIGHT)));
            t.setItems(java.util.List.of("minecraft:diamond", "minecraft:iron_pickaxe", "minecraft:oak_log", "minecraft:redstone", "minecraft:apple"));
            s.move(40, 40);
        });
        stage.state("items row 14", 220, 76, s -> {
            dev.dryaides.minuit.TableComponent<String> t = s.add(new dev.dryaides.minuit.TableComponent<String>(new Box("t", 6, 6, 208, 64), 14)
                    .itemColumn("Item", n -> dev.dryaides.minuit.ItemView.of(n)).textColumn("Mod", n -> n.substring(0, n.indexOf(':'))));
            t.setItems(java.util.List.of("minecraft:diamond", "minecraft:iron_pickaxe", "minecraft:oak_log", "minecraft:redstone", "minecraft:apple"));
        });
        looks(stage);
        sorting(stage);
        selecting(stage);
        keyboard(stage);
        scrolling(stage);
        edges(stage);
    }

    private void looks(Stage stage) {
        stage.state("default", W, H, s -> {
            readout(s, () -> "8 rows, no selection, no sort");
            TableComponent<Pet> table = filled(s);
        });
        stage.state("hover row", W, H, s -> {
            readout(s, () -> "mouse over row 2");
            filled(s);
            s.move(NAME_X, rowMid(1));
            s.waitMs(200);
        });
        stage.state("hover sortable header", W, H, s -> {
            readout(s, () -> "mouse over the Level header");
            filled(s);
            s.move(LEVEL_X, HEADER_MID);
        });
        stage.state("selected, table focused", W, H, s -> {
            List<String> picked = new ArrayList<>();
            TableComponent<Pet>[] holder = holder();
            readout(s, () -> "click row 2: onSelect " + picked + ", selected " + name(holder[0].selected()));
            TableComponent<Pet> table = filled(s).onSelect(p -> picked.add(p.name()));
            holder[0] = table;
            s.click(NAME_X, rowMid(1));
            s.click(NAME_X, rowMid(1));
            s.move(TABLE_X - 4, READOUT_Y);
        });
        stage.state("selected, not focused", W, H, s -> {
            readout(s, () -> "select(Clover) from code");
            TableComponent<Pet> table = filled(s);
            table.select(PETS.get(3));
        });
        stage.state("keyboard ring", W, H, s -> {
            TableComponent<Pet> table = filled(s);
            s.key(InputKey.TAB);
            readout(s, () -> "Tab: cursor on the table, focus " + table.focused);
        });
        stage.state("disabled", W, H, s -> {
            List<String> picked = new ArrayList<>();
            readout(s, () -> "click, sort, wheel and keys do nothing: " + picked);
            TableComponent<Pet> table = filled(s).onSelect(p -> picked.add(p.name())).active(false);
            table.select(PETS.get(1));
            s.click(NAME_X, rowMid(2));
            s.click(LEVEL_X, HEADER_MID);
            s.scroll(NAME_X, rowMid(1), -1);
            s.focus(table);
            s.key(InputKey.DOWN);
        });
    }

    private void sorting(Stage stage) {
        stage.state("sort ascending, ties stable", W, H, s -> {
            List<String> sorts = new ArrayList<>();
            TableComponent<Pet>[] holder = holder();
            readout(s, () -> "Level asc " + names(holder[0]) + ", onSort " + sorts);
            TableComponent<Pet> table = filled(s).onSort((c, dir) -> sorts.add(c + ":" + dir));
            holder[0] = table;
            s.click(LEVEL_X, HEADER_MID);
            // Levels 3 are Biscuit, Ash, Dune and 5 are Clover, Fern, in the given order.
        });
        stage.state("sort descending, ties stable", W, H, s -> {
            TableComponent<Pet>[] holder = holder();
            readout(s, () -> "Level desc " + names(holder[0]) + ", ties keep B A D");
            TableComponent<Pet> table = filled(s);
            holder[0] = table;
            s.click(LEVEL_X, HEADER_MID);
            s.click(LEVEL_X, HEADER_MID);
        });
        stage.state("third click, no sort", W, H, s -> {
            TableComponent<Pet>[] holder = holder();
            readout(s, () -> "third click: " + names(holder[0]) + ", sort column " + holder[0].sortColumn());
            TableComponent<Pet> table = filled(s);
            holder[0] = table;
            s.click(LEVEL_X, HEADER_MID);
            s.click(LEVEL_X, HEADER_MID);
            s.click(LEVEL_X, HEADER_MID);
            s.move(TABLE_X - 4, READOUT_Y);
        });
        stage.state("no comparator, no sort", W, H, s -> {
            TableComponent<Pet>[] holder = holder();
            readout(s, () -> "click on Home: sort column " + holder[0].sortColumn());
            TableComponent<Pet> table = filled(s);
            holder[0] = table;
            s.click(130, HEADER_MID);
        });
        stage.state("sort keeps the selection", W, H, s -> {
            TableComponent<Pet>[] holder = holder();
            readout(s, () -> "Name asc: " + name(holder[0].selected()) + " moved to row " + (pos(holder[0]) + 1));
            TableComponent<Pet> table = filled(s);
            holder[0] = table;
            s.click(NAME_X, rowMid(2));
            s.click(NAME_X, HEADER_MID);
            s.move(TABLE_X - 4, READOUT_Y);
        });
    }

    private void selecting(Stage stage) {
        stage.state("double click activates", W, H, s -> {
            List<String> done = new ArrayList<>();
            readout(s, () -> "onActivate " + done + " (slow clicks did not)");
            TableComponent<Pet> table = filled(s).onActivate(p -> done.add(p.name()));
            s.click(NAME_X, rowMid(0));
            s.waitMs(500);
            s.click(NAME_X, rowMid(0));
            s.waitMs(500);
            s.click(NAME_X, rowMid(2));
            s.waitMs(100);
            s.click(NAME_X, rowMid(2));
        });
    }

    private void keyboard(Stage stage) {
        stage.state("keys: up down home end", W, H, s -> {
            List<String> picked = new ArrayList<>();
            readout(s, () -> "onSelect " + picked);
            TableComponent<Pet> table = filled(s).onSelect(p -> picked.add(p.name()));
            s.focus(table);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.UP);
            s.key(InputKey.END);
            s.key(InputKey.DOWN);
            s.key(InputKey.HOME);
            s.key(InputKey.UP);
            s.key(InputKey.DOWN);
        });
        stage.state("keys: page up and down", W, H, s -> {
            TableComponent<Pet>[] holder = holder();
            readout(s, () -> "raw page keys: row " + (pos(holder[0]) + 1) + "");
            TableComponent<Pet> table = filled(s);
            holder[0] = table;
            s.focus(table);
            KeyPress pageDown = KeyPress.key(267, 0);
            KeyPress pageUp = KeyPress.key(266, 0);
            s.key(InputKey.HOME);
            s.panel().rawKey(pageDown);
            s.panel().rawKey(pageUp);
            s.waitMs(300);
        });
        stage.state("keys: Tab, Down, Enter", W, H, s -> {
            List<String> done = new ArrayList<>();
            readout(s, () -> "Tab, Enter, Down, Enter, Esc: onActivate " + done);
            TableComponent<Pet> table = filled(s).onActivate(p -> done.add(p.name()));
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
            s.key(InputKey.ESCAPE);
        });
    }

    private void scrolling(Stage stage) {
        stage.state("wheel, header stays", W, H, s -> {
            TableComponent<Pet>[] holder = holder();
            readout(s, () -> "wheel down once");
            TableComponent<Pet> table = filled(s);
            holder[0] = table;
            s.scroll(NAME_X, rowMid(1), -1);
            s.waitMs(400);
            s.scroll(NAME_X, rowMid(1), 5);
            s.scroll(NAME_X, rowMid(1), -1);
            s.scroll(NAME_X, rowMid(1), -100);
            s.scroll(NAME_X, rowMid(1), 1);
            s.waitMs(400);
            s.move(TABLE_X - 4, READOUT_Y);
        });
        stage.state("scroll bar dragged", W, H, s -> {
            TableComponent<Pet>[] holder = holder();
            readout(s, () -> "thumb held at the bottom");
            TableComponent<Pet> table = filled(s);
            holder[0] = table;
            int barX = TABLE_X + TABLE_W - 4;
            int barTop = TABLE_Y + 1 + 14;
            s.hold(barX, barTop + 4, barX, barTop + 60);
        });
        stage.state("reduced motion", W, H, s -> {
            TableComponent<Pet>[] holder = holder();
            readout(s, () -> "reduced motion: no easing");
            TableComponent<Pet> table = filled(s);
            holder[0] = table;
            s.scroll(NAME_X, rowMid(1), -1);
            s.scroll(NAME_X, rowMid(1), 1);
            s.waitMs(400);
            HostState.reducedMotion(true);
            try {
                s.scroll(NAME_X, rowMid(1), -1);
            } finally {
                HostState.reducedMotion(false);
            }
        });
        stage.state("5000 rows, visible rows only", W, H, s -> {
            int[] calls = {0};
            int[] perFrame = {0};
            TableComponent<Pet>[] holder = holder();
            readout(s, () -> {
                perFrame[0] = calls[0];
                calls[0] = 0;
                return "5000 rows, " + perFrame[0] + " getter calls a frame, row "
                        + (pos(holder[0]) + 1);
            });
            List<Pet> many = new ArrayList<>();
            for (int i = 0; i < 5000; i++) {
                many.add(new Pet("Pet " + i, "Biome " + i % 7, i % 50, i % 11 / 10F));
            }
            TableComponent<Pet> table = s.add(new TableComponent<Pet>(new Box("many", TABLE_X, TABLE_Y, TABLE_W, TABLE_H), ROW)
                    .column("Name", c -> c.text(p -> {
                        calls[0]++;
                        return p.name();
                    }).comparator(Comparator.comparing(Pet::name)))
                    .column("Level", c -> c.text(p -> Integer.toString(p.level())).fixed(60)
                            .align(TableComponent.Align.RIGHT).comparator(Comparator.comparingInt(Pet::level))));
            table.setItems(many);
            holder[0] = table;
            s.focus(table);
            s.key(InputKey.END);
            s.waitMs(400);
            s.key(InputKey.UP);
            s.waitMs(100);
            s.move(TABLE_X - 4, READOUT_Y);
        });
    }

    private void edges(Stage stage) {
        stage.state("empty", W, H, s -> {
            readout(s, () -> "no rows: keys and clicks do nothing");
            TableComponent<Pet> table = s.add(table().emptyText("No pets yet"));
            s.click(NAME_X, rowMid(1));
            s.focus(table);
            s.key(InputKey.DOWN);
        });
        stage.state("empty, very long text", W, H, s -> {
            readout(s, () -> "the text wraps, and is cut where the body ends");
            s.add(table().emptyText("Nothing matches this search. Try fewer words, look under another tab, ask the "
                    + "server's owner to add a pet, wait for the next update, then read this message once more, "
                    + "because it goes on for far longer than this body can hold and has to stop somewhere."));
        });
        stage.state("long text, all alignments", W, H, s -> {
            readout(s, () -> "left, right and centred, each cut with an ellipsis");
            longTable(s);
        });
        stage.state("long text, tooltip", W, H, s -> {
            readout(s, () -> "the tooltip has the whole text");
            TableComponent<Pet> table = longTable(s);
            s.move(30, rowMid(0));
            s.settle(1500);
        });
        stage.state("narrow, fixed columns overflow", 120, H, s -> {
            TableComponent<Pet> table = s.add(new TableComponent<Pet>(new Box("narrow", 8, TABLE_Y, 104, TABLE_H), ROW)
                    .column("Name", c -> c.text(Pet::name).fixed(70))
                    .column("Home", c -> c.text(Pet::home).fixed(70))
                    .column("Hidden", c -> c.text(Pet::home).fixed(70)));
            table.setItems(PETS);
        });
    }

    private static TableComponent<Pet> longTable(Scene s) {
        TableComponent<Pet> table = s.add(new TableComponent<Pet>(new Box("long", TABLE_X, TABLE_Y, TABLE_W, TABLE_H), ROW)
                .column("A very long column title that cannot fit", c -> c.text(Pet::name).fixed(70)
                        .comparator(Comparator.comparing(Pet::name)))
                .column("Home, also rather long", c -> c.text(Pet::home).align(TableComponent.Align.RIGHT).fixed(70))
                .column("Centred", c -> c.text(Pet::home).align(TableComponent.Align.CENTER)));
        table.setItems(List.of(
                new Pet("Sir Reginald the Unreasonably Long Named Wolf", "The far side of the frozen peaks", 1, 1F),
                new Pet("Bo", "Hut", 2, 0.5F)));
        return table;
    }

    @SuppressWarnings("unchecked")
    private static TableComponent<Pet>[] holder() {
        return (TableComponent<Pet>[]) new TableComponent<?>[1];
    }
}
