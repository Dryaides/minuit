package dev.dryaides.minuit.formui;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Icon;
import dev.dryaides.minuit.ButtonComponent;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.DropdownComponent;
import dev.dryaides.minuit.IconArt;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.ProgressComponent;
import dev.dryaides.minuit.ScrollComponent;
import dev.dryaides.minuit.SliderComponent;
import dev.dryaides.minuit.TabsComponent;
import dev.dryaides.minuit.TextFieldComponent;
import dev.dryaides.minuit.ToggleComponent;
import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Palette;
import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.Words;
import dev.dryaides.minuit.form.CloseReason;
import dev.dryaides.minuit.form.Form;
import dev.dryaides.minuit.form.Role;
import dev.dryaides.minuit.form.Size;
import dev.dryaides.minuit.form.Text;
import dev.dryaides.minuit.internal.Maths;
import dev.dryaides.minuit.wire.Fields;
import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.Limits;
import dev.dryaides.minuit.wire.ScreenMessage;
import dev.dryaides.minuit.wire.WireException;
import dev.dryaides.minuit.wire.WireNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/**
 * A screen a server sent, built from the kit's widgets in the player's theme: a title, a body that
 * scrolls when it is taller than the window, and the form's footer kept in place under it. The
 * player's changes go out through {@code out} as they happen; {@link #apply} takes the server's
 * patches. Hosted like any panel; {@link FormClient} creates it from the bytes.
 */
public final class FormPanel extends PanelComponent {

    /** Below this panel width rows stack, short dropdowns become radio toggles and crowded tabs a dropdown. */
    public static final int NARROW = 260;
    /** A slider sends at most one change per this many milliseconds while dragged. */
    public static final long SLIDER_MS = 100;
    /** A text field sends at most one change per this many milliseconds while the player types. */
    public static final long FIELD_MS = 200;

    private static final int PAD = 8;
    private static final int TITLE = 22;
    private static final int GAP = 4;
    private static final int LINE = 10;
    private static final int CONTROL = 20;
    private static final int SMALL = 16;
    private static final int HEADER = 14;

    private final ScreenMessage.Open open;
    private final Live root;
    private final Live footer;
    private final ToIntFunction<String> font;
    private final Consumer<InputMessage> out;
    private int width;
    private int maxHeight;
    private int height;
    private int seq;
    private boolean quiet;
    private boolean building;
    private ScrollComponent scroll;
    private final Map<String, BaseComponent> byId = new HashMap<>();
    private final Map<String, Integer> pending = new HashMap<>();
    private final Map<String, Long> sentAt = new HashMap<>();
    private final Map<String, Dirty> dirty = new LinkedHashMap<>();

    private record Dirty(Live node, byte[] value, long every) {
    }

    private FormPanel(Theme theme, Words words, ToIntFunction<String> font, ScreenMessage.Open open,
                      Consumer<InputMessage> out) {
        super(theme, words, 0, 0);
        this.open = open;
        this.font = font;
        this.out = out;
        this.root = new Live(open.root());
        Live last = this.root.children.isEmpty() ? null : this.root.children.get(this.root.children.size() - 1);
        this.footer = last != null && last.kind.equals("row") && last.id.equals(Form.FOOTER) ? last : null;
    }

    /**
     * The panel for {@code open}, laid out by {@link #fit} on a screen of {@code screenW} by
     * {@code screenH} GUI pixels.
     *
     * @param font the width of a string in GUI pixels, for wrapping
     * @param out  where the player's input goes, already numbered for the session
     */
    public static FormPanel of(Theme theme, Words words, ToIntFunction<String> font, int screenW, int screenH,
                               ScreenMessage.Open open, Consumer<InputMessage> out) {
        FormPanel panel = new FormPanel(theme, words, font, open, out);
        panel.fit(theme, screenW, screenH);
        return panel;
    }

