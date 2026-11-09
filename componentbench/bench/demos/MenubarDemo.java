package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.ContextMenuComponent.Item;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.MenubarComponent;
import dev.dryaides.minuit.TextMeasure;
import java.util.ArrayList;
import java.util.List;

/** The menu bar, driven by scripted input. */
public final class MenubarDemo implements Demo {
    private static final int W = 300;
    private static final int H = 136;
    private static final class Rig {
        final Scene s;
        final List<String> log = new ArrayList<>();
        final MenubarComponent bar;
        final Box box;
        int behind;
        String note = "";

        Rig(Scene s) {
            this(s, new Box("bar", 6, 6, W - 12, 14));
        }

        Rig(Scene s, Box box) {
            this.s = s;
            this.box = box;
            bar = s.add(new MenubarComponent(box, s.panel())
                    .menu("File",
                            Item.action("New world", () -> log.add("new")).icon(Icon.PLUS).shortcut("Ctrl+N"),
                            Item.action("Open", () -> log.add("open")).icon(Icon.FOLDER).shortcut("Ctrl+O"),
                            Item.separator(),
                            Item.submenu("Recent", Item.action("Survival", () -> log.add("survival")), Item.action("Creative", () -> log.add("creative"))),
                            Item.separator(),
                            Item.action("Quit", () -> log.add("quit")).icon(Icon.EXIT))
                    .menu("Edit",
                            Item.action("Undo", () -> log.add("undo")).shortcut("Ctrl+Z"),
                            Item.action("Redo", () -> log.add("redo")).shortcut("Ctrl+Y"))
                    .menu("View",
                            Item.title("Show"),
                            Item.check("Grid", true, on -> log.add("grid=" + on)),
                            Item.check("Sidebar", false, on -> log.add("side=" + on)))
                    .menu("Help", Item.action("About", () -> log.add("about")).icon(Icon.BUG))
                    .disabled("Redo", true));
            s.add(new ButtonComponent(new Box("behind", 150, 60, 80, 20), "Behind", () -> behind++));
            s.caption(() -> note.isEmpty() ? String.join(" ", log) : note);
            s.add(bar.popup());
        }

        /** A title is its text and 5 pixels each side, 100 at most; titles that do not fit end in a menu of "...". */
        private static int titleW(String label) {
            return Math.min(100, TextMeasure.width(label) + 10);
        }

        /** The places of the slots on the bar, as the bar lays out the titles it was given: left edge and width. */
        int[][] slots() {
            String[] titles = {"File", "Edit", "View", "Help"};
            int total = 0;
            for (String t : titles) {
                total += titleW(t);
            }
            int reserve = total <= box.w() ? 0 : titleW(Draw.ELLIPSIS);
            List<int[]> out = new ArrayList<>();
            int at = 0;
            for (String t : titles) {
                if (at + titleW(t) + reserve > box.w()) {
                    out.add(new int[] {at, reserve});
                    break;
                }
                out.add(new int[] {at, titleW(t)});
                at += titleW(t);
            }
            return out.toArray(new int[0][]);
        }

        void clickTitle(int slot) {
            int[] b = slots()[slot];
            s.click(box.x() + b[0] + b[1] / 2, box.y() + box.h() / 2);
        }

        void moveTitle(int slot) {
            int[] b = slots()[slot];
            s.move(box.x() + b[0] + b[1] / 2, box.y() + box.h() / 2);
        }

        /** A File menu row's line: the menu opens 2 pixels under the bar, inside its frame inset (3 to 4). */
        int fileRowY(int row) {
            int inset = Math.max(3, Math.min(4, s.theme().frame().corner()));
            int rowH = 12;
            for (Icon icon : new Icon[] {Icon.PLUS, Icon.FOLDER, Icon.EXIT}) {
                rowH = Math.max(rowH, Math.min(16, s.theme().icon(icon).height()) + 2);
            }
            int[] tops = {0, rowH, 2 * rowH, 2 * rowH + 5, 3 * rowH + 5, 3 * rowH + 10};
            return box.y() + box.h() + 2 + inset + tops[row] + 5;
        }
    }

