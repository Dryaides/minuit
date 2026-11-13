package dev.dryaides.minuit.port.mc;

import com.mojang.blaze3d.platform.NativeImage;
import dev.dryaides.minuit.AsyncImage;
import dev.dryaides.minuit.internal.ImageSource;
import dev.dryaides.minuit.internal.port.TextureBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/** PNG bytes to a game texture, for {@link dev.dryaides.minuit.client.AsyncImages}. Client only. */
public final class GameTextures implements TextureBridge, ImageSource {

    private final Set<ResourceLocation> baked = new HashSet<>();

    /** Uploads on the render thread. */
    @Override
    public void upload(AsyncImage target, String key, byte[] png) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try (NativeImage read = NativeImage.read(new ByteArrayInputStream(png))) {
                // The texture closes what it holds, so it gets a copy the read buffer does not share.
                NativeImage owned = new NativeImage(read.getWidth(), read.getHeight(), false);
                owned.copyFrom(read);
                ResourceLocation id = GameIds.of("minuit", "dynamic/" + key);
                mc.getTextureManager().release(id);
                mc.getTextureManager().register(id, new DynamicTexture(owned));
                target.ready(id.toString(), owned.getWidth(), owned.getHeight());
            } catch (Exception e) {
                target.failed();
            }
        });
    }

    @Override
    public void release(String texture) {
        ResourceLocation id = GameIds.id(texture);
        if (id != null) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.getTextureManager().release(id));
        }
    }

    /** A PNG of the loaded resource packs, on the render thread. */
    @Override
    public Pixels read(String id) {
        ResourceLocation rl = GameIds.id(id);
        if (rl == null) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        try (InputStream in = mc.getResourceManager().getResource(rl).getInputStream()) {
            try (NativeImage img = NativeImage.read(NativeImage.Format.RGBA, in)) {
                int[] argb = new int[img.getWidth() * img.getHeight()];
                for (int y = 0; y < img.getHeight(); y++) {
                    for (int x = 0; x < img.getWidth(); x++) {
                        argb[y * img.getWidth() + x] = abgr(img.getPixelRGBA(x, y));
                    }
                }
                return new Pixels(img.getWidth(), img.getHeight(), argb);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Called while drawing, so on the render thread: the texture exists before the blit that follows. */
    @Override
    public String bake(String key, Pixels image) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation id = GameIds.of("minuit", "baked/" + key);
        mc.getTextureManager().release(id);
        NativeImage img = new NativeImage(image.w(), image.h(), false);
        for (int y = 0; y < image.h(); y++) {
            for (int x = 0; x < image.w(); x++) {
                img.setPixelRGBA(x, y, abgr(image.argb()[y * image.w() + x]));
            }
        }
        mc.getTextureManager().register(id, new DynamicTexture(img));
        this.baked.add(id);
        return id.toString();
    }

    /** On the render thread, while drawing. */
    @Override
    public void drop(String texture) {
        ResourceLocation id = GameIds.id(texture);
        if (id != null && this.baked.remove(id)) {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    /** Runs in the reload listener, on the render thread, after the fresh families replaced the ones these served. */
    @Override
    public void reload() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation id : this.baked) {
            mc.getTextureManager().release(id);
        }
        this.baked.clear();
    }

    /** ARGB to the ABGR NativeImage keeps, and back: the swap is its own inverse. */
    private static int abgr(int c) {
        return (c & 0xFF00FF00) | (c >> 16 & 0xFF) | (c & 0xFF) << 16;
    }
}
