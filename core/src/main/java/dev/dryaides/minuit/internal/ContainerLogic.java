package dev.dryaides.minuit.internal;

import dev.dryaides.minuit.BaseComponent;
import dev.dryaides.minuit.Box;
import dev.dryaides.minuit.Draw;
import dev.dryaides.minuit.ExpandableHotbarComponent;
import dev.dryaides.minuit.HotbarDrag;
import dev.dryaides.minuit.ItemDropTarget;
import dev.dryaides.minuit.ItemSource;
import dev.dryaides.minuit.ItemView;
import dev.dryaides.minuit.KeyPress;
import dev.dryaides.minuit.PanelComponent;
import dev.dryaides.minuit.PlayerInventoryComponent;
import dev.dryaides.minuit.ScreenAreas;
import dev.dryaides.minuit.SlotComponent;
import dev.dryaides.minuit.SlotGridComponent;
import dev.dryaides.minuit.SlotLayout;
import dev.dryaides.minuit.SlotPlacement;
import dev.dryaides.minuit.internal.port.KeyStroke;
import dev.dryaides.minuit.internal.port.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * What {@code client.MinuitContainerScreen} does with its panel. The game keeps every slot rule
 * through the menu; this decides which clicks and keys the panel takes. A false return lets the
 * screen hand the event to the game. Not API.
 */
public final class ContainerLogic {

    /** The menu as the logic reads it. */
    public interface MenuView {
        /** The stack of the menu slot under the mouse, or null. */
        ItemView hoveredSlotItem();

        /** The vanilla container box, screen coordinates. */
        Box container();

        /**
         * The menu slot under the mouse that the screen shows, with its item corner where the screen draws
         * it (offset included), screen coordinates; null over none.
         */
        default SlotLayout.Cell hoveredCell() {
            return null;
        }

        /** The stack on the cursor, null for none. */
        default ItemView carried() {
            return null;
        }

        /** A left click on the menu slot with this number, as the game sends it: puts, stacks or swaps. */
        default void click(int slot) {
        }

        /**
         * A quick move (shift-click) on the menu slot with this number, as the game sends it; false when the
         * screen does not send one.
         */
        default boolean quickMove(int slot) {
            return false;
        }

        /**
         * True when the menu slot with this number takes the whole carried stack on a left click: its own
         * {@code mayPlace} allows the stack and it is empty or holds the same item with room for all of it.
         * A result slot (crafting, merchant, stonecutter, furnace output) never does.
         */
        default boolean takes(int slot) {
            return false;
        }

        /**
         * Empties the cursor on the client only, after a drop target took the stack: the server, which
         * removed it through the target's own path, sends the empty cursor too.
         */
        default void clearCarried() {
        }

        /**
         * True when the menu slot's own {@code mayPlace} allows the carried stack (it may still swap).
         * False on a result slot: a click there with the same item takes another craft or trade.
         */
        default boolean mayPlace(int slot) {
            return true;
        }

        /** True for a main or hotbar slot of the player's inventory (not armour, not offhand). */
        default boolean playerSlot(int slot) {
            return false;
        }
    }

    /** What a key does on a kit screen opened over a menu, after the panel passed on it. */
    public enum OverKey { NONE, TO_VANILLA, CLOSE }

    /**
     * The inventory key shows the vanilla screen the kit replaced; a movement or jump key closes the
     * menu, as walking away does, when the screen asks for it.
     */
    public static OverKey overKey(boolean inventoryKey, boolean movementKey, boolean hasVanilla, boolean closesOnMovement) {
        if (inventoryKey && hasVanilla) {
            return OverKey.TO_VANILLA;
        }
        return movementKey && closesOnMovement ? OverKey.CLOSE : OverKey.NONE;
    }

    /** The one wheel axis the panel reads: some systems turn a shift-held wheel into a horizontal scroll. */
    public static double wheel(double scrollX, double scrollY, boolean shift) {
        return scrollY == 0 && shift ? scrollX : scrollY;
    }

    private final HostCore host;
    private final MenuView menu;
    /** The mouse at the last frame, screen coordinates. */
    private double mouseX = -1;
    private double mouseY = -1;
    /** The menu slots at the menu's place, screen coordinates, kept from {@link #init}. */
    private List<SlotLayout.Cell> slots = List.of();
    private SlotPlacement placement = SlotPlacement.NONE;
    /** One bit per game button whose press the panel took. */
    private int panelPresses;
    /** The menu slot the carried stack came from, -1 for none: where a refused drop goes back. */
    private int origin = -1;
    /** True while the carried stack came from a left pick or swap on a screen with an expandable hotbar. */
    private boolean barPick;
    /** True from a press that picked a stack until its release, which settles the drag. */
    private boolean dragging;
    /** Screen classes already given their dev notes, so a screen opened again stays quiet. */
    private static final Set<String> NOTED = ConcurrentHashMap.newKeySet();

