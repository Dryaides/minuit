package dev.dryaides.minuit.form;

import dev.dryaides.minuit.wire.Fields;
import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.ScreenMessage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The builder against the bytes FORMS_WIRE.md writes out, then player input through the server
 * session against the rules of the design's security list.
 */
class FormRoundTripTest {

    private static byte[] bytes(Object... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Object part : parts) {
            if (part instanceof String s) {
                out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
            } else {
                out.write((Integer) part);
            }
        }
        return out.toByteArray();
    }

    /** Every handler call, as text, and every message sent to the one player. */
    private static final class Rig implements FormHandler {
        final List<String> calls = new ArrayList<>();
        final List<byte[]> sent = new ArrayList<>();
        long now = 5_000;
        final FormServer<String> server = new FormServer<>((p, b) -> this.sent.add(b), () -> this.now);

        @Override
        public void changed(FormSession s, String id, Value v) {
            this.calls.add("changed " + id + " " + v);
        }

        @Override
        public void pressed(FormSession s, String id) {
            this.calls.add("pressed " + id);
        }

        @Override
        public void closed(FormSession s, CloseReason why) {
            this.calls.add("closed " + why);
        }

        ScreenMessage last() {
            return ScreenMessage.decode(this.sent.get(this.sent.size() - 1));
        }
    }

    private static Form shop() {
        return Form.custom("example:shop").title(Text.lit("Shop"))
                .toggle("sounds", Text.lit("Sounds"), true)
                .slider("volume", Text.lit("Volume"), 0, 100, 5, 80)
                .field("name", Text.lit("Name"), f -> f.maxLength(8))
                .field("count", Text.lit("Count"), f -> f.charset(Form.Charset.DIGITS))
                .dropdown("currency", Text.lit("Currency"), List.of(Text.lit("Emerald"), Text.lit("Diamond")), 0)
                .section("advanced", Text.lit("Advanced"), false, s -> s.button("wipe", Text.lit("Wipe"), Role.DESTRUCTIVE))
                .visible(false)
                .footer(r -> r.button("save", Text.lit("Save"), Role.PRIMARY)
                        .button("cancel", Text.lit("Cancel"), Role.SECONDARY).enabled(false))
                .build();
    }

    @Test
    void builderWritesTheSpecExample() {
        Rig rig = new Rig();
        Form form = Form.custom("example:shop").title(Text.lit("Shop"))
                .toggle("sounds", Text.lit("Sounds"), true)
                .button("save", Text.lit("Save"), Role.PRIMARY)
                .build();
        rig.server.open("p", form, rig);
        assertArrayEquals(bytes(
                0x00, 0x01, 0x0C, "example:shop", 0x02, 0x00, 0x04, "Shop", 0x01,
                0x06, "column", 0x00, 0x37,
                0x02, 0x35, 0x02,
                0x06, "toggle", 0x06, "sounds", 0x0D,
                0x06, 0x08, 0x00, 0x06, "Sounds",
                0x10, 0x01, 0x01,
                0x06, "button", 0x04, "save", 0x0B,
                0x06, 0x06, 0x00, 0x04, "Save",
                0x10, 0x01, 0x00), rig.sent.get(0));
    }

    @Test
    void builderRefusesRepeatedIdsAndBrokenCaps() {
        assertThrows(IllegalArgumentException.class, () -> Form.custom("example:shop")
                .toggle("a", Text.lit("A"), true).toggle("a", Text.lit("B"), true).build());
        assertThrows(IllegalArgumentException.class, () -> Form.custom("no namespace").build());
        // The root at depth 1, seven rows, then the text at depth 9: one past the cap of 8.
        assertThrows(RuntimeException.class, () -> Form.custom("example:deep")
                .row(a -> a.row(b -> b.row(c -> c.row(d -> d.row(e -> e.row(f -> f.row(g -> g.text(Text.lit("deep")))))))))
                .build());
    }

    @Test
    void theSpecSliderMoveReachesTheHandler() {
        Rig rig = new Rig();
        FormSession session = rig.server.open("p", shop(), rig);
        rig.server.receive("p", bytes(0x01, 0x03, 0x00, 0x06, "volume", 0x64));
        assertEquals(List.of("changed volume Num[value=50]"), rig.calls);
        assertEquals(new Value.Num(50), session.value("volume"));
    }

    @Test
    void refusedValueGetsTheStoredOneBack() {
        Rig rig = new Rig();
        FormSession session = rig.server.open("p", shop(), rig);
        rig.server.receive("p", new InputMessage.Change(1, 1, "volume", Fields.svarint(52)).encode());
        rig.server.receive("p", new InputMessage.Change(1, 2, "volume", Fields.svarint(105)).encode());
        assertTrue(rig.calls.isEmpty());
        ScreenMessage.Patch back = assertInstanceOf(ScreenMessage.Patch.class, rig.last());
        assertEquals(2, back.ack());
        assertEquals(new ScreenMessage.FieldSet("volume", Fields.VALUE, Fields.svarint(80)), back.sets().get(0));
        assertEquals(new Value.Num(80), session.value("volume"));
    }

    @Test
    void textMustFitLengthCharsetAndHoldNoControlCharacter() {
        Rig rig = new Rig();
        rig.server.open("p", shop(), rig);
        rig.server.receive("p", new InputMessage.Change(1, 1, "name", Fields.str("Nine char", 256)).encode());
        rig.server.receive("p", new InputMessage.Change(1, 2, "name", Fields.str("a\tb", 256)).encode());
        rig.server.receive("p", new InputMessage.Change(1, 3, "count", Fields.str("12a", 256)).encode());
        rig.server.receive("p", new InputMessage.Change(1, 4, "name", Fields.str("Emporium", 256)).encode());
        rig.server.receive("p", new InputMessage.Change(1, 5, "count", Fields.str("12", 256)).encode());
        assertEquals(List.of("changed name Str[value=Emporium]", "changed count Str[value=12]"), rig.calls);
    }

    @Test
    void onlyLiveButtonsPressAndOnlyValueNodesChange() {
        Rig rig = new Rig();
        rig.server.open("p", shop(), rig);
        rig.server.receive("p", new InputMessage.Press(1, 1, "sounds").encode());
        rig.server.receive("p", new InputMessage.Press(1, 2, "cancel").encode());
        rig.server.receive("p", new InputMessage.Press(1, 3, "wipe").encode());
        rig.server.receive("p", new InputMessage.Press(1, 4, "nothing").encode());
        rig.server.receive("p", new InputMessage.Change(1, 5, "save", Fields.bool(true)).encode());
        rig.server.receive("p", new InputMessage.Change(1, 6, "currency", Fields.varint(2)).encode());
        rig.server.receive("p", new InputMessage.Press(1, 7, "save").encode());
        assertEquals(List.of("pressed save"), rig.calls);
    }

    @Test
    void foreignStaleAndReplacedSessionsAreDropped() {
        Rig rig = new Rig();
        rig.server.open("p", shop(), rig);
        rig.server.receive("p", new InputMessage.Press(7, 1, "save").encode());
        rig.server.receive("p", new InputMessage.Press(1, 4, "save").encode());
        rig.server.receive("p", new InputMessage.Press(1, 4, "save").encode());
        rig.server.receive("p", new InputMessage.Press(1, 3, "save").encode());
        assertEquals(List.of("pressed save"), rig.calls);
        rig.server.open("p", shop(), rig);
        assertEquals("closed REPLACED", rig.calls.get(rig.calls.size() - 1));
        rig.server.receive("p", new InputMessage.Press(1, 9, "save").encode());
        assertEquals(2, rig.calls.size());
        rig.server.receive("p", new InputMessage.Closed(2, 1, CloseReason.PLAYER).encode());
        assertEquals("closed PLAYER", rig.calls.get(rig.calls.size() - 1));
        assertNull(rig.server.session("p"));
    }

    @Test
    void thirtyInputsASecond() {
        Rig rig = new Rig();
        rig.server.open("p", shop(), rig);
        for (int i = 1; i <= 31; i++) {
            rig.server.receive("p", new InputMessage.Press(1, i, "save").encode());
        }
        assertEquals(30, rig.calls.size());
        rig.now += 1000;
        rig.server.receive("p", new InputMessage.Press(1, 32, "save").encode());
        assertEquals(31, rig.calls.size());
    }

    @Test
    void serverPatchesCarryTheLastAppliedInput() {
        Rig rig = new Rig();
        FormSession session = rig.server.open("p", shop(), rig);
        rig.server.receive("p", new InputMessage.Change(1, 4, "volume", Fields.svarint(40)).encode());
        session.text("sounds", Text.lit("Music"));
        ScreenMessage.Patch patch = assertInstanceOf(ScreenMessage.Patch.class, rig.last());
        assertEquals(4, patch.ack());
        session.close();
        assertEquals(new ScreenMessage.Close(1, CloseReason.SERVER), rig.last());
    }

    @Test
    void aShopLineKeepsPricesAboveTheIntRangeThroughTheWire() {
        Form form = Form.custom("t:shop")
                .cart("cart", Text.lit("Buy"), c -> c
                        .product("line.pick", Text.lit("Pick"), new Image.Item("minecraft:iron_pickaxe"), 5_000_000_000L)
                        .sale(4_000_000_000L).stock(7).category("tools").quantity(2))
                .build();
        ScreenMessage.Open open = assertInstanceOf(ScreenMessage.Open.class, ScreenMessage.decode(form.open(1).encode()));
        dev.dryaides.minuit.wire.WireNode line = open.root().children().get(0).children().get(0);
        assertEquals("product", line.kind());
        assertEquals(5_000_000_000L, Fields.readVarlong(line.field(Fields.PRICE).orElseThrow()));
        assertEquals(4_000_000_000L, Fields.readVarlong(line.field(Fields.SALE).orElseThrow()));
        assertEquals(7, Fields.readSvarint(line.field(Fields.STOCK).orElseThrow()));
        assertEquals("tools", Fields.readStr(line.field(Fields.CATEGORY).orElseThrow(), 64));
        assertEquals(2, Fields.readVarint(line.field(Fields.QUANTITY).orElseThrow()));
        assertEquals(new Image.Item("minecraft:iron_pickaxe"), Fields.readImage(line.field(Fields.IMAGE).orElseThrow()));
    }

    @Test
    void aProductIdTooLongForItsCartLineIsRefusedAtBuildTime() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Form.custom("t:shop")
                .product("mymod_enchanted_golden_apple", Text.lit("Apple"), null, 50));
        assertTrue(e.getMessage().contains("mymod_enchanted_golden_apple"));
        Form.custom("t:shop").product("mymod_enchanted_golden_appl", Text.lit("Apple"), null, 50)
                .cart("cart", Text.lit("Buy"), lines -> { }).build();
    }
}
