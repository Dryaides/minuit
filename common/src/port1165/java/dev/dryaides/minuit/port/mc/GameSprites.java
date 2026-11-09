package dev.dryaides.minuit.port.mc;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.internal.GuiPart;
import dev.dryaides.minuit.internal.NineSlice;
import dev.dryaides.minuit.internal.PartTable;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;

/**
 * Atlas sprites on 1.16.5, which has no GUI atlas (it came with 1.20.2): the vanilla parts come
 * from the textures the game binds itself ({@link PartTable#LEGACY_WIDGETS}), cut in nine, so resource packs still
 * restyle them. Map markers have neither their atlas nor their own files (both came with 1.20.5): they
 * come from the one sheet {@code map_icons.png}. Any other atlas goes through its {@code Material}.
 * Client only, not API.
 */
public final class GameSprites {

    private static final String MAP_ICONS = "minecraft:textures/map/map_icons.png";
    private static final String MARKER_FILES = "minecraft:textures/map/decorations/";

    /** The 1.20.5 marker names in the order of the 8 by 8 cells of {@link #MAP_ICONS}, 16 to a row. */
    private static final List<String> MARKERS = Arrays.asList("player", "frame", "red_marker", "blue_marker",
            "target_x", "target_point", "player_off_map", "player_off_limits", "woodland_mansion",
            "ocean_monument", "white_banner", "orange_banner", "magenta_banner", "light_blue_banner",
            "yellow_banner", "lime_banner", "pink_banner", "gray_banner", "light_gray_banner", "cyan_banner",
            "purple_banner", "blue_banner", "brown_banner", "green_banner", "red_banner", "black_banner",
            "red_x");

    private GameSprites() {
    }

    /** The shader colour and blending are the caller's. */
    public static void atlas(PoseStack pose, ResourceLocation atlas, ResourceLocation id, int x, int y, int w, int h) {
        Minecraft.getInstance().getTextureManager().bind(atlas);
        GuiComponent.blit(pose, x, y, 0, w, h, new Material(atlas, id).sprite());
    }

    /** The game's art for a vanilla part, false when these versions draw that part with fills, not a texture. */
    public static boolean part(GuiDraw d, GuiPart part, int x, int y, int w, int h, float alpha) {
        return NineSlice.draw(d, PartTable.LEGACY_WIDGETS.resolve(part), x, y, w, h, alpha);
    }

    /** A sprite of the 1.20.5 {@code map_decorations} atlas drawn from the sheet; false for any other atlas. */
    public static boolean atlasMarker(GuiDraw d, ResourceLocation atlas, ResourceLocation id, int x, int y, int w, int h,
                                      float alpha) {
        if (!atlas.getNamespace().equals("minecraft") || !atlas.getPath().equals("map_decorations")) {
            return false;
        }
        marker(d, id.getPath(), x, y, w, h, alpha);
        return true;
    }

    /**
     * A marker's own file ({@code textures/map/decorations/<name>.png}) drawn from the sheet instead;
     * false for any other texture. A marker the sheet lacks (the village and temple ones) draws nothing.
     */
    public static boolean markerFile(GuiDraw d, String texture, int x, int y, int w, int h, float alpha) {
        if (!texture.startsWith(MARKER_FILES) || !texture.endsWith(".png")) {
            return false;
        }
        marker(d, texture.substring(MARKER_FILES.length(), texture.length() - 4), x, y, w, h, alpha);
        return true;
    }

    private static void marker(GuiDraw d, String name, int x, int y, int w, int h, float alpha) {
        int i = MARKERS.indexOf(name);
        if (i >= 0) {
            d.blitRegion(MAP_ICONS, x, y, w, h, i % 16 * 8, i / 16 * 8, 8, 8, 128, 128, alpha);
        }
    }
}
