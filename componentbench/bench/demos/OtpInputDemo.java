package bench.demos;

import bench.api.Demo;
import bench.api.Scene;
import bench.api.Stage;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.OtpInputComponent;
import dev.dryaides.minuit.TextFieldComponent;
import java.util.ArrayList;
import java.util.List;

/** A code in boxes: typing, paste and backspace. */
public final class OtpInputDemo implements Demo {
    private static final int W = 160;
    private static final int H = 36;
    private static final int X = 8;
    private static final int Y = 8;

    private static final class Fake implements TextFieldComponent.Clipboard {
        String value = "";
        @Override public String get() { return value; }
        @Override public void set(String text) { value = text; }
    }

    private interface WithClip {
        void run(Fake clip);
    }

    private static void withClip(WithClip script) {
        TextFieldComponent.Clipboard saved = TextFieldComponent.clipboard;
        Fake clip = new Fake();
        TextFieldComponent.clipboard = clip;
        try {
            script.run(clip);
        } finally {
            TextFieldComponent.clipboard = saved;
        }
    }

    private static OtpInputComponent six(Scene s) {
        return s.add(new OtpInputComponent(new Box("o", X, Y, 144, 20), 6, v -> { }));
    }

    @Override
    public void states(Stage stage) {
        stage.state("empty", W, H, s -> {
            OtpInputComponent o = six(s);
        });
        stage.state("focused shows the caret", W, H, s -> {
            six(s);
            s.click(X + 4, Y + 8);
            s.settle(0);
        });
        stage.state("typing moves on", W, H, s -> {
            List<String> heard = new ArrayList<>();
            OtpInputComponent o = s.add(new OtpInputComponent(new Box("o", X, Y, 144, 20), 6, heard::add));
            s.focus(o);
            s.type("123");
            s.settle(0);
        });
        stage.state("letters are refused", W, H, s -> {
            OtpInputComponent o = six(s);
            s.focus(o);
            s.type("1a2 b3");
            s.settle(0);
        });
        stage.state("complete fires once", W, H, s -> {
            List<String> done = new ArrayList<>();
            OtpInputComponent o = six(s).onComplete(done::add);
            s.focus(o);
            s.type("123456");
            s.type("6");
            s.type("7");
            s.settle(0);
        });
        stage.state("backspace clears then goes back", W, H, s -> {
            OtpInputComponent o = six(s);
            s.focus(o);
            s.type("123456");
            s.key(InputKey.BACKSPACE);
            s.key(InputKey.BACKSPACE);
            for (int i = 0; i < 5; i++) {
                s.key(InputKey.BACKSPACE);
            }
            s.settle(0);
        });
        stage.state("arrows and overwrite", W, H, s -> {
            OtpInputComponent o = six(s);
            s.focus(o);
            s.type("1234");
            s.key(InputKey.HOME);
            s.key(InputKey.RIGHT);
            s.type("9");
            s.key(InputKey.END);
            s.key(InputKey.RIGHT);
            s.key(InputKey.LEFT);
            s.settle(0);
        });
        stage.state("delete closes the gap", W, H, s -> {
            OtpInputComponent o = six(s);
            s.focus(o);
            s.type("1234");
            s.key(InputKey.HOME);
            s.key(InputKey.DELETE);
            s.settle(0);
        });
        stage.state("click cannot make a hole", W, H, s -> {
            OtpInputComponent o = six(s);
            s.focus(o);
            s.type("12");
            Box far = BenchAccess.cellBox(o, 5);
            s.click(far.x() + 3, far.y() + 3);
            Box first = BenchAccess.cellBox(o, 0);
            s.click(first.x() + 3, first.y() + 3);
            s.settle(0);
        });
        stage.state("paste fills and filters", W, H, s -> withClip(clip -> {
            OtpInputComponent o = six(s);
            s.focus(o);
            clip.value = "12 34-56";
            s.key(InputKey.PASTE);
            s.settle(0);
        }));
        stage.state("paste too long is cut", W, H, s -> withClip(clip -> {
            OtpInputComponent o = six(s);
            s.focus(o);
            clip.value = "123456789";
            s.key(InputKey.PASTE);
            s.settle(0);
        }));
        stage.state("paste from the middle", W, H, s -> withClip(clip -> {
            OtpInputComponent o = six(s);
            s.focus(o);
            s.type("1111");
            s.key(InputKey.HOME);
            s.key(InputKey.RIGHT);
            clip.value = "99";
            s.key(InputKey.PASTE);
            s.settle(0);
        }));
        stage.state("paste with nothing to take", W, H, s -> withClip(clip -> {
            OtpInputComponent o = six(s);
            s.focus(o);
            s.type("12");
            clip.value = "abc";
            s.key(InputKey.PASTE);
            s.settle(0);
        }));
        stage.state("select all marks", W, H, s -> withClip(clip -> {
            OtpInputComponent o = six(s);
            s.focus(o);
            s.type("123456");
            s.key(InputKey.SELECT_ALL);
            s.key(InputKey.COPY);
            s.settle(0);
        }));
        stage.state("select all then type", W, H, s -> {
            OtpInputComponent o = six(s);
            s.focus(o);
            s.type("123456");
            s.key(InputKey.SELECT_ALL);
            s.type("7");
            s.settle(0);
        });
        stage.state("groups", W, H, s -> {
            OtpInputComponent o = s.add(new OtpInputComponent(new Box("o", X, Y, 144, 20), 6, v -> { }).groupEvery(3));
            o.setValue("123456");
        });
        stage.state("mask", W, H, s -> {
            OtpInputComponent o = six(s).mask(true);
            s.focus(o);
            s.type("1234");
            s.settle(0);
        });
        stage.state("alphanumeric upper case", W, H, s -> {
            OtpInputComponent o = s.add(new OtpInputComponent(new Box("o", X, Y, 144, 20), 6, v -> { }).alphanumeric());
            s.focus(o);
            s.type("a9-z");
            s.settle(0);
        });
        stage.state("invalid", W, H, s -> {
            OtpInputComponent o = six(s).invalid(true);
            o.setValue("123456");
        });
        stage.state("disabled", W, H, s -> {
            OtpInputComponent o = six(s).active(false);
            o.setValue("123");
            s.click(X + 4, Y + 8);
            s.focus(o);
            s.type("9");
            s.key(InputKey.BACKSPACE);
        });
        stage.state("tab shows the ring", W, H, s -> {
            OtpInputComponent o = six(s);
            o.setValue("12");
            s.key(InputKey.TAB);
            s.settle(0);
        });
        stage.state("reduced motion keeps the caret", W, H, s -> {
            OtpInputComponent o = six(s);
            s.focus(o);
            BaseComponent.reducedMotion = true;
            try {
                s.waitMs(700);
                s.settle(0);
            } finally {
                BaseComponent.reducedMotion = false;
            }
        });
        stage.state("narrow box falls back to text", W, H, s -> {
            OtpInputComponent o = s.add(new OtpInputComponent(new Box("o", X, Y, 30, 16), 6, v -> { }));
            o.setValue("123456");
        });
        stage.state("boxes stay square in a wide row", W, H, s -> {
            OtpInputComponent o = s.add(new OtpInputComponent(new Box("o", X, Y, 144, 14), 4, v -> { }));
        });
    }
}
