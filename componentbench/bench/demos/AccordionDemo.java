package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.AccordionComponent;
import dev.dryaides.minuit.AccordionComponent.Mode;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.TextMeasure;
import dev.dryaides.minuit.ToggleComponent;
import java.util.ArrayList;
import java.util.List;

/** The accordion, driven by scripted input. */
public final class AccordionDemo implements Demo {
    private static final int W = 200;
    private static final int H = 120;
    private static final int AH = H - 16;
    private static final String A = "Worlds live in the saves folder, one folder for each world.";
    private static final String B = "Open the options, then the video page.";
    private static final String C = "The game keeps three backups for you, and drops the oldest when a fourth is made.";

    private static AccordionComponent stack(Scene s, Mode mode) {
        AccordionComponent c = s.add(new AccordionComponent(new Box("a", 8, 8, W - 16, AH), mode));
        c.item("Where is my world?", A);
        c.item("How do I change the view?", B);
        c.item("Are there backups?", C);
        return c;
    }

    private static final String[] TEXTS = {A, B, C};

    /** A title's top: the stack's top, a title per row above, and the wrapped text of each block open above it. */
    private static int headerY(int row, int... open) {
        int top = 8;
        for (int i = 0; i < row; i++) {
            top += AccordionComponent.HEADER;
            for (int o : open) {
                if (o == i) {
                    top += 3 + TextMeasure.wrapAll(TEXTS[i], W - 16 - 14 - 4).size() * 10 + 3;
                }
            }
        }
        return top;
    }

    private static void clickRow(Scene s, int row, int... open) {
        s.click(8 + 30, headerY(row, open) + 6);
    }

