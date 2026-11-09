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
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
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
 * an empty slot whose panel part draws its own outline skips {@code drawSlot}, which would draw the grey icon.
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
                    guiLeft + placeX(slot) + Math.round(slotOffsetX(slot)), guiTop + placeY(slot) + Math.round(slotOffsetY(slot)));
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
     * Whether the screen draws this slot and lets the pointer reach it, read on every frame and
     * every pointer event. A hidden slot is drawn, hovered and clicked as an inactive one: no item, no
     * hover, no tooltip, no click, drag or number key. The container's own rules still reach it: a
     * shift-click or a double-click gather elsewhere can move items in or out, as the server decides.
     * Return {@link #placedByPanel} to hide every slot no component places.
     */
    public boolean showsSlot(Slot slot) {
        return true;
    }

    /**
     * True when a slot part of the panel ({@code SlotComponent}, {@code SlotGridComponent},
     * {@code PlayerInventoryComponent}, or your own through {@code menuWells}) draws this slot's well at
     * the container's place. Return it from {@link #showsSlot} to hide every slot the panel leaves out: vanilla's
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
        // Of an empty slot the game draws only the grey icon and the drag-split preview.
        if (placement().drawsGhost(slot.slotNumber) && !slot.getHasStack()
                && !(this.dragSplitting && this.dragSplittingSlots.contains(slot))) {
            return;
        }
        super.drawSlot(slot);
    }

    @Override
    public boolean isMouseOverSlot(Slot slot, int mouseX, int mouseY) {
        return showsSlot(slot) && placement().reachable(mouseX, mouseY) && super.isMouseOverSlot(slot, mouseX, mouseY);
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

    /** The slot's x in the container, whether or not {@link #moveSlots} has it shifted now. */
    private int placeX(Slot slot) {
        int i = slot.slotNumber * 2;
        return slot.xPos - (i < this.movedSlots.length ? this.movedSlots[i] : 0);
    }

    private int placeY(Slot slot) {
        int i = slot.slotNumber * 2 + 1;
        return slot.yPos - (i < this.movedSlots.length ? this.movedSlots[i] : 0);
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
        boolean used = this.logic.keyPressed(this.panel, GameInput.stroke(key));
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
    public void updateScreen() {
        super.updateScreen();
        this.logic.tick(this.panel);
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
}
