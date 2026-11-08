package dev.dryaides.minuit.server;

import dev.dryaides.minuit.form.ClientInfo;
import dev.dryaides.minuit.form.FormServer;
import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.internal.game.ServerForms;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** The server half of the kit: which players run minuit, from the hello their client sent, and the forms. */
public final class MinuitServer {

    private MinuitServer() {
    }

    /** The player's hello, kept until they leave; empty when their client has no minuit. */
    public static Optional<ClientInfo> client(ServerPlayer player) {
        return Handshakes.client(player.getUUID());
    }

    /**
     * The server's forms: {@code open} shows one on a player's screen, drawn by minuit when their
     * client has it, else as a vanilla dialog. Call it on the server thread.
     */
    public static FormServer<ServerPlayer> forms() {
        return ServerForms.forms();
    }
}
