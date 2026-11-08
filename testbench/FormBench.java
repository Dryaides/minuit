import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.ProgressComponent;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.TabsComponent;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.ThemeChoice;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.form.CloseReason;
import dev.dryaides.minuit.form.Form;
import dev.dryaides.minuit.form.FormHandler;
import dev.dryaides.minuit.form.FormServer;
import dev.dryaides.minuit.form.FormSession;
import dev.dryaides.minuit.form.Kind;
import dev.dryaides.minuit.form.Role;
import dev.dryaides.minuit.form.Size;
import dev.dryaides.minuit.form.Text;
import dev.dryaides.minuit.form.Value;
import dev.dryaides.minuit.formui.FormClient;
import dev.dryaides.minuit.formui.FormPanel;
import dev.dryaides.minuit.wire.Fields;
import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.ScreenMessage;
import dev.dryaides.minuit.wire.WireNode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The data-driven screens offline: example:shop built on the server side, sent as bytes to the
 * client, drawn at 200, 300 and 400 GUI px in two families, light and dark, then played: the
 * player's input goes back through the server session and its patches come back to the panel.
 * The notice and menu kinds are drawn once per look.
 */
public class FormBench {

    static final int H = 240;
    static long now = 10_000;
    static int fails;

    public static void main(String[] args) {
        File out = new File(args.length > 0 ? args[0] : "out");
        BenchMock.McFont font = new BenchMock.McFont();
        BaseComponent.clock = () -> now;
        for (String family : List.of("slate", "leather")) {
            for (ThemeChoice.Variant v : List.of(ThemeChoice.Variant.LIGHT, ThemeChoice.Variant.DARK)) {
                Theme theme = Themes.resolve(Themes.SLATE, new ThemeChoice(family, "default", v));
                String tag = family + "-" + v.id();
                for (int screenW : new int[]{216, 316, 427}) {
                    Rig rig = new Rig(theme, font, screenW);
                    FormSession session = rig.server.open("p", shop(), rig.handler);
                    FormPanel panel = rig.client.shown();
                    check(panel != null && panel.width() == Math.min(400, screenW - 16), tag + " panel width at " + screenW);
                    noOverlap(panel, tag + "-" + screenW);
                    rig.shot(out, "shop-" + tag + "-" + panel.width());
                    if (tag.equals("slate-light") && screenW == 427) {
                        play(rig, session, out);
                    }
                    if (screenW == 216) {
                        kinds(rig, out, tag);
                    }
                }
            }
        }
        resize(font, out);
        if (!font.missing.isEmpty()) {
            System.out.println("glyphs missing: " + font.missing);
        }
        System.out.println(fails == 0 ? "FormBench ok" : "FormBench " + fails + " FAIL");
        System.exit(fails == 0 ? 0 : 1);
    }

    /** The design's example screen; the handler answers a volume change by moving the fill bar. */
    static Form shop() {
        return Form.custom("example:shop")
                .title(Text.tr("myshop.settings.title", "Shop settings"))
                .size(Size.WIDE)
                .tabs("tabs", t -> t
                        .page("general", Text.lit("General"), p -> p
                                .toggle("sounds", Text.lit("Sounds"), true)
                                .slider("volume", Text.lit("Volume"), 0, 100, 5, 80)
                                .field("name", Text.lit("Shop name"), f -> f.maxLength(32).placeholder(Text.lit("My shop")))
                                .dropdown("currency", Text.lit("Currency"), List.of(Text.lit("Emerald"), Text.lit("Diamond")), 0))
                        .page("stock", Text.lit("Stock"), p -> p
                                .section("advanced", Text.lit("Advanced"), false, s -> s
                                        .progress("fill", Text.lit("Filled"), 0.4F))))
                .footer(r -> r
                        .button("save", Text.lit("Save"), Role.PRIMARY)
                        .button("cancel", Text.lit("Cancel"), Role.SECONDARY))
                .build();
    }

    /** One player, one server, one client, wired back to back through bytes. */
    static final class Rig {
        final Theme theme;
        final BenchMock.McFont font;
        final int screenW;
        final List<InputMessage> inputs = new ArrayList<>();
        final List<String> calls = new ArrayList<>();
        final FormServer<String> server;
        final FormClient client;
        final FormHandler handler = new FormHandler() {
            @Override
            public void changed(FormSession s, String id, Value v) {
                Rig.this.calls.add(id + "=" + v);
                if (id.equals("volume") && v instanceof Value.Num n) {
                    s.progress("fill", n.value() / 100F);
                }
            }

            @Override
            public void pressed(FormSession s, String id) {
                Rig.this.calls.add("press " + id);
            }

            @Override
            public void closed(FormSession s, CloseReason why) {
                Rig.this.calls.add("closed " + why);
            }
        };

