package dev.dryaides.minuit.form;

import dev.dryaides.minuit.wire.Fields;
import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.Limits;
import dev.dryaides.minuit.wire.WireNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The same form as a vanilla dialog, for a player whose client has no minuit. A {@link FormServer}
 * given {@link Dialogs} shows it to every player its {@link Dialogs#hasForms} turns down.
 *
 * <p>The dialog is a {@code minecraft:multi_action} in the dialog JSON format: text, progress bars,
 * item images and section, page and unknown-kind fallback titles become {@code plain_message} and
 * {@code item} bodies; toggles, sliders, text fields and dropdowns become inputs; enabled buttons
 * become actions. Vanilla keeps the bodies above the inputs, every section unfolded and every tab
 * page one under the other; hidden nodes, disabled inputs, sprites and tab rows are left out. Each
 * button and the exit send a {@code minecraft:custom} click with id {@link #ACTION}, the session and
 * every input; the server applies them through the session's own checks, runs the press, then shows
 * the dialog again with the stored values, or clears it once the session is over.
 */
public final class Fallback {

    /** The id of the custom click every fallback dialog sends. */
    public static final String ACTION = "minuit:form";
    /** Payload key of the session id. */
    public static final String SESSION = "minuit_session";
    /** Payload key of the pressed button's node id. */
    public static final String PRESS = "minuit_press";
    /** Payload key set by the exit button and by Escape. */
    public static final String CLOSED = "minuit_closed";

    /** The vanilla side of a server, for the player type {@code P}. */
    public interface Dialogs<P> {

        /** True when the player's client draws minuit forms (its hello carries {@code form/1}). */
        boolean hasForms(P player);

        /** Shows the dialog JSON, replacing any dialog on the player's screen. */
        void show(P player, String dialog);

        /** Takes the dialog off the player's screen. */
        void clear(P player);
    }

    private Fallback() {
    }

    /** The session's form as dialog JSON, with every value the session stores now. */
    public static String dialog(FormSession session) {
        Form form = session.form();
        int width = switch (form.size()) {
            case NARROW -> 200;
            case NORMAL -> 260;
            case WIDE -> 340;
        };
        Walk walk = new Walk(width);
        walk.node(session.now());
        String extra = ",\"" + SESSION + "\":" + session.id();
        String exit = button(Text.tr("gui.back", "Back"), null, "{\"" + CLOSED + "\":1" + extra + "}");
        StringBuilder out = new StringBuilder("{\"type\":");
        out.append(walk.actions.isEmpty() ? "\"minecraft:notice\"" : "\"minecraft:multi_action\"");
        out.append(",\"title\":").append(text(form.title()));
        out.append(",\"body\":").append(list(walk.body));
        if (!walk.inputs.isEmpty()) {
            out.append(",\"inputs\":").append(list(walk.inputs));
        }
        out.append(",\"can_close_with_escape\":true,\"pause\":false,\"after_action\":\"wait_for_response\"");
        if (walk.actions.isEmpty()) {
            out.append(",\"action\":").append(exit);
        } else {
            List<String> actions = new ArrayList<>();
            for (WireNode b : walk.actions) {
                actions.add(button(Fields.readText(b.field(Fields.LABEL).orElseThrow()),
                        b.field(Fields.TOOLTIP).map(Fields::readText).orElse(null),
                        "{\"" + PRESS + "\":" + str(b.id()) + extra + "}"));
            }
            out.append(",\"actions\":").append(list(actions));
            out.append(",\"columns\":").append(Math.min(2, actions.size()));
            out.append(",\"exit_action\":").append(exit);
        }
        return out.append('}').toString();
    }

    /**
     * The inputs a dialog click carries, as the session would take them from a minuit client: one
     * change per input whose value differs from the stored one, then the press or the close. The
     * payload holds the click's tags as Java values: {@code Boolean} or a number for a toggle, a
     * number for a slider, a {@code String} for a field or a dropdown.
     */
    static List<InputMessage> inputs(FormSession session, Map<String, ?> payload) {
        List<InputMessage> out = new ArrayList<>();
        int seq = session.seq();
        if (payload.containsKey(CLOSED)) {
            out.add(new InputMessage.Closed(session.id(), ++seq, CloseReason.PLAYER));
            return out;
        }
        Walk walk = new Walk(0);
        walk.node(session.now());
        for (WireNode node : walk.inputNodes) {
            Value value = value(node, payload.get(key(node.id())));
            if (value != null && !value.equals(session.value(node.id()))) {
                out.add(new InputMessage.Change(session.id(), ++seq, node.id(), FormSession.write(node.kind(), value)));
            }
        }
        if (payload.get(PRESS) instanceof String press) {
            out.add(new InputMessage.Press(session.id(), ++seq, press));
        }
        return out;
    }

    private static Value value(WireNode node, Object raw) {
        return switch (node.kind()) {
            case "toggle" -> raw instanceof Boolean b ? new Value.Bool(b)
                    : raw instanceof Number n ? new Value.Bool(n.intValue() != 0) : null;
            case "slider" -> raw instanceof Number n ? new Value.Num(Math.round(n.floatValue())) : null;
            case "field" -> raw instanceof String s ? new Value.Str(s) : null;
            case "dropdown" -> {
                if (raw instanceof String s && s.matches("o[0-9]{1,3}")) {
                    yield new Value.Num(Integer.parseInt(s.substring(1)));
                }
                yield null;
            }
            default -> null;
        };
    }

    /** The dialog input key of a node id: vanilla keys take only letters, digits and underscores. */
    static String key(String id) {
        return "v_" + id.replace("_", "__").replace(".", "_d").replace("-", "_h");
    }

    /** One pass over the shown tree, in order. */
    private static final class Walk {
        final int width;
        final List<String> body = new ArrayList<>();
        final List<String> inputs = new ArrayList<>();
        final List<WireNode> inputNodes = new ArrayList<>();
        final List<WireNode> actions = new ArrayList<>();

        Walk(int width) {
            this.width = width;
        }

        void node(WireNode node) {
            if (!flag(node, Fields.VISIBLE)) {
                return;
            }
            boolean enabled = flag(node, Fields.ENABLED);
            switch (node.kind()) {
                case "column", "row" -> children(node, enabled);
                case "tabs" -> children(node, enabled);
                case "section", "page" -> {
                    message(label(node));
                    children(node, enabled);
                }
                case "text" -> message(label(node));
                case "progress" -> {
                    int perMille = Fields.readSvarint(node.field(Fields.FRACTION).orElseThrow());
                    String bar = perMille < 0 ? " ..." : " " + Math.round(perMille / 10F) + "%";
                    message("{\"text\":\"\",\"extra\":[" + text(label(node)) + ",{\"text\":" + str(bar) + "}]}");
                }
                case "image" -> {
                    if (Fields.readImage(node.field(Fields.IMAGE).orElseThrow()) instanceof Image.Item item) {
                        this.body.add("{\"type\":\"minecraft:item\",\"item\":{\"id\":" + str(item.id()) + ",\"count\":1}}");
                    }
                }
                case "button" -> {
                    if (enabled) {
                        this.actions.add(node);
                    }
                }
                case "toggle", "slider", "field", "dropdown" -> {
                    if (enabled) {
                        this.inputNodes.add(node);
                        this.inputs.add(input(node));
                    }
                }
                default -> node.fallback().ifPresent(t -> message(text(t)));
            }
        }

        private void children(WireNode node, boolean enabled) {
            if (enabled) {
                node.children().forEach(this::node);
            }
        }

        private void message(Text label) {
            message(text(label));
        }

        private void message(String component) {
            this.body.add("{\"type\":\"minecraft:plain_message\",\"contents\":" + component + ",\"width\":" + this.width + "}");
        }

        private String input(WireNode node) {
            StringBuilder out = new StringBuilder("{\"key\":").append(str(key(node.id())))
                    .append(",\"label\":").append(text(label(node)));
            if (!node.kind().equals("toggle")) {
                out.append(",\"width\":").append(this.width);
            }
            byte[] value = node.field(Fields.VALUE).orElseThrow();
            switch (node.kind()) {
                case "toggle" -> out.append(",\"type\":\"minecraft:boolean\",\"initial\":").append(Fields.readBool(value));
                case "slider" -> out.append(",\"type\":\"minecraft:number_range\",\"start\":")
                        .append(Fields.readSvarint(node.field(Fields.MIN).orElseThrow()))
                        .append(",\"end\":").append(Fields.readSvarint(node.field(Fields.MAX).orElseThrow()))
                        .append(",\"step\":").append(Fields.readSvarint(node.field(Fields.STEP).orElseThrow()))
                        .append(",\"initial\":").append(Fields.readSvarint(value));
                case "field" -> out.append(",\"type\":\"minecraft:text\",\"initial\":")
                        .append(str(Fields.readStr(value, Limits.FIELD_LENGTH)))
                        .append(",\"max_length\":").append(Fields.readVarint(node.field(Fields.MAX_LENGTH).orElseThrow()));
                default -> {
                    int chosen = Fields.readVarint(value);
                    List<Text> options = Fields.readOptions(node.field(Fields.OPTIONS).orElseThrow());
                    List<String> entries = new ArrayList<>();
                    for (int i = 0; i < options.size(); i++) {
                        entries.add("{\"id\":\"o" + i + "\",\"display\":" + text(options.get(i))
                                + (i == chosen ? ",\"initial\":true}" : "}"));
                    }
                    out.append(",\"type\":\"minecraft:single_option\",\"options\":").append(list(entries));
                }
            }
            return out.append('}').toString();
        }
    }

    private static boolean flag(WireNode node, int tag) {
        return node.field(tag).map(Fields::readBool).orElse(true);
    }

    private static Text label(WireNode node) {
        return node.field(Fields.LABEL).map(Fields::readText).orElse(Text.lit(""));
    }

    private static String button(Text label, Text tooltip, String additions) {
        return "{\"label\":" + text(label) + (tooltip == null ? "" : ",\"tooltip\":" + text(tooltip))
                + ",\"action\":{\"type\":\"minecraft:dynamic/custom\",\"id\":" + str(ACTION)
                + ",\"additions\":" + additions + "}}";
    }

    /** A text component as JSON; the style roles take vanilla's nearest named colours. */
    static String text(Text text) {
        if (text instanceof Text.Lit lit) {
            return "{\"text\":" + str(lit.text()) + "}";
        }
        Text.Tr tr = (Text.Tr) text;
        StringBuilder out = new StringBuilder("{\"translate\":").append(str(tr.key()))
                .append(",\"fallback\":").append(str(tr.fallback()));
        if (!tr.args().isEmpty()) {
            out.append(",\"with\":").append(list(tr.args().stream().map(Fallback::text).toList()));
        }
        switch (tr.style()) {
            case MUTED -> out.append(",\"color\":\"gray\"");
            case ACCENT -> out.append(",\"color\":\"gold\"");
            case WARN -> out.append(",\"color\":\"red\"");
            case PLAIN -> {
            }
        }
        return out.append('}').toString();
    }

    private static String list(List<String> items) {
        return "[" + String.join(",", items) + "]";
    }

    private static String str(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
