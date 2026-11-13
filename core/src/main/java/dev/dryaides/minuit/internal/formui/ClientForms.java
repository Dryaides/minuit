package dev.dryaides.minuit.internal.formui;

import dev.dryaides.minuit.internal.port.ClientWire;
import dev.dryaides.minuit.wire.Channels;
import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.ScreenMessage;

import java.util.function.Consumer;

/**
 * The client end of the forms: shows each form sent on {@code minuit:screen} through the host's
 * screens in its mod's theme and sends the player's input on {@code minuit:input}. The loader entry
 * points connect the wire and the host, and hand it every screen message on the render thread and
 * every leave. Not API.
 */
public final class ClientForms {

    private static volatile ClientWire wire = ClientWire.NONE;
    private static volatile FormClient.Host host = new FormClient.Host() {
        @Override
        public FormPanel show(ScreenMessage.Open open, Consumer<InputMessage> out) {
            return null;
        }

        @Override
        public void hide(FormPanel panel) {
        }
    };
    private static FormClient client = fresh();

    private ClientForms() {
    }

    /** How this loader sends to the server, and the screens that show forms; set once at start. */
    public static void connect(ClientWire send, FormClient.Host screens) {
        wire = send;
        host = screens;
    }

    public static void screen(byte[] bytes) {
        client.receive(bytes);
    }

    /** The player left the server: a form still shown belongs to no session any more. */
    public static void left() {
        client = fresh();
    }

    public static FormClient client() {
        return client;
    }

    private static FormClient fresh() {
        return new FormClient(bytes -> wire.send(Channels.INPUT, bytes), new FormClient.Host() {
            @Override
            public FormPanel show(ScreenMessage.Open open, Consumer<InputMessage> out) {
                return host.show(open, out);
            }

            @Override
            public void hide(FormPanel panel) {
                host.hide(panel);
            }
        });
    }
}
