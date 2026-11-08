package dev.dryaides.minuit.formui;

import dev.dryaides.minuit.Theme;
import dev.dryaides.minuit.Themes;
import dev.dryaides.minuit.form.CloseReason;
import dev.dryaides.minuit.wire.ByteReader;
import dev.dryaides.minuit.wire.InputMessage;
import dev.dryaides.minuit.wire.ScreenMessage;
import dev.dryaides.minuit.wire.WireException;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The client end of {@code minuit:screen} and {@code minuit:input}. The host gives it every screen
 * message and a way to show a panel; it answers a form it cannot draw (broken, or over a size cap)
 * with {@code closed(invalid)}.
 */
public final class FormClient {

    /** What the host does with screens: show a new panel, or take the shown one away. */
    public interface Host {

        /**
         * Builds and shows the panel for {@code open}, replacing any form panel shown; {@code out}
         * is the panel's input channel, to pass to {@link FormPanel#of}.
         */
        FormPanel show(ScreenMessage.Open open, Consumer<InputMessage> out);

        void hide(FormPanel panel);
    }

    private final Consumer<byte[]> send;
    private final Host host;
    private FormPanel shown;

    /** @param send writes one {@code minuit:input} message */
    public FormClient(Consumer<byte[]> send, Host host) {
        this.send = send;
        this.host = host;
    }

    /**
     * The theme a form wears: the one registered by the mod that owns {@code screenId} (its
     * namespace), else {@link Themes#defaultTheme()} under that mod's id, under the player's choice for that mod.
     */
    public static Theme theme(String screenId) {
        int colon = screenId.indexOf(':');
        String mod = colon > 0 ? screenId.substring(0, colon) : "minecraft";
        Theme own = Themes.registered(mod);
        return Themes.resolve(own.id().equals(mod) ? own : own.as(mod), Themes.choices().forMod(mod));
    }

    /** The form panel on screen, or null. */
    public FormPanel shown() {
        return this.shown;
    }

    /** One {@code minuit:screen} message. Returns false when it was dropped. */
    public boolean receive(byte[] bytes) {
        ScreenMessage message;
        try {
            message = ScreenMessage.decode(bytes);
        } catch (WireException e) {
            refuse(bytes);
            return false;
        }
        if (message instanceof ScreenMessage.Open open) {
            if (this.shown != null) {
                this.shown.silence();
            }
            this.shown = this.host.show(open, input -> {
                // Escape or any other close by the panel: the form is no longer on screen.
                if (input instanceof InputMessage.Closed && this.shown != null
                        && this.shown.session() == open.session()) {
                    this.shown = null;
                }
                this.send.accept(input.encode());
            });
        } else if (message instanceof ScreenMessage.Patch patch) {
            if (this.shown == null || this.shown.session() != patch.session()) {
                return false;
            }
            this.shown.apply(patch);
        } else if (message instanceof ScreenMessage.Close close) {
            if (this.shown == null || this.shown.session() != close.session()) {
                return false;
            }
            FormPanel gone = this.shown;
            this.shown = null;
            gone.silence();
            this.host.hide(gone);
        } else {
            throw new IllegalStateException(String.valueOf(message));
        }
        return true;
    }

    /** The player left the screen some other way (another screen opened over it). */
    public void replaced() {
        if (this.shown != null) {
            FormPanel gone = this.shown;
            this.shown = null;
            gone.leave(CloseReason.REPLACED);
        }
    }

    /** An open the client cannot draw gets {@code closed(invalid)}; anything else broken is only dropped. */
    private void refuse(byte[] bytes) {
        try {
            ByteReader in = new ByteReader(bytes);
            if (in.varint() == ScreenMessage.OPEN) {
                int session = in.varint();
                this.send.accept(new InputMessage.Closed(session, 1, CloseReason.INVALID).encode());
            }
        } catch (RuntimeException e) {
            // Not even a session id to answer.
        }
    }
}
