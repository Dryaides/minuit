package dev.dryaides.minuit.wire;

import dev.dryaides.minuit.form.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A node as the wire carries it: {@code str kind}, {@code str id} (empty when static),
 * {@code varint length}, then that many bytes of fields, each {@code varint tag},
 * {@code varint length}, bytes. The children field (tag 2) is decoded into {@link #children()};
 * every other field, unknown tags included, stays raw in {@link #fields()} for the kind to read.
 * A writer sends fields in ascending tag order.
 */
public record WireNode(String kind, String id, List<Field> fields, List<WireNode> children) {

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,32}");

    public WireNode {
        if (!id.isEmpty() && !ID.matcher(id).matches()) {
            throw new WireException("node id " + id);
        }
        List<Field> sorted = new ArrayList<>(fields);
        sorted.sort(Comparator.comparingInt(Field::tag));
        for (int i = 0; i < sorted.size(); i++) {
            int tag = sorted.get(i).tag();
            if (tag == Fields.CHILDREN || (i > 0 && sorted.get(i - 1).tag() == tag)) {
                throw new WireException("field " + tag + " twice or out of place in " + kind);
            }
        }
        fields = List.copyOf(sorted);
        children = List.copyOf(children);
    }

    public static WireNode of(String kind, String id, Field... fields) {
        return new WireNode(kind, id, List.of(fields), List.of());
    }

    public WireNode withChildren(WireNode... children) {
        return new WireNode(kind, id, fields, List.of(children));
    }

    /** The raw value of field {@code tag}, or empty when the node does not carry it. */
    public Optional<byte[]> field(int tag) {
        for (Field field : fields) {
            if (field.tag() == tag) {
                return Optional.of(field.value().clone());
            }
        }
        return Optional.empty();
    }

    /** The text a client shows when it does not know {@link #kind()}. */
    public Optional<Text> fallback() {
        return field(Fields.FALLBACK).map(Fields::readText);
    }

    void write(ByteWriter out) {
        ByteWriter body = new ByteWriter();
        boolean childrenWritten = children.isEmpty();
        for (Field field : fields) {
            if (!childrenWritten && field.tag() > Fields.CHILDREN) {
                writeChildrenField(body);
                childrenWritten = true;
            }
            body.varint(field.tag()).varint(field.value().length).bytes(field.value());
        }
        if (!childrenWritten) {
            writeChildrenField(body);
        }
        byte[] bytes = body.toBytes();
        out.str(kind, Limits.NODE_KIND).str(id, Limits.NODE_ID).varint(bytes.length).bytes(bytes);
    }

    private void writeChildrenField(ByteWriter body) {
        ByteWriter list = new ByteWriter();
        writeChildren(list, children);
        byte[] bytes = list.toBytes();
        body.varint(Fields.CHILDREN).varint(bytes.length).bytes(bytes);
    }

    static void writeChildren(ByteWriter out, List<WireNode> children) {
        out.varint(children.size());
        for (WireNode child : children) {
            child.write(out);
        }
    }

    static WireNode read(ByteReader in, Budget budget, int depth) {
        if (depth > Limits.DEPTH) {
            throw new WireException("nodes deeper than " + Limits.DEPTH);
        }
        if (++budget.nodes > Limits.NODES) {
            throw new WireException("over " + Limits.NODES + " nodes");
        }
        String kind = in.str(Limits.NODE_KIND);
        String id = in.str(Limits.NODE_ID);
        ByteReader body = new ByteReader(in.bytes(in.varint()));
        List<Field> fields = new ArrayList<>();
        List<WireNode> children = List.of();
        while (body.remaining() > 0) {
            int tag = body.varint();
            byte[] value = body.bytes(body.varint());
            if (tag == Fields.CHILDREN) {
                ByteReader list = new ByteReader(value);
                children = Fields.end(list, readChildren(list, budget, depth + 1, kind.equals("tabs")));
            } else {
                fields.add(new Field(tag, value));
            }
        }
        return new WireNode(kind, id, fields, children);
    }

    static List<WireNode> readChildren(ByteReader in, Budget budget, int depth, boolean tabs) {
        int count = Fields.count(in, tabs ? Limits.TABS : Limits.NODES, tabs ? "tabs" : "children");
        List<WireNode> children = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            children.add(read(in, budget, depth));
        }
        return children;
    }

    /** The value of a children field, as a patch sets it. */
    public static byte[] encodeChildren(List<WireNode> children) {
        ByteWriter out = new ByteWriter();
        writeChildren(out, children);
        return out.toBytes();
    }

    /** Reads a children field set by a patch; its caps count from a fresh root one level down. */
    public static List<WireNode> decodeChildren(byte[] value) {
        ByteReader in = new ByteReader(value);
        return Fields.end(in, readChildren(in, new Budget(), 2, false));
    }

    static final class Budget {
        int nodes;
    }

    /** One field: its tag and its raw value. */
    public record Field(int tag, byte[] value) {

        public Field {
            if (tag < 1) {
                throw new WireException("field tag " + tag);
            }
            value = value.clone();
        }

        @Override
        public byte[] value() {
            return value.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Field f && f.tag == tag && Arrays.equals(f.value, value);
        }

        @Override
        public int hashCode() {
            return 31 * tag + Arrays.hashCode(value);
        }

        @Override
        public String toString() {
            return "Field[" + tag + ", " + Arrays.toString(value) + "]";
        }
    }
}
