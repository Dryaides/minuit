package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.form.FormServer;
import dev.dryaides.minuit.internal.Handshakes;
import net.minecraft.server.level.ServerPlayer;

/**
 * No vanilla dialogs before 1.21.6, so this generation has no fallback: a form opened for a player
 * whose client draws no forms closes at once as {@code CloseReason.UNSUPPORTED} and nothing is sent.
 */
public final class Dialogs {

    private Dialogs() {
    }

    /** The forms of this generation: minUIt required on the client. */
    public static FormServer<ServerPlayer> attach(FormServer<ServerPlayer> forms) {
        return forms.requireForms(player -> Handshakes.hasForms(player.getUUID()));
    }
}