    /**
     * Lays the panel out again in {@code theme}, centred on a screen of {@code screenW} by
     * {@code screenH} GUI pixels: 200, 300 or 400 px wide by the form's size, never wider than the
     * screen less 16. Values, pending input, focus and scroll stay; for a resize or a theme change.
     */
    public void fit(Theme theme, int screenW, int screenH) {
        int wanted = this.open.size() == Size.NARROW ? 200 : this.open.size() == Size.WIDE ? 400 : 300;
        this.theme = theme;
        this.width = Math.min(wanted, screenW - 16);
        this.maxHeight = screenH - 16;
        this.left = 0;
        this.top = 0;
        rebuild();
        this.left = (screenW - this.width) / 2;
        this.top = Math.max(8, (screenH - this.height) / 2);
        rebuild();
    }

    public int session() {
        return this.open.session();
    }

    public String screenId() {
        return this.open.screenId();
    }

    /** The widget drawn for a node, or null while the node is not shown. */
    public BaseComponent widget(String nodeId) {
        return this.byId.get(nodeId);
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        return this.height;
    }

    /**
     * A patch from the server. A value the player changed after the patch's {@code ack} stays as the
     * player left it; any other field takes the server's value, and the screen is laid out again.
     */
    public void apply(ScreenMessage.Patch patch) {
        if (patch.session() != session()) {
            return;
        }
        boolean relayout = false;
        for (ScreenMessage.FieldSet set : patch.sets()) {
            Live node = set.nodeId().isEmpty() ? null : this.root.find(set.nodeId());
            if (node == null) {
                continue;
            }
            if (set.tag() == Fields.VALUE) {
                Integer mine = this.pending.get(set.nodeId());
                if (mine != null && mine > patch.ack()) {
                    continue;
                }
                this.pending.remove(set.nodeId());
                this.dirty.remove(set.nodeId());
            }
            if (set.tag() == Fields.CHILDREN) {
                try {
                    node.setChildren(WireNode.decodeChildren(set.value()));
                } catch (WireException e) {
                    continue;
                }
                relayout = true;
            } else {
                node.fields.put(set.tag(), set.value());
                relayout |= !inPlace(node, set.tag());
            }
        }
        if (relayout) {
            rebuild();
        }
    }

    /**
     * Shows a new field value on the widget already drawn, so a patch does not interrupt a drag or
     * the typing in another field. False when the screen has to be laid out again.
     */
    private boolean inPlace(Live node, int tag) {
        BaseComponent w = this.byId.get(node.id);
        if (w == null) {
            // Not drawn: a leaf under a folded section or another tab keeps its new field for later.
            return node.children.isEmpty() && tag != Fields.VISIBLE && !node.kind.equals("row")
                    && !node.kind.equals("column") && !node.kind.equals("text");
        }
        if (tag == Fields.FRACTION && w instanceof ProgressComponent bar) {
            int pm = node.svarint(Fields.FRACTION, -1);
            bar.fraction = pm < 0 ? -1 : Math.min(1000, pm) / 1000F;
            return true;
        }
        if (tag == Fields.VALUE) {
            if (w instanceof ToggleComponent t && node.kind.equals("toggle")) {
                t.value = node.bool(Fields.VALUE, false);
            } else if (w instanceof SliderComponent sl) {
                sl.value = node.svarint(Fields.VALUE, 0);
            } else if (w instanceof TextFieldComponent f) {
                f.setText(node.str(Fields.VALUE));
            } else {
                return false;
            }
            return true;
        }
        if (tag == Fields.LABEL) {
            if (w instanceof ToggleComponent t && node.kind.equals("toggle")) {
                t.label = say(node.text(Fields.LABEL));
            } else if (w instanceof ButtonComponent b) {
                b.label = say(node.text(Fields.LABEL));
            } else {
                return false;
            }
            return true;
        }
        if (tag == Fields.TOOLTIP) {
            Text tip = node.text(Fields.TOOLTIP);
            w.tooltip = tip == null ? null : List.of(say(tip));
            return true;
        }
        return false;
    }

    /** Sends the throttled changes that are due, and a field's at once when it lost the keyboard. */
    @Override
    public void tick() {
        long now = BaseComponent.clock.getAsLong();
        for (Dirty d : List.copyOf(this.dirty.values())) {
            BaseComponent w = this.byId.get(d.node.id);
            boolean left = d.every == FIELD_MS && (w == null || !w.focused);
            if (left || now - this.sentAt.getOrDefault(d.node.id, Long.MIN_VALUE / 2) >= d.every) {
                this.dirty.remove(d.node.id);
                emit(d.node, d.value);
            }
        }
    }

