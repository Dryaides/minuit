package dev.dryaides.minuit.port.mc;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.internal.port.SkinBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Player skins as {@link AsyncImage}s, for {@link dev.dryaides.minuit.client.PlayerSkins}. Client only. */
public final class GameSkins implements SkinBridge {

    private static final int SIZE = 64;

    /**
     * Points {@code target} at the skin of {@code id}: the player list's skin when the player is on
     * the server, else the default Steve or Alex of that id, swapped for the real skin once the skin
     * service answers for {@code name} (1.20.1 fills the profile's textures itself, and calls back on
     * the game thread only when it finds a skin).
     */
    @Override
    public void fill(AsyncImage target, UUID id, String name) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(id);
        if (info != null) {
            show(target, info.getSkinLocation());
            return;
        }
        show(target, DefaultPlayerSkin.getDefaultSkin(id));
        if (name != null && !name.isBlank()) {
            mc.getSkinManager().registerSkins(new GameProfile(id, name), (type, texture, profileTexture) -> {
                if (type == MinecraftProfileTexture.Type.SKIN) {
                    show(target, texture);
                }
            }, false);
        }
    }

    private static void show(AsyncImage target, ResourceLocation texture) {
        target.ready(texture.toString(), SIZE, SIZE);
    }
}
