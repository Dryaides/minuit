package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.Mesh3D;
import dev.dryaides.minuit.Scene3DComponent;
import dev.dryaides.minuit.VoxelModel;
import java.util.Random;

/**
 * The software 3D viewport: models, meshes, the orbit, the reset button, motion and the cost guard.
 */
public final class Scene3DDemo implements Demo {
    private static final int W = 200;
    private static final int H = 150;
    private static final int VX = 6;
    private static final int VY = 6;
    private static final int VW = 188;
    private static final int VH = 124;
    private static final int NOTE_Y = 136;
    private static final int RED = 0xE0402C;
    private static final int GREEN = 0x38C050;
    private static final int BLUE = 0x3C70E8;
    /** The reset button of a view at VX, VY, VW wide: its middle. */
    private static final int RESET_X = VX + VW - 10;
    private static final int RESET_Y = VY + 9;

    // ---- models, shared with the card's demo ----

    static VoxelModel house() {
        int wood = 0x9C6B3C;
        int stone = 0x8A8F96;
        int roof = Mesh3D.tone(Mesh3D.Tone.PRIMARY);
        int glass = 0x8FD4E8;
        int door = 0x5A3A20;
        VoxelModel m = new VoxelModel(9, 8, 9);
        m.fill(0, 0, 0, 8, 0, 8, stone);
        m.fill(1, 1, 1, 7, 3, 7, wood);
        m.fill(2, 1, 2, 6, 3, 6, 0);
        for (int y = 1; y <= 3; y++) {
            for (int z = 2; z <= 6; z++) {
                for (int x = 2; x <= 6; x++) {
                    m.clear(x, y, z);
                }
            }
        }
        m.clear(4, 1, 7).clear(4, 2, 7);
        m.set(4, 1, 7, door).set(4, 2, 7, door);
        m.set(2, 2, 7, glass).set(6, 2, 7, glass);
        m.set(7, 2, 4, glass).set(1, 2, 4, glass);
        for (int level = 0; level < 4; level++) {
            m.fill(level, 4 + level, level, 8 - level, 4 + level, 8 - level, roof);
            if (level < 3) {
                m.fill(level + 1, 4 + level, level + 1, 7 - level, 4 + level, 7 - level, roof);
            }
        }
        return m;
    }

    static VoxelModel tree() {
        VoxelModel m = new VoxelModel(7, 9, 7);
        int bark = 0x6B4A2C;
        int leaf = Mesh3D.tone(Mesh3D.Tone.GOOD);
        m.fill(3, 0, 3, 3, 4, 3, bark);
        m.fill(1, 4, 1, 5, 6, 5, leaf);
        m.fill(2, 7, 2, 4, 7, 4, leaf);
        m.set(3, 8, 3, leaf);
        m.clear(1, 4, 1).clear(5, 4, 1).clear(1, 4, 5).clear(5, 4, 5);
        return m;
    }

    static VoxelModel crate() {
        int edge = 0x7A5230;
        int board = 0xB08850;
        VoxelModel m = new VoxelModel(5, 5, 5).fill(0, 0, 0, 4, 4, 4, board);
        for (int a = 0; a < 5; a++) {
            for (int b = 0; b < 2; b++) {
                int c = b * 4;
                m.set(a, c, 0, edge).set(a, c, 4, edge).set(0, c, a, edge).set(4, c, a, edge);
                m.set(0, a, c, edge).set(4, a, c, edge);
            }
        }
        return m;
    }

    static VoxelModel gem() {
        VoxelModel m = new VoxelModel(7, 7, 7);
        int face = Mesh3D.tone(Mesh3D.Tone.ACCENT);
        for (int y = 0; y < 7; y++) {
            for (int z = 0; z < 7; z++) {
                for (int x = 0; x < 7; x++) {
                    if (Math.abs(x - 3) + Math.abs(y - 3) + Math.abs(z - 3) <= 3) {
                        m.set(x, y, z, face);
                    }
                }
            }
        }
        return m;
    }

