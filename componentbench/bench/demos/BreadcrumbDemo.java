package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.BreadcrumbComponent;
import dev.dryaides.minuit.BreadcrumbComponent.Crumb;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.ContextMenuComponent;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The path, driven by scripted input. */
public final class BreadcrumbDemo implements Demo {
    private static final int W = 260;
    private static final int H = 106;
    private static final String[] PATH = {"Worlds", "Survival", "Backups", "Today", "Morning", "Chunk 12"};

    private static final class Rig {
        final Scene s;
        final List<Integer> picks = new ArrayList<>();
        final ButtonComponent after;
        BreadcrumbComponent c;
        ContextMenuComponent menu;
        String note = "";

        Rig(Scene s, int width, String... labels) {
            this.s = s;
            c = s.add(new BreadcrumbComponent(new Box("path", 8, 8, width, 14), s.panel(), picks::add).crumbs(labels));
            after = s.add(new ButtonComponent(new Box("after", W - 8 - 70, 60, 70, 16), "After", () -> { }));
            s.caption(() -> note.isEmpty() ? (picks.isEmpty() ? "" : "picked " + picks) : note);
            menu = s.add(c.menu());
        }

        void clickSlot(int slot) {
            Box b = BenchAccess.slotBox(c, slot);
            s.click(b.x() + b.w() / 2, b.y() + b.h() / 2);
        }
    }

    @Override
    public void states(Stage stage) {
        stage.state("fits", W, H, s -> {
            Rig r = new Rig(s, 244, "Worlds", "Survival", "Backups");
        });
        stage.state("hover", W, H, s -> {
            Rig r = new Rig(s, 244, "Worlds", "Survival", "Backups");
            Box b = BenchAccess.slotBox(r.c, 1);
            s.move(b.x() + 4, b.y() + 4);
        });
        stage.state("pressed", W, H, s -> {
            Rig r = new Rig(s, 244, "Worlds", "Survival", "Backups");
            Box b = BenchAccess.slotBox(r.c, 0);
            s.press(b.x() + 4, b.y() + 4);
        });
        stage.state("click_picks_index", W, H, s -> {
            Rig r = new Rig(s, 244, "Worlds", "Survival", "Backups");
            r.clickSlot(1);
            r.clickSlot(2);
        });
        stage.state("release_elsewhere_cancels", W, H, s -> {
            Rig r = new Rig(s, 244, "Worlds", "Survival", "Backups");
            Box b = BenchAccess.slotBox(r.c, 0);
            s.press(b.x() + 4, b.y() + 4);
            s.release(200, 50);
        });
        stage.state("middle_folded", W, H, s -> {
            Rig r = new Rig(s, 180, PATH);
        });
        stage.state("ellipsis_menu_open", W, H, s -> {
            Rig r = new Rig(s, 180, PATH);
            r.clickSlot(1);
            s.waitMs(150);
        });
        stage.state("ellipsis_menu_picks", W, H, s -> {
            Rig r = new Rig(s, 180, PATH);
            int hiddenFirst = r.c.hiddenRange()[0];
            r.clickSlot(1);
            s.waitMs(150);
            s.key(InputKey.ENTER);
        });
        stage.state("keyboard_ring", W, H, s -> {
            Rig r = new Rig(s, 180, PATH);
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
        });
        stage.state("keyboard_enter_then_past_end", W, H, s -> {
            Rig r = new Rig(s, 180, PATH);
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
            s.key(InputKey.END);
            int end = r.c.cursorSlot();
            s.key(InputKey.RIGHT);
        });
        stage.state("icons", W, H, s -> {
            Rig r = new Rig(s, 244);
            r.c.crumbs(List.of(Crumb.of("Home").icon(Icon.GLOBE), Crumb.of("Files").icon(Icon.FOLDER), Crumb.of("Trash").icon(Icon.TRASH)));
        });
        stage.state("minecraft_items_fit_h_minus_2", W, H, s -> {
            Rig r = new Rig(s, 244);
            r.c.crumbs(List.of(Crumb.of("Chest").iconArt(dev.dryaides.minuit.ItemView.of("minecraft:chest")),
                    Crumb.of("Diamonds").iconArt(dev.dryaides.minuit.ItemView.of("minecraft:diamond")),
                    Crumb.of("Off").iconArt(dev.dryaides.minuit.ItemView.of("minecraft:bread")), Crumb.of("Page")));
            r.c.disabled(2, true);
        });
        stage.state("disabled_crumb", W, H, s -> {
            Rig r = new Rig(s, 244);
            r.c.crumbs(List.of(Crumb.of("Server"), Crumb.of("Locked").icon(Icon.LOCK), Crumb.of("Page")));
            r.c.disabled(1, true);
            r.clickSlot(1);
        });
        stage.state("disabled_all", W, H, s -> {
            Rig r = new Rig(s, 244, "Worlds", "Survival", "Backups");
            r.c.active = false;
            r.clickSlot(0);
            s.move(20, 12);
        });
        stage.state("long_labels_tooltip", W, H, s -> {
            Rig r = new Rig(s, 244, "A very long world name that keeps going and going", "Backups", "An equally long place name that never ends at all");
            Box b = BenchAccess.slotBox(r.c, 0);
            s.move(b.x() + 6, b.y() + 6);
            s.waitMs(600);
        });
        stage.state("narrow_last_cut", W, H, s -> {
            Rig r = new Rig(s, 110, "Worlds", "Survival", "A long place name");
        });
        stage.state("tiny_box", 60, 32, s -> {
            BreadcrumbComponent c = s.add(new BreadcrumbComponent(new Box("path", 4, 4, 44, 10), s.panel(), v -> { }).crumbs(PATH));
        });
        stage.state("empty_and_hidden", W, H, s -> {
            Rig r = new Rig(s, 244);
            s.key(InputKey.TAB);
        });
    }
}
