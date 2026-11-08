package dev.dryaides.minuit.port.mc;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.dryaides.minuit.form.Fallback;
import dev.dryaides.minuit.form.FormServer;
import dev.dryaides.minuit.internal.game.ServerForms;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The vanilla dialog for players whose client does not draw forms, and its clicks. */
public final class Dialogs implements Fallback.Dialogs<ServerPlayer> {

    private static final Logger LOG = LoggerFactory.getLogger("minuit");
    private static final Identifier ACTION = Identifier.parse(Fallback.ACTION);

    private Dialogs() {
    }

    /** The forms of this generation: a vanilla dialog for players without minuit. */
    public static FormServer<ServerPlayer> attach(FormServer<ServerPlayer> forms) {
        return forms.fallback(new Dialogs());
    }

    /** A {@code minecraft:custom} click; true when it was a form's and is taken here. */
    public static boolean click(ServerPlayer player, Identifier id, Optional<Tag> payload) {
        if (!ACTION.equals(id)) {
            return false;
        }
        ServerForms.forms().receiveDialog(player, values(payload.orElse(null)));
        return true;
    }

    /** The click payload as the Java values {@link FormServer#receiveDialog} reads: numbers and strings. */
    static Map<String, Object> values(Tag payload) {
        Map<String, Object> out = new HashMap<>();
        if (payload instanceof CompoundTag compound) {
            for (String key : compound.keySet()) {
                switch (compound.get(key)) {
                    case NumericTag n -> out.put(key, n.box());
                    case StringTag(String s) -> out.put(key, s);
                    case null, default -> {
                    }
                }
            }
        }
        return out;
    }

    @Override
    public boolean hasForms(ServerPlayer player) {
        return ServerForms.hasForms(player);
    }

    @Override
    public void show(ServerPlayer player, String dialog) {
        Dialog.DIRECT_CODEC.parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                        JsonParser.parseString(dialog))
                .ifSuccess(d -> player.connection.send(new ClientboundShowDialogPacket(Holder.direct(d))))
                .ifError(e -> LOG.warn("Dropped a form dialog the game refused: {}", e.message()));
    }

    @Override
    public void clear(ServerPlayer player) {
        player.connection.send(ClientboundClearDialogPacket.INSTANCE);
    }
}
