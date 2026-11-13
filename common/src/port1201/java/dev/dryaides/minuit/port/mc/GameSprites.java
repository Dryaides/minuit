package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.internal.GuiPart;
import dev.dryaides.minuit.internal.NineSlice;
import dev.dryaides.minuit.internal.PartTable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Atlas sprites on 1.20.1, which has no GUI atlas (it came with 1.20.2): the vanilla parts come from
 * the textures the game binds itself ({@link PartTable#WIDGETS}), cut in nine, so resource packs still
 * restyle them. Map markers have neither their atlas nor their own files (both came with 1.20.5): they
 * come from the one sheet {@code map_icons.png}. Any other atlas goes through {@code ModelManager.getAtlas}.
 * Client only, not API.
 */
public final class GameSprites {

    private static final String MAP_ICONS = "minecraft:textures/map/map_icons.png";
    private static final String MARKER_FILES = "minecraft:textures/map/decorations/";

    /** The 1.20.5 marker names in the order of the 8 by 8 cells of {@link #MAP_ICONS}, 16 to a row. */
    private static final List<String> MARKERS = List.of("player", "frame", "red_marker", "blue_marker",
            "target_x", "target_point", "player_off_map", "player_off_limits", "woodland_mansion",
            "ocean_monument", "white_banner", "orange_banner", "magenta_banner", "light_blue_banner",
            "yellow_banner", "lime_banner", "pink_banner", "gray_banner", "light_gray_banner", "cyan_banner",
            "purple_banner", "blue_banner", "brown_banner", "green_banner", "red_banner", "black_banner",
            "red_x");

    private GameSprites() {
    }

    /** No GUI atlas here: a GUI sprite id names nothing, so nothing is drawn. */
    public static void gui(GuiGraphics g, ResourceLocation id, int x, int y, int w, int h, float alpha) {
    }

    public static void atlas(GuiGraphics g, ResourceLocation atlas, ResourceLocation id, int x, int y, int w, int h,
                             float alpha) {
        if (atlas.getNamespace().equals("minecraft") && atlas.getPath().equals("map_decorations")) {
            marker(g, id.getPath(), x, y, w, h, alpha);
            return;
        }
        TextureAtlas found = atlasOf(atlas);
        if (found == null) {
            return;
        }
        g.blit(x, y, 0, w, h, found.getSprite(id), 1F, 1F, 1F, alpha);
    }

    /**
     * A marker's own file ({@code textures/map/decorations/<name>.png}) drawn from the sheet instead;
     * false for any other texture. A marker the sheet lacks (the village and temple ones) draws nothing.
     */
    public static boolean markerFile(GuiGraphics g, String texture, int x, int y, int w, int h, float alpha) {
        if (!texture.startsWith(MARKER_FILES) || !texture.endsWith(".png")) {
            return false;
        }
        marker(g, texture.substring(MARKER_FILES.length(), texture.length() - 4), x, y, w, h, alpha);
        return true;
    }

    private static void marker(GuiGraphics g, String name, int x, int y, int w, int h, float alpha) {
        int i = MARKERS.indexOf(name);
        if (i >= 0) {
            new GuiDraw(g).blitRegion(MAP_ICONS, x, y, w, h, i % 16 * 8, i / 16 * 8, 8, 8, 128, 128, alpha);
        }
    }

    /** The game's art for a vanilla part, false when 1.20.1 draws that part with fills, not a texture. */
    public static boolean part(GuiGraphics g, GuiPart part, int x, int y, int w, int h, float alpha) {
        return NineSlice.draw(new GuiDraw(g), PartTable.WIDGETS.resolve(part), x, y, w, h, alpha);
    }

    /**
     * The atlas a sprite lives in, null when the game has none by that id. Before 26.1 items and blocks
     * share one atlas, under its texture path ({@code minecraft:items} names nothing there).
     */
    private static TextureAtlas atlasOf(ResourceLocation atlas) {
        boolean shared = atlas.getNamespace().equals("minecraft")
                && (atlas.getPath().equals("items") || atlas.getPath().equals("blocks"));
        return Minecraft.getInstance().getModelManager().getAtlas(shared ? TextureAtlas.LOCATION_BLOCKS : atlas);
    }
}