    @Override
    public void closing() {
        leave(CloseReason.PLAYER);
    }

    /** Sends what is still throttled, then {@code closed(why)}, once. */
    void leave(CloseReason why) {
        if (this.quiet) {
            return;
        }
        for (Dirty d : List.copyOf(this.dirty.values())) {
            emit(d.node, d.value);
        }
        this.dirty.clear();
        this.quiet = true;
        this.out.accept(new InputMessage.Closed(session(), ++this.seq, why));
    }

    /** The server closed or replaced the screen: closing sends nothing back. */
    void silence() {
        this.quiet = true;
    }

    @Override
    public List<Box> textBoxes() {
        List<Box> boxes = new ArrayList<>(super.textBoxes());
        boxes.add(new Box("title", this.left + PAD, this.top + 6, this.width - 2 * PAD, 10));
        return boxes;
    }

    @Override
    protected void drawBackground(Draw d, int mx, int my) {
        Palette p = this.theme.palette();
        this.theme.frame().panel(d, p, this.left, this.top, this.width, this.height);
        d.textFit(say(this.open.title()), this.left + PAD, this.top + 8, this.width - 2 * PAD, p.text());
        d.fill(this.left + PAD, this.top + TITLE - 3, this.left + this.width - PAD, this.top + TITLE - 2, p.surfaceEdge());
    }

    // Input

    private void change(Live node, byte[] value, long every) {
        node.fields.put(Fields.VALUE, value);
        if (this.building) {
            return;
        }
        long now = BaseComponent.clock.getAsLong();
        if (every > 0 && now - this.sentAt.getOrDefault(node.id, Long.MIN_VALUE / 2) < every) {
            this.dirty.put(node.id, new Dirty(node, value, every));
            return;
        }
        this.dirty.remove(node.id);
        emit(node, value);
    }

    private void flush(String nodeId) {
        Dirty d = this.dirty.remove(nodeId);
        if (d != null) {
            emit(d.node, d.value);
        }
    }

    private void emit(Live node, byte[] value) {
        int n = ++this.seq;
        this.pending.put(node.id, n);
        this.sentAt.put(node.id, BaseComponent.clock.getAsLong());
        this.out.accept(new InputMessage.Change(session(), n, node.id, value));
    }

    private void press(String nodeId) {
        this.out.accept(new InputMessage.Press(session(), ++this.seq, nodeId));
    }

    // Layout

    private void rebuild() {
        String focusId = null;
        for (Map.Entry<String, BaseComponent> e : this.byId.entrySet()) {
            if (e.getValue() == focus()) {
                focusId = e.getKey();
            }
        }
        int oldScroll = this.scroll == null ? 0 : this.scroll.scroll();
        this.building = true;
        this.widgets.clear();
        this.byId.clear();
        this.overlay = null;
        focus(null);
        boolean narrow = this.width < NARROW;
        int innerW = this.width - 2 * PAD;
        int bodyTop = this.top + TITLE + 2;
        int footerH = this.footer == null || !this.footer.bool(Fields.VISIBLE, true) ? 0
                : build(this.footer, 0, 0, innerW, narrow, true, null);
        int chrome = TITLE + 2 + PAD + (footerH > 0 ? footerH + GAP : 0);
        int content = body(0, 0, innerW, narrow, null);
        int room = this.maxHeight - chrome;
        Consumer<BaseComponent> place = this::add;
        this.scroll = null;
        if (content > room) {
            this.scroll = new ScrollComponent(new Box("body", this.left + PAD, bodyTop, innerW, room));
            content = body(this.left + PAD, bodyTop, innerW - ScrollComponent.BAR - 2, narrow, null);
            this.scroll.bottomRoom = 2;
            ScrollComponent target = this.scroll;
            place = target::add;
        }
        int shown = Math.min(content, room);
        if (this.scroll != null) {
            body(this.left + PAD, bodyTop, innerW - ScrollComponent.BAR - 2, narrow, place);
            add(this.scroll);
            this.scroll.setScroll(oldScroll);
        } else {
            body(this.left + PAD, bodyTop, innerW, narrow, place);
        }
        if (footerH > 0) {
            build(this.footer, this.left + PAD, bodyTop + shown + GAP, innerW, narrow, true, this::add);
        }
        this.height = chrome + shown;
        this.building = false;
        BaseComponent again = focusId == null ? null : this.byId.get(focusId);
        if (again != null && again instanceof TextFieldComponent) {
            focus(this.scroll != null ? this.scroll : again);
            again.focused = true;
        }
    }

