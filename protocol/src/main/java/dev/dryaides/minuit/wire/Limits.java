package dev.dryaides.minuit.wire;

/** The size caps of the form messages; a message over one is dropped whole. */
public final class Limits {

    /**
     * Bytes in one {@code minuit:screen} message. Under the 1 MiB clientbound custom payload limit of vanilla
     * 26.1.2 and Bukkit's {@code Messenger.MAX_MESSAGE_SIZE} (Paper 26.1.2 to 26.3).
     */
    public static final int SCREEN_BYTES = 256 * 1024;
    /** Bytes in one {@code minuit:input} message; vanilla's 26.1.2 serverbound custom payload limit is 32767. */
    public static final int INPUT_BYTES = 4 * 1024;
    /** Nodes in one open, or in one children field of a patch. */
    public static final int NODES = 512;
    /** Nesting depth of nodes; the root is depth 1. */
    public static final int DEPTH = 8;
    /** Pages in one {@code tabs} node. */
    public static final int TABS = 12;
    /** Characters in each string of a {@link dev.dryaides.minuit.form.Text}. */
    public static final int TEXT_CHARS = 1024;
    public static final int TEXT_ARGS = 8;
    /** Nesting of translatable arguments; a text with no arguments is depth 1. */
    public static final int TEXT_DEPTH = 4;
    public static final int OPTIONS = 128;
    public static final int FIELD_LENGTH = 256;
    public static final int SCREEN_ID = 64;
    public static final int NODE_ID = 32;
    public static final int NODE_KIND = 32;
    /** Field sets in one patch. */
    public static final int PATCH_SETS = 512;
    /** Characters of an item or sprite id. */
    public static final int RESOURCE_ID = 256;
    /**
     * Width or height of a sprite image, in GUI pixels. A chosen cap, not derived from a payload
     * limit: well above any vanilla GUI sprite, and it keeps a server from asking for a huge layout.
     */
    public static final int SPRITE_SIZE = 1024;
    /** Characters of a product's category. */
    public static final int CATEGORY = 64;
    /** The most one cart line holds, as the kit's {@code Cart} caps it. */
    public static final int QUANTITY = 999;

    private Limits() {
    }
}