    public ContainerLogic(HostCore host, MenuView menu) {
        this.host = host;
        this.menu = menu;
    }

    /**
     * The screen's init: keeps the menu slots ({@code slots}, item corners at the menu's place, screen
     * coordinates), places them, forgets presses from before, and in a development environment writes the
     * notes for the modder once per screen class. {@code shown} tells which slots the screen shows;
     * {@code title} is the vanilla title's box, null for none.
     */
    public void init(PanelComponent current, List<SlotLayout.Cell> slots, IntPredicate shown, String screen, Box title) {
        this.slots = List.copyOf(slots);
        this.placement = SlotPlacement.of(current, this.slots);
        this.panelPresses = 0;
        forget();
        if (current != null) {
            for (BaseComponent widget : current.widgets()) {
                if (widget instanceof SlotGridComponent grid) {
                    grid.menuKey((cell, quick) -> menuKey(grid, cell, quick));
                }
                if (widget instanceof PlayerInventoryComponent inv) {
                    inv.rows.menuKey((cell, quick) -> menuKey(inv.rows, cell, quick));
                    inv.hotbar.menuKey((cell, quick) -> menuKey(inv.hotbar, cell, quick));
                }
            }
        }
        ExpandableHotbarComponent bar = hotbar(current);
        if (bar != null) {
            bar.carried(this.menu::carried);
        }
        if (current != null && Platform.development() && NOTED.add(screen)) {
            for (String note : notes(current, this.placement, this.slots, shown, screen, title)) {
                Platform.note(note);
            }
        }
    }

    /**
     * Enter on a grid cell: a left click on the menu slot the panel places in that cell, Shift+Enter a quick
     * move; false when no slot sits there.
     */
    boolean menuKey(SlotGridComponent grid, int cell, boolean quick) {
        Box well = grid.cellBox(cell);
        for (SlotLayout.Cell slot : this.slots) {
            if (this.placement.placed(slot.index()) && well.contains(new Box("item", slot.x(), slot.y(), 16, 16))) {
                if (quick) {
                    return this.menu.quickMove(slot.index());
                }
                this.menu.click(slot.index());
                return true;
            }
        }
        return false;
    }

    /**
     * The first visible widget the title overlaps that a player uses: one with keyboard stops, or a slot part,
     * found through the wells of the slots it places since {@code menuWells} is not visible from here.
     */
    private static BaseComponent underTitle(PanelComponent current, SlotPlacement placement, List<SlotLayout.Cell> slots, Box title) {
        for (BaseComponent widget : current.widgets()) {
            if (!widget.visible || !widget.box().intersects(title)) {
                continue;
            }
            if (!widget.stops().isEmpty()) {
                return widget;
            }
            for (SlotLayout.Cell slot : slots) {
                Box well = new Box("well", slot.x() - 1, slot.y() - 1, SlotLayout.PITCH, SlotLayout.PITCH);
                if (placement.placed(slot.index()) && well.intersects(title) && widget.box().contains(well)) {
                    return widget;
                }
            }
        }
        return null;
    }

    static List<String> notes(PanelComponent current, SlotPlacement placement, List<SlotLayout.Cell> slots,
                              IntPredicate shown, String screen, Box title) {
        List<String> out = new ArrayList<>();
        List<Integer> loose = new ArrayList<>();
        for (SlotLayout.Cell slot : slots) {
            if (shown.test(slot.index()) && !placement.placed(slot.index())) {
                loose.add(slot.index());
            }
        }
        loose.sort(null);
        if (!loose.isEmpty()) {
            out.add("minuit: " + screen + " shows menu slots no panel component places: " + ranges(loose)
                    + ". The pointer never finds them; place them with a slot part, or leave showsSlot to its default, which hides them.");
        }
        BaseComponent under = title == null ? null : underTitle(current, placement, slots, title);
        if (under != null) {
            out.add("minuit: " + screen + " draws its title under " + under.box().name()
                    + "; move the widget, the title (titleLabelX/Y) or turn labels off (drawsLabels).");
        }
        return out;
    }

