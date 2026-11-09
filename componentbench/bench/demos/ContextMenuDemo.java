package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.ContextMenuComponent;
import dev.dryaides.minuit.ContextMenuComponent.Item;
import dev.dryaides.minuit.DialogComponent;
import dev.dryaides.minuit.DropdownComponent;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.ScrollComponent;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.TextMeasure;
import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.TreeComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * The menu, driven by scripted input.
 */
public final class ContextMenuDemo implements Demo {
    private static final int W = 300;
    private static final int H = 176;
    private static final Box AREA = new Box("area", 0, 0, W, H - 19);
    private static final int OPENED = 150;
    private static final int LONG_HOVER = 200;
    private static final int SWITCH_HOVER = 300;

    /** A scene with widgets behind the menu and the line of values beneath. */
    private static final class Rig {
        final Scene s;
        final ButtonComponent button;
        final SliderComponent slider;
        int behind;
        String log = "";
        String note = "";
        /** What the menu holds, where it opened, and the submenus of the rows that have one. */
        Row[] rows = DEFAULT;
        Row[] kids1 = SORT_KIDS;
        Row[] kids2 = MORE_KIDS;
        int parentRow1 = SORT;
        int parentRow2 = 2;
        int ox = 60;
        int oy = 34;
        Box area = AREA;

        Rig(Scene s) {
            this.s = s;
            button = s.add(new ButtonComponent(new Box("behind", 8, 8, 90, 20), "Behind", () -> behind++));
            slider = s.add(new SliderComponent(new Box("level", 106, 8, 120, 16), 0, 100, 40, v -> behind++));
            s.caption(() -> note.isEmpty() ? log : note);
        }

        Runnable logs(String word) {
            return () -> log += word + " ";
        }

        /** The usual menu; add it last. */
        ContextMenuComponent menu() {
            return menu(AREA);
        }

        ContextMenuComponent menu(Box area) {
            ContextMenuComponent m = new ContextMenuComponent(area, s.panel()).items(items());
            return s.add(m);
        }

        List<Item> items() {
            return List.of(
                    Item.title("Edit"),
                    Item.action("Copy", logs("copy")).icon(Icon.SHARE).shortcut("Ctrl+C"),
                    Item.action("Paste", logs("paste")).icon(Icon.DOWNLOAD).shortcut("Ctrl+V"),
                    Item.action("Reload", logs("reload")).icon(Icon.REFRESH).shortcut("F5"),
                    Item.separator(),
                    Item.check("Show grid", true, on -> log += "grid=" + on + " "),
                    Item.check("Snap", false, on -> log += "snap=" + on + " "),
                    Item.separator(),
                    Item.submenu("Sort by",
                            Item.action("Name", logs("name")),
                            Item.action("Date", logs("date")),
                            Item.submenu("More",
                                    Item.action("Size", logs("size")),
                                    Item.action("Type", logs("type")))),
                    Item.action("Delete", logs("delete")).icon(Icon.TRASH));
        }

        ContextMenuComponent opened() {
            ContextMenuComponent m = menu();
            m.open(60, 34);
            s.waitMs(OPENED);
            return m;
        }

        /** Where an open menu is, worked out from what the demo gave it and where it opened. */
        Lv level(int n) {
            Lv first = place(area, ContextMenuDemo.level(s, area, rows), ox, oy);
            if (n == 0) {
                return first;
            }
            Lv second = placeChild(first, ContextMenuDemo.level(s, area, kids1), parentRow1, area);
            return n == 1 ? second : placeChild(second, ContextMenuDemo.level(s, area, kids2), parentRow2, area);
        }

        /** The middle of a row of an open menu. */
        void hover(ContextMenuComponent m, int level, int row) {
            Lv l = level(level);
            s.move(l.x + l.w / 2, l.rowY(row) + l.heights[row] / 2);
        }

        void clickRow(ContextMenuComponent m, int level, int row) {
            Lv l = level(level);
            s.click(l.x + l.w / 2, l.rowY(row) + l.heights[row] / 2);
        }
    }

    // Rows of Rig.items().
    private static final int COPY = 1;
    private static final int PASTE = 2;
    private static final int RELOAD = 3;
    private static final int RULE = 4;
    private static final int GRID = 5;
    private static final int SNAP = 6;
    private static final int SORT = 8;
    private static final int DELETE = 9;

    /** A row of a menu, for working out its place: T title, A action, C check, S submenu, R rule. */
    private record Row(char kind, String label, String shortcut, Icon icon, int artSide) {
    }

    private static Row row(char kind, String label) {
        return new Row(kind, label, "", null, 0);
    }

    private static Row row(char kind, String label, String shortcut, Icon icon) {
        return new Row(kind, label, shortcut, icon, 0);
    }

