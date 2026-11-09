package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.ScrollComponent;
import dev.dryaides.minuit.TextAreaComponent;
import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.TextMeasure;
import java.util.ArrayList;
import java.util.List;

/**
 * The multi-line editor, driven by scripted input. The soft line under a widget prints its resulting
 * values.
 */
public final class TextAreaDemo implements Demo {
    private static final String PROSE = "The quick brown fox jumps over the lazy dog.\nA second paragraph follows the first one here.";
    private static final String LONG = "Line one\nLine two\nLine three\nLine four\nLine five\nLine six\nLine seven\nLine eight\nLine nine\nLine ten";
    private static final String WORDS = "alpha beta gamma";

    private static final int W = 200;
    private static final int AREA_H = 58;
    private static final int STATE_H = 88;
    /** Shorter than a blink, so the picture shows the caret. */
    private static final int CARET_ON = 100;
    /** The inner width of an area as wide as W - 16: its padding and its bar's gutter come off. */
    private static final int TEXT_W = W - 16 - 8 - 6;

    @Override
    public void states(Stage stage) {
        stage.state("default", W, 74, s -> area(s, PROSE));
        stage.state("placeholder (empty)", W, 74, s ->
                s.add(new TextAreaComponent(new Box("a", 8, 8, W - 16, AREA_H), "", v -> { }).placeholder("Write something...")));
        stage.state("long placeholder, focused", W, 74, s -> {
            TextAreaComponent a = s.add(new TextAreaComponent(new Box("a", 8, 8, W - 16, AREA_H), "", v -> { })
                    .placeholder("A placeholder long enough that it has to wrap onto a second line in the box."));
            s.focus(a);
            s.settle(CARET_ON);
        });
        stage.state("hover", W, 74, s -> {
            area(s, PROSE);
            s.move(60, 30);
        });
        stage.state("tooltip", 260, 110, s -> {
            s.add(new TextAreaComponent(new Box("a", 8, 8, 200, 40), "Notes", v -> { }).tooltip("Notes", "Saved with the world"));
            s.move(60, 30);
            s.settle(1500);
        });
        stage.state("focused by click", W, STATE_H, s -> {
            TextAreaComponent a = area(s, PROSE);
            s.click(50, 13);
            s.settle(CARET_ON);
            readout(s, a);
        });
        stage.state("disabled, click and type ignored", W, STATE_H, s -> {
            TextAreaComponent a = area(s, PROSE);
            a.active = false;
            s.click(50, 13);
            s.type("x");
            readout(s, a);
        });
        stage.state("disabled placeholder", W, 74, s -> {
            TextAreaComponent a = s.add(new TextAreaComponent(new Box("a", 8, 8, W - 16, AREA_H), "", v -> { }).placeholder("Nothing yet"));
            a.active = false;
        });
        stage.state("typed with enter", W, STATE_H, s -> {
            TextAreaComponent a = area(s, "");
            s.focus(a);
            s.type("Hello");
            s.key(InputKey.ENTER);
            s.type("world");
            s.settle(CARET_ON);
            readout(s, a);
        });
        stage.state("mouse drag selection", W, STATE_H, s -> {
            TextAreaComponent a = area(s, PROSE);
            s.drag(30, 13, 90, 33);
            readout(s, a);
        });
        stage.state("selection over a blank line", W, STATE_H, s -> {
            TextAreaComponent a = area(s, "First\n\nThird line");
            s.focus(a);
            s.key(InputKey.SELECT_ALL);
            readout(s, a);
        });
        stage.state("double click word", W, STATE_H, s -> {
            TextAreaComponent a = area(s, WORDS);
            int mid = xOf("alpha be");
            s.click(mid, 13);
            s.click(mid, 13);
            readout(s, a);
        });
        stage.state("shift arrows", W, STATE_H, s -> {
            TextAreaComponent a = area(s, PROSE);
            s.focus(a);
            a.moveCursor(4, false);
            for (int i = 0; i < 5; i++) {
                s.key(InputKey.RIGHT, true);
            }
            s.key(InputKey.DOWN, true);
            readout(s, a);
        });
        stage.state("end of a wrapped line", W, STATE_H, s -> {
            TextAreaComponent a = area(s, PROSE);
            s.focus(a);
            a.moveCursor(0, false);
            s.key(InputKey.END);
            s.settle(CARET_ON);
            readout(s, a);
        });
        stage.state("end, then down", W, STATE_H, s -> {
            TextAreaComponent a = area(s, PROSE);
            s.focus(a);
            a.moveCursor(0, false);
            s.key(InputKey.END);
            s.key(InputKey.DOWN);
            s.settle(CARET_ON);
            readout(s, a);
        });
        stage.state("clipboard: cut then paste", W, STATE_H, s -> {
            TextFieldComponent.Clipboard saved = TextFieldComponent.clipboard;
            TextFieldComponent.clipboard = new Fake();
            try {
                TextAreaComponent a = area(s, WORDS);
                s.focus(a);
                a.selectWord(0);
                s.key(InputKey.CUT);
                s.key(InputKey.END);
                s.key(InputKey.PASTE);
                s.settle(CARET_ON);
                readout(s, a);
            } finally {
                TextFieldComponent.clipboard = saved;
            }
        });
        stage.state("undo brings back a delete", W, STATE_H, s -> {
            TextAreaComponent a = area(s, "Start ");
            s.focus(a);
            s.type("typed");
            s.key(InputKey.SELECT_ALL);
            s.key(InputKey.DELETE);
            a.undo();
            readout(s, a);
        });
        stage.state("overflow: caret kept visible", W, STATE_H, s -> {
            TextAreaComponent a = area(s, LONG);
            s.focus(a);
            a.moveCursor(a.text().length(), false);
            s.type(" and on");
            s.settle(CARET_ON);
            readout(s, a);
        });
        stage.state("overflow: wheel", W, STATE_H, s -> {
            TextAreaComponent a = area(s, LONG);
            s.scroll(60, 30, -2);
            readout(s, a);
        });
        stage.state("overflow: bar held", W, STATE_H, s -> {
            TextAreaComponent a = area(s, LONG);
            s.hold(W - 11, 16, W - 11, 40);
            readout(s, a);
        });
        stage.state("overflow: bar hover", W, STATE_H, s -> {
            TextAreaComponent a = area(s, LONG);
            s.move(W - 11, 20);
            readout(s, a);
        });
        stage.state("very long word", W, STATE_H, s -> {
            TextAreaComponent a = area(s, "abcdefghijklmnopqrstuvwxyz".repeat(5));
            s.focus(a);
            a.moveCursor(10, false);
            s.key(InputKey.END);
            s.settle(CARET_ON);
            readout(s, a);
        });
        stage.state("narrow box", 60, 74, s -> {
            TextAreaComponent a = s.add(new TextAreaComponent(new Box("a", 8, 8, 44, AREA_H), "Words wrap in here, and scroll.", v -> { }));
            s.focus(a);
            s.settle(CARET_ON);
        });
        stage.state("maxLength 12", W, STATE_H, s -> {
            TextAreaComponent a = area(s, "").maxLength(12);
            s.focus(a);
            s.type("This is much too long");
            s.settle(CARET_ON);
            readout(s, a);
        });
        stage.state("tab moves focus", W, 112, s -> {
            TextAreaComponent first = s.add(new TextAreaComponent(new Box("a", 8, 8, W - 16, 30), "first", v -> { }));
            TextAreaComponent second = s.add(new TextAreaComponent(new Box("b", 8, 42, W - 16, 30), "second", v -> { }));
            s.click(50, 16);
            s.key(InputKey.TAB);
            s.settle(CARET_ON);
            s.paint((d, t) -> {
                d.text("first " + first.focused + ", second " + second.focused, 8, 80, t.palette().textSoft());
                d.text(first.text() + " / " + second.text(), 8, 92, t.palette().textSoft());
            });
        });
        stage.state("next to a text field", W, 112, s -> {
            TextFieldComponent field = s.add(new TextFieldComponent(new Box("f", 8, 8, W - 16, 16), "alpha beta", v -> { }));
            s.add(new TextFieldComponent(new Box("f2", 8, 28, W - 16, 16), "", v -> { }).placeholder("Search..."));
            TextFieldComponent off = s.add(new TextFieldComponent(new Box("f3", 8, 48, W - 16, 16), "disabled", v -> { }));
            off.active = false;
            s.add(new TextAreaComponent(new Box("a", 8, 68, W - 16, 36), "alpha beta", v -> { }));
            s.focus(field);
            field.moveCursor(0, false);
            field.moveCursor(5, true);
        });
        stage.state("field and area, area selected", W, 72, s -> {
            s.add(new TextFieldComponent(new Box("f", 8, 8, W - 16, 16), "alpha beta", v -> { }));
            TextAreaComponent a = s.add(new TextAreaComponent(new Box("a", 8, 28, W - 16, 36), "alpha beta", v -> { }));
            s.focus(a);
            a.selectWord(0);
        });
        stage.state("text field hovered, for reference", W, 32, s -> {
            s.add(new TextFieldComponent(new Box("f", 8, 8, W - 16, 16), "alpha beta", v -> { }));
            s.move(60, 16);
        });
        stage.state("caret in its blink's off half", W, 74, s -> {
            TextAreaComponent a = area(s, WORDS);
            s.focus(a);
            s.settle(600);
        });
        stage.state("reduced motion: caret stays on", W, 74, s -> {
            s.paint((d, t) -> BaseComponent.reducedMotion = true);
            TextAreaComponent a = area(s, WORDS);
            s.paint((d, t) -> BaseComponent.reducedMotion = false);
            s.focus(a);
            s.settle(600);
        });
        stage.state("inside a scrolled ScrollComponent", W, 96, s -> {
            ScrollComponent box = s.add(new ScrollComponent(new Box("s", 8, 8, W - 16, 60)));
            box.add(new ButtonComponent(new Box("b", 12, 12, 100, 16), "Above", () -> { }));
            TextAreaComponent a = box.add(new TextAreaComponent(new Box("a", 12, 32, W - 32, 40), LONG, v -> { }));
            box.add(new ButtonComponent(new Box("c", 12, 76, 100, 16), "Below", () -> { }));
            box.setScroll(20);
            // the area's second line now sits at y 32 + 4 + 10 - 20 = 26 on screen
            s.click(12 + 4 + TextMeasure.width("Line"), 28);
            s.scroll(60, 28, -1);
            s.settle(CARET_ON);
        });
        stage.state("ctrl+z undoes", W, STATE_H, s -> {
            TextAreaComponent a = area(s, "Start ");
            s.focus(a);
            s.type("typed");
            s.held(true, false);
            press(s, RAW_Z);
            s.held(false, false);
            s.settle(CARET_ON);
            readout(s, a);
        });
        stage.state("ctrl+y redoes", W, STATE_H, s -> {
            TextAreaComponent a = area(s, "Start ");
            s.focus(a);
            s.type("typed");
            s.held(true, false);
            press(s, RAW_Z);
            press(s, RAW_Y);
            s.held(false, false);
            s.settle(CARET_ON);
            readout(s, a);
        });
        stage.state("ctrl+shift+z redoes", W, STATE_H, s -> {
            TextAreaComponent a = area(s, "Start ");
            s.focus(a);
            s.type("typed");
            s.held(true, false);
            press(s, RAW_Z);
            s.held(true, true);
            press(s, RAW_Z);
            s.held(false, false);
            s.settle(CARET_ON);
            readout(s, a);
        });
        stage.state("z without ctrl does not undo", W, STATE_H, s -> {
            TextAreaComponent a = area(s, "Start ");
            s.focus(a);
            s.type("typed");
            press(s, RAW_Z);
            s.settle(CARET_ON);
            readout(s, a);
        });
    }