    /** A noisy island: colours are random per cube, so faces do not merge. The heavy case. */
    static VoxelModel island(int side, long seed) {
        Random random = new Random(seed);
        int height = Math.max(3, side / 2);
        VoxelModel m = new VoxelModel(side, height, side);
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                double top = height * (0.45D + 0.28D * Math.sin(x * 0.45D) * Math.cos(z * 0.38D));
                for (int y = 0; y < top && y < height; y++) {
                    int base = y + 1 >= top ? 0x48A838 : 0x8A6A44;
                    m.set(x, y, z, 0xFF000000 | (base + random.nextInt(0x10) * 0x010101));
                }
            }
        }
        return m;
    }

    /** Boxes in one mesh, one colour each. */
    private static final class Boxes {
        final Mesh3D mesh = new Mesh3D();

        Boxes add(float x0, float y0, float z0, float x1, float y1, float z1, int color) {
            mesh.box(x0, y0, z0, x1, y1, z1, color);
            return this;
        }
    }

    private static Boxes overlapping() {
        return new Boxes()
                .add(-2F, -1F, -2F, 1F, 1F, 1F, RED)
                .add(-1F, -2F, -1F, 2F, 0.5F, 2F, GREEN)
                .add(-0.5F, -0.5F, -3F, 0.5F, 2.5F, 3F, BLUE);
    }

    /** Three bars round a corner: from the diagonal each lies over the next, so no order of the three is right. */
    private static Boxes cyclic() {
        float t = 0.4F;
        return new Boxes()
                .add(-1F, 1F - t, 0F - t, 4F, 1F + t, 0F + t, RED)
                .add(0F - t, -1F, 1F - t, 0F + t, 4F, 1F + t, GREEN)
                .add(1F - t, 0F - t, -1F, 1F + t, 0F + t, 4F, BLUE);
    }

    /** Faces on one plane, overlapping, and two faces a hair apart: first added wins the tie, in every frame. */
    private static Boxes coplanar() {
        return new Boxes()
                .add(-2F, -1F, -2F, 1F, 1F, 1F, RED)
                .add(-1F, -1F, -1F, 2F, 1F, 2F, GREEN)
                .add(-2F, 1.02F, -2F, 2F, 1.3F, 0F, BLUE);
    }

    private static void minis(Scene s, Mesh3D mesh, int columns, int size) {
        int[] yaws = {20, 65, 110, 155, 200, 245, 290, 335};
        int[] pitches = {25, -25, 50, 10, 25, -25, 50, 10};
        for (int i = 0; i < 8; i++) {
            int at = i;
            s.add(new Scene3DComponent(new Box("m" + i, 6 + (at % columns) * (size + 2), 6 + (at / columns) * (size + 2), size, size))
                    .mesh(mesh).view(yaws[i], pitches[i]).resetButton(false));
        }
    }

    private static void note(Scene s, int y, String... line) {
        s.paint((d, t) -> {
            for (int i = 0; i < line.length; i++) {
                d.textFit(line[i], 6, y + i * 10, s.panel().width() - 12, t.palette().text());
            }
        });
    }

    private static Scene3DComponent view(Scene s, VoxelModel model) {
        return s.add(new Scene3DComponent(new Box("v", VX, VY, VW, VH)).model(model));
    }

    @Override
    public void states(Stage stage) {
        pictures(stage);
        correctness(stage);
        orbit(stage);
        motion(stage);
        edges(stage);
        cost(stage);
    }

    private void pictures(Stage stage) {
        stage.state("voxel_house", W, H, s -> {
            VoxelModel house = house();
            Scene3DComponent v = view(s, house);
            s.settle(0);
            s.waitMs(16);
        });

        stage.state("solid_block_has_six_faces", W, H, s -> {
            VoxelModel block = new VoxelModel(8, 8, 8).fill(0, 0, 0, 7, 7, 7, 0xB08850);
            Scene3DComponent v = view(s, block);
            s.waitMs(16);
        });

        stage.state("per_face_colours", W, H, s -> {
            int[] faces = new int[Mesh3D.FACES];
            faces[Mesh3D.EAST] = 0xE0402C;
            faces[Mesh3D.WEST] = 0x38C050;
            faces[Mesh3D.UP] = 0xF0D030;
            faces[Mesh3D.DOWN] = 0x808890;
            faces[Mesh3D.SOUTH] = 0x3C70E8;
            faces[Mesh3D.NORTH] = 0xE060D0;
            VoxelModel cube = new VoxelModel(3, 3, 3);
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    for (int x = 0; x < 3; x++) {
                        cube.set(x, y, z, faces);
                    }
                }
            }
            s.add(new Scene3DComponent(new Box("a", 6, 6, 92, 124)).model(cube).view(35, 25));
            s.add(new Scene3DComponent(new Box("b", 102, 6, 92, 124)).model(cube).view(215, -25));
            note(s, NOTE_Y, "same cube from opposite corners");
        });

        stage.state("perspective_and_isometric", W, H, s -> {
            VoxelModel crate = crate();
            Scene3DComponent a = s.add(new Scene3DComponent(new Box("a", 6, 6, 92, 124)).model(crate).view(45, 35.264F));
            Scene3DComponent b = s.add(new Scene3DComponent(new Box("b", 102, 6, 92, 124)).model(crate).isometric());
            note(s, NOTE_Y, "perspective, isometric");
        });

        stage.state("theme_tones", W, H, s -> {
            Mesh3D mesh = new Mesh3D();
            Mesh3D.Tone[] tones = {Mesh3D.Tone.PRIMARY, Mesh3D.Tone.ACCENT, Mesh3D.Tone.GOOD, Mesh3D.Tone.WARN, Mesh3D.Tone.BAD, Mesh3D.Tone.RAISED};
            for (int i = 0; i < tones.length; i++) {
                float cx = -3.3F + (i % 3) * 3.3F;
                float cz = i < 3 ? -1.6F : 1.6F;
                mesh.box(cx - 1F, 0F, cz - 1F, cx + 1F, 2F, cz + 1F, Mesh3D.tone(tones[i]));
            }
            s.add(new Scene3DComponent(new Box("v", VX, VY, VW, VH)).mesh(mesh).floor(Scene3DComponent.Floor.GRID).view(20, 30));
            note(s, NOTE_Y, "colours come from the theme");
        });

        stage.state("floor_grid_and_axes", W, H, s -> {
            Scene3DComponent v = s.add(new Scene3DComponent(new Box("v", VX, VY, VW, VH)).model(tree())
                    .floor(Scene3DComponent.Floor.GRID).axes(true).view(30, 22));
            s.waitMs(16);
            note(s, NOTE_Y, "floor passes behind the model");
        });

        stage.state("edges_on_rotated_boxes", W, H, s -> {
            Mesh3D mesh = new Mesh3D();
            mesh.box(-3F, -0.25F, -3F, 3F, 0F, 3F, Mesh3D.tone(Mesh3D.Tone.TRACK));
            mesh.move(0F, 1F, 0F).turnY(30F).turnX(20F)
                    .box(-1.5F, -1F, -1F, 1.5F, 1F, 1F, 0xB08850).boxEdges(-1.5F, -1F, -1F, 1.5F, 1F, 1F, 0x402810);
            mesh.unmoved().move(1.2F, 2.1F, 0.5F).turnY(-25F).turnZ(35F)
                    .box(-1F, -1F, -1F, 1F, 1F, 1F, 0x5088C0).boxEdges(-1F, -1F, -1F, 1F, 1F, 1F, 0x102840).unmoved();
            s.add(new Scene3DComponent(new Box("v", VX, VY, VW, VH)).mesh(mesh).view(25, 28));
            note(s, NOTE_Y, "they cross; edges hide too");
        });
    }

    private void correctness(Stage stage) {
        stage.state("overlap_from_8_angles", W, 150, s -> {
            minis(s, overlapping().mesh, 4, 45);
            note(s, 104, "three boxes through each other, 8 angles");
        });

        stage.state("cycle_no_sort_can_solve", W, 150, s -> {
            minis(s, cyclic().mesh, 4, 45);
            note(s, 104, "each bar lies over the next, 8 angles");
        });

        stage.state("cycle_from_the_diagonal", W, H, s -> {
            Boxes boxes = cyclic();
            s.add(new Scene3DComponent(new Box("v", VX, VY, VW, VH)).mesh(boxes.mesh).view(45, 35.264F));
            note(s, NOTE_Y, "a cycle: no draw order is right");
        });

        stage.state("coplanar_no_z_fighting", W, 150, s -> {
            minis(s, coplanar().mesh, 4, 45);
            note(s, 104, "shared planes and a 0.02 gap, 8 angles");
        });

        stage.state("merged_faces_leave_no_cracks", W, H, s -> {
            // A big face beside many small ones: every corner of a small face lands on the big face's edge.
            VoxelModel m = new VoxelModel(16, 4, 16).fill(0, 0, 0, 15, 3, 15, 0xB08850);
            for (int z = 0; z < 16; z += 2) {
                for (int x = 0; x < 16; x += 2) {
                    m.set(x, 3, z, 0x6A4A28);
                }
            }
            Scene3DComponent v = view(s, m);
            v.view(33, 41);
            note(s, NOTE_Y, "big and small faces meet with no cracks");
        });

        stage.state("camera_inside_the_scene", W, H, s -> {
            Scene3DComponent v = view(s, house());
            for (int i = 0; i < 20; i++) {
                s.scroll(100, 70, 1);
            }
            s.move(-100, -100);
            s.waitMs(16);
            note(s, NOTE_Y, "near plane cuts faces cleanly");
        });
    }

    private void orbit(Stage stage) {
        stage.state("dragged", W, H, s -> {
            int[] heard = new int[1];
            Scene3DComponent v = view(s, house()).onViewChange(() -> heard[0]++);
            s.hold(100, 70, 60, 90);
        });

        stage.state("drag_goes_on_outside_the_box", W, H, s -> {
            ButtonComponent next = s.add(new ButtonComponent(new Box("n", VX, 132, 60, 14), "Beside", () -> { }));
            Scene3DComponent v = s.add(new Scene3DComponent(new Box("v", VX, VY, VW, 120)).model(house()));
            s.drag(100, 70, 340, 70);
            s.move(-100, -100);
        });

        stage.state("pitch_stops_at_the_pole", W, H, s -> {
            Scene3DComponent v = view(s, house());
            s.drag(100, 20, 100, 400);
            s.move(-100, -100);
        });

        stage.state("zoomed_in_stays_in_its_box", W, H, s -> {
            s.add(new ButtonComponent(new Box("n", VX, 132, 60, 14), "Beside", () -> { }));
            Scene3DComponent v = s.add(new Scene3DComponent(new Box("v", 30, 20, 140, 90)).model(house()));
            for (int i = 0; i < 6; i++) {
                s.scroll(100, 70, 1);
            }
            s.move(-100, -100);
        });

        stage.state("wheel_limits_let_it_go", W, H, s -> {
            Scene3DComponent v = view(s, tree());
            for (int i = 0; i < 40; i++) {
                s.scroll(100, 70, -1);
            }
            v.mouseScrolled(100, 70, -1);
            v.mouseScrolled(100, 70, 1);
            v.mouseScrolled(2, 2, 1);
            s.move(-100, -100);
            note(s, NOTE_Y, "far limit");
        });

        stage.state("keyboard_focus_and_orbit", W, H, s -> {
            Scene3DComponent v = view(s, house());
            s.key(InputKey.TAB);
            s.key(InputKey.LEFT);
            s.key(InputKey.RIGHT, true);
            s.key(InputKey.DOWN);
            s.type("+");
            s.type("-");
            s.settle(0);
            note(s, NOTE_Y, "Tab, arrows, + and -");
        });

        stage.state("reset_button_hover", W, H, s -> {
            Scene3DComponent v = view(s, house());
            s.move(100, 70);
            s.drag(100, 70, 70, 80);
            s.move(RESET_X, RESET_Y);
            note(s, NOTE_Y, "button shows once the view moved");
        });

        stage.state("reset_half_way", W, H, s -> {
            Scene3DComponent v = view(s, house());
            s.drag(100, 70, 20, 70);
            s.click(RESET_X, RESET_Y);
            s.waitMs(48);
            s.settle(0);
            note(s, NOTE_Y, "half way back to the start");
        });

        stage.state("reset_arrives_and_home_key", W, H, s -> {
            Scene3DComponent v = view(s, house());
            s.drag(100, 70, 20, 90);
            s.scroll(100, 70, 2);
            s.click(RESET_X, RESET_Y);
            s.waitMs(240);
            s.focus(v);
            s.key(InputKey.LEFT);
            s.key(InputKey.HOME);
            s.waitMs(240);
            s.move(-100, -100);
        });
    }

    private void motion(Stage stage) {
        stage.state("auto_rotate_turns_and_drag_pauses", W, H, s -> {
            Scene3DComponent v = view(s, house()).autoRotate(true);
            s.waitMs(160);
            s.hold(100, 70, 100, 70);
            s.waitMs(160);
            s.release(100, 70);
            s.waitMs(160);
            s.move(-100, -100);
            s.settle(0);
        });

        stage.state("auto_rotate_off_under_reduced_motion", W, H, s -> {
            s.paint((d, t) -> BaseComponent.reducedMotion = true);
            Scene3DComponent v = view(s, house()).autoRotate(true);
            s.paint((d, t) -> BaseComponent.reducedMotion = false);
            BaseComponent.reducedMotion = true;
            try {
                s.waitMs(200);
                BaseComponent.reducedMotion = true; // the second paint turned it off for the next state
                s.drag(100, 70, 60, 70);
                s.move(RESET_X, RESET_Y);
                s.press(RESET_X, RESET_Y);
                BaseComponent.reducedMotion = true;
                v.mouseReleased(RESET_X, RESET_Y, 0);
                s.move(-100, -100);
            } finally {
                BaseComponent.reducedMotion = false;
            }
            note(s, NOTE_Y, "still; a reset snaps");
        });

        stage.state("space_pauses", W, H, s -> {
            Scene3DComponent v = view(s, tree()).autoRotate(true);
            s.focus(v);
            s.key(InputKey.SPACE);
            s.waitMs(100);
            s.settle(0);
            note(s, NOTE_Y, "paused with Space");
        });
    }

    private void edges(Stage stage) {
        stage.state("disabled_ignores_input", W, H, s -> {
            int[] heard = new int[1];
            Scene3DComponent v = view(s, house()).autoRotate(true).onViewChange(() -> heard[0]++).active(false);
            s.drag(100, 70, 60, 70);
            s.scroll(100, 70, 1);
            s.focus(v);
            s.key(InputKey.LEFT);
            s.key(InputKey.HOME);
            s.waitMs(100);
            note(s, NOTE_Y, "input and rotation are off");
        });

        stage.state("empty", W, H, s -> {
            Scene3DComponent v = s.add(new Scene3DComponent(new Box("v", VX, VY, VW, VH)).model(new VoxelModel(4, 4, 4)).emptyText("Nothing to show yet"));
            s.drag(100, 70, 60, 70);
            s.scroll(100, 70, 1);
            s.focus(v);
            s.key(InputKey.LEFT);
            Scene3DComponent none = new Scene3DComponent(new Box("n", 0, 0, 50, 50)).mesh(null);
            s.move(-100, -100);
            s.settle(0);
        });

        stage.state("tiny_boxes", W, 44, s -> {
            int[] sides = {3, 6, 10, 16, 24, 36};
            int left = 6;
            for (int side : sides) {
                s.add(new Scene3DComponent(new Box("t" + side, left, 4, side, side)).model(crate()));
                left += side + 4;
            }
            Scene3DComponent wide = s.add(new Scene3DComponent(new Box("w", left, 4, 60, 8)).model(crate()));
            s.drag(left + 10, 6, left + 30, 6);
            s.move(-100, -100);
        });

        stage.state("hidden_takes_nothing", W, 44, s -> {
            Scene3DComponent v = s.add(new Scene3DComponent(new Box("v", VX, 4, 80, 36)).model(crate()));
            v.visible = false;
            s.drag(20, 20, 40, 20);
            s.settle(0);
        });

        stage.state("pixel_size_two", W, H, s -> {
            VoxelModel house = house();
            Scene3DComponent a = s.add(new Scene3DComponent(new Box("a", 6, 6, 92, 124)).model(house));
            Scene3DComponent b = s.add(new Scene3DComponent(new Box("b", 102, 6, 92, 124)).model(house).pixelSize(2));
            s.waitMs(16);
        });
    }

    private void cost(Stage stage) {
        stage.state("idle_and_turned_by_keys", W, H, s -> {
            Scene3DComponent v = view(s, house());
            s.waitMs(320);
            s.move(100, 70);
            s.key(InputKey.TAB);
            s.key(InputKey.LEFT);
            s.settle(0);
            s.move(-100, -100);
        });

        stage.state("rect_budget_coarsens_a_busy_picture", W, H, s -> {
            VoxelModel busy = new VoxelModel(40, 6, 40);
            for (int x = 0; x < 40; x++) {
                for (int z = 0; z < 40; z++) {
                    int mix = x * 31 + z * 17;
                    for (int y = 0, h = 1 + Math.floorMod(x * 7 ^ z * 13, 5); y < h; y++) {
                        busy.set(x, y, z, 0xFF000000 | (40 + mix % 80) << 16 | (120 + mix % 100) << 8 | (40 + mix % 60));
                    }
                }
            }
            s.add(new Scene3DComponent(new Box("small", 6, 6, 92, 124)).model(busy).view(40F, 30F));
            s.add(new Scene3DComponent(new Box("big", 102, 6, 92, 124)).model(busy).pixelSize(2).view(40F, 30F));
            s.waitMs(32);
            s.settle(0);
            note(s, NOTE_Y, "budget, pixel size 2");
        });
    }
}
