package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.DialogComponent;
import dev.dryaides.minuit.DropdownComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.Slide;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.TextFieldComponent;
import java.util.List;

/**
 * The modal, driven by scripted input.
 */
public final class DialogDemo implements Demo {
    private static final int W = 300;
    private static final int H = 176;
    private static final Box AREA = new Box("area", 0, 0, W, H);
    private static final String SHORT = "This world will be saved and you will go back to the title screen.";
    private static final String LONG = String.join(" ", java.util.Collections.nCopies(14,
            "The world is kept on disk until you delete it, and nothing here can bring it back later."));
    private static final int SETTLED = 260;

    /** A scene with a button and a slider behind the dialog, and the line of values beneath. */
    private static final class Rig {
        final Scene s;
        int behind;
        String log = "";
        String note = "";

        Rig(Scene s) {
            this.s = s;
            s.fixture(new ButtonComponent(new Box("behind", 8, 8, 90, 20), "Settings", () -> behind++));
            s.fixture(new SliderComponent(new Box("level", 106, 8, 120, 16), 0, 100, 40, v -> behind++));
            s.caption(() -> note.isEmpty() ? log : note);
        }

        DialogComponent confirm() {
            return DialogComponent.confirm(AREA, "Leave this world?", SHORT, "Leave", "Stay",
                    () -> log += "Left the world. ", () -> log += "Stayed. ");
        }

        DialogComponent open(DialogComponent dialog) {
            s.add(dialog);
            dialog.open(s.panel());
            s.waitMs(SETTLED);
            return dialog;
        }

        Runnable logs(String word) {
            return () -> log += word + " ";
        }
    }

    private static int centreX(Box b) { return b.x() + b.w() / 2; }

    /** The middle of an action in the row at the bottom right, counted from the right. */
    private static int[] action(DialogComponent dialog, int fromRight, int buttonWidth) {
        Box c = BenchAccess.cardBox(dialog);
        return new int[]{c.right() - 10 - buttonWidth / 2 - fromRight * (buttonWidth + 6), c.bottom() - 20};
    }

    @Override
    public void states(Stage stage) {
        visual(stage);
        keyboard(stage);
        mouse(stage);
        edges(stage);
        motion(stage);
    }

