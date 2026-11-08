package dev.dryaides.minuit.form;

import dev.dryaides.minuit.wire.Fields;
import dev.dryaides.minuit.wire.Limits;
import dev.dryaides.minuit.wire.ScreenMessage;
import dev.dryaides.minuit.wire.WireNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * A screen as data, built once and sent to any number of players. The root is a static
 * {@code column}; a footer, when the form has one, is its last child, a {@code row} with the id
 * {@link #FOOTER}, which the client keeps in place under the scrolling body.
 *
 * <pre>{@code
 * Form form = Form.custom("myshop:settings")
 *         .title(Text.lit("Shop settings"))
 *         .toggle("sounds", Text.lit("Sounds"), true)
 *         .slider("volume", Text.lit("Volume"), 0, 100, 5, 80)
 *         .footer(r -> r.button("save", Text.lit("Save"), Role.PRIMARY))
 *         .build();
 * }</pre>
 */
public final class Form {

    private final String screenId;
    private final Kind kind;
    private final Text title;
    private final Size size;
    private final WireNode root;

    private Form(String screenId, Kind kind, Text title, Size size, WireNode root) {
        this.screenId = screenId;
        this.kind = kind;
        this.title = title;
        this.size = size;
        this.root = root;
    }

    public String screenId() {
        return this.screenId;
    }

    public Kind kind() {
        return this.kind;
    }

    public Text title() {
        return this.title;
    }

    public Size size() {
        return this.size;
    }

    WireNode root() {
        return this.root;
    }

    /** The id of the footer row. */
    public static final String FOOTER = "footer";

    private static final Pattern SCREEN_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** A screen of inputs, sections and tabs. */
    public static Builder custom(String screenId) {
        return new Builder(screenId, Kind.CUSTOM);
    }

    /** A body and a list of buttons: add them with {@link Nodes#button}. */
    public static Builder menu(String screenId, Text title, Text body) {
        Builder builder = new Builder(screenId, Kind.MENU).title(title);
        builder.text(body);
        return builder;
    }

    /** A body and two buttons, {@code yes} (id {@code yes}) and {@code no} (id {@code no}). */
    public static Form notice(String screenId, Text title, Text body, Text yes, Text no) {
        Builder builder = new Builder(screenId, Kind.NOTICE).title(title).size(Size.NARROW);
        builder.text(body);
        return builder.footer(r -> r.button("yes", yes, Role.PRIMARY).button("no", no, Role.SECONDARY)).build();
    }

    /** The open message of this form for one session. */
    public ScreenMessage.Open open(int session) {
        return new ScreenMessage.Open(session, this.screenId, this.kind, this.title, this.size, this.root);
    }

    /** The nodes of a container, in order. Each call adds one node; the modifiers change the last one. */
    public static class Nodes<B extends Nodes<B>> {

        final List<WireNode> nodes = new ArrayList<>();

        @SuppressWarnings("unchecked")
        private B self() {
            return (B) this;
        }

        private B add(String kind, String id, List<WireNode.Field> fields, List<WireNode> children) {
            this.nodes.add(new WireNode(kind, id, fields, children));
            return self();
        }

        private static List<WireNode.Field> fields(Object... tagValue) {
            List<WireNode.Field> fields = new ArrayList<>();
            for (int i = 0; i < tagValue.length; i += 2) {
                fields.add(new WireNode.Field((Integer) tagValue[i], (byte[]) tagValue[i + 1]));
            }
            return fields;
        }

        private static List<WireNode> children(Consumer<Column> body) {
            Column column = new Column();
            body.accept(column);
            return column.nodes;
        }

        /** Static text, wrapped to the width. */
        public B text(Text body) {
            return text("", body);
        }

        /** Text the server can change later by its id. */
        public B text(String id, Text body) {
            return add("text", id, fields(Fields.LABEL, Fields.text(body)), List.of());
        }

        public B toggle(String id, Text label, boolean value) {
            return add("toggle", id, fields(Fields.LABEL, Fields.text(label), Fields.VALUE, Fields.bool(value)), List.of());
        }

        /** An integer slider from {@code min} to {@code max}, on multiples of {@code step} from {@code min}. */
        public B slider(String id, Text label, int min, int max, int step, int value) {
            if (max < min || step < 1 || value < min || value > max || (value - min) % step != 0) {
                throw new IllegalArgumentException("slider " + id + ": " + value + " in [" + min + ", " + max + "] by " + step);
            }
            return add("slider", id, fields(Fields.LABEL, Fields.text(label), Fields.VALUE, Fields.svarint(value),
                    Fields.MIN, Fields.svarint(min), Fields.MAX, Fields.svarint(max), Fields.STEP, Fields.svarint(step)),
                    List.of());
        }

        /** A one-line text field; {@code spec} sets its value, placeholder, length and characters. */
        public B field(String id, Text label, Consumer<FieldSpec> spec) {
            FieldSpec f = new FieldSpec();
            spec.accept(f);
            List<WireNode.Field> list = fields(Fields.LABEL, Fields.text(label),
                    Fields.VALUE, Fields.str(f.value, Limits.FIELD_LENGTH));
            if (f.placeholder != null) {
                list.add(new WireNode.Field(Fields.PLACEHOLDER, Fields.text(f.placeholder)));
            }
            list.add(new WireNode.Field(Fields.MAX_LENGTH, Fields.varint(f.maxLength)));
            if (f.charset != Charset.ANY) {
                list.add(new WireNode.Field(Fields.CHARSET, Fields.varint(f.charset.ordinal())));
            }
            return add("field", id, list, List.of());
        }

        public B dropdown(String id, Text label, List<Text> options, int value) {
            if (value < 0 || value >= options.size()) {
                throw new IllegalArgumentException("dropdown " + id + ": option " + value + " of " + options.size());
            }
            return add("dropdown", id, fields(Fields.LABEL, Fields.text(label), Fields.VALUE, Fields.varint(value),
                    Fields.OPTIONS, Fields.options(options)), List.of());
        }

        public B button(String id, Text label, Role role) {
            return add("button", id, fields(Fields.LABEL, Fields.text(label), Fields.ROLE, Fields.role(role)), List.of());
        }

        public B button(String id, Text label, Role role, Image image) {
            return add("button", id, fields(Fields.LABEL, Fields.text(label), Fields.ROLE, Fields.role(role),
                    Fields.IMAGE, Fields.image(image)), List.of());
        }

        /** A bar; a negative {@code fraction} means working with no measure. */
        public B progress(String id, Text label, float fraction) {
            return add("progress", id, fields(Fields.LABEL, Fields.text(label), Fields.FRACTION,
                    Fields.svarint(perMille(fraction))), List.of());
        }

        public B image(String id, Image image) {
            return add("image", id, fields(Fields.IMAGE, Fields.image(image)), List.of());
        }

        /** A part the player folds and unfolds by its title. */
        public B section(String id, Text title, boolean open, Consumer<Column> body) {
            return add("section", id, fields(Fields.LABEL, Fields.text(title), Fields.OPEN, Fields.bool(open)),
                    children(body));
        }

        /** Children side by side; below 260 GUI px of panel width they stack. */
        public B row(Consumer<Column> body) {
            return add("row", "", List.of(), children(body));
        }

        /** A row of tabs over one page at a time; the player's page comes back as its index. */
        public B tabs(String id, Consumer<Tabs> pages) {
            Tabs tabs = new Tabs();
            pages.accept(tabs);
            return add("tabs", id, fields(Fields.VALUE, Fields.varint(0)), tabs.pages);
        }

        /** The last node's share of its row, 1 by default. */
        public B weight(int weight) {
            return modify(Fields.WEIGHT, Fields.varint(weight));
        }

        public B tooltip(Text tooltip) {
            return modify(Fields.TOOLTIP, Fields.text(tooltip));
        }

        public B enabled(boolean enabled) {
            return modify(Fields.ENABLED, Fields.bool(enabled));
        }

        public B visible(boolean visible) {
            return modify(Fields.VISIBLE, Fields.bool(visible));
        }

        /** What a client that does not know the last node's kind shows instead. */
        public B fallback(Text fallback) {
            return modify(Fields.FALLBACK, Fields.text(fallback));
        }

        private B modify(int tag, byte[] value) {
            if (this.nodes.isEmpty()) {
                throw new IllegalStateException("no node to modify");
            }
            WireNode last = this.nodes.remove(this.nodes.size() - 1);
            List<WireNode.Field> list = new ArrayList<>();
            for (WireNode.Field f : last.fields()) {
                if (f.tag() != tag) {
                    list.add(f);
                }
            }
            list.add(new WireNode.Field(tag, value));
            this.nodes.add(new WireNode(last.kind(), last.id(), list, last.children()));
            return self();
        }
    }

    /** The nodes of a column, a row, a section or a page. */
    public static final class Column extends Nodes<Column> {
    }

    /** The pages of a {@code tabs} node. */
    public static final class Tabs {

        final List<WireNode> pages = new ArrayList<>();

        public Tabs page(String id, Text label, Consumer<Column> body) {
            this.pages.add(new WireNode("page", id, List.of(new WireNode.Field(Fields.LABEL, Fields.text(label))),
                    Nodes.children(body)));
            return this;
        }
    }

    /** Characters a text field takes. */
    public enum Charset {
        ANY, DIGITS, ID_SAFE
    }

    public static final class FieldSpec {
        String value = "";
        Text placeholder;
        int maxLength = Limits.FIELD_LENGTH;
        Charset charset = Charset.ANY;

        public FieldSpec value(String value) {
            this.value = value;
            return this;
        }

        public FieldSpec placeholder(Text placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        public FieldSpec maxLength(int maxLength) {
            if (maxLength < 1 || maxLength > Limits.FIELD_LENGTH) {
                throw new IllegalArgumentException("maxLength " + maxLength);
            }
            this.maxLength = maxLength;
            return this;
        }

        public FieldSpec charset(Charset charset) {
            this.charset = charset;
            return this;
        }
    }

    public static final class Builder extends Nodes<Builder> {

        private final String screenId;
        private final Kind kind;
        private Text title = Text.lit("");
        private Size size = Size.NORMAL;
        private List<WireNode> footer;

        Builder(String screenId, Kind kind) {
            this.screenId = screenId;
            this.kind = kind;
        }

        public Builder title(Text title) {
            this.title = title;
            return this;
        }

        public Builder size(Size size) {
            this.size = size;
            return this;
        }

        /** The buttons kept in place under the body. */
        public Builder footer(Consumer<Column> row) {
            this.footer = Nodes.children(row);
            return this;
        }

        /**
         * The form, checked as a client will check it: ids well formed and unique, every size cap.
         *
         * @throws IllegalArgumentException for a bad or repeated id
         * @throws dev.dryaides.minuit.wire.WireException for a broken cap
         */
        public Form build() {
            if (this.screenId.length() > Limits.SCREEN_ID || !SCREEN_ID.matcher(this.screenId).matches()) {
                throw new IllegalArgumentException("screen id " + this.screenId);
            }
            List<WireNode> body = new ArrayList<>(this.nodes);
            if (this.footer != null) {
                body.add(new WireNode("row", FOOTER, List.of(), this.footer));
            }
            WireNode root = new WireNode("column", "", List.of(), body);
            uniqueIds(root, new HashSet<>());
            Form form = new Form(this.screenId, this.kind, this.title, this.size, root);
            ScreenMessage.decode(form.open(1).encode());
            return form;
        }

        private static void uniqueIds(WireNode node, Set<String> seen) {
            if (!node.id().isEmpty() && !seen.add(node.id())) {
                throw new IllegalArgumentException("node id " + node.id() + " twice");
            }
            node.children().forEach(child -> uniqueIds(child, seen));
        }
    }

    static int perMille(float fraction) {
        return fraction < 0 ? -1 : Math.round(Math.min(1F, fraction) * 1000);
    }
}