    private static final Row[] DEFAULT = {
            row('T', "Edit"), row('A', "Copy", "Ctrl+C", Icon.SHARE), row('A', "Paste", "Ctrl+V", Icon.DOWNLOAD),
            row('A', "Reload", "F5", Icon.REFRESH), row('R', ""), row('C', "Show grid"), row('C', "Snap"), row('R', ""),
            row('S', "Sort by"), row('A', "Delete", "", Icon.TRASH)};
    private static final Row[] SORT_KIDS = {row('A', "Name"), row('A', "Date"), row('S', "More")};
    private static final Row[] MORE_KIDS = {row('A', "Size"), row('A', "Type")};

    private static Row[] numberedRows(int count) {
        Row[] out = new Row[count];
        for (int i = 0; i < count; i++) {
            out[i] = row('A', "Entry " + (i + 1));
        }
        return out;
    }

    /** An open menu's place, as the component lays it out: the frame inset, the row heights, the width of the widest line. */
    private static final class Lv {
        int x;
        int y;
        int w;
        int h;
        int inset;
        int[] tops;
        int[] heights;

        int rowY(int row) {
            return y + inset + tops[row];
        }

        int right() {
            return x + w;
        }
    }

    private static int inset(Scene s) {
        return Math.max(3, Math.min(4, s.theme().frame().corner()));
    }

    private static Lv level(Scene s, Box area, Row... rows) {
        Lv l = new Lv();
        l.inset = inset(s);
        int rowH = dev.dryaides.minuit.BenchAccess.popupRow();
        int checkW = 0;
        int iconW = 0;
        int sideW = 0;
        int labelW = 0;
        int plainW = 0;
        for (Row r : rows) {
            if (r.icon() != null) {
                rowH = Math.max(rowH, Math.min(16, s.theme().icon(r.icon()).height()) + 2);
                iconW = Math.max(iconW, Math.min(16, s.theme().icon(r.icon()).width()));
            }
            if (r.artSide() > 0) {
                rowH = Math.max(rowH, Math.min(16, r.artSide()) + 2);
                iconW = Math.max(iconW, Math.min(16, r.artSide()));
            }
            if (r.kind() == 'C') {
                checkW = Math.max(checkW, s.theme().icon(Icon.CHECK).width());
            }
            if (r.kind() == 'S') {
                sideW = Math.max(sideW, s.theme().icon(Icon.NEXT).width());
            } else if (!r.shortcut().isEmpty() && r.kind() != 'T') {
                sideW = Math.max(sideW, TextMeasure.width(r.shortcut()));
            }
            if (r.kind() == 'T') {
                plainW = Math.max(plainW, TextMeasure.width(r.label()));
            } else {
                labelW = Math.max(labelW, TextMeasure.width(r.label()));
            }
        }
        l.tops = new int[rows.length];
        l.heights = new int[rows.length];
        int top = 0;
        for (int i = 0; i < rows.length; i++) {
            l.tops[i] = top;
            l.heights[i] = rows[i].kind() == 'R' ? 5 : rows[i].kind() == 'T' ? DropdownComponent.HEADER : rowH;
            top += l.heights[i];
        }
        int fitW = Math.max(1, Math.min(220, area.w() - 4));
        int fitH = Math.max(1, area.h() - 4);
        l.h = Math.min(fitH, top + 2 * l.inset);
        int bar = top + 2 * l.inset > fitH ? ScrollComponent.BAR + 1 : 0;
        int columns = (checkW > 0 ? checkW + 3 : 0) + (iconW > 0 ? iconW + 3 : 0);
        int body = Math.max(columns + labelW + (sideW > 0 ? 10 + sideW : 0), plainW) + 3 + 3;
        l.w = Math.min(fitW, Math.max(60, body + 2 * l.inset + bar));
        return l;
    }

    private static Lv place(Box area, Lv l, int px, int py) {
        int left = area.x() + 2;
        int top = area.y() + 2;
        int right = area.right() - 2;
        int bottom = area.bottom() - 2;
        int at = px;
        if (at + l.w > right) {
            at = px - l.w >= left ? px - l.w : right - l.w;
        }
        int down = py;
        if (down + l.h > bottom) {
            down = py - l.h >= top ? py - l.h : bottom - l.h;
        }
        l.x = Math.max(left, at);
        l.y = Math.max(top, down);
        return l;
    }

    private static Lv placeChild(Lv parent, Lv child, int row, Box area) {
        int left = area.x() + 2;
        int top = area.y() + 2;
        int right = area.right() - 2;
        int bottom = area.bottom() - 2;
        int onRight = parent.right() - 1;
        int onLeft = parent.x - child.w + 1;
        int at;
        if (onRight + child.w <= right) {
            at = onRight;
        } else if (onLeft >= left) {
            at = onLeft;
        } else {
            at = right - onRight >= onLeft + child.w - left ? right - child.w : left;
        }
        child.x = Math.max(left, Math.min(at, right - child.w));
        int aligned = parent.rowY(row) - child.inset;
        child.y = Math.max(top, Math.min(aligned, bottom - child.h));
        return child;
    }

