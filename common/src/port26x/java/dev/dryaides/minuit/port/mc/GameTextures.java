package dev.dryaides.minuit.port.mc;

import com.mojang.blaze3d.platform.NativeImage;
import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.internal.port.TextureBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** PNG bytes to a game texture, for {@link dev.dryaides.minuit.client.AsyncImages}. Client only. */
public final class GameTextures implements TextureBridge {

    /** Uploads on the render thread. */
    @Override
    public void upload(AsyncImage target, String key, byte[] png) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try (NativeImage read = NativeImage.read(png)) {
                // The texture closes what it holds, so it gets a copy the read buffer does not share.
                NativeImage owned = new NativeImage(read.getWidth(), read.getHeight(), false);
                owned.copyFrom(read);
                Identifier id = Identifier.fromNamespaceAndPath("minuit", "dynamic/" + key);
                mc.getTextureManager().release(id);
                mc.getTextureManager().register(id, new DynamicTexture(() -> "minuit_image", owned));
                target.ready(id.toString(), owned.getWidth(), owned.getHeight());
            } catch (Exception e) {
                target.failed();
            }
        });
    }

    @Override
    public void release(String texture) {
        Identifier id = GameIds.id(texture);
        if (id != null) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.getTextureManager().release(id));
        }
    }
}