    private void visual(Stage stage) {
        stage.state("default", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
        });
        stage.state("hover confirm", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            int[] at = action(d, 0, 56);
            s.move(at[0], at[1]);
        });
        stage.state("hover behind stays dim", W, H, s -> {
            Rig r = new Rig(s);
            r.open(r.confirm());
            s.move(50, 18);
        });
        stage.state("focused by tab", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            s.key(InputKey.TAB);
        });
        stage.state("disabled blocks", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            d.active = false;
            s.key(InputKey.ENTER);
            s.key(InputKey.ESCAPE);
            s.click(centreX(BenchAccess.cardBox(d)), BenchAccess.cardBox(d).bottom() - 20);
        });
        stage.state("one action off", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            d.actionActive(1, false);
            s.key(InputKey.ENTER);
        });
        stage.state("destructive", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(DialogComponent.confirmDestructive(AREA, "Delete this world?",
                    "This cannot be undone.", "Delete", "Keep", r.logs("delete"), r.logs("keep")));
        });
        stage.state("widgets under the card", W, H, s -> {
            Rig r = new Rig(s);
            s.add(new ButtonComponent(new Box("under", 70, 50, 160, 40), "Under the card", () -> r.behind++));
            r.open(r.confirm());
            s.click(150, 70);
        });
        stage.state("alert", W, H, s -> {
            Rig r = new Rig(s);
            r.open(DialogComponent.alert(AREA, "Saved", "Your settings are saved.", "OK", r.logs("ok")));
        });
        stage.state("three actions and content", W, H, s -> {
            Rig r = new Rig(s);
            String[] name = {""};
            TextFieldComponent field = new TextFieldComponent(new Box("name", 0, 0, 200, 16), "", v -> name[0] = v);
            DialogComponent d = new DialogComponent(AREA).title("Save as")
                    .message("Pick a name for this layout.").content(field)
                    .action("Cancel", ButtonComponent.Role.SECONDARY, r.logs("cancel"))
                    .action("Discard", ButtonComponent.Role.DESTRUCTIVE, r.logs("discard"))
                    .action("Save", ButtonComponent.Role.PRIMARY, () -> r.log += "save:" + name[0] + " ")
                    .cancelAction(0).defaultAction(2);
            r.open(d);
            Box c = BenchAccess.cardBox(d);
            s.click(c.x() + 30, field.y + 8);
            s.type("Base");
            StringBuilder seen = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                s.key(InputKey.TAB);
                seen.append(d.focusedAction()).append(' ');
            }
            s.type("!");
            s.key(InputKey.TAB);
        });
    }

    private void keyboard(Stage stage) {
        stage.state("enter runs default", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            s.key(InputKey.ENTER);
        });
        stage.state("escape cancels", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            s.key(InputKey.ESCAPE);
        });
        stage.state("tab trapped", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            StringBuilder seen = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                s.key(InputKey.TAB);
                seen.append(d.focusedAction());
            }
            s.key(InputKey.TAB, true);
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
        });
        stage.state("destructive reached by tab", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(DialogComponent.confirmDestructive(AREA, "Delete this world?",
                    "This cannot be undone.", "Delete", "Keep", r.logs("delete"), r.logs("keep")));
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
        });
        stage.state("enter in a field runs default", W, H, s -> {
            Rig r = new Rig(s);
            String[] name = {""};
            TextFieldComponent field = new TextFieldComponent(new Box("name", 0, 0, 200, 16), "", v -> name[0] = v);
            DialogComponent d = r.open(new DialogComponent(AREA).title("Rename").content(field)
                    .action("Cancel", ButtonComponent.Role.SECONDARY, r.logs("cancel"))
                    .action("Rename", ButtonComponent.Role.PRIMARY, () -> r.log += "rename:" + name[0] + " ")
                    .cancelAction(0).defaultAction(1));
            s.click(field.x + 20, field.y + 8);
            s.type("Moss");
            s.key(InputKey.ENTER);
        });
        stage.state("focus goes back on close", W, H, s -> {
            Rig r = new Rig(s);
            BaseComponent before = s.panel().widgets().get(0);
            s.key(InputKey.TAB);
            DialogComponent d = r.open(r.confirm());
            s.key(InputKey.ESCAPE);
            s.key(InputKey.ENTER);
        });
    }

    private void mouse(Stage stage) {
        stage.state("click on action", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            int[] at = action(d, 0, 56);
            s.click(at[0], at[1]);
        });
        stage.state("backdrop off", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            s.click(50, 18);
            s.scroll(150, 18, 1);
            s.drag(120, 16, 200, 16);
            s.type("x");
        });
        stage.state("backdrop on", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm().closeOnBackdrop(true));
            s.click(50, 18);
            s.click(50, 18);
        });
        stage.state("backdrop right click", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm().closeOnBackdrop(true));
            s.rightClick(50, 18);
        });
        stage.state("action tooltip", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm().actionTooltip(1, "Leave", "Saves first"));
            int[] at = action(d, 0, 56);
            s.move(at[0], at[1]);
            s.settle(1500);
        });
        stage.state("no tooltip from behind", W, H, s -> {
            Rig r = new Rig(s);
            s.panel().widgets().get(0).tooltip("Must not show");
            r.open(r.confirm());
            s.move(50, 18);
            s.settle(1500);
        });
    }

    private void edges(Stage stage) {
        stage.state("long text scrolled", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(DialogComponent.alert(AREA, "About this", LONG, "OK", r.logs("ok")));
            Box c = BenchAccess.cardBox(d);
            s.scroll(c.x() + 40, c.y() + 50, -3);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.ENTER);
            d.open(s.panel());
            s.waitMs(SETTLED);
            s.scroll(c.x() + 40, c.y() + 50, -3);
        });
        stage.state("long text top", W, H, s -> {
            Rig r = new Rig(s);
            r.open(DialogComponent.alert(AREA, "About this", LONG, "OK", r.logs("ok")));
        });
        stage.state("long title and labels", W, H, s -> {
            Rig r = new Rig(s);
            r.open(new DialogComponent(AREA)
                    .title("A very long title that keeps on going until it must wrap and then be cut off, as a title cannot scroll at all")
                    .message("Short.")
                    .action("Cancel and go back to the previous screen", ButtonComponent.Role.SECONDARY, r.logs("a"))
                    .action("Discard every change I made", ButtonComponent.Role.DESTRUCTIVE, r.logs("b"))
                    .action("Save all of them to my disk", ButtonComponent.Role.PRIMARY, r.logs("c")));
        });
        stage.state("unbroken word", W, H, s -> {
            Rig r = new Rig(s);
            r.open(DialogComponent.alert(AREA, "Path", "config/" + "averylongfoldername".repeat(8) + "/file.json",
                    "OK", r.logs("ok")));
        });
        stage.state("title only", W, H, s -> {
            Rig r = new Rig(s);
            r.open(new DialogComponent(AREA).title("Are you sure?")
                    .action("No", ButtonComponent.Role.SECONDARY, r.logs("no"))
                    .action("Yes", ButtonComponent.Role.PRIMARY, r.logs("yes")));
        });
        stage.state("empty text", W, H, s -> {
            Rig r = new Rig(s);
            r.open(new DialogComponent(AREA).action("OK", ButtonComponent.Role.PRIMARY, r.logs("ok")));
        });
        stage.state("tiny area", 150, 90, s -> {
            s.paint((d, t) -> { });
            DialogComponent d = s.add(DialogComponent.confirm(new Box("a", 0, 0, 150, 90), "Leave this world?", LONG,
                    "Leave", "Stay", null, null));
            d.open(s.panel());
            s.waitMs(SETTLED);
            Box c = BenchAccess.cardBox(d);
        });
        stage.state("reopened starts on default", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            s.key(InputKey.TAB);
            s.key(InputKey.ESCAPE);
            d.open(s.panel());
            s.waitMs(SETTLED);
        });
        stage.state("reopened mid close", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            s.key(InputKey.ESCAPE);
            s.waitMs(50);
            d.open(s.panel());
            s.waitMs(SETTLED);
        });
        stage.state("content gets ticks", W, H, s -> {
            Rig r = new Rig(s);
            int[] ticks = {0};
            BaseComponent child = new BaseComponent(new Box("child", 0, 0, 200, 10)) {
                @Override protected void render(dev.dryaides.minuit.Draw d, dev.dryaides.minuit.Theme t, int mx, int my, boolean hovered) {
                    d.text("ticks " + ticks[0], x, y, t.palette().text());
                }
                @Override public void tick() { ticks[0]++; }
            };
            r.open(new DialogComponent(AREA).title("Working").content(child)
                    .action("OK", ButtonComponent.Role.PRIMARY, null));
        });
        stage.state("visible false lets input by", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            d.visible = false;
            s.click(50, 18);
            d.visible = true;
            s.waitMs(50);
            s.key(InputKey.ESCAPE);
        });
        stage.state("wrong setup refused", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.confirm();
            s.add(d);
            s.add(new ButtonComponent(new Box("late", 240, 8, 50, 20), "Late", () -> { }));
            String refused = "";
            try {
                d.open(s.panel());
            } catch (IllegalStateException e) {
                refused = e.getMessage();
            }
            String tooMany = "";
            try {
                r.confirm().action("A", ButtonComponent.Role.PRIMARY, null).action("B", ButtonComponent.Role.PRIMARY, null);
            } catch (IllegalStateException e) {
                tooMany = e.getMessage();
            }
        });
        stage.state("dropdown in content", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent[] holder = new DialogComponent[1];
            int[] picked = {0};
            DropdownComponent drop = new DropdownComponent(new Box("pick", 0, 0, 200, 16), "Difficulty", 50, 200,
                    widget -> holder[0].showOverlay(widget));
            drop.lines = List.of(
                    new DropdownComponent.Entry("Easy", "", "", "", DropdownComponent.State.SELECTABLE, -1F, List.of(), () -> picked[0] = 1),
                    new DropdownComponent.Entry("Hard", "", "", "", DropdownComponent.State.SELECTABLE, -1F, List.of(), () -> picked[0] = 2));
            holder[0] = new DialogComponent(AREA).title("New world").message("Pick how hard it is.").content(drop)
                    .action("Create", ButtonComponent.Role.PRIMARY, r.logs("create"));
            DialogComponent d = r.open(holder[0]);
            s.click(drop.x + 40, drop.y + 8);
            s.key(InputKey.ESCAPE);
            s.click(drop.x + 40, drop.y + 8);
            s.click(drop.x + 20, drop.y + 26);
            s.click(drop.x + 40, drop.y + 8);
        });
        stage.state("dropdown hover row", W, H, s -> {
            Rig r = new Rig(s);
            DropdownComponent[] drop = new DropdownComponent[1];
            DialogComponent d = r.open(dropdownDialog(r, drop));
            s.click(drop[0].x + 40, drop[0].y + 8);
            s.waitMs(SETTLED);
            s.move(drop[0].x + 40, drop[0].y + 34);
            s.settle(200);
        });
        // the pointer sits lower here: a family with a bleed under the button shifts the rows down
        stage.state("dropdown hover row low", W, H, s -> {
            Rig r = new Rig(s);
            DropdownComponent[] drop = new DropdownComponent[1];
            DialogComponent d = r.open(dropdownDialog(r, drop));
            s.click(drop[0].x + 40, drop[0].y + 8);
            s.waitMs(SETTLED);
            s.move(drop[0].x + 40, drop[0].y + 38);
            s.settle(200);
        });
    }

    private static DialogComponent dropdownDialog(Rig r, DropdownComponent[] out) {
        DialogComponent[] holder = new DialogComponent[1];
        DropdownComponent drop = new DropdownComponent(new Box("pick", 0, 0, 200, 16), "Difficulty", 50, 200,
                widget -> holder[0].showOverlay(widget));
        drop.lines = List.of(
                new DropdownComponent.Entry("Easy", "", "", "", DropdownComponent.State.SELECTED, -1F, List.of(), () -> { }),
                new DropdownComponent.Entry("Hard", "", "", "", DropdownComponent.State.SELECTABLE, -1F, List.of(), () -> { }),
                new DropdownComponent.Entry("Locked", "", "", "", DropdownComponent.State.DISABLED, -1F, List.of(), () -> { }));
        holder[0] = new DialogComponent(AREA).title("New world").message("Pick how hard it is.").content(drop)
                .action("Create", ButtonComponent.Role.PRIMARY, r.logs("create"));
        out[0] = drop;
        return holder[0];
    }

    private void motion(Stage stage) {
        stage.state("opening, 50 ms in", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.confirm();
            s.add(d);
            d.open(s.panel());
            s.settle(50);
        });
        stage.state("closing, 50 ms in", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            s.key(InputKey.ESCAPE);
            s.settle(50);
        });
        stage.state("closed after the slide", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.open(r.confirm());
            s.key(InputKey.ESCAPE);
            s.waitMs(SETTLED);
        });
        stage.state("reduced motion snaps", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.confirm();
            s.add(d);
            HostState.reducedMotion(true);
            try {
                d.open(s.panel());
                s.settle(0);
                s.key(InputKey.ESCAPE);
                d.open(s.panel());
            } finally {
                HostState.reducedMotion(false);
            }
        });
        stage.state("slides from the left", W, H, s -> {
            Rig r = new Rig(s);
            DialogComponent d = r.confirm().slideFrom(Slide.LEFT);
            s.add(d);
            d.open(s.panel());
            s.settle(50);
        });
    }
}
