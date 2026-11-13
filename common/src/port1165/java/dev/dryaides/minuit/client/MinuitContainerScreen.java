package dev.dryaides.minuit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ItemDropTarget;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.Incubating;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.SlotLayout;
import dev.dryaides.minuit.SlotPlacement;
import dev.dryaides.minuit.internal.ContainerLogic;
import dev.dryaides.minuit.port.mc.GameInput;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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

        @Override
        public SlotLayout.Cell hoveredCell() {
            Slot slot = hoveredSlot;
            return slot == null ? null : new SlotLayout.Cell(slot.index,
                    leftPos + slot.x + Math.round(slotOffsetX(slot)), topPos + slot.y + Math.round(slotOffsetY(slot)));
        }

        @Override
        public ItemView carried() {
            return minecraft.player.inventory.getCarried().isEmpty() ? null : MinuitItems.view(minecraft.player.inventory.getCarried());
        }

        @Override
        public void click(int slot) {
            slotClicked(menu.slots.get(slot), slot, 0, ClickType.PICKUP);
        }

        @Override
        public boolean quickMove(int slot) {
            slotClicked(menu.slots.get(slot), slot, 0, ClickType.QUICK_MOVE);
            return true;
        }

        @Override
        public boolean takes(int slot) {
            Slot s = menu.slots.get(slot);
            ItemStack c = minecraft.player.inventory.getCarried();
            return !c.isEmpty() && s.mayPlace(c) && AbstractContainerMenu.canItemQuickReplace(s, c, false)
                    && s.getItem().getCount() + c.getCount() <= s.getMaxStackSize(c);
        }

        @Override
        public boolean mayPlace(int slot) {
            return menu.slots.get(slot).mayPlace(minecraft.player.inventory.getCarried());
        }

        @Override
        public void clearCarried() {
            minecraft.player.inventory.setCarried(ItemStack.EMPTY);
        }

        @Override
        public boolean playerSlot(int slot) {
            Slot s = menu.slots.get(slot);
            // Armour and offhand slots are the inventory slots with an empty-slot icon (1.16 Slot hides its index).
            return s.container instanceof Inventory && s.getNoItemIcon() == null;
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
        this.inventoryLabelY = imageHeight - 94;
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

    /** The mod's own key that keeps the panel's expandable hotbar unfolded while held; null for none. */
    protected KeyMapping hotbarHoldKey() {
        return null;
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
     * Whether the screen draws this menu slot, read on every frame. The default shows the slots a slot part of
     * the panel places ({@link #placedByPanel}), and every slot on a screen without a panel. A hidden slot is
     * not drawn, item and ghost included. The pointer never finds a slot no part places, shown or not: no hover,
     * tooltip, click, drag or number key reaches it. The menu's own rules still reach a hidden slot: a
     * shift-click or a double-click gather elsewhere can move items in or out, as the server decides.
     */
    public boolean showsSlot(Slot slot) {
        return this.logic.placement().finds(slot.index);
    }

    /**
     * True when a slot part of the panel ({@code SlotComponent}, {@code SlotGridComponent},
     * {@code PlayerInventoryComponent}, or your own through {@code menuWells}) draws this menu slot's well at
     * the menu's place. {@link #showsSlot} hides by default every slot the panel leaves out: vanilla's
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
     * overlay: the game calls this after the slot items and before the carried item, with the GL matrix
     * at the container's corner, so the overlay covers the items ({@link GuiDraw} on the depth).
     */
    @Override
    protected final void renderLabels(PoseStack pose, int xm, int ym) {
        if (drawsLabels()) {
            int color = this.logic.labelColor(this.panel);
            this.font.draw(pose, this.title, this.titleLabelX, this.titleLabelY, color);
            this.font.draw(pose, this.inventory.getDisplayName(), this.inventoryLabelX, this.inventoryLabelY, color);
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
        if (this.logic.keyPressed(this.panel, GameInput.stroke(key, scancode))
                || hotbarHoldKey() != null && hotbarHoldKey().matches(key, scancode) && this.logic.holdHotbar(this.panel, true)) {
            return true;
        }
        switch (MinuitMenuScreens.overKey(mapping -> mapping.matches(key, scancode), this.vanilla != null, closesOnMovement())) {
            case TO_VANILLA:
                toVanilla();
                return true;
            case CLOSE:
                onClose();
                return true;
            default:
                return super.keyPressed(key, scancode, modifiers);
        }
    }

    @Override
    public boolean keyReleased(int key, int scancode, int modifiers) {
        return this.logic.keyReleased(this.panel, GameInput.stroke(key, scancode))
                || hotbarHoldKey() != null && hotbarHoldKey().matches(key, scancode) && this.logic.holdHotbar(this.panel, false)
                || super.keyReleased(key, scancode, modifiers);
    }

    @Override
    public boolean charTyped(char typed, int modifiers) {
        return this.logic.charTyped(this.panel, typed) || super.charTyped(typed, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        this.logic.tick(this.panel);
        if (GamepadPoll.tick(this.host, this.panel)) {
            onClose();
        }
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

    /**
     * Forge's colour of the white slot hover, which a light theme loses: the slot components draw the
     * theme's hover instead. Forge's patched render reads it; vanilla has no such method, so on Fabric this
     * overrides nothing and the container mixin drops the fill.
     */
    public int getSlotColor(int index) {
        return 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scroll) {
        return this.logic.mouseScrolled(this.panel, mouseX, mouseY, scroll)
                || super.mouseScrolled(mouseX, mouseY, scroll);
    }

    /**
     * Keeps a shift-click and the offhand key off armour and offhand slots no panel part places: the game would
     * move the stack there, out of sight. The shift-click then goes through left clicks to the hotbar or rows.
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ClickType type) {
        int size = this.menu.slots.size();
        if (slot != null && type == ClickType.QUICK_MOVE) {
            List<Integer> to = this.logic.quickTargets(slotId, size, this::playerSlot);
            if (to != null) {
                ContainerLogic.route(slotId, to, new ContainerLogic.QuickMove() {
                    public boolean carrying() {
                        return !carried().isEmpty();
                    }

                    public boolean empty(int s) {
                        Slot t = menu.slots.get(s);
                        return !t.hasItem() && t.mayPlace(carried());
                    }

                    public boolean merges(int s) {
                        Slot t = menu.slots.get(s);
                        return t.hasItem() && AbstractContainerMenu.canItemQuickReplace(t, carried(), true)
                                && t.getItem().getCount() < t.getMaxStackSize(carried());
                    }

                    public void click(int s) {
                        MinuitContainerScreen.super.slotClicked(menu.slots.get(s), s, 0, ClickType.PICKUP);
                    }
                });
                return;
            }
        }
        if (type == ClickType.SWAP && button == 40 && this.logic.blocksOffhandSwap(size, this::playerSlot)) {
            return;
        }
        super.slotClicked(slot, slotId, button, type);
    }

    private ItemStack carried() {
        return this.minecraft.player.inventory.getCarried();
    }

    private int playerSlot(int index) {
        return this.menu instanceof InventoryMenu ? ContainerLogic.playerMenuSlot(index) : -1;
    }
}
