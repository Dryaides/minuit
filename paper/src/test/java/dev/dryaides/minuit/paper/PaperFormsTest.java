package dev.dryaides.minuit.paper;

import dev.dryaides.minuit.form.CloseReason;
import dev.dryaides.minuit.form.Form;
import dev.dryaides.minuit.form.FormHandler;
import dev.dryaides.minuit.form.FormSession;
import dev.dryaides.minuit.form.Role;
import dev.dryaides.minuit.form.Text;
import dev.dryaides.minuit.form.Value;
import dev.dryaides.minuit.wire.Caps;
import dev.dryaides.minuit.wire.Fields;
import dev.dryaides.minuit.wire.Hello;
import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.ScreenMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A form and its inputs through a fake Bukkit Messenger, on the channel ids of FORMS_WIRE.md. */
class PaperFormsTest {

    private final Set<String> outgoing = new HashSet<>();
    private final Map<String, List<PluginMessageListener>> incoming = new HashMap<>();
    private final List<String[]> sentChannels = new ArrayList<>();
    private final List<byte[]> sent = new ArrayList<>();

    private final Plugin plugin = (Plugin) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Plugin.class},
            (proxy, m, args) -> switch (m.getName()) {
                case "getLogger" -> Logger.getLogger("test");
                case "isEnabled" -> true;
                case "hashCode" -> 1;
                case "equals" -> proxy == args[0];
                default -> null;
            });

    private final Messenger messenger = (Messenger) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Messenger.class},
            (proxy, m, args) -> {
                switch (m.getName()) {
                    case "registerOutgoingPluginChannel" -> this.outgoing.add((String) args[1]);
                    case "registerIncomingPluginChannel" -> this.incoming.computeIfAbsent((String) args[1], k -> new ArrayList<>())
                            .add((PluginMessageListener) args[2]);
                    default -> {
                    }
                }
                return null;
            });

    private final Player player = fakePlayer(UUID.fromString("00000000-0000-0000-0000-000000000001"));

    private Player fakePlayer(UUID id) {
        return (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> "Steve";
                    case "sendPluginMessage" -> {
                        this.sentChannels.add(new String[]{(String) args[1]});
                        this.sent.add((byte[]) args[2]);
                        yield null;
                    }
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Steve";
                    default -> null;
                });
    }

    /** Bukkit hands a message on a channel to every plugin listening on it. */
    private void deliver(String channel, byte[] bytes) {
        for (PluginMessageListener listener : this.incoming.get(channel)) {
            listener.onPluginMessageReceived(channel, this.player, bytes);
        }
    }

    private static FormHandler recording(List<String> calls, String who) {
        return new FormHandler() {
            @Override
            public void pressed(FormSession s, String id) {
                calls.add(who + " pressed " + id);
            }

            @Override
            public void closed(FormSession s, CloseReason why) {
                calls.add(who + " closed " + why);
            }
        };
    }

    @Test
    void aFormGoesOutOnScreenAndItsInputsComeBackToTheHandler() {
        PaperForms paper = new PaperForms(this.plugin, this.messenger, Runnable::run, new ConcurrentHashMap<>());
        assertEquals(Set.of("minuit:hello", "minuit:screen"), this.outgoing);
        assertEquals(Set.of("minuit:hello", "minuit:input"), this.incoming.keySet());

        deliver("minuit:hello", new Hello(Hello.PROTOCOL, "1.0.0", List.of(Caps.HELLO_1, Caps.FORM_1)).encode());
        assertEquals(List.of(Caps.HELLO_1, Caps.FORM_1), paper.client(this.player).orElseThrow().caps());

        List<String> calls = new ArrayList<>();
        Form form = Form.custom("shop:settings").title(Text.lit("Settings"))
                .slider("volume", Text.lit("Volume"), 0, 100, 5, 80)
                .footer(r -> r.button("save", Text.lit("Save"), Role.PRIMARY))
                .build();
        FormSession session = paper.forms().open(this.player, form, new FormHandler() {
            @Override
            public void changed(FormSession s, String id, Value value) {
                calls.add("changed " + id + " " + value);
            }

            @Override
            public void pressed(FormSession s, String id) {
                calls.add("pressed " + id);
            }

            @Override
            public void closed(FormSession s, CloseReason why) {
                calls.add("closed " + why);
            }
        });

        assertEquals(1, this.sent.size());
        assertEquals("minuit:screen", this.sentChannels.get(0)[0]);
        ScreenMessage.Open open = assertInstanceOf(ScreenMessage.Open.class, ScreenMessage.decode(this.sent.get(0)));
        assertEquals(session.id(), open.session());

        deliver("minuit:input", new InputMessage.Change(session.id(), 1, "volume", Fields.svarint(55)).encode());
        deliver("minuit:input", new InputMessage.Press(session.id(), 2, "save").encode());
        deliver("minuit:input", new InputMessage.Closed(session.id(), 3, CloseReason.PLAYER).encode());

        assertEquals(new Value.Num(55), session.value("volume"));
        assertEquals(List.of("changed volume " + new Value.Num(55), "pressed save", "closed PLAYER"), calls);
        assertFalse(session.isOpen());
    }

    @Test
    void twoPluginsSharingTheServerShowOneFormAtATimeAndEachHearsOnlyItsOwnInput() {
        ConcurrentHashMap<Object, Object> shared = new ConcurrentHashMap<>();
        PaperForms shop = new PaperForms(this.plugin, this.messenger, Runnable::run, shared);
        PaperForms quests = new PaperForms(this.plugin, this.messenger, Runnable::run, shared);
        deliver("minuit:hello", new Hello(Hello.PROTOCOL, "1.0.0", List.of(Caps.HELLO_1, Caps.FORM_1)).encode());
        Form form = Form.custom("x:menu").title(Text.lit("Menu"))
                .footer(r -> r.button("ok", Text.lit("OK"), Role.PRIMARY))
                .build();
        List<String> calls = new ArrayList<>();

        FormSession first = shop.forms().open(this.player, form, recording(calls, "shop"));
        FormSession second = quests.forms().open(this.player, form, recording(calls, "quests"));
        assertNotEquals(first.id(), second.id());
        assertEquals(List.of("shop closed REPLACED"), calls);

        deliver("minuit:input", new InputMessage.Press(second.id(), 1, "ok").encode());
        deliver("minuit:input", new InputMessage.Press(first.id(), 2, "ok").encode());
        assertEquals(List.of("shop closed REPLACED", "quests pressed ok"), calls);
    }

    @Test
    void aDisabledPluginsFormIsNotEndedByTheNextPluginsForm() {
        boolean[] shopEnabled = {true};
        Plugin shopPlugin = (Plugin) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getLogger" -> Logger.getLogger("shop");
                    case "isEnabled" -> shopEnabled[0];
                    case "hashCode" -> 2;
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
        ConcurrentHashMap<Object, Object> shared = new ConcurrentHashMap<>();
        PaperForms shop = new PaperForms(shopPlugin, this.messenger, Runnable::run, shared);
        PaperForms quests = new PaperForms(this.plugin, this.messenger, Runnable::run, shared);
        deliver("minuit:hello", new Hello(Hello.PROTOCOL, "1.0.0", List.of(Caps.HELLO_1, Caps.FORM_1)).encode());
        Form form = Form.custom("x:menu").title(Text.lit("Menu"))
                .footer(r -> r.button("ok", Text.lit("OK"), Role.PRIMARY))
                .build();
        List<String> calls = new ArrayList<>();

        shop.forms().open(this.player, form, recording(calls, "shop"));
        shopEnabled[0] = false;
        shop.disabled();
        assertFalse(shared.containsKey(this.player.getUniqueId()));
        int before = this.sent.size();
        FormSession second = quests.forms().open(this.player, form, recording(calls, "quests"));

        assertEquals(List.of(), calls);
        assertEquals(before + 1, this.sent.size());
        assertInstanceOf(ScreenMessage.Open.class, ScreenMessage.decode(this.sent.get(before)));
        deliver("minuit:input", new InputMessage.Press(second.id(), 1, "ok").encode());
        assertEquals(List.of("quests pressed ok"), calls);
    }

    @Test
    void aVanillaClickPayloadReadsAsTheValuesFallbackExpects() {
        Map<String, Object> payload = Snbt.flat("{minuit_session:3,minuit_press:\"save\",v_name:'Bob \"B\"',v_on:1b,v_volume:55.0f,v_pick:\"o2\"}");

        assertEquals(Map.of("minuit_session", 3, "minuit_press", "save", "v_name", "Bob \"B\"",
                "v_on", 1, "v_volume", 55.0f, "v_pick", "o2"), payload);
    }

    @Test
    void aCopyOnAnotherWireProtocolIsRefusedBeforeItTakesTheChannels() {
        ConcurrentHashMap<Object, Object> shared = new ConcurrentHashMap<>();
        shared.put(PaperForms.PROTOCOL, Hello.PROTOCOL + 1);

        assertThrows(IllegalStateException.class, () -> new PaperForms(this.plugin, this.messenger, Runnable::run, shared));
        assertTrue(this.incoming.isEmpty());
    }
}
