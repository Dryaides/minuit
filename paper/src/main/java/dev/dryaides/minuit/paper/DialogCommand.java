package dev.dryaides.minuit.paper;

import dev.dryaides.minuit.form.CloseReason;
import dev.dryaides.minuit.form.Form;
import dev.dryaides.minuit.form.FormHandler;
import dev.dryaides.minuit.form.FormSession;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@code /<label> dialog <form id> [player]}: shows one of the plugin's forms as the vanilla dialog,
 * even to a player with a minuit client, and tells the sender what the player pressed. A test aid
 * for the dialog fallback on a real server; the plugin registers it itself, under the permission it
 * chooses, for example:
 *
 * <pre>{@code
 * getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e ->
 *         e.registrar().register("minuit", new DialogCommand(forms, Map.of("example:volume", VOLUME))));
 * }</pre>
 */
public final class DialogCommand implements BasicCommand {

    private final PaperForms forms;
    private final Map<String, Form> byId;

    public DialogCommand(PaperForms forms, Map<String, Form> byId) {
        this.forms = forms;
        this.byId = Map.copyOf(byId);
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length < 2 || args.length > 3 || !args[0].equals("dialog")) {
            sender.sendMessage("Usage: dialog <form id> [player]");
            return;
        }
        Form form = this.byId.get(args[1]);
        if (form == null) {
            sender.sendMessage("No form " + args[1] + "; known: " + String.join(", ", this.byId.keySet()));
            return;
        }
        Player target = args.length == 3 ? Bukkit.getPlayerExact(args[2])
                : source.getExecutor() instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage(args.length == 3 ? "No player " + args[2] + " online" : "Name a player");
            return;
        }
        this.forms.openAsDialog(target, form, new FormHandler() {
            @Override
            public void pressed(FormSession session, String id) {
                sender.sendMessage(form.screenId() + ": " + target.getName() + " pressed " + id);
            }

            @Override
            public void closed(FormSession session, CloseReason reason) {
                sender.sendMessage(form.screenId() + ": closed, " + reason);
            }
        });
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        Stream<String> options = switch (args.length) {
            case 0, 1 -> Stream.of("dialog");
            case 2 -> this.byId.keySet().stream();
            case 3 -> Bukkit.getOnlinePlayers().stream().map(Player::getName);
            default -> Stream.empty();
        };
        String typed = args.length == 0 ? "" : args[args.length - 1];
        return options.filter(o -> o.startsWith(typed)).sorted().toList();
    }

    @Override
    public String permission() {
        return "minuit.dialog";
    }
}
