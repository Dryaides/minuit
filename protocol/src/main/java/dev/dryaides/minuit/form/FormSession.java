package dev.dryaides.minuit.form;

import dev.dryaides.minuit.wire.Fields;
import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.Limits;
import dev.dryaides.minuit.wire.ScreenMessage;
import dev.dryaides.minuit.wire.WireException;
import dev.dryaides.minuit.wire.WireNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * One open form on one player's screen. The server owns the state: a player's input reaches the
 * {@link FormHandler} only once it fits the form as sent (the node exists, is shown and enabled,
 * takes that input, and the value is in range), and a refused value is sent back as it was stored.
 * The setters send patches while the screen is open.
 */
public final class FormSession {

    private static final Pattern ID_SAFE = Pattern.compile("[a-z0-9_.-]*");

    private final int id;
    private final Form form;
    private final FormHandler handler;
    private final Consumer<byte[]> send;
    private final Map<String, Slot> slots = new HashMap<>();
    private int lastSeq;
    private boolean open = true;

    FormSession(int id, Form form, FormHandler handler, Consumer<byte[]> send) {
        this.id = id;
        this.form = form;
        this.handler = handler;
        this.send = send;
        index(form.root(), null);
    }

    public int id() {
        return this.id;
    }

    public Form form() {
        return this.form;
    }

    public boolean isOpen() {
        return this.open;
    }

    /** The stored value of an input node, or null for a node without one. */
    public Value value(String nodeId) {
        Slot slot = this.slots.get(nodeId);
        if (slot == null) {
            return null;
        }
        byte[] raw = slot.fields.get(Fields.VALUE);
        return raw == null ? null : read(slot.kind, raw);
    }

    /** Sets an input's value; the client moves its widget. */
    public void set(String nodeId, Value value) {
        Slot slot = require(nodeId);
        patch(nodeId, Fields.VALUE, write(slot.kind, value));
    }

    /** The label of a node, or the body of a text node. */
    public void text(String nodeId, Text text) {
        patch(nodeId, Fields.LABEL, Fields.text(text));
    }

    /** A progress bar's fraction; negative means working with no measure. */
    public void progress(String nodeId, float fraction) {
        patch(nodeId, Fields.FRACTION, Fields.svarint(Form.perMille(fraction)));
    }

    public void enabled(String nodeId, boolean enabled) {
        patch(nodeId, Fields.ENABLED, Fields.bool(enabled));
    }

    public void visible(String nodeId, boolean visible) {
        patch(nodeId, Fields.VISIBLE, Fields.bool(visible));
    }

    /** Folds or unfolds a section. */
    public void fold(String nodeId, boolean open) {
        patch(nodeId, Fields.OPEN, Fields.bool(open));
    }

    /** Closes the screen on the client. */
    public void close() {
        if (this.open) {
            this.open = false;
            this.send.accept(new ScreenMessage.Close(this.id, CloseReason.SERVER).encode());
            this.handler.closed(this, CloseReason.SERVER);
        }
    }

    /** The session is over without a word to the client: replaced, or the player left. */
    void end(CloseReason why) {
        if (this.open) {
            this.open = false;
            this.handler.closed(this, why);
        }
    }

    private void patch(String nodeId, int tag, byte[] value) {
        Slot slot = require(nodeId);
        slot.fields.put(tag, value);
        if (this.open) {
            this.send.accept(new ScreenMessage.Patch(this.id, this.lastSeq,
                    List.of(new ScreenMessage.FieldSet(nodeId, tag, value))).encode());
        }
    }

    private Slot require(String nodeId) {
        Slot slot = this.slots.get(nodeId);
        if (slot == null) {
            throw new IllegalArgumentException("no node " + nodeId + " in " + this.form.screenId());
        }
        return slot;
    }

    /**
     * An input from this session's player, already matched to this session. Returns false when it
     * was refused; a refused change also sends the stored value back.
     */
    boolean accept(InputMessage input) {
        if (!this.open || input.seq() <= this.lastSeq) {
            return false;
        }
        this.lastSeq = input.seq();
        if (input instanceof InputMessage.Closed closed) {
            this.open = false;
            this.handler.closed(this, closed.reason());
            return true;
        }
        if (input instanceof InputMessage.Press press) {
            Slot slot = this.slots.get(press.nodeId());
            if (slot == null || !slot.kind.equals("button") || !live(slot)) {
                return false;
            }
            this.handler.pressed(this, press.nodeId());
            return true;
        }
        if (input instanceof InputMessage.Change change) {
            Slot slot = this.slots.get(change.nodeId());
            if (slot == null || !slot.fields.containsKey(Fields.VALUE) || !live(slot)) {
                return false;
            }
            Value value = checked(slot, change.value());
            if (value == null) {
                patch(change.nodeId(), Fields.VALUE, slot.fields.get(Fields.VALUE));
                return false;
            }
            slot.fields.put(Fields.VALUE, write(slot.kind, value));
            this.handler.changed(this, change.nodeId(), value);
            return true;
        }
        throw new IllegalStateException(String.valueOf(input));
    }

