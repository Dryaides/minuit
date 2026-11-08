package dev.dryaides.minuit.paper;

import dev.dryaides.minuit.form.Form;
import dev.dryaides.minuit.form.FormHandler;
import dev.dryaides.minuit.form.FormSession;
import dev.dryaides.minuit.form.Role;
import dev.dryaides.minuit.form.Text;
import dev.dryaides.minuit.form.Value;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * How a plugin serves a form, not shipped: {@code /volume} opens a slider and a save button. A
 * minuit client draws it with the player's theme, a vanilla client gets the same form as a dialog.
 */
public final class ExamplePlugin extends JavaPlugin {

    private static final Form VOLUME = Form.custom("example:volume").title(Text.lit("Volume"))
            .slider("volume", Text.lit("Volume"), 0, 100, 5, 80)
            .text("status", Text.lit(""))
            .footer(r -> r.button("save", Text.lit("Save"), Role.PRIMARY))
            .build();

    private PaperForms forms;

    @Override
    public void onEnable() {
        this.forms = PaperForms.of(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }
        this.forms.forms().open(player, VOLUME, new FormHandler() {
            @Override
            public void pressed(FormSession session, String id) {
                if (session.value("volume") instanceof Value.Num(int volume)) {
                    session.text("status", Text.lit("Saved " + volume));
                }
            }
        });
        return true;
    }
}