    // ---- helpers ----

    private static final int RAW_Z = 90;
    private static final int RAW_Y = 89;

    /** A key the way the panel's raw path gets it. True when the focused widget took it. */
    private static boolean press(Scene s, int code) {
        return s.panel().rawKey(KeyPress.key(code, 0));
    }

    private static final class Fake implements TextFieldComponent.Clipboard {
        String value = "";
        @Override public String get() { return value; }
        @Override public void set(String v) { value = v; }
    }

    private static TextAreaComponent area(Scene s, String text) {
        return s.add(new TextAreaComponent(new Box("a", 8, 8, W - 16, AREA_H), text, v -> { }));
    }

    /** The pixel after a prefix on the first line of an area at (8, 8). */
    private static int xOf(String prefix) {
        return 12 + TextMeasure.width(prefix);
    }

    /** A line of values under the area. */
    private static void readout(Scene s, TextAreaComponent a) {
        s.paint((d, t) -> {
            int[] sel = a.selection();
            String shown = a.hasSelection() ? " [" + a.selectedText().replace('\n', '/') + "]" : "";
            d.textFit("len " + a.text().length() + " at " + a.cursor() + " sel " + sel[0] + "-" + sel[1]
                    + shown, 8, 72, W - 16, t.palette().textSoft());
        });
    }
}
