package dev.dryaides.minuit.port.mc;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.internal.GuiPart;
import dev.dryaides.minuit.internal.NineSlice;
import dev.dryaides.minuit.internal.PartTable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;

/**
 * Atlas sprites on 1.21.1: the GUI atlas through {@code blitSprite} (its .mcmeta nine-slices, so packs
 * restyle it), any other atlas through its {@code Material}. 1.20.1 has no GUI atlas. Client only, not API.
 */
public final class GameSprites {

    private GameSprites() {
    }

    /** {@code blitSprite} has no colour argument: alpha goes through the shader colour, with blending on. */
    public static void gui(GuiGraphics g, ResourceLocation id, int x, int y, int w, int h, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1F, 1F, 1F, alpha);
        g.blitSprite(id, x, y, w, h);
        g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    public static void atlas(GuiGraphics g, ResourceLocation atlas, ResourceLocation id, int x, int y, int w, int h,
                             float alpha) {
        g.blit(x, y, 0, w, h, new Material(atlas, id).sprite(), 1F, 1F, 1F, alpha);
    }

    /** Marker files are in the 1.21.1 jar: nothing to stand in for. */
    public static boolean markerFile(GuiGraphics g, String texture, int x, int y, int w, int h, float alpha) {
        return false;
    }

    /**
     * The game's art for a vanilla part, false when 1.21.1 has none: the GUI atlas of 1.21.1 lacks the
     * tooltip sprites (they came with 1.21.2), every other {@link PartTable#ATLAS} id is in the 1.21.1 jar.
     */
    public static boolean part(GuiGraphics g, GuiPart part, int x, int y, int w, int h, float alpha) {
        if (part == GuiPart.TOOLTIP_BACKGROUND || part == GuiPart.TOOLTIP_FRAME) {
            return false;
        }
        PartTable.Art art = PartTable.ATLAS.resolve(part);
        if (!(art instanceof PartTable.Sprite sprite)) {
            return NineSlice.draw(new GuiDraw(g), art, x, y, w, h, alpha);
        }
        ResourceLocation id = GameIds.id(sprite.id());
        if (id == null) {
            return false;
        }
        gui(g, id, x, y, w, h, alpha);
        return true;
    }
}
