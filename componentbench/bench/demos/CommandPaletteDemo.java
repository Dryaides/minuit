package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.CommandPaletteComponent;
import dev.dryaides.minuit.CommandPaletteComponent.Command;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.TextFieldComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The palette, driven by scripted input. */
public final class CommandPaletteDemo implements Demo {
    private static final int W = 300;
    private static final int H = 196;
    private static final class Rig {
        final Scene s;
        final List<String> ran = new ArrayList<>();
        final int width;
        final int height;
        final ButtonComponent behind;
        final CommandPaletteComponent c;
        int pressedBehind;

        Rig(Scene s) {
            this(s, W, H);
        }

        Rig(Scene s, int width, int height) {
            this.s = s;
            this.width = width;
            this.height = height;
            behind = s.fixture(new ButtonComponent(new Box("behind", 8, 8, 90, 20), "Settings", () -> pressedBehind++));
            s.caption(() -> ran.isEmpty() ? "" : "Ran " + String.join(", ", ran));
            c = s.add(new CommandPaletteComponent(new Box("area", 0, 0, width, height), s.panel())
                    .group("World",
                            cmd("Teleport home").icon(Icon.GLOBE).shortcut("H").keywords("spawn", "bed"),
                            cmd("Save the world").icon(Icon.DOWNLOAD).shortcut("Ctrl+S"),
                            cmd("Reload chunks").icon(Icon.REFRESH).shortcut("F3+A"),
                            cmd("Delete the world").icon(Icon.TRASH))
                    .group("Look",
                            cmd("Change theme").icon(Icon.PALETTE),
                            cmd("Dark mode").icon(Icon.MOON).shortcut("Ctrl+D"),
                            cmd("Éditer les couleurs").icon(Icon.PALETTE))
                    .group("Sound",
                            cmd("Mute all").icon(Icon.SPEAKER).shortcut("M"),
                            cmd("Open the microphone").icon(Icon.MIC)));
            c.disabled("Delete the world", true, "Not on a server");
        }

        Command cmd(String label) {
            return Command.of(label, () -> ran.add(label));
        }

        /** The card's frame inset, from the theme's corner, 3 to 4 pixels. */
        int inset() {
            return Math.max(3, Math.min(4, s.theme().frame().corner()));
        }

        /** The card is 240 wide at most, centred; its list is 8 rows of 14 high at most, with a 16 high field above. */
        int cardX() {
            return (width - Math.min(240, width - 8)) / 2;
        }

        int cardY() {
            int chrome = 2 * inset() + 16 + 3;
            int cardH = chrome + Math.max(14, Math.min(8 * 14, height - 8 - chrome));
            return Math.max(Math.min(4, Math.max(0, (height - cardH) / 2)), (height - cardH) / 3);
        }

        int rowX() {
            return cardX() + inset();
        }

        /** The top of a list row when the first row is a group title of 13 and the commands are 14 high. */
        int rowTop(int row) {
            return cardY() + inset() + 16 + 3 + (row == 0 ? 0 : 13 + (row - 1) * 14);
        }

        void clickRow(int row) {
            s.click(rowX() + 30, rowTop(row) + 7);
        }

        String lit() {
            return c.litCommand() == null ? null : c.litCommand().label();
        }
    }

