package dev.dryaides.minuit.wire;

import dev.dryaides.minuit.form.Image;
import dev.dryaides.minuit.form.Role;
import dev.dryaides.minuit.form.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Field tags of a node and the codec of each field value. Tags 1 to 15 mean the same for every
 * kind; tags from 16 up are per kind. Each {@code read} method takes the whole field value and
 * throws {@link WireException} when bytes are left over.
 */
public final class Fields {

    public static final int FALLBACK = 1;
    public static final int CHILDREN = 2;
    public static final int VISIBLE = 3;
    public static final int ENABLED = 4;
    public static final int TOOLTIP = 5;
    public static final int LABEL = 6;
    public static final int WEIGHT = 7;

    /** The value of {@code toggle} (bool), {@code slider} (svarint), {@code field} (str), {@code dropdown} and {@code tabs} (varint). */
    public static final int VALUE = 16;
    /** {@code column}, {@code row}: varint. */
    public static final int GAP = 16;
    /** {@code section}: bool. */
    public static final int OPEN = 16;
    /** {@code text}: varint, lines before the cut. */
    public static final int LINES = 16;
    /** {@code button}: role byte. */
    public static final int ROLE = 16;
    /** {@code progress}: svarint per mille, negative when unknown. */
    public static final int FRACTION = 16;
    /** {@code grid}: varint. */
    public static final int COLUMNS = 16;
    /** {@code button}, {@code image}: an image. */
    public static final int IMAGE = 17;
    /** {@code slider}: svarint. */
    public static final int MIN = 17;
    /** {@code slider}: svarint. */
    public static final int MAX = 18;
    /** {@code slider}: svarint. */
    public static final int STEP = 19;
    /** {@code field}: Text. */
    public static final int PLACEHOLDER = 17;
    /** {@code field}: varint, at most {@link Limits#FIELD_LENGTH}. */
    public static final int MAX_LENGTH = 18;
    /** {@code field}: 0 any, 1 digits, 2 id-safe. */
    public static final int CHARSET = 19;
    /** {@code dropdown}: varint count, then each Text. */
    public static final int OPTIONS = 17;

    /** {@code product}: varlong, the full price. */
    public static final int PRICE = 16;
    /** {@code product}: varlong, the price while on sale; absent when not on sale. */
    public static final int SALE = 18;
    /** {@code product}: svarint, how many are left; -1 or absent when there is no limit. */
    public static final int STOCK = 19;
    /** {@code product}: str, the tab or filter it belongs to. */
    public static final int CATEGORY = 20;
    /** {@code product} as a cart line: varint, how many the player wants. The value a client sends back. */
    public static final int QUANTITY = 21;
    /** {@code cart}: varint, checkouts the server accepted; the client shows a purchase toast when it grows. */
    public static final int PURCHASES = 16;
    /** {@code cart}: varint, checkouts the server refused; the client shows {@link #REASON} when it grows. */
    public static final int REFUSALS = 17;
    /** {@code cart}: text, why the last checkout was refused; empty once the cart moves on. */
    public static final int REASON = 18;
    /** {@code balance}: varlong, what the player has. */
    public static final int AMOUNT = 16;

    /** The tag of the value a node of {@code kind} sends back: {@link #QUANTITY} for a product, else {@link #VALUE}. */
    public static int valueTag(String kind) {
        return kind.equals("product") ? QUANTITY : VALUE;
    }

    private Fields() {
    }

    public static byte[] bool(boolean value) {
        return new ByteWriter().bool(value).toBytes();
    }

    public static boolean readBool(byte[] value) {
        ByteReader in = new ByteReader(value);
        return end(in, in.bool());
    }

    public static byte[] varint(int value) {
        return new ByteWriter().varint(value).toBytes();
    }

    public static int readVarint(byte[] value) {
        ByteReader in = new ByteReader(value);
        return end(in, in.varint());
    }

    public static byte[] varlong(long value) {
        return new ByteWriter().varlong(value).toBytes();
    }

    public static long readVarlong(byte[] value) {
        ByteReader in = new ByteReader(value);
        return end(in, in.varlong());
    }

    public static byte[] svarint(int value) {
        return new ByteWriter().svarint(value).toBytes();
    }

    public static int readSvarint(byte[] value) {
        ByteReader in = new ByteReader(value);
        return end(in, in.svarint());
    }

    public static byte[] str(String value, int maxChars) {
        return new ByteWriter().str(value, maxChars).toBytes();
    }

    public static String readStr(byte[] value, int maxChars) {
        ByteReader in = new ByteReader(value);
        return end(in, in.str(maxChars));
    }

