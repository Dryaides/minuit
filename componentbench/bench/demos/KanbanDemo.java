package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.Picture;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.KanbanComponent;
import java.util.ArrayList;
import java.util.List;

/** The board, driven by scripted input. */
public final class KanbanDemo implements Demo {
    private static final int W = 260;
    private static final int H = 120;
    private static final List<String> TODO = List.of("Mine iron", "Build a farm", "Find a village", "Tame a wolf");
    private static final List<String> DOING = List.of("Enchant a pick", "Trade with a mason");
    private static final List<String> DONE = List.of("Craft a bed");

    private static KanbanComponent<String> board(List<String> heard) {
        KanbanComponent<String> c = new KanbanComponent<String>(new Box("k", 6, 6, W - 12, H - 12), v -> v)
                .column("To do", TODO).column("Doing", DOING).column("Done", DONE).emptyText("Nothing here");
        if (heard != null) {
            c.onMove((v, a, b, e, f) -> heard.add(v + ":" + a + "," + b + ">" + e + "," + f));
        }
        return c;
    }

    private static List<String> numbered(int count) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add("Card " + (i + 1));
        }
        return out;
    }

    private static int cx(Box b) {
        return b.x() + b.w() / 2;
    }

    private static int cy(Box b) {
        return b.y() + b.h() / 2;
    }

    /** Holds a card over a place of another column. */
    private static void holdOver(Scene s, KanbanComponent<String> c, int col, int row, int toCol, int atY) {
        Box from = BenchAccess.cardBox(c, col, row);
        Box to = BenchAccess.columnBox(c, toCol);
        s.hold(cx(from), cy(from), cx(to), atY);
    }

    @Override
    public void states(Stage stage) {
        stage.state("cards with items", 240, 110, s -> s.add(new KanbanComponent<String>(new Box("k", 6, 6, 228, 98), v -> v)
                .column("To craft", java.util.List.of("diamond_sword", "apple", "oak_sapling"))
                .column("Done", java.util.List.of("stone"))
                .iconArt(v -> ItemView.of("minecraft:" + v))));
        stage.state("cards with covers", 240, 150, s -> s.add(new KanbanComponent<String>(new Box("k", 6, 6, 228, 138), v -> v)
                .column("Ideas", java.util.List.of("Spawn", "Night"))
                .column("Built", java.util.List.of("Snow"))
                .cover(v -> v.equals("Spawn") ? Shots.day() : v.equals("Night") ? Shots.night() : Shots.snow(), 30)));
        stage.state("at rest", W, H, s -> {
            KanbanComponent<String> c = s.add(board(null));
        });

        stage.state("two lines and limits", W, H, s -> {
            KanbanComponent<String> c = s.add(board(null).subtitle(v -> v.length() + " points").limit(1, 2).limit(2, 5));
        });

        stage.state("hover card", W, H, s -> {
            KanbanComponent<String> c = s.add(board(null));
            Box b = BenchAccess.cardBox(c, 0, 1);
            s.move(cx(b), cy(b));
        });

        stage.state("click selects", W, H, s -> {
            KanbanComponent<String> c = s.add(board(null));
            Box b = BenchAccess.cardBox(c, 1, 0);
            s.click(cx(b), cy(b));
            s.move(-100, -100);
        });

        stage.state("double click opens", W, H, s -> {
            List<String> opened = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(null).onActivate(opened::add));
            Box b = BenchAccess.cardBox(c, 0, 2);
            s.click(cx(b), cy(b));
            s.click(cx(b), cy(b));
            s.move(-100, -100);
        });

        stage.state("lifted over another column", W, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(heard));
            holdOver(s, c, 0, 0, 1, BenchAccess.cardBox(c, 1, 1).y() + 3);
            s.settle(200);
        });

        stage.state("slide half way", W, H, s -> {
            KanbanComponent<String> c = s.add(board(null));
            holdOver(s, c, 0, 0, 1, BenchAccess.cardBox(c, 1, 1).y() + 3);
            s.settle(0);
        });

        stage.state("reduced motion does not slide", W, H, s -> {
            KanbanComponent<String> c = s.add(board(null));
            s.settle(0);
            HostState.reducedMotion(true);
            try {
                holdOver(s, c, 0, 0, 1, BenchAccess.cardBox(c, 1, 1).y() + 3);
            } finally {
                HostState.reducedMotion(false);
            }
        });

        stage.state("drop moves and calls back once", W, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(heard));
            Box from = BenchAccess.cardBox(c, 0, 0);
            Box to = BenchAccess.columnBox(c, 1);
            int y = BenchAccess.cardBox(c, 1, 1).y() + 3;
            s.drag(cx(from), cy(from), cx(to), y);
            s.move(-100, -100);
        });

        stage.state("drop where it began tells nothing", W, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(heard));
            Box from = BenchAccess.cardBox(c, 0, 1);
            s.hold(cx(from), cy(from), cx(from) + 20, cy(from) + 30);
            s.hold(cx(from) + 20, cy(from) + 30, cx(from), cy(from));
            s.release(cx(from), cy(from));
            s.move(-100, -100);
        });

        stage.state("reorder inside a column", W, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(heard));
            Box from = BenchAccess.cardBox(c, 0, 0);
            Box to = BenchAccess.cardBox(c, 0, 2);
            s.drag(cx(from), cy(from), cx(to), cy(to));
            s.move(-100, -100);
        });

        stage.state("escape puts it back", W, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(heard));
            holdOver(s, c, 0, 0, 1, BenchAccess.cardBox(c, 1, 0).y() + 3);
            s.key(InputKey.ESCAPE);
            Box to = BenchAccess.columnBox(c, 1);
            s.release(cx(to), 40);
        });

        stage.state("full column refuses and dims", W, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(heard).limit(1, 2));
            holdOver(s, c, 0, 0, 1, BenchAccess.cardBox(c, 1, 0).y() + 3);
            s.settle(100);
        });

        stage.state("a refused drop goes back", W, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(heard).limit(1, 2));
            Box from = BenchAccess.cardBox(c, 0, 0);
            Box to = BenchAccess.columnBox(c, 1);
            s.drag(cx(from), cy(from), cx(to), to.y() + 30);
            s.move(-100, -100);
            s.settle(0);
        });

        stage.state("own rule refuses a column", W, H, s -> {
            KanbanComponent<String> c = s.add(board(null).canDrop((v, a, b) -> b != 2));
            holdOver(s, c, 0, 1, 2, 40);
            s.settle(100);
        });

        stage.state("empty column takes a card", W, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(heard));
            c.setItems(2, List.of());
            Box from = BenchAccess.cardBox(c, 1, 0);
            Box to = BenchAccess.columnBox(c, 2);
            s.hold(cx(from), cy(from), cx(to), to.y() + 22);
            s.settle(200);
        });

        stage.state("auto scroll at the bottom edge", W, H, s -> {
            KanbanComponent<String> c = new KanbanComponent<String>(new Box("k", 6, 6, W - 12, H - 12), v -> v)
                    .column("Long", numbered(30)).column("Other", DOING);
            s.add(c);
            Box from = BenchAccess.cardBox(c, 0, 0);
            s.hold(cx(from), cy(from), cx(from), H - 10);
            s.waitMs(700);
            s.settle(0);
        });

        stage.state("auto scroll sideways", 150, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = new KanbanComponent<String>(new Box("k", 6, 6, 138, H - 12), v -> v)
                    .column("A", numbered(3)).column("B", DOING).column("C", DONE).column("D", TODO).minColumnWidth(80)
                    .onMove((v, a, b, e, f) -> heard.add(v));
            s.add(c);
            Box from = BenchAccess.cardBox(c, 0, 0);
            s.hold(cx(from), cy(from), 140, cy(from));
            s.waitMs(800);
            s.settle(0);
        });

        stage.state("wheel and bar", W, H, s -> {
            KanbanComponent<String> c = new KanbanComponent<String>(new Box("k", 6, 6, W - 12, H - 12), v -> v)
                    .column("Long", numbered(30)).column("Other", DOING);
            s.add(c);
            Box b = BenchAccess.cardBox(c, 0, 0);
            s.scroll(cx(b), cy(b), -2);
            Box col = BenchAccess.columnBox(c, 0);
            s.hold(col.right() - 4, col.y() + 30, col.right() - 4, col.bottom());
            s.release(col.right() - 4, col.bottom());
            s.move(-100, -100);
        });

        stage.state("keys walk the cards", W, H, s -> {
            KanbanComponent<String> c = s.add(board(null));
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.RIGHT);
            s.key(InputKey.END);
        });

        stage.state("ctrl arrows move the card", W, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(heard));
            s.focus(c);
            s.key(InputKey.DOWN);
            s.held(true, false);
            s.key(InputKey.RIGHT);
            s.key(InputKey.DOWN);
            s.key(InputKey.LEFT);
            s.key(InputKey.LEFT);
            s.key(InputKey.HOME);
            s.key(InputKey.UP);
            s.held(false, false);
        });

        stage.state("a key refused flashes the count", W, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(heard).limit(1, 2));
            s.focus(c);
            s.key(InputKey.DOWN);
            s.held(true, false);
            s.key(InputKey.RIGHT);
            s.held(false, false);
            s.settle(100);
        });

        stage.state("long words cut, tooltip whole", W, H, s -> {
            String name = "A really very long name for a card on this board";
            KanbanComponent<String> c = new KanbanComponent<String>(new Box("k", 6, 6, W - 12, H - 12), v -> v)
                    .column("A column with a very long title indeed", List.of(name)).column("B", DOING);
            s.add(c);
            Box b = BenchAccess.cardBox(c, 0, 0);
            s.move(cx(b), cy(b));
            s.settle(700);
        });

        stage.state("empty board and no columns", W, 60, s -> {
            KanbanComponent<String> c = new KanbanComponent<String>(new Box("a", 6, 6, 160, 48), v -> v)
                    .column("To do", List.of()).column("Done", List.of()).emptyText("Drop here");
            s.add(c);
            KanbanComponent<String> none = s.add(new KanbanComponent<String>(new Box("b", 170, 6, 80, 48), v -> v));
            s.focus(c);
            s.key(InputKey.DOWN);
        });

        stage.state("disabled ignores input", W, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(heard).active(false));
            Box from = BenchAccess.cardBox(c, 0, 0);
            Box to = BenchAccess.columnBox(c, 1);
            s.drag(cx(from), cy(from), cx(to), 40);
        });

        stage.state("hidden mid drag cancels", W, H, s -> {
            List<String> heard = new ArrayList<>();
            KanbanComponent<String> c = s.add(board(heard));
            holdOver(s, c, 0, 0, 1, 40);
            c.visible = false;
            s.tick(1);
            s.release(100, 40);
        });

        stage.state("tiny boxes", W, 40, s -> {
            s.add(new KanbanComponent<String>(new Box("a", 4, 4, 70, 30), v -> v).column("A", TODO).column("B", DOING));
            s.add(new KanbanComponent<String>(new Box("b", 80, 4, 30, 10), v -> v).column("A", TODO));
            s.add(new KanbanComponent<String>(new Box("c", 116, 4, 4, 4), v -> v).column("A", TODO));
        });

        stage.state("50,000 cards, only those in view draw", W, H, s -> {
            int[] asked = {0};
            KanbanComponent<String> c = new KanbanComponent<String>(new Box("k", 6, 6, W - 12, H - 12), v -> {
                asked[0]++;
                return v;
            }).column("Many", numbered(50_000)).column("Few", DONE);
            s.add(c);
            s.waitMs(32);
        });
    }
}
