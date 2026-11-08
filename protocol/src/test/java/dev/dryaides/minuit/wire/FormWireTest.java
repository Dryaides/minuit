package dev.dryaides.minuit.wire;

import dev.dryaides.minuit.form.CloseReason;
import dev.dryaides.minuit.form.Kind;
import dev.dryaides.minuit.form.Role;
import dev.dryaides.minuit.form.Size;
import dev.dryaides.minuit.form.Text;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The form messages of FORMS_WIRE.md, bytes written by hand from the spec. */
class FormWireTest {

    private static byte[] bytes(Object... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Object part : parts) {
            if (part instanceof String s) {
                out.writeBytes(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else {
                out.write((Integer) part);
            }
        }
        return out.toByteArray();
    }

    @Test
    void customOpenMatchesTheSpecBytes() {
        byte[] wire = bytes(
                0x00, 0x01,                            // op open, session 1
                0x0C, "example:shop",                  // screen id
                0x02,                                  // kind custom
                0x00, 0x04, "Shop",                    // title, a literal
                0x01,                                  // size normal
                0x06, "column", 0x00, 0x37,            // root column, static, 55 bytes of fields
                0x02, 0x35, 0x02,                      // children: 53 bytes, two nodes
                0x06, "toggle", 0x06, "sounds", 0x0D,  // toggle "sounds", 13 bytes of fields
                0x06, 0x08, 0x00, 0x06, "Sounds",      // label
                0x10, 0x01, 0x01,                      // value true
                0x06, "button", 0x04, "save", 0x0B,    // button "save", 11 bytes of fields
                0x06, 0x06, 0x00, 0x04, "Save",        // label
                0x10, 0x01, 0x00);                     // role primary

        WireNode root = WireNode.of("column", "").withChildren(
                WireNode.of("toggle", "sounds",
                        new WireNode.Field(Fields.LABEL, Fields.text(Text.lit("Sounds"))),
                        new WireNode.Field(Fields.VALUE, Fields.bool(true))),
                WireNode.of("button", "save",
                        new WireNode.Field(Fields.ROLE, Fields.role(Role.PRIMARY)),
                        new WireNode.Field(Fields.LABEL, Fields.text(Text.lit("Save")))));
        ScreenMessage.Open open = new ScreenMessage.Open(1, "example:shop", Kind.CUSTOM, Text.lit("Shop"),
                Size.NORMAL, root);

        assertArrayEquals(wire, open.encode());
        ScreenMessage.Open back = (ScreenMessage.Open) ScreenMessage.decode(wire);
        assertEquals(open, back);
        assertEquals(Role.PRIMARY, Fields.readRole(back.root().children().get(1).field(Fields.ROLE).orElseThrow()));
    }

    @Test
    void unknownKindKeepsItsFallbackAndUnknownFields() {
        byte[] wire = bytes(
                0x00, 0x02, 0x0C, "example:shop", 0x00, 0x00, 0x02, "Hi", 0x00, // notice, title "Hi", narrow
                0x05, "chart", 0x00, 0x0D,
                0x01, 0x07, 0x00, 0x05, "Chart",       // fallback
                0x30, 0x02, 0xAB, 0xCD);               // tag 48, unknown to 1.0

        WireNode node = ((ScreenMessage.Open) ScreenMessage.decode(wire)).root();
        assertEquals(Text.lit("Chart"), node.fallback().orElseThrow());
        assertArrayEquals(new byte[]{(byte) 0xAB, (byte) 0xCD}, node.field(0x30).orElseThrow());
    }

    @Test
    void translatableTextWithStyleAndArgument() {
        // tag 2, style accent, key "a.b", fallback "B", one argument: literal "x"
        byte[] wire = bytes(0x02, 0x02, 0x03, "a.b", 0x01, "B", 0x01, 0x00, 0x01, "x");
        Text text = new Text.Tr("a.b", "B", List.of(Text.lit("x")), Text.Style.ACCENT);
        assertArrayEquals(wire, Fields.text(text));
        assertEquals(text, Fields.readText(wire));
        // nine arguments, one over the cap
        assertThrows(WireException.class, () -> Fields.readText(bytes(0x01, 0x01, "k", 0x01, "k", 0x09)));
    }

    @Test
    void patchAndCloseMatchTheSpecBytes() {
        // patch session 1, ack 5, one set: "fill" tag 16, progress 400 per mille (zigzag 800 = A0 06)
        byte[] patch = bytes(0x01, 0x01, 0x05, 0x01, 0x04, "fill", 0x10, 0x02, 0xA0, 0x06);
        ScreenMessage.Patch expected = new ScreenMessage.Patch(1, 5,
                List.of(new ScreenMessage.FieldSet("fill", Fields.FRACTION, Fields.svarint(400))));
        assertArrayEquals(patch, expected.encode());
        assertEquals(expected, ScreenMessage.decode(patch));

        byte[] close = bytes(0x02, 0x01, 0x03);
        assertEquals(new ScreenMessage.Close(1, CloseReason.SERVER), ScreenMessage.decode(close));
        assertThrows(WireException.class, () -> ScreenMessage.decode(bytes(0x03, 0x01)));
    }

    @Test
    void inputMessagesMatchTheSpecBytes() {
        // session 1, seq 3, change "volume" to slider value 50 (zigzag 100)
        byte[] change = bytes(0x01, 0x03, 0x00, 0x06, "volume", 0x64);
        InputMessage.Change c = (InputMessage.Change) InputMessage.decode(change);
        assertEquals("volume", c.nodeId());
        assertEquals(50, Fields.readSvarint(c.value()));
        assertArrayEquals(change, c.encode());

        byte[] press = bytes(0x01, 0x04, 0x01, 0x04, "save");
        assertEquals(new InputMessage.Press(1, 4, "save"), InputMessage.decode(press));

        byte[] closed = bytes(0x01, 0x05, 0x02, 0x02);
        assertEquals(new InputMessage.Closed(1, 5, CloseReason.REPLACED), InputMessage.decode(closed));

        assertThrows(WireException.class, () -> InputMessage.decode(new byte[Limits.INPUT_BYTES + 1]));
    }

    @Test
    void formsOverTheNodeCapsAreDropped() {
        WireNode deep = WireNode.of("text", "");
        for (int i = 0; i < Limits.DEPTH; i++) {
            deep = WireNode.of("column", "").withChildren(deep);
        }
        byte[] nine = new ScreenMessage.Open(1, "a:b", Kind.CUSTOM, Text.lit(""), Size.NORMAL, deep).encode();
        assertThrows(WireException.class, () -> ScreenMessage.decode(nine));

        List<WireNode> pages = new ArrayList<>();
        for (int i = 0; i <= Limits.TABS; i++) {
            pages.add(WireNode.of("page", "p" + i));
        }
        WireNode tabs = new WireNode("tabs", "tabs", List.of(), pages);
        byte[] thirteen = new ScreenMessage.Open(1, "a:b", Kind.CUSTOM, Text.lit(""), Size.NORMAL, tabs).encode();
        assertThrows(WireException.class, () -> ScreenMessage.decode(thirteen));
    }
}
