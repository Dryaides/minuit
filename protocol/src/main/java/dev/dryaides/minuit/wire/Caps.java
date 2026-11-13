package dev.dryaides.minuit.wire;

/**
 * Capability names a client or a server announces in its hello. A name is {@code area/level} or
 * {@code area.feature}; a higher level includes the lower ones.
 */
public final class Caps {

    /** The client sends hellos: every minuit 1.x client. */
    public static final String HELLO_1 = "hello/1";
    /** The client draws forms sent on {@code minuit:screen}: the v1 node kinds and the three form kinds. */
    public static final String FORM_1 = dev.dryaides.minuit.form.ClientInfo.FORMS;

    /** The client draws the shop nodes {@code product}, {@code balance} and {@code cart}. */
    public static final String FORM_SHOP = "form.shop";

    private Caps() {
    }
}
