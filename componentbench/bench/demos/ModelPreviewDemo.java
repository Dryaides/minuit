package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.GridComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.ModelPreviewComponent;
import dev.dryaides.minuit.VoxelModel;
import java.util.ArrayList;
import java.util.List;

/** The turntable card, driven by scripted input. */
public final class ModelPreviewDemo implements Demo {
    private static final int W = 120;
    private static final int H = 128;
    private static final int CX = 8;
    private static final int CY = 8;
    private static final int CW = 104;
    private static final int CH = 112;

    private static ModelPreviewComponent card(Scene s, int[] presses) {
        return s.add(new ModelPreviewComponent(new Box("c", CX, CY, CW, CH), Scene3DDemo.house(), "Cottage")
                .subtitle("9 x 8 x 9 blocks")
                .onClick(() -> presses[0]++));
    }

    @Override
    public void states(Stage stage) {
        looks(stage);
        input(stage);
        limits(stage);
        together(stage);
    }

    private void looks(Stage stage) {
        stage.state("default", W, H, s -> {
            int[] presses = new int[1];
            ModelPreviewComponent c = card(s, presses);
        });

        stage.state("hover_turns_the_model", W, H, s -> {
            int[] presses = new int[1];
            ModelPreviewComponent c = card(s, presses);
            s.move(60, 60);
            s.waitMs(320);
            s.settle(0);
        });

        stage.state("leaving_brings_it_back", W, H, s -> {
            int[] presses = new int[1];
            ModelPreviewComponent c = card(s, presses);
            s.move(60, 60);
            s.waitMs(320);
            s.move(-100, -100);
            s.waitMs(320);
        });

        stage.state("selected", W, H, s -> {
            int[] presses = new int[1];
            ModelPreviewComponent c = card(s, presses).selected(true);
        });

        stage.state("selected_and_hovered_with_badge", W, H, s -> {
            int[] presses = new int[1];
            ModelPreviewComponent c = card(s, presses).selected(true).badge("New");
            s.move(60, 60);
            s.waitMs(160);
            s.settle(0);
        });

        stage.state("isometric_with_no_disc", W, H, s -> {
            ModelPreviewComponent c = s.add(new ModelPreviewComponent(new Box("c", CX, CY, CW, CH), Scene3DDemo.crate(), "Oak crate")
                    .subtitle("12 planks").turntable(false).isometric());
        });

        stage.state("tones_follow_the_theme", W, H, s -> {
            s.add(new ModelPreviewComponent(new Box("c", CX, CY, CW, CH), Scene3DDemo.gem(), "Accent gem").subtitle("colour from the theme").badge("12 coins"));
        });
    }

    private void input(Stage stage) {
        stage.state("press_held_shows", W, H, s -> {
            int[] presses = new int[1];
            ModelPreviewComponent c = card(s, presses);
            s.press(60, 60);
            s.settle(0);
        });

        stage.state("click_presses_once", W, H, s -> {
            int[] presses = new int[1];
            ModelPreviewComponent c = card(s, presses);
            s.click(60, 60);
            s.move(-100, -100);
            s.settle(0);
        });

        stage.state("drag_turns_and_presses_nothing", W, H, s -> {
            int[] presses = new int[1];
            ModelPreviewComponent c = card(s, presses);
            s.hold(60, 60, 20, 60);
            s.waitMs(160);
            s.release(20, 60);
            s.settle(0);
        });

        stage.state("released_off_the_card_presses_nothing", W, H, s -> {
            int[] presses = new int[1];
            card(s, presses);
            s.press(60, 60);
            s.release(-20, -20);
        });

        stage.state("keyboard_focus_enter_and_arrows", W, H, s -> {
            int[] presses = new int[1];
            ModelPreviewComponent c = card(s, presses).spin(ModelPreviewComponent.Spin.OFF);
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
            s.key(InputKey.SPACE);
            s.key(InputKey.LEFT);
            s.key(InputKey.HOME);
            s.waitMs(240);
            s.settle(0);
        });

        stage.state("wheel_is_left_to_the_list", W, H, s -> {
            int[] presses = new int[1];
            ModelPreviewComponent c = card(s, presses);
            c.mouseScrolled(60, 60, 1);
        });

        stage.state("reduced_motion_never_turns", W, H, s -> {
            int[] presses = new int[1];
            s.paint((d, t) -> HostState.reducedMotion(true));
            ModelPreviewComponent c = card(s, presses).spin(ModelPreviewComponent.Spin.ALWAYS);
            s.paint((d, t) -> HostState.reducedMotion(false));
            HostState.reducedMotion(true);
            try {
                s.move(60, 60);
                s.waitMs(320);
            } finally {
                HostState.reducedMotion(false);
            }
        });
    }