    @Override
    public void states(Stage stage) {
        stage.state("closed", W, 16 + 3 * AccordionComponent.HEADER, s -> stack(s, Mode.SINGLE).fit());

        stage.state("hover title", W, H, s -> {
            AccordionComponent c = stack(s, Mode.SINGLE);
            s.move(8 + 30, headerY(1) + 6);
        });

        stage.state("click opens", W, H, s -> {
            List<String> heard = new ArrayList<>();
            AccordionComponent c = stack(s, Mode.SINGLE).onChange((i, on) -> heard.add(i + ":" + on));
            clickRow(s, 0);
            s.waitMs(300);
        });

        stage.state("press alone does nothing yet", W, H, s -> {
            AccordionComponent c = stack(s, Mode.SINGLE);
            s.press(8 + 30, headerY(1) + 6);
        });

        stage.state("released elsewhere keeps it shut", W, H, s -> {
            AccordionComponent c = stack(s, Mode.SINGLE);
            s.drag(8 + 30, headerY(0) + 6, 8 + 30, headerY(0) + 40);
        });

        stage.state("opening half way", W, H, s -> {
            AccordionComponent c = stack(s, Mode.SINGLE);
            clickRow(s, 0);
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("reduced motion opens at once", W, H, s -> {
            AccordionComponent c = stack(s, Mode.SINGLE);
            HostState.reducedMotion(true);
            try {
                clickRow(s, 0);
            } finally {
                HostState.reducedMotion(false);
            }
        });

        stage.state("single swaps", W, H, s -> {
            List<String> heard = new ArrayList<>();
            AccordionComponent c = stack(s, Mode.SINGLE);
            c.setOpen(0, true);
            c.onChange((i, on) -> heard.add(i + ":" + on));
            clickRow(s, 1, 0);
        });

        stage.state("single swap half way", W, H, s -> {
            AccordionComponent c = stack(s, Mode.SINGLE);
            c.setOpen(0, true);
            clickRow(s, 1, 0);
            s.waitMs(32);
            s.settle(0);
        });

        stage.state("multiple stay open", W, 160, s -> {
            AccordionComponent c = s.add(new AccordionComponent(new Box("a", 8, 8, W - 16, 144), Mode.MULTIPLE));
            c.item("Where is my world?", A);
            c.item("How do I change the view?", B);
            c.item("Are there backups?", C);
            clickRow(s, 0);
            s.waitMs(300);
            clickRow(s, 2, 0);
        });

        stage.state("not collapsible keeps one open", W, H, s -> {
            AccordionComponent c = stack(s, Mode.SINGLE).collapsible(false);
            c.setOpen(1, true);
            clickRow(s, 1, 1);
            clickRow(s, 0, 1);
        });

        stage.state("tab rings a title", W, H, s -> {
            AccordionComponent c = stack(s, Mode.SINGLE);
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.settle(0);
        });

        stage.state("keys walk and open", W, H, s -> {
            AccordionComponent c = stack(s, Mode.SINGLE);
            c.disabled(1, true);
            s.key(InputKey.TAB);
            s.key(InputKey.DOWN);
            s.key(InputKey.DOWN);
            s.key(InputKey.END);
            s.key(InputKey.HOME);
            s.key(InputKey.ENTER);
            s.key(InputKey.LEFT);
            s.key(InputKey.RIGHT);
            s.waitMs(300);
            s.settle(0);
        });

        stage.state("disabled item", W, H, s -> {
            AccordionComponent c = stack(s, Mode.SINGLE);
            c.disabled(1, true);
            clickRow(s, 1);
            s.move(60, headerY(1) + 6);
        });

        stage.state("disabled whole", W, H, s -> {
            AccordionComponent c = stack(s, Mode.SINGLE).active(false);
            c.setOpen(0, true);
            clickRow(s, 1, 0);
            s.key(InputKey.TAB);
        });

        stage.state("taller than the box scrolls", W, 80, s -> {
            AccordionComponent c = s.add(new AccordionComponent(new Box("a", 8, 8, W - 16, 64), Mode.MULTIPLE));
            c.item("Where is my world?", A);
            c.item("How do I change the view?", B);
            c.item("Are there backups?", C);
            c.setOpen(0, true);
            c.setOpen(2, true);
            s.scroll(60, 40, -1);
            s.scroll(60, 40, -50);
        });

        stage.state("keys reveal a title", W, 56, s -> {
            AccordionComponent c = s.add(new AccordionComponent(new Box("a", 8, 8, W - 16, 40), Mode.MULTIPLE));
            for (int i = 0; i < 6; i++) {
                c.item("Title " + i, "");
            }
            s.key(InputKey.TAB);
            s.key(InputKey.END);
            s.settle(0);
        });

        stage.state("drag the bar", W, 80, s -> {
            AccordionComponent c = s.add(new AccordionComponent(new Box("a", 8, 8, W - 16, 64), Mode.MULTIPLE));
            c.item("Where is my world?", A);
            c.item("How do I change the view?", B);
            c.item("Are there backups?", C);
            c.setOpen(0, true);
            c.setOpen(2, true);
            s.hold(W - 11, 12, W - 11, 80);
        });

        stage.state("widgets inside", W, 130, s -> {
            int[] pressed = new int[1];
            AccordionComponent c = s.add(new AccordionComponent(new Box("a", 8, 8, W - 16, 114), Mode.MULTIPLE));
            AccordionComponent.Item video = c.item("Video", "");
            ToggleComponent toggle = video.add(new ToggleComponent(new Box("t", 0, 0, 150, 12), "Show clouds", true, on -> { }), 14, 0);
            SliderComponent slider = video.add(new SliderComponent(new Box("f", 0, 0, 150, 12), 30, 110, 70, v -> { }), 14, 16);
            ButtonComponent button = c.item("Actions", "Press this one.").add(new ButtonComponent(new Box("b", 0, 0, 80, 16), "Press", () -> pressed[0]++), 14, 12);
            c.setOpen(0, true);
            c.setOpen(1, true);
            s.waitMs(16);
            s.click(button.x + 4, button.y + 4);
            s.key(InputKey.TAB);
        });

        stage.state("closed widgets take nothing", W, 80, s -> {
            int[] pressed = new int[1];
            AccordionComponent c = s.add(new AccordionComponent(new Box("a", 8, 8, W - 16, 64), Mode.MULTIPLE));
            ButtonComponent button = c.item("Closed", "").add(new ButtonComponent(new Box("b", 0, 0, 80, 16), "Press", () -> pressed[0]++), 14, 2);
            c.item("Below", "Next block.");
            s.waitMs(16);
            s.click(button.x + 4, button.y + 4);
        });

        stage.state("fit follows what is open", W, 100, s -> {
            AccordionComponent c = s.add(new AccordionComponent(new Box("a", 8, 8, W - 16, 10), Mode.SINGLE));
            c.item("One", B);
            c.item("Two", "");
            c.setOpen(0, true);
            c.fit();
        });

        stage.state("very long title", W, 56, s -> {
            AccordionComponent c = s.add(new AccordionComponent(new Box("a", 8, 8, W - 16, 32), Mode.SINGLE));
            c.item("A very very long title that cannot possibly fit on this line at all", B);
            s.move(60, 12);
        });

        stage.state("empty", W, 40, s -> {
            AccordionComponent c = s.add(new AccordionComponent(new Box("a", 8, 8, W - 16, 24), Mode.SINGLE));
            s.click(50, 16);
            s.key(InputKey.TAB);
        });

        stage.state("tiny", W, 44, s -> {
            AccordionComponent a = s.add(new AccordionComponent(new Box("a", 8, 8, 30, 10), Mode.SINGLE));
            a.item("Tiny box", A);
            a.setOpen(0, true);
            AccordionComponent b = s.add(new AccordionComponent(new Box("b", 46, 8, 60, 28), Mode.SINGLE));
            b.item("Narrow one", A);
            b.setOpen(0, true);
        });

        stage.state("hidden", W, 40, s -> {
            AccordionComponent c = s.add(new AccordionComponent(new Box("a", 8, 8, W - 16, 24), Mode.SINGLE));
            c.item("Hidden", "");
            c.visible = false;
            s.click(50, 12);
        });
    }
}