        Rig(Theme theme, BenchMock.McFont font, int screenW) {
            this.theme = theme;
            this.font = font;
            this.screenW = screenW;
            FormClient[] c = new FormClient[1];
            this.server = new FormServer<>((p, bytes) -> c[0].receive(bytes), () -> now);
            c[0] = new FormClient(bytes -> {
                this.inputs.add(InputMessage.decode(bytes));
                this.server.receive("p", bytes);
            }, new FormClient.Host() {
                @Override
                public FormPanel show(ScreenMessage.Open open, java.util.function.Consumer<InputMessage> out) {
                    return FormPanel.of(theme, (key, a) -> key, font::width, screenW, H, open, out);
                }

                @Override
                public void hide(FormPanel panel) {
                }
            });
            this.client = c[0];
        }

        FormPanel panel() {
            return this.client.shown();
        }

        BenchMock.MockDraw shot(File out, String name) {
            return shot(out, name, this.screenW);
        }

        BenchMock.MockDraw shot(File out, String name, int screenW) {
            BenchMock.MockDraw d = new BenchMock.MockDraw(this.font, screenW, H);
            d.backdrop();
            panel().draw(d, -1, -1, 1F);
            d.save(new File(out, name + ".png"));
            return d;
        }
    }

    /** The window shrinks under an open form: the panel lays out on the new size and keeps what was typed. */
    static void resize(BenchMock.McFont font, File out) {
        Theme theme = Themes.resolve(Themes.SLATE, new ThemeChoice("slate", "default", ThemeChoice.Variant.LIGHT));
        Rig rig = new Rig(theme, font, 427);
        FormSession session = rig.server.open("p", shop(), rig.handler);
        FormPanel panel = rig.panel();
        BaseComponent field = panel.widget("name");
        panel.mouseClicked(field.x + 5, field.y + 5, 0);
        panel.charTyped('Z');
        rig.shot(out, "resize-1-wide");
        panel.fit(theme, 216, H);
        check(panel.width() == 200, "resized panel width " + panel.width());
        check(panel.bounds().x() == 8, "resized panel centred: x " + panel.bounds().x());
        check(rig.panel() == panel && panel.session() == session.id(), "resize keeps the session's panel");
        check("Z".equals(((TextFieldComponent) panel.widget("name")).text()), "resize keeps the typed text");
        noOverlap(panel, "resize-216");
        rig.shot(out, "resize-2-narrow", 216);
    }

    static void play(Rig rig, FormSession session, File out) {
        FormPanel panel = rig.panel();
        // The toggle: one change, seq 1, the stored value follows.
        Box sounds = panel.widget("sounds").box();
        panel.mouseClicked(sounds.x() + 5, sounds.y() + 5, 0);
        check(rig.inputs.size() == 1 && rig.inputs.getFirst() instanceof InputMessage.Change c
                && c.seq() == 1 && c.nodeId().equals("sounds") && !Fields.readBool(c.value()), "toggle sends change seq 1: " + rig.inputs);
        check(new Value.Bool(false).equals(session.value("sounds")), "server stored the toggle");

        // The slider: a drag on the 5 grid, one change at once, the next held 100 ms.
        SliderComponent slider = (SliderComponent) panel.widget("volume");
        int tx = slider.x + 4;
        int tw = slider.w - 32;
        int before = rig.inputs.size();
        panel.mouseClicked(tx + tw * 0.31, slider.y + 5, 0);
        now += 30;
        panel.mouseDragged(tx + tw * 0.52, slider.y + 5, 0, 0, 0);
        panel.mouseReleased(tx + tw * 0.52, slider.y + 5, 0);
        check(rig.inputs.size() == before + 1, "drag holds the second change: " + (rig.inputs.size() - before));
        panel.tick();
        check(rig.inputs.size() == before + 1, "still held at 30 ms");
        now += 100;
        panel.tick();
        check(rig.inputs.size() == before + 2, "sent after 100 ms: " + (rig.inputs.size() - before));
        check(slider.value % 5 == 0 && new Value.Num(slider.value).equals(session.value("volume")),
                "slider on its step and the server agrees: " + slider.value + " vs " + session.value("volume"));
        int volume = slider.value;

        // The field: two keys 50 ms apart give one change, the rest 200 ms after the first.
        BaseComponent field = panel.widget("name");
        panel.mouseClicked(field.x + 5, field.y + 5, 0);
        before = rig.inputs.size();
        panel.charTyped('A');
        now += 50;
        panel.charTyped('B');
        panel.tick();
        check(rig.inputs.size() == before + 1, "typing sends at most once per 200 ms");
        now += 200;
        panel.tick();
        check(new Value.Str("AB").equals(session.value("name")), "field text reaches the server: " + session.value("name"));
        rig.shot(out, "play-1-general");

        // The ack rule: a server value older than the player's last change does not undo it.
        int mine = rig.inputs.stream().filter(m -> m instanceof InputMessage.Change c && c.nodeId().equals("volume"))
                .mapToInt(InputMessage::seq).max().orElseThrow();
        panel.apply(new ScreenMessage.Patch(panel.session(), mine - 1,
                List.of(new ScreenMessage.FieldSet("volume", Fields.VALUE, Fields.svarint(0)))));
        check(slider.value == volume, "stale patch keeps the player's slider");
        panel.apply(new ScreenMessage.Patch(panel.session(), mine,
                List.of(new ScreenMessage.FieldSet("volume", Fields.VALUE, Fields.svarint(volume)))));

        // The stock page, the section unfolded: the bar the volume patched while it was hidden.
        TabsComponent tabs = (TabsComponent) panel.widget("tabs");
        panel.mouseClicked(tabs.x + tabs.w * 3 / 4, tabs.y + 4, 0);
        check(new Value.Num(1).equals(session.value("tabs")), "tab index reaches the server");
        BaseComponent fold = panel.widget("advanced");
        panel.mouseClicked(fold.x + 5, fold.y + 5, 0);
        BaseComponent bar = panel.widget("fill");
        check(bar instanceof ProgressComponent p && Math.abs(p.fraction - volume / 100F) < 0.001F,
                "fill follows the volume: " + (bar instanceof ProgressComponent p ? p.fraction : "absent"));
        rig.shot(out, "play-2-stock");

        // Live update while shown: a server patch moves the bar and disables Save.
        session.progress("fill", 0.9F);
        check(((ProgressComponent) panel.widget("fill")).fraction == 0.9F, "patch moves the shown bar");
        BaseComponent save = panel.widget("save");
        panel.mouseClicked(save.x + 5, save.y + 5, 0);
        check(rig.calls.contains("press save"), "save press reaches the handler");
        session.enabled("save", false);
        check(!panel.widget("save").active, "patch disables save");
        session.text("cancel", Text.lit("Close"));
        check("Close".equals(((ButtonComponent) panel.widget("cancel")).label), "patch renames cancel");
        rig.shot(out, "play-3-patched");

        // The player closes: closed(player) reaches the handler.
        panel.closing();
        check(rig.calls.getLast().equals("closed PLAYER"), "closing reaches the handler: " + rig.calls.getLast());
    }