    private static List<Item> numbered(int count) {
        List<Item> out = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            out.add(Item.action("Entry " + i, null));
        }
        return out;
    }

    @Override
    public void states(Stage stage) {
        look(stage);
        pointer(stage);
        keyboard(stage);
        typeAhead(stage);
        submenus(stage);
        placement(stage);
        scrolling(stage);
        edges(stage);
    }

    private void look(Stage stage) {
        stage.state("default", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
        });
        stage.state("minecraft items, rows of 18", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = s.add(new ContextMenuComponent(AREA, s.panel()).items(List.of(
                    Item.title("Give"),
                    Item.action("Diamond", null).iconArt(dev.dryaides.minuit.ItemView.of("minecraft:diamond")).shortcut("D"),
                    Item.action("Iron ingot", null).iconArt(dev.dryaides.minuit.ItemView.of("minecraft:iron_ingot", 12)),
                    Item.action("Bread (off)", null).iconArt(dev.dryaides.minuit.ItemView.of("minecraft:bread")),
                    Item.action("Theme icon", null).icon(Icon.SHARE))));
            m.disabled("Bread (off)", true);
            m.open(60, 34);
            s.waitMs(OPENED);
            r.rows = new Row[] {row('T', "Give"), new Row('A', "Diamond", "D", null, 16), new Row('A', "Iron ingot", "", null, 16),
                    new Row('A', "Bread (off)", "", null, 16), row('A', "Theme icon", "", Icon.SHARE)};
            r.hover(m, 0, 2);
        });
        stage.state("hover item", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, COPY);
        });
        stage.state("hover disabled", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, COPY);
            r.hover(m, 0, PASTE);
        });
        stage.state("keyboard row", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
        });
        stage.state("hover title and rule", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, 0);
            r.hover(m, 0, RULE);
            r.clickRow(m, 0, RULE);
            r.clickRow(m, 0, 0);
        });
        stage.state("icons only", 180, 90, s -> {
            ContextMenuComponent m = s.add(new ContextMenuComponent(new Box("a", 0, 0, 180, 90), s.panel()).items(
                    Item.action("Open", null).icon(Icon.FOLDER),
                    Item.action("Play", null).icon(Icon.PLAY),
                    Item.action("Stop", null).icon(Icon.STOP),
                    Item.action("Lock", null).icon(Icon.LOCK)));
            m.open(20, 10);
            s.waitMs(OPENED);
        });
        stage.state("plain labels", 180, 90, s -> {
            ContextMenuComponent m = s.add(new ContextMenuComponent(new Box("a", 0, 0, 180, 90), s.panel()).items(
                    Item.action("Open", null), Item.action("Rename", null), Item.action("Remove", null)));
            m.open(20, 10);
            s.waitMs(OPENED);
        });
        stage.state("checks and icons", 200, 90, s -> {
            ContextMenuComponent m = s.add(new ContextMenuComponent(new Box("a", 0, 0, 200, 90), s.panel()).items(
                    Item.check("Sound", true, null).icon(Icon.SPEAKER),
                    Item.check("Microphone", false, null).icon(Icon.MIC),
                    Item.check("Locked", true, null).icon(Icon.LOCK),
                    Item.action("No check here", null).icon(Icon.GLOBE)));
            m.disabled("Locked", true);
            m.open(20, 10);
            s.waitMs(OPENED);
        });
        stage.state("disabled all", 180, 70, s -> {
            ContextMenuComponent m = s.add(new ContextMenuComponent(new Box("a", 0, 0, 180, 70), s.panel()).items(
                    Item.action("Copy", null).shortcut("Ctrl+C"),
                    Item.check("Checked", true, null),
                    Item.submenu("Sub", Item.action("x", null))));
            m.disabled("Copy", true).disabled("Checked", true).disabled("Sub", true);
            m.open(20, 10);
            s.waitMs(OPENED);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
            s.key(InputKey.RIGHT);
        });
        stage.state("half open", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.open(60, 34);
            s.waitMs(32);
            s.settle(0);
        });
        stage.state("half open upward", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.open(60, 150);
            s.waitMs(32);
            s.settle(0);
        });
        stage.state("reduced motion snaps", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            BaseComponent.reducedMotion = true;
            try {
                m.open(60, 34);
                s.settle(0);
                s.move(1, 1);
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });
    }

    private void pointer(Stage stage) {
        stage.state("right click opens", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu().on(new Box("zone", 0, 30, W, 120));
            s.rightClick(50, 34);
        });
        stage.state("right click elsewhere", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu().on(new Box("zone", 0, 30, W, 120));
            s.rightClick(50, 160);
            s.click(50, 60);
            s.click(50, 18);
        });
        stage.state("left click in region passes", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu().on(r.button);
            s.click(50, 18);
            s.rightClick(50, 18);
        });
        stage.state("field in a region", W, H, s -> {
            Rig r = new Rig(s);
            TextFieldComponent field = s.add(new TextFieldComponent(new Box("f", 8, 34, 110, 16), "", v -> { }));
            ContextMenuComponent m = r.menu().on(field);
            s.click(40, 42);
            s.type("ab");
            s.focus(null);
            s.rightClick(40, 42);
            s.key(InputKey.ESCAPE);
            s.type("c");
            s.rightClick(40, 42);
            s.key(InputKey.DOWN);
        });
        stage.state("itemsAt reads the place", W, H, s -> {
            Rig r = new Rig(s);
            TreeComponent<String> t = s.add(new TreeComponent<>(new Box("t", 8, 34, 100, 60),
                    List.of("alpha", "beta", "gamma"), n -> List.of(), n -> n));
            ContextMenuComponent m = s.add(new ContextMenuComponent(AREA, s.panel())
                    .on(t)
                    .itemsAt((x, y) -> {
                        String node = t.rowAt(x, y);
                        return node == null ? List.of() : List.of(Item.title(node), Item.action("Open", r.logs(node)));
                    }));
            s.rightClick(40, 90);
            s.rightClick(40, 48);
            r.rows = new Row[] {row('T', "alpha"), row('A', "Open")};
            r.ox = 40;
            r.oy = 48;
            r.clickRow(m, 0, 1);
            s.rightClick(40, 60);
        });
        stage.state("click runs and closes", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.clickRow(m, 0, RELOAD);
        });
        stage.state("click disabled does nothing", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.clickRow(m, 0, PASTE);
        });
        stage.state("check toggles", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            List<Item> items = r.items();
            m.items(items);
            m.open(60, 34);
            s.waitMs(OPENED);
            r.clickRow(m, 0, GRID);
            m.open(60, 34);
            s.waitMs(OPENED);
            r.clickRow(m, 0, SNAP);
            m.open(60, 34);
            s.waitMs(OPENED);
        });
        stage.state("check keepOpen", 220, 90, s -> {
            int[] count = new int[1];
            Item a = Item.check("Stays open", false, on -> count[0]++).keepOpen();
            Item b = Item.check("Stays too", true, on -> count[0]++).keepOpen();
            ContextMenuComponent m = s.add(new ContextMenuComponent(new Box("a", 0, 0, 220, 90), s.panel()).items(a, b));
            m.open(20, 10);
            s.waitMs(OPENED);
            Box area = new Box("a", 0, 0, 220, 90);
            Lv keep = place(area, level(s, area, row('C', "Stays open"), row('C', "Stays too")), 20, 10);
            s.click(keep.x + 20, keep.rowY(0) + 4);
            s.click(keep.x + 20, keep.rowY(1) + 4);
            s.key(InputKey.UP);
            s.key(InputKey.SPACE);
        });
        stage.state("outside click closes", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            s.click(50, 18);
            s.click(50, 18);
        });
        stage.state("outside right click reopens", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu().on(AREA);
            s.rightClick(30, 40);
            s.waitMs(OPENED);
            s.rightClick(170, 32);
            s.waitMs(OPENED);
        });
        stage.state("right click on an item", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu().on(AREA);
            s.rightClick(30, 40);
            r.ox = 30;
            r.oy = 40;
            Lv menuAt = r.level(0);
            s.rightClick(menuAt.x + 10, menuAt.rowY(COPY) + 4);
        });
        stage.state("pointer leaves unlights", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, COPY);
            s.move(280, 120);
        });
        stage.state("tooltip on item", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(Item.action("Export", null).tooltip("Export", "Writes a file"), Item.action("Other", null));
            r.rows = new Row[] {row('A', "Export"), row('A', "Other")};
            m.open(60, 34);
            s.waitMs(OPENED);
            r.hover(m, 0, 0);
            s.settle(1500);
        });
        stage.state("tooltip says why disabled", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(Item.action("Paste", null), Item.action("Other", null));
            m.disabled("Paste", true, "Nothing to paste");
            r.rows = new Row[] {row('A', "Paste"), row('A', "Other")};
            m.open(60, 34);
            s.waitMs(OPENED);
            r.hover(m, 0, 0);
            s.settle(1500);
        });
        stage.state("no tooltip from behind", W, H, s -> {
            Rig r = new Rig(s);
            r.button.tooltip("Behind's tip");
            ContextMenuComponent m = r.menu();
            m.open(60, 34);
            s.move(50, 18);
            s.settle(1500);
        });
    }

    private void keyboard(Stage stage) {
        stage.state("keys walk and skip", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            StringBuilder seen = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                s.key(InputKey.DOWN);
                seen.append(m.currentLabel()).append(' ');
            }
            s.key(InputKey.UP);
            s.key(InputKey.HOME);
            s.key(InputKey.END);
        });
        stage.state("up from nothing", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            s.key(InputKey.UP);
        });
        stage.state("enter runs once", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            s.key(InputKey.ENTER);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
            s.key(InputKey.ENTER);
        });
        stage.state("space runs", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            s.key(InputKey.DOWN);
            s.key(InputKey.SPACE);
            s.type(" ");
        });
        stage.state("escape closes", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            s.focus(r.button);
            m.open(60, 34);
            s.waitMs(OPENED);
            s.key(InputKey.ESCAPE);
        });
        stage.state("tab closes", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            s.key(InputKey.DOWN);
            s.key(InputKey.TAB);
        });
        stage.state("focus returns after a click", W, H, s -> {
            Rig r = new Rig(s);
            TextFieldComponent field = s.add(new TextFieldComponent(new Box("f", 8, 34, 110, 16), "", v -> { }));
            ContextMenuComponent m = r.menu();
            s.click(40, 42);
            s.type("ab");
            m.open(60, 60);
            s.waitMs(OPENED);
            s.type("zz");
            r.oy = 60;
            r.clickRow(m, 0, COPY);
            s.type("c");
            m.open(60, 60);
            s.waitMs(OPENED);
            s.click(280, 140);
        });
        stage.state("ring of the widget behind", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            s.key(InputKey.TAB);
            BaseComponent cursor = s.panel().cursor();
            m.open(20, 12);
            s.waitMs(OPENED);
            s.key(InputKey.DOWN);
            s.key(InputKey.ESCAPE);
            s.key(InputKey.ENTER);
            m.open(20, 12);
            s.waitMs(OPENED);
        });
        stage.state("opened from a button", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent[] holder = new ContextMenuComponent[1];
            ButtonComponent opener = s.add(new ButtonComponent(new Box("opener", 106, 30, 80, 18), "Menu",
                    () -> holder[0].openBelow(new Box("under", 106, 30, 80, 18))));
            holder[0] = r.menu();
            ContextMenuComponent m = holder[0];
            m.items(Item.action("Copy", null), Item.action("Reload", null), Item.action("Delete", null));
            s.click(140, 38);
            s.waitMs(OPENED);
            s.key(InputKey.DOWN);
        });
        stage.state("opener then escape", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent[] holder = new ContextMenuComponent[1];
            ButtonComponent opener = s.add(new ButtonComponent(new Box("opener", 106, 30, 80, 18), "Menu",
                    () -> holder[0].openBelow(new Box("under", 106, 30, 80, 18))));
            holder[0] = r.menu();
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            BaseComponent stop = s.panel().cursor();
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
            s.waitMs(OPENED);
            s.key(InputKey.ESCAPE);
        });
    }

    private void typeAhead(Stage stage) {
        stage.state("type ahead", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            s.type("r");
            s.waitMs(800);
            s.type("SN");
        });
        stage.state("type ahead cycles", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            StringBuilder seen = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                s.type("s");
                seen.append(m.currentLabel()).append(" / ");
            }
        });
        stage.state("type ahead skips disabled", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            s.type("p");
            s.key(InputKey.ENTER);
        });
        stage.state("type ahead accents and spaces", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(Item.action("Fermer", r.logs("fermer")), Item.action("Éditer", r.logs("edit")),
                    Item.action("Enregistrer sous", r.logs("sous")), Item.action("Enregistrer tout", r.logs("tout")));
            m.open(60, 34);
            s.waitMs(OPENED);
            s.type("ed");
            s.waitMs(800);
            s.type("enregistrer");
            s.key(InputKey.SPACE);
            s.type(" t");
            s.waitMs(800);
            s.key(InputKey.SPACE);
        });
        stage.state("type ahead accent lit", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(Item.action("Fermer", null), Item.action("Éditer", null).shortcut("É"), Item.action("Été", null));
            m.open(60, 34);
            s.waitMs(OPENED);
            s.type("e");
            s.type("t");
        });
        stage.state("type ahead no match", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            s.key(InputKey.DOWN);
            s.type("q");
        });
        stage.state("type ahead in a submenu", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            s.type("so");
            s.key(InputKey.RIGHT);
            s.waitMs(OPENED);
            s.waitMs(800);
            s.type("d");
        });
    }

    private void submenus(Stage stage) {
        stage.state("submenu on hover", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, SORT);
            s.waitMs(LONG_HOVER);
        });
        stage.state("submenu in use", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, SORT);
            s.waitMs(LONG_HOVER);
            r.hover(m, 1, 1);
        });
        stage.state("three levels", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, SORT);
            s.waitMs(LONG_HOVER);
            r.hover(m, 1, 2);
            s.waitMs(LONG_HOVER);
            r.hover(m, 2, 1);
            r.clickRow(m, 2, 1);
        });
        stage.state("three levels open", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, SORT);
            s.waitMs(LONG_HOVER);
            r.hover(m, 1, 2);
            s.waitMs(LONG_HOVER);
            r.hover(m, 2, 0);
        });
        stage.state("crossing keeps the submenu", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, SORT);
            s.waitMs(LONG_HOVER);
            r.hover(m, 0, SNAP);
            s.waitMs(100);
            r.hover(m, 1, 0);
            s.waitMs(SWITCH_HOVER);
        });
        stage.state("leaving closes the submenu", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, SORT);
            s.waitMs(LONG_HOVER);
            r.hover(m, 0, COPY);
            s.waitMs(SWITCH_HOVER);
        });
        stage.state("pointer away keeps the trail", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, SORT);
            s.waitMs(LONG_HOVER);
            s.move(4, 140);
            s.waitMs(SWITCH_HOVER);
        });
        stage.state("click on submenu opens at once", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.clickRow(m, 0, SORT);
            r.clickRow(m, 0, SORT);
        });
        stage.state("keys enter and leave", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            s.key(InputKey.END);
            s.key(InputKey.UP);
            s.key(InputKey.RIGHT);
            s.key(InputKey.END);
            s.key(InputKey.ENTER);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.RIGHT);
            s.waitMs(OPENED);
        });
        stage.state("escape one level at a time", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.clickRow(m, 0, SORT);
            r.clickRow(m, 1, 2);
            s.key(InputKey.ESCAPE);
            s.key(InputKey.ESCAPE);
            s.key(InputKey.ESCAPE);
        });
        stage.state("hover open then right key", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, SORT);
            s.waitMs(LONG_HOVER);
            s.key(InputKey.RIGHT);
        });
        stage.state("hover open then down key", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, SORT);
            s.waitMs(LONG_HOVER);
            s.key(InputKey.DOWN);
        });
        stage.state("empty and disabled submenus", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(Item.submenu("Empty"), Item.submenu("Off", Item.action("x", null)), Item.action("Fine", null));
            m.disabled("Off", true);
            r.rows = new Row[] {row('S', "Empty"), row('S', "Off"), row('A', "Fine")};
            m.open(60, 34);
            s.waitMs(OPENED);
            r.hover(m, 0, 0);
            s.waitMs(LONG_HOVER);
            r.hover(m, 0, 1);
            s.waitMs(LONG_HOVER);
            r.clickRow(m, 0, 0);
            s.key(InputKey.DOWN);
        });
    }

    private void placement(Stage stage) {
        stage.state("flips up at the bottom", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.open(40, 150);
            s.waitMs(OPENED);
        });
        stage.state("flips left at the edge", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.open(290, 30);
            s.waitMs(OPENED);
        });
        stage.state("flips both ways", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.open(296, 156);
            s.waitMs(OPENED);
        });
        stage.state("submenu flips left", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.open(296, 30);
            s.waitMs(OPENED);
            r.ox = 296;
            r.oy = 30;
            r.clickRow(m, 0, SORT);
            s.waitMs(OPENED);
        });
        stage.state("submenu shifts up", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(Item.action("One", null), Item.action("Two", null), Item.action("Three", null),
                    Item.submenu("Tall", numbered(6)));
            m.open(40, 100);
            s.waitMs(OPENED);
            r.rows = new Row[] {row('A', "One"), row('A', "Two"), row('A', "Three"), row('S', "Tall")};
            r.kids1 = numberedRows(6);
            r.parentRow1 = 3;
            r.ox = 40;
            r.oy = 100;
            r.clickRow(m, 0, 3);
            s.waitMs(OPENED);
        });
        stage.state("third level flips back", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.open(150, 30);
            s.waitMs(OPENED);
            r.ox = 150;
            r.oy = 30;
            r.clickRow(m, 0, SORT);
            r.clickRow(m, 1, 2);
            s.waitMs(OPENED);
        });
        stage.state("openBelow flips above", W, H, s -> {
            Rig r = new Rig(s);
            Box anchor = new Box("anchor", 150, 130, 70, 18);
            s.paint((d, t) -> d.fill(anchor.x(), anchor.y(), anchor.right(), anchor.bottom(), t.palette().accent()));
            ContextMenuComponent m = r.menu();
            m.openBelow(anchor);
            s.waitMs(OPENED);
        });
        stage.state("smaller area", W, H, s -> {
            Rig r = new Rig(s);
            Box area = new Box("area", 40, 30, 150, 110);
            s.paint((d, t) -> {
                d.fill(area.x(), area.y(), area.right(), area.y() + 1, t.palette().accent());
                d.fill(area.x(), area.bottom() - 1, area.right(), area.bottom(), t.palette().accent());
                d.fill(area.x(), area.y(), area.x() + 1, area.bottom(), t.palette().accent());
                d.fill(area.right() - 1, area.y(), area.right(), area.bottom(), t.palette().accent());
            });
            ContextMenuComponent m = r.menu(area);
            m.open(170, 120);
            s.waitMs(OPENED);
        });
    }

    private void scrolling(Stage stage) {
        stage.state("tall menu scrolls", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(numbered(40));
            m.open(60, 10);
            s.waitMs(OPENED);
        });
        stage.state("wheel", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(numbered(40));
            m.open(60, 10);
            s.waitMs(OPENED);
            r.rows = numberedRows(40);
            r.ox = 60;
            r.oy = 10;
            Lv b = r.level(0);
            s.scroll(b.x + 20, b.y + 30, -3);
        });
        stage.state("wheel outside and without need", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            s.scroll(100, 60, -3);
            s.scroll(280, 140, -3);
        });
        stage.state("wheel closes the child", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            List<Item> items = new ArrayList<>(numbered(20));
            items.add(0, Item.submenu("Sub", Item.action("x", null)));
            m.items(items);
            m.open(60, 10);
            s.waitMs(OPENED);
            Row[] sub = new Row[21];
            sub[0] = row('S', "Sub");
            System.arraycopy(numberedRows(20), 0, sub, 1, 20);
            r.rows = sub;
            r.kids1 = new Row[] {row('A', "x")};
            r.parentRow1 = 0;
            r.ox = 60;
            r.oy = 10;
            r.clickRow(m, 0, 0);
            Lv b = r.level(0);
            s.scroll(b.x + 20, b.y + 30, -1);
        });
        stage.state("keys scroll into view", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(numbered(40));
            m.open(60, 10);
            s.waitMs(OPENED);
            s.key(InputKey.END);
            s.key(InputKey.DOWN);
            s.key(InputKey.UP);
        });
        stage.state("scroll bar dragged", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(numbered(40));
            m.open(60, 10);
            s.waitMs(OPENED);
            r.rows = numberedRows(40);
            r.ox = 60;
            r.oy = 10;
            Lv b = r.level(0);
            s.hold(b.right() - 5, b.y + 8, b.right() - 5, b.y + b.h);
        });
        stage.state("title scrolls back with the first item", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            List<Item> items = new ArrayList<>(numbered(30));
            items.add(0, Item.title("Section"));
            m.items(items);
            m.open(60, 10);
            s.waitMs(OPENED);
            s.key(InputKey.END);
            s.key(InputKey.HOME);
        });
    }

    private void edges(Stage stage) {
        stage.state("disabled takes no input", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu().on(AREA);
            m.active = false;
            s.rightClick(100, 60);
            m.open(100, 60);
            s.click(50, 18);
        });
        stage.state("disabled while open closes", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            s.focus(r.button);
            m.open(60, 34);
            s.waitMs(OPENED);
            m.active = false;
            s.waitMs(60);
        });
        stage.state("hidden takes no input", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu().on(AREA);
            m.visible = false;
            s.rightClick(100, 60);
            m.open(100, 60);
            s.click(50, 18);
        });
        stage.state("hidden while open closes", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            m.visible = false;
            s.waitMs(60);
            s.click(50, 18);
        });
        stage.state("empty does not open", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu().on(new Box("zone", 0, 30, W, 120));
            m.items(List.of());
            m.open(60, 34);
            s.rightClick(60, 50);
            m.itemsAt((x, y) -> null).open(60, 34);
        });
        stage.state("empty text", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu().emptyText("Nothing here");
            m.items(List.of());
            m.open(60, 34);
            s.waitMs(OPENED);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
        });
        stage.state("separators tidied", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(java.util.Arrays.asList(Item.separator(), Item.action("One", null), null, Item.separator(), Item.separator(),
                    Item.action("Two", null), Item.separator()));
            m.open(60, 34);
            s.waitMs(OPENED);
        });
        stage.state("only titles and rules", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(Item.title("Nothing to do"), Item.separator(), Item.title("Really"));
            m.open(60, 34);
            s.waitMs(OPENED);
            s.key(InputKey.DOWN);
            s.key(InputKey.HOME);
            s.key(InputKey.END);
            s.type("n");
        });
        stage.state("long text", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(
                    Item.title("A very long section title that cannot possibly fit in this menu at all"),
                    Item.action("A very long action label that cannot possibly fit either", null).shortcut("Ctrl+Shift+Alt+F12"),
                    Item.check("A very long checkable label that goes on and on and on", true, null),
                    Item.submenu("A very long submenu label that goes on and on and on", Item.action("x", null)));
            m.open(20, 34);
            s.waitMs(OPENED);
            r.rows = new Row[] {row('T', "A very long section title that cannot possibly fit in this menu at all"),
                    row('A', "A very long action label that cannot possibly fit either", "Ctrl+Shift+Alt+F12", null),
                    row('C', "A very long checkable label that goes on and on and on"),
                    row('S', "A very long submenu label that goes on and on and on")};
            r.ox = 20;
            Lv long1 = r.level(0);
            s.move(long1.x + 20, long1.rowY(1) + 5);
            s.settle(1500);
        });
        stage.state("long text, no tip", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.items(
                    Item.title("A very long section title that cannot possibly fit in this menu at all"),
                    Item.action("A very long action label that cannot possibly fit either", null).shortcut("Ctrl+Shift+Alt+F12"),
                    Item.action("Short", null).shortcut("A very long shortcut hint that cannot fit either, not even half"),
                    Item.check("A very long checkable label that goes on and on and on", true, null),
                    Item.action("Averyveryveryveryveryveryveryveryveryveryveryveryverylongwordwithoutanyspace", null).icon(Icon.TRASH),
                    Item.submenu("A very long submenu label that goes on and on and on", Item.action("x", null)));
            m.open(20, 34);
            s.waitMs(OPENED);
            s.key(InputKey.DOWN);
            s.settle(0);
        });
        stage.state("tiny area", 60, 40, s -> {
            Box area = new Box("a", 4, 4, 52, 32);
            ContextMenuComponent m = s.add(new ContextMenuComponent(area, s.panel()).items(
                    Item.action("A long enough label", null).shortcut("Ctrl+X"),
                    Item.check("Second", true, null),
                    Item.submenu("Third", Item.action("x", null)),
                    Item.action("Fourth", null)));
            m.open(30, 20);
            s.waitMs(OPENED);
            s.key(InputKey.END);
        });
        stage.state("very tiny area", 24, 16, s -> {
            Box area = new Box("a", 2, 2, 20, 12);
            ContextMenuComponent m = s.add(new ContextMenuComponent(area, s.panel()).items(
                    Item.action("Label", null).icon(Icon.TRASH), Item.action("Other", null)));
            m.open(10, 8);
            s.waitMs(OPENED);
            s.key(InputKey.DOWN);
            s.key(InputKey.RIGHT);
            s.scroll(10, 8, -1);
            s.settle(200);
        });
        stage.state("maxSize", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu().maxSize(90, 60);
            m.open(60, 34);
            s.waitMs(OPENED);
        });
        stage.state("must come last", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            s.add(new ButtonComponent(new Box("late", 200, 30, 60, 18), "Late", () -> { }));
            String thrown = "";
            try {
                m.open(60, 34);
            } catch (IllegalStateException e) {
                thrown = e.getMessage();
            }
        });
        stage.state("dialog comes later and blocks", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu().on(AREA);
            DialogComponent dialog = s.add(DialogComponent.alert(AREA, "Title", "A dialog above", "OK", null));
            dialog.open(s.panel());
            s.waitMs(260);
            s.rightClick(40, 40);
        });
        stage.state("closes the panel's overlay", W, H, s -> {
            Rig r = new Rig(s);
            s.overlay(new BaseComponent(0, 0, 20, 20) {
                @Override protected void render(dev.dryaides.minuit.Draw d, dev.dryaides.minuit.Theme t, int x, int y, boolean hovered) {
                    d.fill(x, y, x + w, y + h, t.palette().bad());
                }
            });
            ContextMenuComponent m = r.menu();
            m.open(60, 34);
            s.waitMs(OPENED);
        });
        stage.state("callback reopens", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent[] holder = new ContextMenuComponent[1];
            int[] closes = new int[1];
            holder[0] = r.menu().onClose(() -> closes[0]++);
            holder[0].items(Item.action("Again", () -> holder[0].open(120, 60)));
            r.rows = new Row[] {row('A', "Again")};
            holder[0].open(60, 34);
            s.waitMs(OPENED);
            r.clickRow(holder[0], 0, 0);
            s.waitMs(OPENED);
        });
        stage.state("keyboard not stolen by a still pointer", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.opened();
            r.hover(m, 0, COPY);
            s.key(InputKey.DOWN);
            s.waitMs(100);
            r.hover(m, 0, DELETE);
        });
        stage.state("the slider behind stays put", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent m = r.menu();
            m.open(100, 2);
            s.waitMs(OPENED);
            r.ox = 100;
            r.oy = 2;
            Lv b = r.level(0);
            s.drag(b.right() + 10, 14, b.right() + 40, 14);
        });
    }
}
