import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Bubble;
import dev.dryaides.minuit.BubbleStyle;

import java.io.File;
import java.util.List;

/**
 * Draws the speech bubble for the four cases a player meets (a short order, one long line, two
 * lines, a clip without words), in English and French, over a world-like backdrop, the speaker's
 * head under the tail. No checks here: the shots are diffed against the previous render, pixel for pixel.
 *
 * <p>Run by {@code testbench/run.sh}, into {@code <shots folder>/bubble}.
 */
public class BubbleBench {

    public static void main(String[] args) {
        File dir = new File(args.length > 0 ? args[0] : "out/bubble");
        String prefix = args.length > 1 ? args[1] : "after";
        BenchDraw.McFont font = new BenchDraw.McFont();
        String[][] cases = {
                {"en", "short", "Sit!"},
                {"en", "long", "Open the barn door, please"},
                {"en", "two", "Open the attic door and bring me the stick, please"},
                {"en", "dots", ""},
                {"fr", "short", "Assis !"},
                {"fr", "long", "Ouvre la porte de la grange"},
                {"fr", "two", "Ouvre la porte du grenier s'il te plaît"},
                {"fr", "dots", ""},
        };
        int w = 200;
        int h = 70;
        for (String[] c : cases) {
            BenchDraw.MockDraw d = new BenchDraw.MockDraw(font, w, h);
            d.backdrop();
            int ax = w / 2 - 20;
            int ay = h - 8;
            d.fill(ax - 4, ay, ax + 4, ay + 8, 0xFFD6342E);
            List<String> lines = Bubble.wrap(d, c[2], Bubble.MAX_LINE_WIDTH, Bubble.MAX_LINES);
            Box box = Bubble.box(d, lines, ax, ay, w, h);
            Bubble.draw(d, BubbleStyle.CLASSIC, lines, box, ax, 1F);
            d.save(new File(dir, prefix + "-" + c[1] + "-" + c[0] + ".png"));
        }
        System.out.println("OK " + dir.getAbsolutePath());
    }
}
