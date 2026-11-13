package dev.dryaides.minuit.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * A container screen whose look is a kit panel. The game keeps every slot rule (clicks, shift-click,
 * drag-split, number keys, Q, the carried item) through the menu and the server; the panel draws the
 * wells ({@code SlotGridComponent}, {@code PlayerInventoryComponent} on the menu's {@code SlotLayout}),
 * the labels and the hover in the theme, and its other widgets work as on a {@link MinuitScreen}.
 *
 * <p>Over a crafting or furnace menu the kit gives up the recipe book: the screen listens to no recipe
 * update and draws no recipe book button; the vanilla screen behind {@code toVanilla()} keeps it. A recipe
 * viewer (JEI, REI, EMI) still fills the craft slots through the menu, hidden or not, and draws its slot
 * highlights at the slot's vanilla place, not at {@code slotOffsetX/Y} (not tested). Its plugin reads
 * {@code itemUnderMouse} for its R and U keys, {@code extraAreas} for its exclusion zones and
 * {@code dropTargets} for items dragged from its list; minuit ships no such plugin.
 *
 * <p>Client only.
 */
public abstract class MinuitContainerScreen<M extends AbstractContainerMenu> extends MinuitContainerScreenBody<M> {

    protected MinuitContainerScreen(M menu, Inventory inventory, Component title, int imageWidth, int imageHeight) {
        this(menu, inventory, title, imageWidth, imageHeight, null);
    }

    /** Over an open menu: {@code vanilla} is the screen {@link MinuitMenuScreens#over} passes the factory. */
    protected MinuitContainerScreen(M menu, Inventory inventory, Component title, int imageWidth, int imageHeight, Screen vanilla) {
        super(menu, inventory, title, vanilla);
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        // 1.20.1's container render draws no dim background: each screen asks for it, as 1.21.1 does by itself.
        if (drawsBackground()) {
            this.renderBackground(graphics);
        }
        super.render(graphics, mouseX, mouseY, partial);
    }

    /**
     * Forge's colour of the white slot hover, which a light theme loses: the slot components draw the
     * theme's hover instead. Forge draws the highlight through its own overload, which the container mixin
     * does not reach; vanilla has no such method, so on Fabric this overrides nothing and the mixin drops it.
     */
    public int getSlotColor(int index) {
        return 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scroll) {
        return this.logic.mouseScrolled(this.panel, mouseX, mouseY, scroll)
                || super.mouseScrolled(mouseX, mouseY, scroll);
    }
}