    /** The root's children but the footer, stacked. */
    private int body(int x, int y, int w, boolean narrow, Consumer<BaseComponent> place) {
        List<Live> nodes = new ArrayList<>(this.root.children);
        nodes.remove(this.footer);
        return stack(nodes, x, y, w, narrow, true, place, gap(this.root));
    }

    private static int gap(Live node) {
        return Math.max(0, node.varint(Fields.GAP, GAP));
    }

    private int stack(List<Live> nodes, int x, int y, int w, boolean narrow, boolean enabled,
                      Consumer<BaseComponent> place, int gap) {
        int at = 0;
        for (Live child : nodes) {
            int h = build(child, x, y + at, w, narrow, enabled, place);
            if (h > 0) {
                at += h + gap;
            }
        }
        return Math.max(0, at - gap);
    }

    /**
     * Lays out {@code node} at ({@code x}, {@code y}) in {@code w} and returns its height. With a
     * null {@code place} it only measures.
     */
    private int build(Live node, int x, int y, int w, boolean narrow, boolean enabled, Consumer<BaseComponent> place) {
        if (!node.bool(Fields.VISIBLE, true)) {
            return 0;
        }
        boolean on = enabled && node.bool(Fields.ENABLED, true);
        Text label = node.text(Fields.LABEL);
        return switch (node.kind) {
            case "column", "page" -> stack(node.children, x, y, w, narrow, on, place, gap(node));
            case "row" -> row(node, x, y, w, narrow, on, place);
            case "section" -> section(node, x, y, w, narrow, on, place);
            case "tabs" -> tabs(node, x, y, w, narrow, on, place);
            case "text" -> lines(node.id, label, x, y, w, node.varint(Fields.LINES, 0), place);
            case "toggle" -> put(place, node, new ToggleComponent(new Box("", x, y, w, SMALL), say(label),
                    node.bool(Fields.VALUE, false), v -> change(node, Fields.bool(v), 0)), on, SMALL);
            case "slider" -> slider(node, label, x, y, w, on, place);
            case "field" -> field(node, label, x, y, w, on, place);
            case "dropdown" -> dropdown(node, label, x, y, w, narrow, on, place);
            case "button" -> button(node, label, x, y, w, on, place);
            case "progress" -> {
                int top = label == null ? 0 : lines("", label, x, y, w, 1, place) + 2;
                ProgressComponent bar = new ProgressComponent(new Box("", x, y + top, w, 8));
                int pm = node.svarint(Fields.FRACTION, -1);
                bar.fraction = pm < 0 ? -1 : Math.min(1000, pm) / 1000F;
                yield top + put(place, node, bar, on, 8);
            }
            case "grid" -> grid(node, x, y, w, on, place);
            default -> {
                // An unknown kind, or an image this client cannot draw yet: its fallback, then its children.
                Text fallback = node.text(Fields.FALLBACK);
                int top = fallback == null ? 0 : lines(node.id, fallback, x, y, w, 0, place);
                int below = stack(node.children, x, y + top + (top > 0 ? GAP : 0), w, narrow, on, place, GAP);
                yield top + (top > 0 && below > 0 ? GAP : 0) + below;
            }
        };
    }

    private int put(Consumer<BaseComponent> place, Live node, BaseComponent widget, boolean on, int h) {
        if (place != null) {
            widget.active = on;
            Text tip = node.text(Fields.TOOLTIP);
            if (tip != null) {
                widget.tooltip(say(tip));
            }
            place.accept(widget);
            if (!node.id.isEmpty()) {
                this.byId.put(node.id, widget);
            }
        }
        return h;
    }

