package bench.demos;

import dev.dryaides.minuit.internal.HostState;
import bench.api.Demo;
import bench.api.Stage;
import dev.dryaides.minuit.AlertComponent;
import dev.dryaides.minuit.AlertComponent.Variant;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.BenchAccess;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.InputKey;
import dev.dryaides.minuit.Theme;
import java.util.ArrayList;
import java.util.List;

/** The inline banner, driven by scripted input. */
public final class AlertDemo implements Demo {
    private static final int W = 220;
    private static final String BODY = "The backup finished, but two chunks could not be read and were skipped.";

    private static Box box(int y) {
        return new Box("a", 8, y, W - 16, 0);
    }

    @Override
    public void states(Stage stage) {
        stage.state("all variants", W, 134, s -> {
            int y = 8;
            for (Variant v : Variant.values()) {
                AlertComponent a = s.add(new AlertComponent(box(y), v, v.name().charAt(0) + v.name().substring(1).toLowerCase(), "A short line about it.").fit());
                y += a.h + 3;
            }
        });

        stage.state("minecraft item icon", W, 82, s -> {
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.SUCCESS, "Diamond found", "Sixty-four of them.")
                    .icon(dev.dryaides.minuit.ItemView.of("minecraft:diamond", 64)).fit());
            s.add(new AlertComponent(box(8 + a.h + 4), Variant.WARNING, "", "One line, item box of 16.")
                    .icon(dev.dryaides.minuit.ItemView.of("minecraft:torch")).fit());
        });
        stage.state("title only and message only", W, 62, s -> {
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.SUCCESS, "World saved", "").fit());
            AlertComponent b = s.add(new AlertComponent(box(8 + a.h + 4), Variant.INFO, "", "Only a message, on one line.").fit());
        });

        stage.state("long text wraps", W, 56, s -> {
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.WARNING, "Backup incomplete", BODY).fit());
        });

        stage.state("very long words in a short box", W, 44, s -> {
            AlertComponent a = s.add(new AlertComponent(new Box("a", 8, 8, W - 16, 28), Variant.ERROR, "Could not connect to the server", BODY + " " + BODY));
            s.waitMs(16);
            s.move(100, 20);
            s.settle(1500);
            List<String> tip = s.panel().tooltipAt(100, 20);
        });

        stage.state("dismissible", W, 44, s -> {
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.INFO, "New version", "Version 1.2 is out.").dismissible(true).fit());
        });

        stage.state("hover close", W, 44, s -> {
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.WARNING, "Low memory", "Close some tabs.").dismissible(true).fit());
            Box c = BenchAccess.closeBox(a);
            s.move(c.x() + 5, c.y() + 5);
        });

        stage.state("close held", W, 44, s -> {
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.ERROR, "Failed", "It did not work.").dismissible(true).fit());
            Box c = BenchAccess.closeBox(a);
            s.press(c.x() + 5, c.y() + 5);
        });

        stage.state("released elsewhere keeps", W, 44, s -> {
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.SUCCESS, "Done", "All good.").dismissible(true).fit());
            Box c = BenchAccess.closeBox(a);
            s.drag(c.x() + 5, c.y() + 5, 40, 20);
        });

        stage.state("click dismisses and calls back", W, 44, s -> {
            int[] heard = new int[1];
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.SUCCESS, "Done", "All good.").dismissible(true).onDismiss(() -> heard[0]++).fit());
            Box c = BenchAccess.closeBox(a);
            s.click(c.x() + 5, c.y() + 5);
            s.waitMs(300);
            s.click(c.x() + 5, c.y() + 5);
            a.show();
        });

        stage.state("folding half way", W, 44, s -> {
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.WARNING, "Folding", "On its way out.").dismissible(true).fit());
            a.dismiss();
            s.waitMs(16);
            s.settle(0);
        });

        stage.state("dismiss under reduced motion", W, 44, s -> {
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.INFO, "Gone at once", "").dismissible(true).fit());
            HostState.reducedMotion(true);
            try {
                a.dismiss();
            } finally {
                HostState.reducedMotion(false);
            }
        });

        stage.state("tab rings the close button, enter dismisses", W, 44, s -> {
            int[] heard = new int[1];
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.INFO, "Keyboard", "Tab, then Enter.").dismissible(true).onDismiss(() -> heard[0]++).fit());
            s.key(InputKey.TAB);
            s.settle(0);
        });

        stage.state("enter on the close button", W, 44, s -> {
            int[] heard = new int[1];
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.INFO, "Keyboard", "Tab, then Enter.").dismissible(true).onDismiss(() -> heard[0]++).fit());
            s.key(InputKey.TAB);
            s.key(InputKey.ENTER);
        });

        stage.state("no close button, no tab stop", W, 44, s -> {
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.INFO, "Quiet", "Nothing to press.").fit());
            s.key(InputKey.TAB);
        });

        stage.state("disabled", W, 76, s -> {
            int[] heard = new int[1];
            AlertComponent a = s.add(new AlertComponent(box(8), Variant.ERROR, "Disabled", "Fades, and its button is off.").dismissible(true).active(false).onDismiss(() -> heard[0]++).fit());
            s.add(new AlertComponent(box(8 + a.h + 4), Variant.SUCCESS, "Disabled", "The same in another colour.").active(false).fit());
            Box c = BenchAccess.closeBox(a);
            s.click(c.x() + 5, c.y() + 5);
        });

        stage.state("own icon", W, 44, s -> {
            Theme t = s.theme();
            s.add(new AlertComponent(box(8), Variant.INFO, "Own icon", "From the theme.").icon(t.icon(Icon.GLOBE)).fit());
        });

        stage.state("hidden", W, 44, s -> {
            AlertComponent a = s.add(new AlertComponent(new Box("a", 8, 8, W - 16, 28), Variant.ERROR, "Hidden", "Not drawn.").dismissible(true));
            a.visible = false;
            Box c = BenchAccess.closeBox(a);
            s.click(c.x() + 5, c.y() + 5);
        });

        stage.state("empty text", W, 36, s -> {
            AlertComponent a = s.add(new AlertComponent(new Box("a", 8, 8, W - 16, 20), Variant.WARNING, null, null));
        });

        stage.state("tiny boxes", W, 56, s -> {
            List<AlertComponent> all = new ArrayList<>();
            all.add(s.add(new AlertComponent(new Box("a", 8, 8, 34, 14), Variant.ERROR, "Tiny", BODY).dismissible(true)));
            all.add(s.add(new AlertComponent(new Box("b", 50, 8, 20, 10), Variant.INFO, "Tiny", BODY)));
            all.add(s.add(new AlertComponent(new Box("c", 78, 8, 6, 6), Variant.WARNING, "Tiny", BODY)));
            all.add(s.add(new AlertComponent(new Box("d", 8, 30, W - 16, 12), Variant.SUCCESS, "A box of one line", BODY).dismissible(true)));
            for (AlertComponent a : all) {
            }
        });
    }
}
