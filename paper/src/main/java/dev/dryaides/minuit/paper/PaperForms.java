package dev.dryaides.minuit.paper;

import dev.dryaides.minuit.form.ClientInfo;
import dev.dryaides.minuit.form.Fallback;
import dev.dryaides.minuit.form.Form;
import dev.dryaides.minuit.form.FormHandler;
import dev.dryaides.minuit.form.FormServer;
import dev.dryaides.minuit.form.FormSession;
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
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executor;

/**
 * minuit forms from a Paper plugin. {@link #of} registers the {@code minuit:hello},
 * {@code minuit:screen} and {@code minuit:input} channels and the listeners; the plugin then opens
 * forms through {@link #forms()}. A player whose client did not say {@code form/1} in its hello
 * gets the same form as a vanilla dialog. Every handler call runs on the server's main thread.
 *
 * <p>Several plugins on one server can each shade this module: they share one map, registered as a
 * Bukkit service of the JDK type {@link ConcurrentMap} (relocated minuit classes differ between
 * plugins, JDK and Bukkit ones do not). It holds the session id counter and, per player, the plugin
 * whose form the screen shows. Every plugin hears every {@code minuit:input} message, as Bukkit
 * delivers a channel to each listener; only the plugin that showed the player's form reads it, and a
 * form from another plugin ends the shown one as {@link dev.dryaides.minuit.form.CloseReason#REPLACED}.
 * The map also holds the wire protocol of the first copy; a copy on another protocol fails in
 * {@link #of} instead of sending screens the client would read wrong.
 */
public final class PaperForms {

    static final String HELLO = Channels.NAMESPACE + ":" + Channels.HELLO;
    static final String SCREEN = Channels.NAMESPACE + ":" + Channels.SCREEN;
    static final String INPUT = Channels.NAMESPACE + ":" + Channels.INPUT;

    private final Plugin plugin;
    private final Executor main;
    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();
    /** Players whose next form shows as a vanilla dialog whatever their client says. */
    private final Set<UUID> dialogOnce = ConcurrentHashMap.newKeySet();
    /** The key that marks the shared map among the {@link ConcurrentMap} services. */
    static final String SHARED = "minuit:forms";
    private static final String IDS = "minuit:forms/ids";
    /** The wire protocol of the first copy that made the shared map; every later copy must match it. */
    static final String PROTOCOL = "minuit:forms/protocol";

    private final FormServer<Player> forms;
    private final ConcurrentMap<Object, Object> shared;

    PaperForms(Plugin plugin, Messenger messenger, Executor main, ConcurrentMap<Object, Object> shared) {
        this.plugin = plugin;
        this.main = main;
        this.shared = shared;
        Object protocol = shared.putIfAbsent(PROTOCOL, Hello.PROTOCOL);
        if (protocol != null && !protocol.equals(Hello.PROTOCOL)) {
            throw new IllegalStateException(plugin.getName() + " shades minuit-paper on wire protocol " + Hello.PROTOCOL
                    + " but another plugin on this server serves protocol " + protocol
                    + "; update the plugins so they shade minuit versions with the same protocol");
        }
        AtomicInteger ids = (AtomicInteger) shared.computeIfAbsent(IDS, k -> new AtomicInteger(1));
        this.forms = new FormServer<Player>((player, bytes) -> player.sendPluginMessage(plugin, SCREEN, bytes))
                .fallback(new Dialogs())
                .sessionIds(ids::getAndIncrement)
                .onShown((player, session) -> shown(player));
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
        }, shared(server.getServicesManager(), plugin));
        server.getPluginManager().registerEvents(forms.new Events(), plugin);
        byte[] hello = new ServerHello(Hello.PROTOCOL, List.of(Caps.FORM_1)).encode();
        for (Player player : server.getOnlinePlayers()) {
            if (player.getListeningPluginChannels().contains(HELLO)) {
                player.sendPluginMessage(plugin, HELLO, hello);
            }
        }
        return forms;
    }

    /**
     * The map every minuit-paper copy on the server shares. Each plugin registers it under its own name
     * too, so it outlives the plugin that made it.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ConcurrentMap<Object, Object> shared(ServicesManager services, Plugin plugin) {
        ConcurrentMap<Object, Object> found = null;
        for (RegisteredServiceProvider<ConcurrentMap> r : services.getRegistrations(ConcurrentMap.class)) {
            if (r.getProvider().containsKey(SHARED)) {
                found = r.getProvider();
                break;
            }
        }
        if (found == null) {
            found = new ConcurrentHashMap<>();
            found.put(SHARED, Boolean.TRUE);
        }
        services.register(ConcurrentMap.class, found, plugin, ServicePriority.Lowest);
        return found;
    }

    /**
     * This plugin's form now shows on the player's screen: the plugin whose form it replaced ends it,
     * unless that plugin was disabled meanwhile. An entry is {owner, its replaced() call, its plugin}.
     */
    private void shown(Player player) {
        Object[] mine = {this, (Runnable) () -> this.forms.replaced(player), this.plugin};
        Object before = this.shared.put(player.getUniqueId(), mine);
        if (before instanceof Object[] other && other[0] != this
                && (other.length < 3 || !(other[2] instanceof Plugin owner) || owner.isEnabled())) {
            ((Runnable) other[1]).run();
        }
    }

    /**
     * The plugin is being disabled: its entries leave the shared map so no other plugin reaches its
     * handlers. Bukkit drops its service registrations right after the disable event.
     */
    void disabled() {
        this.shared.values().removeIf(v -> v instanceof Object[] entry && entry[0] == this);
        this.clients.clear();
    }

    /** True when the player's screen shows this plugin's form, or no minuit-paper form at all. */
    private boolean showsMine(Player player) {
        return !(this.shared.get(player.getUniqueId()) instanceof Object[] entry) || entry[0] == this;
    }

    /** The open forms of this plugin's players. */
    public FormServer<Player> forms() {
        return this.forms;
    }

    /**
     * Opens {@code form} as a vanilla dialog even for a player with a minuit client, to check how a
     * form reads without minuit; {@link DialogCommand} serves it as a command.
     */
    public FormSession openAsDialog(Player player, Form form, FormHandler handler) {
        this.dialogOnce.add(player.getUniqueId());
        try {
            return this.forms.open(player, form, handler);
        } finally {
            this.dialogOnce.remove(player.getUniqueId());
        }
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
            } else if (channel.equals(INPUT) && showsMine(player)) {
                this.forms.receive(player, bytes);
            }
        });
    }

    /** The player left; package-private so the tests can drive it without an event. */
    void left(Player player) {
        this.forms.left(player);
        this.clients.remove(player.getUniqueId());
        this.shared.remove(player.getUniqueId());
    }

    /** A {@code minecraft:custom} click with the given SNBT payload. */
    void clicked(Player player, String snbt) {
        Map<String, Object> payload;
        try {
            payload = Snbt.flat(snbt);
        } catch (IllegalArgumentException e) {
            return;
        }
        this.main.execute(() -> {
            if (showsMine(player)) {
                this.forms.receiveDialog(player, payload);
            }
        });
    }

    final class Events implements Listener {

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            left(event.getPlayer());
        }

        @EventHandler
        public void onDisable(PluginDisableEvent event) {
            if (event.getPlugin() == PaperForms.this.plugin) {
                disabled();
            }
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
            if (PaperForms.this.dialogOnce.contains(player.getUniqueId())) {
                return false;
            }
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