    private int row(Live node, int x, int y, int w, boolean narrow, boolean on, Consumer<BaseComponent> place) {
        List<Live> shown = node.children.stream().filter(c -> c.bool(Fields.VISIBLE, true)).toList();
        if (narrow || shown.isEmpty()) {
            return stack(shown, x, y, w, narrow, on, place, gap(node));
        }
        int gap = gap(node);
        int total = 0;
        for (Live c : shown) {
            total += Math.max(1, c.varint(Fields.WEIGHT, 1));
        }
        int room = w - gap * (shown.size() - 1);
        int at = x;
        int used = 0;
        int h = 0;
        for (int i = 0; i < shown.size(); i++) {
            Live c = shown.get(i);
            used += Math.max(1, c.varint(Fields.WEIGHT, 1));
            int end = x + (int) ((long) room * used / total) + gap * i;
            h = Math.max(h, build(c, at, y, end - at, false, on, place));
            at = end + gap;
        }
        return h;
    }

    private int section(Live node, int x, int y, int w, boolean narrow, boolean on, Consumer<BaseComponent> place) {
        boolean open = node.bool(Fields.OPEN, true);
        put(place, node, new Fold(new Box("", x, y, w, HEADER), say(node.text(Fields.LABEL)), open, () -> {
            node.fields.put(Fields.OPEN, Fields.bool(!open));
            rebuild();
        }), on, HEADER);
        if (!open) {
            return HEADER;
        }
        int inner = stack(node.children, x + 6, y + HEADER + GAP, w - 6, narrow, on, place, gap(node));
        return HEADER + (inner > 0 ? GAP + inner : 0);
    }

    private int tabs(Live node, int x, int y, int w, boolean narrow, boolean on, Consumer<BaseComponent> place) {
        List<Live> pages = node.children.stream().filter(c -> c.bool(Fields.VISIBLE, true)).toList();
        if (pages.isEmpty()) {
            return 0;
        }
        int selected = Maths.clamp(node.varint(Fields.VALUE, 0), 0, pages.size() - 1);
        List<String> labels = pages.stream().map(p -> say(p.text(Fields.LABEL))).toList();
        int need = 0;
        for (String l : labels) {
            need += this.font.applyAsInt(l) + 12 + TabsComponent.GAP;
        }
        int bar;
        if (narrow && need > w) {
            bar = put(place, node, choice(node, labels, selected, x, y, w, () -> rebuild()), on, CONTROL);
        } else {
            bar = put(place, node, new TabsComponent(new Box("", x, y, w, SMALL), labels, selected, i -> {
                change(node, Fields.varint(i), 0);
                rebuild();
            }), on, SMALL) + 3;
        }
        int page = build(pages.get(selected), x, y + bar + GAP + 2, w, narrow, on, place);
        return bar + GAP + 2 + page;
    }

    private int slider(Live node, Text label, int x, int y, int w, boolean on, Consumer<BaseComponent> place) {
        int top = label == null ? 0 : lines("", label, x, y, w, 1, place);
        int min = node.svarint(Fields.MIN, 0);
        int max = Math.max(min, node.svarint(Fields.MAX, 0));
        int step = Math.max(1, node.svarint(Fields.STEP, 1));
        SliderComponent[] made = new SliderComponent[1];
        made[0] = new SliderComponent(new Box("", x, y + top, w, SMALL), min, max, node.svarint(Fields.VALUE, min), v -> {
            int snapped = Math.min(max, min + Math.round((v - min) / (float) step) * step);
            made[0].value = snapped;
            if (node.svarint(Fields.VALUE, min) != snapped) {
                change(node, Fields.svarint(snapped), SLIDER_MS);
            }
        });
        return top + put(place, node, made[0], on, SMALL);
    }

