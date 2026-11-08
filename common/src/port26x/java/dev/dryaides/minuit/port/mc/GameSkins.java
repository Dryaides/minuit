package dev.dryaides.minuit.port.mc;

import com.mojang.authlib.GameProfile;
import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.internal.port.SkinBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;

/** Player skins as {@link AsyncImage}s, for {@link dev.dryaides.minuit.client.PlayerSkins}. Client only. */
public final class GameSkins implements SkinBridge {

    private static final int SIZE = 64;

    /**
     * Points {@code target} at the skin of {@code id}: the player list's skin when the player is on
     * the server, else the default Steve or Alex of that id, swapped for the real skin if the skin
     * service finds one for {@code name}.
     */
    @Override
    public void fill(AsyncImage target, UUID id, String name) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(id);
        if (info != null) {
            show(target, info.getSkin());
            return;
        }
        show(target, DefaultPlayerSkin.get(id));
        if (name != null && !name.isBlank()) {
            mc.getSkinManager().get(new GameProfile(id, name))
                    .thenAccept(found -> found.ifPresent(skin -> mc.execute(() -> show(target, skin))));
        }
    }

    private static void show(AsyncImage target, PlayerSkin skin) {
        target.ready(skin.body().texturePath().toString(), SIZE, SIZE);
    }
}
