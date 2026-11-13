import dev.dryaides.minuit.internal.HostState;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.ColorPickerComponent;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.FrameStyles;
import dev.dryaides.minuit.FrameStyle;
import dev.dryaides.minuit.GlassFrame;
import dev.dryaides.minuit.GlassPalette;
import dev.dryaides.minuit.RadialComponent;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.ToggleComponent;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.WindowComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.UiSound;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Lot B widgets drawn offline: radial wheel (with its page slide frame by frame), colour picker, window. */
public class WindowBench {

    static final int W = 427;
    static final int H = 240;
    static long now = 10_000;
    static int fails;

    public static void main(String[] args) {
        File out = new File(args.length > 0 ? args[0] : "out");
        BenchMock.McFont font = new BenchMock.McFont();
        HostState.clock(() -> now);
        List<String> taps = new ArrayList<>();
        HostState.sound(new UiSound() {
            @Override
            public void tap() {
                taps.add("tap@" + now);
            }
        });
        for (String family : List.of("slate", "leather")) {
            for (ThemeChoice.Variant v : List.of(ThemeChoice.Variant.LIGHT, ThemeChoice.Variant.DARK)) {
                Theme theme = Themes.resolve(Themes.SLATE, new ThemeChoice(family, "default", v));
                String tag = family + "-" + v.id();
                radial(font, theme, out, tag, tag.equals("slate-dark"));
                picker(font, theme, out, tag);
                window(font, theme, out, tag);
            }
        }
        for (String frame : List.of("leather", "slate", "bedrock", "slate-glass", "soft", "bubbly")) {
            boolean glass = frame.endsWith("-glass");
            FrameStyle style = FrameStyles.byName(glass ? frame.substring(0, frame.length() - "-glass".length()) : frame);
            Theme t = glass
                    ? new Theme(frame, GlassPalette.of(Themes.SLATE.palette()), new GlassFrame(style))
                    : new Theme(frame, Themes.SLATE.palette(), style);
            RadialComponent<String> w = RadialComponent.<String>builder(new Box("screen", 0, 0, W, H), 76)
                    .slot(40, 20).page("Poses", List.of("Wave", "Sit", "Point", "Bow", "Dance", "Salute"))
                    .page("Props", List.of("Hat", "Cape")).label(x -> x).build();
            BenchMock.MockDraw d = draw(font);
            w.draw(d, t, W / 2, H / 2 - 60, 1F);
            d.save(new File(out, "radial-strip-" + frame + ".png"));
        }
        check(taps.size() == 4, "one tap per notch, got " + taps);
        System.out.println(fails == 0 ? "WindowBench ok" : "WindowBench " + fails + " FAIL");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    static void radial(BenchMock.McFont font, Theme theme, File out, String tag, boolean frames) {
        String[] picked = {null};
        boolean[] cancelled = {false};
        RadialComponent<String> wheel = RadialComponent.<String>builder(new Box("screen", 0, 0, W, H), 76)
                .slot(40, 20)
                .page("Poses", List.of("Wave", "Sit", "Point", "Bow", "Dance", "Salute", "Shrug", "Cheer"))
                .page("Props", List.of("Hat", "Cape", "Wings"))
                .page("Empty", List.of())
                .label(s -> s)
                .icon(s -> Icon.values()[Math.floorMod(s.hashCode(), Icon.values().length)].glyph())
                .centerLabel(() -> "Cancel")
                .onPick(s -> picked[0] = s)
                .onCancel(() -> cancelled[0] = true)
                .build();
        int cx = W / 2;
        int cy = H / 2;
        // Aim right-down: 8 parts, 135 degrees from the top is part 3 ("Bow").
        BenchMock.MockDraw d = draw(font);
        wheel.draw(d, theme, cx + 40, cy + 40, 1F);
        d.save(new File(out, "radial-aim-" + tag + ".png"));
        check("Bow".equals(wheel.selected()), tag + " aim gives Bow, got " + wheel.selected());
        BenchMock.MockDraw centre = draw(font);
        wheel.draw(centre, theme, cx + 5, cy, 1F);
        centre.save(new File(out, "radial-deadzone-" + tag + ".png"));
        check(wheel.selected() == null, tag + " dead zone");
        if (frames) {
            wheel.mouseScrolled(cx, cy, -1);
            long start = now;
            for (int t = 0; t <= 200; t += 20) {
                now = start + t;
                BenchMock.MockDraw f = draw(font);
                wheel.draw(f, theme, cx, cy - 30, 1F);
                f.save(new File(out, String.format("radial-slide-%s-%03dms.png", tag, t)));
            }
            check(wheel.page() == 1, "notch down goes to page 1");
            wheel.mouseScrolled(cx, cy, 1);
            wheel.mouseScrolled(cx, cy, 1);
            check(wheel.page() == 2, "two notches up wrap to the last page");
            wheel.mouseScrolled(cx, cy, -1);
            // Release on a part picks it; the tap guard does not apply once 150 ms have passed.
            now += 500;
            wheel.draw(draw(font), theme, cx, cy - 40, 1F);
            wheel.release();
            check("Wave".equals(picked[0]), "back on page 0, release on the top part picks Wave, got " + picked[0]);
            RadialComponent<String> tap = RadialComponent.<String>builder(new Box("s", 0, 0, W, H), 60)
                    .page("P", List.of("A", "B")).onCancel(() -> cancelled[0] = true)
                    .onPick(s -> picked[0] = "tap-picked").build();
            now += 100;
            tap.mouseClicked(cx, cy, 0);
            check(cancelled[0] && !"tap-picked".equals(picked[0]), "quick tap cancels");
            now += 1000;
        }
    }

    static void picker(BenchMock.McFont font, Theme theme, File out, String tag) {
        List<Integer> recent = new ArrayList<>(List.of(0xFFE04040, 0xFF40A0E0, 0xFF60C060, 0xFFFFD040, 0xFF8040C0,
                0xFF202020, 0xFFFFFFFF));
        int[] last = {0};
        Panel panel = new Panel(theme, 120, 30, 140, 170, "Colour");
        ColorPickerComponent picker = new ColorPickerComponent(new Box("picker", 140, 54, 0, 0), 0xC06699CC, c -> last[0] = c)
                .alpha(true).recent(recent);
        BenchMock.MockDraw d = draw(font);
        panel.draw(d, 0, 0, 1F);
        picker.draw(d, theme, 0, 0, 1F);
        d.save(new File(out, "picker-" + tag + ".png"));
        // Drag into the black bottom row: the hue must stay.
        picker.mouseClicked(160, 80, 0);
        picker.mouseDragged(160, 54 + 79, 0, 0, 0);
        picker.mouseDragged(190, 80, 0, 0, 0);
        picker.mouseReleased(190, 80, 0);
        float hueBack = dev.dryaides.minuit.internal.ColorMath.hsv(picker.color())[0];
        check(Math.abs(hueBack - 210) < 3, tag + " hue kept through black, got " + hueBack);
        check(recent.get(0) == picker.color(), tag + " commit joins recent");
    }

    static void window(BenchMock.McFont font, Theme theme, File out, String tag) {
        WindowComponent.layouts = dev.dryaides.minuit.LayoutStore.memory();
        PanelComponent content = new Panel(theme, 0, 0, 150, 70, null) {
            {
                add(new SliderComponent(new Box("s", 6, 8, 130, 14), 0, 100, 40, v -> {
                }));
                add(new ToggleComponent(new Box("t", 6, 30, 130, 14), "Snap to grid", true, v -> {
                }));
            }
        };
        WindowComponent win = new WindowComponent("bench:" + tag, content, new Box("w", 40, 40, 0, 0))
                .title("Paint tools").within(new Box("screen", 0, 0, W, H));
        HostState.keys(new dev.dryaides.minuit.HeldKeys() {
            @Override
            public boolean ctrl() {
                return true;
            }

            @Override
            public boolean shift() {
                return false;
            }
        });
        BenchMock.MockDraw d = draw(font);
        win.draw(d, theme, 0, 0, 1F);
        // Move by the title bar, scale by Ctrl+wheel, then draw again.
        win.mouseClicked(60, 45, 0);
        win.mouseDragged(260, 125, 0, 200, 80);
        win.mouseReleased(260, 125, 0);
        for (int i = 0; i < 4; i++) {
            win.mouseScrolled(300, 150, 1);
        }
        win.draw(d, theme, 0, 0, 1F);
        d.save(new File(out, "window-" + tag + ".png"));
        dev.dryaides.minuit.LayoutStore.Layout saved = WindowComponent.layouts.get("bench:" + tag);
        check(saved != null && saved.dx() >= 195 && saved.dy() == 80 && Math.abs(saved.scale() - 1.2F) < 1e-3, tag + " layout saved " + saved);
        check(win.box().right() <= W && win.box().bottom() <= H, tag + " window kept on screen " + win.box());
        WindowComponent again = new WindowComponent("bench:" + tag, content, new Box("w", 40, 40, 0, 0));
        check(again.box().x() == win.box().x() && again.scale() == win.scale(), tag + " reopens where it was left");
        HostState.keys(dev.dryaides.minuit.HeldKeys.NONE);
        again.collapsible(true).collapse(true);
        WindowComponent folded = new WindowComponent("bench:" + tag, content, new Box("w", 40, 40, 0, 0)).collapsible(true);
        check(folded.collapsed() && folded.box().x() == win.box().x(), tag + " reopens folded where it was left");
        tools(font, theme, out, tag);
    }

    /** Paint mode: three tool windows on one screen, compact, foldable, closable; a click raises one. */
    static void tools(BenchMock.McFont font, Theme theme, File out, String tag) {
        WindowComponent.layouts = dev.dryaides.minuit.LayoutStore.memory();
        Box screen = new Box("screen", 0, 0, W, H);
        WindowComponent brush = new WindowComponent("tools:brush", toolPanel(theme, "Size"), new Box("w", 20, 20, 0, 0))
                .title("Brush").compact(true).collapsible(true).within(screen);
        WindowComponent colours = new WindowComponent("tools:colours", toolPanel(theme, "Hue"), new Box("w", 90, 44, 0, 0))
                .title("Colours").compact(true).onClose(() -> { }).within(screen);
        WindowComponent layers = new WindowComponent("tools:layers", toolPanel(theme, "Opacity"), new Box("w", 230, 30, 0, 0))
                .title("Layers").collapsible(true).within(screen);
        layers.collapse(true);
        Panel host = new Panel(theme, 0, 0, W, H, null) {
            {
                add(brush);
                add(colours);
                add(layers);
            }
        };
        check(layers.box().h() == 18, tag + " folded window is its title bar " + layers.box());
        host.mouseClicked(brush.box().x() + 4, brush.box().bottom() - 3, 0);
        check(host.widgets().get(2) == brush, tag + " clicked window comes to the front");
        check(brush.focused, tag + " clicked window takes the keyboard");
        host.mouseClicked(brush.box().x() + 20, brush.box().y() + 4, 0);
        host.mouseDragged(brush.box().x() + 400, brush.box().y() + 4, 0, 380, 0);
        host.mouseReleased(brush.box().x() + 400, brush.box().y() + 4, 0);
        check(brush.box().right() <= W, tag + " dragged window stays on screen " + brush.box());
        check(WindowComponent.layouts.get("tools:brush") != null && WindowComponent.layouts.get("tools:colours") == null,
                tag + " each window saves under its own id");
        BenchMock.MockDraw d = draw(font);
        host.draw(d, colours.box().x() + 10, colours.box().y() + 30, 1F);
        d.save(new File(out, "tools-" + tag + ".png"));
    }

    static PanelComponent toolPanel(Theme theme, String what) {
        return new Panel(theme, 0, 0, 110, 44, null) {
            {
                add(new SliderComponent(new Box("s", 4, 4, 102, 12), 0, 100, 40, v -> { }).step(10)
                        .label(v -> what + " " + v));
                add(new dev.dryaides.minuit.ButtonComponent(new Box("b", 4, 24, 50, 14), "Reset", () -> { }));
            }
        };
    }

    static BenchMock.MockDraw draw(BenchMock.McFont font) {
        BenchMock.MockDraw d = new BenchMock.MockDraw(font, W, H);
        d.backdrop();
        return d;
    }

    static class Panel extends PanelComponent {
        final int pw;
        final int ph;
        final String title;

        Panel(Theme theme, int left, int top, int w, int h, String title) {
            super(theme, (key, a) -> key, left, top);
            this.pw = w;
            this.ph = h;
            this.title = title;
        }

        @Override
        public int width() {
            return this.pw;
        }

        @Override
        public int height() {
            return this.ph;
        }

        @Override
        protected void drawBackground(Draw d, int mx, int my) {
            if (this.title != null) {
                this.theme.frame().panel(d, this.theme.palette(), this.left, this.top, this.pw, this.ph);
                d.text(this.title, this.left + 8, this.top + 8, this.theme.palette().text());
            }
        }
    }
}