    @Override
    public void states(Stage stage) {
        stage.state("closed", W, H, s -> {
            Rig r = new Rig(s);
        });
        stage.state("hover_title", W, H, s -> {
            Rig r = new Rig(s);
            r.moveTitle(1);
        });
        stage.state("click_opens", W, H, s -> {
            Rig r = new Rig(s);
            r.clickTitle(0);
            s.waitMs(150);
        });
        stage.state("hover_switches", W, H, s -> {
            Rig r = new Rig(s);
            r.clickTitle(0);
            r.moveTitle(2);
            s.waitMs(150);
        });
        stage.state("click_title_again_closes", W, H, s -> {
            Rig r = new Rig(s);
            r.clickTitle(1);
            r.clickTitle(1);
        });
        stage.state("click_item_runs", W, H, s -> {
            Rig r = new Rig(s);
            r.clickTitle(0);
            s.waitMs(150);
            s.click(20, r.fileRowY(1));
        });
        stage.state("submenu_by_pointer", W, H, s -> {
            Rig r = new Rig(s);
            r.clickTitle(0);
            s.waitMs(150);
            s.move(20, r.fileRowY(3));
            s.waitMs(220);
        });
        stage.state("click_outside_presses_nothing", W, H, s -> {
            Rig r = new Rig(s);
            r.clickTitle(2);
            s.click(190, 70);
        });
        stage.state("keys_ring_on_bar", W, H, s -> {
            Rig r = new Rig(s);
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
        });
        stage.state("keys_down_opens_first_lit", W, H, s -> {
            Rig r = new Rig(s);
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.waitMs(150);
        });
        stage.state("keys_ignore_resting_pointer", W, H, s -> {
            Rig r = new Rig(s);
            r.moveTitle(3);
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.RIGHT);
            s.waitMs(150);
        });
        stage.state("keys_right_walks_the_bar", W, H, s -> {
            Rig r = new Rig(s);
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.RIGHT);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.waitMs(150);
        });
        stage.state("keys_right_enters_submenu_first", W, H, s -> {
            Rig r = new Rig(s);
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.waitMs(150);
        });
        stage.state("keys_escape_back_on_bar", W, H, s -> {
            Rig r = new Rig(s);
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
            s.key(InputKey.DOWN);
            s.key(InputKey.ESCAPE);
        });
        stage.state("keys_check_item", W, H, s -> {
            Rig r = new Rig(s);
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
        });
        stage.state("overflow_into_ellipsis", W, H, s -> {
            Rig r = new Rig(s, new Box("bar", 6, 6, 100, 14));
            r.clickTitle(2);
            s.waitMs(150);
        });
        stage.state("menu_stays_in_screen", W, H, s -> {
            Rig r = new Rig(s, new Box("bar", 194, 6, 100, 14));
            r.clickTitle(r.slots().length - 1);
            s.waitMs(150);
        });
        stage.state("disabled_title", W, H, s -> {
            Rig r = new Rig(s);
            r.bar.disabled(1, true);
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
            r.clickTitle(1);
        });
        stage.state("disabled_whole", W, H, s -> {
            Rig r = new Rig(s);
            r.bar.active = false;
            r.clickTitle(0);
        });
        stage.state("disabling_closes", W, H, s -> {
            Rig r = new Rig(s);
            r.clickTitle(0);
            r.bar.active = false;
            s.waitMs(50);
        });
        stage.state("long_title", W, H, s -> {
            MenubarComponent bar = s.add(new MenubarComponent(new Box("bar", 6, 6, W - 12, 14), s.panel())
                    .menu("A very long menu title that goes on far too long", Item.action("Item", () -> { }))
                    .menu("Two", Item.action("Item", () -> { })));
            s.add(bar.popup());
            s.move(10, 6);
            s.waitMs(600);
        });
        stage.state("empty_bar", W, 40, s -> {
            MenubarComponent bar = s.add(new MenubarComponent(new Box("bar", 6, 6, W - 12, 14), s.panel()));
            s.add(bar.popup());
        });
        stage.state("tiny_box", 40, 24, s -> {
            MenubarComponent bar = s.add(new MenubarComponent(new Box("bar", 0, 0, 36, 10), s.panel())
                    .menu("File", Item.action("Item", () -> { }))
                    .menu("Edit", Item.action("Item", () -> { })));
            s.add(bar.popup());
        });
    }
}
