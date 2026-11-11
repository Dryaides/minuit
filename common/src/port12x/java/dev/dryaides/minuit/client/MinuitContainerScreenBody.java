package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ItemDropTarget;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.Incubating;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.SlotLayout;
import dev.dryaides.minuit.SlotPlacement;
import dev.dryaides.minuit.internal.ContainerLogic;
import dev.dryaides.minuit.port.mc.GameInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/** The body {@link MinuitContainerScreen} shares between 1.21.1 and 1.20.1; each version adds only what differs. */
abstract class MinuitContainerScreenBody<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {

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

        @Override
        public SlotLayout.Cell hoveredCell() {
            Slot slot = hoveredSlot;
            return slot == null ? null : new SlotLayout.Cell(slot.index,
                    leftPos + slot.x + Math.round(slotOffsetX(slot)), topPos + slot.y + Math.round(slotOffsetY(slot)));
        }
    });

    /** The vanilla screen of the same menu this one replaced; null when the kit screen is the menu's own. */
    private final Screen vanilla;
    private boolean handingBack;

    MinuitContainerScreenBody(M menu, Inventory inventory, Component title, Screen vanilla) {
        super(menu, inventory, title);
        this.vanilla = vanilla;
    }

    /** False keeps the world in view behind the panel: no dim, no blur. */
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

    /**
     * Whether the screen draws this menu slot and lets the pointer reach it, read on every frame and
     * every pointer event. A hidden slot is drawn, hovered and clicked as an inactive one: no item, no
     * hover, no tooltip, no click, drag or number key. The menu's own rules still reach it: a shift-click
     * or a double-click gather elsewhere can move items in or out, as the server decides.
     * Return {@link #placedByPanel} to hide every slot no component places.
     */
    public boolean showsSlot(Slot slot) {
        return true;
    }

    /**
     * True when a slot part of the panel ({@code SlotComponent}, {@code SlotGridComponent},
     * {@code PlayerInventoryComponent}, or your own through {@code menuWells}) draws this menu slot's well at
     * the menu's place. Return it from {@link #showsSlot} to hide every slot the panel leaves out: vanilla's
     * armour, offhand and crafting slots under a kit that only lays out the inventory. Reads the placement of
     * the last frame; a slot moved for good with {@link #slotOffsetX} counts as not placed.
     */
    public final boolean placedByPanel(Slot slot) {
        return this.logic.placement().placed(slot.index);
    }

    /** The panel's slot placement this frame; see {@link SlotPlacement}. */
    @Incubating
    public final SlotPlacement placement() {
        return this.logic.placement();
    }

    /**
     * True moves the inventory title onto the panel's {@code PlayerInventoryComponent}, where its own label
     * would sit, on every init. False (the default) leaves inventoryLabelX/Y as the game and your constructor set them.
     */
    @Incubating
    protected boolean labelsFollowPanel() {
        return false;
    }

    /** False hides the carried stack the game draws at the pointer, read on every frame. */
    public boolean drawsCarried() {
        return true;
    }

    /**
     * How far from its place the game draws this menu slot's item now, read on every frame and every
     * pointer lookup, so the pointer finds the slot where it is drawn. Give the slots of a sliding
     * region the offset of its {@code Glide} ({@code glide.offsetY(distance)}), the same glide as the
     * wells' widget.
     */
    public float slotOffsetX(Slot slot) {
        return 0F;
    }

    /** The y part of {@link #slotOffsetX}. */
    public float slotOffsetY(Slot slot) {
        return 0F;
    }

    /** The container's left edge on screen, read on every {@code init}; vanilla centres it. */
    protected int containerLeft() {
        return (this.width - this.imageWidth) / 2;
    }

    /** The container's top edge on screen, read on every {@code init}; vanilla centres it. */
    protected int containerTop() {
        return (this.height - this.imageHeight) / 2;
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
        this.leftPos = containerLeft();
        this.topPos = containerTop();
        this.host.init();
        this.panel = build(this.leftPos, this.topPos);
        List<SlotLayout.Cell> cells = new ArrayList<>();
        for (Slot slot : this.menu.slots) {
            cells.add(new SlotLayout.Cell(slot.index, this.leftPos + slot.x, this.topPos + slot.y));
        }
        this.logic.init(this.panel, cells, i -> showsSlot(this.menu.slots.get(i)), getClass().getName(),
                new Box("title", this.leftPos + this.titleLabelX, this.topPos + this.titleLabelY,
                        this.font.width(this.title), this.font.lineHeight));
        Box label = labelsFollowPanel() ? this.logic.inventoryLabel(this.panel) : null;
        if (label != null) {
            this.inventoryLabelX = label.x() - this.leftPos;
            this.inventoryLabelY = label.y() - this.topPos;
        }
    }

    /** The wells go here, from the background pass, under the items the game draws next. */
    @Override
    protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        if (this.panel != null) {
            this.logic.drawBase(new GuiDraw(graphics), this.panel, mouseX, mouseY);
        }
    }

    /** The game's slot tooltip, then the panel's tooltips, last. */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial);
        this.renderTooltip(graphics, mouseX, mouseY);
        this.logic.drawTop(new GuiDraw(graphics), this.panel, mouseX, mouseY);
    }

    /**
     * Vanilla's labels in the theme's text colour (vanilla hard-codes a dark grey), then the panel's
     * overlay: the game calls this after the slot items and before the carried item, with the pose at
     * the container's corner, so the overlay covers the items and the carried item still covers it.
     */
    @Override
    protected final void renderLabels(GuiGraphics graphics, int xm, int ym) {
        if (drawsLabels()) {
            int color = this.logic.labelColor(this.panel);
            graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, color, false);
            graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, color, false);
        }
        if (this.panel != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(-this.leftPos, -this.topPos, 0F);
            this.logic.drawOverlay(new GuiDraw(graphics), this.panel, xm, ym);
            graphics.pose().popPose();
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
        return this.logic.mouseReleased(this.panel, mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        return this.logic.clickedOutside(this.panel, mouseX, mouseY, super.hasClickedOutside(mouseX, mouseY, left, top, button));
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
    public void onClose() {
        if (!this.logic.closeOverlay(this.panel)) {
            super.onClose();
        }
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
}
