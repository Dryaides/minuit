package dev.dryaides.minuit.client;

import dev.dryaides.minuit.internal.ContainerLogic;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * A container screen whose look is a kit panel. The game keeps every slot rule (clicks, shift-click,
 * drag-split, number keys, Q, the carried item) through the menu and the server; the panel draws the
 * wells ({@code SlotGridComponent}, {@code PlayerInventoryComponent} on the menu's {@code SlotLayout}),
 * the labels and the hover in the theme, and its other widgets work as on a {@link MinuitScreen}.
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
    }

    /**
     * NeoForge's hook for the white slot hover, which a light theme loses: the slot components draw the
     * theme's hover instead. Vanilla has no such method, so on Fabric this overrides nothing and the
     * container mixin drops the static highlight there.
     */
    protected void renderSlotHighlight(GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partial) {
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        if (drawsBackground()) {
            super.renderBackground(graphics, mouseX, mouseY, partial);
        } else {
            // 1.21.1 draws the panel and wells from renderBg, which only the super call reaches.
            renderBg(graphics, partial, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double wheel = ContainerLogic.wheel(scrollX, scrollY, hasShiftDown());
        return this.logic.mouseScrolled(this.panel, mouseX, mouseY, wheel)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
