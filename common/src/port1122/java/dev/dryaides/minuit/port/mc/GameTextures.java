package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.internal.ImageSource;
import dev.dryaides.minuit.internal.port.TextureBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

/** PNG bytes to a game texture, for {@link dev.dryaides.minuit.client.AsyncImages}. Client only. */
public final class GameTextures implements TextureBridge, ImageSource {

    private final Set<ResourceLocation> baked = new HashSet<>();

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

    /** A PNG of the loaded resource packs, on the client thread. */
    @Override
    public Pixels read(String id) {
        ResourceLocation rl = GameIds.id(id);
        if (rl == null) {
            return null;
        }
        try (IResource res = Minecraft.getMinecraft().getResourceManager().getResource(rl)) {
            BufferedImage img = ImageIO.read(res.getInputStream());
            if (img == null) {
                return null;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            return new Pixels(w, h, img.getRGB(0, 0, w, h, null, 0, w));
        } catch (Exception e) {
            return null;
        }
    }

    /** Called while drawing, so on the client thread: the texture exists before the blit that follows. */
    @Override
    public String bake(String key, Pixels image) {
        BufferedImage img = new BufferedImage(image.w(), image.h(), BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, image.w(), image.h(), image.argb(), 0, image.w());
        ResourceLocation id = GameIds.of("minuit", "baked/" + key);
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().deleteTexture(id);
        mc.getTextureManager().loadTexture(id, new DynamicTexture(img));
        this.baked.add(id);
        return id.toString();
    }

    /** On the render thread, while drawing. */
    @Override
    public void drop(String texture) {
        ResourceLocation id = GameIds.id(texture);
        if (id != null && this.baked.remove(id)) {
            Minecraft.getMinecraft().getTextureManager().deleteTexture(id);
        }
    }

    /** Runs in the reload listener, on the client thread, after the fresh families replaced the ones these served. */
    @Override
    public void reload() {
        Minecraft mc = Minecraft.getMinecraft();
        for (ResourceLocation id : this.baked) {
            mc.getTextureManager().deleteTexture(id);
        }
        this.baked.clear();
    }
}
