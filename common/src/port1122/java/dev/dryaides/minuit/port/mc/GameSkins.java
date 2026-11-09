package dev.dryaides.minuit.port.mc;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.internal.port.SkinBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Player skins as {@link AsyncImage}s, for {@link dev.dryaides.minuit.client.PlayerSkins}. Client only. */
public final class GameSkins implements SkinBridge {

    private static final int SIZE = 64;

    /** The session service blocks on the network: profiles are filled off the game thread, one at a time. */
    private static final ExecutorService LOOKUPS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "minuit skin lookup");
        t.setDaemon(true);
        return t;
    });

    /**
     * Points {@code target} at the skin of {@code id}: the player list's skin when the player is on the
     * server, else the default Steve or Alex of that id, swapped for the real skin once the session
     * service answers for {@code name}. 1.12.2's skin manager reads the textures from the profile's
     * properties, so the profile is filled first; the manager calls back on the game thread.
     */
    @Override
    public void fill(AsyncImage target, UUID id, String name) {
        Minecraft mc = Minecraft.getMinecraft();
        NetHandlerPlayClient connection = mc.getConnection();
        NetworkPlayerInfo info = connection == null ? null : connection.getPlayerInfo(id);
        if (info != null) {
            show(target, info.getLocationSkin());
            return;
        }
        show(target, DefaultPlayerSkin.getDefaultSkin(id));
        if (name != null && !name.trim().isEmpty()) {
            LOOKUPS.execute(() -> {
                GameProfile profile = mc.getSessionService().fillProfileProperties(new GameProfile(id, name), false);
                mc.getSkinManager().loadProfileTextures(profile, (type, texture, profileTexture) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        show(target, texture);
                    }
                }, false);
            });
        }
    }

    private static void show(AsyncImage target, ResourceLocation texture) {
        target.ready(texture.toString(), SIZE, SIZE);
    }
}
