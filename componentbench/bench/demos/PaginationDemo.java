package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.ContextMenuComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.PaginationComponent;
import java.util.ArrayList;
import java.util.List;

/** The pages, driven by scripted input. */
public final class PaginationDemo implements Demo {
    private static final int W = 290;
    private static final int H = 84;
    private static final class Rig {
        final Scene s;
        final List<Integer> picks = new ArrayList<>();
        final List<Integer> sizes = new ArrayList<>();
        final PaginationComponent c;
        final ButtonComponent after;
        ContextMenuComponent menu;
        String note = "";

        Rig(Scene s, int total, int size) {
            this(s, 274, total, size);
        }

        Rig(Scene s, int width, int total, int size) {
            this.s = s;
            c = s.add(new PaginationComponent(new Box("pages", 8, 8, width, 16), s.panel(), total, size, picks::add).onPageSize(sizes::add));
            after = s.add(new ButtonComponent(new Box("after", 8, 38, 60, 16), "After", () -> { }));
            s.caption(() -> note.isEmpty() ? "page " + (c.page() + 1) + "  " + c.slotsText() : note);
        }

        void clickSlot(int slot) {
            Box b = BenchAccess.slotBox(c, slot);
            s.click(b.x() + b.w() / 2, b.y() + b.h() / 2);
        }

        void clickPage(int page) {
            clickSlot(c.slotOfPage(page));
        }
    }

    @Override
    public void states(Stage stage) {
        stage.state("all_pages_fit", W, H, s -> {
            Rig r = new Rig(s, 50, 10);
        });
        stage.state("start_of_many", W, H, s -> {
            Rig r = new Rig(s, 200, 10);
        });
        stage.state("middle_of_many", W, H, s -> {
            Rig r = new Rig(s, 200, 10);
            r.c.setPage(9);
        });
        stage.state("end_of_many", W, H, s -> {
            Rig r = new Rig(s, 200, 10);
            r.c.setPage(19);
        });
        stage.state("same_slot_count_on_every_page", W, H, s -> {
            Rig r = new Rig(s, 200, 10);
            int count = r.c.slotCount();
            for (int page = 0; page < 20; page++) {
                r.c.setPage(page);
            }
            r.c.setPage(4);
        });
        stage.state("hover_page", W, H, s -> {
            Rig r = new Rig(s, 200, 10);
            Box b = BenchAccess.slotBox(r.c, r.c.slotOfPage(2));
            s.move(b.x() + 3, b.y() + 3);
        });
        stage.state("hover_ellipsis", W, H, s -> {
            Rig r = new Rig(s, 200, 10);
            Box b = BenchAccess.slotBox(r.c, r.c.ellipsisSlot());
            s.move(b.x() + 3, b.y() + 3);
        });
        stage.state("click_page", W, H, s -> {
            Rig r = new Rig(s, 200, 10);
            r.clickPage(3);
        });
        stage.state("click_arrow_and_ellipsis", W, H, s -> {
            Rig r = new Rig(s, 200, 10);
            r.clickSlot(r.c.slotCount() - 1);
            r.clickSlot(r.c.ellipsisSlot());
            r.clickSlot(0);
        });
        stage.state("keys_with_ring", W, H, s -> {
            Rig r = new Rig(s, 200, 10);
            s.key(InputKey.TAB);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT, true);
            s.key(InputKey.END);
            s.key(InputKey.RIGHT);
            s.key(InputKey.HOME);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
            s.key(InputKey.RIGHT);
        });
        stage.state("wheel", W, H, s -> {
            Rig r = new Rig(s, 200, 10);
            s.scroll(40, 12, -1);
            s.scroll(40, 12, -1);
            s.scroll(40, 12, 1);
            s.scroll(40, 50, -1);
        });
        stage.state("page_size", W, H, s -> {
            Rig r = new Rig(s, 500, 25);
            r.c.pageSizes(10, 25, 50);
        });
        stage.state("page_size_menu", W, 96, s -> {
            Rig r = new Rig(s, 500, 25);
            r.c.pageSizes(10, 25, 50);
            r.menu = s.add(r.c.menu());
            r.c.setPage(4); // item 100
            Box b = BenchAccess.sizeBox(r.c);
            s.click(b.x() + 4, b.y() + 4);
            s.waitMs(150);
        });
        stage.state("page_size_keeps_first_item", W, H, s -> {
            Rig r = new Rig(s, 500, 25);
            r.c.pageSizes(10, 25, 50);
            r.c.setPage(4);
            s.focus(r.c);
            s.key(InputKey.UP);
        });
        stage.state("size_cycles_without_menu", W, H, s -> {
            Rig r = new Rig(s, 500, 25);
            r.c.pageSizes(10, 25, 50);
            Box b = BenchAccess.sizeBox(r.c);
            s.click(b.x() + 4, b.y() + 4);
        });
        stage.state("narrow_drops_neighbours", W, H, s -> {
            Rig r = new Rig(s, 160, 200, 10);
        });
        stage.state("narrow_goes_compact", W, H, s -> {
            Rig r = new Rig(s, 100, 200, 10);
            r.c.setPage(2);
        });
        stage.state("big_numbers", W, H, s -> {
            Rig r = new Rig(s, 1_234_500, 10);
            r.c.setPage(60_000);
            s.key(InputKey.TAB);
        });
        stage.state("tiny_box", 40, 26, s -> {
            PaginationComponent c = s.add(new PaginationComponent(new Box("p", 4, 4, 32, 14), s.panel(), 200, 10, v -> { }));
        });
        stage.state("empty", W, H, s -> {
            Rig r = new Rig(s, 0, 10);
            s.key(InputKey.TAB);
        });
        stage.state("disabled", W, H, s -> {
            Rig r = new Rig(s, 500, 25);
            r.c.pageSizes(10, 25, 50);
            r.c.active = false;
            r.clickPage(1);
            s.scroll(40, 12, -1);
        });
        stage.state("shrinking_total_moves_back", W, H, s -> {
            Rig r = new Rig(s, 200, 10);
            r.c.setPage(19);
            r.c.setTotal(45);
        });
    }
}
