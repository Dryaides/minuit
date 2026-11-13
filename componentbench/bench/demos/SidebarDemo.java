package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ContextMenuComponent;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.SidebarComponent;
import dev.dryaides.minuit.SidebarComponent.Group;
import dev.dryaides.minuit.SidebarComponent.Item;
import dev.dryaides.minuit.SidebarComponent.Tone;
import java.util.ArrayList;
import java.util.List;

/** The sidebar, driven by scripted input. */
public final class SidebarDemo implements Demo {
    private static final int W = 240;
    private static final int H = 182;
    private static final Box NAV = new Box("nav", 6, 6, 120, H - 26);

    private static final class Rig {
        final Scene s;
        final List<String> picks = new ArrayList<>();
        final List<Boolean> folds = new ArrayList<>();
        final Item map = Item.of("Map").icon(Icon.GLOBE);
        final Item players = Item.of("Players").icon(Icon.SHARE).badge("12");
        final Item saves = Item.of("Saves").badge("3", Tone.GOOD);
        final Item logs = Item.of("Logs").badge("!", Tone.WARN);
        final Item files = Item.of("Files").icon(Icon.FOLDER).children(saves, logs);
        final Item theme = Item.of("Theme").icon(Icon.PALETTE);
        final Item sound = Item.of("Sound").icon(Icon.SPEAKER);
        final Item locked = Item.of("Locked").icon(Icon.LOCK);
        final SidebarComponent c;
        String note = "";

        Rig(Scene s) {
            this(s, NAV);
        }

        Rig(Scene s, Box box) {
            this.s = s;
            c = s.add(new SidebarComponent(box, s.panel())
                    .groups(Group.of("World", map, players, files), Group.of("Settings", theme, sound, locked))
                    .onSelect(i -> picks.add(i.label()))
                    .onCollapse(folds::add)
                    .disabled("Locked", true, "Ask an admin"));
            s.caption(() -> note.isEmpty() ? (picks.isEmpty() ? "" : "picked " + picks) : note);
        }

        /**
         * A row's top inside the box when the sidebar is open and Files is unfolded: a group title is 13 high, a row 14,
         * and the rows start 2 pixels inside the box. Settings sit 28 lower while Files is open.
         */
        int top(Item item) {
            if (item == map) {
                return 13;
            }
            if (item == players) {
                return 27;
            }
            if (item == files) {
                return 41;
            }
            if (item == saves) {
                return 55;
            }
            if (item == logs) {
                return 69;
            }
            int settings = files.isExpanded() ? 96 : 68;
            return item == theme ? settings : item == sound ? settings + 14 : settings + 28;
        }

        void clickRow(Item item) {
            s.click(NAV.x() + 2 + 30, NAV.y() + 2 + top(item) + 7);
        }

        /** On the rail the titles are gone and the first group's rows start at the top: map, players, files. */
        void clickRailRow(Item item) {
            s.click(NAV.x() + 2 + 8, NAV.y() + 2 + (item == map ? 0 : item == players ? 14 : 28) + 7);
        }

        void clickToggle() {
            SidebarDemo.clickToggle(s);
        }
    }

    /** The fold button sits in the foot of the box, 2 pixels from its bottom and 14 high. */
    private static void clickToggle(Scene s) {
        s.click(NAV.x() + 2 + 6, NAV.y() + NAV.h() - 2 - 14 + 6);
    }

