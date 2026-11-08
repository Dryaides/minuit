package dev.dryaides.minuit.port.mc;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.internal.GuiPart;
import dev.dryaides.minuit.internal.NineSlice;
import dev.dryaides.minuit.internal.PartTable;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;

/**
 * Atlas sprites on 1.19.2 and 1.18.2, which have no GUI atlas (it came with 1.20.2): the vanilla parts come
 * from the textures the game binds itself ({@link PartTable#LEGACY_WIDGETS}), cut in nine, so resource packs still
 * restyle them. Any other atlas goes through its {@code Material}. Client only, not API.
 */
public final class GameSprites {

    private GameSprites() {
    }

    /** The shader colour and blending are the caller's. */
    public static void atlas(PoseStack pose, ResourceLocation atlas, ResourceLocation id, int x, int y, int w, int h) {
        RenderSystem.setShaderTexture(0, atlas);
        GuiComponent.blit(pose, x, y, 0, w, h, new Material(atlas, id).sprite());
    }

    /** The game's art for a vanilla part, false when these versions draw that part with fills, not a texture. */
    public static boolean part(GuiDraw d, GuiPart part, int x, int y, int w, int h, float alpha) {
        return NineSlice.draw(d, PartTable.LEGACY_WIDGETS.resolve(part), x, y, w, h, alpha);
    }
}
