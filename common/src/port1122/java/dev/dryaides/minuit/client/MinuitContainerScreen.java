package dev.dryaides.minuit.client;

import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.ItemDropTarget;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.Incubating;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.SlotLayout;
import dev.dryaides.minuit.SlotPlacement;
import dev.dryaides.minuit.SpriteIcon;
import dev.dryaides.minuit.internal.ContainerLogic;
import dev.dryaides.minuit.port.mc.GameInput;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.ClickType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A container screen whose look is a kit panel. The game keeps every slot rule (clicks, shift-click,
 * drag-split, number keys, Q, the carried item) through the container and the server; the panel draws the
 * wells ({@code SlotGridComponent}, {@code PlayerInventoryComponent} on the container's {@code SlotLayout}),
 * the labels and the hover in the theme, and its other widgets work as on a {@link MinuitScreen}.
 *
 * <p>Forge 1.12.2 has no mixin, so the slot hooks are overrides of GuiContainer: hidden slots answer no
 * to {@code isMouseOverSlot} and skip {@code drawSlot}, moved slots have their {@code xPos/yPos} shifted
 * while the game draws and reads the pointer, the white hover is the one 16x16 gradient of
 * {@code drawScreen}, the carried stack is lifted out of the player's inventory while the game draws, and
 * an empty placed slot skips {@code drawSlot}, and the theme draws its ghost from the slot's own icon.
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
public abstract class MinuitContainerScreen<M extends Container> extends GuiContainer {

    /** Vanilla's slot hover, the only 16x16 gradient GuiContainer draws in this colour. */
    private static final int VANILLA_HOVER = 0x80FFFFFF;

    protected final M menu;
    protected final InventoryPlayer inventory;
    /** The container title, already translated. */
    protected final String title;
    protected int titleLabelX = 8;
    protected int titleLabelY = 6;
    protected int inventoryLabelX = 8;
    protected int inventoryLabelY;

    protected PanelComponent panel;
    protected final ScreenHost host = new ScreenHost(this);

    {
        this.host.themeTab(this::showsThemeTab);
    }
    final ContainerLogic logic = new ContainerLogic(this.host, new ContainerLogic.MenuView() {
        @Override
        public ItemView hoveredSlotItem() {
            Slot hovered = getSlotUnderMouse();
            return hovered != null && hovered.getHasStack() ? MinuitItems.view(hovered.getStack()) : null;
        }

        @Override
        public Box container() {
            return containerBox();
        }

        @Override
        public SlotLayout.Cell hoveredCell() {
            Slot slot = getSlotUnderMouse();
            return slot == null ? null : new SlotLayout.Cell(slot.slotNumber,
                    guiLeft + slot.xPos + Math.round(slotOffsetX(slot)), guiTop + slot.yPos + Math.round(slotOffsetY(slot)));
        }

        @Override
        public ItemView carried() {
            ItemStack stack = mc.player.inventory.getItemStack();
            return stack.isEmpty() ? null : MinuitItems.view(stack);
        }

        @Override
        public void click(int slot) {
            handleMouseClick(inventorySlots.inventorySlots.get(slot), slot, 0, ClickType.PICKUP);
        }

        @Override
        public boolean quickMove(int slot) {
            handleMouseClick(inventorySlots.inventorySlots.get(slot), slot, 0, ClickType.QUICK_MOVE);
            return true;
        }

        @Override
        public boolean takes(int slot) {
            Slot s = inventorySlots.inventorySlots.get(slot);
            ItemStack c = mc.player.inventory.getItemStack();
            return !c.isEmpty() && s.isItemValid(c) && Container.canAddItemToSlot(s, c, false)
                    && s.getStack().getCount() + c.getCount() <= s.getItemStackLimit(c);
        }

        @Override
        public boolean mayPlace(int slot) {
            return inventorySlots.inventorySlots.get(slot).isItemValid(mc.player.inventory.getItemStack());
        }

        @Override
        public void clearCarried() {
            mc.player.inventory.setItemStack(ItemStack.EMPTY);
        }

        @Override
        public boolean playerSlot(int slot) {
            Slot s = inventorySlots.inventorySlots.get(slot);
            return s.inventory instanceof InventoryPlayer && s.getSlotIndex() < 36;
        }
    });

    /** The vanilla screen of the same container this one replaced; null when the kit screen is the container's own. */
    private final GuiScreen vanilla;
    private boolean handingBack;
    private int[] movedSlots = new int[0];
    private int lastX;
    private int lastY;

    protected MinuitContainerScreen(M menu, InventoryPlayer inventory, String title, int imageWidth, int imageHeight) {
        this(menu, inventory, title, imageWidth, imageHeight, null);
    }

    /** Over an open container: {@code vanilla} is the screen {@link MinuitMenuScreens#over} passes the factory. */
    protected MinuitContainerScreen(M menu, InventoryPlayer inventory, String title, int imageWidth, int imageHeight,
                                    GuiScreen vanilla) {
        super(menu);
        this.menu = menu;
        this.inventory = inventory;
        this.title = title;
        this.xSize = imageWidth;
        this.ySize = imageHeight;
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

    /** True closes the container on a movement or jump key, as walking away does. */
    protected boolean closesOnMovement() {
        return false;
    }

    /** The mod's own key that keeps the panel's expandable hotbar unfolded while held; null for none. */
    protected KeyBinding hotbarHoldKey() {
        return null;
    }

    /** Shows the vanilla screen of the same container; the container stays open and the server sees nothing. */
    public void toVanilla() {
        if (this.vanilla == null || this.mc == null) {
            return;
        }
        this.handingBack = true;
        MinuitMenuScreens.handBack(this.menu);
        this.mc.displayGuiScreen(this.vanilla);
    }

    /**
     * Whether the screen draws this container slot, read on every frame. The default shows the slots a slot part of
     * the panel places ({@link #placedByPanel}), and every slot on a screen without a panel. A hidden slot is
     * not drawn, item and ghost included. The pointer never finds a slot no part places, shown or not: no hover,
     * tooltip, click, drag or number key reaches it. The container's own rules still reach a hidden slot: a
     * shift-click or a double-click gather elsewhere can move items in or out, as the server decides.
     */
    public boolean showsSlot(Slot slot) {
        return this.logic.placement().finds(slot.slotNumber);
    }

    /**
     * True when a slot part of the panel ({@code SlotComponent}, {@code SlotGridComponent},
     * {@code PlayerInventoryComponent}, or your own through {@code menuWells}) draws this slot's well at
     * the container's place. {@link #showsSlot} hides by default every slot the panel leaves out: vanilla's
     * armour, offhand and crafting slots under a kit that only lays out the inventory. Reads the placement of
     * the last frame; a slot moved for good with {@link #slotOffsetX} counts as not placed.
     */
    public final boolean placedByPanel(Slot slot) {
        return this.logic.placement().placed(slot.slotNumber);
    }

    /** The panel's slot placement this frame; see {@link SlotPlacement}. */
    @Incubating
    public final SlotPlacement placement() {
        return this.logic.placement();
    }

    /**
     * True moves the inventory title onto the panel's {@code PlayerInventoryComponent}, where its own label
     * would sit, on every init. False (the default) leaves inventoryLabelX/Y as your constructor set them.
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
     * How far from its place the game draws this slot's item now, read on every frame and every
     * pointer lookup, so the pointer finds the slot where it is drawn. Give the slots of a sliding
     * region the offset of its {@code Glide} ({@code glide.offsetY(distance)}), the same glide as the
     * wells' widget. 1.12.2 draws slots on whole GUI pixels: the offset is rounded.
     */
    public float slotOffsetX(Slot slot) {
        return 0F;
    }

    /** The y part of {@link #slotOffsetX}. */
    public float slotOffsetY(Slot slot) {
        return 0F;
    }

    /** The container's left edge on screen, read on every {@code initGui}; vanilla centres it. */
    protected int containerLeft() {
        return (this.width - this.xSize) / 2;
    }

    /** The container's top edge on screen, read on every {@code initGui}; vanilla centres it. */
    protected int containerTop() {
        return (this.height - this.ySize) / 2;
    }

    /** False hides the theme tab beside the panel; read on every frame and click. */
    protected boolean showsThemeTab() {
        return true;
    }

    /** Called on every {@code initGui}, with the container's corner on screen; build the panel there. */
    protected abstract PanelComponent build(int leftPos, int topPos);

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        this.guiLeft = containerLeft();
        this.guiTop = containerTop();
        this.host.init();
        this.panel = build(this.guiLeft, this.guiTop);
        List<SlotLayout.Cell> cells = new ArrayList<>();
        for (Slot slot : this.menu.inventorySlots) {
            cells.add(new SlotLayout.Cell(slot.slotNumber, this.guiLeft + slot.xPos, this.guiTop + slot.yPos));
        }
        this.logic.init(this.panel, cells, i -> showsSlot(this.menu.inventorySlots.get(i)), getClass().getName(),
                new Box("title", this.guiLeft + this.titleLabelX, this.guiTop + this.titleLabelY,
                        this.fontRenderer.getStringWidth(this.title), this.fontRenderer.FONT_HEIGHT));
        Box label = labelsFollowPanel() ? this.logic.inventoryLabel(this.panel) : null;
        if (label != null) {
            this.inventoryLabelX = label.x() - this.guiLeft;
            this.inventoryLabelY = label.y() - this.guiTop;
        }
    }

    /** The wells go here, from the background pass, under the items the game draws next. */
    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        if (this.panel != null) {
            this.logic.drawBase(new GuiDraw(), this.panel, mouseX, mouseY);
        }
    }

    /** The game's slot tooltip, then the panel's tooltips, last. */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        if (drawsBackground()) {
            drawDefaultBackground();
        }
        ItemStack carried = this.mc.player.inventory.getItemStack();
        boolean hideCarried = !carried.isEmpty() && !drawsCarried();
        if (hideCarried) {
            this.mc.player.inventory.setItemStack(ItemStack.EMPTY);
        }
        moveSlots();
        try {
            super.drawScreen(mouseX, mouseY, partial);
        } finally {
            restoreSlots();
            if (hideCarried) {
                this.mc.player.inventory.setItemStack(carried);
            }
        }
        renderHoveredToolTip(mouseX, mouseY);
        this.logic.drawTop(new GuiDraw(), this.panel, mouseX, mouseY);
    }

    /**
     * Vanilla's labels in the theme's text colour, then the panel's overlay: the game calls this after the
     * slot items and before the carried item, with the GL matrix at the container's corner, so the overlay
     * covers the items ({@link GuiDraw} on the depth).
     */
    @Override
    protected final void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        if (drawsLabels()) {
            int color = this.logic.labelColor(this.panel);
            this.fontRenderer.drawString(this.title, this.titleLabelX, this.titleLabelY, color);
            this.fontRenderer.drawString(this.inventory.getDisplayName().getUnformattedText(),
                    this.inventoryLabelX, this.inventoryLabelY, color);
        }
        if (this.panel != null) {
            GuiDraw d = new GuiDraw();
            d.push(-this.guiLeft, -this.guiTop, 1F);
            this.logic.drawOverlay(d, this.panel, mouseX, mouseY);
            d.pop();
        }
    }

    @Override
    public void drawSlot(Slot slot) {
        if (!showsSlot(slot)) {
            return;
        }
        // Of an empty slot the game draws only its grey outline and the drag-split preview; the theme draws the outline.
        String ghost = slot.getSlotTexture();
        if (placement() != SlotPlacement.NONE && !slot.getHasStack() && slot.isEnabled()
                && !(this.dragSplitting && this.dragSplittingSlots.contains(slot))
                && (ghost == null || TextureMap.LOCATION_BLOCKS_TEXTURE.equals(slot.getBackgroundLocation()))) {
            if (ghost != null) {
                placement().drawGhost(new GuiDraw(), slot.slotNumber, SpriteIcon.block(ghost), slot.xPos, slot.yPos);
            }
            return;
        }
        super.drawSlot(slot);
    }

    @Override
    public boolean isMouseOverSlot(Slot slot, int mouseX, int mouseY) {
        return placement().finds(slot.slotNumber) && showsSlot(slot) && placement().reachable(mouseX, mouseY) && super.isMouseOverSlot(slot, mouseX, mouseY);
    }

    @Override
    protected boolean hasClickedOutside(int mouseX, int mouseY, int left, int top) {
        return this.logic.clickedOutside(this.panel, mouseX, mouseY, super.hasClickedOutside(mouseX, mouseY, left, top));
    }

    /** Drops vanilla's white slot hover, which a light theme loses: the slot components draw the theme's. */
    @Override
    protected void drawGradientRect(int left, int top, int right, int bottom, int startColor, int endColor) {
        if (startColor == VANILLA_HOVER && endColor == VANILLA_HOVER && right - left == 16 && bottom - top == 16) {
            return;
        }
        super.drawGradientRect(left, top, right, bottom, startColor, endColor);
    }

    /** Shifts every moved slot to where the screen draws it; {@link #restoreSlots} puts them back. */
    private void moveSlots() {
        List<Slot> slots = this.menu.inventorySlots;
        if (this.movedSlots.length != slots.size() * 2) {
            this.movedSlots = new int[slots.size() * 2];
        }
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            int dx = Math.round(slotOffsetX(slot)) + placement().shiftX(slot.slotNumber);
            int dy = Math.round(slotOffsetY(slot)) + placement().shiftY(slot.slotNumber);
            this.movedSlots[i * 2] = dx;
            this.movedSlots[i * 2 + 1] = dy;
            slot.xPos += dx;
            slot.yPos += dy;
        }
    }

    private void restoreSlots() {
        List<Slot> slots = this.menu.inventorySlots;
        for (int i = 0; i < slots.size() && i * 2 + 1 < this.movedSlots.length; i++) {
            Slot slot = slots.get(i);
            slot.xPos -= this.movedSlots[i * 2];
            slot.yPos -= this.movedSlots[i * 2 + 1];
            this.movedSlots[i * 2] = 0;
            this.movedSlots[i * 2 + 1] = 0;
        }
    }

    /** The vanilla container box, screen coordinates. */
    protected Box containerBox() {
        return new Box("container", this.guiLeft, this.guiTop, this.xSize, this.ySize);
    }

    /**
     * The item under the mouse: a display slot or item icon of the panel, else the slot's stack;
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
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        this.lastX = mouseX;
        this.lastY = mouseY;
        if (this.logic.mouseClicked(this.panel, mouseX, mouseY, button)) {
            return;
        }
        moveSlots();
        try {
            super.mouseClicked(mouseX, mouseY, button);
        } finally {
            restoreSlots();
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (this.logic.mouseReleased(this.panel, mouseX, mouseY, button)) {
            return;
        }
        moveSlots();
        try {
            super.mouseReleased(mouseX, mouseY, button);
        } finally {
            restoreSlots();
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long held) {
        int dx = mouseX - this.lastX;
        int dy = mouseY - this.lastY;
        this.lastX = mouseX;
        this.lastY = mouseY;
        if (this.logic.mouseDragged(this.panel, mouseX, mouseY, button, dx, dy)) {
            return;
        }
        moveSlots();
        try {
            super.mouseClickMove(mouseX, mouseY, button, held);
        } finally {
            restoreSlots();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            this.logic.mouseScrolled(this.panel, x, y, GameInput.wheel(wheel));
        }
    }

    @Override
    protected void keyTyped(char typed, int key) throws IOException {
        boolean used = this.logic.keyPressed(this.panel, GameInput.stroke(key))
                || hotbarHoldKey() != null && hotbarHoldKey().isActiveAndMatches(key) && this.logic.holdHotbar(this.panel, true);
        // GLFW sends the char as its own event whether or not the key press was used.
        used |= GameInput.typed(typed) && this.logic.charTyped(this.panel, typed);
        if (used) {
            return;
        }
        switch (MinuitMenuScreens.overKey(mapping -> mapping.isActiveAndMatches(key), this.vanilla != null, closesOnMovement())) {
            case TO_VANILLA:
                toVanilla();
                return;
            case CLOSE:
                this.mc.player.closeScreen();
                return;
            default:
                super.keyTyped(typed, key);
        }
    }

    @Override
    public void handleKeyboardInput() throws IOException {
        if (!Keyboard.getEventKeyState() && this.logic.keyReleased(this.panel, GameInput.stroke(Keyboard.getEventKey()))) {
            return;
        }
        if (!Keyboard.getEventKeyState() && hotbarHoldKey() != null && hotbarHoldKey().isActiveAndMatches(Keyboard.getEventKey())) {
            this.logic.holdHotbar(this.panel, false);
        }
        super.handleKeyboardInput();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        this.logic.tick(this.panel);
        if (GamepadPoll.tick(this.host, this.panel)) {
            try {
                keyTyped((char) 0, Keyboard.KEY_ESCAPE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        boolean back = this.handingBack;
        this.handingBack = false;
        // Handing back to the vanilla screen keeps the container, so its close waits for that screen.
        if (this.logic.removed(this.panel) && !back) {
            super.onGuiClosed();
        }
    }

    /**
     * Keeps a shift-click off armour and offhand slots no panel part places: the game would move the stack
     * there, out of sight. The shift-click then goes through left clicks to the hotbar or rows.
     */
    @Override
    protected void handleMouseClick(Slot slot, int slotId, int button, ClickType type) {
        int size = this.menu.inventorySlots.size();
        if (slot != null && type == ClickType.QUICK_MOVE) {
            List<Integer> to = this.logic.quickTargets(slotId, size, this::playerSlot);
            if (to != null) {
                ContainerLogic.route(slotId, to, new ContainerLogic.QuickMove() {
                    public boolean carrying() {
                        return !carried().isEmpty();
                    }

                    public boolean empty(int s) {
                        Slot t = menu.inventorySlots.get(s);
                        return !t.getHasStack() && t.isItemValid(carried());
                    }

                    public boolean merges(int s) {
                        Slot t = menu.inventorySlots.get(s);
                        return t.getHasStack() && Container.canAddItemToSlot(t, carried(), true)
                                && t.getStack().getCount() < t.getItemStackLimit(carried());
                    }

                    public void click(int s) {
                        MinuitContainerScreen.super.handleMouseClick(menu.inventorySlots.get(s), s, 0, ClickType.PICKUP);
                    }
                });
                return;
            }
        }
        super.handleMouseClick(slot, slotId, button, type);
    }

    private ItemStack carried() {
        return this.mc.player.inventory.getItemStack();
    }

    private int playerSlot(int index) {
        Slot slot = this.menu.inventorySlots.get(index);
        return slot.inventory instanceof InventoryPlayer ? slot.getSlotIndex() : -1;
    }
}
