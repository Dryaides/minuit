package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ItemDropTarget;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.Incubating;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.SlotLayout;
import dev.dryaides.minuit.SlotPlacement;
import dev.dryaides.minuit.internal.ContainerLogic;
import dev.dryaides.minuit.internal.port.Platform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A container screen whose look is a kit panel. The game keeps every slot rule (clicks, shift-click,
 * drag-split, number keys, Q, the carried item) through the menu and the server; the panel draws the
 * wells ({@code SlotGridComponent}, {@code PlayerInventoryComponent} on the menu's {@code SlotLayout}),
 * the labels and the hover in the theme, and its other widgets work as on a {@link MinuitScreen}.
 *
 * <p>Opened by {@link MinuitMenuScreens#over} in place of a menu's vanilla screen, it keeps that screen:
 * the inventory key switches to it on the same menu ({@link #toVanilla()}). {@link #drawsBackground()}
 * and {@link #closesOnMovement()} set how it sits over the world.
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
        this.host.viewers(this::showsContainerAddons);
    }
    private final ContainerLogic logic = new ContainerLogic(this.host, new ContainerLogic.MenuView() {
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
            return menu.getCarried().isEmpty() ? null : MinuitItems.view(menu.getCarried());
        }

        @Override
        public void click(int slot) {
            slotClicked(menu.slots.get(slot), slot, 0, ContainerInput.PICKUP);
        }

        @Override
        public boolean quickMove(int slot) {
            slotClicked(menu.slots.get(slot), slot, 0, ContainerInput.QUICK_MOVE);
            return true;
        }

        @Override
        public boolean takes(int slot) {
            Slot s = menu.slots.get(slot);
            ItemStack c = menu.getCarried();
            return !c.isEmpty() && s.mayPlace(c) && AbstractContainerMenu.canItemQuickReplace(s, c, false)
                    && s.getItem().getCount() + c.getCount() <= s.getMaxStackSize(c);
        }

        @Override
        public boolean mayPlace(int slot) {
            return menu.slots.get(slot).mayPlace(menu.getCarried());
        }

        @Override
        public void clearCarried() {
            menu.setCarried(ItemStack.EMPTY);
        }

        @Override
        public boolean playerSlot(int slot) {
            Slot s = menu.slots.get(slot);
            return s.container instanceof Inventory && s.getContainerSlot() < 36;
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
        super(menu, inventory, title, imageWidth, imageHeight);
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
        this.minecraft.setScreenAndShow(this.vanilla);
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

    /**
     * False keeps recipe viewers (JEI, REI) off this screen: their item list, search bar and buttons; read on
     * every frame. The vanilla screen behind {@code toVanilla()} keeps them.
     */
    protected boolean showsContainerAddons() {
        return true;
    }

    /** Called on every {@code init}, with the container's corner on screen; build the panel there. */
    protected abstract PanelComponent build(int leftPos, int topPos);

    private List<GuiEventListener> ownWidgets = new ArrayList<>();

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
        this.ownWidgets = new ArrayList<>(this.children());
    }

    /**
     * Buttons other mods add to every container screen after {@code init} (inventory sorters, viewer
     * toggles) leave with the viewers when {@link #showsContainerAddons} is false; the screen's own widgets stay.
     */
    private void sweepAddons() {
        if (showsContainerAddons() || this.children().size() == this.ownWidgets.size()) {
            return;
        }
        for (GuiEventListener child : new ArrayList<>(this.children())) {
            if (!this.ownWidgets.contains(child)) {
                this.removeWidget(child);
            }
        }
    }

    /** The dim of an in-game screen; off when the world stays in view. */
    @Override
    public void extractTransparentBackground(GuiGraphicsExtractor graphics) {
        if (drawsBackground()) {
            super.extractTransparentBackground(graphics);
        }
    }

    /** The wells go here, under the items the game draws a stratum up. */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractBackground(graphics, mouseX, mouseY, partial);
        if (this.panel != null) {
            this.logic.drawBase(new GuiDraw(graphics), this.panel, mouseX, mouseY);
        }
    }

    /** After the items, a stratum up, so an open dropdown covers them; the carried item still comes after it. */
    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractContents(graphics, mouseX, mouseY, partial);
        if (this.panel != null) {
            this.logic.drawOverlay(new GuiDraw(graphics), this.panel, mouseX, mouseY);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        sweepAddons();
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        this.logic.drawTop(new GuiDraw(graphics), this.panel, mouseX, mouseY);
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (!showsSlot(slot)) {
            return;
        }
        float ox = slotOffsetX(slot) + this.logic.placement().shiftX(slot.index);
        float oy = slotOffsetY(slot) + this.logic.placement().shiftY(slot.index);
        if (ox == 0F && oy == 0F) {
            super.extractSlot(graphics, slot, mouseX, mouseY);
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(ox, oy);
        super.extractSlot(graphics, slot, mouseX, mouseY);
        graphics.pose().popMatrix();
    }

    @Override
    public void extractCarriedItem(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (drawsCarried()) {
            super.extractCarriedItem(graphics, mouseX, mouseY);
        }
    }

    /** Vanilla's labels in the theme's text colour; vanilla hard-codes a dark grey. */
    @Override
    protected final void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        if (!drawsLabels()) {
            return;
        }
        int color = this.logic.labelColor(this.panel);
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, color, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, color, false);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return this.logic.mouseClicked(this.panel, event.x(), event.y(), event.button())
                || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return this.logic.mouseReleased(this.panel, event.x(), event.y(), event.button())
                || super.mouseReleased(event);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
        return this.logic.clickedOutside(this.panel, mx, my, super.hasClickedOutside(mx, my, xo, yo));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return this.logic.mouseDragged(this.panel, event.x(), event.y(), event.button(), dx, dy)
                || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double wheel = ContainerLogic.wheel(scrollX, scrollY, this.minecraft.hasShiftDown());
        return this.logic.mouseScrolled(this.panel, mouseX, mouseY, wheel)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.logic.keyPressed(this.panel, Platform.screens().stroke(event))
                || hotbarHoldKey() != null && hotbarHoldKey().matches(event) && this.logic.holdHotbar(this.panel, true)) {
            return true;
        }
        switch (MinuitMenuScreens.overKey(key -> key.matches(event), this.vanilla != null, closesOnMovement())) {
            case TO_VANILLA -> toVanilla();
            case CLOSE -> onClose();
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return this.logic.keyReleased(this.panel, Platform.screens().stroke(event))
                || hotbarHoldKey() != null && hotbarHoldKey().matches(event) && this.logic.holdHotbar(this.panel, false)
                || super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return this.logic.charTyped(this.panel, event.codepoint()) || super.charTyped(event);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
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
     * Keeps a shift-click and the offhand key off armour and offhand slots no panel part places: the game would
     * move the stack there, out of sight. The shift-click then goes through left clicks to the hotbar or rows.
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ContainerInput type) {
        int size = this.menu.slots.size();
        if (slot != null && type == ContainerInput.QUICK_MOVE) {
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
                        MinuitContainerScreen.super.slotClicked(menu.slots.get(s), s, 0, ContainerInput.PICKUP);
                    }
                });
                return;
            }
        }
        if (type == ContainerInput.SWAP && button == 40 && this.logic.blocksOffhandSwap(size, this::playerSlot)) {
            return;
        }
        super.slotClicked(slot, slotId, button, type);
    }

    private ItemStack carried() {
        return this.menu.getCarried();
    }

    private int playerSlot(int index) {
        Slot slot = this.menu.slots.get(index);
        return slot.container instanceof Inventory ? slot.getContainerSlot() : -1;
    }
}
