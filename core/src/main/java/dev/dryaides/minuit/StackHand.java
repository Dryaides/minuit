package dev.dryaides.minuit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Slots of item stacks and the stack carried at the mouse, played with vanilla's click rules, for a menu
 * with no server container (a client-only screen, the site's demos). In a container screen the game's
 * menu owns the slots and the server stays the authority: leave those components unbound, so
 * {@code holdsMenu()} stays true and {@code ContainerLogic} hands their clicks to the game.
 *
 * <p>The rules are those of the game's {@code AbstractContainerMenu.doClick} and the gestures those of
 * {@code AbstractContainerScreen} (mouseClicked, mouseDragged, mouseReleased, keyPressed), 1.21:
 * <ul>
 * <li>PICKUP ({@link #click}): left takes or places the whole stack, merges up to the item's maximum, swaps
 * different items; right takes half rounded up, or places one.</li>
 * <li>QUICK_MOVE ({@link #quickMove}, Shift + click): the stack goes to the other section, merging into
 * stacks of the same item first, then into the first empty slot; what finds no room stays.</li>
 * <li>QUICK_CRAFT (press with a carried stack, drag over slots, release): left splits the stack evenly,
 * right puts one in each; over a single slot it is a plain click.</li>
 * <li>PICKUP_ALL (double-click with the left button): gathers the same item into the carried stack,
 * partial stacks before full ones.</li>
 * <li>SWAP ({@link #swap}, keys 1 to 9 over a slot, empty hand): the slot and that hotbar slot trade.</li>
 * <li>THROW ({@link #throwFrom}, Q over a slot, empty hand): one item leaves the slot, the whole stack with
 * Ctrl. With no world to drop it into, it is gone.</li>
 * </ul>
 * Clicking outside the slots with a carried stack does not drop it here: it stays at the mouse.
 */
@Incubating
public final class StackHand {

    /** An item id ({@code namespace:path}) and a count of at least 1. */
    public record Stack(String id, int count) {
        public Stack {
            if (id == null || count < 1) {
                throw new IllegalArgumentException(id + " x" + count);
            }
        }

        Stack withCount(int n) {
            return n < 1 ? null : new Stack(this.id, n);
        }
    }

    private final Stack[] slots;
    private final ToIntFunction<String> maxOf;
    private final List<Runnable> views = new ArrayList<>();
    private Stack carried;
    /** Where the mouse finds a slot: one per bound component, -1 when it is not over one of theirs. */
    private final List<Locator> places = new ArrayList<>();
    /** Per slot, the slots a quick move tries, in order; null for a slot that does not quick-move. */
    private final int[][] targets;
    /** The slots in the game menu's order, which PICKUP_ALL walks; null for index order. */
    private int[] menuOrder;
    private int hotbarFirst = -1;

    private boolean pressed;
    private boolean skipRelease;
    private boolean doubleClick;
    private int lastSlot = -1;
    private long lastTime;
    private int lastButton = -1;
    private int[] hotbarKeys = {49, 50, 51, 52, 53, 54, 55, 56, 57};
    private int dropKey = 81;
    private boolean dragging;
    private int dragButton;
    private final Set<Integer> dragSlots = new LinkedHashSet<>();

    private interface Locator {
        int slotAt(double mx, double my);
    }

    /** {@code size} slots whose items all stack to 64. */
    public StackHand(int size) {
        this(size, id -> 64);
    }

    /** @param maxOf the most one slot holds of an item id (64, 16 for ender pearls, 1 for tools) */
    public StackHand(int size, ToIntFunction<String> maxOf) {
        this.slots = new Stack[size];
        this.targets = new int[size][];
        this.maxOf = maxOf;
    }

    /**
     * Wires a container above the player inventory as vanilla's {@code ChestMenu.quickMoveStack} does: a
     * container slot moves into the inventory from the menu's last slot backwards (the hotbar right to left,
     * then the rows from the bottom), an inventory slot into the container from its first slot. The menu
     * lists the container, the three rows, then the hotbar; here the inventory is bound hotbar first
     * ({@link #bind(PlayerInventoryComponent, int)}). Keys 1 to 9 swap with that hotbar.
     */
    public StackHand containerOver(int containerFirst, int containerSize, int inventoryFirst) {
        int[] container = range(containerFirst, containerSize);
        int[] inventory = new int[36];
        for (int i = 0; i < 27; i++) {
            inventory[i] = inventoryFirst + 9 + i;
        }
        for (int i = 0; i < 9; i++) {
            inventory[27 + i] = inventoryFirst + i;
        }
        int[] back = new int[36];
        for (int i = 0; i < 36; i++) {
            back[i] = inventory[35 - i];
        }
        moves(containerFirst, containerFirst + containerSize - 1, back);
        moves(inventoryFirst, inventoryFirst + 35, container);
        this.menuOrder = new int[containerSize + 36];
        System.arraycopy(container, 0, this.menuOrder, 0, containerSize);
        System.arraycopy(inventory, 0, this.menuOrder, containerSize, 36);
        return hotbar(inventoryFirst);
    }

    /** Slots {@code first} to {@code last} quick-move into {@code into}, tried in that order. */
    public StackHand moves(int first, int last, int... into) {
        for (int i = first; i <= last; i++) {
            this.targets[i] = into;
        }
        return this;
    }

    /** The nine slots from {@code first} are the hotbar the number keys swap with. */
    public StackHand hotbar(int first) {
        this.hotbarFirst = first;
        return this;
    }

    private static int[] range(int first, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = first + i;
        }
        return out;
    }

    public int size() {
        return this.slots.length;
    }

    public Stack get(int slot) {
        return this.slots[slot];
    }

    public StackHand set(int slot, Stack stack) {
        this.slots[slot] = stack;
        refresh();
        return this;
    }

    /** The stack at the mouse, or null. */
    public Stack carried() {
        return this.carried;
    }

    public StackHand carried(Stack stack) {
        this.carried = stack;
        return this;
    }

    /**
     * Applies a click on {@code slot} with mouse {@code button} (0 left, 1 right; others do nothing).
     *
     * @return true when a slot or the hand changed
     */
    public boolean click(int slot, int button) {
        if (button != 0 && button != 1) {
            return false;
        }
        Stack in = this.slots[slot];
        Stack hand = this.carried;
        if (in == null && hand == null) {
            return false;
        }
        if (hand == null) {
            int take = button == 0 ? in.count() : (in.count() + 1) / 2;
            this.carried = in.withCount(take);
            this.slots[slot] = in.withCount(in.count() - take);
        } else if (in == null) {
            int put = button == 0 ? Math.min(hand.count(), this.maxOf.applyAsInt(hand.id())) : 1;
            this.slots[slot] = hand.withCount(put);
            this.carried = hand.withCount(hand.count() - put);
        } else if (in.id().equals(hand.id())) {
            int room = this.maxOf.applyAsInt(in.id()) - in.count();
            int put = Math.min(button == 0 ? hand.count() : 1, Math.max(room, 0));
            if (put == 0) {
                return false;
            }
            this.slots[slot] = in.withCount(in.count() + put);
            this.carried = hand.withCount(hand.count() - put);
        } else {
            this.slots[slot] = hand;
            this.carried = in;
        }
        refresh();
        return true;
    }

    /**
     * QUICK_MOVE: the stack on {@code slot} goes into its targets ({@link #moves}), first onto stacks of the
     * same item below their maximum, then whole into the first empty one. What finds no room stays.
     *
     * @return true when anything moved
     */
    public boolean quickMove(int slot) {
        Stack in = this.slots[slot];
        int[] into = this.targets[slot];
        if (in == null || into == null) {
            return false;
        }
        int max = this.maxOf.applyAsInt(in.id());
        int left = in.count();
        for (int t : into) {
            Stack there = this.slots[t];
            if (left > 0 && t != slot && there != null && there.id().equals(in.id()) && there.count() < max) {
                int put = Math.min(left, max - there.count());
                this.slots[t] = there.withCount(there.count() + put);
                left -= put;
            }
        }
        for (int t : into) {
            if (left > 0 && t != slot && this.slots[t] == null) {
                this.slots[t] = in.withCount(left);
                left = 0;
            }
        }
        if (left == in.count()) {
            return false;
        }
        this.slots[slot] = in.withCount(left);
        refresh();
        return true;
    }

    /** SWAP: with an empty hand, {@code slot} and hotbar slot {@code key} (0 to 8) trade their stacks. */
    public boolean swap(int slot, int key) {
        if (this.carried != null || this.hotbarFirst < 0 || key < 0 || key > 8) {
            return false;
        }
        int bar = this.hotbarFirst + key;
        if (bar == slot || this.slots[slot] == null && this.slots[bar] == null) {
            return false;
        }
        Stack in = this.slots[slot];
        this.slots[slot] = this.slots[bar];
        this.slots[bar] = in;
        refresh();
        return true;
    }

    /** THROW: with an empty hand, one item leaves {@code slot}, or the whole stack when {@code all}. */
    public boolean throwFrom(int slot, boolean all) {
        Stack in = this.slots[slot];
        if (this.carried != null || in == null) {
            return false;
        }
        this.slots[slot] = all ? null : in.withCount(in.count() - 1);
        refresh();
        return true;
    }

    /**
     * PICKUP_ALL: with a stack carried and {@code slot} empty, takes the same item from the slots in menu
     * order until the hand holds the item's maximum, stacks below their maximum first, full stacks after.
     */
    public boolean pickAll(int slot) {
        Stack hand = this.carried;
        if (hand == null || this.slots[slot] != null) {
            return false;
        }
        int max = this.maxOf.applyAsInt(hand.id());
        int have = hand.count();
        int[] order = this.menuOrder != null ? this.menuOrder : range(0, this.slots.length);
        for (int pass = 0; pass < 2; pass++) {
            for (int i : order) {
                Stack s = this.slots[i];
                if (have < max && s != null && s.id().equals(hand.id()) && (pass != 0 || s.count() != max)) {
                    int take = Math.min(max - have, s.count());
                    this.slots[i] = s.withCount(s.count() - take);
                    have += take;
                }
            }
        }
        if (have == hand.count()) {
            return false;
        }
        this.carried = hand.withCount(have);
        refresh();
        return true;
    }

    /**
     * A mouse button pressed on {@code slot}, as {@code AbstractContainerScreen.mouseClicked}: an empty hand
     * clicks at once (a quick move with Shift); a carried stack starts a drag that the release ends. A second
     * press of the same button on the same slot within 250 ms makes the release a double-click.
     */
    public void press(int slot, int button, boolean shift, long now) {
        if (button != 0 && button != 1) {
            return;
        }
        this.doubleClick = slot == this.lastSlot && now - this.lastTime < 250 && button == this.lastButton;
        this.skipRelease = false;
        this.pressed = true;
        if (!this.dragging) {
            if (this.carried == null) {
                if (shift) {
                    quickMove(slot);
                } else {
                    click(slot, button);
                }
                this.skipRelease = true;
            } else {
                this.dragging = true;
                this.dragButton = button;
                this.dragSlots.clear();
            }
        }
        this.lastSlot = slot;
        this.lastTime = now;
        this.lastButton = button;
    }

    /** The mouse moved over {@code slot} (-1 for none) with a button down: a drag takes in a slot the stack may go to. */
    public void drag(int slot) {
        Stack hand = this.carried;
        if (!this.dragging || slot < 0 || hand == null || hand.count() <= this.dragSlots.size()) {
            return;
        }
        Stack there = this.slots[slot];
        if ((there == null || there.id().equals(hand.id())) && this.dragSlots.add(slot)) {
            refresh();
        }
    }

    /**
     * The button let go over {@code slot} (-1 for none), as {@code AbstractContainerScreen.mouseReleased}:
     * a double-click gathers, a drag over several slots spreads (left evenly, right one each), over one slot
     * or none it is a click on the slot under the mouse (a quick move with Shift).
     */
    public void release(int slot, int button, boolean shift) {
        if (!this.pressed) {
            return;
        }
        if (this.doubleClick && slot >= 0 && button == 0) {
            pickAll(slot);
            this.doubleClick = false;
            this.lastSlot = -1;
            endGesture();
            return;
        }
        if (this.dragging && this.dragButton != button) {
            this.dragging = false;
            this.dragSlots.clear();
            this.skipRelease = true;
            refresh();
            return;
        }
        if (this.skipRelease) {
            endGesture();
            return;
        }
        if (this.dragging && this.dragSlots.size() == 1) {
            click(this.dragSlots.iterator().next(), this.dragButton);
        } else if (this.dragging && !this.dragSlots.isEmpty()) {
            spread(this.dragButton);
        } else if (this.carried != null && slot >= 0) {
            if (shift) {
                quickMove(slot);
            } else {
                click(slot, button);
            }
        }
        if (this.carried == null) {
            this.lastTime = 0;
        }
        endGesture();
    }

    private void endGesture() {
        boolean shown = this.dragging && this.dragSlots.size() > 1;
        this.pressed = false;
        this.skipRelease = false;
        this.dragging = false;
        this.dragSlots.clear();
        if (shown) {
            refresh();
        }
    }

    /** True while a drag covers several slots: the game then shows what the release would leave. */
    private boolean spreading() {
        return this.dragging && this.dragSlots.size() > 1 && this.carried != null;
    }

    /** The count dragged slot {@code t} ends with on release, as {@link #spread} gives it. */
    private int placed(int t, int button) {
        Stack hand = this.carried;
        int each = button == 0 ? hand.count() / this.dragSlots.size() : 1;
        Stack there = this.slots[t];
        return Math.min(each + (there == null ? 0 : there.count()), this.maxOf.applyAsInt(hand.id()));
    }

    /** What {@code slot} shows: during a spreading drag, the stack the release would leave there. */
    private Stack shown(int slot) {
        if (spreading() && this.dragSlots.contains(slot)) {
            return this.carried.withCount(placed(slot, this.dragButton));
        }
        return this.slots[slot];
    }

    /** The carried stack as shown: during a spreading drag, what the release would leave in the hand. */
    private Stack carriedShown() {
        if (!spreading()) {
            return this.carried;
        }
        int left = this.carried.count();
        for (int t : this.dragSlots) {
            Stack there = this.slots[t];
            left -= placed(t, this.dragButton) - (there == null ? 0 : there.count());
        }
        return this.carried.withCount(left);
    }

    /** QUICK_CRAFT's end: each dragged slot gets the count divided evenly (left) or one (right), up to the maximum. */
    private void spread(int button) {
        Stack hand = this.carried;
        int left = hand.count();
        int[] now = new int[this.slots.length];
        for (int t : this.dragSlots) {
            now[t] = placed(t, button);
        }
        for (int t : this.dragSlots) {
            Stack there = this.slots[t];
            int had = there == null ? 0 : there.count();
            if (now[t] > 0) {
                this.slots[t] = hand.withCount(now[t]);
                left -= now[t] - had;
            }
        }
        this.carried = hand.withCount(left);
        refresh();
    }

    /** The bound slot under the mouse, or -1. */
    public int slotAt(double mx, double my) {
        for (Locator place : this.places) {
            int slot = place.slotAt(mx, my);
            if (slot >= 0) {
                return slot;
            }
        }
        return -1;
    }

    /** The host's mouse move with a button down; the press itself comes through the bound components. */
    public void mouseDragged(double mx, double my) {
        drag(slotAt(mx, my));
    }

    /** The host's button release, Shift read from {@link BaseComponent#keys}. */
    public void mouseReleased(double mx, double my, int button) {
        release(slotAt(mx, my), button, BaseComponent.heldKeys().shift());
    }

    /**
     * The key codes of the player's hotbar bindings (nine, slot 1 first) and of the drop binding, so a
     * host passes its key mappings; the defaults are GLFW 1 to 9 and Q.
     */
    public StackHand keys(int[] hotbar, int drop) {
        if (hotbar.length != 9) {
            throw new IllegalArgumentException("nine hotbar keys, got " + hotbar.length);
        }
        this.hotbarKeys = hotbar.clone();
        this.dropKey = drop;
        return this;
    }

    /**
     * A key pressed with the mouse at {@code mx, my}, matched by key code as the game matches its key
     * mappings: a hotbar key swaps the slot under the mouse with that hotbar slot, the drop key throws one
     * item from it, the stack with Ctrl. As in the game, both need an empty hand.
     *
     * @return true when the mouse is over a slot and the key is one of those
     */
    public boolean keyPressed(double mx, double my, int keyCode) {
        int slot = slotAt(mx, my);
        if (slot < 0) {
            return false;
        }
        for (int i = 0; i < this.hotbarKeys.length; i++) {
            if (this.hotbarKeys[i] == keyCode) {
                swap(slot, i);
                return true;
            }
        }
        if (keyCode == this.dropKey) {
            throwFrom(slot, BaseComponent.heldKeys().ctrl());
            return true;
        }
        return false;
    }

    private void pressed(int slot, int button) {
        press(slot, button, BaseComponent.heldKeys().shift(), BaseComponent.clock().getAsLong());
    }

    /** The slot as an icon, or null when empty. */
    public ItemView view(int slot) {
        return view(this.slots[slot]);
    }

    private static ItemView view(Stack stack) {
        return stack == null ? null : ItemView.of(stack.id(), stack.count());
    }

    /**
     * Slots {@code first} to {@code first + rows * cols - 1} shown in the grid. A mouse press on it starts a
     * gesture ({@link #press}), which the host ends with {@link #mouseReleased}; Enter on its cursor is a click.
     */
    public StackHand bind(SlotGridComponent grid, int first) {
        int cells = grid.rows * grid.cols;
        grid.onClick((cell, button) -> click(first + cell, button));
        grid.onPress((cell, button) -> pressed(first + cell, button));
        this.places.add((mx, my) -> {
            int cell = grid.visible && grid.active ? grid.cellAt(mx, my) : -1;
            return cell < 0 ? -1 : first + cell;
        });
        this.views.add(() -> {
            for (int i = 0; i < cells; i++) {
                ItemView v = view(shown(first + i));
                grid.item(i, v, v == null ? 0 : v.count());
                grid.dragged(i, spreading() && this.dragSlots.contains(first + i));
            }
        });
        refresh();
        return this;
    }

    /** Slot {@code slot} shown in the single well, played by its clicks. */
    public StackHand bind(SlotComponent well, int slot) {
        well.onClick(button -> click(slot, button));
        well.onPress(button -> pressed(slot, button));
        this.places.add((mx, my) -> well.visible && well.active && well.isOver(mx, my) ? slot : -1);
        this.views.add(() -> {
            well.item(view(shown(slot)));
            well.dragged = spreading() && this.dragSlots.contains(slot);
        });
        refresh();
        return this;
    }

    /** Slots {@code first} to {@code first + 35} in vanilla's order: the hotbar first, then the rows. */
    public StackHand bind(PlayerInventoryComponent inventory, int first) {
        bind(inventory.hotbar, first);
        return bind(inventory.rows, first + 9);
    }

    /** Draws the carried stack centred on the mouse; the host calls it last, over everything else. */
    public void drawCarried(Draw d, int mx, int my) {
        Stack hand = carriedShown();
        if (hand == null && spreading()) {
            // The game keeps the stack at the mouse with a 0 when the drag will empty the hand.
            ItemView whole = view(this.carried);
            d.item(whole, mx - 8, my - 8);
            d.nextLayer();
            d.itemDecorations(whole, mx - 8, my - 8, "0");
            return;
        }
        ItemView v = view(hand);
        if (v == null) {
            return;
        }
        d.item(v, mx - 8, my - 8);
        d.itemDecorations(v, mx - 8, my - 8);
    }

    private void refresh() {
        for (Runnable view : this.views) {
            view.run();
        }
    }
}
