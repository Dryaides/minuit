package dev.dryaides.minuit.internal.game;

import dev.dryaides.minuit.form.FormServer;
import dev.dryaides.minuit.internal.Handshakes;
import dev.dryaides.minuit.port.mc.Dialogs;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.function.BiConsumer;

/**
 * The server end of the forms: one {@link FormServer} over {@code minuit:screen}, with what this
 * game version offers players whose client does not draw forms ({@link Dialogs#attach}). The loader
 * entry points hand it the sender, every {@code minuit:input} message and every leave, all on the
 * server thread. Not API.
 */
public final class ServerForms {

    private static volatile BiConsumer<EntityPlayerMP, byte[]> sender = (player, bytes) -> {
    };
    private static final FormServer<EntityPlayerMP> FORMS = Dialogs.attach(
            new FormServer<EntityPlayerMP>((player, bytes) -> sender.accept(player, bytes))
                    .keyedBy(EntityPlayerMP::getUniqueID));

    private ServerForms() {
    }

    public static FormServer<EntityPlayerMP> forms() {
        return FORMS;
    }

    /** How this loader sends a {@code minuit:screen} message to one player; set once at start. */
    public static void sender(BiConsumer<EntityPlayerMP, byte[]> send) {
        sender = send;
    }

    public static void input(EntityPlayerMP player, byte[] bytes) {
        FORMS.receive(player, bytes);
    }

    public static void left(EntityPlayerMP player) {
        FORMS.left(player);
    }

    /** True when the player's client sent a hello with {@code form/1}. */
    public static boolean hasForms(EntityPlayerMP player) {
        return Handshakes.hasForms(player.getUniqueID());
    }
}
