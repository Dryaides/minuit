package dev.dryaides.minuit.paper;

import dev.dryaides.minuit.form.ClientInfo;
import dev.dryaides.minuit.form.Fallback;
import dev.dryaides.minuit.form.FormServer;
import dev.dryaides.minuit.wire.Caps;
import dev.dryaides.minuit.wire.Channels;
import dev.dryaides.minuit.wire.Hello;
import dev.dryaides.minuit.wire.ServerHello;
import dev.dryaides.minuit.wire.WireException;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * minuit forms from a Paper plugin. {@link #of} registers the {@code minuit:hello},
 * {@code minuit:screen} and {@code minuit:input} channels and the listeners; the plugin then opens
 * forms through {@link #forms()}. A player whose client did not say {@code form/1} in its hello
 * gets the same form as a vanilla dialog. Every handler call runs on the server's main thread.
 */
public final class PaperForms {

    static final String HELLO = Channels.NAMESPACE + ":" + Channels.HELLO;
    static final String SCREEN = Channels.NAMESPACE + ":" + Channels.SCREEN;
    static final String INPUT = Channels.NAMESPACE + ":" + Channels.INPUT;

    private final Plugin plugin;
    private final Executor main;
    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();
    private final FormServer<Player> forms;

    PaperForms(Plugin plugin, Messenger messenger, Executor main) {
        this.plugin = plugin;
        this.main = main;
        this.forms = new FormServer<Player>((player, bytes) -> player.sendPluginMessage(plugin, SCREEN, bytes))
                .fallback(new Dialogs());
        PluginMessageListener incoming = this::received;
        messenger.registerOutgoingPluginChannel(plugin, HELLO);
        messenger.registerOutgoingPluginChannel(plugin, SCREEN);
        messenger.registerIncomingPluginChannel(plugin, HELLO, incoming);
        messenger.registerIncomingPluginChannel(plugin, INPUT, incoming);
    }

    /**
     * Serves forms for {@code plugin}; call it once from {@code onEnable}. Players already online
     * get a server hello, which their minuit client answers.
     */
    public static PaperForms of(Plugin plugin) {
        Server server = plugin.getServer();
        PaperForms forms = new PaperForms(plugin, server.getMessenger(), task -> {
            if (server.isPrimaryThread()) {
                task.run();
            } else {
                server.getScheduler().runTask(plugin, task);
            }
        });
        server.getPluginManager().registerEvents(forms.new Events(), plugin);
        byte[] hello = new ServerHello(Hello.PROTOCOL, List.of(Caps.FORM_1)).encode();
        for (Player player : server.getOnlinePlayers()) {
            if (player.getListeningPluginChannels().contains(HELLO)) {
                player.sendPluginMessage(plugin, HELLO, hello);
            }
        }
        return forms;
    }

    /** The open forms of this plugin's players. */
    public FormServer<Player> forms() {
        return this.forms;
    }

    /** The player's minuit hello, empty for a client without minuit or one that has not said it yet. */
    public Optional<ClientInfo> client(Player player) {
        return Optional.ofNullable(this.clients.get(player.getUniqueId()));
    }

    /** The locale to write a form's literals in: a plugin has no client lang file. */
    public Locale locale(Player player) {
        return player.locale();
    }

    private void received(String channel, Player player, byte[] bytes) {
        this.main.execute(() -> {
            if (channel.equals(HELLO)) {
                try {
                    Hello hello = Hello.decode(bytes);
                    this.clients.put(player.getUniqueId(), new ClientInfo(hello.protocol(), hello.version(), hello.caps()));
                } catch (WireException e) {
                    this.plugin.getLogger().warning("Dropped a broken minuit hello from " + player.getName() + ": " + e.getMessage());
                }
            } else if (channel.equals(INPUT)) {
                this.forms.receive(player, bytes);
            }
        });
    }

    /** The player left; package-private so the tests can drive it without an event. */
    void left(Player player) {
        this.forms.left(player);
        this.clients.remove(player.getUniqueId());
    }

    /** A {@code minecraft:custom} click with the given SNBT payload. */
    void clicked(Player player, String snbt) {
        Map<String, Object> payload;
        try {
            payload = Snbt.flat(snbt);
        } catch (IllegalArgumentException e) {
            return;
        }
        this.main.execute(() -> this.forms.receiveDialog(player, payload));
    }

    final class Events implements Listener {

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            left(event.getPlayer());
        }

        @EventHandler
        public void onClick(PlayerCustomClickEvent event) {
            if (event.getIdentifier().asString().equals(Fallback.ACTION)
                    && event.getCommonConnection() instanceof PlayerGameConnection game) {
                BinaryTagHolder tag = event.getTag();
                clicked(game.getPlayer(), tag == null ? "{}" : tag.string());
            }
        }
    }

    /**
     * The Paper API builds dialogs only through its registry builders and has no way in from dialog
     * JSON, so the fallback goes through the vanilla {@code dialog show} command, whose inline
     * dialog argument reads the JSON as SNBT, from a sender that drops its feedback.
     */
    private final class Dialogs implements Fallback.Dialogs<Player> {

        @Override
        public boolean hasForms(Player player) {
            return client(player).map(c -> c.has(ClientInfo.FORMS)).orElse(false);
        }

        @Override
        public void show(Player player, String dialog) {
            Server server = PaperForms.this.plugin.getServer();
            server.dispatchCommand(server.createCommandSender(feedback -> {
            }), "minecraft:dialog show " + player.getUniqueId() + " " + dialog);
        }

        @Override
        public void clear(Player player) {
            player.closeDialog();
        }
    }
}