    private void limits(Stage stage) {
        stage.state("disabled_ignores_input", W, H, s -> {
            int[] presses = new int[1];
            ModelPreviewComponent c = card(s, presses).selected(true).badge("Sold").active(false);
            s.click(60, 60);
            s.drag(60, 60, 20, 60);
            s.focus(c);
            s.key(InputKey.ENTER);
            s.key(InputKey.LEFT);
            s.waitMs(160);
        });

        stage.state("very_long_text", 280, H, s -> {
            String longTitle = "An extremely long model name that cannot fit on any card at all";
            ModelPreviewComponent c = s.add(new ModelPreviewComponent(new Box("c", CX, CY, CW, CH), Scene3DDemo.tree(), longTitle)
                    .subtitle("And a very long second line that has to be cut as well")
                    .badge("A very long badge that would pass the edge"));
            s.move(60, 100);
            s.settle(1500);
        });

        stage.state("empty_model", W, H, s -> {
            int[] presses = new int[1];
            ModelPreviewComponent c = s.add(new ModelPreviewComponent(new Box("c", CX, CY, CW, CH), null, "Nothing yet")
                    .subtitle("Pick a model").emptyText("No model").onClick(() -> presses[0]++));
            s.drag(60, 60, 20, 60);
            s.key(InputKey.TAB);
            s.key(InputKey.LEFT);
            s.click(60, 60);
            s.move(-100, -100);
            s.settle(0);
        });

        stage.state("tiny_and_short", 200, 76, s -> {
            int[][] boxes = {{4, 4, 6, 6}, {14, 4, 16, 16}, {34, 4, 30, 30}, {68, 4, 40, 52}, {112, 4, 40, 58}, {156, 4, 40, 68}};
            for (int[] b : boxes) {
                ModelPreviewComponent c = s.add(new ModelPreviewComponent(new Box("t" + b[2], b[0], b[1], b[2], b[3]), Scene3DDemo.crate(), "Crate")
                        .subtitle("Line two"));
            }
        });

        stage.state("no_text_at_all", W, H, s -> {
            ModelPreviewComponent c = s.add(new ModelPreviewComponent(new Box("c", CX, CY, CW, CH), Scene3DDemo.gem(), null));
        });
    }

    private void together(Stage stage) {
        stage.state("a_row_of_cards_hover_one", 300, 112, s -> {
            ModelPreviewComponent a = s.add(new ModelPreviewComponent(new Box("a", 6, 6, 68, 100), Scene3DDemo.house(), "Cottage").subtitle("9 x 8 x 9"));
            ModelPreviewComponent b = s.add(new ModelPreviewComponent(new Box("b", 78, 6, 68, 100), Scene3DDemo.tree(), "Oak tree").subtitle("Grows").badge("New"));
            ModelPreviewComponent c = s.add(new ModelPreviewComponent(new Box("c", 150, 6, 68, 100), Scene3DDemo.crate(), "Crate").subtitle("12 planks").selected(true));
            ModelPreviewComponent e = s.add(new ModelPreviewComponent(new Box("d", 222, 6, 68, 100), Scene3DDemo.gem(), "Gem").subtitle("Sold out").active(false));
            s.move(112, 50);
            s.waitMs(240);
            s.waitMs(160);
            s.settle(0);
        });

        stage.state("cells_in_a_grid", 240, 66, s -> {
            List<VoxelModel> models = new ArrayList<>(List.of(Scene3DDemo.house(), Scene3DDemo.tree(), Scene3DDemo.crate(), Scene3DDemo.gem()));
            VoxelModel[] picked = new VoxelModel[1];
            GridComponent<VoxelModel> grid = s.add(new GridComponent<>(new Box("g", 6, 6, 228, 0), 4, 54, 4, 4, models,
                    ModelPreviewComponent.cells(m -> m, m -> m.sizeX() + " x " + m.sizeY()), m -> picked[0] = m));
            grid.selected(models.get(2));
            Box cell = grid.cell(1);
            s.move(cell.x() + 10, cell.y() + 20);
            s.waitMs(160);
            s.click(cell.x() + 10, cell.y() + 20);
            s.settle(0);
        });
    }
}
