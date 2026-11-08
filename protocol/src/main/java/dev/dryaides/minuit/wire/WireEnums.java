package dev.dryaides.minuit.wire;

import dev.dryaides.minuit.form.CloseReason;

import java.util.Arrays;

final class WireEnums {

    /** The reasons a close or a closed may carry: every one but UNSUPPORTED, which stays on the server. */
    static final CloseReason[] CLOSE_REASONS = Arrays.copyOf(CloseReason.values(), CloseReason.UNSUPPORTED.ordinal());

    private WireEnums() {
    }

    /** Every enum of the wire numbers its bytes by ordinal. */
    static <E extends Enum<E>> E of(E[] values, int wire, String what) {
        if (wire < 0 || wire >= values.length) {
            throw new WireException(what + " byte " + wire);
        }
        return values[wire];
    }
}