    @Override
    public void states(Stage stage) {
        stage.state("default", W, H, s -> {
            Rig r = new Rig(s);
        });
        stage.state("minecraft_items", W, H, s -> {
            Item diamond = Item.of("Diamonds").iconArt(dev.dryaides.minuit.ItemView.of("minecraft:diamond")).badge("64");
            Item bread = Item.of("Food (off)").iconArt(dev.dryaides.minuit.ItemView.of("minecraft:bread"));
            SidebarComponent c = s.add(new SidebarComponent(NAV, s.panel())
                    .groups(Group.of("Items", diamond, Item.of("Iron").iconArt(dev.dryaides.minuit.ItemView.of("minecraft:iron_ingot")), bread),
                            Group.of("Other", Item.of("Theme").icon(Icon.PALETTE)))
                    .disabled("Food (off)", true));
            s.move(NAV.x() + 2 + 30, NAV.y() + 2 + 13 + 5);
        });
        stage.state("minecraft_items_rail", W, H, s -> {
            Item diamond = Item.of("Diamonds").iconArt(dev.dryaides.minuit.ItemView.of("minecraft:diamond")).badge("64");
            SidebarComponent c = s.add(new SidebarComponent(NAV, s.panel()).railWidth(14)
                    .groups(Group.of("Items", diamond, Item.of("Iron").iconArt(dev.dryaides.minuit.ItemView.of("minecraft:iron_ingot"))),
                            Group.of("Other", Item.of("Theme").icon(Icon.PALETTE))));
            clickToggle(s);
            s.waitMs(600);
        });
        stage.state("hover", W, H, s -> {
            Rig r = new Rig(s);
            s.move(NAV.x() + 2 + 20, NAV.y() + 2 + r.top(r.theme) + 5);
        });
        stage.state("click_selects", W, H, s -> {
            Rig r = new Rig(s);
            r.clickRow(r.players);
        });
        stage.state("disabled_item_ignored", W, H, s -> {
            Rig r = new Rig(s);
            r.clickRow(r.locked);
            s.move(NAV.x() + 2 + 20, NAV.y() + 2 + r.top(r.locked) + 5);
            s.waitMs(600);
        });
        stage.state("nested_open", W, H, s -> {
            Rig r = new Rig(s);
            r.clickRow(r.files);
            r.clickRow(r.logs);
        });
        stage.state("selection_inside_shut_parent", W, H, s -> {
            Rig r = new Rig(s);
            r.c.select(r.saves);
            r.clickRow(r.files);
        });
        stage.state("keys_with_ring", W, H, s -> {
            Rig r = new Rig(s);
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.ENTER);
        });
        stage.state("keys_left_goes_up_and_shuts", W, H, s -> {
            Rig r = new Rig(s);
            r.c.select(r.logs);
            s.key(InputKey.TAB);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
        });
        stage.state("keys_skip_disabled_reach_toggle", W, H, s -> {
            Rig r = new Rig(s);
            r.c.select(r.sound);
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
        });
        stage.state("rail", W, H, s -> {
            Rig r = new Rig(s);
            r.c.select(r.players);
            r.clickToggle();
            s.waitMs(200);
            s.move(70, 40);
        });
        stage.state("mid_fold", W, H, s -> {
            Rig r = new Rig(s);
            r.clickToggle();
            s.waitMs(64);
            s.settle(0);
        });
        stage.state("rail_reduced_motion", W, H, s -> {
            boolean was = BaseComponent.reducedMotion();
            HostState.reducedMotion(true);
            try {
                Rig r = new Rig(s);
                r.clickToggle();
                r.clickToggle();
                r.clickToggle();
            } finally {
                HostState.reducedMotion(was);
            }
        });
        stage.state("rail_tooltip", W, H, s -> {
            Rig r = new Rig(s);
            r.c.collapsed(true);
            s.move(NAV.x() + 2 + 8, NAV.y() + 2 + 14 + 6);
            s.waitMs(600);
        });
        stage.state("rail_letters_without_icons", W, H, s -> {
            Rig r = new Rig(s);
            r.c.groups(Group.of("One", Item.of("Alpha"), Item.of("Beta").badge("2"), Item.of("Emeraude")));
            r.c.collapsed(true);
        });
        stage.state("rail_flyout", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent menu = s.add(r.c.menu());
            r.c.collapsed(true);
            r.clickRailRow(r.files);
            s.waitMs(150);
        });
        stage.state("rail_flyout_keys_pick", W, H, s -> {
            Rig r = new Rig(s);
            ContextMenuComponent menu = s.add(r.c.menu());
            r.c.collapsed(true);
            s.focus(r.c);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.RIGHT);
            s.key(InputKey.ENTER);
        });
        stage.state("rail_without_menu_opens_sidebar", W, H, s -> {
            Rig r = new Rig(s);
            r.c.collapsed(true);
            r.clickRailRow(r.files);
            s.waitMs(200);
        });
        stage.state("overflow_wheel_and_bar", W, 90, s -> {
            Rig r = new Rig(s, new Box("nav", 6, 6, 120, 70));
            s.scroll(40, 30, -1);
            s.move(40, 30);
        });
        stage.state("focus_scrolls_row_into_view", W, 90, s -> {
            Rig r = new Rig(s, new Box("nav", 6, 6, 120, 70));
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
        });
        stage.state("long_text", W, H, s -> {
            Rig r = new Rig(s);
            Item longOne = Item.of("A label that is far too long to fit").icon(Icon.BUG).badge("1234");
            r.c.groups(Group.of("A title that is far too long to fit in here", longOne, Item.of("Another label which runs on and on").icon(Icon.CHIP)));
            s.move(NAV.x() + 2 + 30, NAV.y() + 2 + 13 + 5);
            s.waitMs(600);
        });
        stage.state("disabled", W, H, s -> {
            Rig r = new Rig(s);
            r.c.select(r.players);
            r.c.active = false;
            s.click(NAV.x() + 2 + 20, NAV.y() + 2 + r.top(r.map) + 5);
        });
        stage.state("empty", W, H, s -> {
            Rig r = new Rig(s);
            r.c.groups();
        });
        stage.state("tiny_box", 50, 30, s -> {
            SidebarComponent c = s.add(new SidebarComponent(new Box("nav", 2, 2, 44, 24), s.panel())
                    .groups(Group.of("World", Item.of("Map").icon(Icon.GLOBE), Item.of("Players").icon(Icon.SHARE))));
        });
    }
}
