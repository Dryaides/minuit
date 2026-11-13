package dev.dryaides.minuit.internal.formui;

import dev.dryaides.minuit.form.Text;
import dev.dryaides.minuit.wire.Fields;
import dev.dryaides.minuit.wire.Limits;
import dev.dryaides.minuit.wire.WireException;
import dev.dryaides.minuit.wire.WireNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** A node of the open screen as the client holds it: the open's fields, then every patch and local change. */
final class Live {

    final String kind;
    final String id;
    final Map<Integer, byte[]> fields = new TreeMap<>();
    final List<Live> children = new ArrayList<>();

    Live(WireNode node) {
        this.kind = node.kind();
        this.id = node.id();
        for (WireNode.Field f : node.fields()) {
            this.fields.put(f.tag(), f.value());
        }
        setChildren(node.children());
    }

    void setChildren(List<WireNode> nodes) {
        this.children.clear();
        for (WireNode child : nodes) {
            this.children.add(new Live(child));
        }
    }

    Live find(String nodeId) {
        if (this.id.equals(nodeId)) {
            return this;
        }
        for (Live child : this.children) {
            Live found = child.find(nodeId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    boolean bool(int tag, boolean otherwise) {
        byte[] raw = this.fields.get(tag);
        return raw == null ? otherwise : read(() -> Fields.readBool(raw), otherwise);
    }

    int varint(int tag, int otherwise) {
        byte[] raw = this.fields.get(tag);
        return raw == null ? otherwise : read(() -> Fields.readVarint(raw), otherwise);
    }

    int svarint(int tag, int otherwise) {
        byte[] raw = this.fields.get(tag);
        return raw == null ? otherwise : read(() -> Fields.readSvarint(raw), otherwise);
    }

    long varlong(int tag, long otherwise) {
        byte[] raw = this.fields.get(tag);
        return raw == null ? otherwise : read(() -> Fields.readVarlong(raw), otherwise);
    }

    dev.dryaides.minuit.form.Image image(int tag) {
        byte[] raw = this.fields.get(tag);
        return raw == null ? null : read(() -> Fields.readImage(raw), null);
    }

    /** The first node of {@code kind} in this subtree, depth first, or null. */
    Live first(String kind) {
        if (this.kind.equals(kind)) {
            return this;
        }
        for (Live child : this.children) {
            Live found = child.first(kind);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    String str(int tag) {
        byte[] raw = this.fields.get(tag);
        return raw == null ? "" : read(() -> Fields.readStr(raw, Limits.FIELD_LENGTH), "");
    }

    Text text(int tag) {
        byte[] raw = this.fields.get(tag);
        return raw == null ? null : read(() -> Fields.readText(raw), null);
    }

    List<Text> options() {
        byte[] raw = this.fields.get(Fields.OPTIONS);
        return raw == null ? List.of() : read(() -> Fields.readOptions(raw), List.of());
    }

    /** A field a client cannot read counts as absent, as an unknown one would. */
    private static <T> T read(java.util.function.Supplier<T> reader, T otherwise) {
        try {
            return reader.get();
        } catch (WireException e) {
            return otherwise;
        }
    }
}
