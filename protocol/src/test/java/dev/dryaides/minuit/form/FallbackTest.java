package dev.dryaides.minuit.form;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallbackTest {

    /** A vanilla dialog input key: StringTemplate's variable names. */
    private static final Pattern VANILLA_KEY = Pattern.compile("[A-Za-z0-9_]+");

    static final class Rig implements FormHandler, Fallback.Dialogs<String> {
        final List<String> calls = new ArrayList<>();
        final List<String> shown = new ArrayList<>();
        int cleared;
        final FormServer<String> server = new FormServer<String>((p, b) -> this.calls.add("bytes to " + p), () -> 5_000)
                .fallback(this);

        @Override
        public boolean hasForms(String player) {
            return player.equals("modded");
        }

        @Override
        public void show(String player, String dialog) {
            this.shown.add(dialog);
        }

        @Override
        public void clear(String player) {
            this.cleared++;
        }

        @Override
        public void changed(FormSession s, String id, Value v) {
            this.calls.add("changed " + id + " " + v);
        }

        @Override
        public void pressed(FormSession s, String id) {
            this.calls.add("pressed " + id);
            if (id.equals("save")) {
                s.text("status", Text.lit("Saved"));
            }
        }

        @Override
        public void closed(FormSession s, CloseReason why) {
            this.calls.add("closed " + why);
        }
    }

    private static Form shop() {
        return Form.custom("example:shop").title(Text.lit("Shop"))
                .text("status", Text.lit("Ready"))
                .toggle("sounds", Text.lit("Sounds"), true)
                .slider("volume", Text.lit("Volume"), 0, 100, 5, 80)
                .field("count", Text.lit("Count"), f -> f.charset(Form.Charset.DIGITS).maxLength(4))
                .dropdown("currency", Text.lit("Currency"), List.of(Text.lit("Emerald"), Text.lit("Diamond")), 0)
                .section("advanced", Text.lit("Advanced"), false, s -> s.button("wipe", Text.lit("Wipe"), Role.DESTRUCTIVE))
                .visible(false)
                .footer(r -> r.button("save", Text.lit("Save"), Role.PRIMARY)
                        .button("cancel", Text.lit("Cancel"), Role.SECONDARY).enabled(false))
                .build();
    }

    @Test
    void aClientWithoutMinuitGetsTheShopAsAMultiActionDialog() {
        Rig rig = new Rig();
        rig.server.open("vanilla", shop(), rig);
        rig.server.open("modded", shop(), rig);
        assertEquals(List.of("bytes to modded"), rig.calls);
        assertEquals(1, rig.shown.size());
        String d = rig.shown.get(0);
        assertTrue(d.startsWith("{\"type\":\"minecraft:multi_action\""), d);
        assertTrue(d.contains("\"after_action\":\"wait_for_response\",") && d.contains("\"pause\":false"), d);
        assertEquals(List.of("minecraft:boolean", "minecraft:number_range", "minecraft:text", "minecraft:single_option"),
                all(d, "\"type\":\"(minecraft:(?:boolean|number_range|text|single_option))\""));
        for (String key : all(d, "\"key\":\"([^\"]*)\"")) {
            assertTrue(VANILLA_KEY.matcher(key).matches(), key);
        }
        assertTrue(d.contains("\"start\":0,\"end\":100,\"step\":5,\"initial\":80"), d);
        assertTrue(d.contains("\"max_length\":4"), d);
        assertTrue(d.contains("{\"id\":\"o0\",\"display\":{\"text\":\"Emerald\"},\"initial\":true}"), d);
        // The hidden section's wipe and the disabled cancel have no button: save and the exit are left.
        assertEquals(List.of("save"), all(d, "\"minuit_press\":\"([^\"]*)\""));
        assertFalse(d.contains("Advanced") || d.contains("Cancel"), d);
        assertEquals(2, all(d, "\"type\":\"minecraft:dynamic/custom\",\"id\":\"minuit:form\"").size());
    }

    @Test
    void aDialogClickGoesThroughTheSessionChecksAndTheDialogComesBackPatched() {
        Rig rig = new Rig();
        FormSession session = rig.server.open("vanilla", shop(), rig);
        // As vanilla sends it: a boolean as a byte tag, a range as a float, options and text as strings.
        rig.server.receiveDialog("vanilla", Map.of(
                Fallback.SESSION, session.id(), Fallback.PRESS, "save",
                Fallback.key("sounds"), (byte) 0, Fallback.key("volume"), 50.0F,
                Fallback.key("count"), "12a", Fallback.key("currency"), "o1"));
        assertEquals(List.of("changed sounds Bool[value=false]", "changed volume Num[value=50]",
                "changed currency Num[value=1]", "pressed save"), rig.calls);
        assertEquals(new Value.Str(""), session.value("count"));
        String again = rig.shown.get(rig.shown.size() - 1);
        assertTrue(again.contains("{\"text\":\"Saved\"}") && !again.contains("Ready"), again);
        assertTrue(again.contains("\"type\":\"minecraft:boolean\",\"initial\":false"), again);

        rig.server.receiveDialog("vanilla", Map.of(Fallback.SESSION, session.id() + 1, Fallback.PRESS, "save"));
        assertEquals(4, rig.calls.size(), "a click for another session is dropped");
        rig.server.receiveDialog("vanilla", Map.of(Fallback.SESSION, session.id(), Fallback.CLOSED, (byte) 1));
        assertEquals("closed PLAYER", rig.calls.get(rig.calls.size() - 1));
        assertFalse(session.isOpen());
        assertEquals(1, rig.cleared);
    }

    private static List<String> all(String s, String regex) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile(regex).matcher(s);
        while (m.find()) {
            out.add(m.groupCount() > 0 ? m.group(1) : m.group());
        }
        return out;
    }
}
