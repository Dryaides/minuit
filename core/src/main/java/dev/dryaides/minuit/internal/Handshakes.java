package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.form.ClientInfo;
import dev.dryaides.minuit.wire.Caps;
import dev.dryaides.minuit.wire.Hello;
import dev.dryaides.minuit.wire.WireException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Both halves of the hello, called by the loader entry points; mods read it through MinuitServer. Not API. */
public final class Handshakes {

    private static final Map<UUID, ClientInfo> CLIENTS = new ConcurrentHashMap<>();
    private static volatile byte[] ownHello;

    private Handshakes() {
    }

    /** The hello this client sends at join, when the server announces the channel, and to answer a server hello. */
    public static byte[] clientHello() {
        byte[] now = ownHello;
        if (now == null) {
            now = new Hello(Hello.PROTOCOL, version(), List.of(Caps.HELLO_1, Caps.FORM_1)).encode();
            ownHello = now;
        }
        return now;
    }

    /** A client hello reached the server: kept until {@link #left}. A broken one is dropped with a line to {@code warn}. */
    public static void received(UUID player, byte[] bytes, Consumer<String> warn) {
        try {
            Hello hello = Hello.decode(bytes);
            CLIENTS.put(player, new ClientInfo(hello.protocol(), hello.version(), hello.caps()));
        } catch (WireException e) {
            warn.accept("Dropped a broken minuit hello from " + player + ": " + e.getMessage());
        }
    }

    public static void left(UUID player) {
        CLIENTS.remove(player);
    }

    public static Optional<ClientInfo> client(UUID player) {
        return Optional.ofNullable(CLIENTS.get(player));
    }

    /** True when the player's client sent a hello with {@code form/1}. */
    public static boolean hasForms(UUID player) {
        return client(player).map(c -> c.caps().contains(Caps.FORM_1)).orElse(false);
    }

    private static String version() {
        Properties props = new Properties();
        try (InputStream in = Handshakes.class.getResourceAsStream("/META-INF/minuit/build.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            System.getLogger("minuit").log(System.Logger.Level.WARNING, "Could not read the minuit build properties", e);
        }
        String version = props.getProperty("version", "unknown");
        return version.length() > Hello.MAX_VERSION ? version.substring(0, Hello.MAX_VERSION) : version;
    }
}
