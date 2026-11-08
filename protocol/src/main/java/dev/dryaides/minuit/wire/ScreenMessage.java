package dev.dryaides.minuit.wire;

import dev.dryaides.minuit.form.CloseReason;
import dev.dryaides.minuit.form.Kind;
import dev.dryaides.minuit.form.Size;
import dev.dryaides.minuit.form.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A message on {@code minuit:screen}, server to client: {@code varint op}, then the op's body.
 * {@link #decode} throws {@link WireException} on an unknown op or a broken cap.
 */
public sealed interface ScreenMessage {

    int OPEN = 0;
    int PATCH = 1;
    int CLOSE = 2;

    byte[] encode();

    static ScreenMessage decode(byte[] bytes) {
        if (bytes.length > Limits.SCREEN_BYTES) {
            throw new WireException(bytes.length + " bytes, cap " + Limits.SCREEN_BYTES);
        }
        ByteReader in = new ByteReader(bytes);
        int op = in.varint();
        ScreenMessage message = switch (op) {
            case OPEN -> new Open(in.varint(), in.str(Limits.SCREEN_ID),
                    WireEnums.of(Kind.values(), in.u8(), "kind"), Fields.readText(in, 1),
                    WireEnums.of(Size.values(), in.u8(), "size"), WireNode.read(in, new WireNode.Budget(), 1));
            case PATCH -> {
                int session = in.varint();
                int ack = in.varint();
                int count = Fields.count(in, Limits.PATCH_SETS, "field sets");
                List<FieldSet> sets = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    sets.add(new FieldSet(in.str(Limits.NODE_ID), in.varint(), in.bytes(in.varint())));
                }
                yield new Patch(session, ack, sets);
            }
            case CLOSE -> new Close(in.varint(), WireEnums.of(WireEnums.CLOSE_REASONS, in.u8(), "reason"));
            default -> throw new WireException("screen op " + op);
        };
        return Fields.end(in, message);
    }

    /** Opens a screen; a new open replaces the screen of an earlier session. */
    record Open(int session, String screenId, Kind kind, Text title, Size size, WireNode root)
            implements ScreenMessage {

        @Override
        public byte[] encode() {
            ByteWriter out = new ByteWriter().varint(OPEN).varint(session).str(screenId, Limits.SCREEN_ID)
                    .u8(kind.wire());
            Fields.writeText(out, title, 1);
            out.u8(size.wire());
            root.write(out);
            return checked(out.toBytes());
        }
    }

    /**
     * Sets node fields the way {@link Open} set them. {@code ack} is the last input {@code seq} the
     * server has applied.
     */
    record Patch(int session, int ack, List<FieldSet> sets) implements ScreenMessage {

        public Patch {
            sets = List.copyOf(sets);
        }

        @Override
        public byte[] encode() {
            if (sets.size() > Limits.PATCH_SETS) {
                throw new WireException(sets.size() + " field sets, cap " + Limits.PATCH_SETS);
            }
            ByteWriter out = new ByteWriter().varint(PATCH).varint(session).varint(ack).varint(sets.size());
            for (FieldSet set : sets) {
                out.str(set.nodeId(), Limits.NODE_ID).varint(set.tag()).varint(set.value.length).bytes(set.value);
            }
            return checked(out.toBytes());
        }
    }

    /** One field set of a patch; a children value is checked against the node caps. */
    record FieldSet(String nodeId, int tag, byte[] value) {

        public FieldSet {
            if (tag < 1) {
                throw new WireException("field tag " + tag);
            }
            value = value.clone();
            if (tag == Fields.CHILDREN) {
                WireNode.decodeChildren(value);
            }
        }

        @Override
        public byte[] value() {
            return value.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof FieldSet s && s.nodeId.equals(nodeId) && s.tag == tag && Arrays.equals(s.value, value);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * nodeId.hashCode() + tag) + Arrays.hashCode(value);
        }
    }

    record Close(int session, CloseReason reason) implements ScreenMessage {

        @Override
        public byte[] encode() {
            return new ByteWriter().varint(CLOSE).varint(session).u8(reason.wire()).toBytes();
        }
    }

    private static byte[] checked(byte[] bytes) {
        if (bytes.length > Limits.SCREEN_BYTES) {
            throw new WireException(bytes.length + " bytes, cap " + Limits.SCREEN_BYTES);
        }
        return bytes;
    }
}
