package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.internal.port.TextureBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/** PNG bytes to a game texture, for {@link dev.dryaides.minuit.client.AsyncImages}. Client only. */
public final class GameTextures implements TextureBridge {

    /** Decodes here, uploads on the client thread, where GL calls belong. */
    @Override
    public void upload(AsyncImage target, String key, byte[] png) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(png));
        } catch (Exception e) {
            image = null;
        }
        if (image == null) {
            target.failed();
            return;
        }
        BufferedImage read = image;
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            try {
                ResourceLocation id = GameIds.of("minuit", "dynamic/" + key);
                mc.getTextureManager().deleteTexture(id);
                mc.getTextureManager().loadTexture(id, new DynamicTexture(read));
                target.ready(id.toString(), read.getWidth(), read.getHeight());
            } catch (Exception e) {
                target.failed();
            }
        });
    }

    @Override
    public void release(String texture) {
        ResourceLocation id = GameIds.id(texture);
        if (id != null) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> mc.getTextureManager().deleteTexture(id));
        }
    }
}