    /** The notice and menu kinds, an unknown node kind, and a form over the node cap. */
    static void kinds(Rig rig, File out, String tag) {
        rig.server.open("p", Form.notice("example:sell", Text.lit("Sell all?"),
                Text.lit("Sells every item in the chest for 12 emeralds."), Text.lit("Sell"), Text.lit("Keep")), rig.handler);
        rig.shot(out, "notice-" + tag);
        Form.Builder menu = Form.menu("example:menu", Text.lit("Shop"), Text.lit("Pick a counter."));
        menu.button("tools", Text.lit("Tools"), Role.SECONDARY).button("food", Text.lit("Food"), Role.SECONDARY)
                .button("close", Text.lit("Close"), Role.DESTRUCTIVE);
        rig.server.open("p", menu.build(), rig.handler);
        rig.shot(out, "menu-" + tag);

        WireNode chart = new WireNode("chart", "sales", List.of(new WireNode.Field(Fields.FALLBACK,
                Fields.text(Text.lit("Chart: 3 sales today")))), List.of());
        rig.client.receive(new ScreenMessage.Open(90, "example:future", Kind.CUSTOM, Text.lit("Future"), Size.NARROW,
                WireNode.of("column", "").withChildren(chart)).encode());
        BenchMock.MockDraw d = rig.shot(out, "unknown-" + tag);
        check(d.texts.stream().anyMatch(t -> t.text().equals("Chart: 3 sales today")), tag + " unknown kind draws its fallback");

        List<WireNode> many = new ArrayList<>();
        for (int i = 0; i < 512; i++) {
            many.add(WireNode.of("text", ""));
        }
        int sent = rig.inputs.size();
        boolean drawn = rig.client.receive(new ScreenMessage.Open(91, "example:big", Kind.CUSTOM, Text.lit("Big"), Size.NARROW,
                new WireNode("column", "", List.of(), many)).encode());
        check(!drawn && rig.inputs.size() == sent + 1 && rig.inputs.getLast() instanceof InputMessage.Closed c
                && c.session() == 91 && c.reason() == CloseReason.INVALID, tag + " over-cap form answered closed(invalid)");
    }

    /** No two drawn widgets overlap, and each stays inside the panel. */
    static void noOverlap(FormPanel panel, String tag) {
        List<BaseComponent> shown = panel.widgets().stream().filter(w -> w.visible).toList();
        Box bounds = panel.bounds();
        for (int i = 0; i < shown.size(); i++) {
            Box a = shown.get(i).box();
            check(bounds.contains(a), tag + " " + a + " inside " + bounds);
            for (int j = i + 1; j < shown.size(); j++) {
                Box b = shown.get(j).box();
                check(!a.intersects(b), tag + " " + a + " overlaps " + b);
            }
        }
    }

    static void check(boolean ok, String what) {
        if (!ok) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }
}