    public static byte[] role(Role role) {
        return new ByteWriter().u8(role.wire()).toBytes();
    }

    public static Role readRole(byte[] value) {
        ByteReader in = new ByteReader(value);
        return end(in, WireEnums.of(Role.values(), in.u8(), "role"));
    }

    public static byte[] text(Text value) {
        ByteWriter out = new ByteWriter();
        writeText(out, value, 1);
        return out.toBytes();
    }

    public static Text readText(byte[] value) {
        ByteReader in = new ByteReader(value);
        return end(in, readText(in, 1));
    }

    public static byte[] options(List<Text> options) {
        if (options.size() > Limits.OPTIONS) {
            throw new WireException(options.size() + " options, cap " + Limits.OPTIONS);
        }
        ByteWriter out = new ByteWriter().varint(options.size());
        for (Text option : options) {
            writeText(out, option, 1);
        }
        return out.toBytes();
    }

    public static List<Text> readOptions(byte[] value) {
        ByteReader in = new ByteReader(value);
        int count = count(in, Limits.OPTIONS, "options");
        List<Text> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            options.add(readText(in, 1));
        }
        return end(in, options);
    }

    /** Tag byte 0 item ({@code str id}), or 1 sprite ({@code str id}, {@code varint w}, {@code varint h}). */
    public static byte[] image(Image value) {
        ByteWriter out = new ByteWriter();
        if (value instanceof Image.Item item) {
            out.u8(0).str(item.id(), Limits.RESOURCE_ID);
        } else {
            Image.Sprite sprite = (Image.Sprite) value;
            out.u8(1).str(sprite.id(), Limits.RESOURCE_ID).varint(sprite.w()).varint(sprite.h());
        }
        return out.toBytes();
    }

    public static Image readImage(byte[] value) {
        ByteReader in = new ByteReader(value);
        int tag = in.u8();
        Image image = switch (tag) {
            case 0 -> new Image.Item(in.str(Limits.RESOURCE_ID));
            case 1 -> new Image.Sprite(in.str(Limits.RESOURCE_ID),
                    count(in, Limits.SPRITE_SIZE, "sprite width"), count(in, Limits.SPRITE_SIZE, "sprite height"));
            default -> throw new WireException("image tag " + tag);
        };
        return end(in, image);
    }

    static void writeText(ByteWriter out, Text text, int depth) {
        if (depth > Limits.TEXT_DEPTH) {
            throw new WireException("text deeper than " + Limits.TEXT_DEPTH);
        }
        if (text instanceof Text.Lit lit) {
            out.u8(0).str(lit.text(), Limits.TEXT_CHARS);
        } else {
            Text.Tr tr = (Text.Tr) text;
            if (tr.style() == Text.Style.PLAIN) {
                out.u8(1);
            } else {
                out.u8(2).u8(tr.style().wire());
            }
            out.str(tr.key(), Limits.TEXT_CHARS).str(tr.fallback(), Limits.TEXT_CHARS);
            if (tr.args().size() > Limits.TEXT_ARGS) {
                throw new WireException(tr.args().size() + " text arguments, cap " + Limits.TEXT_ARGS);
            }
            out.varint(tr.args().size());
            for (Text arg : tr.args()) {
                writeText(out, arg, depth + 1);
            }
        }
    }

    static Text readText(ByteReader in, int depth) {
        if (depth > Limits.TEXT_DEPTH) {
            throw new WireException("text deeper than " + Limits.TEXT_DEPTH);
        }
        int tag = in.u8();
        if (tag == 0) {
            return new Text.Lit(in.str(Limits.TEXT_CHARS));
        }
        if (tag != 1 && tag != 2) {
            throw new WireException("text tag " + tag);
        }
        Text.Style style = tag == 1 ? Text.Style.PLAIN : WireEnums.of(Text.Style.values(), in.u8(), "style");
        String key = in.str(Limits.TEXT_CHARS);
        String fallback = in.str(Limits.TEXT_CHARS);
        int count = count(in, Limits.TEXT_ARGS, "text arguments");
        List<Text> args = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            args.add(readText(in, depth + 1));
        }
        return new Text.Tr(key, fallback, args, style);
    }

    static int count(ByteReader in, int cap, String what) {
        int count = in.varint();
        if (count < 0 || count > cap) {
            throw new WireException(count + " " + what + ", cap " + cap);
        }
        return count;
    }

    static <T> T end(ByteReader in, T value) {
        if (in.remaining() != 0) {
            throw new WireException(in.remaining() + " bytes after the value");
        }
        return value;
    }
}
