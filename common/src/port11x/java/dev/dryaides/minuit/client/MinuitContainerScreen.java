package dev.dryaides.minuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ItemDropTarget;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.internal.ContainerLogic;
import dev.dryaides.minuit.port.mc.GameInput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.List;

/**
 * A container screen whose look is a kit panel. The game keeps every slot rule (clicks, shift-click,
 * drag-split, number keys, Q, the carried item) through the menu and the server; the panel draws the
 * wells ({@code SlotGridComponent}, {@code PlayerInventoryComponent} on the menu's {@code SlotLayout}),
 * the labels and the hover in the theme, and its other widgets work as on a {@link MinuitScreen}.
 *
 * <p>Client only.
 */
public abstract class MinuitContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

    protected PanelComponent panel;
    protected final ScreenHost host = new ScreenHost(this);

    {
        this.host.themeTab(this::showsThemeTab);
    }
    final ContainerLogic logic = new ContainerLogic(this.host, new ContainerLogic.MenuView() {
        @Override
        public ItemView hoveredSlotItem() {
            return hoveredSlot != null && hoveredSlot.hasItem() ? MinuitItems.view(hoveredSlot.getItem()) : null;
        }

        @Override
        public Box container() {
            return containerBox();
        }
    });

    /** The vanilla screen of the same menu this one replaced; null when the kit screen is the menu's own. */
    private final Screen vanilla;
    private boolean handingBack;

    protected MinuitContainerScreen(M menu, Inventory inventory, Component title, int imageWidth, int imageHeight) {
        this(menu, inventory, title, imageWidth, imageHeight, null);
    }

    /** Over an open menu: {@code vanilla} is the screen {@link MinuitMenuScreens#over} passes the factory. */
    protected MinuitContainerScreen(M menu, Inventory inventory, Component title, int imageWidth, int imageHeight, Screen vanilla) {
        super(menu, inventory, title);
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.vanilla = vanilla;
    }

    /** False keeps the world in view behind the panel: no dim. */
    protected boolean drawsBackground() {
        return true;
    }

    /** False hides the title and the inventory title, which the game draws in the theme's text colour; the panel overlay stays. */
    protected boolean drawsLabels() {
        return true;
    }

    /** True closes the menu on a movement or jump key, as walking away does. */
    protected boolean closesOnMovement() {
        return false;
    }

    /** Shows the vanilla screen of the same menu; the menu stays open and the server sees nothing. */
    public void toVanilla() {
        if (this.vanilla == null || this.minecraft == null) {
            return;
        }
        this.handingBack = true;
        MinuitMenuScreens.handBack(this.menu);
        this.minecraft.setScreen(this.vanilla);
    }

    /** False hides the theme tab beside the panel; read on every frame and click. */
    protected boolean showsThemeTab() {
        return true;
    }

    /** Called on every {@code init}, with the container's corner on screen; build the panel there. */
    protected abstract PanelComponent build(int leftPos, int topPos);

    @Override
    protected void init() {
        super.init();
        this.host.init();
        this.panel = build(this.leftPos, this.topPos);
    }

    /** The wells go here, from the background pass, under the items the game draws next. */
    @Override
    protected void renderBg(PoseStack pose, float partial, int mouseX, int mouseY) {
        if (this.panel != null) {
            this.logic.drawBase(new GuiDraw(pose), this.panel, mouseX, mouseY);
        }
    }

    /** The game's slot tooltip, then the panel's tooltips, last. */
    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partial) {
        // The container render draws no dim background here: each screen asks for it.
        if (drawsBackground()) {
            this.renderBackground(pose);
        }
        super.render(pose, mouseX, mouseY, partial);
        this.renderTooltip(pose, mouseX, mouseY);
        this.logic.drawTop(new GuiDraw(pose), this.panel, mouseX, mouseY);
    }

    /**
     * Vanilla's labels in the theme's text colour (vanilla hard-codes a dark grey), then the panel's
     * overlay: the game calls this after the slot items and before the carried item, with the model-view
     * stack at the container's corner, so the overlay covers the items ({@link GuiDraw} on the depth).
     */
    @Override
    protected final void renderLabels(PoseStack pose, int xm, int ym) {
        if (drawsLabels()) {
            int color = this.logic.labelColor(this.panel);
            this.font.draw(pose, this.title, this.titleLabelX, this.titleLabelY, color);
            this.font.draw(pose, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, color);
        }
        if (this.panel != null) {
            pose.pushPose();
            pose.translate(-this.leftPos, -this.topPos, 0F);
            this.logic.drawOverlay(new GuiDraw(pose), this.panel, xm, ym);
            pose.popPose();
        }
    }

    /** The vanilla container box, screen coordinates. */
    protected Box containerBox() {
        return new Box("container", this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
    }

    /**
     * The item under the mouse: a display slot or item icon of the panel, else the menu slot's stack;
     * null over nothing. What a recipe viewer plugin reads for its R and U keys and bookmarks.
     */
    public ItemView itemUnderMouse() {
        return this.logic.itemUnderMouse(this.panel);
    }

    /** The panel's rectangles outside the container box, for a recipe viewer's exclusion zones. */
    public List<Box> extraAreas() {
        return this.logic.extraAreas(this.panel);
    }

    /** Where the panel takes an item dropped from a recipe viewer, screen coordinates. */
    public List<ItemDropTarget.Drop> dropTargets() {
        return this.logic.dropTargets(this.panel);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return this.logic.mouseClicked(this.panel, mouseX, mouseY, button)
                || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.logic.mouseReleased(this.panel, mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return this.logic.mouseDragged(this.panel, mouseX, mouseY, button, dx, dy)
                || super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        if (this.logic.keyPressed(this.panel, GameInput.stroke(key, scancode))) {
            return true;
        }
        switch (MinuitMenuScreens.overKey(mapping -> mapping.matches(key, scancode), this.vanilla != null, closesOnMovement())) {
            case TO_VANILLA -> toVanilla();
            case CLOSE -> onClose();
            default -> {
                return super.keyPressed(key, scancode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char typed, int modifiers) {
        return this.logic.charTyped(this.panel, typed) || super.charTyped(typed, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.logic.tick(this.panel);
    }

    @Override
    public void removed() {
        boolean back = this.handingBack;
        this.handingBack = false;
        // Handing back to the vanilla screen keeps the menu, so the menu's own removed waits for that screen.
        if (this.logic.removed(this.panel) && !back) {
            super.removed();
        }
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
