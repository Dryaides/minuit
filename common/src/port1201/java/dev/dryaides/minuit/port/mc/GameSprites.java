package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.internal.GuiPart;
import dev.dryaides.minuit.internal.NineSlice;
import dev.dryaides.minuit.internal.PartTable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;

/**
 * Atlas sprites on 1.20.1, which has no GUI atlas (it came with 1.20.2): the vanilla parts come from
 * the textures the game binds itself ({@link PartTable#WIDGETS}), cut in nine, so resource packs still
 * restyle them. Any other atlas goes through its {@code Material}. Client only, not API.
 */
public final class GameSprites {

    private GameSprites() {
    }

    /** No GUI atlas here: a GUI sprite id names nothing, so nothing is drawn. */
    public static void gui(GuiGraphics g, ResourceLocation id, int x, int y, int w, int h, float alpha) {
    }

    public static void atlas(GuiGraphics g, ResourceLocation atlas, ResourceLocation id, int x, int y, int w, int h,
                             float alpha) {
        g.blit(x, y, 0, w, h, new Material(atlas, id).sprite(), 1F, 1F, 1F, alpha);
    }

    /** The game's art for a vanilla part, false when 1.20.1 draws that part with fills, not a texture. */
    public static boolean part(GuiGraphics g, GuiPart part, int x, int y, int w, int h, float alpha) {
        return NineSlice.draw(new GuiDraw(g), PartTable.WIDGETS.resolve(part), x, y, w, h, alpha);
    }
}