    /** Shown and enabled, its parents too. */
    private static boolean live(Slot slot) {
        for (Slot at = slot; at != null; at = at.parent) {
            if (!flag(at, Fields.VISIBLE) || !flag(at, Fields.ENABLED)) {
                return false;
            }
        }
        return true;
    }

    private static boolean flag(Slot slot, int tag) {
        byte[] raw = slot.fields.get(tag);
        return raw == null || Fields.readBool(raw);
    }

    /** The value if it fits the node, else null. */
    private Value checked(Slot slot, byte[] raw) {
        try {
            Value value = read(slot.kind, raw);
            if (value instanceof Value.Num n) {
                if (slot.kind.equals("slider")) {
                    int min = svarint(slot, Fields.MIN, 0);
                    int max = svarint(slot, Fields.MAX, 0);
                    int step = Math.max(1, svarint(slot, Fields.STEP, 1));
                    return n.value() >= min && n.value() <= max && (n.value() - min) % step == 0 ? n : null;
                }
                if (slot.kind.equals("dropdown")) {
                    return n.value() >= 0 && n.value() < Fields.readOptions(slot.fields.get(Fields.OPTIONS)).size() ? n : null;
                }
                return n.value() >= 0 && n.value() < slot.pages ? n : null;
            }
            if (value instanceof Value.Str s) {
                return fits(slot, s.value()) ? s : null;
            }
            return value;
        } catch (WireException e) {
            return null;
        }
    }

    private static boolean fits(Slot slot, String text) {
        byte[] max = slot.fields.get(Fields.MAX_LENGTH);
        if (text.codePointCount(0, text.length()) > (max == null ? Limits.FIELD_LENGTH : Fields.readVarint(max))) {
            return false;
        }
        if (text.codePoints().anyMatch(Character::isISOControl)) {
            return false;
        }
        byte[] charset = slot.fields.get(Fields.CHARSET);
        int set = charset == null ? 0 : Fields.readVarint(charset);
        return switch (set) {
            case 1 -> text.chars().allMatch(c -> c >= '0' && c <= '9');
            case 2 -> ID_SAFE.matcher(text).matches();
            default -> true;
        };
    }

    private static int svarint(Slot slot, int tag, int otherwise) {
        byte[] raw = slot.fields.get(tag);
        return raw == null ? otherwise : Fields.readSvarint(raw);
    }

    private static Value read(String kind, byte[] raw) {
        return switch (kind) {
            case "toggle" -> new Value.Bool(Fields.readBool(raw));
            case "slider" -> new Value.Num(Fields.readSvarint(raw));
            case "field" -> new Value.Str(Fields.readStr(raw, Limits.FIELD_LENGTH));
            case "dropdown", "tabs" -> new Value.Num(Fields.readVarint(raw));
            default -> throw new WireException("no value for " + kind);
        };
    }

    static byte[] write(String kind, Value value) {
        if (value instanceof Value.Bool b && kind.equals("toggle")) {
            return Fields.bool(b.value());
        }
        if (value instanceof Value.Num n && kind.equals("slider")) {
            return Fields.svarint(n.value());
        }
        if (value instanceof Value.Num n && (kind.equals("dropdown") || kind.equals("tabs"))) {
            return Fields.varint(n.value());
        }
        if (value instanceof Value.Str s && kind.equals("field")) {
            return Fields.str(s.value(), Limits.FIELD_LENGTH);
        }
        throw new IllegalArgumentException(value + " does not fit a " + kind);
    }

    /** The last input seq this session applied. */
    int seq() {
        return this.lastSeq;
    }

    /** The form's tree with every patch applied so far. */
    WireNode now() {
        return now(this.form.root());
    }

    private WireNode now(WireNode node) {
        List<WireNode> children = node.children().stream().map(this::now).toList();
        Slot slot = node.id().isEmpty() ? null : this.slots.get(node.id());
        if (slot == null) {
            return new WireNode(node.kind(), node.id(), node.fields(), children);
        }
        List<WireNode.Field> fields = slot.fields.entrySet().stream()
                .map(e -> new WireNode.Field(e.getKey(), e.getValue())).toList();
        return new WireNode(node.kind(), node.id(), fields, children);
    }

    /** Every node with an id gets a slot, and so does a static node that can hide or disable its children. */
    private void index(WireNode node, Slot parent) {
        Slot slot = parent;
        boolean gate = node.field(Fields.VISIBLE).isPresent() || node.field(Fields.ENABLED).isPresent();
        if (!node.id().isEmpty() || gate) {
            slot = new Slot(node.kind(), parent, node.children().size());
            for (WireNode.Field f : node.fields()) {
                slot.fields.put(f.tag(), f.value());
            }
            if (!node.id().isEmpty()) {
                this.slots.put(node.id(), slot);
            }
        }
        for (WireNode child : node.children()) {
            index(child, slot);
        }
    }

    private static final class Slot {
        final String kind;
        final Slot parent;
        final int pages;
        final Map<Integer, byte[]> fields = new TreeMap<>();

        Slot(String kind, Slot parent, int pages) {
            this.kind = kind;
            this.parent = parent;
            this.pages = pages;
        }
    }
}