    private int field(Live node, Text label, int x, int y, int w, boolean on, Consumer<BaseComponent> place) {
        int top = label == null ? 0 : lines("", label, x, y, w, 1, place);
        int max = Maths.clamp(node.varint(Fields.MAX_LENGTH, Limits.FIELD_LENGTH), 1, Limits.FIELD_LENGTH);
        IntPredicate allowed = switch (node.varint(Fields.CHARSET, 0)) {
            case 1 -> c -> c >= '0' && c <= '9';
            case 2 -> c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-';
            default -> c -> !Character.isISOControl(c);
        };
        TextFieldComponent f = new TextFieldComponent(new Box("", x, y + top, w, 18), node.str(Fields.VALUE),
                s -> change(node, Fields.str(s, Limits.FIELD_LENGTH), FIELD_MS))
                .maxLength(max).filter(allowed).onSubmit(s -> flush(node.id));
        Text hint = node.text(Fields.PLACEHOLDER);
        if (hint != null) {
            f.placeholder(say(hint));
        }
        return top + put(place, node, f, on, 18);
    }

    private int dropdown(Live node, Text label, int x, int y, int w, boolean narrow, boolean on,
                         Consumer<BaseComponent> place) {
        int top = label == null ? 0 : lines("", label, x, y, w, 1, place);
        List<String> options = node.options().stream().map(this::say).toList();
        int selected = node.varint(Fields.VALUE, 0);
        if (narrow && options.size() <= 4) {
            for (int i = 0; i < options.size(); i++) {
                int index = i;
                ToggleComponent radio = new ToggleComponent(new Box("", x, y + top + i * SMALL, w, SMALL), options.get(i), i == selected,
                        v -> {
                            change(node, Fields.varint(index), 0);
                            rebuild();
                        });
                put(place, i == selected ? node : new Live(new WireNode("toggle", "", List.of(), List.of())), radio, on, SMALL);
            }
            return top + options.size() * SMALL;
        }
        return top + put(place, node, choice(node, options, selected, x, y + top, w, () -> rebuild()), on, CONTROL - 2);
    }

