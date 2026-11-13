package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Internal;
import dev.dryaides.minuit.ItemTipLook;
import dev.dryaides.minuit.mixin.client.GuiGraphicsAccessor;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * The game's tooltip box in the player's theme on 1.20.1 and 1.21.1, for the hooks: the {@code TooltipRenderUtil}
 * mixin on Fabric (and for mods that draw the game's box themselves), the tooltip colour event on Forge and NeoForge.
 *
 * <p>Client only.
 */
@Internal
public final class GameTooltips {

    /** The depth the game draws its tooltip at, over the items of a screen. */
    public static final int DEPTH = 400;

    private GameTooltips() {
    }

    /**
     * Draws the box round tooltip lines that start at {@code x, y} and fill {@code w} by {@code h}, {@code z} deep,
     * in {@link ItemTipLook}, and starts the tooltip's themed text. False, drawing nothing, while the player's option
     * is off or {@code gameBox} is false (a mod or the item chose its own colours): the caller draws the game's box.
     */
    public static boolean frame(GuiGraphics g, int x, int y, int w, int h, int z, boolean gameBox) {
        ItemTipLook look = ItemTipLook.begin(gameBox && MinuitClient.themedItemTips());
        if (look == null) {
            return false;
        }
        // The game holds the box's fills for one flush while the theme's art blits at once: held, a frame's
        // fills would land over its own textures. Unheld, it draws as on any minuit screen.
        GuiGraphicsAccessor batch = (GuiGraphicsAccessor) g;
        boolean managed = batch.minuit$managed();
        g.flush();
        batch.minuit$managed(false);
        g.pose().pushPose();
        try {
            g.pose().translate(0F, 0F, z);
            look.frame(new GuiDraw(g), x, y, w, h);
        } finally {
            g.pose().popPose();
            batch.minuit$managed(managed);
        }
        return true;
    }

    /** The width of tooltip lines, as the game measures them for its box. */
    public static int width(List<ClientTooltipComponent> lines, Font font) {
        int w = 0;
        for (ClientTooltipComponent line : lines) {
            w = Math.max(w, line.getWidth(font));
        }
        return w;
    }

    /** The height of tooltip lines, as the game measures them for its box: a lone line drops its 2 pixels of gap. */
    public static int height(List<ClientTooltipComponent> lines) {
        int h = lines.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent line : lines) {
            h += line.getHeight();
        }
        return h;
    }
}
