package dev.dryaides.minuit.wire;

import dev.dryaides.minuit.form.CloseReason;

import java.util.Arrays;

/**
 * A message on {@code minuit:input}, client to server: {@code varint session}, {@code varint seq}
 * (counts up per session), {@code byte op}, then the op's body. At most
 * {@link Limits#INPUT_BYTES} bytes.
 */
public sealed interface InputMessage {

    int CHANGE = 0;
    int PRESS = 1;
    int CLOSED = 2;

    int session();

    int seq();

    byte[] encode();

    static InputMessage decode(byte[] bytes) {
        if (bytes.length > Limits.INPUT_BYTES) {
            throw new WireException(bytes.length + " bytes, cap " + Limits.INPUT_BYTES);
        }
        ByteReader in = new ByteReader(bytes);
        int session = in.varint();
        int seq = in.varint();
        int op = in.u8();
        return switch (op) {
            case CHANGE -> new Change(session, seq, in.str(Limits.NODE_ID), in.bytes(in.remaining()));
            case PRESS -> {
                String nodeId = in.str(Limits.NODE_ID);
                long confirmed = in.remaining() > 0 ? in.varlong() : NONE;
                if (confirmed < NONE) {
                    throw new WireException("confirmed total " + confirmed);
                }
                yield Fields.end(in, new Press(session, seq, nodeId, confirmed));
            }
            case CLOSED -> Fields.end(in, new Closed(session, seq,
                    WireEnums.of(WireEnums.CLOSE_REASONS, in.u8(), "reason")));
            default -> throw new WireException("input op " + op);
        };
    }

    /**
     * A new value for a node. The value runs to the end of the message, in the node's value type;
     * the server reads it with the matching {@link Fields} reader once it knows the node's kind.
     */
    record Change(int session, int seq, String nodeId, byte[] value) implements InputMessage {

        public Change {
            value = value.clone();
        }

        @Override
        public byte[] value() {
            return value.clone();
        }

        @Override
        public byte[] encode() {
            return checked(head(session, seq, CHANGE).str(nodeId, Limits.NODE_ID).bytes(value));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Change c && c.session == session && c.seq == seq && c.nodeId.equals(nodeId)
                    && Arrays.equals(c.value, value);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * (31 * session + seq) + nodeId.hashCode()) + Arrays.hashCode(value);
        }
    }

    /** No confirmed total on a press. */
    long NONE = -1;

    /**
     * A press. A checkout press also carries {@code confirmed}, the cart total the player saw and
     * confirmed, as a trailing varlong; {@link #NONE} (left off the wire) on any other press.
     */
    record Press(int session, int seq, String nodeId, long confirmed) implements InputMessage {

        public Press(int session, int seq, String nodeId) {
            this(session, seq, nodeId, NONE);
        }

        @Override
        public byte[] encode() {
            ByteWriter out = head(session, seq, PRESS).str(nodeId, Limits.NODE_ID);
            return checked(confirmed < 0 ? out : out.varlong(confirmed));
        }
    }

    record Closed(int session, int seq, CloseReason reason) implements InputMessage {

        @Override
        public byte[] encode() {
            return head(session, seq, CLOSED).u8(reason.wire()).toBytes();
        }
    }

    private static ByteWriter head(int session, int seq, int op) {
        return new ByteWriter().varint(session).varint(seq).u8(op);
    }

    private static byte[] checked(ByteWriter out) {
        byte[] bytes = out.toBytes();
        if (bytes.length > Limits.INPUT_BYTES) {
            throw new WireException(bytes.length + " bytes, cap " + Limits.INPUT_BYTES);
        }
        return bytes;
    }
}