    @Override
    public void states(Stage stage) {
        stage.state("closed_takes_nothing", W, H, s -> {
            Rig r = new Rig(s);
            s.click(30, 16);
            s.key(InputKey.TAB);
        });
        stage.state("minecraft_items_rows_of_18", W, H, s -> {
            CommandPaletteComponent c = s.add(new CommandPaletteComponent(new Box("area", 0, 0, W, H), s.panel())
                    .group("Give",
                            Command.of("Diamond", null).iconArt(dev.dryaides.minuit.ItemView.of("minecraft:diamond")).shortcut("D"),
                            Command.of("Iron ingot x12", null).iconArt(dev.dryaides.minuit.ItemView.of("minecraft:iron_ingot", 12)),
                            Command.of("Bread (off)", null).iconArt(dev.dryaides.minuit.ItemView.of("minecraft:bread")),
                            Command.of("Theme icon", null).icon(Icon.GLOBE)));
            c.disabled("Bread (off)", true);
            c.open();
        });
        stage.state("open_groups", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
        });
        stage.state("opening_midway", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.waitMs(48);
            s.settle(0);
        });
        stage.state("fuzzy_marks", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.type("tw");
            s.waitMs(150);
        });
        stage.state("site_open", W, H, s -> {
            Rig r = new Rig(s);
            r.c.footer("Enter: run   Esc: close").open();
            s.key(InputKey.DOWN);
            s.key(InputKey.UP);
            s.waitMs(150);
        });
        stage.state("site_search", W, H, s -> {
            Rig r = new Rig(s);
            r.c.footer("Enter: run   Esc: close").open();
            s.type("tw");
            s.key(InputKey.DOWN);
            s.key(InputKey.UP);
            s.waitMs(150);
        });
        stage.state("prefix_beats_scattered", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.type("mu");
        });
        stage.state("accents_ignored", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.type("edit");
            s.waitMs(150);
        });
        stage.state("keyword_finds_unmarked", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.type("spawn");
            s.waitMs(150);
        });
        stage.state("no_results", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.type("zzzz");
            s.waitMs(150);
            s.key(InputKey.ENTER);
        });
        stage.state("keys_move_wrap_skip_disabled", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.UP);
            s.key(InputKey.UP);
            s.key(InputKey.UP);
            s.key(InputKey.UP);
            s.waitMs(150);
        });
        stage.state("tab_walks_groups", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB);
            s.key(InputKey.TAB, true);
            s.key(InputKey.TAB, true);
            s.waitMs(150);
        });
        stage.state("enter_runs_and_closes", W, H, s -> {
            Rig r = new Rig(s);
            s.focus(r.behind);
            r.c.open();
            s.type("dark");
            s.key(InputKey.ENTER);
        });
        stage.state("recent_on_top", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.type("mute");
            s.key(InputKey.ENTER);
            r.c.open();
            s.type("dark");
            s.key(InputKey.ENTER);
            r.c.open();
            s.waitMs(150);
        });
        stage.state("recent_from_saved_ids", W, H, s -> {
            Rig r = new Rig(s);
            r.c.recent(List.of("Change theme", "gone from the list", "Mute all"));
            r.c.open();
        });
        stage.state("pointer_lights_and_click_runs", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.waitMs(150);
            s.move(r.rowX() + 30, r.rowTop(3) + 5);
            s.move(r.rowX() + 31, r.rowTop(3) + 5);
        });
        stage.state("disabled_row_tooltip", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.waitMs(150);
            r.clickRow(4);
            s.move(r.rowX() + 30, r.rowTop(4) + 5);
            s.waitMs(600);
        });
        stage.state("click_outside_closes_only", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.waitMs(150);
            s.click(10, 16);
        });
        stage.state("wheel_and_bar", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.waitMs(150);
            s.scroll(r.cardX() + 40, r.cardY() + 60, -1);
        });
        stage.state("escape_closes", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            s.type("abc");
            s.key(InputKey.ESCAPE);
            r.c.open();
            s.key(InputKey.ESCAPE);
        });
        stage.state("long_text", W, H, s -> {
            Rig r = new Rig(s);
            r.c.clearCommands().group("A very long group title that goes on and on and on and on and on and on",
                    r.cmd("A command with a very long label that cannot possibly fit in this card at all").icon(Icon.BUG).shortcut("Ctrl+Shift+Alt+F12"),
                    r.cmd("Another command with a very long label that needs to be cut short").shortcut("X"));
            r.c.open();
            s.type("cmd");
            s.waitMs(150);
            s.move(r.rowX() + 30, r.rowTop(1) + 5);
            s.waitMs(600);
        });
        stage.state("small_area", 120, 60, s -> {
            Rig r = new Rig(s, 120, 60);
            r.c.open();
            s.waitMs(150);
        });
        stage.state("empty_commands", W, H, s -> {
            Rig r = new Rig(s);
            r.c.clearCommands();
            r.c.open();
        });
        stage.state("five_thousand_commands", W, H, s -> {
            Rig r = new Rig(s);
            List<Command> many = new ArrayList<>();
            for (int i = 0; i < 5000; i++) {
                many.add(r.cmd("Command number " + i));
            }
            r.c.clearCommands().group("Many", many);
            r.c.open();
            s.type("n 4999");
            s.waitMs(150);
        });
        stage.state("disabling_closes", W, H, s -> {
            Rig r = new Rig(s);
            r.c.open();
            r.c.active = false;
            s.waitMs(50);
            r.c.open();
        });
        stage.state("reduced_motion_at_once", W, H, s -> {
            boolean was = BaseComponent.reducedMotion();
            HostState.reducedMotion(true);
            try {
                Rig r = new Rig(s);
                r.c.open();
                s.settle(0);
            } finally {
                HostState.reducedMotion(was);
            }
        });
        stage.state("copy_paste_in_search", W, H, s -> {
            TextFieldComponent.clipboard = new TextFieldComponent.Clipboard() {
                String value = "theme";
                @Override public String get() { return value; }
                @Override public void set(String text) { value = text; }
            };
            Rig r = new Rig(s);
            r.c.open();
            s.key(InputKey.PASTE);
            s.waitMs(150);
        });
    }
}