    /** Runs of indices as the note prints them: 0-8, 45. */
    static String ranges(List<Integer> sorted) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            int start = sorted.get(i);
            while (i + 1 < sorted.size() && sorted.get(i + 1) == sorted.get(i) + 1) {
                i++;
            }
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(start == sorted.get(i) ? String.valueOf(start) : start + "-" + sorted.get(i));
        }
        return out.toString();
    }

    /** The placement of the last frame; {@link SlotPlacement#NONE} before {@link #init} or without a panel. */
    public SlotPlacement placement() {
        return this.placement;
    }

    /**
     * Where the first visible player inventory of the panel draws its label, screen coordinates, as its own
     * {@code label} does; null when the panel has none.
     */
    public Box inventoryLabel(PanelComponent current) {
        if (current == null) {
            return null;
        }
        for (BaseComponent widget : current.widgets()) {
            if (widget.visible && widget instanceof PlayerInventoryComponent) {
                return new Box("inventoryLabel", widget.x + 1, widget.y + 1 - SlotLayout.LABEL_GAP, 0, 0);
            }
        }
        return null;
    }

    /** Vanilla's answer, unless the point lies on the panel: a click on what the player sees never throws the stack. */
    public boolean clickedOutside(PanelComponent current, double x, double y, boolean vanilla) {
        return vanilla && !SlotPlacement.of(current, List.of()).covers(x, y);
    }

    /** The wells, under the items the game draws a layer up; places the kept slots for this frame first. */
    public void drawBase(Draw d, PanelComponent current, int mx, int my) {
        this.placement = SlotPlacement.of(current, this.slots);
        if (current == null) {
            return;
        }
        this.host.drawTab(d, current.theme(), current.bounds(), mx, my);
        current.drawBase(d, mx, my, this.host.frame());
    }

    /** After the items, each popup a layer up, so an open dropdown covers them; the carried item still comes after it. */
    public void drawOverlay(Draw d, PanelComponent current, int mx, int my) {
        if (current != null) {
            current.drawOverlay(d, mx, my, 1F);
        }
    }

    /** The tooltips, last. */
    public void drawTop(Draw d, PanelComponent current, int mx, int my) {
        this.mouseX = mx;
        this.mouseY = my;
        if (current == null) {
            return;
        }
        current.drawTooltip(d, mx, my);
        this.host.drawTabTip(d, current.theme(), current.bounds(), mx, my);
    }

    /** Vanilla's labels in the theme's text colour; vanilla hard-codes a dark grey. */
    public int labelColor(PanelComponent current) {
        return current == null ? 0xFF404040 : current.theme().palette().text();
    }

    /** A display slot or item icon of the panel, else the menu slot's stack; null over nothing. */
    public ItemView itemUnderMouse(PanelComponent current) {
        if (current != null) {
            ItemView shown = ItemSource.under(current, this.mouseX, this.mouseY);
            if (shown != null) {
                return shown;
            }
        }
        return this.menu.hoveredSlotItem();
    }

    public List<Box> extraAreas(PanelComponent current) {
        return current == null ? List.of() : ScreenAreas.outside(current, this.menu.container());
    }

    public List<ItemDropTarget.Drop> dropTargets(PanelComponent current) {
        return current == null ? List.of() : ItemDropTarget.all(current);
    }

    /** True when no widget is under the mouse, or the topmost one is a slot part over menu slots: the game takes that click. */
    static boolean onMenuSlots(PanelComponent current, double mx, double my) {
        List<BaseComponent> widgets = current.widgets();
        for (int i = widgets.size() - 1; i >= 0; i--) {
            BaseComponent widget = widgets.get(i);
            if (widget.isOver(mx, my)) {
                if (widget instanceof SlotGridComponent) {
                    return ((SlotGridComponent) widget).holdsMenu();
                }
                if (widget instanceof PlayerInventoryComponent) {
                    return ((PlayerInventoryComponent) widget).holdsMenu();
                }
                return widget instanceof SlotComponent && ((SlotComponent) widget).holdsMenu();
            }
        }
        return true;
    }

    public boolean mouseClicked(PanelComponent current, double mx, double my, int gameButton) {
        if (current == null) {
            return false;
        }
        boolean took = takesClick(current, mx, my, gameButton);
        if (!took && KeyRouter.button(gameButton, Platform.keyCodes()) == 0) {
            took = pressStack(current, mx, my);
        } else if (!took) {
            gamePress();
        }
        if (took && gameButton >= 0 && gameButton < 32) {
            this.panelPresses |= 1 << gameButton;
        }
        return took;
    }

    private boolean takesClick(PanelComponent current, double mx, double my, int gameButton) {
        if (current.capturing() && current.rawKey(KeyPress.mouse(gameButton))) {
            return true;
        }
        int button = KeyRouter.button(gameButton, Platform.keyCodes());
        boolean toPanel = button >= 0 && (current.popupOpen() || !onMenuSlots(current, mx, my));
        if (toPanel && current.mouseClicked(mx, my, button)) {
            return true;
        }
        return button >= 0 && this.host.clickTab(current.theme(), current.bounds(), mx, my);
    }

    /**
     * Gives the release to the panel; true when the panel took the press before it, and the screen then keeps
     * the release from the game, which would otherwise act on a press it never saw.
     */
    public boolean mouseReleased(PanelComponent current, double mx, double my, int gameButton) {
        boolean pressed = gameButton >= 0 && gameButton < 32 && (this.panelPresses & 1 << gameButton) != 0;
        if (pressed) {
            this.panelPresses &= ~(1 << gameButton);
        }
        int button = KeyRouter.button(gameButton, Platform.keyCodes());
        if (current != null && button >= 0) {
            current.mouseReleased(mx, my, button);
        }
        return pressed || button == 0 && releaseStack(current, mx, my);
    }

    /** The panel's visible expandable hotbar, or null: a screen with one settles drags with {@link HotbarDrag}. */
    private static ExpandableHotbarComponent hotbar(PanelComponent current) {
        if (current == null) {
            return null;
        }
        for (BaseComponent widget : current.widgets()) {
            if (widget.visible && widget instanceof ExpandableHotbarComponent) {
                return (ExpandableHotbarComponent) widget;
            }
        }
        return null;
    }

    /**
     * A left press the panel left to the game, on a screen with an expandable hotbar. A pick or a place
     * stays the game's slot click (false); a drop or a give-back is done here and the press kept (true).
     */
    private boolean pressStack(PanelComponent current, double mx, double my) {
        ExpandableHotbarComponent bar = hotbar(current);
        this.dragging = false;
        if (bar == null) {
            return false;
        }
        SlotLayout.Cell cell = this.menu.hoveredCell();
        ItemView carried = this.menu.carried();
        if (carried == null) {
            boolean pick = HotbarDrag.press(false, under(bar, cell, mx, my, null), this.menu.hoveredSlotItem() != null) == HotbarDrag.Act.PICK;
            this.origin = pick ? cell.index() : -1;
            this.barPick = pick;
            this.dragging = pick;
            return false;
        }
        HotbarDrag.Act act = HotbarDrag.press(true, under(bar, cell, mx, my, carried), cell != null);
        if (act == HotbarDrag.Act.PLACE) {
            placed(cell, carried);
            this.barPick = true;
            return false;
        }
        if (act == HotbarDrag.Act.RETURN && !this.barPick) {
            // A stack the drag did not pick: the game's click outside throws it.
            forget();
            return false;
        }
        return settle(act, bar, mx, my, carried);
    }

    /**
     * A press or a key the game handles itself (right or middle button, shift, number swap): a pick with it
     * sets the origin, as a left pick does, but a stack it picks is not the drag's, so a click outside
     * still throws it.
     */
    private void gamePress() {
        if (this.menu.carried() == null) {
            SlotLayout.Cell cell = this.menu.hoveredCell();
            this.origin = cell == null ? -1 : cell.index();
            this.barPick = false;
        }
    }

    /**
     * A left click puts the carried stack on this cell: a different item there swaps, and the stack then on
     * the cursor came from this cell; a merge leaves the remainder from the first origin.
     */
    private void placed(SlotLayout.Cell cell, ItemView carried) {
        ItemView there = this.menu.hoveredSlotItem();
        if (there != null && !there.id().equals(carried.id())) {
            this.origin = cell.index();
        }
    }

    private void forget() {
        this.origin = -1;
        this.barPick = false;
        this.dragging = false;
    }

    /** The release of a press that picked a stack: on its slot it keeps carrying, elsewhere it ends the drag. */
    private boolean releaseStack(PanelComponent current, double mx, double my) {
        boolean wasDragging = this.dragging;
        this.dragging = false;
        ExpandableHotbarComponent bar = hotbar(current);
        ItemView carried = this.menu.carried();
        if (!wasDragging || bar == null || carried == null) {
            return false;
        }
        SlotLayout.Cell cell = this.menu.hoveredCell();
        HotbarDrag.Under under = cell != null && !this.menu.mayPlace(cell.index()) ? HotbarDrag.Under.NOTHING : under(bar, cell, mx, my, carried);
        HotbarDrag.Act act = HotbarDrag.release(true, under, cell != null && cell.index() == this.origin);
        if (act == HotbarDrag.Act.PLACE) {
            placed(cell, carried);
            this.menu.click(cell.index());
            return true;
        }
        return settle(act, bar, mx, my, carried);
    }

    /** A target counts only when the carried stack fits it; one that does not is nothing, so the stack goes back. */
    private static HotbarDrag.Under under(ExpandableHotbarComponent bar, SlotLayout.Cell cell, double mx, double my, ItemView carried) {
        if (cell != null) {
            return HotbarDrag.Under.SLOT;
        }
        ItemDropTarget.Drop drop = bar.isOver(mx, my) ? null : bar.targetAt((int) mx, (int) my);
        return drop != null && (carried == null || drop.fits(carried)) ? HotbarDrag.Under.TARGET : HotbarDrag.Under.NOTHING;
    }

    private boolean settle(HotbarDrag.Act act, ExpandableHotbarComponent bar, double mx, double my, ItemView carried) {
        if (act == HotbarDrag.Act.DROP) {
            if (bar.targetAt((int) mx, (int) my).acceptDrop(carried)) {
                // The target's server side takes the stack from the cursor; the client shows it gone at once.
                this.menu.clearCarried();
                forget();
            } else {
                giveBack();
            }
            return true;
        }
        return act == HotbarDrag.Act.RETURN && giveBack();
    }

    /**
     * Puts the carried stack back with a left click on a slot that takes all of it: the slot it came from,
     * else the first slot of the player's inventory that does, else none, and the stack stays carried.
     * A slot that refuses it is never clicked: on a result slot the game would craft or trade again.
     * False when no slot takes it, so a click outside stays the game's and throws the stack.
     */
    private boolean giveBack() {
        int slot = giveBackSlot();
        if (slot < 0) {
            return false;
        }
        this.menu.click(slot);
        forget();
        return true;
    }

    private int giveBackSlot() {
        if (this.origin >= 0 && this.menu.takes(this.origin)) {
            return this.origin;
        }
        for (SlotLayout.Cell cell : this.slots) {
            if (this.menu.playerSlot(cell.index()) && this.menu.takes(cell.index())) {
                return cell.index();
            }
        }
        return -1;
    }

    /**
     * The screen's hold key went down ({@code open}) or up: the expandable hotbar stays unfolded while it is
     * held. False when the panel has no such hotbar.
     */
    public boolean holdHotbar(PanelComponent current, boolean open) {
        ExpandableHotbarComponent bar = hotbar(current);
        if (bar != null) {
            bar.hold(open);
        }
        return bar != null;
    }

    public boolean mouseDragged(PanelComponent current, double mx, double my, int gameButton, double dx, double dy) {
        int button = KeyRouter.button(gameButton, Platform.keyCodes());
        return current != null && button >= 0 && current.mouseDragged(mx, my, button, dx, dy);
    }

    public boolean mouseScrolled(PanelComponent current, double mx, double my, double scrollY) {
        return current != null && current.mouseScrolled(mx, my, scrollY);
    }

    /**
     * The panel first; while one of its fields holds the keyboard every key stays with it, so typing
     * neither closes the screen on the inventory key nor swaps hotbar slots.
     */
    public boolean keyPressed(PanelComponent current, KeyStroke stroke) {
        boolean took = current != null && (this.host.keyPressed(current, stroke) || current.focus() != null);
        if (!took) {
            gamePress();
        }
        return took;
    }

    public boolean keyReleased(PanelComponent current, KeyStroke stroke) {
        return current != null && this.host.keyReleased(current, stroke);
    }

    /** A controller's back button reaches the screen's {@code onClose} without a key: it closes an open overlay first. */
    public boolean closeOverlay(PanelComponent current) {
        return current != null && current.closeOverlay();
    }

    public boolean charTyped(PanelComponent current, int codepoint) {
        return current != null && current.charTyped(codepoint);
    }

    public void tick(PanelComponent current) {
        if (current != null) {
            current.tickAll();
        }
    }

    /**
     * Back from the theme page the game builds the screen again on the same menu, so nothing closes:
     * false then, and the screen skips its own {@code removed}.
     */
    public boolean removed(PanelComponent current) {
        if (this.host.away()) {
            return false;
        }
        if (current != null) {
            current.closing();
        }
        forget();
        return true;
    }

    /** The menu's live state while a routed shift-click runs, read again after each click. */
    public interface QuickMove {
        /** True while a stack is on the cursor. */
        boolean carrying();

        /** True when the slot is empty and takes the carried stack. */
        boolean empty(int slot);

        /** True when the slot holds the carried item and has room for more of it. */
        boolean merges(int slot);

        /** A plain left click on the slot (pick up, place, merge, swap). */
        void click(int slot);
    }

    /**
     * A slot of the game's player menu ({@code InventoryMenu}, fixed since 1.9) as its index in the player
     * inventory: armour 5 to 8 are 39 to 36, the rows keep 9 to 35, the hotbar 36 to 44 is 0 to 8, the offhand
     * 45 is 40; -1 for the craft result and grid. For a port whose names give no slot's inventory index.
     */
    public static int playerMenuSlot(int index) {
        if (index >= 5 && index <= 8) {
            return 44 - index;
        }
        if (index >= 9 && index <= 35) {
            return index;
        }
        if (index >= 36 && index <= 44) {
            return index - 36;
        }
        return index == 45 ? 40 : -1;
    }

    /** Inventory slots 36 to 39 are the armour, 40 the offhand, in the player inventory of every version. */
    private static boolean equipment(int playerSlot) {
        return playerSlot >= 36 && playerSlot <= 40;
    }

    /**
     * The menu slots a shift-click on {@code from} goes to instead of the menu's quick move, or null to leave
     * it to the game. Routed when the menu holds an armour or offhand slot the pointer cannot find (no panel
     * part places it): the game's quick move would send armour or a shield there, out of sight. A player row
     * slot then goes to the hotbar and a hotbar slot to the rows, in menu order, as the game does for any other
     * item. {@code playerSlot} gives a menu slot's index in the player inventory, -1 for another container.
     */
    public static List<Integer> quickTargets(int from, int menuSize, IntUnaryOperator playerSlot, IntPredicate finds) {
        if (!hidesEquipment(menuSize, playerSlot, finds, false)) {
            return null;
        }
        int own = playerSlot.applyAsInt(from);
        boolean rows = own >= 9 && own <= 35;
        if (!rows && (own < 0 || own > 8)) {
            return null;
        }
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < menuSize; i++) {
            int p = playerSlot.applyAsInt(i);
            if (finds.test(i) && (rows ? p >= 0 && p <= 8 : p >= 9 && p <= 35)) {
                out.add(i);
            }
        }
        return out;
    }

    /**
     * True when the offhand key (swap with inventory slot 40) must do nothing: the menu holds the offhand slot
     * and no panel part places it, so the swapped stack would land out of sight.
     */
    public static boolean blocksOffhandSwap(int menuSize, IntUnaryOperator playerSlot, IntPredicate finds) {
        return hidesEquipment(menuSize, playerSlot, finds, true);
    }

    private static boolean hidesEquipment(int menuSize, IntUnaryOperator playerSlot, IntPredicate finds, boolean offhandOnly) {
        for (int i = 0; i < menuSize; i++) {
            int p = playerSlot.applyAsInt(i);
            if ((offhandOnly ? p == 40 : equipment(p)) && !finds.test(i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves the stack of {@code from} with left clicks, as the game's quick move would without the hidden
     * slots: onto stacks of the same item in {@code targets} first, then into the first empty ones, and what
     * is left back to {@code from}.
     */
    public static void route(int from, List<Integer> targets, QuickMove menu) {
        menu.click(from);
        if (!menu.carrying()) {
            return;
        }
        for (int t : targets) {
            if (!menu.carrying()) {
                return;
            }
            if (menu.merges(t)) {
                menu.click(t);
            }
        }
        for (int t : targets) {
            if (!menu.carrying()) {
                return;
            }
            if (menu.empty(t)) {
                menu.click(t);
            }
        }
        if (menu.carrying()) {
            menu.click(from);
        }
    }

    /** {@link #quickTargets} on this screen's placement. */
    public List<Integer> quickTargets(int from, int menuSize, IntUnaryOperator playerSlot) {
        return quickTargets(from, menuSize, playerSlot, this.placement::finds);
    }

    /** {@link #blocksOffhandSwap} on this screen's placement. */
    public boolean blocksOffhandSwap(int menuSize, IntUnaryOperator playerSlot) {
        return blocksOffhandSwap(menuSize, playerSlot, this.placement::finds);
    }
}
