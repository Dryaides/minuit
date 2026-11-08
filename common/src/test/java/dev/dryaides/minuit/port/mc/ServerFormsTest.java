package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.form.Fallback;
import dev.dryaides.minuit.form.Form;
import dev.dryaides.minuit.form.FormHandler;
import dev.dryaides.minuit.form.FormServer;
import dev.dryaides.minuit.form.FormSession;
import dev.dryaides.minuit.form.Role;
import dev.dryaides.minuit.form.Text;
import dev.dryaides.minuit.form.Value;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A vanilla dialog click, as the game's {@code dynamic/custom} action sends it, reaches the form. */
class ServerFormsTest {

    @Test
    void aDialogClickChangesTheValuesAndPresses() {
        List<String> heard = new ArrayList<>();
        FormServer<String> server = new FormServer<String>((p, b) -> { }).fallback(new Fallback.Dialogs<>() {
            @Override
            public boolean hasForms(String player) {
                return false;
            }

            @Override
            public void show(String player, String dialog) {
            }

            @Override
            public void clear(String player) {
            }
        });
        Form form = Form.custom("example:shop").title(Text.lit("Shop"))
                .toggle("sounds", Text.lit("Sounds"), true)
                .slider("volume", Text.lit("Volume"), 0, 100, 5, 80)
                .footer(r -> r.button("save", Text.lit("Save"), Role.PRIMARY))
                .build();
        FormSession session = server.open("p", form, new FormHandler() {
            @Override
            public void changed(FormSession s, String id, Value value) {
                heard.add(id + "=" + value);
            }

            @Override
            public void pressed(FormSession s, String id) {
                heard.add("press " + id);
            }
        });

        // A boolean input arrives as a byte, a number range as a float, the button's additions as they were written.
        CompoundTag click = new CompoundTag();
        click.put(Fallback.SESSION, IntTag.valueOf(session.id()));
        click.put("v_sounds", ByteTag.valueOf(false));
        click.put("v_volume", FloatTag.valueOf(40.0F));
        click.put(Fallback.PRESS, StringTag.valueOf("save"));
        server.receiveDialog("p", Dialogs.values(click));

        assertEquals(List.of("sounds=" + new Value.Bool(false), "volume=" + new Value.Num(40), "press save"), heard);
    }
}