    /** A dropdown over {@code options} that sends the picked index as the node's value. */
    private DropdownComponent choice(Live node, List<String> options, int selected, int x, int y, int w, Runnable after) {
        String shown = selected >= 0 && selected < options.size() ? options.get(selected) : "";
        DropdownComponent drop = new DropdownComponent(new Box("", x, y, w, CONTROL - 2), shown, x, w, o -> this.overlay = o);
        List<DropdownComponent.Line> lines = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            int index = i;
            lines.add(new DropdownComponent.Entry(options.get(i), "", "", "",
                    i == selected ? DropdownComponent.State.SELECTED : DropdownComponent.State.SELECTABLE, -1, List.of(), () -> {
                change(node, Fields.varint(index), 0);
                after.run();
            }));
        }
        drop.lines = lines;
        return drop;
    }

    private int button(Live node, Text label, int x, int y, int w, boolean on, Consumer<BaseComponent> place) {
        Role role = Role.PRIMARY;
        byte[] raw = node.fields.get(Fields.ROLE);
        if (raw != null) {
            try {
                role = Fields.readRole(raw);
            } catch (WireException e) {
                role = Role.SECONDARY;
            }
        }
        ButtonComponent b = new ButtonComponent(new Box("", x, y, w, CONTROL), say(label), () -> press(node.id));
        switch (role) {
            case PRIMARY, HERO -> b.style(ButtonComponent.Style.PRIMARY);
            case SECONDARY -> b.style(ButtonComponent.Style.RAISED);
            case DESTRUCTIVE -> b.style(ButtonComponent.Style.RAISED).icon(Icon.WARN);
        }
        return put(place, node, b, on, CONTROL);
    }

    private int grid(Live node, int x, int y, int w, boolean on, Consumer<BaseComponent> place) {
        List<Live> cells = node.children.stream().filter(c -> c.bool(Fields.VISIBLE, true)).toList();
        int columns = Math.max(1, node.varint(Fields.COLUMNS, 1));
        Box[] cols = new Box("", x, y, w, CONTROL).columns(columns, GAP);
        int rows = (cells.size() + columns - 1) / columns;
        for (int i = 0; i < cells.size(); i++) {
            Box c = cols[i % columns];
            build(cells.get(i), c.x(), y + (i / columns) * (CONTROL + GAP), c.w(), false, on, place);
        }
        return rows == 0 ? 0 : rows * (CONTROL + GAP) - GAP;
    }

    private int lines(String id, Text text, int x, int y, int w, int cap, Consumer<BaseComponent> place) {
        if (text == null) {
            return 0;
        }
        List<String> wrapped = wrap(say(text), w);
        if (cap > 0 && wrapped.size() > cap) {
            wrapped = new ArrayList<>(wrapped.subList(0, cap));
            wrapped.set(cap - 1, wrapped.get(cap - 1) + "...");
        }
        int h = wrapped.size() * LINE;
        if (place != null) {
            Lines widget = new Lines(new Box("", x, y, w, h), wrapped, text);
            place.accept(widget);
            if (!id.isEmpty()) {
                this.byId.put(id, widget);
            }
        }
        return h;
    }

    private List<String> wrap(String text, int w) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (this.font.applyAsInt(next) <= w) {
                    line.setLength(0);
                    line.append(next);
                    continue;
                }
                if (!line.isEmpty()) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                String rest = word;
                while (this.font.applyAsInt(rest) > w && rest.length() > 1) {
                    int cut = rest.length() - 1;
                    while (cut > 1 && this.font.applyAsInt(rest.substring(0, cut)) > w) {
                        cut--;
                    }
                    out.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                line.append(rest);
            }
            out.add(line.toString());
        }
        return out;
    }

    /** A text in the player's language: the key through {@link Words}, its fallback when the key is missing. */
    String say(Text text) {
        if (text == null) {
            return "";
        }
        if (text instanceof Text.Lit lit) {
            return lit.text();
        }
        Text.Tr tr = (Text.Tr) text;
        Object[] args = tr.args().stream().map(this::say).toArray();
        String found = this.words.get(tr.key(), args);
        if (!found.equals(tr.key())) {
            return found;
        }
        try {
            return dev.dryaides.minuit.internal.Fmt.apply(tr.fallback(), args);
        } catch (RuntimeException e) {
            return tr.fallback();
        }
    }

    private int color(Text text, Palette p) {
        Text.Style style = text instanceof Text.Tr tr ? tr.style() : Text.Style.PLAIN;
        return switch (style) {
            case PLAIN -> p.text();
            case MUTED -> p.textSoft();
            case ACCENT -> p.accent();
            case WARN -> p.warn();
        };
    }

    /** Wrapped text in its style's palette role. */
    private final class Lines extends BaseComponent {
        final List<String> lines;
        final Text text;

        Lines(Box box, List<String> lines, Text text) {
            super(box);
            this.lines = lines;
            this.text = text;
        }

        @Override
        protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
            int c = this.active ? color(this.text, theme.palette()) : theme.palette().disabledText();
            for (int i = 0; i < this.lines.size(); i++) {
                d.text(this.lines.get(i), this.x, this.y + 1 + i * LINE, c);
            }
        }
    }

    /** A section's title line with its fold arrow; a click or Enter folds and unfolds it. */
    private static final class Fold extends BaseComponent {
        final String title;
        final boolean open;
        final Runnable toggle;

        Fold(Box box, String title, boolean open, Runnable toggle) {
            super(box);
            this.title = title;
            this.open = open;
            this.toggle = toggle;
        }

        @Override
        protected void render(Draw d, Theme theme, int mx, int my, boolean hovered) {
            Palette p = theme.palette();
            int c = !this.active ? p.disabledText() : hovered ? p.accent() : p.text();
            IconArt arrow = theme.icon(this.open ? Icon.CARET : Icon.NEXT);
            arrow.draw(d, this.x, Draw.iconTop(this.y + (this.h - 8) / 2, arrow.height()), c, 1F);
            int tx = this.x + arrow.width() + 4;
            d.textFit(this.title, tx, this.y + (this.h - 8) / 2, this.x + this.w - tx, c);
            d.fill(this.x, this.y + this.h - 1, this.x + this.w, this.y + this.h, p.surfaceEdge());
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            return button == 0 && this.active && isOver(mx, my) && activate();
        }

        @Override
        protected boolean navigable() {
            return true;
        }

        @Override
        protected boolean activate() {
            if (!this.active) {
                return false;
            }
            click();
            this.toggle.run();
            return true;
        }
    }
}
