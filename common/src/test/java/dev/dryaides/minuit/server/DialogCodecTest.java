package dev.dryaides.minuit.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.dryaides.minuit.form.Fallback;
import dev.dryaides.minuit.form.Form;
import dev.dryaides.minuit.form.FormHandler;
import dev.dryaides.minuit.form.FormServer;
import dev.dryaides.minuit.form.Image;
import dev.dryaides.minuit.form.Role;
import dev.dryaides.minuit.form.Text;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Every fallback dialog parses with the game's own dialog codec and keeps every field it sent. */
class DialogCodecTest {

    /** Fields the codec leaves out when they hold its default. */
    private static final Map<String, String> DEFAULTS = Map.of(
            "can_close_with_escape", "true", "count", "1", "width", "200", "label_visible", "true");

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theShopParses() {
        Form shop = Form.custom("example:shop").title(Text.lit("Shop"))
                .text(Text.tr("example.hint", "Pick one", Text.lit("x")))
                .toggle("sounds", Text.lit("Sounds"), true)
                .slider("volume", Text.lit("Volume"), 0, 100, 5, 80)
                .field("count", Text.lit("Count"), f -> f.value("3").maxLength(4))
                .dropdown("currency", Text.lit("Currency"), List.of(Text.lit("Emerald"), Text.lit("Diamond")), 1)
                .progress("load", Text.lit("Load"), 0.5F)
                .image("gem", new Image.Item("minecraft:emerald"))
                .tabs("pages", t -> t.page("one", Text.lit("One"), p -> p.text(Text.lit("First"))))
                .footer(r -> r.button("save", Text.lit("Save"), Role.PRIMARY).tooltip(Text.lit("Keep it")))
                .build();
        assertInstanceOf(MultiActionDialog.class, parse(shown(shop)));
    }

    @Test
    void aFormWithoutButtonsIsANotice() {
        Form bare = Form.custom("example:bare").title(Text.lit("Bare")).text(Text.lit("Nothing to press")).build();
        assertInstanceOf(NoticeDialog.class, parse(shown(bare)));
    }

    private static String shown(Form form) {
        List<String> out = new ArrayList<>();
        FormServer<String> server = new FormServer<String>((p, b) -> { }).fallback(new Fallback.Dialogs<>() {
            @Override
            public boolean hasForms(String player) {
                return false;
            }

            @Override
            public void show(String player, String dialog) {
                out.add(dialog);
            }

            @Override
            public void clear(String player) {
            }
        });
        server.open("p", form, new FormHandler() { });
        return out.getFirst();
    }

    private static Dialog parse(String json) {
        DynamicOps<JsonElement> ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                .createSerializationContext(JsonOps.INSTANCE);
        JsonElement sent = JsonParser.parseString(json);
        Dialog dialog = Dialog.DIRECT_CODEC.parse(ops, sent).getOrThrow();
        JsonElement back = Dialog.DIRECT_CODEC.encodeStart(ops, dialog).getOrThrow();
        List<String> dropped = new ArrayList<>();
        dropped(sent, back, "", dropped);
        assertEquals(List.of(), dropped, json + "\nback: " + back);
        return dialog;
    }

    /** Fields the codec ignored: in {@code sent}, missing from {@code back}, and not a default. */
    private static void dropped(JsonElement sent, JsonElement back, String path, List<String> out) {
        if (sent.isJsonObject() && back.isJsonPrimitive()) {
            JsonObject o = sent.getAsJsonObject();
            if (!(o.size() == 1 && o.has("text") && o.get("text").equals(back))) {
                out.add(path + " became " + back);
            }
        } else if (sent.isJsonObject()) {
            JsonObject b = back.isJsonObject() ? back.getAsJsonObject() : new JsonObject();
            for (Map.Entry<String, JsonElement> e : sent.getAsJsonObject().entrySet()) {
                JsonElement got = b.get(e.getKey());
                if (got == null) {
                    if (!e.getValue().toString().equals(DEFAULTS.get(e.getKey()))) {
                        out.add(path + "/" + e.getKey());
                    }
                } else {
                    dropped(e.getValue(), got, path + "/" + e.getKey(), out);
                }
            }
        } else if (sent.isJsonArray() && back.isJsonArray()) {
            for (int i = 0; i < Math.min(sent.getAsJsonArray().size(), back.getAsJsonArray().size()); i++) {
                dropped(sent.getAsJsonArray().get(i), back.getAsJsonArray().get(i), path + "/" + i, out);
            }
        }
    }
}
