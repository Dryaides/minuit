package dev.dryaides.minuit.port.mc;

import dev.dryaides.minuit.SpriteIcon;
import dev.dryaides.minuit.client.GuiDraw;
import dev.dryaides.minuit.internal.GuiPart;
import dev.dryaides.minuit.internal.NineSlice;
import dev.dryaides.minuit.internal.PartTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.List;

/**
 * Atlas sprites and vanilla parts on 1.12.2. One atlas holds items and blocks, under {@code items/} and
 * {@code blocks/}; there is no GUI atlas. The vanilla parts come from the textures the game binds itself
 * ({@link PartTable#LEGACY_WIDGETS}, the same layout in widgets.png and inventory.png), cut in nine, so
 * resource packs still restyle them; 1.12.2 has no checkbox.png, so checkboxes keep the pixel copy. Map
 * markers come from the 4 by 4 sheet {@code map_icons.png}. Client only, not API.
 */
public final class GameSprites {

    private static final String MAP_ICONS = "minecraft:textures/map/map_icons.png";
    private static final String MARKER_FILES = "minecraft:textures/map/decorations/";
    private static final String MAP_DECORATIONS = "minecraft:map_decorations";

    /** The 1.20.5 marker names in the order of 1.12.2's map icon cells, 4 to a row. */
    private static final List<String> MARKERS = Arrays.asList("player", "frame", "red_marker", "blue_marker",
            "target_x", "target_point", "player_off_map", "player_off_limits", "woodland_mansion",
            "ocean_monument");

    private GameSprites() {
    }

    /** The art 1.12.2 has for {@code part}; null when the pixel copy draws it. */
    public static PartTable.Art art(GuiPart part) {
        switch (part) {
            case CHECKBOX:
            case CHECKBOX_HIGHLIGHTED:
            case CHECKBOX_SELECTED:
            case CHECKBOX_SELECTED_HIGHLIGHTED:
                return null;
            default:
                return PartTable.LEGACY_WIDGETS.resolve(part);
        }
    }

    public static boolean part(GuiDraw d, GuiPart part, int x, int y, int w, int h, float alpha) {
        return NineSlice.draw(d, art(part), x, y, w, h, alpha);
    }

    /**
     * A sprite of the item or block atlas, stretched to {@code w} by {@code h}; the colour and blending are
     * the caller's. Modern folder names ({@code item/}, {@code block/}) read as 1.12.2's; the 1.13 file
     * renames are not mapped, a caller on 1.12.2 passes 1.12.2 names. Nothing for another atlas.
     */
    public static void atlas(String atlas, String sprite, int x, int y, int w, int h) {
        if (!SpriteIcon.ITEMS.equals(atlas) && !SpriteIcon.BLOCKS.equals(atlas)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        TextureAtlasSprite found = mc.getTextureMapBlocks().getAtlasSprite(legacyName(sprite));
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + h, 0D).tex(found.getMinU(), found.getMaxV()).endVertex();
        buffer.pos(x + w, y + h, 0D).tex(found.getMaxU(), found.getMaxV()).endVertex();
        buffer.pos(x + w, y, 0D).tex(found.getMaxU(), found.getMinV()).endVertex();
        buffer.pos(x, y, 0D).tex(found.getMinU(), found.getMinV()).endVertex();
        Tessellator.getInstance().draw();
    }

    /** {@code ns:item/x} to {@code ns:items/x}, {@code ns:block/x} to {@code ns:blocks/x}. */
    static String legacyName(String sprite) {
        int colon = sprite.indexOf(':');
        String ns = colon < 0 ? "minecraft" : sprite.substring(0, colon);
        String path = sprite.substring(colon + 1);
        if (path.startsWith("item/")) {
            path = "items/" + path.substring(5);
        } else if (path.startsWith("block/")) {
            path = "blocks/" + path.substring(6);
        }
        return ns + ":" + path;
    }

    /** A sprite of the 1.20.5 {@code map_decorations} atlas drawn from the sheet; false for any other atlas. */
    public static boolean atlasMarker(GuiDraw d, String atlas, String sprite, int x, int y, int w, int h,
                                      float alpha) {
        if (!MAP_DECORATIONS.equals(atlas)) {
            return false;
        }
        marker(d, sprite.substring(sprite.indexOf(':') + 1), x, y, w, h, alpha);
        return true;
    }

    /**
     * A marker's own file ({@code textures/map/decorations/<name>.png}) drawn from the sheet instead;
     * false for any other texture. A marker the sheet lacks (banners, villages) draws nothing.
     */
    public static boolean markerFile(GuiDraw d, String texture, int x, int y, int w, int h, float alpha) {
        if (!texture.startsWith(MARKER_FILES) || !texture.endsWith(".png")) {
            return false;
        }
        marker(d, texture.substring(MARKER_FILES.length(), texture.length() - 4), x, y, w, h, alpha);
        return true;
    }

    /** Cells as fractions of the sheet, as MapItemRenderer reads them, so a pack's larger sheet still fits. */
    private static void marker(GuiDraw d, String name, int x, int y, int w, int h, float alpha) {
        int i = MARKERS.indexOf(name);
        if (i >= 0) {
            d.blitRegion(MAP_ICONS, x, y, w, h, i % 4 * 8, i / 4 * 8, 8, 8, 32, 32, alpha);
        }
    }
}
